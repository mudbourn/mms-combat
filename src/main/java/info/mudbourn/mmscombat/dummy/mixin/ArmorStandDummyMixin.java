package info.mudbourn.mmscombat.dummy.mixin;

import info.mudbourn.mmscombat.dummy.TrainingDummy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Routes every hit on a training dummy to TrainingDummy, so it never breaks, burns, wobbles or makes a sound; /kill still removes it.
@Mixin(ArmorStand.class)
public class ArmorStandDummyMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void mmsCombat$absorbDummyHit(ServerLevel level, DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        ArmorStand self = (ArmorStand) (Object) this;
        if (self.getTags().contains(TrainingDummy.TAG) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            TrainingDummy.hit(level, self, source, amount);
            cir.setReturnValue(false);
        }
    }
}
