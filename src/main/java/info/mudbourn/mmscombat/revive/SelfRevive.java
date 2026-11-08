package info.mudbourn.mmscombat.revive;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.network.AbsolutReviveNetworking;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.compat.revive.PlayerDamageModelAccessor;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.SelfRevivePayload;
import info.mudbourn.mmscombat.net.SelfReviveStatusPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

// Per-player self-revive progress and the per-life use budget.
public final class SelfRevive {

    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> USES = new HashMap<>();
    private static final Map<UUID, List<Integer>> STRAINS = new HashMap<>();
    private static final Map<UUID, UUID> LAST_ENEMY = new HashMap<>();
    private static final int LETHAL_STRAINS = 3;
    private static final ResourceKey<DamageType> SUCCUMB = damageType("succumb");
    private static final ResourceKey<DamageType> SUCCUMB_FIGHTING = damageType("succumb_fighting");
    private static final int MIN_DEATH_TICKS = 20;
    private static final int STATUS_INTERVAL = 10;

    private SelfRevive() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SelfRevivePayload.TYPE, (payload, context) ->
            hold(context.player(), payload.held()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
            Entity attacker = source.getEntity();
            if (entity instanceof ServerPlayer victim
                && attacker != victim
                && (attacker instanceof Player || attacker instanceof Enemy)) {
                LAST_ENEMY.put(victim.getUUID(), attacker.getUUID());
            }
        });
    }

    private static ResourceKey<DamageType> damageType(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, name));
    }

    private static void hold(ServerPlayer player, boolean held) {
        UUID id = player.getUUID();
        if (!held) {
            cancel(id);
            return;
        }
        if (!(CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model)
            || !model.isUnconscious()
            || !model.canBeRescued()
            || PROGRESS.containsKey(id)) {
            return;
        }
        PROGRESS.put(id, 0);
        STRAINS.put(id, rollStrains(id, player.getRandom()));
    }

    // Picks the ticks of this attempt where the effort hurts: each earlier get-up adds a roll and raises the odds, and the last allowed one and the fatal one beyond it strain on every roll.
    private static List<Integer> rollStrains(UUID id, RandomSource random) {
        int total = Math.max(1, CombatConfig.get().selfReviveUses);
        int used = USES.getOrDefault(id, 0);
        int rolls = used >= total ? LETHAL_STRAINS : used + 1;
        double chance = used >= total - 1 ? 1 : 0.5 + 0.5 * used / (total - 1);
        int duration = CombatConfig.get().selfReviveTicks;
        List<Integer> strains = new ArrayList<>();
        for (int roll = 0; roll < rolls; roll++) {
            if (random.nextDouble() >= chance) {
                continue;
            }
            int tick;
            do {
                tick = Mth.nextInt(random, duration * 15 / 100, duration * 90 / 100);
            } while (strains.contains(tick));
            strains.add(tick);
        }
        strains.sort(null);
        return strains;
    }

    // Bleeds the player out, naming the enemy they were fighting when one is still around.
    private static void succumb(ServerPlayer player) {
        ServerLevel level = player.level();
        UUID enemyId = LAST_ENEMY.get(player.getUUID());
        Entity enemy = enemyId == null || !CombatManager.get().isFlagged(player) ? null : level.getEntity(enemyId);
        var types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        DamageSource source = enemy == null
            ? new DamageSource(types.getOrThrow(SUCCUMB))
            : new DamageSource(types.getOrThrow(SUCCUMB_FIGHTING), enemy);
        player.hurtServer(level, source, Float.MAX_VALUE);
    }

    // Makes the downed player flinch as if hurt and takes time off their death countdown.
    private static void strain(ServerPlayer player, PlayerDamageModel model) {
        player.level().broadcastDamageEvent(player, player.damageSources().generic());
        PlayerDamageModelAccessor countdown = (PlayerDamageModelAccessor) model;
        int left = countdown.mmsCombat$getUnconsciousTicks() - CombatConfig.get().selfReviveStrainTicks;
        countdown.mmsCombat$setUnconsciousTicks(Math.max(MIN_DEATH_TICKS, left));
        AbsolutReviveNetworking.sendDamageModelSync(player, model, false);
    }

    private static void cancel(UUID id) {
        PROGRESS.remove(id);
        STRAINS.remove(id);
    }

    public static void tick(ServerPlayer player, PlayerDamageModel model) {
        UUID id = player.getUUID();
        if (!model.isUnconscious() || !model.canBeRescued()) {
            cancel(id);
            return;
        }
        if (!PROGRESS.containsKey(id)) {
            if (player.tickCount % STATUS_INTERVAL == 0) {
                sendStatus(player, usesLeft(id) <= 0, -1, CombatConfig.get().selfReviveTicks);
            }
            return;
        }
        int ticks = PROGRESS.merge(id, 1, Integer::sum);
        int duration = CombatConfig.get().selfReviveTicks;
        List<Integer> strains = STRAINS.getOrDefault(id, List.of());
        if (strains.contains(ticks)) {
            strain(player, model);
            if (usesLeft(id) <= 0 && ticks == strains.get(strains.size() - 1)) {
                cancel(id);
                succumb(player);
                return;
            }
        }
        if (ticks < duration) {
            sendStatus(player, usesLeft(id) <= 0, ticks, duration);
            return;
        }
        cancel(id);
        if (model.rescueFromCriticalState(player)) {
            USES.merge(id, 1, Integer::sum);
            if (usesLeft(id) <= 0) {
                showStatus(player, "Your injuries are very severe. Be careful.", ChatFormatting.RED);
            } else {
                showStatus(player, "You got back up.", ChatFormatting.GREEN);
            }
        }
    }

    private static int usesLeft(UUID id) {
        return CombatConfig.get().selfReviveUses - USES.getOrDefault(id, 0);
    }

    private static void sendStatus(ServerPlayer player, boolean severe, int ticks, int duration) {
        ServerPlayNetworking.send(player, new SelfReviveStatusPayload(severe, ticks, duration));
    }

    private static void showStatus(ServerPlayer player, String text, ChatFormatting color) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(text).withStyle(color)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }

    public static void reset(ServerPlayer player) {
        UUID id = player.getUUID();
        cancel(id);
        USES.remove(id);
        LAST_ENEMY.remove(id);
    }
}
