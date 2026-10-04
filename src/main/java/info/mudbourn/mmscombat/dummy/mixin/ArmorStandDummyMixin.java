package info.mudbourn.mmscombat.dummy.mixin;

import info.mudbourn.mmscombat.dummy.TrainingDummy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Routes every hit on a training dummy to TrainingDummy and sizes its hitbox to the dummy model; /kill still removes it.
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

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void mmsCombat$dummyDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (TrainingDummy.isDummy((ArmorStand) (Object) this)) {
            cir.setReturnValue(TrainingDummy.DIMENSIONS);
        }
    }

    @Inject(method = "setItemSlot", at = @At("TAIL"))
    private void mmsCombat$resizeOnHeadChange(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (slot == EquipmentSlot.HEAD) {
            ((ArmorStand) (Object) this).refreshDimensions();
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mmsCombat$resizeOnLoad(ValueInput input, CallbackInfo ci) {
        ((ArmorStand) (Object) this).refreshDimensions();
    }
}
