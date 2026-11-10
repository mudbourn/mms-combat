package info.mudbourn.mmscombat.client;

import info.mudbourn.mmscombat.client.compat.SelfReviveHud;
import info.mudbourn.mmscombat.client.compat.SelfReviveKey;
import info.mudbourn.mmscombat.client.hud.CombatHudRenderer;
import info.mudbourn.mmscombat.client.hud.CombatHudState;
import info.mudbourn.mmscombat.client.render.KillstreakCrateRenderer;
import info.mudbourn.mmscombat.client.screen.LoadoutScreen;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.net.LoadoutPromptPayload;
import info.mudbourn.mmscombat.net.SelfReviveStatusPayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;

// Receives combat state from the server, draws the in-combat indicator over the hotbar, and opens the fair-use loadout picker.
public class MmsCombatClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CombatStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> CombatHudState.update(
                payload.inCombat(),
                payload.secondsLeft(),
                payload.inZone(),
                payload.streak(),
                payload.decaySeconds())));
        ClientPlayNetworking.registerGlobalReceiver(LoadoutPromptPayload.TYPE, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new LoadoutScreen(payload.items(), payload.saved()))));
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            CombatHudRenderer.ID,
            new CombatHudRenderer());

        if (FabricLoader.getInstance().isModLoaded("absolutrevive")) {
            ClientPlayNetworking.registerGlobalReceiver(SelfReviveStatusPayload.TYPE, (payload, context) ->
                context.client().execute(() -> SelfReviveHud.update(payload)));
            SelfReviveKey.register();
        }
        EntityRendererRegistry.register(MmsCombatRegistries.KILLSTREAK_CRATE, KillstreakCrateRenderer::new);
    }
}
