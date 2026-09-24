package info.mudbourn.mmscombat.armory.item;

import info.mudbourn.mmscombat.armory.ArmoryComponents;
import info.mudbourn.mmscombat.armory.ArmoryItems;
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

// Katana that sheathes into an off-hand Gun Sheath and hits harder right after a quickdraw.
public class MurasamaItem extends ArmoryWeaponItem {

    public static final int QUICKDRAW_TICKS = 20;
    private static final int SHEATH_COOLDOWN = 40;
    private static final WeaponStats QUICKDRAW = new WeaponStats(12.0, 3.6, 1.4, 0.0);

    public MurasamaItem(Properties properties) {
        super(new WeaponStats(11.0, -0.4, -2.6, 1.0), properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.getOffhandItem().is(ArmoryItems.GUN_SHEATH)) {
            return InteractionResult.PASS;
        }
        ItemStack sheathed = transmute(player.getItemInHand(hand), ArmoryItems.MURASAMA_SHEATHED);
        sheathed.remove(ArmoryComponents.ABILITY_TICK);
        sheathed.remove(ArmoryComponents.ANIMATION_START);
        player.getOffhandItem().shrink(1);
        player.setItemInHand(hand, sheathed);
        if (!player.isCreative()) {
            player.getCooldowns().addCooldown(sheathed, SHEATH_COOLDOWN);
        }
        level.playSound(player, player.blockPosition(), ArmorySounds.MURASAMA_SHEATH, SoundSource.PLAYERS, 0.5F, 1.0F);
        level.playSound(player, player.blockPosition(), ArmorySounds.MURASAMA_INSERT, SoundSource.PLAYERS, 0.5F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (stack.getOrDefault(ArmoryComponents.ABILITY_TICK, 0) > 0) {
            attacker.playSound(ArmorySounds.MURASAMA_SPECIAL_HIT, 0.5F, 1.0F);
            endQuickdraw(stack);
        }
    }

    @Override
    protected void tickAbility(ItemStack stack, ServerLevel level, Entity holder) {
        Long start = stack.get(ArmoryComponents.ANIMATION_START);
        if (start != null && level.getGameTime() - start >= QUICKDRAW_TICKS) {
            endQuickdraw(stack);
        }
    }

    // Starts the quickdraw window on a freshly drawn stack.
    public static void startQuickdraw(ItemStack stack, Level level) {
        stack.set(ArmoryComponents.ABILITY_TICK, 1);
        stack.set(ArmoryComponents.ANIMATION_START, level.getGameTime());
    }

    private static void endQuickdraw(ItemStack stack) {
        stack.remove(ArmoryComponents.ABILITY_TICK);
        stack.remove(ArmoryComponents.ANIMATION_START);
    }

    @Override
    protected WeaponStats bonus(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.ABILITY_TICK, 0) > 0 ? QUICKDRAW : WeaponStats.NONE;
    }
}
