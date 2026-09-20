package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// The floating killstreak crate: a four-slot container that eases to hover above and ahead of its owner and swings behind them as they move, opened by the owner to collect the reward. It is transient (never saved), so it simply despawns on restart or when emptied.
public class KillstreakCrateEntity extends Entity implements MenuProvider {

    // A full vanilla single-row chest (9) so the crate opens the stock GENERIC_9x1 UI; the reward sits in the first slot.
    private static final int SLOTS = 9;
    private static final double CATCH_UP = 0.25;
    private static final double HOVER_HEIGHT = 2.0;
    private static final double IDLE_FORWARD = 2.5;
    private static final double MOVING_BACK = -1.7;
    private static final double WALK_SPEED = 0.12;
    private static final int ORPHAN_TICKS = 1200;
    // Length of the fade-and-tween the crate plays on arrival and again before it is removed.
    public static final int ANIM_TICKS = 12;

    // Synced so the client can play the leaving animation before the server actually discards the crate.
    private static final EntityDataAccessor<Boolean> DESPAWNING =
        SynchedEntityData.defineId(KillstreakCrateEntity.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer contents = new SimpleContainer(SLOTS);
    // Smooths the server's per-tick follow across the client's frames so the crate glides instead of snapping between tracker updates.
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private UUID owner;
    private Vec3 lastOwnerPos;
    private int orphanAge;
    private int despawnTick = -1;
    private int clientDespawnAge;

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
        if (this.level().isClientSide()) {
            this.interpolation.interpolate();
            if (this.entityData.get(DESPAWNING)) {
                this.clientDespawnAge++;
            }
            return;
        }
        if (this.despawnTick >= 0) {
            if (this.tickCount - this.despawnTick >= ANIM_TICKS) {
                this.discard();
            }
            return;
        }
        if (this.contents.isEmpty()) {
            beginDespawn();
            return;
        }
        Player target = this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
        if (target == null) {
            if (++this.orphanAge > ORPHAN_TICKS) {
                beginDespawn();
            }
            return;
        }
        this.orphanAge = 0;
        followOwner(target);
    }

    // Flags the crate as leaving so the client fades and tweens it out, then discards it once the animation has played.
    private void beginDespawn() {
        this.despawnTick = this.tickCount;
        this.entityData.set(DESPAWNING, true);
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
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (this.owner != null && !this.owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("This crate is not yours."), true);
            return InteractionResult.FAIL;
        }
        player.openMenu(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this.contents, 1);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Killstreak Crate");
    }

    public Container contents() {
        return this.contents;
    }

    // Routes the tracker's position updates through the interpolator on the client so movement is stepped over several frames.
    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // Eased 0..1 arrival progress the client uses to fade and tween the crate into place.
    public float appearProgress(float partialTick) {
        return Mth.clamp((this.tickCount + partialTick) / ANIM_TICKS, 0.0F, 1.0F);
    }

    // Eased 0..1 leaving progress once the server has flagged the crate for removal.
    public float despawnProgress(float partialTick) {
        if (!this.entityData.get(DESPAWNING)) {
            return 0.0F;
        }
        return Mth.clamp((this.clientDespawnAge + partialTick) / ANIM_TICKS, 0.0F, 1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DESPAWNING, false);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
