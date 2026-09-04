package dev.voidpvp.client.actuator;

/**
 * The Toggle sprint actuator's state machine (§6.7): pressing the sprint key
 * latches sprint on, pressing it again lets go. In {@code hold} mode the latch
 * never engages and vanilla hold-to-sprint is back, which is what the setting
 * promises.
 *
 * <p>The Mixin side is one call to {@code KeyBinding.setKeyPressed}; everything
 * that decides whether to make it lives here, where it can be tested.</p>
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

    public void release() {
        latched = false;
        keyWasDown = false;
    }
}
