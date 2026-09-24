package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.MmsCombat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;

// Owner-bound killstreak rewards that exist only while their owner is alive, conscious and online.
public final class Perishable {

    public static final DataComponentType<UUID> OWNER = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "perishable_owner"),
        DataComponentType.<UUID>builder()
            .persistent(UUIDUtil.CODEC)
            .networkSynchronized(UUIDUtil.STREAM_CODEC)
            .build()
    );

    private static final String REVIVE_MOD = "absolutrevive";
    private static Method reviveModelLookup;
    private static boolean reviveLookupFailed;

    private Perishable() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                purge(player);
            }
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> purge(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> purge(handler.player));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && isPerishable(item.getItem())) {
                item.discard();
            }
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
            isPerishable(player.getItemInHand(hand)) && (entity instanceof ItemFrame || entity instanceof ArmorStand)
                ? InteractionResult.FAIL
                : InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(Perishable::tick);
    }

    // Binds a stack to its owner and tags it so the holder can see it will not last.
    public static void bind(ItemStack stack, ServerPlayer owner) {
        stack.set(OWNER, owner.getUUID());
        List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lines.add(Component.literal("Perishable: lost on death, downing or leaving.")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    // Carries a perishable binding from one stack onto another made from it.
    public static void copyBinding(ItemStack from, ItemStack to) {
        UUID owner = from.get(OWNER);
        if (owner != null) {
            to.set(OWNER, owner);
            to.set(DataComponents.LORE, from.get(DataComponents.LORE));
        }
    }

    public static boolean isPerishable(ItemStack stack) {
        return stack.has(OWNER);
    }

    // Strips every perishable item the player is carrying, including their cursor and crafting grid.
    public static void purge(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isPerishable(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isPerishable(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        for (Slot slot : player.inventoryMenu.slots) {
            if (isPerishable(slot.getItem())) {
                slot.set(ItemStack.EMPTY);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isDowned(player)) {
                purge(player);
                continue;
            }
            guardInventory(player);
            guardOpenContainer(player);
        }
    }

    // Removes perishables that belong to someone else or were tucked into a bundle.
    private static void guardInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isPerishable(stack) && !player.getUUID().equals(stack.get(OWNER))) {
                inventory.setItem(i, ItemStack.EMPTY);
            } else {
                stripBundle(stack);
            }
        }
    }

    // Removes perishables placed into any container other than the player's own inventory or a killstreak crate.
    private static void guardOpenContainer(ServerPlayer player) {
        if (player.containerMenu == player.inventoryMenu) {
            return;
        }
        for (Slot slot : player.containerMenu.slots) {
            Container container = slot.container;
            if (container instanceof Inventory || KillstreakCrateEntity.isCrateContainer(container)) {
                continue;
            }
            if (isPerishable(slot.getItem())) {
                slot.set(ItemStack.EMPTY);
            }
        }
    }

    private static void stripBundle(ItemStack stack) {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.itemCopyStream().noneMatch(Perishable::isPerishable)) {
            return;
        }
        BundleContents.Mutable kept = new BundleContents.Mutable(BundleContents.EMPTY);
        for (ItemStack inner : contents.itemsCopy()) {
            if (!isPerishable(inner)) {
                kept.tryInsert(inner);
            }
        }
        stack.set(DataComponents.BUNDLE_CONTENTS, kept.toImmutable());
    }

    // Whether Absolute Revive has the player downed: unconscious or in critical condition.
    private static boolean isDowned(Player player) {
        Method lookup = reviveModelLookup();
        if (lookup == null) {
            return false;
        }
        try {
            Optional<?> model = (Optional<?>) lookup.invoke(null, player);
            if (model.isEmpty()) {
                return false;
            }
            Object value = model.get();
            return (boolean) value.getClass().getMethod("isUnconscious").invoke(value)
                || (boolean) value.getClass().getMethod("isCriticalConditionActive").invoke(value);
        } catch (ReflectiveOperationException | ClassCastException e) {
            MmsCombat.LOG.error("Absolute Revive downed check failed; disabling it", e);
            reviveLookupFailed = true;
            return false;
        }
    }

    private static Method reviveModelLookup() {
        if (reviveModelLookup != null || reviveLookupFailed) {
            return reviveModelLookup;
        }
        if (!FabricLoader.getInstance().isModLoaded(REVIVE_MOD)) {
            reviveLookupFailed = true;
            return null;
        }
        try {
            reviveModelLookup = Class.forName("goetic.mods.absolutrevive.common.util.CommonUtils")
                .getMethod("getOptionalDamageModel", Player.class);
        } catch (ReflectiveOperationException e) {
            MmsCombat.LOG.error("Absolute Revive is loaded but its damage model lookup was not found", e);
            reviveLookupFailed = true;
        }
        return reviveModelLookup;
    }
}
