package info.mudbourn.mmscombat.compat.revive;

import goetic.mods.absolutrevive.common.EventHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Makes any hit on a downed player lethal.
@Mixin(LivingEntity.class)
public abstract class DownedLivingEntityMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float mmsCombat$oneShotDowned(float amount, ServerLevel level, DamageSource source) {
        if ((Object) this instanceof Player player && EventHandler.isUnconscious(player)) {
            return Float.MAX_VALUE;
        }
        return amount;
    }
}
