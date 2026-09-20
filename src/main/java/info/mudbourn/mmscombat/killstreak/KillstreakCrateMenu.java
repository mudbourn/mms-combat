package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// The four-slot crate menu: the reward slots on top, the player's inventory below.
public class KillstreakCrateMenu extends AbstractContainerMenu {

    private static final int CRATE_SLOTS = 4;

    private final Container crate;

    public KillstreakCrateMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CRATE_SLOTS));
    }

    public KillstreakCrateMenu(int containerId, Inventory playerInventory, Container crate) {
        super(MmsCombatRegistries.KILLSTREAK_CRATE_MENU, containerId);
        this.crate = crate;

        int rowStartX = 88 - (CRATE_SLOTS * 18) / 2;
        for (int slot = 0; slot < CRATE_SLOTS; slot++) {
            addSlot(new Slot(crate, slot, rowStartX + slot * 18, 20));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 109));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.crate.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        int playerStart = CRATE_SLOTS;
        int playerEnd = this.slots.size();
        if (index < CRATE_SLOTS) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, CRATE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
