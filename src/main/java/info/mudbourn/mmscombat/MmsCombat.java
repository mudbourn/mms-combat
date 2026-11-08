package info.mudbourn.mmscombat;

import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.combatlog.LoadoutChoices;
import info.mudbourn.mmscombat.revive.SelfRevive;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.killstreak.Perishable;
import info.mudbourn.mmscombat.killstreak.RewardPool;
import info.mudbourn.mmscombat.killstreak.StreakManager;
import info.mudbourn.mmscombat.command.MmsCombatCommands;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.net.LoadoutChoicePayload;
import info.mudbourn.mmscombat.net.LoadoutPromptPayload;
import info.mudbourn.mmscombat.net.SelfRevivePayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import info.mudbourn.mmscombat.zone.ZoneStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Wires the subsystems: combat logging, combat zones, the HUD payload, and killstreak rewards.
public class MmsCombat implements ModInitializer {
    public static final String MOD_ID = "mms_combat";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CombatConfig.load();
        MmsCombatRegistries.register();
        PayloadTypeRegistry.playS2C().register(CombatStatePayload.TYPE, CombatStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LoadoutPromptPayload.TYPE, LoadoutPromptPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LoadoutChoicePayload.TYPE, LoadoutChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelfRevivePayload.TYPE, SelfRevivePayload.CODEC);
        LoadoutChoices.register();
        CombatManager.register();
        StreakManager.register();
        Perishable.register();
        RewardPool.register();
        if (FabricLoader.getInstance().isModLoaded("absolutrevive")) {
            SelfRevive.register();
            ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SelfRevive.reset(newPlayer));
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            MmsCombatCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(ZoneStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZoneStore.save());

        LOG.info("MMS Combat loaded");
    }
}
