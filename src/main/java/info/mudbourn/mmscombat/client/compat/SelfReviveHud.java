package info.mudbourn.mmscombat.client.compat;

import goetic.mods.absolutrevive.common.EventHandler;
import info.mudbourn.mmscombat.net.SelfReviveStatusPayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.Event;
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

    // Whether the last status from the server said keepInventory is on.
    public static boolean keepInventory() {
        return status != null && status.keepInventory();
    }

    public static void update(SelfReviveStatusPayload payload) {
        Minecraft client = Minecraft.getInstance();
        status = payload;
        receivedAt = client.level == null ? 0 : client.level.getGameTime();
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
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
        String seconds = seconds(status.duration());
        Component text;
        if (status.ticks() >= 0) {
            text = Component.literal("Getting up: " + seconds(status.ticks()) + " / " + seconds + " s");
        } else {
            text = Component.literal("Hold ")
                .append(SelfReviveKey.key().getTranslatedKeyMessage())
                .append(" for " + seconds + " seconds to get up.");
        }
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 75;
        graphics.drawCenteredString(client.font, text, x, y, 0xFFFFFFFF);
    }
}
