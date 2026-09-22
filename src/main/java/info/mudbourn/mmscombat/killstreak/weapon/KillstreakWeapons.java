package info.mudbourn.mmscombat.killstreak.weapon;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;

// Builds the ks-support killstreak weapons as fully-componented, owner-bound stacks in Java, replacing the datapack give functions.
public final class KillstreakWeapons {

    // ks-support tier ids stamped into custom_data.streak_item, kept so the existing contract-purge datapack still recognises these stacks.
    private static final int STREAK_ITEM_GUN = 1;
    private static final int STREAK_ITEM_MJOLNIR = 2;
    private static final int STREAK_ITEM_SCYTHE = 3;
    private static final int STREAK_ITEM_HAMMER = 4;
    private static final int STREAK_ITEM_TRIDENT = 5;

    private static final Map<String, Function<ServerPlayer, List<ItemStack>>> REGISTRY = Map.of(
        "mjolnir", KillstreakWeapons::buildMjolnir,
        "assault_rifle", KillstreakWeapons::buildAssaultRifle,
        "scythe", KillstreakWeapons::buildScythe,
        "hammer", KillstreakWeapons::buildHammer,
        "trident", KillstreakWeapons::buildTrident
    );

    private KillstreakWeapons() {
    }

    // Whether a weapon key has a builder registered.
    public static boolean isDefined(String key) {
        return REGISTRY.containsKey(key);
    }

    // Resolves a weapon key to its stacks, or an empty list when the key or a required item is missing.
    public static List<ItemStack> build(String key, ServerPlayer owner) {
        Function<ServerPlayer, List<ItemStack>> builder = REGISTRY.get(key);
        if (builder == null) {
            MmsCombat.LOG.warn("Killstreak weapon {} is not defined", key);
            return List.of();
        }
        return builder.apply(owner);
    }

    // Mjolnir: Thor's Recognition, a channeling mace bound to its owner.
    private static List<ItemStack> buildMjolnir(ServerPlayer owner) {
        ItemStack mace = stack("minecraft:mace", 1, STREAK_ITEM_MJOLNIR, owner);
        if (mace.isEmpty()) {
            return List.of();
        }
        name(mace, "Mjolnir", ChatFormatting.AQUA, true);
        mace.set(DataComponents.ITEM_MODEL, Identifier.tryParse("killstreak:mjolnir"));
        enchant(mace, owner, "channeling", 4);
        enchant(mace, owner, "sharpness", 5);
        enchant(mace, owner, "breach", 8);
        enchant(mace, owner, "loyalty", 4);
        enchant(mace, owner, "vanishing_curse", 1);
        lore(mace,
            line("Thor's Recognition", ChatFormatting.AQUA),
            line("Contract-bound. Vanishes on death.", ChatFormatting.DARK_GRAY));
        return List.of(mace);
    }

    // Reaper's Scythe: a soul-reaping netherite hoe bound to its owner.
    private static List<ItemStack> buildScythe(ServerPlayer owner) {
        ItemStack scythe = stack("minecraft:netherite_hoe", 1, STREAK_ITEM_SCYTHE, owner);
        if (scythe.isEmpty()) {
            return List.of();
        }
        name(scythe, "Reaper's Scythe", ChatFormatting.DARK_PURPLE, true);
        scythe.set(DataComponents.ITEM_MODEL, Identifier.tryParse("killstreak:scythe"));
        enchant(scythe, owner, "sharpness", 5);
        enchant(scythe, owner, "looting", 3);
        enchant(scythe, owner, "fire_aspect", 2);
        enchant(scythe, owner, "unbreaking", 3);
        enchant(scythe, owner, "mending", 1);
        enchant(scythe, owner, "vanishing_curse", 1);
        lore(scythe,
            line("Harvest of Souls", ChatFormatting.DARK_PURPLE),
            line("Contract-bound. Vanishes on death.", ChatFormatting.DARK_GRAY));
        return List.of(scythe);
    }

