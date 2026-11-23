package info.mudbourn.mmscombat.combatlog.mixin;

import info.mudbourn.mmscombat.killstreak.Perishable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Killstreak crate weapons ignore armor: their hits skip the armor step entirely, so the target's
// armor neither reduces the damage nor takes durability damage from it.
@Mixin(LivingEntity.class)
public abstract class KillstreakArmorBypassMixin {

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void mmsCombat$killstreakBypassesArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (killstreakWeapon(source)) {
            cir.setReturnValue(amount);
        }
    }

    private static boolean killstreakWeapon(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (isKillstreak(weapon)) {
            return true;
        }
        // Guns and other hits that carry no weapon item: the attacker's held crate weapon.
        return source.getEntity() instanceof LivingEntity attacker && isKillstreak(attacker.getMainHandItem());
    }

    private static boolean isKillstreak(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(Perishable.OWNER) && !stack.has(DataComponents.CONSUMABLE);
    }
}
