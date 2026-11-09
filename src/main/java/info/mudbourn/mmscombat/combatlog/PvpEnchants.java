package info.mudbourn.mmscombat.combatlog;

import info.mudbourn.mmscombat.config.CombatConfig;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

// Tracks when the current thread is resolving a player-vs-player hit, so enchant lookups read fair-play levels only then.
public final class PvpEnchants {

    private static final ThreadLocal<UUID[]> FIGHTERS = new ThreadLocal<>();

    private PvpEnchants() {
    }

    // Marks a hit between two distinct players as in progress, returning the context to restore afterwards.
    public static UUID[] enter(Entity attacker, Entity victim) {
        UUID[] previous = FIGHTERS.get();
        if (CombatConfig.get().nerfPvpEnchants
            && attacker instanceof Player
            && victim instanceof Player
            && attacker != victim) {
            FIGHTERS.set(new UUID[] {attacker.getUUID(), victim.getUUID()});
        }
        return previous;
    }

    public static void exit(UUID[] previous) {
        FIGHTERS.set(previous);
    }

    // The levels an enchant lookup should see: fair-play levels during a player-vs-player hit, the real ones otherwise.
    public static ItemEnchantments levels(ItemStack stack, ItemEnchantments real) {
        UUID[] fighters = FIGHTERS.get();
        if (fighters == null) {
            return real;
        }
        ItemEnchantments fair = FairPlayEnchants.overBudget(stack) ? LoadoutChoices.fairLevels(fighters, stack) : real;
        return FairPlayEnchants.restrict(fair);
    }
}
