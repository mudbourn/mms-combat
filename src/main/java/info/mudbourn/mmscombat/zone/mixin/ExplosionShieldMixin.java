package info.mudbourn.mmscombat.zone.mixin;

import info.mudbourn.mmscombat.zone.Zone;
import info.mudbourn.mmscombat.zone.ZoneStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
        List<Zone> shields = ZoneStore.explosionShields(this.level);
        if (shields.isEmpty()) {
            return positions;
        }
        ResourceKey<Level> dim = this.level.dimension();
        Vec3 origin = center();
        BlockPos originPos = BlockPos.containing(origin.x, origin.y, origin.z);
        for (Zone zone : shields) {
            if (zone.contains(dim, originPos.getX(), originPos.getY(), originPos.getZ())) {
                return Collections.emptyList();
            }
        }
        List<BlockPos> kept = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (!mmsCombat$shielded(shields, dim, pos)) {
                kept.add(pos);
            }
        }
        return kept.size() == positions.size() ? positions : kept;
    }

    private static boolean mmsCombat$shielded(List<Zone> shields, ResourceKey<Level> dim, BlockPos pos) {
        for (Zone zone : shields) {
            if (zone.shieldsColumn(dim, pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }
}
