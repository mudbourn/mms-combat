package info.mudbourn.mmscombat.armory;

import java.util.function.LongSupplier;

// Game-time source for client-side weapon readouts such as durability-style bars and tooltips.
public final class ArmoryClock {

    public static final long UNKNOWN = Long.MIN_VALUE;
    private static LongSupplier display = () -> UNKNOWN;

    private ArmoryClock() {
    }

    // Installed by the client entrypoint so readouts can follow time-derived weapon state.
    public static void setDisplay(LongSupplier clock) {
        display = clock;
    }

    public static long display() {
        return display.getAsLong();
    }
}
