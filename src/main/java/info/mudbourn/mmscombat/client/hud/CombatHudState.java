package info.mudbourn.mmscombat.client.hud;

// The latest combat state pushed from the server, read each frame by the HUD renderer.
public final class CombatHudState {

    private static volatile boolean inCombat;
    private static volatile int secondsLeft;

    private CombatHudState() {
    }

    public static void update(boolean combat, int seconds) {
        inCombat = combat;
        secondsLeft = seconds;
    }

    public static boolean inCombat() {
        return inCombat;
    }

    public static int secondsLeft() {
        return secondsLeft;
    }
}
