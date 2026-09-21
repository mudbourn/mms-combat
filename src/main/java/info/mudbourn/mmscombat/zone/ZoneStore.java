package info.mudbourn.mmscombat.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import info.mudbourn.mmscombat.MmsCombat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

// The per-save registry of zones, kept in memory and mirrored to a JSON file in the world folder so it survives restarts and never leaks between saves.
public final class ZoneStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Zone> ZONES = new ArrayList<>();
    private static Path filePath;

    private ZoneStore() {
    }

    public static void load(MinecraftServer server) {
        ZONES.clear();
        filePath = server.getWorldPath(LevelResource.ROOT).resolve(MmsCombat.MOD_ID + "_zones.json");
        if (Files.exists(filePath)) {
            try {
                List<Zone> loaded = GSON.fromJson(Files.readString(filePath),
                    new TypeToken<List<Zone>>() { }.getType());
                if (loaded != null) {
                    ZONES.addAll(loaded);
                }
            } catch (IOException | RuntimeException e) {
                MmsCombat.LOG.error("Failed to read zones from {}", filePath, e);
            }
        }
    }

    public static void save() {
        if (filePath == null) {
            return;
        }
        try {
            Files.writeString(filePath, GSON.toJson(ZONES));
        } catch (IOException e) {
            MmsCombat.LOG.error("Failed to write zones to {}", filePath, e);
        }
    }

    public static List<Zone> all() {
        return ZONES;
    }

    public static Zone byName(String name) {
        for (Zone zone : ZONES) {
            if (zone.name.equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    public static boolean add(Zone zone) {
        if (byName(zone.name) != null) {
            return false;
        }
        ZONES.add(zone);
        save();
        return true;
    }

    public static boolean remove(String name) {
        boolean removed = ZONES.removeIf(zone -> zone.name.equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    public static boolean flagsCombatOnEntry(ServerLevel level, BlockPos pos) {
        return matches(level, pos, true);
    }

    public static boolean suppressesSpawn(ServerLevel level, BlockPos pos) {
        return matches(level, pos, false);
    }

    // Whether an explosion centred at this position sits in a zone that shields terrain from explosions.
    public static boolean shieldsExplosion(ServerLevel level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        for (Zone zone : ZONES) {
            if (zone.blockExplosionShield && zone.contains(dim, pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ServerLevel level, BlockPos pos, boolean combat) {
        String dim = level.dimension().identifier().toString();
        for (Zone zone : ZONES) {
            if (zone.contains(dim, pos.getX(), pos.getY(), pos.getZ())
                && (combat ? zone.flagCombatOnEnter : zone.suppressNaturalSpawns)) {
                return true;
            }
        }
        return false;
    }
}
