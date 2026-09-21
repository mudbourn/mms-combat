package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.killstreak.StreakManager;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

// The combat flag and its per-player timer: damage and zone entry stamp a deadline, a tick handler counts it down and drives the HUD, and disconnecting while flagged leaves a logout body.
public final class CombatManager {

    private static final CombatManager INSTANCE = new CombatManager();

    private final Map<UUID, Long> deadlines = new HashMap<>();
    private final Map<UUID, Integer> lastSentSeconds = new HashMap<>();
    private final Map<UUID, Boolean> lastSentInZone = new HashMap<>();
    private final Map<UUID, Boolean> lastSentInCombat = new HashMap<>();
    private final Map<UUID, Integer> lastSentStreak = new HashMap<>();
    private final Map<UUID, Integer> lastSentDecay = new HashMap<>();
    private final Set<UUID> persistentCombat = new HashSet<>();
    private final LogoutBodyManager bodies = new LogoutBodyManager();
    private final RandomKillProtection rkp = new RandomKillProtection();

    // Scoreboard team whose only job is to paint a flagged player's nametag red.
    private static final String COMBAT_TEAM = "mms_combat";
    // The logout body left by a player who was only holding themselves in combat with the manual toggle: 15 seconds.
    private static final int PERSISTENT_BODY_TICKS = 300;

    private CombatManager() {
    }

