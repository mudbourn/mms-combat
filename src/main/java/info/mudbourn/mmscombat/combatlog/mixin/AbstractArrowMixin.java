package info.mudbourn.mmscombat.combatlog.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import info.mudbourn.mmscombat.combatlog.PvpEnchants;
import java.util.UUID;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;

// Marks a player's arrow or trident striking another player as a player-vs-player hit.
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @WrapMethod(method = "onHitEntity")
    private void mmsCombat$pvpArrowHit(EntityHitResult result, Operation<Void> original) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        UUID[] previous = PvpEnchants.enter(arrow.getOwner(), result.getEntity());
        try {
            original.call(result);
        } finally {
            PvpEnchants.exit(previous);
        }
    }
}
