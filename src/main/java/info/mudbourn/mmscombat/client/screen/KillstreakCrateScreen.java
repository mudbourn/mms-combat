package info.mudbourn.mmscombat.client.screen;

import info.mudbourn.mmscombat.killstreak.KillstreakCrateMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// The Killstreak Crate screen. Its panel and slots are drawn as flat rectangles, so it needs no bespoke GUI texture.
public class KillstreakCrateScreen extends AbstractContainerScreen<KillstreakCrateMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_EDGE = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_EDGE = 0xFF373737;

    public KillstreakCrateScreen(KillstreakCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x - 1, y - 1, x + this.imageWidth + 1, y + this.imageHeight + 1, PANEL_EDGE);
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_EDGE);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT);
        }
    }
}
