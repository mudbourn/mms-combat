package info.mudbourn.mmscombat.armory.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// A filled blood vessel; drinking it restores half of the drinker's missing health.
public class BloodVesselItem extends Item {

    private static final int COOLDOWN = 20;

    public BloodVesselItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        player.heal((player.getMaxHealth() - player.getHealth()) / 2.0F);
        level.playSound(player, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0F, 1.15F);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
