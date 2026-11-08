package info.mudbourn.mmscombat.compat.revive;

import goetic.mods.absolutrevive.common.EventHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Lets every damage source reach a downed player, not only attacks with a source entity.
@Mixin(value = EventHandler.class, remap = false)
public abstract class DownedDamageMixin {

    @Inject(method = "lambda$registerServerEvents$5", at = @At("HEAD"), cancellable = true)
    private static void mmsCombat$allowAllDamage(
        LivingEntity entity,
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof Player player && EventHandler.isUnconscious(player)) {
            cir.setReturnValue(true);
        }
    }
}
