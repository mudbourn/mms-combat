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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Drops explosion-affected blocks that fall inside a blockExplosionShield zone before they are broken or set alight, so blasts still hurt entities but never break terrain there.
@Mixin(ServerExplosion.class)
public class ExplosionShieldMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @ModifyVariable(
        method = {"interactWithBlocks", "createFire"},
        at = @At("HEAD"),
        argsOnly = true)
    private List<BlockPos> mmsCombat$shieldBlocks(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return positions;
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
        return shielded ? kept : positions;
    }
}
