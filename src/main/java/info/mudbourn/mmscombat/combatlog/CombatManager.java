package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.killstreak.StreakManager;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.scores.TeamColor;

// The combat flag and its per-player timer: damage and zone entry stamp a deadline, a tick handler counts it down and drives the HUD, and disconnecting while flagged leaves a logout body.
public final class CombatManager {

    private static final CombatManager INSTANCE = new CombatManager();

    private final Map<UUID, Long> deadlines = new HashMap<>();
    private final Map<UUID, HudSnapshot> lastSent = new HashMap<>();
    private final Set<UUID> persistentCombat = new HashSet<>();
    private final LogoutBodyManager bodies = new LogoutBodyManager();
    private final Set<UUID> inZoneLastTick = new HashSet<>();
    // Zone notices: a crossing within five seconds of a notice is held back until the player has stayed on one side
    // of the border for ten seconds, so walking along an edge shows one message instead of a stream.
    private static final int NOTICE_BURST_TICKS = 100;
    private static final int NOTICE_SETTLE_TICKS = 200;
    private final Map<UUID, Boolean> noticeShown = new HashMap<>();
    private final Map<UUID, Integer> noticeShownAt = new HashMap<>();
    private final Map<UUID, Integer> noticeHeldSince = new HashMap<>();
    private final List<Predicate<ServerPlayer>> pvpOverrides = new ArrayList<>();
    private final List<Predicate<ServerPlayer>> logoutBodyVetoes = new ArrayList<>();

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
                return INSTANCE.allowPvp(victim, attacker);
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

    // Whether a player-on-player hit may land: both sides must have PvP on, and a landed hit flags both.
    private boolean allowPvp(ServerPlayer victim, ServerPlayer attacker) {
        if (!isPvpEnabled(attacker)) {
            attacker.sendSystemMessage(Component.literal("Your PvP is off. Use /combatlog on to fight."), true);
            return false;
        }
        if (!isPvpEnabled(victim)) {
            attacker.sendSystemMessage(Component.literal("That player has PvP off."), true);
            return false;
        }
        flag(victim);
        flag(attacker);
        return true;
    }

    // Whether a player has opted into PvP with the toggle, is standing in a combat zone, or is forced on by another mod.
    public boolean isPvpEnabled(ServerPlayer player) {
        return persistentCombat.contains(player.getUUID())
            || inFlaggingZone(player)
            || anyMatch(pvpOverrides, player);
    }

    /**
     * Adds a check that forces a player's PvP on while it returns true, whatever
     * their own toggle says. Call it once at mod init.
     */
    public void addPvpOverride(Predicate<ServerPlayer> override) {
        pvpOverrides.add(override);
    }

    /**
     * Adds a check that stops a flagged player from leaving a logout body when
     * they disconnect while it returns true. Call it once at mod init.
     */
    public void addLogoutBodyVeto(Predicate<ServerPlayer> veto) {
        logoutBodyVetoes.add(veto);
    }

    private static boolean anyMatch(List<Predicate<ServerPlayer>> checks, ServerPlayer player) {
        for (Predicate<ServerPlayer> check : checks) {
            if (check.test(player)) {
                return true;
            }
        }
        return false;
    }

