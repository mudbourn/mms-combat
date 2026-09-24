package info.mudbourn.mmscombat.armory.mixin;

import info.mudbourn.mmscombat.armory.ArmoryEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Silences the footsteps, swimming and sprint dust of a Non-Existence bearer.
@Mixin(Entity.class)
public abstract class NonexistenceSoundMixin {

    @Inject(
        method = {
            "playStepSound",
            "playMuffledStepSound",
            "waterSwimSound",
            "spawnSprintParticle"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmsCombat$silenceNonexistent(CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity living && living.hasEffect(ArmoryEffects.NONEXISTENCE)) {
            ci.cancel();
        }
    }
}
