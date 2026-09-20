# MMS Combat

Server-authoritative combat systems for MMSLive01, built as a Fabric mod so the
logic is event-driven Java instead of per-tick datapack scoreboard polling.

Four features, one mod:

1. **Combat logging** — precise damage-event combat flagging with a real
   per-player timer, and a *vulnerable logout body* left behind when a flagged
   player disconnects.
2. **Combat zones** — admin-defined regions that auto-flag combat on entry and
   suppress natural mob spawning inside, so the area needs no lighting pass.
3. **In-combat HUD** — a networked on-screen indicator (the dagger sprite) with
   the remaining combat timer.
4. **Killstreak rewards** — a chest entity that follows a player when they hit a
   streak tier and hands over a JEG gun or a bespoke ability weapon.

See [docs/DESIGN.md](docs/DESIGN.md) for the architecture and the datapack
pitfalls this deliberately avoids. This mod supersedes the killstreak half of
`ks-support` (never deployed).

Build: `./gradlew build`. Release via the shared engine (see
`mms-pack/.github/workflows/mod-release.yml`).
