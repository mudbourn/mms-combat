package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// Fragile crystal katana that poisons what it hits.
public class OriginiumKatanaItem extends ArmoryWeaponItem {

    public static final int FORM_TICKS = 20;
    private static final int POISON_TICKS = 60;

    public OriginiumKatanaItem(Properties properties) {
        super(new WeaponStats(9.0, 0.2, -2.8, 1.0), properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), attacker);
    }

    @Override
    protected void tickAbility(ItemStack stack, ServerLevel level, Entity holder) {
        Long start = stack.get(ArmoryComponents.ANIMATION_START);
        if (start != null && level.getGameTime() - start >= FORM_TICKS) {
            stack.remove(ArmoryComponents.ANIMATION_START);
        }
    }
}