    public static CombatManager get() {
        return INSTANCE;
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim
                && source.getEntity() instanceof ServerPlayer attacker
                && attacker != victim) {
                return INSTANCE.rkp.allowHit(victim, attacker, INSTANCE);
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (entity instanceof ServerPlayer victim) {
                INSTANCE.onDamaged(victim, source.getEntity());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                INSTANCE.rkp.remove(player.getUUID());
                INSTANCE.clear(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            INSTANCE.onDisconnect(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            INSTANCE.bodies.onReconnect(handler.player));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) ->
            INSTANCE.bodies.discardIfOrphan(entity));
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE::tick);
    }

    private void onDamaged(ServerPlayer victim, net.minecraft.world.entity.Entity attacker) {
        if (attacker instanceof ServerPlayer aggressor && aggressor != victim) {
            // Player-vs-player flagging is decided in allowHit before the hit lands, so a courting hit never drags the victim into combat.
            return;
        }
        if (attacker instanceof Player && attacker != victim) {
            flag(victim);
        } else if (CombatConfig.get().countPvE) {
            flag(victim);
        }
    }

    // Stamps or refreshes a player's combat deadline and pushes the HUD update immediately.
    public void flag(ServerPlayer player) {
        boolean wasFlagged = isFlagged(player);
        long deadline = player.level().getGameTime() + CombatConfig.get().combatTicks;
        deadlines.put(player.getUUID(), deadline);
        if (!wasFlagged) {
            playCombatSound(player, MmsCombatRegistries.COMBAT_START);
        }
        syncCombatTeam(player);
        sendState(player, true, combatSeconds(player), inFlaggingZone(player));
    }

    public boolean isFlagged(ServerPlayer player) {
        return deadlines.containsKey(player.getUUID()) || persistentCombat.contains(player.getUUID());
    }

    // Whether a player is holding themselves in combat with the manual toggle.
    public boolean isPersistentCombat(ServerPlayer player) {
        return persistentCombat.contains(player.getUUID());
    }

    // Flips a player's manual combat hold, which keeps them flagged and attackable until they turn it back off.
    public void setPersistentCombat(ServerPlayer player, boolean on) {
        boolean wasFlagged = isFlagged(player);
        if (on) {
            persistentCombat.add(player.getUUID());
        } else {
            persistentCombat.remove(player.getUUID());
        }
        if (on && !wasFlagged) {
            playCombatSound(player, MmsCombatRegistries.COMBAT_START);
        }
        forgetSentState(player.getUUID());
        syncCombatTeam(player);
        sendState(player, isFlagged(player), combatSeconds(player), inFlaggingZone(player));
    }

    // The countdown to show: the persistent hold takes priority over any timer, then the ticking deadline, then nothing.
    private int combatSeconds(ServerPlayer player) {
        if (persistentCombat.contains(player.getUUID())) {
            return -1;
        }
        Long deadline = deadlines.get(player.getUUID());
        return deadline != null ? secondsLeft(player, deadline) : 0;
    }

    public int remainingSeconds(ServerPlayer player) {
        Long deadline = deadlines.get(player.getUUID());
        return deadline == null ? 0 : secondsLeft(player, deadline);
    }

    public void clearFlag(ServerPlayer player) {
        boolean wasFlagged = isFlagged(player);
        persistentCombat.remove(player.getUUID());
        deadlines.remove(player.getUUID());
        if (wasFlagged) {
            forgetSentState(player.getUUID());
            playCombatSound(player, MmsCombatRegistries.COMBAT_END);
            syncCombatTeam(player);
            sendState(player, false, 0, false);
        }
    }

    private void clear(ServerPlayer player) {
        if (deadlines.remove(player.getUUID()) != null && !isFlagged(player)) {
            forgetSentState(player.getUUID());
            playCombatSound(player, MmsCombatRegistries.COMBAT_END);
            syncCombatTeam(player);
            sendState(player, false, 0, false);
        }
    }

    private void onDisconnect(ServerPlayer player) {
        boolean flagged = isFlagged(player);
        rkp.remove(player.getUUID());
        Long deadline = deadlines.remove(player.getUUID());
        forgetSentState(player.getUUID());
        if (!flagged) {
            return;
        }
        // A timed fight leaves a body for the full linger; a manual PvP hold leaves a short one so persistent loggers are still vulnerable on logout.
        int linger;
        if (deadline != null) {
            int remaining = (int) Math.max(0, deadline - player.level().getGameTime());
            linger = CombatConfig.get().resolveLinger(remaining);
        } else {
            linger = PERSISTENT_BODY_TICKS;
        }
        boolean forceLoad = inFlaggingZone(player);
        bodies.createBody(player, linger, forceLoad);
    }

    private void tick(MinecraftServer server) {
        bodies.tick(server);
        rkp.tick(server.overworld());
        // Standing in a flagCombatOnEnter zone flags or refreshes combat, even for a player not already fighting.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (inFlaggingZone(player)) {
                if (!isFlagged(player)) {
                    playCombatSound(player, MmsCombatRegistries.COMBAT_START);
                }
                deadlines.put(player.getUUID(),
                    player.level().getGameTime() + CombatConfig.get().combatTicks);
            }
        }
        Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.level().getGameTime() >= entry.getValue()) {
                it.remove();
                if (!isFlagged(player)) {
                    forgetSentState(entry.getKey());
                    playCombatSound(player, MmsCombatRegistries.COMBAT_END);
                    sendState(player, false, 0, false);
                }
            }
        }
        // Keeps every player's HUD in sync, so the streak counter and its decay countdown update even for players who are not combat logged.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshHud(player);
        }
    }

    // Whether the player currently stands in a zone that flags combat on entry.
    private boolean inFlaggingZone(ServerPlayer player) {
        return player.level() instanceof ServerLevel level
            && ZoneStore.flagsCombatOnEntry(level, player.blockPosition());
    }

    // Pushes an update only when the displayed content changes: the combat flag, its countdown, the zone hold, the streak, or the streak's decay countdown.
    private void refreshHud(ServerPlayer player) {
        UUID id = player.getUUID();
        syncCombatTeam(player);
        boolean inCombat = isFlagged(player);
        int seconds = combatSeconds(player);
        boolean inZone = inFlaggingZone(player);
        int streak = StreakManager.get().getStreak(player);
        int decay = StreakManager.get().decaySecondsLeft(player);
        Boolean lastCombat = lastSentInCombat.get(id);
        Integer lastSeconds = lastSentSeconds.get(id);
        Boolean lastZone = lastSentInZone.get(id);
        Integer lastStreak = lastSentStreak.get(id);
        Integer lastDecay = lastSentDecay.get(id);
        boolean changed = lastCombat == null || lastCombat != inCombat
            || lastSeconds == null || lastSeconds != seconds
            || lastZone == null || lastZone != inZone
            || lastStreak == null || lastStreak != streak
            || lastDecay == null || lastDecay != decay;
        if (changed) {
            sendState(player, inCombat, seconds, inZone);
        }
    }

    private int secondsLeft(ServerPlayer player, long deadline) {
        long ticks = Math.max(0, deadline - player.level().getGameTime());
        return (int) Math.ceil(ticks / 20.0);
    }

    // Drops the last-sent HUD snapshot for a player so the next send is never suppressed as unchanged.
    private void forgetSentState(UUID player) {
        lastSentInCombat.remove(player);
        lastSentSeconds.remove(player);
        lastSentInZone.remove(player);
        lastSentStreak.remove(player);
        lastSentDecay.remove(player);
    }

    // Keeps a flagged player on the red-nametag team and pulls everyone else off it, so combat state is visible to others.
    private void syncCombatTeam(ServerPlayer player) {
        Scoreboard scoreboard = player.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(COMBAT_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(COMBAT_TEAM);
            team.setColor(ChatFormatting.RED);
            team.setAllowFriendlyFire(true);
            team.setSeeFriendlyInvisibles(false);
        }
        String name = player.getScoreboardName();
        boolean onTeam = team.getPlayers().contains(name);
        boolean inCombat = isFlagged(player);
        if (inCombat && !onTeam) {
            scoreboard.addPlayerToTeam(name, team);
        } else if (!inCombat && onTeam) {
            scoreboard.removePlayerFromTeam(name, team);
        }
    }

    // Plays a combat-state cue to just this player, at their own position so they always hear it.
    private void playCombatSound(ServerPlayer player, SoundEvent sound) {
        player.connection.send(new ClientboundSoundPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
            SoundSource.PLAYERS,
            player.getX(),
            player.getY(),
            player.getZ(),
            1.0F,
            1.0F,
            player.level().getRandom().nextLong()));
    }

    private void sendState(ServerPlayer player, boolean inCombat, int seconds, boolean inZone) {
        int streak = StreakManager.get().getStreak(player);
        int decay = StreakManager.get().decaySecondsLeft(player);
        lastSentInCombat.put(player.getUUID(), inCombat);
        lastSentSeconds.put(player.getUUID(), seconds);
        lastSentInZone.put(player.getUUID(), inZone);
        lastSentStreak.put(player.getUUID(), streak);
        lastSentDecay.put(player.getUUID(), decay);
        ServerPlayNetworking.send(player, new CombatStatePayload(inCombat, seconds, inZone, streak, decay));
    }
}
