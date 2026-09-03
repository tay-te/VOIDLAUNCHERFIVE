package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;

import java.util.function.Function;

/**
 * {@link WebView} over {@code mod/native}'s Ultralight binding: one renderer,
 * one transparent view sized to the framebuffer, rendering straight into
 * Minecraft's GL context (§6.2). No CPU readback and no full-frame texture
 * upload — the view paints into a GL texture we then draw as a quad.
 *
 * <p>Every call goes through {@link NativeUltralight}; see the note there on
 * why the binding is reached by reflection rather than by import.</p>
 */
final class UltralightWebView implements WebView {

    private final Object renderer;
    private final Object view;
    private boolean closed;

    private UltralightWebView(Object renderer, Object view) {
        this.renderer = renderer;
        this.view = view;
    }

    /**
     * Creates the renderer and its one view.
     *
     * @throws UnsatisfiedLinkError when the binding or its natives are missing;
     *         {@link WebViews#create} turns that into a {@link NullWebView}
     */
    static UltralightWebView create(int width, int height) {
        Object renderer = NativeUltralight.createRenderer();
        Object view = NativeUltralight.call(NativeUltralight.rendererCreateView, renderer,
                Integer.valueOf(Math.max(1, width)), Integer.valueOf(Math.max(1, height)),
                Boolean.TRUE);
        if (view == null) {
            throw new UnsatisfiedLinkError("Ultralight createView returned null");
        }
        return new UltralightWebView(renderer, view);
    }

    @Override
    public boolean isAvailable() {
        return !closed;
    }

    @Override
    public void loadUrl(String url) {
        NativeUltralight.call(NativeUltralight.viewLoadUrl, view, url);
    }

    @Override
    public void resize(int width, int height) {
        NativeUltralight.call(NativeUltralight.viewResize, view,
                Integer.valueOf(Math.max(1, width)), Integer.valueOf(Math.max(1, height)));
    }

    @Override
    public void setDeviceScale(double scale) {
        NativeUltralight.call(NativeUltralight.viewSetDeviceScale, view, Double.valueOf(scale));
    }

    @Override
    public void update() {
        NativeUltralight.call(NativeUltralight.rendererUpdate, renderer);
    }

    @Override
    public void render() {
        NativeUltralight.call(NativeUltralight.rendererRender, renderer);
    }

    @Override
    public int glTextureId() {
        Object id = NativeUltralight.call(NativeUltralight.viewGlTextureId, view);
        return id instanceof Number ? ((Number) id).intValue() : 0;
    }

    @Override
    public float uvScaleX() {
        return uvScale(NativeUltralight.viewUvScaleX);
    }

    @Override
    public float uvScaleY() {
        return uvScale(NativeUltralight.viewUvScaleY);
    }

    private float uvScale(java.lang.reflect.Method method) {
        if (method == null) {
            return 1f;
        }
        Object value = NativeUltralight.call(method, view);
        if (!(value instanceof Number)) {
            return 1f;
        }
        float scale = ((Number) value).floatValue();
        return scale > 0f && scale <= 1f ? scale : 1f;
    }

    @Override
    public boolean isDirty() {
        Object dirty = NativeUltralight.call(NativeUltralight.viewIsDirty, view);
        return dirty instanceof Boolean && ((Boolean) dirty).booleanValue();
    }

    @Override
    public void fireMouseEvent(int type, int x, int y, int button) {
        NativeUltralight.call(NativeUltralight.viewFireMouseEvent, view,
                Integer.valueOf(type), Integer.valueOf(x), Integer.valueOf(y),
                Integer.valueOf(button));
    }

    @Override
    public void fireKeyEvent(int type, int virtualKey, int modifiers, String text) {
        NativeUltralight.call(NativeUltralight.viewFireKeyEvent, view,
                Integer.valueOf(type), Integer.valueOf(virtualKey), Integer.valueOf(modifiers),
                text == null ? "" : text);
    }

    @Override
    public void fireScrollEvent(int dx, int dy) {
        NativeUltralight.call(NativeUltralight.viewFireScrollEvent, view,
                Integer.valueOf(dx), Integer.valueOf(dy));
    }

    @Override
    public String evaluateScript(String js) {
        Object result = NativeUltralight.call(NativeUltralight.viewEvaluateScript, view, js);
        return result == null ? "" : result.toString();
    }

    @Override
    public void setMessageHandler(Function<String, String> handler) {
        NativeUltralight.call(NativeUltralight.viewSetMessageHandler, view, handler);
    }

    @Override
    public void setFocus(boolean focused) {
        NativeUltralight.call(NativeUltralight.viewSetFocus, view, Boolean.valueOf(focused));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            NativeUltralight.call(NativeUltralight.viewClose, view);
            NativeUltralight.call(NativeUltralight.rendererClose, renderer);
        } catch (RuntimeException e) {
            VoidLog.warn("Ultralight close failed: " + e);
        }
    }
}
