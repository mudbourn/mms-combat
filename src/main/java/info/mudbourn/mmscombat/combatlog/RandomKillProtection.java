package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Keeps players safe from random killing: a hit only lands when the victim has opted into combat, and an aggressor who strikes an unprovoked player is bounced away and has their PvP disabled for the penalty window.
public final class RandomKillProtection {

    private final Map<UUID, Long> pvpDisabled = new HashMap<>();

    // Whether this player-on-player hit is allowed, penalizing and separating the aggressor when it is not.
    public boolean allowHit(ServerPlayer victim, ServerPlayer attacker, CombatManager combat) {
        CombatConfig config = CombatConfig.get();
        if (!config.randomKillProtection) {
            return true;
        }
        long now = attacker.level().getGameTime();
        if (isPvpDisabled(attacker.getUUID(), now)) {
            attacker.displayClientMessage(Component.literal("Your PvP is disabled."), true);
            return false;
        }
        if (isPvpDisabled(victim.getUUID(), now)) {
            attacker.displayClientMessage(Component.literal("That player's PvP is disabled."), true);
            return false;
        }
        if (combat.isFlagged(victim)) {
            return true;
        }
        penalize(attacker, victim, now, config);
        return false;
    }

    // Whether a player currently has their PvP disabled by a penalty, forgetting the entry once it lapses.
    public boolean isPvpDisabled(UUID player, long now) {
        Long expiry = pvpDisabled.get(player);
        if (expiry == null) {
            return false;
        }
        if (now >= expiry) {
            pvpDisabled.remove(player);
            return false;
        }
        return true;
    }

    // Disables the aggressor's PvP for the penalty window and throws them clear of the victim so the fight is broken up.
    private void penalize(ServerPlayer attacker, ServerPlayer victim, long now, CombatConfig config) {
        pvpDisabled.put(attacker.getUUID(), now + config.rkpPenaltyTicks);
        double dx = attacker.getX() - victim.getX();
        double dz = attacker.getZ() - victim.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) {
            float yaw = attacker.getYRot() * ((float) Math.PI / 180.0f);
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            length = 1.0;
        }
        double distance = config.rkpTeleportBlocks;
        double x = victim.getX() + dx / length * distance;
        double z = victim.getZ() + dz / length * distance;
        attacker.teleportTo(x, victim.getY(), z);
        attacker.sendSystemMessage(Component.literal("You attacked an unprovoked player. Your PvP is disabled for a while."));
    }

    // Drops any penalty tracked for a player who died or logged out.
    public void remove(UUID player) {
        pvpDisabled.remove(player);
    }

    // Forgets penalty windows that have run their course.
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = pvpDisabled.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue()) {
                it.remove();
            }
        }
    }
}
