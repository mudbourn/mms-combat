package info.mudbourn.mmscombat.combatlog.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import info.mudbourn.mmscombat.combatlog.PvpEnchants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Swaps in fair-play enchant levels wherever vanilla reads an item's enchantments during a player-vs-player hit.
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @ModifyExpressionValue(
        method = {
            "getItemEnchantmentLevel",
            "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
            "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
        ),
        require = 3,
        allow = 3
    )
    private static Object mmsCombat$fairLevels(Object enchantments, @Local(argsOnly = true) ItemStack stack) {
        return PvpEnchants.levels(stack, (ItemEnchantments) enchantments);
    }
}
