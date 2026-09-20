package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.config.CombatConfig.StreakTier;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

// Per-life kill tracking: a player-kills-player death advances the killer's streak, hitting a configured tier spawns a killstreak crate, and death or logout resets the count.
public final class StreakManager {

    private static final StreakManager INSTANCE = new StreakManager();

    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Long> lastKillTick = new HashMap<>();
    private final Set<UUID> flaggedAtDeath = new HashSet<>();

    private StreakManager() {
    }

    public static StreakManager get() {
        return INSTANCE;
    }

    public int getStreak(ServerPlayer player) {
        return streaks.getOrDefault(player.getUUID(), 0);
    }

    // Seconds until this player's streak next decays, or 0 when they hold no streak or decay is disabled.
    public int decaySecondsLeft(ServerPlayer player) {
        int decayTicks = CombatConfig.get().streakDecayTicks;
        if (decayTicks <= 0 || !streaks.containsKey(player.getUUID())) {
            return 0;
        }
        long last = lastKillTick.getOrDefault(player.getUUID(), player.level().getGameTime());
        long remaining = decayTicks - (player.level().getGameTime() - last);
        return (int) Math.ceil(Math.max(0, remaining) / 20.0);
    }

    // Sets a player's streak directly and fires the tier reward if the new count lands on one, so a tier can be tested without grinding kills.
    public void setStreak(ServerPlayer player, int count) {
        if (count <= 0) {
            streaks.remove(player.getUUID());
            lastKillTick.remove(player.getUUID());
            return;
        }
        streaks.put(player.getUUID(), count);
        lastKillTick.put(player.getUUID(), player.level().getGameTime());
        StreakTier tier = tierFor(count);
        if (tier != null) {
            awardTier(player, count, tier);
        }
    }

    public StreakTier tierByKills(int kills) {
        return tierFor(kills);
    }

    // Spawns a crate for an arbitrary tier, for testing the reward flow on demand.
    public void spawnReward(ServerPlayer player, StreakTier tier) {
        awardTier(player, tier.kills, tier);
    }

    public static void register() {
        // Snapshots whether the dying player was combat logged before the death clears their flag, so streaks only count kills on flagged victims.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim) {
                if (CombatManager.get().isFlagged(victim)) {
                    INSTANCE.flaggedAtDeath.add(victim.getUUID());
                } else {
                    INSTANCE.flaggedAtDeath.remove(victim.getUUID());
                }
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim) {
                INSTANCE.onPlayerDeath(victim, source.getEntity());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            INSTANCE.streaks.remove(handler.player.getUUID());
            INSTANCE.lastKillTick.remove(handler.player.getUUID());
            INSTANCE.flaggedAtDeath.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE::tick);
    }

    private void onPlayerDeath(ServerPlayer victim, net.minecraft.world.entity.Entity killer) {
        boolean victimWasFlagged = flaggedAtDeath.remove(victim.getUUID());
        streaks.remove(victim.getUUID());
        lastKillTick.remove(victim.getUUID());
        if (victimWasFlagged && killer instanceof ServerPlayer aggressor && aggressor != victim) {
            int streak = streaks.merge(aggressor.getUUID(), 1, Integer::sum);
            lastKillTick.put(aggressor.getUUID(), aggressor.level().getGameTime());
            StreakTier tier = tierFor(streak);
            if (tier != null) {
                awardTier(aggressor, streak, tier);
            }
        }
    }

    // Erodes idle streaks: a player who has not killed a flagged victim within the decay window loses the configured amount each interval, so a streak takes sustained effort to keep.
    private void tick(MinecraftServer server) {
        int decayTicks = CombatConfig.get().streakDecayTicks;
        if (decayTicks <= 0 || streaks.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        int amount = CombatConfig.get().streakDecayAmount;
        Iterator<Map.Entry<UUID, Integer>> it = streaks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            long last = lastKillTick.getOrDefault(entry.getKey(), now);
            if (now - last < decayTicks) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            int reduced = entry.getValue() - amount;
            if (reduced <= 0) {
                it.remove();
                lastKillTick.remove(entry.getKey());
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Your kill streak faded away."));
                }
            } else {
                entry.setValue(reduced);
                lastKillTick.put(entry.getKey(), now);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Your kill streak decayed to " + reduced + "."));
                }
            }
        }
    }

    private void awardTier(ServerPlayer player, int streak, StreakTier tier) {
        java.util.List<ItemStack> rewards = RewardPool.roll(tier, player);
        announceStreak(player, streak);
        if (rewards.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        KillstreakCrateEntity crate = new KillstreakCrateEntity(MmsCombatRegistries.KILLSTREAK_CRATE, level);
        crate.setPos(player.getX(), player.getY() + 1.0, player.getZ());
        crate.setOwner(player.getUUID());
        crate.giveRewards(rewards);
        level.addFreshEntity(crate);
    }

    // Broadcasts the streak to everyone in chat and rolls a dragon growl out to every player, wherever they are.
    private void announceStreak(ServerPlayer player, int streak) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Component message = Component.literal(player.getName().getString() + " is on a " + streak + " kill streak!");
        Holder<SoundEvent> growl = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ENDER_DRAGON_GROWL);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            viewer.sendSystemMessage(message);
            viewer.connection.send(new ClientboundSoundPacket(
                growl,
                SoundSource.MASTER,
                viewer.getX(),
                viewer.getY(),
                viewer.getZ(),
                1.0F,
                1.0F,
                viewer.getRandom().nextLong()));
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
}
