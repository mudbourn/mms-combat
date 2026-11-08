package info.mudbourn.mmscombat.combatlog.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import info.mudbourn.mmscombat.combatlog.PvpEnchants;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

// Marks damage one player deals another as a player-vs-player hit, covering protection and thorns.
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    @WrapMethod(method = "hurtServer")
    private boolean mmsCombat$pvpHurt(
        ServerLevel level,
        DamageSource source,
        float amount,
        Operation<Boolean> original
    ) {
        UUID[] previous = PvpEnchants.enter(source.getEntity(), (LivingEntity) (Object) this);
        try {
            return original.call(level, source, amount);
        } finally {
            PvpEnchants.exit(previous);
        }
    }
}
