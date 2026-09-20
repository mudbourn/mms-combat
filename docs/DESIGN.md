# MMS Combat — Design

Status: implemented. This document is the plan the code is built against. It is
the contract for what each feature does and, just as importantly, how it avoids
the failure modes that made the killstreak and combat-log **datapacks** painful.

## What ships today

- Combat flagging, the per-player timer, and the HUD payload are live. The
  logout body and the killstreak follower chest are both backed by a configured
  `ArmorStand` proxy rather than a bespoke entity type, so there is no custom
  registration or renderer to maintain; the body is killable and the chest is
  invulnerable and owner-named.
- Zones persist as `mms_combat_zones.json` in the world folder (per-save, not a
  `SavedData` blob) and are edited with `/mmscombat zone`. Spawn suppression is
  the `NaturalSpawnerMixin` on `isValidSpawnPostitionForType`.
- Killstreak rewards are a weighted table of registry ids in the config; an id
  whose mod is absent (a JEG gun on a client without JEG) is skipped, so the
  pool degrades instead of erroring.
- Hitting a tier spawns a **killstreak crate**: a custom entity that eases to
  hover ahead of and above its owner and swings behind them as they move, opened
  by the owner into a four-slot "Killstreak Crate" menu to collect the reward.
  It is transient (never saved) and despawns when emptied, its owner is gone, or
  the server restarts. It is drawn from the ported `weapon_crate` Bedrock geo
  through `mms-render-common`'s new `GeoModel` tool.
- Logout bodies are per-session: the snapshot is not restored across a restart.
  A body that outlives the process is discarded the moment its chunk loads on the
  next boot (tagged stands not tracked this session are removed), so nothing
  lingers. Everything else survives a restart through the config and zones file.

## Why a mod, not a datapack

The two references we are replacing — the third-party `ly-combat-log` datapack
and the in-house `ks-support` killstreak datapack — share one root cause of
bugginess: everything they do is expressed as `execute as @a` scoreboard loops
and advancement grant/revoke dances, run every tick, with state smeared across
dozens of `.mcfunction` files and scoreboard objectives. That model has no real
event hooks, no typed state, no atomic transitions, and no way to react to a
disconnect after it has happened.

`mms-combat` moves all of it to server-authoritative Java:

| Concern | Datapack way (buggy) | mms-combat way |
| --- | --- | --- |
| Detect combat | advancement `player_hurt_entity` grant/revoke each tick | `ServerLivingEntityEvents.AFTER_DAMAGE` — fires exactly on damage |
| Combat timer | `scoreboard players remove @s time 1` on every player every tick | one `int` per player in a server-side map, ticked once |
| Logout punish | custom stat `minecraft.leave_game` polled, `/kill` on rejoin | `ServerPlayConnectionEvents.DISCONNECT` — acts at the moment of logout |
| Zone spawn control | `/kill @e[type=monster]` on tick (kills already-spawned mobs) | mixin cancels the spawn *before* it happens |
| HUD | `title @s actionbar` text spam | a custom payload the client renders as a sprite |
| Config | click-to-edit `tellraw` menus mutating scoreboards | one JSON config + brigadier commands |

Everything is server-side except the HUD renderer. State that must survive a
restart lives in a `SavedData` (zones) or is transient per-session (combat
timers, streaks — a streak resets on death anyway).

## Package layout

```
info.mudbourn.mmscombat
├── MmsCombat                 ModInitializer — wires the four subsystems
├── combatlog/                combat flagging + timer + logout body
├── zone/                     region model, SavedData, spawn-cancel mixin, commands
├── net/                      client<->server payloads (combat state, streak)
├── killstreak/               kill tracking, tiers, follower chest, reward pool
└── client/
    ├── MmsCombatClient       ClientModInitializer
    └── hud/                  in-combat indicator renderer
```

Mixins (added as features land) live under `mms_combat.<area>.mixins.json`;
never put a plain helper in a mixin package (mms-weapons enforces this with a
build check — we adopt the same rule if/when we add mixins).

---

## Feature 1 — Combat logging (with vulnerable logout body)

### Flagging
A player enters combat when they deal or take damage from **another player**
(PvP) — configurable to also count damage to/from hostile mobs (PvE). Detection
is a single `ServerLivingEntityEvents.AFTER_DAMAGE` listener that resolves the
attacker/victim to `ServerPlayer`s and stamps a combat deadline
(`gameTime + combatTicks`) into a `CombatTracker` map keyed by player UUID.

Re-triggering damage refreshes the deadline (no stacking). A server tick handler
walks only the *flagged* players (not all players), decrements toward the
deadline, sends HUD updates, and clears the flag at zero.

### The logout body (anti-combat-log)
When a flagged player disconnects (`ServerPlayConnectionEvents.DISCONNECT`):

1. Their real player entity is removed as usual, but we **snapshot** their
   position, inventory, health, and remaining combat time.
