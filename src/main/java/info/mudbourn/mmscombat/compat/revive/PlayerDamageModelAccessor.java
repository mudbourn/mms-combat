package info.mudbourn.mmscombat.compat.revive;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Reads and writes the downed countdown, in ticks until death.
@Mixin(value = PlayerDamageModel.class, remap = false)
public interface PlayerDamageModelAccessor {

    @Accessor("unconsciousTicks")
    int mmsCombat$getUnconsciousTicks();

    @Accessor("unconsciousTicks")
    void mmsCombat$setUnconsciousTicks(int ticks);
}
