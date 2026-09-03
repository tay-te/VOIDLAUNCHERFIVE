package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.render.GlBlit;

import java.util.function.Function;

/**
 * The Ultralight host (§6.2): view lifecycle, sizing, the per-frame pump, the
 * GL paint and input forwarding.
 *
 * <p>Creation is lazy and happens on the first paint, because the renderer
 * needs Minecraft's GL context to be current and there is no earlier moment in
 * the mod's life where that is guaranteed. If it fails the host falls back to
 * {@link NullWebView} and never tries again: the HUD is gone, the game is
 * untouched, and the failure is logged once.</p>
 */
public final class UiHost {

    /**
     * The in-game bundle, built by {@code packages/ingame} into
     * {@code assets/void/ui/} and resolved by Ultralight against the resource
     * prefix the renderer was created with.
     */
    private static final String ENTRY_URL = "file:///index.html";

    private final VoidBridge bridge;
    private WebView view = new NullWebView();
    private boolean started;

    private int logicalWidth;
    private int logicalHeight;
    private int framebufferWidth;
    private int framebufferHeight;
    private double deviceScale = 1;

    public UiHost(VoidBridge bridge) {
        this.bridge = bridge;
    }

    public boolean isAvailable() {
        return view.isAvailable();
    }

    /**
     * Creates the view on first use and keeps it sized to the framebuffer.
     * Must be called on the render thread with the GL context current.
     */
    public void ensure(int fbWidth, int fbHeight, double scale) {
        double s = scale <= 0 ? 1 : scale;
        int lw = Math.max(1, (int) Math.ceil(fbWidth / s));
        int lh = Math.max(1, (int) Math.ceil(fbHeight / s));
        if (!started) {
            started = true;
            logicalWidth = lw;
            logicalHeight = lh;
            framebufferWidth = fbWidth;
            framebufferHeight = fbHeight;
            deviceScale = s;
            view = WebViews.create(lw, lh);
            if (!view.isAvailable()) {
                return;
            }
            view.setDeviceScale(s);
            view.setMessageHandler(new Function<String, String>() {
                @Override
                public String apply(String request) {
                    // window.__void_native(json): JS to Java, in-process and
                    // synchronous, on this very thread (§6.5).
                    return bridge.dispatch(request);
                }
            });
            view.loadUrl(ENTRY_URL);
            VoidLog.info("in-game UI started at " + lw + "x" + lh + " (scale " + s + ")");
            return;
        }
        if (!view.isAvailable()) {
            return;
        }
        if (lw != logicalWidth || lh != logicalHeight) {
            logicalWidth = lw;
            logicalHeight = lh;
            framebufferWidth = fbWidth;
            framebufferHeight = fbHeight;
            view.resize(lw, lh);
        }
        if (s != deviceScale) {
            deviceScale = s;
            view.setDeviceScale(s);
        }
    }

    /** Called from {@code Minecraft.onResolutionChanged}; the next frame resizes. */
    public void invalidateSize() {
        logicalWidth = 0;
        logicalHeight = 0;
    }

    /**
     * One frame: deliver this frame's events, run JS timers and layout, paint
     * into the texture. Events go first so a key pressed this frame reaches the
     * pixel in this same frame (§10).
     */
    public void frame() {
        if (!view.isAvailable()) {
            return;
        }
        try {
            String script = bridge.drainScript();
            if (script != null) {
                view.evaluateScript(script);
            }
            view.update();
            view.render();
        } catch (RuntimeException e) {
            VoidLog.error("in-game UI frame failed, disabling", e);
            disable();
        }
    }

    /** Runs one script in the view, e.g. the keybind capture resolution. */
    public void evaluate(String script) {
        if (view.isAvailable() && script != null) {
            try {
                view.evaluateScript(script);
            } catch (RuntimeException e) {
                VoidLog.warn("evaluateScript failed: " + e);
            }
        }
    }

