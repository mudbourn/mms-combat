package info.mudbourn.mmscombat.client.hud;

// The latest combat state pushed from the server, read each frame by the HUD renderer.
public final class CombatHudState {

    private static volatile boolean inCombat;
    private static volatile int secondsLeft;
    private static volatile boolean inZone;

    private CombatHudState() {
    }

    public static void update(boolean combat, int seconds, boolean zone) {
        inCombat = combat;
        secondsLeft = seconds;
        inZone = zone;
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
}
