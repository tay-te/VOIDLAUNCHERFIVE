package dev.voidpvp.client.ui;

import java.util.function.Function;

/**
 * The view used when the Ultralight binding will not load: every call is a
 * no-op, {@link #isAvailable} is false, and the game runs exactly as it would
 * without the mod.
 *
 * <p>This is the whole failure story for a missing or broken native library
 * (§6.2): log once, disable the HUD, do not touch the game. It is also what
 * makes the JAR runnable on a machine that has no natives for its platform at
 * all, which is how it is built and tested here.</p>
 */
public final class NullWebView implements WebView {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void loadUrl(String url) {
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void setDeviceScale(double scale) {
    }

    @Override
    public void update() {
    }

    @Override
    public void refreshDisplay() {
    }

    @Override
    public void render() {
    }

    @Override
    public int glTextureId() {
        return 0;
    }

    @Override
    public float uvScaleX() {
        return 1f;
    }

    @Override
    public float uvScaleY() {
        return 1f;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public void fireMouseEvent(int type, int x, int y, int button) {
    }

    @Override
    public void fireKeyEvent(int type, int virtualKey, int modifiers, String text) {
    }

    @Override
    public void fireScrollEvent(int dx, int dy) {
    }

    @Override
    public String evaluateScript(String js) {
        return "";
    }

    @Override
    public void setMessageHandler(Function<String, String> handler) {
    }

    @Override
    public void setFocus(boolean focused) {
    }

    @Override
    public void close() {
    }
}
