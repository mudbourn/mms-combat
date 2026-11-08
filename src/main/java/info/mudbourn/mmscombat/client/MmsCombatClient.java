package info.mudbourn.mmscombat.client;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.client.compat.SelfReviveKey;
import info.mudbourn.mmscombat.client.hud.CombatHudRenderer;
import info.mudbourn.mmscombat.client.hud.CombatHudState;
import info.mudbourn.mmscombat.client.render.KillstreakCrateRenderer;
import info.mudbourn.mmscombat.client.screen.LoadoutScreen;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.net.LoadoutPromptPayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Receives combat state from the server, draws the in-combat indicator over the hotbar, opens the fair-use loadout picker, and ships the 3D weapon model pack.
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
            context.client().execute(() -> context.client().setScreen(new LoadoutScreen(payload.items()))));
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            CombatHudRenderer.ID,
            new CombatHudRenderer());

        FabricLoader.getInstance().getModContainer(MmsCombat.MOD_ID).ifPresent(mod -> ResourceLoader.registerBuiltinPack(
            Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "weapon_models"),
            mod,
            Component.literal("MMS 3D Weapon Models"),
            PackActivationType.DEFAULT_ENABLED));
        if (FabricLoader.getInstance().isModLoaded("absolutrevive")) {
            SelfReviveKey.register();
        }
        EntityRendererRegistry.register(MmsCombatRegistries.KILLSTREAK_CRATE, KillstreakCrateRenderer::new);
    }
}
