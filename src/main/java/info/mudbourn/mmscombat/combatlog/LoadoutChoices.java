package info.mudbourn.mmscombat.combatlog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.LoadoutChoicePayload;
import info.mudbourn.mmscombat.net.LoadoutPromptPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

// Per-player picks of which enchant levels a logout body loses, saved on the player and asked for again only when an item's enchants change.
public final class LoadoutChoices {

    private static final int MAX_PICKS = 64;
    private static final Codec<Choice> CHOICE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Choice::item),
        ItemEnchantments.CODEC.fieldOf("original").forGetter(Choice::original),
        ItemEnchantments.CODEC.fieldOf("levels").forGetter(Choice::levels)
    ).apply(instance, Choice::new));
    private static final AttachmentType<List<Choice>> PICKS = AttachmentRegistry.<List<Choice>>builder()
        .persistent(CHOICE_CODEC.listOf())
        .copyOnDeath()
        .initializer(List::of)
        .buildAndRegister(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "loadout_picks"));
    private static final Map<UUID, List<ItemStack>> prompted = new HashMap<>();
    private static final Map<UUID, List<Choice>> chosen = new HashMap<>();

    private LoadoutChoices() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(LoadoutChoicePayload.TYPE, (payload, context) ->
            context.server().execute(() -> receive(context.player(), payload.choices())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            chosen.put(handler.getPlayer().getUUID(), handler.getPlayer().getAttachedOrCreate(PICKS)));
    }

    // Opens the picker if the player carries over-budget items with no matching pick yet.
    public static void prompt(ServerPlayer player) {
        if (CombatConfig.get().nerfLogoutGear) {
            open(player, false);
        }
    }

    // Opens the picker for every over-budget item the player carries, filled in with their saved picks; returns how many items it shows.
    public static int edit(ServerPlayer player) {
        return CombatConfig.get().nerfLogoutGear ? open(player, true) : 0;
    }

    // Forgets every saved pick, so the picker asks again on the next combat entry.
    public static void clear(ServerPlayer player) {
        chosen.remove(player.getUUID());
        player.removeAttached(PICKS);
    }

    private static int open(ServerPlayer player, boolean includePicked) {
        List<ItemStack> items = new ArrayList<>();
        List<Optional<ItemEnchantments>> saved = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!FairPlayEnchants.overBudget(stack) || items.stream().anyMatch(item -> sameLoadout(item, stack))) {
                continue;
            }
            Choice choice = find(player.getUUID(), stack);
            if (choice == null || includePicked) {
                items.add(stack.copyWithCount(1));
                saved.add(choice == null ? Optional.empty() : Optional.of(choice.levels));
            }
        }
        if (items.isEmpty() || items.size() > 64) {
            return 0;
        }
        prompted.put(player.getUUID(), items);
        ServerPlayNetworking.send(player, new LoadoutPromptPayload(items, saved));
        return items.size();
    }

    private static boolean sameLoadout(ItemStack a, ItemStack b) {
        return a.is(b.getItem()) && a.getEnchantments().equals(b.getEnchantments());
    }

    // Copies a stack for a logout body, using the owner's pick when one matches and the automatic nerf otherwise.
    public static ItemStack fairCopy(UUID owner, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!CombatConfig.get().nerfLogoutGear) {
            return copy;
        }
        Choice choice = find(owner, stack);
        if (choice != null) {
            FairPlayEnchants.apply(copy, choice.levels);
        } else {
            FairPlayEnchants.nerf(copy);
        }
        return copy;
    }

    // The levels a stack fights with against another player: the first matching pick among the fighters, or the automatic nerf.
    public static ItemEnchantments fairLevels(UUID[] owners, ItemStack stack) {
        ItemStack copy = stack.copy();
        for (UUID owner : owners) {
            Choice choice = find(owner, stack);
            if (choice != null) {
                FairPlayEnchants.apply(copy, choice.levels);
                return copy.getEnchantments();
            }
        }
        FairPlayEnchants.nerf(copy);
        return copy.getEnchantments();
    }

    private static void receive(ServerPlayer player, List<ItemEnchantments> levels) {
        List<ItemStack> items = prompted.remove(player.getUUID());
        if (items == null || items.size() != levels.size()) {
            return;
        }
        List<Choice> picks = new ArrayList<>(chosen.getOrDefault(player.getUUID(), List.of()));
        int rejected = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack original = items.get(i);
            if (!FairPlayEnchants.fits(original, levels.get(i))) {
                rejected++;
                continue;
            }
            picks.removeIf(pick -> pick.matches(original));
            picks.add(new Choice(original.getItem(), original.getEnchantments(), levels.get(i)));
        }
        while (picks.size() > MAX_PICKS) {
            picks.remove(0);
        }
        chosen.put(player.getUUID(), List.copyOf(picks));
        player.setAttached(PICKS, List.copyOf(picks));
        if (rejected > 0) {
            player.displayClientMessage(Component.literal(
                rejected + " pick(s) broke the fair-use rules and will be nerfed automatically."), false);
        }
    }

    private static Choice find(UUID owner, ItemStack stack) {
        for (Choice choice : chosen.getOrDefault(owner, List.of())) {
            if (choice.matches(stack)) {
                return choice;
            }
        }
        return null;
    }

    // One saved pick: the item and the enchants it had when picked, and the levels it keeps.
    private record Choice(Item item, ItemEnchantments original, ItemEnchantments levels) {

        boolean matches(ItemStack stack) {
            return stack.is(item) && stack.getEnchantments().equals(original);
        }
    }
}
