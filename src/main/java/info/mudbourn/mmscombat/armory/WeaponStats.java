package info.mudbourn.mmscombat.armory;

import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

// Main-hand attribute bonuses of a weapon, added to the player's base values.
public record WeaponStats(double damage, double speed, double knockback, double reach) {

    public static final WeaponStats NONE = new WeaponStats(0.0, 0.0, 0.0, 0.0);

    private static final Identifier ABILITY_ID = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "armory_ability");
    private static final Identifier KNOCKBACK_ID = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "armory_knockback");
    private static final Identifier REACH_ID = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "armory_reach");

    // Base modifiers plus an ability bonus under its own id.
    public ItemAttributeModifiers modifiers(WeaponStats bonus) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        add(builder, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID, this.damage);
        add(builder, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID, this.speed);
        add(builder, Attributes.ATTACK_KNOCKBACK, KNOCKBACK_ID, this.knockback);
        add(builder, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, this.reach);
        add(builder, Attributes.ATTACK_DAMAGE, ABILITY_ID, bonus.damage);
        add(builder, Attributes.ATTACK_SPEED, ABILITY_ID, bonus.speed);
        add(builder, Attributes.ATTACK_KNOCKBACK, ABILITY_ID, bonus.knockback);
        return builder.build();
    }

    // Rewrites the stack's attribute component when the ability bonus changed.
    public void apply(ItemStack stack, WeaponStats bonus) {
        ItemAttributeModifiers wanted = this.modifiers(bonus);
        if (!wanted.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, wanted);
        }
    }

    private static void add(ItemAttributeModifiers.Builder builder,
                            Holder<Attribute> attribute,
                            Identifier id,
                            double amount) {
        if (amount != 0.0) {
            builder.add(
                attribute,
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            );
        }
    }
}
