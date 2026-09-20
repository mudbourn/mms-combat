package info.mudbourn.mmscombat.killstreak;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.config.CombatConfig.RewardEntry;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import java.util.Comparator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// The op-gated /mmscombat streaks subtree for editing tiers, their reward items, and the decay window at runtime.
public final class StreakCommands {

    private StreakCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> streaksSubcommand() {
        return Commands.literal("streaks")
            .then(Commands.literal("list")
                .executes(StreakCommands::list))
            .then(Commands.literal("addtier")
                .then(Commands.argument("kills", IntegerArgumentType.integer(1))
                    .executes(StreakCommands::addTier)))
            .then(Commands.literal("removetier")
                .then(Commands.argument("kills", IntegerArgumentType.integer(1))
                    .executes(StreakCommands::removeTier)))
            .then(Commands.literal("additem")
                .then(Commands.argument("kills", IntegerArgumentType.integer(1))
                    .then(Commands.argument("item", StringArgumentType.string())
                        .executes(ctx -> addItem(ctx, 1, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(ctx -> addItem(ctx, IntegerArgumentType.getInteger(ctx, "count"), 1))
                            .then(Commands.argument("weight", IntegerArgumentType.integer(1))
                                .executes(ctx -> addItem(
                                    ctx,
                                    IntegerArgumentType.getInteger(ctx, "count"),
                                    IntegerArgumentType.getInteger(ctx, "weight"))))))))
            .then(Commands.literal("removeitem")
                .then(Commands.argument("kills", IntegerArgumentType.integer(1))
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes(StreakCommands::removeItem))))
            .then(Commands.literal("decay")
                .executes(StreakCommands::showDecay)
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                    .executes(StreakCommands::setDecay)));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var tiers = CombatConfig.get().streakTiers;
        ctx.getSource().sendSuccess(
            () -> Component.literal("Streak tiers (" + tiers.size() + "):"), false);
        for (StreakTier tier : tiers) {
            StringBuilder line = new StringBuilder("  " + tier.kills + " kills:");
            if (tier.rewardTable.isEmpty()) {
                line.append(" (no rewards)");
            }
            for (int i = 0; i < tier.rewardTable.size(); i++) {
                RewardEntry entry = tier.rewardTable.get(i);
                String name = entry.weapon != null ? "weapon:" + entry.weapon : entry.item;
                line.append(" [").append(i).append("] ")
                    .append(name)
                    .append(" x").append(entry.count)
                    .append(" w").append(entry.weight);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
        }
        return 1;
    }

    private static int addTier(CommandContext<CommandSourceStack> ctx) {
        int kills = IntegerArgumentType.getInteger(ctx, "kills");
        if (findTier(kills) != null) {
            ctx.getSource().sendFailure(Component.literal("A tier already exists at " + kills + " kills."));
            return 0;
        }
        CombatConfig.get().streakTiers.add(StreakTier.of(kills));
        sortAndSave();
        ctx.getSource().sendSuccess(
            () -> Component.literal("Added an empty tier at " + kills + " kills."), true);
        return 1;
    }

    private static int removeTier(CommandContext<CommandSourceStack> ctx) {
        int kills = IntegerArgumentType.getInteger(ctx, "kills");
        StreakTier tier = findTier(kills);
        if (tier == null) {
            ctx.getSource().sendFailure(Component.literal("No tier at " + kills + " kills."));
            return 0;
        }
        CombatConfig.get().streakTiers.remove(tier);
        sortAndSave();
        ctx.getSource().sendSuccess(
            () -> Component.literal("Removed the tier at " + kills + " kills."), true);
        return 1;
    }

    private static int addItem(CommandContext<CommandSourceStack> ctx, int count, int weight) {
        int kills = IntegerArgumentType.getInteger(ctx, "kills");
        String item = StringArgumentType.getString(ctx, "item");
        Identifier id = Identifier.tryParse(item);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Malformed item id: " + item));
            return 0;
        }
        StreakTier tier = findTier(kills);
        if (tier == null) {
            ctx.getSource().sendFailure(Component.literal("No tier at " + kills + " kills; add one first."));
            return 0;
        }
        boolean known = BuiltInRegistries.ITEM.getOptional(id).isPresent();
        tier.rewardTable.add(RewardEntry.of(item, count, weight));
        sortAndSave();
        String note = known ? "" : " (warning: not currently registered)";
        ctx.getSource().sendSuccess(
            () -> Component.literal("Added " + item + " x" + count + " w" + weight
                + " to the " + kills + "-kill tier." + note), true);
        return 1;
    }

    private static int removeItem(CommandContext<CommandSourceStack> ctx) {
        int kills = IntegerArgumentType.getInteger(ctx, "kills");
        int index = IntegerArgumentType.getInteger(ctx, "index");
        StreakTier tier = findTier(kills);
        if (tier == null) {
            ctx.getSource().sendFailure(Component.literal("No tier at " + kills + " kills."));
            return 0;
        }
        if (index >= tier.rewardTable.size()) {
            ctx.getSource().sendFailure(Component.literal("No reward at index " + index + " in that tier."));
            return 0;
        }
        RewardEntry removed = tier.rewardTable.remove(index);
        sortAndSave();
        String name = removed.weapon != null ? "weapon:" + removed.weapon : removed.item;
        ctx.getSource().sendSuccess(
            () -> Component.literal("Removed " + name + " from the " + kills + "-kill tier."), true);
        return 1;
    }

    private static int showDecay(CommandContext<CommandSourceStack> ctx) {
        int ticks = CombatConfig.get().streakDecayTicks;
        int amount = CombatConfig.get().streakDecayAmount;
        ctx.getSource().sendSuccess(() -> Component.literal(ticks <= 0
            ? "Streak decay is disabled."
            : "Streaks decay by " + amount + " every " + (ticks / 20) + "s without a kill."), false);
        return 1;
    }

    private static int setDecay(CommandContext<CommandSourceStack> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        CombatConfig.get().streakDecayTicks = seconds * 20;
        CombatConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(seconds <= 0
            ? "Disabled streak decay."
            : "Streaks now decay every " + seconds + "s without a kill."), true);
        return 1;
    }

    private static StreakTier findTier(int kills) {
        for (StreakTier tier : CombatConfig.get().streakTiers) {
            if (tier.kills == kills) {
                return tier;
            }
        }
        return null;
    }

    private static void sortAndSave() {
        CombatConfig.get().streakTiers.sort(Comparator.comparingInt(tier -> tier.kills));
        CombatConfig.save();
    }
}
