package info.mudbourn.mmscombat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import info.mudbourn.mmscombat.killstreak.StreakManager;
import info.mudbourn.mmscombat.zone.ZoneCommands;
import info.mudbourn.mmscombat.zone.ZoneStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// The op-gated /mmscombat root: the zone subtree plus test hooks that drive combat, streaks, and rewards without a second player.
public final class MmsCombatCommands {

    private MmsCombatCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mmscombat")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(ZoneCommands.zoneSubcommand())
            .then(Commands.literal("flag")
                .executes(ctx -> flag(ctx, self(ctx)))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ctx -> flag(ctx, EntityArgument.getPlayer(ctx, "target")))))
            .then(Commands.literal("clear")
                .executes(ctx -> clear(ctx, self(ctx)))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ctx -> clear(ctx, EntityArgument.getPlayer(ctx, "target")))))
            .then(Commands.literal("status")
                .executes(ctx -> status(ctx, self(ctx)))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "target")))))
            .then(Commands.literal("streak")
                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                    .executes(ctx -> streak(ctx, self(ctx)))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> streak(ctx, EntityArgument.getPlayer(ctx, "target"))))))
            .then(Commands.literal("reward")
                .then(Commands.argument("tierKills", IntegerArgumentType.integer(1))
                    .executes(ctx -> reward(ctx, self(ctx))))));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int flag(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatManager.get().flag(target);
        ctx.getSource().sendSuccess(
            () -> Component.literal("Flagged " + target.getName().getString() + " in combat."), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatManager.get().clearFlag(target);
        ctx.getSource().sendSuccess(
            () -> Component.literal("Cleared combat on " + target.getName().getString() + "."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        boolean flagged = CombatManager.get().isFlagged(target);
        int seconds = CombatManager.get().remainingSeconds(target);
        int streak = StreakManager.get().getStreak(target);
        String zones = zonesAt(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
            target.getName().getString()
                + " | combat=" + flagged + " (" + seconds + "s)"
                + " | streak=" + streak
                + " | zones=" + zones), false);
        return 1;
    }

    private static int streak(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        StreakManager.get().setStreak(target, count);
        ctx.getSource().sendSuccess(
            () -> Component.literal("Set streak of " + target.getName().getString() + " to " + count + "."), true);
        return 1;
    }

    private static int reward(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int kills = IntegerArgumentType.getInteger(ctx, "tierKills");
        StreakTier tier = StreakManager.get().tierByKills(kills);
        if (tier == null) {
            ctx.getSource().sendFailure(Component.literal("No tier defined at " + kills + " kills."));
            return 0;
        }
        StreakManager.get().spawnReward(target, tier);
        ctx.getSource().sendSuccess(
            () -> Component.literal("Spawned a reward chest for the " + kills + "-kill tier."), true);
        return 1;
    }

    private static String zonesAt(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return "none";
        }
        String dim = level.dimension().identifier().toString();
        StringBuilder names = new StringBuilder();
        for (var zone : ZoneStore.all()) {
            if (zone.contains(dim, player.getBlockX(), player.getBlockY(), player.getBlockZ())) {
                names.append(names.isEmpty() ? "" : ",").append(zone.name);
            }
        }
        return names.isEmpty() ? "none" : names.toString();
    }
}
