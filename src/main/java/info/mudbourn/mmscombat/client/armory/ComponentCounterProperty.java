package info.mudbourn.mmscombat.client.armory;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

// The mms_combat:counter range property: the value of an integer data component, or 0 when absent.
public record ComponentCounterProperty(DataComponentType<?> component) implements RangeSelectItemModelProperty {

    public static final MapCodec<ComponentCounterProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().fieldOf("component").forGetter(ComponentCounterProperty::component)
        ).apply(instance, ComponentCounterProperty::new)
    );

    public static void register() {
        RangeSelectItemModelProperties.ID_MAPPER.put(
            Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "counter"),
            MAP_CODEC
        );
    }

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        return stack.get(this.component) instanceof Integer value ? value : 0.0F;
    }

    @Override
    public MapCodec<ComponentCounterProperty> type() {
        return MAP_CODEC;
    }
}
