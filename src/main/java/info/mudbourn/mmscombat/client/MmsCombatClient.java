package info.mudbourn.mmscombat.client;

import info.mudbourn.mmscombat.client.hud.CombatHudRenderer;
import info.mudbourn.mmscombat.client.hud.CombatHudState;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

// Receives combat state from the server and draws the in-combat indicator over the hotbar.
public class MmsCombatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CombatStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> CombatHudState.update(payload.inCombat(), payload.secondsLeft())));
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            CombatHudRenderer.ID,
            new CombatHudRenderer());
    }
}