    // Earthshaker: a heavy smashing mace bound to its owner.
    private static List<ItemStack> buildHammer(ServerPlayer owner) {
        ItemStack hammer = stack("minecraft:mace", 1, STREAK_ITEM_HAMMER, owner);
        if (hammer.isEmpty()) {
            return List.of();
        }
        name(hammer, "Earthshaker", ChatFormatting.GOLD, true);
        hammer.set(DataComponents.ITEM_MODEL, Identifier.tryParse("killstreak:hammer"));
        enchant(hammer, owner, "density", 5);
        enchant(hammer, owner, "breach", 4);
        enchant(hammer, owner, "wind_burst", 3);
        enchant(hammer, owner, "fire_aspect", 2);
        enchant(hammer, owner, "unbreaking", 3);
        enchant(hammer, owner, "vanishing_curse", 1);
        lore(hammer,
            line("The Mountain's Fury", ChatFormatting.GOLD),
            line("Contract-bound. Vanishes on death.", ChatFormatting.DARK_GRAY));
        return List.of(hammer);
    }

    // Poseidon's Wrath: a storm-calling trident bound to its owner.
    private static List<ItemStack> buildTrident(ServerPlayer owner) {
        ItemStack trident = stack("minecraft:trident", 1, STREAK_ITEM_TRIDENT, owner);
        if (trident.isEmpty()) {
            return List.of();
        }
        name(trident, "Poseidon's Wrath", ChatFormatting.AQUA, true);
        trident.set(DataComponents.ITEM_MODEL, Identifier.tryParse("killstreak:trident"));
        enchant(trident, owner, "impaling", 5);
        enchant(trident, owner, "loyalty", 3);
        enchant(trident, owner, "channeling", 1);
        enchant(trident, owner, "unbreaking", 3);
        enchant(trident, owner, "mending", 1);
        enchant(trident, owner, "vanishing_curse", 1);
        lore(trident,
            line("Tide of the Deep", ChatFormatting.AQUA),
            line("Contract-bound. Vanishes on death.", ChatFormatting.DARK_GRAY));
        return List.of(trident);
    }

    // Assault Rifle: a JEG gun loaded to 30 with a 60-round reserve, both owner-bound.
    private static List<ItemStack> buildAssaultRifle(ServerPlayer owner) {
        ItemStack gun = stack("jeg:assault_rifle", 1, STREAK_ITEM_GUN, owner);
        if (gun.isEmpty()) {
            return List.of();
        }
        name(gun, "Assault Rifle", ChatFormatting.GOLD, false);
        setIntComponent(gun, "jeg:gun_ammo", 30);
        ItemStack ammo = stack("jeg:rifle_ammo", 60, STREAK_ITEM_GUN, owner);
        return ammo.isEmpty() ? List.of(gun) : List.of(gun, ammo);
    }

    // A base stack of the given item stamped with the owner-binding custom_data, or empty when the item is not registered.
    private static ItemStack stack(String itemId, int count, int streakItem, ServerPlayer owner) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            MmsCombat.LOG.warn("Killstreak weapon item {} is malformed", itemId);
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            MmsCombat.LOG.warn("Killstreak weapon item {} is not registered; skipping", itemId);
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);
        CompoundTag data = new CompoundTag();
        data.putInt("streak_item", streakItem);
        data.putIntArray("streak_owner", UUIDUtil.uuidToIntArray(owner.getUUID()));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    // Sets a non-italic custom name in the given colour, matching the datapack's custom_name styling.
    private static void name(ItemStack stack, String text, ChatFormatting color, boolean bold) {
        Style style = Style.EMPTY.withColor(color).withBold(bold).withItalic(false);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(text).setStyle(style));
    }

    private static void lore(ItemStack stack, Component... lines) {
        stack.set(DataComponents.LORE, new ItemLore(List.of(lines)));
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    // Applies a vanilla enchantment by id via the owner's registry lookup.
    private static void enchant(ItemStack stack, ServerPlayer owner, String enchantId, int level) {
        Identifier id = Identifier.tryParse(enchantId);
        if (id == null) {
            return;
        }
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
        owner.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .get(key)
            .ifPresent(holder -> stack.enchant(holder, level));
    }

    // Sets an integer-valued component (a JEG gun component, say) by id, skipping it when the type is absent.
    @SuppressWarnings("unchecked")
    private static void setIntComponent(ItemStack stack, String componentId, int value) {
        Identifier id = Identifier.tryParse(componentId);
        if (id == null) {
            return;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            MmsCombat.LOG.warn("Component {} is not registered; skipping", componentId);
            return;
        }
        stack.set((DataComponentType<Integer>) type, value);
    }
}