2. We spawn a **logout body**: a marker/armor-stand-backed living proxy (or a
   minimal custom entity) at that spot, holding the snapshot, that stays
   killable for `bodyLingerTicks` (default = remaining combat time, min 30s).
3. If the body is killed before it expires, the snapshot inventory drops at the
   body and the player is marked "died while logged" — on next login they
   respawn empty (death applied). If the body survives to expiry, it despawns
   and the player keeps everything.
4. On reconnect while the body still exists, the body is removed and the player
   is restored in place (no double items).

This is strictly better than `ly-combat-log`'s "kill + drop on rejoin": the
attacker gets a real window to finish the kill, the logger cannot escape by
staying offline, and there is no rejoin-time `/kill` that fires in the wrong
dimension or after inventory changes.

Edge cases the design must handle explicitly: server shutdown while a body
exists (persist snapshot in `SavedData`, restore or resolve on boot); body in an
unloaded chunk (keep it force-loaded for its short life, or resolve on expiry
timer independent of chunk load); player banned/kicked vs. voluntary quit
(treat identically — flagged is flagged).

Config: `combatTicks`, `countPvE`, `bodyLingerTicks`, `bodyForceLoad`.

---

## Feature 2 — Combat zones

### Model
A zone is `{ name, dimension, AABB (or column + y-range), flags }` where flags
include `flagCombatOnEnter`, `suppressNaturalSpawns`, and (future) `noElytra`,
`keepInventoryOverride`. Zones are stored in a world-scoped `SavedData`
(`mms_combat_zones`) so they persist and are per-save, not global.

### Auto-flag on entry
The combat-log tick already iterates players cheaply; zone membership is checked
there (point-in-AABB per zone the player's chunk touches). Entering a
`flagCombatOnEnter` zone stamps/refreshes the combat deadline exactly like taking
a hit, so the HUD and logout-body rules apply automatically inside arenas.

### Spawn suppression (the lighting-free goal)
Natural mob spawning is cancelled *inside* the zone by a mixin on the natural
spawner's per-position check (the method that decides whether a mob may spawn at
a `BlockPos`). If the pos is inside any `suppressNaturalSpawns` zone, return
"deny" before the entity is created. This is the key win over the datapack
approach: no mobs ever spawn, so there is nothing to `/kill`, no flicker, no
lighting pass, and no per-tick entity sweep. Spawner blocks, spawn eggs, and
event/reward spawns are unaffected (they do not go through natural spawning).

We already ship `mms-ddd-no-natural-gen` (a global biome-tag emptying datapack)
for a different, world-wide purpose; zones are the *regional* tool and do not
touch biome tags.

### Commands (brigadier, op-gated)
`/mmscombat zone add <name> <corner1> <corner2>`, `... remove <name>`,
`... list`, `... flag <name> <flag> <bool>`, and `... show` (a particle outline
of nearby zones in the caller's dimension).

### Test commands (brigadier, op-gated)
So each feature can be exercised solo, without a second player or a grind:
`/mmscombat flag [target]` and `... clear [target]` drive the combat flag and its
HUD directly; `... status [target]` prints combat state, seconds left, streak,
and the zones the target stands in; `... streak <count> [target]` sets a streak
and fires the matching tier reward; `... reward <tierKills>` spawns a follower
chest for that tier on the spot.

---

## Feature 3 — In-combat HUD indicator

Server sends a small custom payload (`CombatStatePayload { inCombat, secondsLeft }`)
to a player whenever their combat state changes and on a throttled interval
while the timer counts down. The client caches the latest state and, in a
`HudRenderCallback` / layered HUD registration, draws:

- the dagger sprite (`assets/mms_combat/textures/gui/in_combat.png`, the 800×800
  source art rendered scaled to ~24–32px) near the hotbar/crosshair, and
- the remaining seconds as text beside it, fading out when combat ends.

All clients in the pack have the mod, so there is no "vanilla client" fallback
to design for, but the payload is defined so a missing client simply renders
nothing (server logic never depends on the HUD).

Source art note: `in_combat.png` is kept at full resolution as the source of
truth; the renderer scales it down. If crispness at small sizes is an issue we
add a pre-downscaled nearest-neighbor 64×64 variant rather than blurring at draw
time.

---

## Feature 4 — Killstreak rewards (killstreak crate + weapon pool)

### Streak tracking
Kills are counted per life in a streak map, incremented on a player-kills-player
event, reset on death or logout. Tiers are a config list
`[{ kills, rewardTable }]` (e.g. 3 / 5 / 8 / 12).

### The killstreak crate
On hitting a tier, spawn a **killstreak crate** (`KillstreakCrateEntity`): a
custom entity holding the rolled reward in a four-slot `SimpleContainer`, owner
UUID stored on the entity so only the owner can open it. It is not a mob with AI
and not a display entity; it is moved each tick with exponential easing toward a
hover point computed from the owner's position and look. Idle, it sits ahead of
and above the owner with a small back offset so it is not in their face; as the
owner moves, the target swings behind them so it tweens out of the way. It floats
(no gravity, no physics) and cannot be damaged.

Right-clicking it (owner only) opens a drawn four-slot menu titled "Killstreak
Crate"; taking the reward empties the container and the crate despawns. It is
transient: `shouldBeSaved()` is false, so a restart or a vanished owner simply
drops it rather than leaving an orphan.

The crate is rendered from the ported `weapon_crate` Bedrock geo through the new
`GeoModel` tool in `mms-render-common` (the non-humanoid companion to the
existing `HumanoidGeoModel`), wired in as a composite build. This is the concrete
improvement over the `ks-support` datapack's item-recall / armor-stand chase,
which used teleport loops and tag juggling that desynced: one owned entity with
a lifetime and an owner check removes the whole class of "whose chest is this /
it teleported into a wall" bugs.

### Reward pool (JEG + custom, per your pick)
Each tier's `rewardTable` is a weighted list mixing:

- **JEG guns** — granted by building an `ItemStack` from the JEG item registry
  id (JEG is a native Fabric jar; compile-only dep, runtime-provided). Higher
  tiers unlock stronger guns / attachments / ammo bundles.
- **Custom ability weapons** — bespoke items defined in `mms-combat` (or leaning
  on `mms-weapons` primitives) with an activated ability (e.g. a dash strike, a
  ground-slam, a homing throw). Abilities are server-driven on use so they work
  regardless of client mods.

Config lets each tier's table be re-weighted or restricted (guns-only zones,
custom-only tiers) without code changes.

