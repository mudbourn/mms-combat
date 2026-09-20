package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

// The combat flag and its per-player timer: damage and zone entry stamp a deadline, a tick handler counts it down and drives the HUD, and disconnecting while flagged leaves a logout body.
public final class CombatManager {

    private static final CombatManager INSTANCE = new CombatManager();

    private final Map<UUID, Long> deadlines = new HashMap<>();
    private final Map<UUID, Integer> lastSentSeconds = new HashMap<>();
    private final Map<UUID, Boolean> lastSentInZone = new HashMap<>();
    private final LogoutBodyManager bodies = new LogoutBodyManager();
    private final RandomKillProtection rkp = new RandomKillProtection();

    private CombatManager() {
    }

    public static CombatManager get() {
        return INSTANCE;
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer attacker) {
                return !INSTANCE.rkp.shouldCancel(victim, attacker, victim.level().getGameTime());
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
            rkp.onPlayerHit(aggressor, victim, this);
        } else if (attacker instanceof Player && attacker != victim) {
            flag(victim);
        } else if (CombatConfig.get().countPvE) {
            flag(victim);
        }
    }

    // Stamps or refreshes a player's combat deadline and pushes the HUD update immediately.
    public void flag(ServerPlayer player) {
        boolean wasFlagged = deadlines.containsKey(player.getUUID());
        long deadline = player.level().getGameTime() + CombatConfig.get().combatTicks;
        deadlines.put(player.getUUID(), deadline);
        if (!wasFlagged) {
            playCombatSound(player, MmsCombatRegistries.COMBAT_START);
        }
        sendState(player, true, secondsLeft(player, deadline), inFlaggingZone(player));
    }

    public boolean isFlagged(ServerPlayer player) {
        return deadlines.containsKey(player.getUUID());
    }

    public int remainingSeconds(ServerPlayer player) {
        Long deadline = deadlines.get(player.getUUID());
        return deadline == null ? 0 : secondsLeft(player, deadline);
    }

    public void clearFlag(ServerPlayer player) {
        clear(player);
    }

    private void clear(ServerPlayer player) {
        if (deadlines.remove(player.getUUID()) != null) {
            lastSentSeconds.remove(player.getUUID());
            lastSentInZone.remove(player.getUUID());
            playCombatSound(player, MmsCombatRegistries.COMBAT_END);
            sendState(player, false, 0, false);
        }
    }

    private void onDisconnect(ServerPlayer player) {
        rkp.remove(player.getUUID());
        Long deadline = deadlines.remove(player.getUUID());
        lastSentSeconds.remove(player.getUUID());
        lastSentInZone.remove(player.getUUID());
        if (deadline != null) {
            int remaining = (int) Math.max(0, deadline - player.level().getGameTime());
            boolean persistent = inFlaggingZone(player);
            bodies.createBody(player, CombatConfig.get().resolveLinger(remaining), persistent);
        }
    }

    private void tick(MinecraftServer server) {
        bodies.tick(server);
        rkp.tick(server.overworld());
        // Standing in a flagCombatOnEnter zone flags or refreshes combat, even for a player not already fighting.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (inFlaggingZone(player)) {
                if (!deadlines.containsKey(player.getUUID())) {
                    playCombatSound(player, MmsCombatRegistries.COMBAT_START);
                }
                deadlines.put(player.getUUID(),
                    player.level().getGameTime() + CombatConfig.get().combatTicks);
            }
        }
        if (deadlines.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> it = deadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            boolean inZone = inFlaggingZone(player);
            long now = player.level().getGameTime();
            if (now >= entry.getValue()) {
                it.remove();
                lastSentSeconds.remove(entry.getKey());
                lastSentInZone.remove(entry.getKey());
                playCombatSound(player, MmsCombatRegistries.COMBAT_END);
                sendState(player, false, 0, false);
                continue;
            }
            maybeSendCountdown(player, secondsLeft(player, entry.getValue()), inZone);
        }
    }

    // Whether the player currently stands in a zone that flags combat on entry.
    private boolean inFlaggingZone(ServerPlayer player) {
        return player.level() instanceof ServerLevel level
            && ZoneStore.flagsCombatOnEntry(level, player.blockPosition());
    }

    // Pushes an update only when the displayed content changes: the second while counting down, or the zone hold toggling.
    private void maybeSendCountdown(ServerPlayer player, int seconds, boolean inZone) {
        Integer lastSeconds = lastSentSeconds.get(player.getUUID());
        Boolean lastZone = lastSentInZone.get(player.getUUID());
        boolean changed = lastSeconds == null || lastSeconds != seconds
            || lastZone == null || lastZone != inZone;
        if (changed) {
            sendState(player, true, seconds, inZone);
        }
    }

    private int secondsLeft(ServerPlayer player, long deadline) {
        long ticks = Math.max(0, deadline - player.level().getGameTime());
        return (int) Math.ceil(ticks / 20.0);
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
        lastSentSeconds.put(player.getUUID(), seconds);
        lastSentInZone.put(player.getUUID(), inZone);
        ServerPlayNetworking.send(player, new CombatStatePayload(inCombat, seconds, inZone));
    }
}
