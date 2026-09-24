package info.mudbourn.mmscombat.armory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

// Hides its bearer from sight and mobs; running out withers the bearer.
public class NonexistenceEffect extends MobEffect {

    public NonexistenceEffect() {
        super(MobEffectCategory.NEUTRAL, 0x530000);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        ArmoryEffects.punish(entity);
        return true;
    }
}
