package info.mudbourn.mmscombat.client.armory.mixin;

import info.mudbourn.mmscombat.client.armory.NonexistenceClientState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Skips drawing a Non-Existence bearer entirely: body, armor, held items and name tag.
@Mixin(EntityRenderDispatcher.class)
public abstract class NonexistenceRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void mmsCombat$hideNonexistent(E entity, Frustum frustum, double x, double y, double z,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (NonexistenceClientState.isHidden(entity)) {
            cir.setReturnValue(false);
        }
    }
}
