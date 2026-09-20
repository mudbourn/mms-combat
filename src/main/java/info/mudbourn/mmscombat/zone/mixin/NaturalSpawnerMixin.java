package info.mudbourn.mmscombat.zone.mixin;

import info.mudbourn.mmscombat.zone.ZoneStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Denies natural spawns inside a suppressNaturalSpawns zone before the mob is ever created, so arenas need no lighting pass and no per-tick kill sweep.
@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Inject(
        method = "isValidSpawnPostitionForType",
        at = @At("HEAD"),
        cancellable = true)
    private static void mmsCombat$denySuppressedSpawns(
            ServerLevel level,
            MobCategory category,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            MobSpawnSettings.SpawnerData spawnerData,
            BlockPos.MutableBlockPos pos,
            double distanceSq,
            CallbackInfoReturnable<Boolean> cir) {
        if (ZoneStore.suppressesSpawn(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
