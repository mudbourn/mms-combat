package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// The floating killstreak crate: a four-slot container that eases to hover above and ahead of its owner and swings behind them as they move, opened by the owner to collect the reward. It is transient (never saved), so it simply despawns on restart or when emptied.
public class KillstreakCrateEntity extends Entity implements MenuProvider {

    private static final int SLOTS = 4;
    private static final double CATCH_UP = 0.25;
    private static final double HOVER_HEIGHT = 2.0;
    private static final double IDLE_FORWARD = 0.9;
    private static final double MOVING_BACK = -1.7;
    private static final double WALK_SPEED = 0.12;
    private static final int ORPHAN_TICKS = 1200;

    private final SimpleContainer contents = new SimpleContainer(SLOTS);
    private UUID owner;
    private Vec3 lastOwnerPos;
    private int orphanAge;

    public KillstreakCrateEntity(EntityType<? extends KillstreakCrateEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void giveReward(ItemStack reward) {
        this.contents.setItem(0, reward);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.contents.isEmpty()) {
            this.discard();
            return;
        }
        Player target = this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
        if (target == null) {
            if (++this.orphanAge > ORPHAN_TICKS) {
                this.discard();
            }
            return;
        }
        this.orphanAge = 0;
        followOwner(target);
    }

    private void followOwner(Player target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();

        Vec3 ownerPos = target.position();
        double moved = this.lastOwnerPos == null ? 0.0 : ownerPos.distanceTo(this.lastOwnerPos);
        this.lastOwnerPos = ownerPos;
        double moving = Mth.clamp(moved / WALK_SPEED, 0.0, 1.0);
        double forward = Mth.lerp(moving, IDLE_FORWARD, MOVING_BACK);

        Vec3 goal = ownerPos.add(flat.scale(forward)).add(0.0, HOVER_HEIGHT, 0.0);
        Vec3 next = this.position().add(goal.subtract(this.position()).scale(CATCH_UP));
        this.setPos(next.x, next.y, next.z);
        this.setYRot(target.getYRot());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (this.owner != null && !this.owner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("This crate is not yours."));
            return InteractionResult.FAIL;
        }
        player.openMenu(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new KillstreakCrateMenu(containerId, inventory, this.contents);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Killstreak Crate");
    }

    public Container contents() {
        return this.contents;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
