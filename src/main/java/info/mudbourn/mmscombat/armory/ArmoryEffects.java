package info.mudbourn.mmscombat.armory;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.net.NonexistencePayload;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

// The Non-Existence effect and the rules for granting and breaking it.
public final class ArmoryEffects {

    public static final Holder<MobEffect> NONEXISTENCE = Registry.registerForHolder(
        BuiltInRegistries.MOB_EFFECT,
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "nonexistence"),
        new NonexistenceEffect()
    );

    private static final int SELF_WITHER_TICKS = 120;
    private static final int TARGET_WITHER_TICKS = 240;
    private static final double FORGET_RANGE = 32.0;

    private ArmoryEffects() {
    }

    public static void register() {
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living && living.hasEffect(NONEXISTENCE)) {
                ServerPlayNetworking.send(player, new NonexistencePayload(living.getId(), true));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.hasEffect(NONEXISTENCE)) {
                sender.sendPacket(new NonexistencePayload(handler.player.getId(), true));
            }
        });
    }

    // Tells the bearer and every client tracking it whether to draw the bearer at all.
    public static void syncHidden(LivingEntity entity, boolean hidden) {
        if (entity.level().isClientSide()) {
            return;
        }
        NonexistencePayload payload = new NonexistencePayload(entity.getId(), hidden);
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    // Applies Non-Existence and makes nearby mobs drop the bearer as a target.
    public static void grantNonexistence(LivingEntity entity, int ticks) {
        entity.addEffect(new MobEffectInstance(NONEXISTENCE, ticks, 0, false, false, true));
        for (Mob mob : entity.level().getEntitiesOfClass(
            Mob.class,
            entity.getBoundingBox().inflate(FORGET_RANGE),
            mob -> mob.getTarget() == entity
        )) {
            mob.setTarget(null);
        }
    }

    // Ends Non-Existence early, withering the bearer.
    public static void breakNonexistence(LivingEntity entity) {
        if (entity.hasEffect(NONEXISTENCE)) {
            entity.removeEffect(NONEXISTENCE);
            punish(entity);
        }
    }

    // Ends Non-Existence because the bearer struck a target, withering the target instead.
    public static void strikeFromNonexistence(LivingEntity attacker, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, TARGET_WITHER_TICKS, 1), attacker);
        attacker.removeEffect(NONEXISTENCE);
        playFade(attacker);
    }

    static void punish(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.WITHER, SELF_WITHER_TICKS, 0));
        playFade(entity);
    }

    private static void playFade(LivingEntity entity) {
        entity.level().playSound(
            null,
            entity.blockPosition(),
            ArmorySounds.EDGE_OF_EXISTENCE_DEACTIVATE,
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
    }
}
