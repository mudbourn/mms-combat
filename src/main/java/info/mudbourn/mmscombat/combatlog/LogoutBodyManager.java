package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.MmsCombat;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

// The vulnerable logout body: a killable mannequin left when a flagged player logs out, wearing their skin, size, and gear and holding a snapshot of their inventory. Killed in time, the snapshot drops and the player loses it on next login; left to expire, it vanishes and the player keeps everything.
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
    private final Set<UUID> liveBodyIds = new HashSet<>();

    // Discards any tagged body that is not one of this session's live bodies, so a body orphaned by a restart vanishes the moment its chunk loads instead of lingering.
    public void discardIfOrphan(Entity entity) {
        if (entity instanceof Mannequin mannequin
            && mannequin.getTags().contains(BODY_TAG)
            && !liveBodyIds.contains(mannequin.getUUID())) {
            mannequin.discard();
        }
    }

    // Snapshots the disconnecting player and spawns a mannequin of them where they stood; a persistent body never times out and stands until it is killed or the player returns.
    public void createBody(ServerPlayer player, int lingerTicks, boolean persistent) {
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

        Mannequin body = new Mannequin(EntityType.MANNEQUIN, level);
        float yaw = player.getYRot();
        body.snapTo(player.getX(), player.getY(), player.getZ(), yaw, player.getXRot());
        body.setYBodyRot(yaw);
        body.setYHeadRot(player.getYHeadRot());
        body.setProfile(ResolvableProfile.createResolved(player.getGameProfile()));
        body.setImmovable(true);
        body.setNoGravity(true);
        body.setCustomName(Component.literal(player.getName().getString()));
        body.setCustomNameVisible(true);
        body.addTag(BODY_TAG);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            body.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
        copyAttribute(player, body, Attributes.SCALE);
        copyAttribute(player, body, Attributes.MAX_HEALTH);
        body.refreshDimensions();
        body.setHealth(player.getHealth());
        liveBodyIds.add(body.getUUID());
        level.addFreshEntity(body);
        LogoutBodyEvents.CREATED.invoker().onCreated(player, body);

        long expiry = level.getGameTime() + lingerTicks;
        bodies.put(player.getUUID(), new Body(
            body.getUUID(),
            snapshot,
            level.dimension().identifier().toString(),
            player.getX(),
            player.getY(),
            player.getZ(),
            expiry,
            persistent));
        MmsCombat.LOG.info("Logout body for {} spawned, linger {} ticks, persistent {}",
            player.getName().getString(), lingerTicks, persistent);
    }

    // Mirrors the player's effective value for an attribute onto the body's base value, so mod-driven size (Origins furs, say) and max health carry over.
    private static void copyAttribute(ServerPlayer player, Mannequin body,
                                      net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        AttributeInstance instance = body.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(player.getAttributeValue(attribute));
        }
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
            Mannequin mannequin = level.getEntity(body.bodyId) instanceof Mannequin found ? found : null;
            if (mannequin == null || mannequin.isRemoved()) {
                dropSnapshot(level, body);
                pendingDeaths.add(entry.getKey());
                liveBodyIds.remove(body.bodyId);
                it.remove();
                continue;
            }
            if (!body.persistent && level.getGameTime() >= body.expiry) {
                mannequin.discard();
                liveBodyIds.remove(body.bodyId);
                it.remove();
            }
        }
    }

    // Removes a live body and returns the reconnecting player to normal, or empties them if their body was killed while away.
    public void onReconnect(ServerPlayer player) {
        Body body = bodies.remove(player.getUUID());
        if (body != null) {
            liveBodyIds.remove(body.bodyId);
            if (player.level() instanceof ServerLevel level
                && level.getEntity(body.bodyId) instanceof Mannequin mannequin) {
                mannequin.discard();
            }
        }
        if (pendingDeaths.remove(player.getUUID())) {
            player.getInventory().clearContent();
            player.sendSystemMessage(Component.literal("You were killed after logging out in combat."));
        }
    }

    private void dropSnapshot(ServerLevel level, Body body) {
        Mannequin mannequin = level.getEntity(body.bodyId) instanceof Mannequin found ? found : null;
        double x = mannequin != null ? mannequin.getX() : body.x;
        double y = mannequin != null ? mannequin.getY() : body.y;
        double z = mannequin != null ? mannequin.getZ() : body.z;
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

    private record Body(UUID bodyId, List<ItemStack> snapshot, String dimension,
                        double x, double y, double z, long expiry, boolean persistent) {
    }
}
