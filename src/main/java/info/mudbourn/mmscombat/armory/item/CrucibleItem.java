package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.MmsCombat;
import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmoryItems;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import info.mudbourn.mmscombat.armory.ArmoryClock;

// Lit greatsword whose armor-piercing swings drain an energy reserve that refills while it rests.
public class CrucibleItem extends ArmoryWeaponItem {

    public static final int MAX_ENERGY = 6;
    private static final int RECHARGE_TICKS = 50;
    private static final int TOGGLE_COOLDOWN = 20;
    public static final float ARMOR_PIERCE = 0.4F;
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(MmsCombat.MOD_ID, "crucible")
    );

    public CrucibleItem(Properties properties) {
        super(new WeaponStats(22.0, -3.2, -1.5, 1.0), properties);
    }

    // Energy at the given game time: the stored amount plus whatever has refilled since the last swing.
    public static int energy(ItemStack stack, long now) {
        int stored = stack.getOrDefault(ArmoryComponents.USAGES, MAX_ENERGY);
        Long since = stack.get(ArmoryComponents.CHARGE_START);
        if (since == null || now == ArmoryClock.UNKNOWN) {
            return stored;
        }
        return (int) Math.min(MAX_ENERGY, stored + Math.max(0L, now - since) / RECHARGE_TICKS);
    }

    // Spends one energy and restarts the refill timer from now.
    private static int spend(ItemStack stack, long now) {
        int left = Math.max(0, energy(stack, now) - 1);
        stack.set(ArmoryComponents.USAGES, left);
        stack.set(ArmoryComponents.CHARGE_START, now);
        return left;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack inactive = transmute(player.getItemInHand(hand), ArmoryItems.CRUCIBLE_INACTIVE);
        player.setItemInHand(hand, inactive);
        level.playSound(player, player.blockPosition(), ArmorySounds.CRUCIBLE_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.getCooldowns().addCooldown(inactive, TOGGLE_COOLDOWN);
        return InteractionResult.SUCCESS;
    }

    @Override
    public DamageSource getItemDamageSource(LivingEntity attacker) {
        return attacker.damageSources().source(DAMAGE_TYPE, attacker);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (spend(stack, attacker.level().getGameTime()) > 0) {
            return;
        }
        attacker.playSound(ArmorySounds.CRUCIBLE_DEACTIVATE, 1.0F, 1.0F);
        if (attacker instanceof Player player && player.getMainHandItem() == stack) {
            ItemStack inactive = transmute(stack, ArmoryItems.CRUCIBLE_INACTIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, inactive);
            player.getCooldowns().addCooldown(inactive, TOGGLE_COOLDOWN);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energy(stack, ArmoryClock.display()) < MAX_ENERGY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(energy(stack, ArmoryClock.display()) / (float) MAX_ENERGY * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ARGB.colorFromFloat(1.0F, 0.9F, 0.2F, 0.1F);
    }
}
