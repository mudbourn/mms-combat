package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.config.CombatConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// The vulnerable logout body: a killable armor-stand proxy left when a flagged player logs out, holding a snapshot of their gear. Killed in time, the snapshot drops and the player loses it on next login; left to expire, it vanishes and the player keeps everything.
public final class LogoutBodyManager {

    private static final String BODY_TAG = "mms_combat_logout_body";
    private static final EquipmentSlot[] VISIBLE_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND
    };

    private final Map<UUID, Body> bodies = new HashMap<>();
    private final Set<UUID> pendingDeaths = new HashSet<>();
    private final Set<UUID> liveStandIds = new HashSet<>();

    // Discards any tagged body that is not one of this session's live bodies, so a body orphaned by a restart vanishes the moment its chunk loads instead of lingering.
    public void discardIfOrphan(Entity entity) {
        if (entity instanceof ArmorStand stand
            && stand.getTags().contains(BODY_TAG)
            && !liveStandIds.contains(stand.getUUID())) {
            stand.discard();
        }
    }

    // Snapshots the disconnecting player and spawns their body where they stood.
    public void createBody(ServerPlayer player, int lingerTicks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> snapshot = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                snapshot.add(stack.copy());
            }
        }

        ArmorStand stand = new ArmorStand(level, player.getX(), player.getY(), player.getZ());
        stand.setYRot(player.getYRot());
        stand.setNoGravity(false);
        stand.setInvulnerable(false);
        stand.setCustomName(Component.literal(player.getName().getString()));
        stand.setCustomNameVisible(true);
        stand.addTag(BODY_TAG);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            stand.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
        liveStandIds.add(stand.getUUID());
        level.addFreshEntity(stand);

        long expiry = level.getGameTime() + lingerTicks;
        bodies.put(player.getUUID(), new Body(
            stand.getUUID(),
            snapshot,
            level.dimension().identifier().toString(),
            player.getX(),
            player.getY(),
            player.getZ(),
            expiry));
        MmsCombat.LOG.info("Logout body for {} spawned, linger {} ticks", player.getName().getString(), lingerTicks);
    }

    public void tick(MinecraftServer server) {
        if (bodies.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Body>> it = bodies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Body> entry = it.next();
            Body body = entry.getValue();
            ServerLevel level = resolveLevel(server, body.dimension);
            if (level == null) {
                continue;
            }
            ArmorStand stand = level.getEntity(body.standId) instanceof ArmorStand found ? found : null;
            if (stand == null || stand.isRemoved()) {
                dropSnapshot(level, body);
                pendingDeaths.add(entry.getKey());
                liveStandIds.remove(body.standId);
                it.remove();
                continue;
            }
            if (level.getGameTime() >= body.expiry) {
                stand.discard();
                liveStandIds.remove(body.standId);
                it.remove();
            }
        }
    }

    // Removes a live body and returns the reconnecting player to normal, or empties them if their body was killed while away.
    public void onReconnect(ServerPlayer player) {
        Body body = bodies.remove(player.getUUID());
        if (body != null) {
            liveStandIds.remove(body.standId);
            if (player.level() instanceof ServerLevel level
                && level.getEntity(body.standId) instanceof ArmorStand stand) {
                stand.discard();
            }
        }
        if (pendingDeaths.remove(player.getUUID())) {
            player.getInventory().clearContent();
            player.sendSystemMessage(Component.literal("You were killed after logging out in combat."));
        }
    }

    private void dropSnapshot(ServerLevel level, Body body) {
        ArmorStand stand = level.getEntity(body.standId) instanceof ArmorStand found ? found : null;
        double x = stand != null ? stand.getX() : body.x;
        double y = stand != null ? stand.getY() : body.y;
        double z = stand != null ? stand.getZ() : body.z;
        for (ItemStack stack : body.snapshot) {
            Containers.dropItemStack(level, x, y, z, stack);
        }
    }

    private ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private record Body(UUID standId, List<ItemStack> snapshot, String dimension,
                        double x, double y, double z, long expiry) {
    }
}
