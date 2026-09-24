package info.mudbourn.mmscombat.armory;

import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

// Sound events for the Armory weapons, backed by sounds.json.
public final class ArmorySounds {

    public static final SoundEvent MURASAMA_INSERT = register("murasama_insert");
    public static final SoundEvent MURASAMA_SHOOT = register("murasama_shoot");
    public static final SoundEvent MURASAMA_SPECIAL_HIT = register("murasama_special_hit");
    public static final SoundEvent MURASAMA_SHEATH = register("murasama_sheath");
    public static final SoundEvent ORIGINIUM_CATALYST_ACTIVATE = register("originium_catalyst_activate");
    public static final SoundEvent PUNISHER_ACTIVATE = register("punisher_activate");
    public static final SoundEvent PUNISHER_DEACTIVATE = register("punisher_deactivate");
    public static final SoundEvent PUNISHER_ATTACK = register("punisher_attack");
    public static final SoundEvent BLOODLETTER_ACTIVATE = register("bloodletter_activate");
    public static final SoundEvent BLOODLETTER_DEACTIVATE = register("bloodletter_deactivate");
    public static final SoundEvent BLOODLETTER_HIT = register("bloodletter_hit");
    public static final SoundEvent CRUCIBLE_ACTIVATE = register("crucible_activate");
    public static final SoundEvent CRUCIBLE_DEACTIVATE = register("crucible_deactivate");
    public static final SoundEvent CRUCIBLE_SWING = register("crucible_swing");
    public static final SoundEvent DRAGON_SLAYER_SWING = register("dragon_slayer_swing");
    public static final SoundEvent EDGE_OF_EXISTENCE_ACTIVATE = register("edge_of_existence_activate");
    public static final SoundEvent EDGE_OF_EXISTENCE_DEACTIVATE = register("edge_of_existence_deactivate");

    private ArmorySounds() {
    }

    public static void register() {
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
