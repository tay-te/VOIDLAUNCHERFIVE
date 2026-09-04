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

    public static WebView create(int width, int height) {
        try {
            return UltralightWebView.create(width, height);
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
