package info.mudbourn.mmscombat;

import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.killstreak.StreakManager;
import info.mudbourn.mmscombat.command.MmsCombatCommands;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.zone.ZoneStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Wires the four subsystems: combat logging, combat zones, the HUD payload, and killstreak rewards.
public class MmsCombat implements ModInitializer {
    public static final String MOD_ID = "mms_combat";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CombatConfig.load();
        PayloadTypeRegistry.playS2C().register(CombatStatePayload.TYPE, CombatStatePayload.CODEC);
        CombatManager.register();
        StreakManager.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            MmsCombatCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(ZoneStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZoneStore.save());

        LOG.info("MMS Combat loaded");
    }
}
