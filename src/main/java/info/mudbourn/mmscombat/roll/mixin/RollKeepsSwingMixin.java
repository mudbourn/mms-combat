package info.mudbourn.mmscombat.roll.mixin;

import net.combat_roll.compatibility.BetterCombatHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keeps a Better Combat swing going when the player rolls.
@Mixin(value = BetterCombatHelper.class, remap = false)
public abstract class RollKeepsSwingMixin {

    @Inject(method = "cancelUpswing", at = @At("HEAD"), cancellable = true)
    private static void mmsCombat$keepSwing(CallbackInfo ci) {
        ci.cancel();
    }
}
