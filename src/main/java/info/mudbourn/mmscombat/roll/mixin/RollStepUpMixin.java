package info.mudbourn.mmscombat.roll.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import info.mudbourn.mmscombat.roll.RollTuning;
import net.combat_roll.internals.RollingEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Carries a rolling player up a full block.
@Mixin(LivingEntity.class)
public abstract class RollStepUpMixin {

    @ModifyReturnValue(method = "maxUpStep", at = @At("RETURN"))
    private float mmsCombat$rollStepUp(float original) {
        Object self = this;
        boolean rolling = self instanceof RollingEntity entity && entity.getRollManager().isRolling()
            || RollTuning.rolling((LivingEntity) self);
        return rolling ? Math.max(original, RollTuning.ROLL_STEP_HEIGHT) : original;
    }
}
