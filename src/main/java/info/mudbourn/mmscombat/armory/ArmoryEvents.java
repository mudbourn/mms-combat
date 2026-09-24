package info.mudbourn.mmscombat.armory;

import info.mudbourn.mmscombat.armory.item.BloodletterItem;
import info.mudbourn.mmscombat.armory.item.DragonSlayerItem;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Server hooks for weapon abilities that react to damage and interaction.
public final class ArmoryEvents {

    private static final float VESSEL_FILL_CHANCE = 0.3F;

    private ArmoryEvents() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(target instanceof LivingEntity living)) {
                return InteractionResult.PASS;
            }
            if (living.hasEffect(ArmoryEffects.NONEXISTENCE)) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide() && player.hasEffect(ArmoryEffects.NONEXISTENCE)) {
                ArmoryEffects.strikeFromNonexistence(player, living);
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            breakOnBlockTouch(player, level, hit.getBlockPos());
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            breakOnBlockTouch(player, level, pos);
            return InteractionResult.PASS;
        });
    }

    // Reacts to damage a living entity takes, including a killing blow.
    public static void onDamage(LivingEntity victim, DamageSource source, float taken) {
        ArmoryEffects.breakNonexistence(victim);
        if (!(source.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.is(ArmoryItems.DRAGON_SLAYER)) {
            DragonSlayerItem.recordDamage(held, taken);
        }
        if (held.is(ArmoryItems.BLOODLETTER)) {
            fillVessel(player);
        }
        ItemStack bloodletter = find(player, ArmoryItems.BLOODLETTER);
        if (!bloodletter.isEmpty()) {
            BloodletterItem.absorb(bloodletter, taken);
        }
    }

    private static void fillVessel(Player player) {
        ItemStack empty = find(player, ArmoryItems.BLOOD_VESSEL_EMPTY);
        if (!empty.isEmpty() && player.getRandom().nextFloat() < VESSEL_FILL_CHANCE) {
            empty.shrink(1);
            ItemStack full = new ItemStack(ArmoryItems.BLOOD_VESSEL_FULL);
            if (!player.addItem(full)) {
                player.drop(full, false);
            }
        }
    }

    private static void breakOnBlockTouch(Player player, Level level, BlockPos pos) {
        if (!level.isClientSide() && !level.getBlockState(pos).isAir()) {
            ArmoryEffects.breakNonexistence(player);
        }
    }

    private static ItemStack find(Player player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                return stack;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(item) ? offhand : ItemStack.EMPTY;
    }
}
