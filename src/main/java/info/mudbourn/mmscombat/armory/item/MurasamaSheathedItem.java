package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryItems;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import info.mudbourn.mmscombat.killstreak.Perishable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Murasama in its Gun Sheath; using it with a free off hand fires the blade out for a quickdraw.
public class MurasamaSheathedItem extends Item {

    private static final double DASH_FORCE = 2.0;

    public MurasamaSheathedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        ItemStack drawn = ArmoryWeaponItem.transmute(player.getItemInHand(hand), ArmoryItems.MURASAMA);
        MurasamaItem.startQuickdraw(drawn, level);
        ItemStack sheath = new ItemStack(ArmoryItems.GUN_SHEATH);
        Perishable.copyBinding(drawn, sheath);
        player.setItemInHand(InteractionHand.OFF_HAND, sheath);
        player.setItemInHand(InteractionHand.MAIN_HAND, drawn);
        level.playSound(player, player.blockPosition(), ArmorySounds.MURASAMA_SHOOT, SoundSource.PLAYERS, 0.5F, 1.0F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (player.isShiftKeyDown()) {
            Vec3 push = player.getLookAngle().scale(DASH_FORCE).add(0.0, 0.2, 0.0);
            player.push(push.x, push.y, push.z);
            player.hurtMarked = true;
        }
        return InteractionResult.SUCCESS;
    }
}
