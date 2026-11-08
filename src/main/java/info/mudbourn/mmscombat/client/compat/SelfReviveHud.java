package info.mudbourn.mmscombat.client.compat;

import goetic.mods.absolutrevive.common.EventHandler;
import info.mudbourn.mmscombat.net.SelfReviveStatusPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Draws the self-revive prompt or progress after Absolute Revive's downed overlay so it stays readable.
public final class SelfReviveHud {

    private static final Identifier LATE = Identifier.fromNamespaceAndPath("mms_combat", "after_revive_overlay");

    private static final int STALE_TICKS = 20;

    private static SelfReviveStatusPayload status;

    private static long receivedAt;

    private SelfReviveHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        HudRenderCallback.EVENT.register(LATE, SelfReviveHud::render);
    }

    public static void update(SelfReviveStatusPayload payload) {
        Minecraft client = Minecraft.getInstance();
        status = payload;
        receivedAt = client.level == null ? 0 : client.level.getGameTime();
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (status == null
            || client.player == null
            || client.level == null
            || client.options.hideGui
            || client.level.getGameTime() - receivedAt > STALE_TICKS
            || !EventHandler.isUnconscious(client.player)) {
            return;
        }
        Component text;
        if (status.percent() >= 0) {
            text = Component.literal("Getting up... " + status.percent() + "%").withStyle(ChatFormatting.YELLOW);
        } else if (status.usesLeft() > 0) {
            text = Component.literal("Press [")
                .append(SelfReviveKey.key().getTranslatedKeyMessage())
                .append("] to get up on your own (" + status.usesLeft() + " left)")
                .withStyle(ChatFormatting.GOLD);
        } else {
            text = Component.literal("Too injured to get up on your own").withStyle(ChatFormatting.RED);
        }
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 20;
        graphics.drawCenteredString(client.font, text, x, y, 0xFFFFFFFF);
    }
}
