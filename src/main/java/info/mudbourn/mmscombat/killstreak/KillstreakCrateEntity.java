package info.mudbourn.mmscombat.killstreak;

import info.mudbourn.mmscombat.registry.MmsCombatRegistries;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
    // The window the owner has to collect before the crate detonates.
    private static final int LIFETIME_TICKS = 600;
    // A blast that spares the owner and the terrain but punishes anyone else who crowds the drop.
    private static final double EXPLOSION_RADIUS = 4.0;
    private static final float EXPLOSION_DAMAGE = 12.0F;
    // The invulnerability and heal window the owner gets the instant they collect.
    private static final int COLLECT_BUFF_TICKS = 100;
    // Length of the fade-and-tween the crate plays on arrival and again before it is removed.
    public static final int ANIM_TICKS = 12;
    // The interact box, pinned to where the renderer floats the model above the entity origin so a click lands on the crate the player sees.
    private static final double BOX_HALF_WIDTH = 1.0;
    private static final double BOX_BOTTOM = 0.35;
    private static final double BOX_TOP = 1.2;

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
    private int lastShownSeconds = -1;

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

    // Lays the reward stacks across the front slots, so a gun and its ammo land side by side.
    public void giveRewards(java.util.List<ItemStack> rewards) {
        int slot = 0;
        for (ItemStack reward : rewards) {
            if (slot >= SLOTS) {
                break;
            }
            this.contents.setItem(slot, reward);
            slot++;
        }
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
            grantCollectBuffs();
            beginDespawn();
            return;
        }
        if (this.tickCount >= LIFETIME_TICKS) {
            explode();
            this.discard();
            return;
        }
        updateCountdown();
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

    // Grants the owner a brief invulnerability and heal the instant they clear the crate.
    private void grantCollectBuffs() {
        if (this.owner == null) {
            return;
        }
        if (this.level().getPlayerByUUID(this.owner) instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, COLLECT_BUFF_TICKS, 4, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, COLLECT_BUFF_TICKS, 4, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, COLLECT_BUFF_TICKS, 0, false, true, true));
        }
    }

    // Shows the remaining seconds above the crate, refreshed only when the whole-second value changes.
    private void updateCountdown() {
        int secondsLeft = (int) Math.ceil((LIFETIME_TICKS - this.tickCount) / 20.0);
        if (secondsLeft != this.lastShownSeconds) {
            this.lastShownSeconds = secondsLeft;
            this.setCustomName(Component.literal("Explodes in " + secondsLeft + "s"));
            this.setCustomNameVisible(true);
        }
    }

    // Detonates an uncollected crate: sound and particles, damage that falls off with distance to every nearby entity except the owner, and no block damage at all.
    private void explode() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = this.position();
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        // An unattributed blast so it reads as the crate detonating, not the owner attacking, and so it is never suppressed as unprovoked PvP.
        DamageSource source = level.damageSources().explosion(this, null);
        AABB area = this.getBoundingBox().inflate(EXPLOSION_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (this.owner != null && this.owner.equals(victim.getUUID())) {
                continue;
            }
            // Horizontal distance, so a player standing under the floating crate still takes the crowding punishment.
            double dx = victim.getX() - center.x;
            double dz = victim.getZ() - center.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > EXPLOSION_RADIUS) {
                continue;
            }
            float damage = (float) (EXPLOSION_DAMAGE * (1.0 - distance / EXPLOSION_RADIUS));
            if (damage > 0.0F) {
                victim.hurtServer(level, source, damage);
            }
        }
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
        Component custom = this.getCustomName();
        return custom != null ? custom : Component.literal("Killstreak Crate");
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

    // Lifts the bounding box off the entity origin to wrap the floating model, so the interact target matches the rendered crate.
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        return new AABB(
            pos.x - BOX_HALF_WIDTH,
            pos.y + BOX_BOTTOM,
            pos.z - BOX_HALF_WIDTH,
            pos.x + BOX_HALF_WIDTH,
            pos.y + BOX_TOP,
            pos.z + BOX_HALF_WIDTH);
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
