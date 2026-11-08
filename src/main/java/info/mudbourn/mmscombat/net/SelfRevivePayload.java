package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client to server: a downed player started or stopped holding the self-revive key.
public record SelfRevivePayload(boolean held) implements CustomPacketPayload {

    public static final Type<SelfRevivePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "self_revive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelfRevivePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        SelfRevivePayload::held,
        SelfRevivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
