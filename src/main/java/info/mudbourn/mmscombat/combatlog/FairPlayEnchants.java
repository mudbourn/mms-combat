package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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
    private static final int FREE_LEVELS = 6;
    private static final String MOB_AI_TWEAKS = "mob-ai-tweaks";
    private static final Identifier PERFECT_PARRY = Identifier.fromNamespaceAndPath(MOB_AI_TWEAKS, "perfect_parry");
    private static final Set<Identifier> BANNED = Set.of(
        Identifier.withDefaultNamespace("fire_aspect"),
        Identifier.fromNamespaceAndPath("weaponsexpanded", "freeze"),
        Identifier.fromNamespaceAndPath("weaponsexpanded", "frostbite"),
        Identifier.fromNamespaceAndPath("weaponsexpanded", "polluting"),
        Identifier.fromNamespaceAndPath("weaponsexpanded", "withering"));
    private static final Set<Identifier> FREE = Set.of(
        Identifier.withDefaultNamespace("sharpness"),
        Identifier.withDefaultNamespace("protection"),
        Identifier.withDefaultNamespace("power"),
        Identifier.fromNamespaceAndPath("basicweapons", "might"));
    private static final int[] ARMOR_BUDGETS = {4, 2, 0};
    private static final TagKey<Item> IRON_TIER_ARMOR = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "fair_play/iron_tier_armor"));
    private static final int[] MELEE_BUDGETS = {10, 8, 6};
    private static final double BASE_DAMAGE = 1;
    private static final double BASE_SPEED = 4;
    // Damage per second of vanilla iron, diamond and netherite swords.
    private static final double[] SWORD_DPS = {9.6, 11.2, 12.8};
    // Armor plus toughness per slot (head, chest, legs, feet) for vanilla iron, diamond and netherite.
    private static final double[][] ARMOR_SCORES = {
        {2, 6, 5, 2},
        {5, 10, 8, 5},
        {6, 11, 9, 6}
    };

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
            if (!budgeted(original, enchantment)) {
                continue;
            }
            total += cost(enchantment, level);
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
        Predicate<Holder<Enchantment>> budgeted = enchantment -> budgeted(stack, enchantment);
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            while (sum(mutable, Kind.DAMAGE::matches) + sum(mutable, Kind.STATUS::matches) > paid) {
                Kind kind = sum(mutable, Kind.DAMAGE::matches) > 0 ? Kind.DAMAGE : Kind.STATUS;
                lowerHighest(mutable, kind::matches);
            }
            Predicate<Holder<Enchantment>> costly = enchantment -> budgeted.test(enchantment)
                && cost(enchantment, mutable.getLevel(enchantment)) > 0;
            while (cap != UNCAPPED && cost(mutable) > cap) {
                lowerHighest(mutable, costly);
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

    // Total of the levels that count against the stack's budget.
    public static int budgetedLevels(ItemStack stack, ItemEnchantments enchantments) {
        int sum = 0;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (budgeted(stack, enchantment)) {
                sum += cost(enchantment, enchantments.getLevel(enchantment));
            }
        }
        return sum;
    }

    // Whether the enchant counts against the budget: curses, durability, disallowed and capped-free enchants never do.
    private static boolean budgeted(ItemStack stack, Holder<Enchantment> enchantment) {
        return !enchantment.is(EnchantmentTags.CURSE)
            && !isUpkeep(enchantment)
            && !disallowed(enchantment)
            && !enchantment.is(PERFECT_PARRY);
    }

    // Whether the enchant is switched off in player-vs-player fights: every mob-ai-tweaks enchant but Perfect Parry, Fire Aspect and the frost and poison inflictors.
    public static boolean disallowed(Holder<Enchantment> enchantment) {
        if (enchantment.is(PERFECT_PARRY)) {
            return false;
        }
        return enchantment.unwrapKey()
            .map(key -> BANNED.contains(key.identifier()) || key.identifier().getNamespace().equals(MOB_AI_TWEAKS))
            .orElse(false);
    }

    // Budget cost of a level: Sharpness, Might, Power and Protection are free up to level 6.
    private static int cost(Holder<Enchantment> enchantment, int level) {
        boolean free = enchantment.unwrapKey().map(key -> FREE.contains(key.identifier())).orElse(false);
        return free ? Math.max(0, level - FREE_LEVELS) : level;
    }

    private static int cost(ItemEnchantments.Mutable mutable) {
        int sum = 0;
        for (Holder<Enchantment> enchantment : mutable.keySet()) {
            if (budgeted(ItemStack.EMPTY, enchantment)) {
                sum += cost(enchantment, mutable.getLevel(enchantment));
            }
        }
        return sum;
    }

    // Strips disallowed enchants and holds Perfect Parry at level 1, returning the input unchanged when nothing applies.
    public static ItemEnchantments restrict(ItemEnchantments enchantments) {
        boolean changed = false;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (limit(enchantment) < enchantments.getLevel(enchantment)) {
                changed = true;
            }
        }
        if (!changed) {
            return enchantments;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            mutable.set(enchantment, Math.min(limit(enchantment), enchantments.getLevel(enchantment)));
        }
        return mutable.toImmutable();
    }

    private static int limit(Holder<Enchantment> enchantment) {
        if (disallowed(enchantment)) {
            return 0;
        }
        return enchantment.is(PERFECT_PARRY) ? 1 : Integer.MAX_VALUE;
    }

    // Whether the enchant repairs the item or slows its durability loss, vanilla or data-driven.
    public static boolean isUpkeep(Holder<Enchantment> enchantment) {
        Enchantment value = enchantment.value();
        return !value.getEffects(EnchantmentEffectComponents.REPAIR_WITH_XP).isEmpty()
            || !value.getEffects(EnchantmentEffectComponents.ITEM_DAMAGE).isEmpty();
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
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            if (stack.is(IRON_TIER_ARMOR)) {
                return ARMOR_BUDGETS[0];
            }
            return ARMOR_BUDGETS[armorTier(stack, equippable.slot())];
        }
        int melee = meleeTier(stack, path);
        return melee < 0 ? UNCAPPED : MELEE_BUDGETS[melee];
    }

    // Ranks a melee weapon against vanilla iron, diamond and netherite swords by damage per second, one tier higher if it grants any other buff; -1 for a non-weapon.
    private static int meleeTier(ItemStack stack, String path) {
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
            return -1;
        }
        double damage = 0;
        double speed = 0;
        boolean buffed = false;
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (!entry.slot().test(EquipmentSlot.MAINHAND)) {
                continue;
            }
            boolean added = entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE;
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                damage += added ? entry.modifier().amount() : 0;
            } else if (entry.attribute().is(Attributes.ATTACK_SPEED)) {
                speed += added ? entry.modifier().amount() : 0;
            } else {
                buffed = true;
            }
        }
        if (damage <= 0) {
            return -1;
        }
        double dps = (BASE_DAMAGE + damage) * Math.max(0, BASE_SPEED + speed);
        int tier = 0;
        for (int next = 1; next < SWORD_DPS.length; next++) {
            if (dps >= SWORD_DPS[next]) {
                tier = next;
            }
        }
        int named = tiered(path, 0, 1, 2);
        tier = Math.max(tier, named == UNCAPPED ? 0 : named);
        return Math.min(tier + (buffed ? 1 : 0), MELEE_BUDGETS.length - 1);
    }

    // Ranks a piece against vanilla iron, diamond and netherite in its slot by armor plus toughness, one tier higher if it grants any other buff.
    private static int armorTier(ItemStack stack, EquipmentSlot slot) {
        double score = 0;
        boolean buffed = false;
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (!entry.slot().test(slot)) {
                continue;
            }
            if (entry.attribute().is(Attributes.ARMOR) || entry.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                if (entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                    score += entry.modifier().amount();
                }
            } else if (!entry.attribute().is(Attributes.KNOCKBACK_RESISTANCE)) {
                buffed = true;
            }
        }
        int index = switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            default -> 3;
        };
        int tier = 0;
        for (int next = 1; next < ARMOR_SCORES.length; next++) {
            if (score >= ARMOR_SCORES[next][index]) {
                tier = next;
            }
        }
        return Math.min(tier + (buffed ? 1 : 0), ARMOR_BUDGETS.length - 1);
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

    private static int sum(ItemEnchantments.Mutable mutable, Predicate<Holder<Enchantment>> kind) {
        int sum = 0;
        for (Holder<Enchantment> enchantment : mutable.keySet()) {
            if (kind.test(enchantment)) {
                sum += mutable.getLevel(enchantment);
            }
        }
        return sum;
    }

    private static void lowerHighest(ItemEnchantments.Mutable mutable, Predicate<Holder<Enchantment>> kind) {
        Holder<Enchantment> highest = null;
        int highestLevel = 0;
        for (Holder<Enchantment> enchantment : mutable.keySet()) {
            int level = mutable.getLevel(enchantment);
            if (kind.test(enchantment) && level > highestLevel) {
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
        DAMAGE,
        STATUS;

        boolean matches(Holder<Enchantment> enchantment) {
            if (enchantment.is(EnchantmentTags.CURSE) || disallowed(enchantment)) {
                return false;
            }
            return switch (this) {
                case STATUS -> isStatus(enchantment);
                case DAMAGE -> !isStatus(enchantment) && isDamage(enchantment);
            };
        }
    }
}
