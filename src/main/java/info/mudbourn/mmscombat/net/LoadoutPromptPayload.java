package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Server to client: the player's over-budget items, opening the screen where they pick what a logout body loses.
public record LoadoutPromptPayload(List<ItemStack> items) implements CustomPacketPayload {

    public static final Type<LoadoutPromptPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "loadout_prompt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutPromptPayload> CODEC = StreamCodec.composite(
        ItemStack.OPTIONAL_LIST_STREAM_CODEC,
        LoadoutPromptPayload::items,
        LoadoutPromptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
