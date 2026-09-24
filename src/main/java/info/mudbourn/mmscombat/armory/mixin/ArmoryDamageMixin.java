package info.mudbourn.mmscombat.armory.mixin;

import info.mudbourn.mmscombat.armory.ArmoryEvents;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports every damage instance a living entity takes, killing blows included, to the weapon hooks.
@Mixin(CombatTracker.class)
public abstract class ArmoryDamageMixin {

    @Shadow
    @Final
    private LivingEntity mob;

    @Inject(method = "recordDamage", at = @At("HEAD"))
    private void mmsCombat$onDamage(DamageSource source, float damage, CallbackInfo ci) {
        if (!this.mob.level().isClientSide()) {
            ArmoryEvents.onDamage(this.mob, source, damage);
        }
    }
}
