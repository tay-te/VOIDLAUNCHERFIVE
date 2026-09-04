package dev.voidpvp.client.ui;

import dev.voidclient.ultralight.Renderer;
import dev.voidclient.ultralight.Ultralight;
import dev.voidclient.ultralight.View;
import dev.voidpvp.client.VoidLog;

import java.util.function.Function;

/**
 * {@link WebView} over {@code mod/native}'s Ultralight binding: one renderer,
 * one transparent view sized to the framebuffer, rendering straight into
 * Minecraft's GL context (§6.2). No CPU readback and no full-frame texture
 * upload — the view paints into a GL texture we then draw as a quad.
 *
 * <p><b>Why this is a plain import now.</b> It used to reach the binding by
 * reflection, because the architecture's {@code dev.void.ultralight} is not a
 * legal package (§4: {@code void} is a keyword) and the two owners had to
 * rename it independently. Both names are settled —
 * {@code dev.voidclient.ultralight} for the binding, {@code dev.voidpvp.client}
 * for the mod — and {@code build.gradle} already compiles {@code native/java}
 * into this JAR, so the indirection bought nothing but a stringly-typed API
 * that could not be checked at compile time. A signature change in the binding
 * is now a compile error instead of a {@code NoSuchMethodException} in game.</p>
 *
 * <p><b>The fallback is unaffected.</b> {@link WebViews} still answers with
 * {@link NullWebView} when the binding cannot run: {@link Ultralight#load()}
 * throws {@link UnsatisfiedLinkError} when this platform's natives are not in
 * the JAR, and a JAR built without {@code native/java} at all throws
 * {@link NoClassDefFoundError} on the first touch of this class — both are
 * {@link LinkageError}, both are caught there, and neither reaches the game.
 * That is why every reference to the binding lives in this one class.</p>
 */
final class UltralightWebView implements WebView {

    /** Where {@code Ultralight.load()} extracts natives from and resolves URLs against. */
    static final String RESOURCE_PREFIX = "assets/void/ui/";

    private final Renderer renderer;
    private final View view;
    private boolean closed;

    private UltralightWebView(Renderer renderer, View view) {
        this.renderer = renderer;
        this.view = view;
    }

    /**
     * Creates the renderer and its one view.
     *
     * @throws UnsatisfiedLinkError when the natives are missing or will not load
     * @throws NoClassDefFoundError when the JAR was built without {@code native/java}
     */
    static UltralightWebView create(int width, int height) {
        Ultralight.load();
        Renderer renderer = Ultralight.createRenderer(RESOURCE_PREFIX);
        if (renderer == null) {
            throw new UnsatisfiedLinkError("Ultralight.createRenderer returned null");
        }
        View view = renderer.createView(Math.max(1, width), Math.max(1, height), true);
        if (view == null) {
            throw new UnsatisfiedLinkError("Ultralight createView returned null");
        }
        VoidLog.info("Ultralight " + Ultralight.version() + " (WebKit "
                + Ultralight.webKitVersion() + ") ready");
        return new UltralightWebView(renderer, view);
    }

    @Override
    public boolean isAvailable() {
        return !closed;
    }

    @Override
    public void loadUrl(String url) {
        view.loadUrl(url);
    }

    @Override
    public void resize(int width, int height) {
        view.resize(Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void setDeviceScale(double scale) {
        view.setDeviceScale(scale);
    }

    @Override
    public void update() {
        renderer.update();
    }

    @Override
    public void refreshDisplay() {
        renderer.refreshDisplay();
    }

    @Override
    public void render() {
        renderer.render();
    }

    @Override
    public int glTextureId() {
        return view.glTextureId();
    }

    @Override
    public float uvScaleX() {
        return clampUv(view.uvScaleX());
    }

    @Override
    public float uvScaleY() {
        return clampUv(view.uvScaleY());
    }

    /** A binding that fills its texture exactly reports 1; anything odd is treated as 1. */
    private static float clampUv(float scale) {
        return scale > 0f && scale <= 1f ? scale : 1f;
    }

    @Override
    public boolean isDirty() {
        return view.isDirty();
    }

    @Override
    public void fireMouseEvent(int type, int x, int y, int button) {
        view.fireMouseEvent(type, x, y, button);
    }

    @Override
    public void fireKeyEvent(int type, int virtualKey, int modifiers, String text) {
        view.fireKeyEvent(type, virtualKey, modifiers, text == null ? "" : text);
    }

    @Override
    public void fireScrollEvent(int dx, int dy) {
        view.fireScrollEvent(dx, dy);
    }

    @Override
    public String evaluateScript(String js) {
        String result = view.evaluateScript(js);
        return result == null ? "" : result;
    }

    @Override
    public void setMessageHandler(Function<String, String> handler) {
        view.setMessageHandler(handler);
    }

    @Override
    public void setFocus(boolean focused) {
        view.setFocus(focused);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            view.close();
            renderer.close();
        } catch (RuntimeException e) {
            VoidLog.warn("Ultralight close failed: " + e);
        }
    }
}
