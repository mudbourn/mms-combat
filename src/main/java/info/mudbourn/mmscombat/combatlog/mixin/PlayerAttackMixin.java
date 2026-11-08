package info.mudbourn.mmscombat.combatlog.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import info.mudbourn.mmscombat.combatlog.PvpEnchants;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

// Marks a melee swing at another player as a player-vs-player hit.
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @WrapMethod(method = "attack")
    private void mmsCombat$pvpAttack(Entity target, Operation<Void> original) {
        UUID[] previous = PvpEnchants.enter((Player) (Object) this, target);
        try {
            original.call(target);
        } finally {
            PvpEnchants.exit(previous);
        }
    }
}
