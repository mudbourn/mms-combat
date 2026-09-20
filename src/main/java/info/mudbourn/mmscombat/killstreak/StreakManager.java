package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// Per-life kill tracking: a player-kills-player death advances the killer's streak, hitting a configured tier spawns a follower chest, and death or logout resets the count.
public final class StreakManager {

    private static final StreakManager INSTANCE = new StreakManager();

    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final List<FollowerChest> chests = new ArrayList<>();

    private StreakManager() {
    }

    public static StreakManager get() {
        return INSTANCE;
    }

    public int getStreak(ServerPlayer player) {
        return streaks.getOrDefault(player.getUUID(), 0);
    }

    // Sets a player's streak directly and fires the tier reward if the new count lands on one, so a tier can be tested without grinding kills.
    public void setStreak(ServerPlayer player, int count) {
        if (count <= 0) {
            streaks.remove(player.getUUID());
            return;
        }
        streaks.put(player.getUUID(), count);
        StreakTier tier = tierFor(count);
        if (tier != null) {
            awardTier(player, count, tier);
        }
    }

    public StreakTier tierByKills(int kills) {
        return tierFor(kills);
    }

    // Spawns a follower chest for an arbitrary tier, for testing the reward flow on demand.
    public void spawnReward(ServerPlayer player, StreakTier tier) {
        awardTier(player, tier.kills, tier);
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim) {
                INSTANCE.onPlayerDeath(victim, source.getEntity());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            INSTANCE.streaks.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE::tick);
    }

    private void onPlayerDeath(ServerPlayer victim, net.minecraft.world.entity.Entity killer) {
        streaks.remove(victim.getUUID());
        if (killer instanceof ServerPlayer aggressor && aggressor != victim) {
            int streak = streaks.merge(aggressor.getUUID(), 1, Integer::sum);
            StreakTier tier = tierFor(streak);
            if (tier != null) {
                awardTier(aggressor, streak, tier);
            }
        }
    }

    private void awardTier(ServerPlayer player, int streak, StreakTier tier) {
        ItemStack reward = RewardPool.roll(tier, player.level().getRandom());
        player.sendSystemMessage(Component.literal(streak + " kill streak!"));
        if (!reward.isEmpty()) {
            chests.add(FollowerChest.spawn(player, reward));
        }
    }

    private StreakTier tierFor(int streak) {
        for (StreakTier tier : CombatConfig.get().streakTiers) {
            if (tier.kills == streak) {
                return tier;
            }
        }
        return null;
    }

    private void tick(MinecraftServer server) {
        if (chests.isEmpty()) {
            return;
        }
        Iterator<FollowerChest> it = chests.iterator();
        while (it.hasNext()) {
            if (it.next().tick(server)) {
                it.remove();
            }
        }
    }
}
