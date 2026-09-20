package info.mudbourn.mmscombat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.mudbourn.mmscombat.MmsCombat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

// The one JSON config for every subsystem, loaded once at startup and re-savable to backfill new defaults.
public final class CombatConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CombatConfig instance = new CombatConfig();

    public int combatTicks = 600;
    public boolean countPvE = false;
    public int bodyLingerTicks = 600;
    public boolean bodyForceLoad = true;
    public int hudUpdateInterval = 10;

    public boolean randomKillProtection = true;
    public int rkpFreeHits = 1;
    public int rkpWeaknessTicks = 200;
    public int rkpWeaknessAmplifier = 0;
    public int rkpTeleportBlocks = 15;
    public int rkpWindowTicks = 200;

    public int streakDecayTicks = 1200;
    public int streakDecayAmount = 1;

    public List<StreakTier> streakTiers = defaultTiers();

    public static CombatConfig get() {
        return instance;
    }

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                CombatConfig loaded = GSON.fromJson(Files.readString(path), CombatConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                MmsCombat.LOG.error("Failed to read {}, using defaults", path, e);
            }
        }
        instance.normalize();
        save();
    }

    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            MmsCombat.LOG.error("Failed to write {}", path, e);
        }
    }

    private void normalize() {
        combatTicks = Math.max(20, combatTicks);
        bodyLingerTicks = Math.max(600, bodyLingerTicks);
        hudUpdateInterval = Math.max(1, hudUpdateInterval);
        rkpFreeHits = Math.max(1, rkpFreeHits);
        rkpWeaknessTicks = Math.max(1, rkpWeaknessTicks);
        rkpWeaknessAmplifier = Math.max(0, rkpWeaknessAmplifier);
        rkpTeleportBlocks = Math.max(1, rkpTeleportBlocks);
        rkpWindowTicks = Math.max(1, rkpWindowTicks);
        streakDecayTicks = Math.max(0, streakDecayTicks);
        streakDecayAmount = Math.max(1, streakDecayAmount);
        if (streakTiers == null || streakTiers.isEmpty()) {
            streakTiers = defaultTiers();
        }
        streakTiers.sort((a, b) -> Integer.compare(a.kills, b.kills));
    }

    // The linger window a fresh body gets: the configured floor, never shorter than the combat still owed.
    public int resolveLinger(int remainingCombatTicks) {
        return Math.max(bodyLingerTicks, remainingCombatTicks);
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(MmsCombat.MOD_ID + ".json");
    }

    private static List<StreakTier> defaultTiers() {
        List<StreakTier> tiers = new ArrayList<>();
        tiers.add(StreakTier.of(3, RewardEntry.of("minecraft:golden_apple", 2, 1)));
        tiers.add(StreakTier.of(5, RewardEntry.of("minecraft:diamond", 3, 1)));
        tiers.add(StreakTier.of(8, RewardEntry.weapon("assault_rifle", 1),
            RewardEntry.of("minecraft:diamond_block", 1, 1)));
        tiers.add(StreakTier.of(12, RewardEntry.weapon("mjolnir", 2),
            RewardEntry.of("minecraft:netherite_ingot", 1, 1)));
        return tiers;
    }

    // One killstreak tier: the kill count that triggers it and the weighted table it draws one reward from.
    public static final class StreakTier {
        public int kills;
        public List<RewardEntry> rewardTable = new ArrayList<>();

        public static StreakTier of(int kills, RewardEntry... entries) {
            StreakTier tier = new StreakTier();
            tier.kills = kills;
            tier.rewardTable = new ArrayList<>(List.of(entries));
            return tier;
        }
    }

    // One weighted reward: either a plain registry id (with count) or a killstreak weapon key, plus its relative draw weight.
    public static final class RewardEntry {
        public String item;
        public String weapon;
        public int count = 1;
        public int weight = 1;

        public static RewardEntry of(String item, int count, int weight) {
            RewardEntry entry = new RewardEntry();
            entry.item = item;
            entry.count = count;
            entry.weight = weight;
            return entry;
        }

        public static RewardEntry weapon(String weapon, int weight) {
            RewardEntry entry = new RewardEntry();
            entry.weapon = weapon;
            entry.weight = weight;
            return entry;
        }
    }
}
