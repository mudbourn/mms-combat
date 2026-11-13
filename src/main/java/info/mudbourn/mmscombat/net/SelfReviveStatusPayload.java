package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: whether a downed player's injuries are severe, and their get-up progress in ticks (-1 when idle) out of the full duration, and the keepInventory rule.
public record SelfReviveStatusPayload(boolean severe, int ticks, int duration, boolean keepInventory) implements CustomPacketPayload {

    public static final Type<SelfReviveStatusPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "self_revive_status"));

    public static final StreamCodec<ByteBuf, SelfReviveStatusPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        SelfReviveStatusPayload::severe,
        ByteBufCodecs.VAR_INT,
        SelfReviveStatusPayload::ticks,
        ByteBufCodecs.VAR_INT,
        SelfReviveStatusPayload::duration,
        ByteBufCodecs.BOOL,
        SelfReviveStatusPayload::keepInventory,
        SelfReviveStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
