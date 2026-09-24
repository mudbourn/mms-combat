package info.mudbourn.mmscombat.client.armory;

import info.mudbourn.mmscombat.net.NonexistencePayload;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.Entity;

// Client-side set of entity ids in Non-Existence, which the renderer skips entirely.
public final class NonexistenceClientState {

    private static final IntSet HIDDEN = new IntOpenHashSet();

    private NonexistenceClientState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NonexistencePayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (payload.hidden()) {
                    HIDDEN.add(payload.entityId());
                } else {
                    HIDDEN.remove(payload.entityId());
                }
            }));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> HIDDEN.remove(entity.getId()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(HIDDEN::clear));
    }

    public static boolean isHidden(Entity entity) {
        return HIDDEN.contains(entity.getId());
    }
}
