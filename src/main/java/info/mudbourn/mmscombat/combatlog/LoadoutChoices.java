package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.LoadoutChoicePayload;
import info.mudbourn.mmscombat.net.LoadoutPromptPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

// Per-player picks of which enchant levels a logout body loses, asked for on entering combat and applied when the body is made.
public final class LoadoutChoices {

    private static final Map<UUID, List<ItemStack>> prompted = new HashMap<>();
    private static final Map<UUID, List<Choice>> chosen = new HashMap<>();

    private LoadoutChoices() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(LoadoutChoicePayload.TYPE, (payload, context) ->
            context.server().execute(() -> receive(context.player(), payload.choices())));
    }

    // Opens the picker if the player carries over-budget items with no matching pick yet.
    public static void prompt(ServerPlayer player) {
        if (!CombatConfig.get().nerfLogoutGear) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (FairPlayEnchants.overBudget(stack) && find(player.getUUID(), stack) == null) {
                items.add(stack.copyWithCount(1));
            }
        }
        if (items.isEmpty() || items.size() > 64) {
            return;
        }
        prompted.put(player.getUUID(), items);
        ServerPlayNetworking.send(player, new LoadoutPromptPayload(items));
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

    private static void receive(ServerPlayer player, List<ItemEnchantments> levels) {
        List<ItemStack> items = prompted.remove(player.getUUID());
        if (items == null || items.size() != levels.size()) {
            return;
        }
        List<Choice> picks = chosen.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        int rejected = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack original = items.get(i);
            if (!FairPlayEnchants.fits(original, levels.get(i))) {
                rejected++;
                continue;
            }
            picks.removeIf(pick -> ItemStack.isSameItemSameComponents(pick.original, original));
            picks.add(new Choice(original, levels.get(i)));
        }
        if (rejected > 0) {
            player.displayClientMessage(Component.literal(
                rejected + " pick(s) broke the fair-use rules and will be nerfed automatically."), false);
        }
    }

    private static Choice find(UUID owner, ItemStack stack) {
        for (Choice choice : chosen.getOrDefault(owner, List.of())) {
            if (ItemStack.isSameItemSameComponents(choice.original, stack)) {
                return choice;
            }
        }
        return null;
    }

    private record Choice(ItemStack original, ItemEnchantments levels) {
    }
}
