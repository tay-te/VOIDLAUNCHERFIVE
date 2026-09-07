package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;

/**
 * Creates the one {@link WebView} the mod paints, falling back to
 * {@link NullWebView} when the Ultralight binding is missing or will not load.
 * The failure is logged once and the game is not disturbed (§6.2).
 */
public final class WebViews {

    private static boolean warned;

    private WebViews() {
    }

    /**
     * Whether the accelerated renderer was asked for.
     *
     * <p>{@code VOID_UI_RENDERER=gpu} or {@code -Dvoid.ui.renderer=gpu}. Asking is not the same as
     * getting it: the accelerated path needs a GL context on the thread that owns the engine
     * ({@link UiGlContext}), and without one the host builds a CPU view instead.</p>
     *
     * <p><b>Why {@code cpu} is still the default.</b> Not the measurement, which favours the GL
     * driver. In game on an M1 Max, menu open, the same scripted interaction, a 3336x1870 view at
     * 2.28 dp/css: mean frame rate is a tie at the display's 120 Hz ceiling (113 against 115), but
     * the worst game frame is 17-28 ms against 40-47 and the worst UI paint 6-8 ms against 39-42.
     * That gap is the whole point — the CPU surface's slow paints are what the game thread's
     * upload queues behind, so its worst frame tracks the rasteriser's. Under the heavier page
     * this was first measured against, the same comparison was 87 fps against 77 and 2% of frames
     * over 20 ms against 12%. Output is now pixel-comparable: the driver's
     * {@code repeating-linear-gradient} bug is moot since the design dropped gradients entirely.
     *
     * <p>What holds the flip is platform coverage. The GL driver has executed on exactly one
     * machine — macOS, Apple's 2.1 profile — and never on Windows or Linux, and its failure mode
     * there is not graceful: if the GLSL 1.20 programs will not link, {@code rendererRender} sets
     * {@code g_gpu_failed} and accelerated views simply stop painting, which is a blank overlay
     * rather than a fall back to this path. Make that degrade to a CPU view, or run the driver on
     * Windows, and this default should change.</p>
     */
    public static boolean acceleratedRequested() {
        String choice = System.getenv("VOID_UI_RENDERER");
        if (choice == null) {
            choice = System.getProperty("void.ui.renderer", "cpu");
        }
        return "gpu".equalsIgnoreCase(choice);
    }

    public static WebView create(int width, int height, boolean accelerated) {
        try {
            return UltralightWebView.create(width, height, accelerated);
        } catch (UnsatisfiedLinkError e) {
            warnOnce("Ultralight is unavailable, in-game UI disabled: " + e.getMessage());
        } catch (RuntimeException e) {
            warnOnce("Ultralight failed to start, in-game UI disabled: " + e);
        } catch (LinkageError e) {
            warnOnce("Ultralight failed to link, in-game UI disabled: " + e);
        }
        return new NullWebView();
    }

    private static void warnOnce(String message) {
        if (!warned) {
            warned = true;
            VoidLog.warn(message);
        }
    }
}
