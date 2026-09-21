package info.mudbourn.mmscombat.registry;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.killstreak.KillstreakCrateEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

// The mod's registered content: the killstreak crate entity, which opens a vanilla chest menu, and the combat-state cues.
public final class MmsCombatRegistries {

    public static final EntityType<KillstreakCrateEntity> KILLSTREAK_CRATE = buildCrateType();
    public static final SoundEvent COMBAT_START = SoundEvent.createVariableRangeEvent(id("combat_start"));
    public static final SoundEvent COMBAT_END = SoundEvent.createVariableRangeEvent(id("combat_end"));

    private MmsCombatRegistries() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id("killstreak_crate"), KILLSTREAK_CRATE);
        Registry.register(BuiltInRegistries.SOUND_EVENT, id("combat_start"), COMBAT_START);
        Registry.register(BuiltInRegistries.SOUND_EVENT, id("combat_end"), COMBAT_END);
    }

    private static EntityType<KillstreakCrateEntity> buildCrateType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("killstreak_crate"));
        return EntityType.Builder
            .of(KillstreakCrateEntity::new, MobCategory.MISC)
            // Tall enough to reach the model, which the renderer floats up to ~1.2 blocks above the entity origin, so the interact box sits under the crate the player sees.
            .sized(2.0F, 1.4F)
            // One position packet every 3 ticks, matching the crate's 3-tick client interpolation window so each glide finishes as the next update lands.
            .updateInterval(3)
            .build(key);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, path);
    }
}
