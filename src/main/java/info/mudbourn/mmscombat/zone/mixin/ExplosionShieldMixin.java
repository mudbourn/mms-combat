package info.mudbourn.mmscombat.zone.mixin;

import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Drops every block an explosion would break or set alight when the blast originates inside a blockExplosionShield zone, so it still hurts entities but never breaks terrain anywhere.
@Mixin(ServerExplosion.class)
public class ExplosionShieldMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    public Vec3 center() {
        throw new AssertionError();
    }

    @ModifyVariable(
        method = {"interactWithBlocks", "createFire"},
        at = @At("HEAD"),
        argsOnly = true)
    private List<BlockPos> mmsCombat$shieldBlocks(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return positions;
        }
        Vec3 origin = center();
        BlockPos originPos = BlockPos.containing(origin.x, origin.y, origin.z);
        if (ZoneStore.shieldsExplosion(this.level, originPos)) {
            return Collections.emptyList();
        }
        return positions;
    }
}
