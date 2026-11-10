package info.mudbourn.mmscombat.dummy;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.dummy.mixin.TextDisplayAccessor;
import info.mudbourn.mmsweapons.headshot.HeadshotBoxes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// A silent invisible armor stand wearing the dummy model that absorbs every hit, floats each hit and keeps a last/total readout; sneak-attacking it picks it back up.
public final class TrainingDummy {

    public static final String TAG = "mms_combat_training_dummy";
    private static final String NUMBER_TAG = "mms_combat_dummy_number";
    private static final String LEGACY_TAG = "training_dummy";
    private static final String TALLY_TAG = "mms_combat_dummy_tally";
    private static final int ALL_SLOTS_DISABLED = 4144959;
    private static final int NUMBER_TICKS = 20;
    // Five seconds without a hit zeroes the running total.
    private static final int TALLY_RESET_TICKS = 100;
    // How long the zeroed total stays up before it disappears.
    private static final int TALLY_CLEARED_TICKS = 40;
    // The total at which the readout is fully red.
    private static final float TALLY_FULL_RED = 100.0F;
    private static final double TALLY_HEIGHT = 2.4;
    private static final double NUMBER_SPACING = 0.35;
    private static final double TALLY_SPACING = 0.5;
    private static final int NUMBER_PLACEMENT_TRIES = 16;
    // Heights above the dummy's feet that damage numbers spawn between: the lower torso up to mid-head.
    private static final double NUMBER_LOWEST = 0.9;
    private static final double NUMBER_HIGHEST = 1.7;
    // How far in front of the dummy's center the numbers' plane sits, past its half-block-wide hitbox.
    private static final double NUMBER_FORWARD = 0.7;
    // How wide the numbers' plane is.
    private static final double NUMBER_WIDTH = 1.0;

    private static final AABB HEAD = new AABB(-0.25, 1.5, -0.25, 0.25, 2.0, 0.25);

    public static final EntityDimensions DIMENSIONS = EntityDimensions.fixed(1.0F, 2.0F).withEyeHeight(1.7775F);

    public static final Item ITEM = registerItem();

    // Damage reactions other mods run on a dummy hit, since the dummy cancels the hurt that would normally trigger them.
    @FunctionalInterface
    public interface DamageHook {
        void onDamage(ArmorStand dummy, DamageSource source, float dealt);
    }

    private static final java.util.List<DamageHook> DAMAGE_HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addDamageHook(DamageHook hook) {
        DAMAGE_HOOKS.add(hook);
    }

    private static final Map<Display.TextDisplay, Integer> numbers = new HashMap<>();
    private static final Map<ArmorStand, Tally> tallies = new HashMap<>();

    private static final class Tally {
        private final Display.TextDisplay display;
        private float total;
        private int idle;
        private int cleared;

        private Tally(Display.TextDisplay display) {
            this.display = display;
        }
    }

