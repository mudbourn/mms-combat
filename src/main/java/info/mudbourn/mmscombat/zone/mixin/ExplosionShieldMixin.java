package info.mudbourn.mmscombat.zone.mixin;

import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Drops explosion-destroyed blocks that fall inside a blockExplosionShield zone, so blasts still hurt entities but never break terrain there.
@Mixin(ServerExplosion.class)
public class ExplosionShieldMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(
        method = "calculateExplodedPositions",
        at = @At("RETURN"),
        cancellable = true)
    private void mmsCombat$shieldBlocks(CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> positions = cir.getReturnValue();
        if (positions.isEmpty()) {
            return;
        }
        List<BlockPos> kept = new ArrayList<>(positions.size());
        boolean shielded = false;
        for (BlockPos pos : positions) {
            if (ZoneStore.shieldsExplosion(this.level, pos)) {
                shielded = true;
            } else {
                kept.add(pos);
            }
        }
        if (shielded) {
            cir.setReturnValue(kept);
        }
    }
}
