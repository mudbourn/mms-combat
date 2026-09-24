package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryClock;
import info.mudbourn.mmscombat.armory.ArmoryItems;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// The unlit Crucible, refilling its energy while it rests and relit once enough is stored.
public class CrucibleInactiveItem extends Item {

    private static final int TOGGLE_COOLDOWN = 20;
    private static final int RELIGHT_ENERGY = 3;

    public CrucibleInactiveItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (CrucibleItem.energy(stack, level.getGameTime()) < RELIGHT_ENERGY) {
            return InteractionResult.FAIL;
        }
        ItemStack lit = ArmoryWeaponItem.transmute(stack, ArmoryItems.CRUCIBLE);
        player.setItemInHand(hand, lit);
        level.playSound(player, player.blockPosition(), ArmorySounds.CRUCIBLE_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.getCooldowns().addCooldown(lit, TOGGLE_COOLDOWN);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return CrucibleItem.energy(stack, ArmoryClock.display()) < CrucibleItem.MAX_ENERGY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(CrucibleItem.energy(stack, ArmoryClock.display()) / (float) CrucibleItem.MAX_ENERGY * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ARGB.colorFromFloat(1.0F, 0.5F, 0.5F, 0.5F);
    }
}
