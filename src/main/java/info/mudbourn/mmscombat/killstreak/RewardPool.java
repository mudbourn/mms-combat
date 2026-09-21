package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.config.CombatConfig.RewardEntry;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import info.mudbourn.mmscombat.killstreak.weapon.KillstreakWeapons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// Rolls one weighted reward from a tier's table and resolves it to its stacks: a killstreak weapon by key, or a plain item by registry id.
public final class RewardPool {

    private RewardPool() {
    }

    public static List<ItemStack> roll(StreakTier tier, ServerPlayer owner) {
        // Resolve every entry up front and weight only among those that produce stacks, so an uninstalled reward never yields an empty roll and a missing crate.
        List<RewardEntry> available = new ArrayList<>();
        List<List<ItemStack>> resolved = new ArrayList<>();
        for (RewardEntry entry : tier.rewardTable) {
            if (Math.max(0, entry.weight) <= 0) {
                continue;
            }
            List<ItemStack> stacks = resolve(entry, owner);
            if (!stacks.isEmpty()) {
                available.add(entry);
                resolved.add(stacks);
            }
        }
        if (available.isEmpty()) {
            return List.of();
        }
        int total = 0;
        for (RewardEntry entry : available) {
            total += Math.max(0, entry.weight);
        }
        int roll = owner.level().getRandom().nextInt(total);
        for (int i = 0; i < available.size(); i++) {
            roll -= Math.max(0, available.get(i).weight);
            if (roll < 0) {
                return resolved.get(i);
            }
        }
        return resolved.get(resolved.size() - 1);
    }

    // Resolves one reward entry to its stacks: a killstreak weapon by key, or a plain item by registry id, empty when the backing item is not installed.
    private static List<ItemStack> resolve(RewardEntry chosen, ServerPlayer owner) {
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
}
