package info.mudbourn.mmscombat.revive;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

// Fires when a self-revive attempt strains, so other mods (mms-origins, say) can voice the pain in the player's own species sound.
public final class SelfReviveEvents {

    public static final Event<Strain> STRAIN = EventFactory.createArrayBacked(Strain.class,
        listeners -> (player, source) -> {
            boolean voiced = false;
            for (Strain listener : listeners) {
                voiced |= listener.onStrain(player, source);
            }
            return voiced;
        });

    private SelfReviveEvents() {
    }

    @FunctionalInterface
    public interface Strain {
        // The straining player and the damage source of the flinch; returns true when the listener played a hurt sound.
        boolean onStrain(ServerPlayer player, DamageSource source);
    }
}
