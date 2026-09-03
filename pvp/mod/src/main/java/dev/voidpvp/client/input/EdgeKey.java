package dev.voidpvp.client.input;

/**
 * Rising-edge detection for a polled key.
 *
 * <p>The menu and loadout-cycle hotkeys are rebindable from the launcher
 * through {@code global_settings}, not from Minecraft's Controls screen, so
 * they are polled by key code rather than registered as vanilla
 * {@code KeyBinding}s — registering them would put a second, contradictory
 * source of truth in the game's options file.</p>
 */
public final class EdgeKey {

    private boolean wasDown;

    /** @return true on the frame the key goes down */
    public boolean pressed(boolean down) {
        boolean edge = down && !wasDown;
        wasDown = down;
        return edge;
    }

    /** @return true on the frame the key comes up */
    public boolean released(boolean down) {
        boolean edge = !down && wasDown;
        wasDown = down;
        return edge;
    }

    public boolean isDown() {
        return wasDown;
    }

    public void reset() {
        wasDown = false;
    }
}
