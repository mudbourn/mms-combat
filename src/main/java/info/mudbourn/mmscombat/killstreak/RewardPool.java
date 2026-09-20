package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.config.CombatConfig.RewardEntry;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import info.mudbourn.mmscombat.killstreak.weapon.KillstreakWeapons;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// Rolls one weighted reward from a tier's table and resolves it to its stacks: a killstreak weapon by key, or a plain item by registry id.
public final class RewardPool {

    private RewardPool() {
    }

    public static List<ItemStack> roll(StreakTier tier, ServerPlayer owner) {
        RewardEntry chosen = pick(tier, owner.level().getRandom());
        if (chosen == null) {
            return List.of();
        }
        if (chosen.weapon != null) {
            return KillstreakWeapons.build(chosen.weapon, owner);
        }
        Identifier id = Identifier.tryParse(chosen.item);
        if (id == null) {
            MmsCombat.LOG.warn("Reward id {} is malformed", chosen.item);
            return List.of();
        }
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(item -> List.of(new ItemStack(item, Math.max(1, chosen.count))))
            .orElseGet(() -> {
                MmsCombat.LOG.warn("Reward item {} is not registered; skipping", chosen.item);
                return List.of();
            });
    }

    private static RewardEntry pick(StreakTier tier, RandomSource random) {
        int total = 0;
        for (RewardEntry entry : tier.rewardTable) {
            total += Math.max(0, entry.weight);
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (RewardEntry entry : tier.rewardTable) {
            roll -= Math.max(0, entry.weight);
            if (roll < 0) {
                return entry;
            }
        }
        return null;
    }
}
