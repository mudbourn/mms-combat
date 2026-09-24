package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmoryItems;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Stackable catalyst; using one forms it into an Originium Katana.
public class OriginiumCatalystItem extends Item {

    private static final int COOLDOWN = 20;

    public OriginiumCatalystItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack rest = stack.copyWithCount(stack.getCount() - 1);
        ItemStack katana = ArmoryWeaponItem.transmute(stack, ArmoryItems.ORIGINIUM_KATANA);
        katana.set(ArmoryComponents.ANIMATION_START, level.getGameTime());

        player.setItemInHand(hand, katana);
        if (!rest.isEmpty() && !player.addItem(rest)) {
            player.drop(rest, false);
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        level.playSound(
            player,
            player.blockPosition(),
            ArmorySounds.ORIGINIUM_CATALYST_ACTIVATE,
            SoundSource.PLAYERS,
            0.5F,
            1.0F
        );
        return InteractionResult.SUCCESS;
    }
}
