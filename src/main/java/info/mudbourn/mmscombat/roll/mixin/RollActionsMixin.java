package info.mudbourn.mmscombat.roll.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import info.mudbourn.mmscombat.roll.RollLockout;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

// Lets players attack, mine and use items mid-roll.
@Mixin(Minecraft.class)
public abstract class RollActionsMixin {

    @WrapMethod(method = "startAttack")
    private boolean mmsCombat$attackWhileRolling(Operation<Boolean> original) {
        RollLockout.set(true);
        try {
            return original.call();
        } finally {
            RollLockout.set(false);
        }
    }

    @WrapMethod(method = "continueAttack")
    private void mmsCombat$mineWhileRolling(boolean leftClick, Operation<Void> original) {
        RollLockout.set(true);
        try {
            original.call(leftClick);
        } finally {
            RollLockout.set(false);
        }
    }

    @WrapMethod(method = "startUseItem")
    private void mmsCombat$useWhileRolling(Operation<Void> original) {
        RollLockout.set(true);
        try {
            original.call();
        } finally {
            RollLockout.set(false);
        }
    }
}
