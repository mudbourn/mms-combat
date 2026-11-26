package info.mudbourn.mmscombat.roll.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import info.mudbourn.mmscombat.roll.RollLockout;
import info.mudbourn.mmscombat.roll.RollTuning;
import net.combat_roll.internals.RollManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Lengthens the roll, which also stretches its animation, and hides it from Combat Roll's action lockout.
@Mixin(value = RollManager.class, remap = false)
public abstract class RollDurationMixin {

    @ModifyReturnValue(method = "rollDuration", at = @At("RETURN"))
    private static int mmsCombat$longerRoll(int original) {
        return RollTuning.ROLL_TICKS;
    }

    @ModifyReturnValue(method = "isRolling", at = @At("RETURN"))
    private boolean mmsCombat$openLockout(boolean original) {
        return original && !RollLockout.bypassed();
    }
}
