package info.mudbourn.mmscombat.armory.mixin;

import info.mudbourn.mmscombat.armory.item.CrucibleItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Lets Crucible hits ignore part of the target's armor and toughness.
@Mixin(LivingEntity.class)
public abstract class CrucibleArmorMixin {

    @ModifyArg(
        method = "getDamageAfterArmorAbsorb",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"
        ),
        index = 3
    )
    private float mmsCombat$pierceArmor(LivingEntity entity, float damage, DamageSource source, float armor, float toughness) {
        return pierced(source, armor);
    }

    @ModifyArg(
        method = "getDamageAfterArmorAbsorb",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"
        ),
        index = 4
    )
    private float mmsCombat$pierceToughness(LivingEntity entity, float damage, DamageSource source, float armor, float toughness) {
        return pierced(source, toughness);
    }

    private static float pierced(DamageSource source, float value) {
        return source.is(CrucibleItem.DAMAGE_TYPE) ? value * (1.0F - CrucibleItem.ARMOR_PIERCE) : value;
    }
}
