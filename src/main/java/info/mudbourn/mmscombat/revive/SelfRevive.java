package info.mudbourn.mmscombat.revive;

import goetic.mods.absolutrevive.common.damagesystem.PlayerDamageModel;
import goetic.mods.absolutrevive.common.util.CommonUtils;
import info.mudbourn.mmscombat.config.CombatConfig;
import info.mudbourn.mmscombat.net.SelfRevivePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// Per-player self-revive progress and the per-life use budget.
public final class SelfRevive {

    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();
    private static final Map<UUID, Integer> USES = new HashMap<>();

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
        if (USES.getOrDefault(id, 0) >= CombatConfig.get().selfReviveUses) {
            showStatus(player, "You're too injured to get up.", ChatFormatting.RED);
            return;
        }
        PROGRESS.put(id, 0);
    }

    public static void tick(ServerPlayer player, PlayerDamageModel model) {
        UUID id = player.getUUID();
        if (!PROGRESS.containsKey(id)) {
            return;
        }
        if (!model.isUnconscious() || !model.canBeRescued()) {
            PROGRESS.remove(id);
            return;
        }
        int ticks = PROGRESS.merge(id, 1, Integer::sum);
        int duration = CombatConfig.get().selfReviveTicks;
        if (ticks < duration) {
            showStatus(player, "Getting up... " + ticks * 100 / duration + "%", ChatFormatting.YELLOW);
            return;
        }
        PROGRESS.remove(id);
        if (model.rescueFromCriticalState(player)) {
            int left = CombatConfig.get().selfReviveUses - USES.merge(id, 1, Integer::sum);
            showStatus(player, "You got back up. Self-revives left: " + left, ChatFormatting.GREEN);
        }
    }

    private static void showStatus(ServerPlayer player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    public static void reset(ServerPlayer player) {
        UUID id = player.getUUID();
        PROGRESS.remove(id);
        USES.remove(id);
    }
}
