package info.mudbourn.mmscombat.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import goetic.mods.absolutrevive.common.EventHandler;
import info.mudbourn.mmscombat.net.SelfRevivePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

// Tells the server when the local player starts or stops holding the self-revive key while downed.
public final class SelfReviveKey {

    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mms_combat", "revive"));

    private static final KeyMapping KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.mms_combat.self_revive",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        CATEGORY
    ));

    private static boolean held;

    private SelfReviveKey() {
    }

    public static KeyMapping key() {
        return KEY;
    }

    public static void register() {
        SelfReviveHud.register();
        ClientTickEvents.START_CLIENT_TICK.register(SelfReviveKey::tick);
    }

    private static void tick(Minecraft client) {
        while (KEY.consumeClick()) {
            continue;
        }
        boolean downed = client.player != null && EventHandler.isUnconscious(client.player);
        boolean holding = downed && client.screen == null && KEY.isDown();
        if (holding != held && client.getConnection() != null) {
            ClientPlayNetworking.send(new SelfRevivePayload(holding));
        }
        held = holding;
    }
}
