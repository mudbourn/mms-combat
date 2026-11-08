package info.mudbourn.mmscombat.compat.revive;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import info.mudbourn.mmscombat.revive.SelfRevive;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Drives self-revive from Absolute Revive's per-tick damage model update.
@Mixin(value = PlayerDamageModel.class, remap = false)
public abstract class PlayerDamageModelMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void mmsCombat$tickSelfRevive(Level level, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            SelfRevive.tick(serverPlayer, (PlayerDamageModel) (Object) this);
        }
    }
}
