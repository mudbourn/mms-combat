package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

// A melee weapon whose attribute component follows its ability state.
public class ArmoryWeaponItem extends Item {

    private final WeaponStats stats;

    public ArmoryWeaponItem(WeaponStats stats, Properties properties) {
        super(properties.attributes(stats.modifiers(WeaponStats.NONE)));
        this.stats = stats;
    }

    // Copies a stack onto another item, keeping its state but not its attribute bonuses.
    public static ItemStack transmute(ItemStack stack, ItemLike item) {
        ItemStack copy = stack.transmuteCopy(item);
        copy.copyFrom(DataComponents.ATTRIBUTE_MODIFIERS, copy.getPrototype());
        return copy;
    }

    // Extra stats granted by the stack's current ability state.
    protected WeaponStats bonus(ItemStack stack) {
        return WeaponStats.NONE;
    }

    // Advances the stack's ability state once per server tick.
    protected void tickAbility(ItemStack stack, ServerLevel level, Entity holder) {
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, EquipmentSlot slot) {
        this.tickAbility(stack, level, holder);
        this.stats.apply(stack, this.bonus(stack));
    }
}
