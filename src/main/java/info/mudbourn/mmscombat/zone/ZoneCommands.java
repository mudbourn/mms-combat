package info.mudbourn.mmscombat.zone;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

// The op-gated /mmscombat zone subtree for defining, flagging, and outlining combat zones.
public final class ZoneCommands {

    private static final double SHOW_RANGE = 96.0;
    private static final int SHOW_MAX_POINTS = 600;

    // The three zone flag names, suggested for the flag argument.
    private static final List<String> FLAG_NAMES = List.of(
        "flagCombatOnEnter",
        "suppressNaturalSpawns",
        "blockExplosionShield");

    // Completes an existing zone name from the live store.
    private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (ctx, builder) ->
        SharedSuggestionProvider.suggest(ZoneStore.all().stream().map(zone -> zone.name), builder);

    // Completes a flag name.
    private static final SuggestionProvider<CommandSourceStack> FLAG_SUGGESTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(FLAG_NAMES, builder);

    private ZoneCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> zoneSubcommand() {
        return Commands.literal("zone")
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("corner1", BlockPosArgument.blockPos())
                        .then(Commands.argument("corner2", BlockPosArgument.blockPos())
                            .executes(ZoneCommands::addZone)))))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(ZONE_NAMES)
                    .executes(ZoneCommands::removeZone)))
            .then(Commands.literal("list")
                .executes(ZoneCommands::listZones))
            .then(Commands.literal("show")
                .executes(ZoneCommands::showZones))
            .then(Commands.literal("flag")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(ZONE_NAMES)
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .suggests(FLAG_SUGGESTIONS)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ZoneCommands::flagZone)))));
    }

    private static int addZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos corner1 = BlockPosArgument.getBlockPos(ctx, "corner1");
        BlockPos corner2 = BlockPosArgument.getBlockPos(ctx, "corner2");
        String dimension = ctx.getSource().getLevel().dimension().identifier().toString();
        Zone zone = new Zone(name, dimension, corner1, corner2);
        if (!ZoneStore.add(zone)) {
            ctx.getSource().sendFailure(Component.literal("A zone named " + name + " already exists."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Added zone " + zone), true);
        return 1;
    }

    private static int removeZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZoneStore.remove(name)) {
            ctx.getSource().sendFailure(Component.literal("No zone named " + name + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Removed zone " + name), true);
        return 1;
    }

    private static int listZones(CommandContext<CommandSourceStack> ctx) {
        if (ZoneStore.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No zones defined."), false);
            return 0;
        }
        for (Zone zone : ZoneStore.all()) {
            ctx.getSource().sendSuccess(() -> Component.literal(zone.toString()), false);
        }
        return ZoneStore.all().size();
    }

    private static int flagZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String flag = StringArgumentType.getString(ctx, "flag");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        Zone zone = ZoneStore.byName(name);
        if (zone == null) {
            ctx.getSource().sendFailure(Component.literal("No zone named " + name + "."));
            return 0;
        }
        if (!zone.setFlag(flag, value)) {
            ctx.getSource().sendFailure(Component.literal(
                "Unknown flag. Use flagCombatOnEnter, suppressNaturalSpawns, or blockExplosionShield."));
            return 0;
        }
        ZoneStore.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + flag + " = " + value + " on " + name), true);
        return 1;
    }

    // Draws a particle outline of every nearby zone in the caller's dimension so its bounds are visible in world.
    private static int showZones(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String dim = level.dimension().identifier().toString();
        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
        int shown = 0;
        for (Zone zone : ZoneStore.all()) {
            if (zone.dimension.equals(dim) && nearOrigin(zone, origin)) {
                outline(level, zone);
                shown++;
            }
        }
        int finalShown = shown;
        ctx.getSource().sendSuccess(() -> Component.literal("Outlined " + finalShown + " zone(s)."), false);
        return shown;
    }

    private static boolean nearOrigin(Zone zone, BlockPos origin) {
        double dx = Math.max(0, Math.max(zone.minX - origin.getX(), origin.getX() - zone.maxX));
        double dz = Math.max(0, Math.max(zone.minZ - origin.getZ(), origin.getZ() - zone.maxZ));
        return dx <= SHOW_RANGE && dz <= SHOW_RANGE;
    }

    private static void outline(ServerLevel level, Zone zone) {
        double sizeX = zone.maxX - zone.minX + 1;
        double sizeY = zone.maxY - zone.minY + 1;
        double sizeZ = zone.maxZ - zone.minZ + 1;
        double step = Math.max(1.0, (sizeX + sizeY + sizeZ) / SHOW_MAX_POINTS);
        for (double x = zone.minX; x <= zone.maxX + 1; x += step) {
            for (double y : new double[] {zone.minY, zone.maxY + 1}) {
                for (double z : new double[] {zone.minZ, zone.maxZ + 1}) {
                    spark(level, x, y, z);
                }
            }
        }
        for (double y = zone.minY; y <= zone.maxY + 1; y += step) {
            for (double x : new double[] {zone.minX, zone.maxX + 1}) {
                for (double z : new double[] {zone.minZ, zone.maxZ + 1}) {
                    spark(level, x, y, z);
                }
            }
        }
        for (double z = zone.minZ; z <= zone.maxZ + 1; z += step) {
            for (double x : new double[] {zone.minX, zone.maxX + 1}) {
                for (double y : new double[] {zone.minY, zone.maxY + 1}) {
                    spark(level, x, y, z);
                }
            }
        }
    }

    private static void spark(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
