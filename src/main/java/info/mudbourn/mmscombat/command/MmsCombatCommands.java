package info.mudbourn.mmscombat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import info.mudbourn.mmscombat.killstreak.StreakCommands;
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
            .then(StreakCommands.streaksSubcommand())
            .then(Commands.literal("duration")
                .executes(MmsCombatCommands::showDuration)
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                    .executes(MmsCombatCommands::setDuration)))
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
        // Open to every player: hold yourself in combat so others can fight you, until you turn it back off.
        dispatcher.register(Commands.literal("combatlog")
            .executes(MmsCombatCommands::combatLogToggle)
            .then(Commands.literal("on").executes(ctx -> combatLog(ctx, true)))
            .then(Commands.literal("off").executes(ctx -> combatLog(ctx, false))));
    }

    private static int combatLogToggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return combatLog(ctx, !CombatManager.get().isPersistentCombat(self(ctx)));
    }

    private static int combatLog(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
        ServerPlayer player = self(ctx);
        CombatManager.get().setPersistentCombat(player, on);
        ctx.getSource().sendSuccess(() -> Component.literal(on
            ? "PvP is on: your nametag is red and other players can fight you until you turn it off."
            : "PvP hold is off."), false);
        return 1;
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int showDuration(CommandContext<CommandSourceStack> ctx) {
        int seconds = CombatConfig.get().combatTicks / 20;
        ctx.getSource().sendSuccess(
            () -> Component.literal("Combat log duration is " + seconds + "s."), false);
        return 1;
    }

    private static int setDuration(CommandContext<CommandSourceStack> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        CombatConfig.get().combatTicks = seconds * 20;
        CombatConfig.save();
        ctx.getSource().sendSuccess(
            () -> Component.literal("Set combat log duration to " + seconds + "s."), true);
        return 1;
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
        StringBuilder names = new StringBuilder();
        for (var zone : ZoneStore.all()) {
            if (zone.contains(level.dimension(), player.getBlockX(), player.getBlockY(), player.getBlockZ())) {
                names.append(names.isEmpty() ? "" : ",").append(zone.name);
            }
        }
        return names.isEmpty() ? "none" : names.toString();
    }
}
