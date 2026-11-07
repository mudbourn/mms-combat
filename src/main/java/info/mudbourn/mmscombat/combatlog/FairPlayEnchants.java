package info.mudbourn.mmscombat.combatlog;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.TargetedConditionalEffect;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ApplyMobEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.Ignite;
import net.minecraft.world.item.equipment.Equippable;

// The colosseum fair-use rules: a total enchant level budget per weapon or armor tier, and status enchants paid for with damage enchant levels.
public final class FairPlayEnchants {

    public static final int UNCAPPED = -1;

    private FairPlayEnchants() {
    }

    // Whether the stack breaks a rule and so needs levels dropped before it can fight.
    public static boolean overBudget(ItemStack stack) {
        return stack.isEnchanted() && !fits(stack, stack.getEnchantments());
    }

    // Whether a chosen set of levels is legal for the original stack.
    public static boolean fits(ItemStack original, ItemEnchantments chosen) {
        ItemEnchantments base = original.getEnchantments();
        int total = 0;
        int damage = 0;
        int status = 0;
        for (Holder<Enchantment> enchantment : chosen.keySet()) {
            int level = chosen.getLevel(enchantment);
            if (level > base.getLevel(enchantment)) {
                return false;
            }
            if (enchantment.is(EnchantmentTags.CURSE)) {
                continue;
            }
            total += level;
            if (isStatus(enchantment)) {
                status += level;
            } else if (isDamage(enchantment)) {
                damage += level;
            }
        }
        int cap = budget(original);
        return (cap == UNCAPPED || total <= cap) && damage + status <= damageLevels(base);
    }

    // Drops levels automatically: damage levels pay for status levels first, then the highest enchant shrinks until the total fits the budget.
    public static void nerf(ItemStack stack) {
        if (!overBudget(stack)) {
            return;
        }
        int paid = damageLevels(stack.getEnchantments());
        int cap = budget(stack);
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            while (sum(mutable, Kind.DAMAGE) + sum(mutable, Kind.STATUS) > paid) {
                Kind kind = sum(mutable, Kind.DAMAGE) > 0 ? Kind.DAMAGE : Kind.STATUS;
                lowerHighest(mutable, kind);
            }
            while (cap != UNCAPPED && sum(mutable, Kind.ANY) > cap) {
                lowerHighest(mutable, Kind.ANY);
            }
        });
    }

    // Replaces the stack's enchantments with a chosen set, keeping its curses.
    public static void apply(ItemStack stack, ItemEnchantments chosen) {
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            for (Holder<Enchantment> enchantment : List.copyOf(mutable.keySet())) {
                if (!enchantment.is(EnchantmentTags.CURSE)) {
                    mutable.set(enchantment, chosen.getLevel(enchantment));
                }
            }
        });
    }

    public static int budget(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("slingshot")) {
            return 9;
        }
        if (stack.is(Items.BOW)
            || stack.is(Items.CROSSBOW)
            || path.contains("longbow")
            || path.contains("chain_crossbow")) {
            return 7;
        }
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || path.endsWith("_spear")) {
            return tiered(path, 10, 8, 6);
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            return tiered(path, 4, 2, 0);
        }
        return UNCAPPED;
    }

    // Whether the enchant raises damage, vanilla or data-driven.
    public static boolean isDamage(Holder<Enchantment> enchantment) {
        Enchantment value = enchantment.value();
        return !value.getEffects(EnchantmentEffectComponents.DAMAGE).isEmpty()
            || !value.getEffects(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK).isEmpty();
    }

    // Whether the enchant ignites or applies a mob effect on hit or on a fired projectile, vanilla or data-driven.
    public static boolean isStatus(Holder<Enchantment> enchantment) {
        Enchantment value = enchantment.value();
        for (TargetedConditionalEffect<EnchantmentEntityEffect> effect
            : value.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (inflicts(effect.effect())) {
                return true;
            }
        }
        for (ConditionalEffect<EnchantmentEntityEffect> effect
            : value.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED)) {
            if (inflicts(effect.effect())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inflicts(EnchantmentEntityEffect effect) {
        if (effect instanceof Ignite || effect instanceof ApplyMobEffect) {
            return true;
        }
        if (effect instanceof AllOf.EntityEffects all) {
            for (EnchantmentEntityEffect inner : all.effects()) {
                if (inflicts(inner)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int damageLevels(ItemEnchantments enchantments) {
        int sum = 0;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (!isStatus(enchantment) && isDamage(enchantment)) {
                sum += enchantments.getLevel(enchantment);
            }
        }
        return sum;
    }

    private static int sum(ItemEnchantments.Mutable mutable, Kind kind) {
        int sum = 0;
        for (Holder<Enchantment> enchantment : mutable.keySet()) {
            if (kind.matches(enchantment)) {
                sum += mutable.getLevel(enchantment);
            }
        }
        return sum;
    }

    private static void lowerHighest(ItemEnchantments.Mutable mutable, Kind kind) {
        Holder<Enchantment> highest = null;
        int highestLevel = 0;
        for (Holder<Enchantment> enchantment : mutable.keySet()) {
            int level = mutable.getLevel(enchantment);
            if (kind.matches(enchantment) && level > highestLevel) {
                highest = enchantment;
                highestLevel = level;
            }
        }
        mutable.set(highest, highestLevel - 1);
    }

    private static int tiered(String path, int iron, int diamond, int netherite) {
        if (path.startsWith("netherite_")) {
            return netherite;
        }
        if (path.startsWith("diamond_")) {
            return diamond;
        }
        if (path.startsWith("iron_")) {
            return iron;
        }
        return UNCAPPED;
    }

    private enum Kind {
        ANY,
        DAMAGE,
        STATUS;

        boolean matches(Holder<Enchantment> enchantment) {
            if (enchantment.is(EnchantmentTags.CURSE)) {
                return false;
            }
            return switch (this) {
                case ANY -> true;
                case STATUS -> isStatus(enchantment);
                case DAMAGE -> !isStatus(enchantment) && isDamage(enchantment);
            };
        }
    }
}