    private void onDamaged(ServerPlayer victim, net.minecraft.world.entity.Entity attacker) {
        if (attacker instanceof ServerPlayer aggressor && aggressor != victim) {
            // Player-vs-player flagging happens in allowPvp before the hit lands.
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
            LoadoutChoices.prompt(player);
        }
        syncCombatTeam(player);
        sendState(player, true, combatSeconds(player), hudZone(player, inFlaggingZone(player)));
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
            LoadoutChoices.prompt(player);
        }
        forgetSentState(player.getUUID());
        syncCombatTeam(player);
        sendState(player, isFlagged(player), combatSeconds(player), hudZone(player, inFlaggingZone(player)));
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
        inZoneLastTick.remove(player.getUUID());
        Long deadline = deadlines.remove(player.getUUID());
        forgetSentState(player.getUUID());
        if (!flagged || anyMatch(logoutBodyVetoes, player)) {
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
        if (!deadlines.isEmpty()) {
            deadlines.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
        if (!inZoneLastTick.isEmpty()) {
            inZoneLastTick.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
        if (!noticeShown.isEmpty() || !noticeHeldSince.isEmpty()) {
            noticeShown.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
            noticeShownAt.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
            noticeHeldSince.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inZone = inFlaggingZone(player);
            if (inZone && inZoneLastTick.add(player.getUUID())) {
                enterZone(player);
            } else if (!inZone && inZoneLastTick.remove(player.getUUID())) {
                exitZone(player);
            }
            settleZoneNotice(player, server.getTickCount());
            if (inZone) {
                holdInZone(player);
            } else {
                expireIfDue(player);
            }
            refreshHud(player, inZone);
        }
    }

    // Entering a combat zone turns the PvP toggle on for as long as the player stays inside.
    private void enterZone(ServerPlayer player) {
        if (persistentCombat.contains(player.getUUID())) {
            return;
        }
        setPersistentCombat(player, true);
        zoneNotice(player, true);
    }

    // Leaving a combat zone turns the PvP toggle off and leaves the combat timer counting down from the exit.
    private void exitZone(ServerPlayer player) {
        if (!persistentCombat.contains(player.getUUID())) {
            return;
        }
        setPersistentCombat(player, false);
        zoneNotice(player, false);
    }

    // Shows a zone notice now, or holds it back when it follows another within the burst window.
    private void zoneNotice(ServerPlayer player, boolean inZone) {
        UUID id = player.getUUID();
        int now = player.level().getServer().getTickCount();
        Integer shownAt = noticeShownAt.get(id);
        if (noticeHeldSince.containsKey(id) || shownAt != null && now - shownAt < NOTICE_BURST_TICKS) {
            noticeHeldSince.put(id, now);
        } else {
            showZoneNotice(player, inZone, now);
        }
    }

    // Once a held-back player has stayed put long enough, shows where they ended up if it differs from the last notice.
    private void settleZoneNotice(ServerPlayer player, int now) {
        UUID id = player.getUUID();
        Integer heldSince = noticeHeldSince.get(id);
        if (heldSince == null || now - heldSince < NOTICE_SETTLE_TICKS) {
            return;
        }
        noticeHeldSince.remove(id);
        boolean inZone = persistentCombat.contains(id);
        if (!Boolean.valueOf(inZone).equals(noticeShown.get(id))) {
            showZoneNotice(player, inZone, now);
        }
    }

    private void showZoneNotice(ServerPlayer player, boolean inZone, int now) {
        noticeShown.put(player.getUUID(), inZone);
        noticeShownAt.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(inZone ? "You entered a combat zone. PvP is on." : "PvP has been disabled."), false);
    }

    // Standing in a flagCombatOnEnter zone flags or refreshes combat, even for a player not already fighting.
    private void holdInZone(ServerPlayer player) {
        if (!isFlagged(player)) {
            playCombatSound(player, MmsCombatRegistries.COMBAT_START);
            LoadoutChoices.prompt(player);
        }
        deadlines.put(player.getUUID(), player.level().getGameTime() + CombatConfig.get().combatTicks);
    }

    // Drops a lapsed combat deadline and tells the player they are out of combat unless the manual hold keeps them in.
    private void expireIfDue(ServerPlayer player) {
        Long deadline = deadlines.get(player.getUUID());
        if (deadline == null || player.level().getGameTime() < deadline) {
            return;
        }
        deadlines.remove(player.getUUID());
        if (!isFlagged(player)) {
            forgetSentState(player.getUUID());
            playCombatSound(player, MmsCombatRegistries.COMBAT_END);
            sendState(player, false, 0, false);
        }
    }

    // Whether the player currently stands in a zone that flags combat on entry.
    private boolean inFlaggingZone(ServerPlayer player) {
        return player.level() instanceof ServerLevel level
            && ZoneStore.flagsCombatOnEntry(level, player.blockPosition());
    }

    // Pushes an update only when the displayed content changes: the combat flag, its countdown, the zone hold, the streak, or the streak's decay countdown.
    // The zone state the HUD shows: while a zone notice is held back, the HUD keeps showing what the last notice said,
    // so the indicator settles at the same moment as the chat notice instead of flickering along a border.
    private boolean hudZone(ServerPlayer player, boolean inZone) {
        Boolean shown = noticeShown.get(player.getUUID());
        return noticeHeldSince.containsKey(player.getUUID()) && shown != null ? shown : inZone;
    }

    private void refreshHud(ServerPlayer player, boolean actualZone) {
        boolean inZone = hudZone(player, actualZone);
        syncCombatTeam(player);
        boolean inCombat = isFlagged(player);
        int seconds = combatSeconds(player);
        int streak = StreakManager.get().getStreak(player);
        int decay = StreakManager.get().decaySecondsLeft(player);
        HudSnapshot last = lastSent.get(player.getUUID());
        boolean changed = last == null
            || last.inCombat() != inCombat
            || last.seconds() != seconds
            || last.inZone() != inZone
            || last.streak() != streak
            || last.decay() != decay;
        if (changed) {
            sendState(player, inCombat, seconds, inZone, streak, decay);
        }
    }

    private int secondsLeft(ServerPlayer player, long deadline) {
        long ticks = Math.max(0, deadline - player.level().getGameTime());
        return (int) Math.ceil(ticks / 20.0);
    }

    // Drops the last-sent HUD snapshot for a player so the next send is never suppressed as unchanged.
    private void forgetSentState(UUID player) {
        lastSent.remove(player);
    }

    // Keeps a flagged player on the red-nametag team and pulls everyone else off it, so combat state is visible to others.
    private void syncCombatTeam(ServerPlayer player) {
        Scoreboard scoreboard = player.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(COMBAT_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(COMBAT_TEAM);
            team.setColor(Optional.of(TeamColor.RED));
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
        sendState(
            player,
            inCombat,
            seconds,
            inZone,
            StreakManager.get().getStreak(player),
            StreakManager.get().decaySecondsLeft(player));
    }

    private void sendState(ServerPlayer player, boolean inCombat, int seconds, boolean inZone, int streak, int decay) {
        lastSent.put(player.getUUID(), new HudSnapshot(inCombat, seconds, inZone, streak, decay));
        ServerPlayNetworking.send(player, new CombatStatePayload(inCombat, seconds, inZone, streak, decay));
    }

    // The HUD content last sent to a player, compared each tick so unchanged state is never resent.
    private record HudSnapshot(boolean inCombat, int seconds, boolean inZone, int streak, int decay) {
    }
}
