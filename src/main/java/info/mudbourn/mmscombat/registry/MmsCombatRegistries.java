package info.mudbourn.mmscombat.registry;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.killstreak.KillstreakCrateEntity;
import info.mudbourn.mmscombat.killstreak.KillstreakCrateMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

// The mod's registered content: the killstreak crate entity and its 4-slot menu.
public final class MmsCombatRegistries {

    public static final EntityType<KillstreakCrateEntity> KILLSTREAK_CRATE = buildCrateType();
    public static final MenuType<KillstreakCrateMenu> KILLSTREAK_CRATE_MENU =
        new MenuType<>(KillstreakCrateMenu::new, FeatureFlags.VANILLA_SET);

    private MmsCombatRegistries() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id("killstreak_crate"), KILLSTREAK_CRATE);
        Registry.register(BuiltInRegistries.MENU, id("killstreak_crate"), KILLSTREAK_CRATE_MENU);
    }

    private static EntityType<KillstreakCrateEntity> buildCrateType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("killstreak_crate"));
        return EntityType.Builder
            .of(KillstreakCrateEntity::new, MobCategory.MISC)
            .sized(2.0F, 0.6F)
            .build(key);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, path);
    }
}
