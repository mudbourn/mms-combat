package info.mudbourn.mmscombat.killstreak;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

// A reward chest that floats to the player who earned a killstreak tier and hands over its roll once it reaches them. A lerped armor stand, so there is no pathfinding to get stuck and one owner check to answer "whose chest is this".
public final class FollowerChest {

    private static final double CATCH_UP = 0.12;
    private static final double DELIVER_RANGE_SQ = 4.0;
    private static final int MAX_LIFETIME_TICKS = 2400;

    private final UUID owner;
    private final ItemStack reward;
    private final ArmorStand stand;
    private int age;

    private FollowerChest(UUID owner, ItemStack reward, ArmorStand stand) {
        this.owner = owner;
        this.reward = reward;
        this.stand = stand;
    }

    public static FollowerChest spawn(ServerPlayer player, ItemStack reward) {
        ServerLevel level = player.level();
        ArmorStand stand = new ArmorStand(level, player.getX(), player.getY() + 1.2, player.getZ());
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setNoBasePlate(true);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHEST));
        stand.setCustomName(Component.literal(player.getName().getString() + "'s reward"));
        stand.setCustomNameVisible(true);
        level.addFreshEntity(stand);
        return new FollowerChest(player.getUUID(), reward, stand);
    }

    // Advances the chest one tick, returning true once it is finished and should be dropped from the active list.
    public boolean tick(MinecraftServer server) {
        if (stand.isRemoved()) {
            return true;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(owner);
        if (target == null || ++age > MAX_LIFETIME_TICKS) {
            stand.discard();
            return true;
        }
        Vec3 current = stand.position();
        Vec3 goal = target.position().add(0.0, 1.2, 0.0);
        Vec3 next = current.add(goal.subtract(current).scale(CATCH_UP));
        stand.setPos(next.x, next.y, next.z);
        if (stand.distanceToSqr(target) <= DELIVER_RANGE_SQ) {
            deliver(target);
            stand.discard();
            return true;
        }
        return false;
    }

    private void deliver(ServerPlayer target) {
        if (!target.getInventory().add(reward)) {
            target.drop(reward, false);
        }
        target.sendSystemMessage(Component.literal("Killstreak reward delivered."));
    }
}