    private TrainingDummy() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Display.TextDisplay display && isStaleReadout(display)) {
                display.discard();
            } else if (entity instanceof Slime slime && slime.getTags().contains(LEGACY_TAG)) {
                slime.discard();
            } else if (entity instanceof ArmorStand stand && stand.getTags().contains(LEGACY_TAG)) {
                level.getServer().execute(() -> convertLegacy(stand));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(TrainingDummy::tick);
        HeadshotBoxes.register(EntityType.ARMOR_STAND, entity -> entity instanceof ArmorStand stand && isDummy(stand) ? HEAD : null);
    }

    // Whether an armor stand is a placed dummy, read from state the client also sees.
    public static boolean isDummy(ArmorStand stand) {
        return stand.isInvisible() && stand.getItemBySlot(EquipmentSlot.HEAD).is(ITEM);
    }

    // Absorbs one hit: a sneaking player's own swing picks the dummy up, anything else floats the damage dealt after the dummy's armor.
    public static void hit(ServerLevel level, ArmorStand dummy, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player
            && source.getDirectEntity() == player
            && player.isShiftKeyDown()) {
            pickUp(dummy, player);
            return;
        }
        float dealt = afterAbsorb(dummy, source, amount);
        weaponHit(dummy, source, dealt);
        if (dealt > 0) {
            showNumber(level, dummy, source, dealt);
            tally(level, dummy, dealt);
        }
    }

    // The damage left after the dummy's armor and enchantments. Apoli's armor mixin expects state only the normal hurt flow sets, so calling these directly can throw; fall back to the raw amount rather than abort the attacker's shot.
    private static float afterAbsorb(ArmorStand dummy, DamageSource source, float amount) {
        try {
            return dummy.getDamageAfterMagicAbsorb(source, dummy.getDamageAfterArmorAbsorb(source, amount));
        } catch (RuntimeException e) {
            return amount;
        }
    }

    // Runs the held weapon's on-hit logic and the registered damage hooks, which the cancelled hurt would otherwise skip.
    private static void weaponHit(ArmorStand dummy, DamageSource source, float dealt) {
        if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
            ItemStack held = player.getMainHandItem();
            held.getItem().hurtEnemy(held, dummy, player);
        }
        if (dealt > 0) {
            for (DamageHook hook : DAMAGE_HOOKS) {
                hook.onDamage(dummy, source, dealt);
            }
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

    // Floats the number between the lower torso and mid-head on a plane in front of the dummy facing the attacker, at the first spot clear of other numbers and the total.
    private static void showNumber(ServerLevel level, ArmorStand dummy, DamageSource source, float dealt) {
        Vec3 base = dummy.position();
        Vec3 attacker = source.getSourcePosition();
        Vec3 toward = attacker == null ? Vec3.ZERO : attacker.subtract(base).multiply(1, 0, 1);
        Vec3 facing = toward.lengthSqr() > 0 ? toward.normalize() : Vec3.directionFromRotation(0.0F, dummy.getYRot());
        Vec3 sideways = new Vec3(-facing.z, 0, facing.x);
        Vec3 center = base.add(facing.scale(NUMBER_FORWARD)).add(0, (NUMBER_LOWEST + NUMBER_HIGHEST) / 2.0, 0);
        Vec3 pos = center;
        double best = -1.0;
        for (int i = 0; i < NUMBER_PLACEMENT_TRIES; i++) {
            Vec3 candidate = center
                .add(sideways.scale((level.getRandom().nextDouble() - 0.5) * NUMBER_WIDTH))
                .add(0, (level.getRandom().nextDouble() - 0.5) * (NUMBER_HIGHEST - NUMBER_LOWEST), 0);
            double clearance = clearance(level, candidate);
            if (clearance > best) {
                best = clearance;
                pos = candidate;
            }
            if (clearance >= NUMBER_SPACING) {
                break;
            }
        }

        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setPos(pos);
        ChatFormatting color = HeadshotBoxes.isHeadshot(dummy) ? ChatFormatting.GOLD : ChatFormatting.RED;
        display.setText(Component.literal(String.format("-%.1f", dealt)).withStyle(color));
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.addTag(NUMBER_TAG);
        clearBackground(display);
        numbers.put(display, NUMBER_TICKS);
        level.addFreshEntity(display);
    }

    // Distance from a spot to the nearest live damage number, or to the total less its wider margin.
    private static double clearance(ServerLevel level, Vec3 pos) {
        double nearest = Double.MAX_VALUE;
        for (Display.TextDisplay other : numbers.keySet()) {
            if (other.level() == level && !other.isRemoved()) {
                nearest = Math.min(nearest, other.position().distanceTo(pos));
            }
        }
        for (Tally tally : tallies.values()) {
            if (tally.display.level() == level && !tally.display.isRemoved()) {
                nearest = Math.min(nearest, tally.display.position().distanceTo(pos) - (TALLY_SPACING - NUMBER_SPACING));
            }
        }
        return nearest;
    }

    // Adds a hit to the total above the dummy, shading it from yellow toward red as it grows.
    private static void tally(ServerLevel level, ArmorStand dummy, float dealt) {
        Tally tally = tallies.get(dummy);
        if (tally == null || tally.display.isRemoved()) {
            Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
            display.setPos(dummy.position().add(0, TALLY_HEIGHT, 0));
            display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            display.addTag(TALLY_TAG);
            clearBackground(display);
            tally = new Tally(display);
            tallies.put(dummy, tally);
            level.addFreshEntity(display);
        }
        tally.total += dealt;
        tally.idle = TALLY_RESET_TICKS;
        tally.cleared = 0;
        float heat = Math.min(tally.total / TALLY_FULL_RED, 1.0F);
        int green = Math.round(255 * (1.0F - heat));
        tally.display.setText(Component.literal(formatTotal(tally.total)).withStyle(style -> style.withBold(true).withColor(0xFF0000 | green << 8)));
    }

    // Drops the translucent backing quad, which otherwise hides the dummy model drawn behind it.
    private static void clearBackground(Display.TextDisplay display) {
        ((TextDisplayAccessor) display).mmsCombat$setBackgroundColor(0);
    }

    // A whole total without decimals, anything else to one place.
    private static String formatTotal(float total) {
        return total == Math.round(total) ? Integer.toString(Math.round(total)) : String.format("%.1f", total);
    }

    private static boolean isStaleReadout(Display.TextDisplay display) {
        if (display.getTags().contains(NUMBER_TAG)) {
            return !numbers.containsKey(display);
        }
        return display.getTags().contains(TALLY_TAG)
            && tallies.values().stream().noneMatch(tally -> tally.display == display);
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

        Iterator<Map.Entry<ArmorStand, Tally>> tallyIt = tallies.entrySet().iterator();
        while (tallyIt.hasNext()) {
            Map.Entry<ArmorStand, Tally> entry = tallyIt.next();
            Tally tally = entry.getValue();
            if (tally.idle > 0 && --tally.idle == 0) {
                tally.total = 0.0F;
                tally.cleared = TALLY_CLEARED_TICKS;
                tally.display.setText(Component.literal("0").withStyle(style -> style.withBold(true).withColor(ChatFormatting.WHITE)));
            } else if (tally.idle == 0) {
                tally.cleared--;
            }
            if (tally.idle == 0 && tally.cleared <= 0 || entry.getKey().isRemoved() || tally.display.isRemoved()) {
                tally.display.discard();
                tallyIt.remove();
            } else {
                tally.display.setPos(entry.getKey().position().add(0, TALLY_HEIGHT, 0));
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
