package dev.voidpvp.client.perf;

/**
 * The {@code frame_limiter} decision, pure so the tests can walk every state.
 *
 * <p>The rule the schema promises: <b>focused and in the world, nothing changes</b> - the frame
 * rate is whatever vanilla says (the player's own Max Framerate). Otherwise the lowest applicable
 * cap wins. On the title screen and server list {@code menu_fps} replaces vanilla's own 30.</p>
 */
public final class FrameCap {

    /** What a minimised window idles at. */
    public static final int MINIMIZED_FPS = 5;

    private FrameCap() {
    }

    /**
     * @param vanilla      what {@code getMaxFramerate()} returned
     * @param focused      {@code Display.isActive()}
     * @param visible      {@code Display.isVisible()}, false when minimised
     * @param inMenu       title screen / server list (no world), or the pause menu
     */
    public static int cap(int vanilla, boolean focused, boolean visible, boolean inMenu,
                          int unfocusedFps, int menuFps, boolean idleMinimized) {
        if (focused && visible && !inMenu) {
            return vanilla;
        }
        int limit = inMenu ? menuFps : vanilla;
        if (!focused) {
            limit = Math.min(limit, unfocusedFps);
        }
        if (!visible && idleMinimized) {
            limit = Math.min(limit, MINIMIZED_FPS);
        }
        return Math.max(1, limit);
    }
}
