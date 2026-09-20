package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

// Discourages random kills: an aggressor who hits an unretaliating victim more than the free-hit count is weakened and bounced away, and the victim is barred from striking back until that penalty ends.
public final class RandomKillProtection {

    private final Map<UUID, Map<UUID, Aggression>> aggression = new HashMap<>();
    private final Map<UUID, Protection> protections = new HashMap<>();

    // Whether an incoming player hit must be denied because the victim is barred from striking the protected aggressor during the penalty window.
    public boolean shouldCancel(ServerPlayer victim, ServerPlayer attacker, long now) {
        Protection protection = protections.get(victim.getUUID());
        if (protection == null || !protection.victim.equals(attacker.getUUID())) {
            return false;
        }
        if (now < protection.expiry) {
            attacker.sendSystemMessage(Component.literal("You cannot attack this player yet."));
            return true;
        }
        return false;
    }

    // Resolves one player-on-player hit into mutual combat, a random-kill penalty, or a tracked unanswered hit.
    public void onPlayerHit(ServerPlayer attacker, ServerPlayer victim, CombatManager combat) {
        long now = attacker.level().getGameTime();
        CombatConfig config = CombatConfig.get();
        if (!config.randomKillProtection) {
            combat.flag(victim);
            combat.flag(attacker);
            return;
        }
        if (combat.isFlagged(attacker) && combat.isFlagged(victim)) {
            combat.flag(attacker);
            combat.flag(victim);
            return;
        }
        Protection protection = protections.get(victim.getUUID());
        if (protection != null && protection.victim.equals(attacker.getUUID())) {
            protections.remove(victim.getUUID());
            combat.flag(attacker);
            combat.flag(victim);
            return;
        }
        if (consumeAggression(victim.getUUID(), attacker.getUUID())) {
            clearAggression(attacker.getUUID(), victim.getUUID());
            combat.flag(attacker);
            combat.flag(victim);
            return;
        }
        int hits = recordAggression(attacker.getUUID(), victim.getUUID(), now);
        if (hits > config.rkpFreeHits) {
            penalize(attacker, victim, now, config);
            clearAggression(attacker.getUUID(), victim.getUUID());
        }
        if (combat.isFlagged(attacker)) {
            combat.flag(attacker);
        }
    }

    // Weakens the aggressor, throws them the configured distance away from the victim, and opens the victim's no-retaliation window.
    private void penalize(ServerPlayer attacker, ServerPlayer victim, long now, CombatConfig config) {
        attacker.addEffect(new MobEffectInstance(
            MobEffects.WEAKNESS,
            config.rkpWeaknessTicks,
            config.rkpWeaknessAmplifier));
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
        attacker.sendSystemMessage(Component.literal("You were penalized for attacking an unprovoked player."));
        protections.put(victim.getUUID(), new Protection(
            attacker.getUUID(),
            now + config.rkpWeaknessTicks));
    }

    // Drops every aggression record and protection touching a player who died or logged out.
    public void remove(UUID player) {
        aggression.remove(player);
        for (Map<UUID, Aggression> targets : aggression.values()) {
            targets.remove(player);
        }
        protections.remove(player);
        protections.values().removeIf(protection -> protection.victim.equals(player));
    }

    // Forgets stale unanswered hits and spent protection windows so pairs reset once their window passes.
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        long window = CombatConfig.get().rkpWindowTicks;
        Iterator<Map<UUID, Aggression>> targetSets = aggression.values().iterator();
        while (targetSets.hasNext()) {
            Map<UUID, Aggression> targets = targetSets.next();
            targets.values().removeIf(record -> now - record.lastHitTick > window);
            if (targets.isEmpty()) {
                targetSets.remove();
            }
        }
        protections.values().removeIf(protection -> now > protection.expiry + window);
    }

    private boolean consumeAggression(UUID attacker, UUID victim) {
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

    private record Protection(UUID victim, long expiry) {
    }
}
