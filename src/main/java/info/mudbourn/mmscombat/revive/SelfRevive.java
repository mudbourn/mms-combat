package info.mudbourn.mmscombat.revive;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.SelfRevivePayload;
import info.mudbourn.mmscombat.net.SelfReviveStatusPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

// Per-player self-revive progress and the per-life use budget.
public final class SelfRevive {

    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> USES = new HashMap<>();
    private static final int STATUS_INTERVAL = 10;

    private SelfRevive() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SelfRevivePayload.TYPE, (payload, context) ->
            start(context.player()));
    }

    private static void start(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!(CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model)
            || !model.isUnconscious()
            || !model.canBeRescued()
            || PROGRESS.containsKey(id)) {
            return;
        }
        if (usesLeft(id) <= 0) {
            return;
        }
        PROGRESS.put(id, 0);
    }

    public static void tick(ServerPlayer player, PlayerDamageModel model) {
        UUID id = player.getUUID();
        if (!model.isUnconscious() || !model.canBeRescued()) {
            PROGRESS.remove(id);
            return;
        }
        if (!PROGRESS.containsKey(id)) {
            if (player.tickCount % STATUS_INTERVAL == 0) {
                sendStatus(player, usesLeft(id), -1);
            }
            return;
        }
        int ticks = PROGRESS.merge(id, 1, Integer::sum);
        int duration = CombatConfig.get().selfReviveTicks;
        if (ticks < duration) {
            sendStatus(player, usesLeft(id), ticks * 100 / duration);
            return;
        }
        PROGRESS.remove(id);
        if (model.rescueFromCriticalState(player)) {
            int left = CombatConfig.get().selfReviveUses - USES.merge(id, 1, Integer::sum);
            showStatus(player, "You got back up. Self-revives left: " + left, ChatFormatting.GREEN);
        }
    }

    private static int usesLeft(UUID id) {
        return CombatConfig.get().selfReviveUses - USES.getOrDefault(id, 0);
    }

    private static void sendStatus(ServerPlayer player, int usesLeft, int percent) {
        ServerPlayNetworking.send(player, new SelfReviveStatusPayload(Math.max(0, usesLeft), percent));
    }

    private static void showStatus(ServerPlayer player, String text, ChatFormatting color) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(text).withStyle(color)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }

    public static void reset(ServerPlayer player) {
        UUID id = player.getUUID();
        PROGRESS.remove(id);
        USES.remove(id);
    }
}
