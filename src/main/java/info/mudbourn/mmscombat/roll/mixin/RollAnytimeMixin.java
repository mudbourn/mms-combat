package info.mudbourn.mmscombat.roll.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Lets Combat Roll's tryRolling start a roll mid-swing, mid-draw or while blocking.
@Mixin(value = Minecraft.class, priority = 1500)
public abstract class RollAnytimeMixin {

    @Dynamic("tryRolling is merged into Minecraft by Combat Roll")
    @ModifyExpressionValue(
        method = "tryRolling",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
    )
    private boolean mmsCombat$rollWhileUsing(boolean original) {
        return false;
    }

    @Dynamic("tryRolling is merged into Minecraft by Combat Roll")
    @ModifyExpressionValue(
        method = "tryRolling",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isBlocking()Z")
    )
    private boolean mmsCombat$rollWhileBlocking(boolean original) {
        return false;
    }

    @Dynamic("tryRolling is merged into Minecraft by Combat Roll")
    @ModifyExpressionValue(
        method = "tryRolling",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F")
    )
    private float mmsCombat$rollWhileRecovering(float original) {
        return 1.0f;
    }

    @Dynamic("tryRolling is merged into Minecraft by Combat Roll")
    @ModifyExpressionValue(
        method = "tryRolling",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 1)
    )
    private boolean mmsCombat$rollWhileAttackHeld(boolean original) {
        return false;
    }

    @Dynamic("tryRolling is merged into Minecraft by Combat Roll")
    @ModifyExpressionValue(
        method = "tryRolling",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I")
    )
    private int mmsCombat$rollAfterUse(int original) {
        return 0;
    }
}
