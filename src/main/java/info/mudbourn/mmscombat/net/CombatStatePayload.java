package info.mudbourn.mmscombat.net;

import info.mudbourn.mmscombat.MmsCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: the player's current combat flag, the seconds still left on it, and whether they are held by a combat zone, driving the HUD indicator.
public record CombatStatePayload(boolean inCombat, int secondsLeft, boolean inZone) implements CustomPacketPayload {

    public static final Type<CombatStatePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "combat_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        CombatStatePayload::inCombat,
        ByteBufCodecs.VAR_INT,
        CombatStatePayload::secondsLeft,
        ByteBufCodecs.BOOL,
        CombatStatePayload::inZone,
        CombatStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
