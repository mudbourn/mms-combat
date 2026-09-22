package info.mudbourn.mmscombat.zone.mixin;

import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.ArrayList;
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

// Shields terrain from explosions: a blast centred inside a blockExplosionShield zone breaks nothing anywhere, and any blast from outside still cannot break blocks that lie inside such a zone.
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
        List<BlockPos> kept = new ArrayList<>(positions.size());
        boolean shielded = false;
        for (BlockPos pos : positions) {
            if (ZoneStore.shieldsColumn(this.level, pos)) {
                shielded = true;
            } else {
                kept.add(pos);
            }
        }
        return shielded ? kept : positions;
    }
}
