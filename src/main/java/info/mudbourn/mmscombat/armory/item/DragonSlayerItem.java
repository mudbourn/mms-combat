package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

// Greatsword that grows stronger with the total damage it has dealt.
public class DragonSlayerItem extends ArmoryWeaponItem {

    private static final float MAX_BONUS = 24.0F;
    private static final float DAMAGE_CAP = 10240.0F;

    public DragonSlayerItem(Properties properties) {
        super(new WeaponStats(18.0, -3.6, 1.5, 3.0), properties);
    }

    // Adds damage dealt with this stack to its running total.
    public static void recordDamage(ItemStack stack, float amount) {
        stack.set(ArmoryComponents.DAMAGE_DEALT, stack.getOrDefault(ArmoryComponents.DAMAGE_DEALT, 0.0F) + amount);
    }

    @Override
    protected WeaponStats bonus(ItemStack stack) {
        float dealt = stack.getOrDefault(ArmoryComponents.DAMAGE_DEALT, 0.0F);
        if (dealt <= 0.0F) {
            return WeaponStats.NONE;
        }
        return new WeaponStats(Math.min(MAX_BONUS, MAX_BONUS * dealt / DAMAGE_CAP), 0.0, 0.0, 0.0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int dealt = (int) (float) stack.getOrDefault(ArmoryComponents.DAMAGE_DEALT, 0.0F);
        tooltip.accept(Component.translatable("item.mms_combat.dragon_slayer.damage_dealt", dealt)
            .withStyle(ChatFormatting.GRAY));
    }
}
