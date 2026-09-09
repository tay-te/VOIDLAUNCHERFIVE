package dev.voidpvp.client.actuator;

/**
 * The Toggle sprint actuator's state machine (§6.7): pressing the sprint key
 * latches sprint on, pressing it again lets go. In {@code hold} mode the latch
 * never engages and vanilla hold-to-sprint is back, which is what the setting
 * promises.
 *
 * <p>The Mixin side is one call to {@code KeyBinding.setKeyPressed}; everything
 * that decides whether to make it lives here, where it can be tested.</p>
 *
 * <p><b>Two mods drive one of these each.</b> Toggle sprint reads vanilla's sprint key and
 * writes it back, so its {@code hold} means "stop latching and let vanilla have the key" and
 * writes nothing at all. Toggle sneak reads a bind of its own and writes vanilla's <em>sneak</em>
 * key, so its {@code hold} is a real behaviour — sneak on a key that is not Shift — and does
 * write. This class is the part they share and knows about neither: it is handed
 * {@code enabled}, {@code holdMode} and whether the driving key is down, and
 * {@code VoidClient.applyActuators} decides what to do with the answer.</p>
 */
public final class SprintLatch {

    private boolean keyWasDown;
    private boolean latched;

    /**
     * Advances one tick.
     *
     * @param enabled  the mod is on
     * @param holdMode {@code mode} is {@code hold} rather than {@code toggle}
     * @param keyDown  the bound key is physically down this tick
     * @param canMove  the player exists and is not in a screen that eats input
     * @return whether the key should be reported as held this tick
     */
    public boolean update(boolean enabled, boolean holdMode, boolean keyDown, boolean canMove) {
        if (!enabled || !canMove) {
            latched = false;
            keyWasDown = keyDown;
            return keyDown;
        }
        if (holdMode) {
            latched = false;
            keyWasDown = keyDown;
            return keyDown;
        }
        if (keyDown && !keyWasDown) {
            latched = !latched;
        }
        keyWasDown = keyDown;
        return latched || keyDown;
    }

    public boolean isLatched() {
        return latched;
    }
}
