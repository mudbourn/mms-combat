package info.mudbourn.mmscombat.dummy.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Resizes an armor stand when its head slot changes, since ArmorStand no longer overrides setItemSlot.
@Mixin(LivingEntity.class)
public class LivingEntityDummyMixin {

    @Inject(method = "setItemSlot", at = @At("TAIL"))
    private void mmsCombat$resizeOnHeadChange(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (slot == EquipmentSlot.HEAD && (Object) this instanceof ArmorStand stand) {
            stand.refreshDimensions();
        }
    }
}
