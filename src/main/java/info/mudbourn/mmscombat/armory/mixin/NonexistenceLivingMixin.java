package info.mudbourn.mmscombat.armory.mixin;

import info.mudbourn.mmscombat.armory.ArmoryEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

// Keeps a Non-Existence bearer invisible and tells clients when to stop or resume drawing it.
@Mixin(LivingEntity.class)
public abstract class NonexistenceLivingMixin {

    @Inject(method = "updateInvisibilityStatus", at = @At("RETURN"))
    private void mmsCombat$hideNonexistent(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.hasEffect(ArmoryEffects.NONEXISTENCE)) {
            self.setInvisible(true);
        }
    }

    @Inject(method = "onEffectAdded", at = @At("RETURN"))
    private void mmsCombat$syncAdded(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        if (effect.is(ArmoryEffects.NONEXISTENCE)) {
            ArmoryEffects.syncHidden((LivingEntity) (Object) this, true);
        }
    }

    @Inject(method = "onEffectsRemoved", at = @At("RETURN"))
    private void mmsCombat$syncRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        for (MobEffectInstance effect : effects) {
            if (effect.is(ArmoryEffects.NONEXISTENCE)) {
                ArmoryEffects.syncHidden((LivingEntity) (Object) this, false);
                return;
            }
        }
    }
}
