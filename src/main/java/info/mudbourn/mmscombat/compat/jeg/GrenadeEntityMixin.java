package info.mudbourn.mmscombat.compat.jeg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import ttv.migami.jeg.entity.GrenadeEntity;

// Stops JEG grenades and launcher rounds from breaking or burning blocks; the blast still hurts entities.
@Mixin(GrenadeEntity.class)
public abstract class GrenadeEntityMixin {

    @ModifyArg(
        method = "explode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"
        ),
        index = 5
    )
    private Level.ExplosionInteraction mmsCombat$keepBlocks(Level.ExplosionInteraction interaction) {
        return Level.ExplosionInteraction.NONE;
    }

    @Redirect(
        method = "igniteNearby",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private boolean mmsCombat$skipIgnite(Level level, BlockPos pos, BlockState state) {
        return false;
    }
}