### Griefing safety (which reward guns destroy blocks)

Before handing out RPG-class guns we must be sure they cannot blow up terrain.
Investigation of the shipped `justenoughguns-fabric-1.4.0.jar` (2026-09-19):

- **Bullets** — governed by `jeg-server.toml [combat] bulletBlockDestructionEnabled`,
  which is already `false` on MMSLive01. Off = no block damage on bullet impact.
- **Explosives** (grenade, grenade launcher, rocket, rocket launcher) — JEG does
  **not** use vanilla explosions. `GrenadeEntity` runs a custom
  `applyBalancedBlastDamage` (radius damage + `BIG_EXPLOSION`/`SMALL_EXPLOSION`
  *visual* particles) with no block removal. A whole-jar scan found **zero**
  references to `Level.explode` / `createExplosion` / `destroyBlock` /
  `removeBlock` / any `Explosion.BlockInteraction`. So JEG explosives already
  deal player/mob damage only and leave terrain intact.

Conclusion: **JEG 1.4.0 reward guns are grief-safe as configured** — no
mms-combat code is required to make them safe. The griefing memory is most
likely an older JEG build or the NPC-only `gunnerTerrainInteractionEnabled`
(also `false` here), not the player guns.

Standing tasks that stay in the plan:

1. **Version guard** — re-run the whole-jar block-API scan whenever JEG is
   bumped in the pack; a future version could switch to vanilla explosions. The
   reward-pool wiring should read this doc's checklist, not assume.
2. **Belt-and-suspenders zone guard (optional)** — since combat zones already
   hook spawning, add an optional per-zone flag that cancels *block* damage from
   any explosion inside the zone (via Fabric's explosion/`ExplosionEvent`-style
   hook, clearing the affected-blocks list while keeping entity damage). This
   costs nothing for JEG (it never triggers a vanilla explosion) but future-
   proofs the arena against *any* explosive mod, not just JEG. Flag name:
   `blockExplosionShield`.
3. **Per-gun allowlist** — the reward table only draws from a vetted gun id list;
   a gun is added to the pool only after it passes the block-API check. Never
   pull "all JEG guns" dynamically.

---

## Build & release (matches the cousins)

- Standard tree: loom-remap 1.17.14, Mojang mappings, Java 21, MC 1.21.11,
  fabric-api `0.141.4+1.21.11`, loader `0.19.3`.
- `.github/workflows/release.yml` is the verbatim caller stub; the reusable
  engine in `mms-pack` does libs/version/publish.
- To make it a shipped mod: add `mms-combat = { role = "mod" }` to
  `mms-pack/mms-repos.toml`, then a `mms-combat.pw.toml` in `mms-pack/mods/`
  with a `[update.github]` block on `mudbourn/mms-combat` (done at first release,
  not now).

## Open questions / risks to resolve before coding a feature

- **Logout body persistence across restart** — simplest correct option is to
  fold the snapshot into the zones `SavedData` and resolve pending bodies on
  boot; confirm we want offline players' bodies to survive a restart at all.
- **JEG item ids** — need to enumerate the exact registry ids/ammo items from
  `justenoughguns-fabric-1.4.0` before wiring the gun rewards. (Block-griefing
  safety already resolved — see "Griefing safety" above; guns are safe as
  configured.)
- **Follower chest rendering** — display-entity + client renderer vs. a real
  entity type; leaning display-entity for zero AI cost.
- **`ks-support` retirement** — strip its killstreak datapack when this ships so
  two systems never both count kills.
