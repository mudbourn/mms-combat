package info.mudbourn.mmscombat.roll.mixin;

import info.mudbourn.mmscombat.roll.RollTuning;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Spreads arrows and thrown weapons fired mid-roll.
@Mixin(Projectile.class)
public abstract class RollAimMixin {

    @ModifyVariable(method = "shoot(DDDFF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float mmsCombat$rollingAim(float inaccuracy) {
        Projectile self = (Projectile) (Object) this;
        return RollTuning.rolling(self.getOwner()) ? inaccuracy + RollTuning.ROLL_INACCURACY : inaccuracy;
    }
}
