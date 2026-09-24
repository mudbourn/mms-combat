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

// Rolls one weighted reward from a tier's table and resolves it to perishable, owner-bound stacks.
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

    // Resolves one reward entry to its perishable stacks, empty when the backing item is not installed.
    private static List<ItemStack> resolve(RewardEntry chosen, ServerPlayer owner) {
        List<ItemStack> stacks = new ArrayList<>();
        if (chosen.weapon != null) {
            stacks.addAll(KillstreakWeapons.build(chosen.weapon, owner));
        } else if (chosen.gun != null) {
            stacks.addAll(KillstreakWeapons.buildGun(chosen.gun, owner));
        } else {
            stacks.addAll(item(chosen.item, chosen.count));
            if (stacks.isEmpty()) {
                return List.of();
            }
            if (chosen.with != null) {
                for (String companion : chosen.with) {
                    stacks.addAll(item(companion, 1));
                }
            }
        }
        for (ItemStack stack : stacks) {
            Perishable.bind(stack, owner);
        }
        return stacks;
    }

    private static List<ItemStack> item(String itemId, int count) {
        Identifier id = itemId == null ? null : Identifier.tryParse(itemId);
        if (id == null) {
            MmsCombat.LOG.warn("Reward id {} is malformed", itemId);
            return List.of();
        }
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(item -> List.of(new ItemStack(item, Math.max(1, count))))
            .orElseGet(() -> {
                MmsCombat.LOG.warn("Reward item {} is not registered; skipping", itemId);
                return List.of();
            });
    }
}
