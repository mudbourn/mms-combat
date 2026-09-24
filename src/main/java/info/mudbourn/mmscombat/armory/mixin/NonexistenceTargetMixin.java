package info.mudbourn.mmscombat.armory.mixin;

import info.mudbourn.mmscombat.armory.ArmoryEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Stops mobs from targeting a Non-Existence bearer.
@Mixin(Mob.class)
public abstract class NonexistenceTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mmsCombat$ignoreNonexistent(LivingEntity target, CallbackInfo ci) {
        if (target != null && target.hasEffect(ArmoryEffects.NONEXISTENCE)) {
            ci.cancel();
        }
    }
}
