package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

// Server to client: the player's over-budget items and any saved pick for each, opening the screen where they pick what a logout body loses.
public record LoadoutPromptPayload(List<ItemStack> items, List<Optional<ItemEnchantments>> saved) implements CustomPacketPayload {

    public static final Type<LoadoutPromptPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "loadout_prompt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutPromptPayload> CODEC = StreamCodec.composite(
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        LoadoutPromptPayload::items,
        ByteBufCodecs.optional(ItemEnchantments.STREAM_CODEC).apply(ByteBufCodecs.list(64)),
        LoadoutPromptPayload::saved,
        LoadoutPromptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
