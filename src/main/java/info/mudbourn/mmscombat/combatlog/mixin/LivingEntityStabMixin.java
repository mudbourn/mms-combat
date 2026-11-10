package info.mudbourn.mmscombat.combatlog.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import info.mudbourn.mmscombat.combatlog.PvpEnchants;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

// Marks a spear stab or charge at another player as a player-vs-player hit.
@Mixin(LivingEntity.class)
public abstract class LivingEntityStabMixin {

    @WrapMethod(method = "stabAttack")
    private boolean mmsCombat$pvpStab(
        EquipmentSlot slot,
        Entity target,
        float damage,
        boolean dealDamage,
        boolean knockback,
        boolean dismount,
        Operation<Boolean> original
    ) {
        UUID[] previous = PvpEnchants.enter((LivingEntity) (Object) this, target);
        try {
            return original.call(slot, target, damage, dealDamage, knockback, dismount);
        } finally {
            PvpEnchants.exit(previous);
        }
    }
}
