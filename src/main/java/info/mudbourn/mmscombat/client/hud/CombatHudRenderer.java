package info.mudbourn.mmscombat.client.hud;

import info.mudbourn.mmscombat.MmsCombat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Draws the in-combat dagger sprite and the remaining-seconds text above the hotbar while the client is flagged.
public final class CombatHudRenderer implements HudElement {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "combat_indicator");

    private static final Identifier SPRITE =
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "textures/gui/in_combat.png");
    private static final int SOURCE_SIZE = 800;
    private static final int ICON_SIZE = 28;
    private static final int TEXT_COLOR = 0xFFFF5555;

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!CombatHudState.inCombat()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int iconX = screenWidth / 2 - ICON_SIZE / 2;
        int iconY = screenHeight - 75;

        context.blit(
            RenderPipelines.GUI_TEXTURED,
            SPRITE,
            iconX,
            iconY,
            0.0F,
            0.0F,
            ICON_SIZE,
            ICON_SIZE,
            SOURCE_SIZE,
            SOURCE_SIZE,
            SOURCE_SIZE,
            SOURCE_SIZE);

        Component label = Component.literal(Integer.toString(CombatHudState.secondsLeft()) + "s");
        int textX = iconX + ICON_SIZE + 4;
        int textY = iconY + ICON_SIZE / 2 - font.lineHeight / 2;
        context.drawString(font, label, textX, textY, TEXT_COLOR, true);
    }
}
