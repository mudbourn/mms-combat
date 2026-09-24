package info.mudbourn.mmscombat;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmoryEffects;
import info.mudbourn.mmscombat.armory.ArmoryEvents;
import info.mudbourn.mmscombat.armory.ArmoryItems;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import info.mudbourn.mmscombat.combatlog.CombatManager;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.killstreak.Perishable;
import info.mudbourn.mmscombat.killstreak.StreakManager;
import info.mudbourn.mmscombat.command.MmsCombatCommands;
import info.mudbourn.mmscombat.net.CombatStatePayload;
import info.mudbourn.mmscombat.net.NonexistencePayload;
import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import info.mudbourn.mmscombat.zone.ZoneStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Wires the subsystems: combat logging, combat zones, the HUD payload, killstreak rewards, and the Armory weapons.
public class MmsCombat implements ModInitializer {
    public static final String MOD_ID = "mms_combat";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CombatConfig.load();
        MmsCombatRegistries.register();
        ArmoryComponents.register();
        ArmorySounds.register();
        ArmoryEffects.register();
        ArmoryItems.register();
        ArmoryEvents.register();
        PayloadTypeRegistry.playS2C().register(CombatStatePayload.TYPE, CombatStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NonexistencePayload.TYPE, NonexistencePayload.CODEC);
        CombatManager.register();
        StreakManager.register();
        Perishable.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            MmsCombatCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(ZoneStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZoneStore.save());

        LOG.info("MMS Combat loaded");
    }
}
