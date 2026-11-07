package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.ItemEnchantments;

// Client to server: the enchant levels the player keeps on each prompted item, in prompt order.
public record LoadoutChoicePayload(List<ItemEnchantments> choices) implements CustomPacketPayload {

    public static final Type<LoadoutChoicePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "loadout_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutChoicePayload> CODEC = StreamCodec.composite(
        ItemEnchantments.STREAM_CODEC.apply(ByteBufCodecs.list(64)),
        LoadoutChoicePayload::choices,
        LoadoutChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
