package info.mudbourn.mmscombat.armory;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.armory.item.BloodVesselItem;
import info.mudbourn.mmscombat.armory.item.BloodletterItem;
import info.mudbourn.mmscombat.armory.item.CrucibleInactiveItem;
import info.mudbourn.mmscombat.armory.item.CrucibleItem;
import info.mudbourn.mmscombat.armory.item.DragonSlayerItem;
import info.mudbourn.mmscombat.armory.item.EdgeOfExistenceItem;
import info.mudbourn.mmscombat.armory.item.MurasamaItem;
import info.mudbourn.mmscombat.armory.item.MurasamaSheathedItem;
import info.mudbourn.mmscombat.armory.item.OriginiumCatalystItem;
import info.mudbourn.mmscombat.armory.item.OriginiumKatanaItem;
import info.mudbourn.mmscombat.armory.item.PunisherItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Weapon;

import java.util.List;
import java.util.function.Function;

// The Armory of Destiny melee weapons and their companion items.
public final class ArmoryItems {

    public static final Item DRAGON_SLAYER = register(
        "dragon_slayer",
        DragonSlayerItem::new,
        weapon(2048).repairable(Items.NETHERITE_INGOT)
    );
    public static final Item BLOODLETTER = register(
        "bloodletter",
        BloodletterItem::new,
        weapon(1024).repairable(Items.NETHERITE_INGOT)
    );
    public static final Item CRUCIBLE = register(
        "crucible",
        CrucibleItem::new,
        epic()
            .enchantable(15)
            .component(ArmoryComponents.USAGES, CrucibleItem.MAX_ENERGY)
    );
    public static final Item CRUCIBLE_INACTIVE = register(
        "crucible_inactive",
        CrucibleInactiveItem::new,
        epic().overrideDescription("item.mms_combat.crucible")
    );
    public static final Item EDGE_OF_EXISTENCE = register(
        "edge_of_existence",
        EdgeOfExistenceItem::new,
        weapon(1150).repairable(Items.NETHERITE_INGOT)
    );
    public static final Item MURASAMA = register(
        "murasama",
        MurasamaItem::new,
        weapon(1300).repairable(Items.ECHO_SHARD)
    );
    public static final Item MURASAMA_SHEATHED = register(
        "murasama_sheathed",
        MurasamaSheathedItem::new,
        epic().overrideDescription("item.mms_combat.murasama")
    );
    public static final Item GUN_SHEATH = register(
        "gun_sheath",
        Item::new,
        epic()
    );
    public static final Item ORIGINIUM_KATANA = register(
        "originium_katana",
        OriginiumKatanaItem::new,
        weapon(64)
    );
    public static final Item ORIGINIUM_CATALYST = register(
        "originium_catalyst",
        OriginiumCatalystItem::new,
        new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)
    );
    public static final Item PUNISHER = register(
        "punisher",
        PunisherItem::new,
        weapon(1800).repairable(Items.DIAMOND)
    );
    public static final Item BLOOD_VESSEL_EMPTY = register(
        "blood_vessel_empty",
        Item::new,
        new Item.Properties().stacksTo(8)
    );
    public static final Item BLOOD_VESSEL_FULL = register(
        "blood_vessel_full",
        BloodVesselItem::new,
        new Item.Properties().stacksTo(8)
    );

    private static final List<Item> CREATIVE_ORDER = List.of(
        DRAGON_SLAYER,
        CRUCIBLE_INACTIVE,
        BLOODLETTER,
        BLOOD_VESSEL_EMPTY,
        BLOOD_VESSEL_FULL,
        EDGE_OF_EXISTENCE,
        MURASAMA,
        GUN_SHEATH,
        ORIGINIUM_CATALYST,
        ORIGINIUM_KATANA,
        PUNISHER
    );

    private ArmoryItems() {
    }

    public static void register() {
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "armory"),
            FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.mms_combat.armory"))
                .icon(() -> new ItemStack(DRAGON_SLAYER))
                .displayItems((parameters, output) -> CREATIVE_ORDER.forEach(output::accept))
                .build()
        );
    }

    private static Item.Properties epic() {
        return new Item.Properties().rarity(Rarity.EPIC).stacksTo(1);
    }

    private static Item.Properties weapon(int durability) {
        return epic()
            .durability(durability)
            .enchantable(15)
            .component(DataComponents.WEAPON, new Weapon(1));
    }

    private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = factory.apply(properties.setId(key));

        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
