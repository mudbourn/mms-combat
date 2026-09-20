package info.mudbourn.mmscombat.combatlog;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;

// Fires when a logout body is spawned, so other mods (mms-origins, say) can dress the mannequin to match the player without this mod knowing about them.
public final class LogoutBodyEvents {

    public static final Event<Created> CREATED = EventFactory.createArrayBacked(Created.class,
        listeners -> (player, body) -> {
            for (Created listener : listeners) {
                listener.onCreated(player, body);
            }
        });

    private LogoutBodyEvents() {
    }

    @FunctionalInterface
    public interface Created {
        // The disconnecting player and the mannequin standing in for them.
        void onCreated(ServerPlayer player, Mannequin body);
    }
}