    /**
     * Paints the view over the whole screen.
     *
     * <p>Depth off, blend on. Ultralight's texture is premultiplied alpha with
     * a top-left origin, so it is drawn with {@code (ONE, ONE_MINUS_SRC_ALPHA)}
     * and a flipped V — see {@link #PREMULTIPLIED}.</p>
     */
    public void paint(int screenWidth, int screenHeight) {
        if (!view.isAvailable()) {
            return;
        }
        int texture = view.glTextureId();
        if (texture == 0) {
            return;
        }
        GlBlit.begin2d(screenWidth, screenHeight);
        try {
            GlBlit.drawTexture(texture, 0, 0, screenWidth, screenHeight,
                    PREMULTIPLIED, true, 1f);
        } finally {
            GlBlit.end2d();
        }
    }

    /**
     * Whether the view's texture is premultiplied alpha.
     *
     * <p>{@code mod/native}'s API says premultiplied, which is what Ultralight's
     * GPU driver produces; PVP_ARCHITECTURE.md §6.2 says "straight-alpha blend".
     * Premultiplied wins because it is the one of the two statements that
     * describes the texture rather than the blend, and blending a premultiplied
     * texture with {@code SRC_ALPHA} darkens every antialiased edge. Flip this
     * if the binding turns out to unpremultiply.</p>
     */
    public static final boolean PREMULTIPLIED = true;

    /** {@code window.void.__hasFocus()} — does JS have a text input focused? */
    public boolean hasFocusedInput() {
        if (!view.isAvailable()) {
            return false;
        }
        try {
            String result = view.evaluateScript("window.void.__hasFocus()");
            return "true".equalsIgnoreCase(result == null ? "" : result.trim());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // -- input forwarding (menu mode only, §6.3) -------------------------

    public void mouseMoved(int x, int y) {
        if (view.isAvailable()) {
            view.fireMouseEvent(0, x, y, 0);
        }
    }

    public void mouseDown(int x, int y, int button) {
        if (view.isAvailable()) {
            view.fireMouseEvent(1, x, y, mouseButton(button));
        }
    }

    public void mouseUp(int x, int y, int button) {
        if (view.isAvailable()) {
            view.fireMouseEvent(2, x, y, mouseButton(button));
        }
    }

    public void scroll(int dx, int dy) {
        if (view.isAvailable()) {
            view.fireScrollEvent(dx, dy);
        }
    }

    public void keyDown(int virtualKey, int modifiers) {
        if (view.isAvailable()) {
            view.fireKeyEvent(0, virtualKey, modifiers, "");
        }
    }

    public void keyUp(int virtualKey, int modifiers) {
        if (view.isAvailable()) {
            view.fireKeyEvent(1, virtualKey, modifiers, "");
        }
    }

    public void keyChar(String text, int modifiers) {
        if (view.isAvailable() && text != null && !text.isEmpty()) {
            view.fireKeyEvent(2, 0, modifiers, text);
        }
    }

    public void setFocus(boolean focused) {
        if (view.isAvailable()) {
            view.setFocus(focused);
        }
    }

    /** LWJGL mouse button index to Ultralight's 0 none, 1 left, 2 middle, 3 right. */
    public static int mouseButton(int lwjglButton) {
        switch (lwjglButton) {
            case 0:
                return 1;
            case 1:
                return 3;
            case 2:
                return 2;
            default:
                return 0;
        }
    }

    public int logicalWidth() {
        return logicalWidth;
    }

    public int logicalHeight() {
        return logicalHeight;
    }

    public double deviceScale() {
        return deviceScale;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    private void disable() {
        WebView old = view;
        view = new NullWebView();
        try {
            old.close();
        } catch (RuntimeException ignored) {
            // Already broken; nothing useful to do with a second failure.
        }
    }

    public void shutdown() {
        try {
            view.close();
        } catch (RuntimeException e) {
            VoidLog.warn("in-game UI shutdown failed: " + e);
        }
        view = new NullWebView();
    }
}
