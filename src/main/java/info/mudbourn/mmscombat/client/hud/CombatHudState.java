package info.mudbourn.mmscombat.client.hud;

// The latest combat state pushed from the server, read each frame by the HUD renderer.
public final class CombatHudState {

    private static volatile boolean inCombat;
    private static volatile int secondsLeft;
    private static volatile boolean inZone;
    private static volatile int streak;
    private static volatile int decaySeconds;

    private CombatHudState() {
    }

    public static void update(boolean combat, int seconds, boolean zone, int streakCount, int decayLeft) {
        inCombat = combat;
        secondsLeft = seconds;
        inZone = zone;
        streak = streakCount;
        decaySeconds = decayLeft;
    }

    public static boolean inCombat() {
        return inCombat;
    }

    public static int secondsLeft() {
        return secondsLeft;
    }

    public static boolean inZone() {
        return inZone;
    }

    public static int streak() {
        return streak;
    }

    public static int decaySeconds() {
        return decaySeconds;
    }
}
