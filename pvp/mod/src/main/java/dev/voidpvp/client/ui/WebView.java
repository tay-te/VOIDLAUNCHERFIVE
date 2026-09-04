package dev.voidpvp.client.ui;

import java.util.function.Function;

/**
 * The mod's view of an Ultralight surface: one renderer plus the one view we
 * paint, behind a seam the rest of the mod can hold without knowing whether
 * the native binding is present.
 *
 * <p>There is exactly one view (§6.2), sized to the framebuffer and drawn
 * twice — once at the end of {@code InGameHud.render} for the HUD layer and
 * once in {@code VoidMenuScreen} for the menu layer. The React app decides
 * what is visible, so the two layers share this object.</p>
 *
 * <p>Implementations: {@link UltralightWebView} over {@code mod/native}'s JNI
 * binding, and {@link NullWebView} when that binding will not load — in which
 * case the HUD is simply absent and the game is untouched.</p>
 */
public interface WebView extends AutoCloseable {

    /** False for {@link NullWebView}: nothing will ever be painted. */
    boolean isAvailable();

    /** Resolves inside the renderer's resource prefix, e.g. {@code file:///index.html}. */
    void loadUrl(String url);

    /** Logical (CSS pixel) size of the view. */
    void resize(int width, int height);

    /** MC GUI scale x window DPI x the launcher's {@code ui_scale} (§6.2). */
    void setDeviceScale(double scale);

    /** Runs JS timers and layout; once per game <em>tick</em> (CONTRACTS.md, rule 3). */
    void update();

    /**
     * Advances CSS animations, transitions and {@code requestAnimationFrame};
     * once per frame, immediately before {@link #render}. Without it the page is
     * static — the panel enter motion of §9 never plays (CONTRACTS.md, rule 3).
     */
    void refreshDisplay();

    /** Paints dirty views into their GL textures; needs MC's GL context current. */
    void render();

    /** Valid after {@link #render}: RGBA, premultiplied alpha, top-left origin. */
    int glTextureId();

    /**
     * Right edge of the view inside its backing texture, in UV space.
     * The binding may hand back a texture larger than the view, so the quad
     * samples this sub-rectangle rather than the whole thing.
     */
    float uvScaleX();

    /** Bottom edge of the view inside its backing texture, in UV space. */
    float uvScaleY();

    boolean isDirty();

    /** type: 0 move, 1 down, 2 up. button: 0 none, 1 left, 2 middle, 3 right. */
    void fireMouseEvent(int type, int x, int y, int button);

    /** type: 0 keydown, 1 keyup, 2 char. modifiers: 1 alt, 2 ctrl, 4 meta, 8 shift. */
    void fireKeyEvent(int type, int virtualKey, int modifiers, String text);

    void fireScrollEvent(int dx, int dy);

    /** Returns the result as a string, or the empty string for undefined. */
    String evaluateScript(String js);

    /** Handles {@code window.__void_native(json)}; runs on the render thread. */
    void setMessageHandler(Function<String, String> handler);

    void setFocus(boolean focused);

    @Override
    void close();
}
