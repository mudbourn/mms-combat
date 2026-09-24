package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: whether an entity is in Non-Existence and must not be drawn at all.
public record NonexistencePayload(int entityId, boolean hidden) implements CustomPacketPayload {

    public static final Type<NonexistencePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "nonexistence"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NonexistencePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        NonexistencePayload::entityId,
        ByteBufCodecs.BOOL,
        NonexistencePayload::hidden,
        NonexistencePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
