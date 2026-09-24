package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmoryEffects;
import info.mudbourn.mmscombat.armory.ArmorySounds;
import info.mudbourn.mmscombat.armory.WeaponStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Katana that grants Non-Existence on use and glows while its holder has it.
public class EdgeOfExistenceItem extends ArmoryWeaponItem {

    private static final int EFFECT_TICKS = 300;
    private static final int COOLDOWN = 320;

    public EdgeOfExistenceItem(Properties properties) {
        super(new WeaponStats(11.0, -0.2, -1.0, 1.0), properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            ArmoryEffects.grantNonexistence(player, EFFECT_TICKS);
        }
        level.playSound(
            player,
            player.blockPosition(),
            ArmorySounds.EDGE_OF_EXISTENCE_ACTIVATE,
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tickAbility(ItemStack stack, ServerLevel level, Entity holder) {
        if (holder instanceof LivingEntity living) {
            int wanted = living.hasEffect(ArmoryEffects.NONEXISTENCE) ? 1 : 0;
            if (stack.getOrDefault(ArmoryComponents.ABILITY_TICK, 0) != wanted) {
                stack.set(ArmoryComponents.ABILITY_TICK, wanted);
            }
        }
    }
}
