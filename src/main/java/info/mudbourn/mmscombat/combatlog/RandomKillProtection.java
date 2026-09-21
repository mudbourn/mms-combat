package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Keeps players safe from random killing while allowing a courting duel: a probing hit lands, a hit back makes it a real fight, and only spamming an unwilling player disables the aggressor's PvP and bounces them clear.
public final class RandomKillProtection {

    private final Map<UUID, Long> pvpDisabled = new HashMap<>();
    private final Map<UUID, Map<UUID, Aggression>> aggression = new HashMap<>();

    // Whether this player-on-player hit is allowed, resolving it into normal combat, a reciprocated duel, a tracked courting hit, or a penalty for spamming an unwilling player.
    public boolean allowHit(ServerPlayer victim, ServerPlayer attacker, CombatManager combat) {
        CombatConfig config = CombatConfig.get();
        if (!config.randomKillProtection) {
            combat.flag(victim);
            combat.flag(attacker);
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
        // The victim has opted in already, so the attacker simply joins the fight.
        if (combat.isFlagged(victim)) {
            clearAggression(attacker.getUUID(), victim.getUUID());
            clearAggression(victim.getUUID(), attacker.getUUID());
            combat.flag(victim);
            combat.flag(attacker);
            return true;
        }
        // The victim courted the attacker first, so this hit back is the mutual agreement to fight.
        if (hasAggression(victim.getUUID(), attacker.getUUID())) {
            clearAggression(victim.getUUID(), attacker.getUUID());
            clearAggression(attacker.getUUID(), victim.getUUID());
            combat.flag(victim);
            combat.flag(attacker);
            return true;
        }
        // A courting hit is allowed to land without dragging the victim into combat; going past the allowance without a hit back is spam and is penalized.
        int hits = recordAggression(attacker.getUUID(), victim.getUUID(), now);
        if (hits > config.rkpFreeHits) {
            penalize(attacker, victim, now, config);
            clearAggression(attacker.getUUID(), victim.getUUID());
            return false;
        }
        return true;
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
        attacker.sendSystemMessage(Component.literal("You kept attacking an unwilling player. Your PvP is disabled for a while."));
    }

    // Drops any penalty and every aggression record touching a player who died or logged out.
    public void remove(UUID player) {
        pvpDisabled.remove(player);
        aggression.remove(player);
        for (Map<UUID, Aggression> targets : aggression.values()) {
            targets.remove(player);
        }
    }

    // Forgets penalty windows and stale courting records once they have run their course.
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        pvpDisabled.entrySet().removeIf(entry -> now >= entry.getValue());
        long window = CombatConfig.get().rkpWindowTicks;
        Iterator<Map<UUID, Aggression>> targetSets = aggression.values().iterator();
        while (targetSets.hasNext()) {
            Map<UUID, Aggression> targets = targetSets.next();
            targets.values().removeIf(record -> now - record.lastHitTick > window);
            if (targets.isEmpty()) {
                targetSets.remove();
            }
        }
    }

    private boolean hasAggression(UUID attacker, UUID victim) {
        Map<UUID, Aggression> targets = aggression.get(attacker);
        return targets != null && targets.containsKey(victim);
    }

    private int recordAggression(UUID attacker, UUID victim, long now) {
        Aggression record = aggression.computeIfAbsent(attacker, key -> new HashMap<>())
            .computeIfAbsent(victim, key -> new Aggression());
        record.hits++;
        record.lastHitTick = now;
        return record.hits;
    }

    private void clearAggression(UUID attacker, UUID victim) {
        Map<UUID, Aggression> targets = aggression.get(attacker);
        if (targets != null) {
            targets.remove(victim);
            if (targets.isEmpty()) {
                aggression.remove(attacker);
            }
        }
    }

    private static final class Aggression {
        private int hits;
        private long lastHitTick;
    }
}
