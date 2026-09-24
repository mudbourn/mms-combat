package info.mudbourn.mmscombat.armory;

import com.mojang.serialization.Codec;
import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

// Per-stack weapon state, synced to clients so item models can pick textures from it.
public final class ArmoryComponents {

    public static final DataComponentType<Integer> ABILITY_TICK = counter("ability_tick");
    public static final DataComponentType<Integer> STORED_BLOOD = counter("stored_blood");
    public static final DataComponentType<Integer> USAGES = counter("usages");
    public static final DataComponentType<Integer> STRIKES_REMAINING = counter("strikes_remaining");
    public static final DataComponentType<Long> ANIMATION_START = timestamp("animation_start");
    public static final DataComponentType<Long> CHARGE_START = timestamp("charge_start");
    public static final DataComponentType<Float> DAMAGE_DEALT = register(
        "damage_dealt",
        DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .ignoreSwapAnimation()
            .build()
    );

    private ArmoryComponents() {
    }

    public static void register() {
    }

    private static DataComponentType<Integer> counter(String path) {
        return register(
            path,
            DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .ignoreSwapAnimation()
                .build()
        );
    }

    private static DataComponentType<Long> timestamp(String path) {
        return register(
            path,
            DataComponentType.<Long>builder()
                .persistent(Codec.LONG)
                .networkSynchronized(ByteBufCodecs.VAR_LONG)
                .ignoreSwapAnimation()
                .build()
        );
    }

    private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
        return Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, path),
            type
        );
    }
}
