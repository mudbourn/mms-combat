package info.mudbourn.mmscombat.dummy;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.phys.Vec3;

// A silent invisible armor stand wearing the dummy model that absorbs every hit and floats the damage it would have dealt; sneak-attacking it picks it back up.
public final class TrainingDummy {

    public static final String TAG = "mms_combat_training_dummy";
    private static final String NUMBER_TAG = "mms_combat_dummy_number";
    private static final String LEGACY_TAG = "training_dummy";
    private static final int ALL_SLOTS_DISABLED = 4144959;
    private static final int NUMBER_TICKS = 20;

    public static final Item ITEM = registerItem();

    private static final Map<Display.TextDisplay, Integer> numbers = new HashMap<>();

    private TrainingDummy() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(ITEM));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Display.TextDisplay display
                && display.getTags().contains(NUMBER_TAG)
                && !numbers.containsKey(display)) {
                display.discard();
            } else if (entity instanceof Slime slime && slime.getTags().contains(LEGACY_TAG)) {
                slime.discard();
            } else if (entity instanceof ArmorStand stand && stand.getTags().contains(LEGACY_TAG)) {
                level.getServer().execute(() -> convertLegacy(stand));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(TrainingDummy::tick);
    }

    // Absorbs one hit: a sneaking player's own swing picks the dummy up, anything else floats the damage dealt after the dummy's armor.
    public static void hit(ServerLevel level, ArmorStand dummy, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player
            && source.getDirectEntity() == player
            && player.isShiftKeyDown()) {
            pickUp(dummy, player);
            return;
        }
        float dealt = dummy.getDamageAfterMagicAbsorb(source, dummy.getDamageAfterArmorAbsorb(source, amount));
        if (dealt > 0) {
            showNumber(level, dummy, source, dealt);
        }
    }

    private static void pickUp(ArmorStand dummy, ServerPlayer player) {
        dummy.discard();
        if (player.isCreative()) {
            return;
        }
        ItemStack stack = new ItemStack(ITEM);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void showNumber(ServerLevel level, ArmorStand dummy, DamageSource source, float dealt) {
        Vec3 pos = dummy.getEyePosition();
        Vec3 attacker = source.getSourcePosition();
        if (attacker != null) {
            Vec3 toward = attacker.subtract(pos).multiply(1, 0, 1);
            if (toward.lengthSqr() > 0) {
                pos = pos.add(toward.normalize().scale(0.5));
            }
        }
        pos = pos.add(
            (level.getRandom().nextDouble() - 0.5) * 0.4,
            level.getRandom().nextDouble() * 0.3,
            (level.getRandom().nextDouble() - 0.5) * 0.4
        );

        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setPos(pos);
        display.setText(Component.literal(String.format("-%.1f", dealt)).withStyle(ChatFormatting.RED));
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.addTag(NUMBER_TAG);
        numbers.put(display, NUMBER_TICKS);
        level.addFreshEntity(display);
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<Display.TextDisplay, Integer>> it = numbers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Display.TextDisplay, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0 || entry.getKey().isRemoved()) {
                entry.getKey().discard();
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
    }

    // Turns a dummy placed by the old Training Dummy datapack into this one, whose hidden slime hitbox is discarded separately.
    private static void convertLegacy(ArmorStand stand) {
        if (stand.isRemoved()) {
            return;
        }
        stand.removeTag(LEGACY_TAG);
        stand.addTag(TAG);
        stand.setMarker(false);
        stand.setNoGravity(false);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ITEM));
    }

    private static Item registerItem() {
        Identifier id = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "training_dummy");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = new ArmorStandItem(new Item.Properties()
            .setId(key)
            .stacksTo(16)
            .component(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityType.ARMOR_STAND, entityData())));

        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static CompoundTag entityData() {
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));

        CompoundTag head = new CompoundTag();
        head.putString("id", "mms_combat:training_dummy");
        head.putInt("count", 1);
        CompoundTag equipment = new CompoundTag();
        equipment.put("head", head);

        CompoundTag data = new CompoundTag();
        data.put("Tags", tags);
        data.put("equipment", equipment);
        data.putBoolean("Invisible", true);
        data.putBoolean("NoBasePlate", true);
        data.putInt("DisabledSlots", ALL_SLOTS_DISABLED);
        return data;
    }
}
