package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryClock;
import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

// Rapier that stores blood from damage its holder deals and spends it on a toggled power-up.
public class BloodletterItem extends ArmoryWeaponItem {

    public static final int MAX_BLOOD = 200;
    private static final int BLOOD_PER_CYCLE = 10;
    private static final int BLOOD_PER_HIT = 10;
    private static final int FRAMES = 15;
    private static final int TOGGLE_COOLDOWN = 60;
    private static final WeaponStats ACTIVE = new WeaponStats(8.0, 2.0, 1.0, 0.0);

    public BloodletterItem(Properties properties) {
        super(new WeaponStats(15.0, -2.0, -0.5, 2.0), properties);
    }

    // Stores blood from damage dealt while the rapier is dormant.
    public static void absorb(ItemStack stack, float amount) {
        if (isActive(stack)) {
            return;
        }
        int stored = stack.getOrDefault(ArmoryComponents.STORED_BLOOD, 0);
        stack.set(ArmoryComponents.STORED_BLOOD, (int) Math.min(MAX_BLOOD, stored + amount));
    }

    private static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.ABILITY_TICK, 0) > 0;
    }

    private static long drainCycles(ItemStack stack, long now) {
        Long start = stack.get(ArmoryComponents.ANIMATION_START);
        if (start == null || now == ArmoryClock.UNKNOWN) {
            return 0L;
        }
        return Math.max(0L, (now - start) / FRAMES);
    }

    // Blood left at the given game time, counting drain cycles not yet written to the stack.
    private static int blood(ItemStack stack, long now) {
        int stored = stack.getOrDefault(ArmoryComponents.STORED_BLOOD, 0);
        return (int) Math.max(0L, stored - drainCycles(stack, now) * BLOOD_PER_CYCLE);
    }

    // Writes elapsed drain cycles into the stored blood, keeping the animation phase.
    private static void settle(ItemStack stack, long now) {
        long cycles = drainCycles(stack, now);
        if (cycles <= 0L) {
            return;
        }
        stack.set(ArmoryComponents.STORED_BLOOD, blood(stack, now));
        stack.set(ArmoryComponents.ANIMATION_START, stack.get(ArmoryComponents.ANIMATION_START) + cycles * FRAMES);
    }

    private static void deactivate(ItemStack stack, long now) {
        settle(stack, now);
        stack.remove(ArmoryComponents.ABILITY_TICK);
        stack.remove(ArmoryComponents.ANIMATION_START);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        boolean activate = !isActive(stack);
        if (activate && blood(stack, now) <= 0) {
            return InteractionResult.PASS;
        }

        if (activate) {
            stack.set(ArmoryComponents.ABILITY_TICK, 1);
            stack.set(ArmoryComponents.ANIMATION_START, now);
        } else {
            deactivate(stack, now);
        }
        level.playSound(
            player,
            player.blockPosition(),
            activate ? ArmorySounds.BLOODLETTER_ACTIVATE : ArmorySounds.BLOODLETTER_DEACTIVATE,
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        player.getCooldowns().addCooldown(stack, TOGGLE_COOLDOWN);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.invulnerableTime = 0;
        if (isActive(stack)) {
            attacker.playSound(ArmorySounds.BLOODLETTER_HIT, 1.0F, 1.0F);
            settle(stack, attacker.level().getGameTime());
            int stored = stack.getOrDefault(ArmoryComponents.STORED_BLOOD, 0);
            stack.set(ArmoryComponents.STORED_BLOOD, Math.max(0, stored - BLOOD_PER_HIT));
        }
    }

    @Override
    protected void tickAbility(ItemStack stack, ServerLevel level, Entity holder) {
        if (!(holder instanceof Player player) || !isActive(stack)) {
            return;
        }
        if (blood(stack, level.getGameTime()) <= 0) {
            deactivate(stack, level.getGameTime());
            level.playSound(
                player,
                player.blockPosition(),
                ArmorySounds.BLOODLETTER_DEACTIVATE,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
            );
        }
    }

    @Override
    protected WeaponStats bonus(ItemStack stack) {
        return isActive(stack) ? ACTIVE : WeaponStats.NONE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return blood(stack, ArmoryClock.display()) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(blood(stack, ArmoryClock.display()) / (float) MAX_BLOOD * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ARGB.colorFromFloat(1.0F, 0.9F, 0.1F, 0.1F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(
            "item.mms_combat.bloodletter.stored_blood",
            blood(stack, ArmoryClock.display())
        ).withStyle(ChatFormatting.GRAY));
    }
}
