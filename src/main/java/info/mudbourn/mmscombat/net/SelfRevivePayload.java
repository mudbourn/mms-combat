package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client to server: a downed player pressed the inventory key to start a self-revive.
public record SelfRevivePayload() implements CustomPacketPayload {

    public static final SelfRevivePayload INSTANCE = new SelfRevivePayload();

    public static final Type<SelfRevivePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "self_revive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelfRevivePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
