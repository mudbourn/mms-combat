package info.mudbourn.mmscombat.roll;

// Set while the client runs an attack or item use, so Combat Roll's lockout reads the player as not rolling.
public final class RollLockout {

    private static boolean bypass;

    private RollLockout() {
    }

    public static boolean bypassed() {
        return bypass;
    }

    public static void set(boolean value) {
        bypass = value;
    }
}
