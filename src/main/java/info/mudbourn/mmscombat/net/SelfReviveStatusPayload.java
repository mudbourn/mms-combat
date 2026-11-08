package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: a downed player's self-revive uses left and get-up progress (-1 when idle).
public record SelfReviveStatusPayload(int usesLeft, int percent) implements CustomPacketPayload {

    public static final Type<SelfReviveStatusPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "self_revive_status"));

    public static final StreamCodec<ByteBuf, SelfReviveStatusPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SelfReviveStatusPayload::usesLeft,
        ByteBufCodecs.VAR_INT,
        SelfReviveStatusPayload::percent,
        SelfReviveStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
