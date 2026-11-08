package info.mudbourn.mmscombat.client.compat;

import goetic.mods.absolutrevive.common.EventHandler;
import info.mudbourn.mmscombat.net.SelfRevivePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

// Turns the inventory key into a self-revive request while the local player is downed.
public final class SelfReviveKey {

    private SelfReviveKey() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(SelfReviveKey::tick);
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.screen != null || !EventHandler.isUnconscious(client.player)) {
            return;
        }
        boolean pressed = false;
        while (client.options.keyInventory.consumeClick()) {
            pressed = true;
        }
        if (pressed) {
            ClientPlayNetworking.send(SelfRevivePayload.INSTANCE);
        }
    }
}
