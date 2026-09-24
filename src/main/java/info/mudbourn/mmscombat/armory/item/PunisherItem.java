package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Hammer whose ignited engines empower its next three strikes.
public class PunisherItem extends ArmoryWeaponItem {

    private static final int STRIKES = 3;
    private static final int COOLDOWN = 200;
    private static final WeaponStats IGNITED = new WeaponStats(7.0, 0.9, 2.8, 0.0);

    public PunisherItem(Properties properties) {
        super(new WeaponStats(17.0, -3.3, -1.2, 0.0), properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || stack.getOrDefault(ArmoryComponents.STRIKES_REMAINING, 0) > 0) {
            return InteractionResult.PASS;
        }
        stack.set(ArmoryComponents.STRIKES_REMAINING, STRIKES);
        stack.set(ArmoryComponents.ANIMATION_START, level.getGameTime());
        level.playSound(player, player.blockPosition(), ArmorySounds.PUNISHER_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        int strikes = stack.getOrDefault(ArmoryComponents.STRIKES_REMAINING, 0);
        if (strikes <= 0) {
            return;
        }
        attacker.playSound(ArmorySounds.PUNISHER_ATTACK, 1.0F, 1.0F);
        stack.set(ArmoryComponents.STRIKES_REMAINING, strikes - 1);
        if (strikes == 1) {
            attacker.playSound(ArmorySounds.PUNISHER_DEACTIVATE, 1.0F, 1.0F);
            stack.remove(ArmoryComponents.ANIMATION_START);
            if (attacker instanceof Player player) {
                player.getCooldowns().addCooldown(stack, COOLDOWN);
            }
        }
    }

    @Override
    protected WeaponStats bonus(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.STRIKES_REMAINING, 0) > 0 ? IGNITED : WeaponStats.NONE;
    }
}
