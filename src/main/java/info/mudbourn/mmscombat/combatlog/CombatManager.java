package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.CombatStatePayload;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

// The combat flag and its per-player timer: damage and zone entry stamp a deadline, a tick handler counts it down and drives the HUD, and disconnecting while flagged leaves a logout body.
public final class CombatManager {

    private static final CombatManager INSTANCE = new CombatManager();

    private final Map<UUID, Long> deadlines = new HashMap<>();
    private final Map<UUID, Integer> lastSentSeconds = new HashMap<>();
    private final LogoutBodyManager bodies = new LogoutBodyManager();

    private CombatManager() {
    }

    public static CombatManager get() {
        return INSTANCE;
    }

    public static void register() {
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

    private void onDamaged(ServerPlayer victim, net.minecraft.world.entity.Entity attacker) {
        boolean fromPlayer = attacker instanceof Player && attacker != victim;
        if (fromPlayer) {
            flag(victim);
            if (attacker instanceof ServerPlayer aggressor) {
                flag(aggressor);
            }
        } else if (CombatConfig.get().countPvE) {
            flag(victim);
        }
    }

    // Stamps or refreshes a player's combat deadline and pushes the HUD update immediately.
    public void flag(ServerPlayer player) {
        long deadline = player.level().getGameTime() + CombatConfig.get().combatTicks;
        deadlines.put(player.getUUID(), deadline);
        sendState(player, true, secondsLeft(player, deadline));
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
            sendState(player, false, 0);
        }
    }

    private void onDisconnect(ServerPlayer player) {
        Long deadline = deadlines.remove(player.getUUID());
        lastSentSeconds.remove(player.getUUID());
        if (deadline != null) {
            int remaining = (int) Math.max(0, deadline - player.level().getGameTime());
            bodies.createBody(player, CombatConfig.get().resolveLinger(remaining));
        }
    }

    private void tick(MinecraftServer server) {
        bodies.tick(server);
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
            refreshZoneEntry(player);
            long now = player.level().getGameTime();
            if (now >= entry.getValue()) {
                it.remove();
                lastSentSeconds.remove(entry.getKey());
                sendState(player, false, 0);
                continue;
            }
            maybeSendCountdown(player, secondsLeft(player, entry.getValue()));
        }
    }

    private void refreshZoneEntry(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level
            && ZoneStore.flagsCombatOnEntry(level, player.blockPosition())) {
            deadlines.put(player.getUUID(),
                player.level().getGameTime() + CombatConfig.get().combatTicks);
        }
    }

    private void maybeSendCountdown(ServerPlayer player, int seconds) {
        Integer last = lastSentSeconds.get(player.getUUID());
        if (last == null || last != seconds) {
            sendState(player, true, seconds);
        }
    }

    private int secondsLeft(ServerPlayer player, long deadline) {
        long ticks = Math.max(0, deadline - player.level().getGameTime());
        return (int) Math.ceil(ticks / 20.0);
    }

    private void sendState(ServerPlayer player, boolean inCombat, int seconds) {
        lastSentSeconds.put(player.getUUID(), seconds);
        ServerPlayNetworking.send(player, new CombatStatePayload(inCombat, seconds));
    }
}
