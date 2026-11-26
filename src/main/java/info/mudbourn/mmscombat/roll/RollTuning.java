package info.mudbourn.mmscombat.roll;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.combat_roll.api.event.ServerSideRollEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// Tracks which players are mid-roll on the server and how badly that throws off their aim.
public final class RollTuning {

    // Roll length in ticks.
    public static final int ROLL_TICKS = 14;
    // Spread added to a projectile fired mid-roll.
    public static final float ROLL_INACCURACY = 8.0f;
    private static final Map<UUID, Long> STARTED_AT = new ConcurrentHashMap<>();

    private RollTuning() {
    }

    public static void register() {
        ServerSideRollEvents.PLAYER_START_ROLLING.register((player, velocity) ->
            STARTED_AT.put(player.getUUID(), player.level().getGameTime()));
    }

    public static boolean rolling(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        Long started = STARTED_AT.get(player.getUUID());
        return started != null && player.level().getGameTime() - started < ROLL_TICKS;
    }
}
