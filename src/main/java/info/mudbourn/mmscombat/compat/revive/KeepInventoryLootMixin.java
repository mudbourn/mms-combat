package info.mudbourn.mmscombat.compat.revive;

import goetic.mods.absolutrevive.common.EventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Stops players looting a downed player while keepInventory is on.
@Mixin(value = EventHandler.class, remap = false)
public abstract class KeepInventoryLootMixin {

    @Inject(method = "openLootMenu", at = @At("HEAD"), cancellable = true)
    private static void mmsCombat$noLootWithKeepInventory(ServerPlayer looter, Player target, CallbackInfo ci) {
        if (looter.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
            ci.cancel();
        }
    }
}
