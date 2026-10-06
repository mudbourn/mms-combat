package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.config.CombatConfig.RewardEntry;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import info.mudbourn.mmscombat.killstreak.weapon.KillstreakWeapons;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// Rolls one weighted reward from a tier's table and resolves it to perishable, owner-bound stacks.
public final class RewardPool {

    // How many of a player's previous crate main rewards a new crate may not repeat.
    private static final int REWARD_HISTORY = 2;

    // The player's most recent crate main rewards, newest last, kept across deaths and restarts.
    private static final AttachmentType<List<String>> RECENT_REWARDS = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "recent_crate_rewards"),
        builder -> builder
            .persistent(Codec.STRING.listOf())
            .copyOnDeath()
    );

    private RewardPool() {
    }

    // Forces the history attachment to register before any player data loads.
    public static void register() {
        MmsCombat.LOG.debug("Registered crate reward history {}", RECENT_REWARDS.identifier());
    }

    // The tier's main reward plus its utility draws; empty when the main table yields nothing, so no crate spawns.
    public static List<ItemStack> roll(StreakTier tier, ServerPlayer owner) {
        List<String> recentRewards = owner.getAttachedOrElse(RECENT_REWARDS, List.of());
        List<RewardEntry> mainTable = new ArrayList<>(tier.rewardTable);
        mainTable.removeIf(entry -> recentRewards.contains(key(entry)));
        RewardEntry main = draw(mainTable, owner);
        if (main == null) {
            main = draw(new ArrayList<>(tier.rewardTable), owner);
        }
        if (main == null) {
            return List.of();
        }
        List<String> history = new ArrayList<>(recentRewards);
        history.add(key(main));
        owner.setAttached(RECENT_REWARDS, List.copyOf(history.subList(Math.max(0, history.size() - REWARD_HISTORY), history.size())));
        List<ItemStack> rewards = new ArrayList<>(resolve(main, owner));
        if (tier.utilityTable != null) {
            List<RewardEntry> utilities = new ArrayList<>(tier.utilityTable);
            for (int i = 0; i < tier.utilityCount; i++) {
                RewardEntry utility = draw(utilities, owner);
                if (utility == null) {
                    break;
                }
                utilities.remove(utility);
                rewards.addAll(resolve(utility, owner));
            }
        }
        return rewards;
    }

    // One weighted draw from a table among entries that resolve, or null when none do; unresolvable entries are dropped from the table.
    private static RewardEntry draw(List<RewardEntry> table, ServerPlayer owner) {
        table.removeIf(entry -> entry.weight <= 0 || resolve(entry, owner).isEmpty());
        if (table.isEmpty()) {
            return null;
        }
        int total = 0;
        for (RewardEntry entry : table) {
            total += entry.weight;
        }
        int roll = owner.level().getRandom().nextInt(total);
        for (RewardEntry entry : table) {
            roll -= entry.weight;
            if (roll < 0) {
                return entry;
            }
        }
        return table.get(table.size() - 1);
    }

    // The id a reward is remembered by: its gun, killstreak weapon or item id.
    private static String key(RewardEntry entry) {
        return entry.gun != null ? entry.gun : entry.weapon != null ? entry.weapon : entry.item;
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
