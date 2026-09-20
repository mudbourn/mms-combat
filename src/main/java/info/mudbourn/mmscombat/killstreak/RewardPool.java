package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.config.CombatConfig.RewardEntry;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// Rolls one weighted reward from a tier's table and resolves it to an item stack, skipping ids whose mod (a JEG gun, say) is not installed.
public final class RewardPool {

    private RewardPool() {
    }

    public static ItemStack roll(StreakTier tier, RandomSource random) {
        RewardEntry chosen = pick(tier, random);
        if (chosen == null) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(chosen.item);
        if (id == null) {
            MmsCombat.LOG.warn("Reward id {} is malformed", chosen.item);
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(item -> new ItemStack(item, Math.max(1, chosen.count)))
            .orElseGet(() -> {
                MmsCombat.LOG.warn("Reward item {} is not registered; skipping", chosen.item);
                return ItemStack.EMPTY;
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
