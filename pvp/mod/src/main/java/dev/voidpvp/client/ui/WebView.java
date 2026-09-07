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

    /**
     * Marks the whole view dirty.
     *
     * <p>The renderer runs with {@code ForceRepaint} off, so it repaints only the regions it
     * knows changed and treats the rest of the render target as still valid. After a resize
     * that assumption is wrong — the target holds pixels drawn at the previous size, and any
     * area the new layout does not happen to touch keeps showing them. That is what the
     * ghosted, larger-looking text was.</p>
     */
    void setNeedsPaint();

    /**
     * Clears the dirty flag {@link #setNeedsPaint} sets.
     *
     * <p>Only meaningful where {@link #needsFullRepaintEachFrame} is true, and there it is what
     * keeps the demand-driven gate from latching. On the accelerated path the host sets the flag
     * before every render so the whole view is repainted; Ultralight clears it again as part of a
     * paint that <em>draws something</em>, and leaves it alone when the page has nothing new. The
     * two together are a trap: one render whose content had not changed left the flag set, the
     * host read that back as "dirty" on the next frame, set it again, and the view rendered every
     * frame for the rest of the process. Measured with the menu open and the cursor still — 50
     * paints/s against 0-2 presents/s, all of it publishing nothing.</p>
     *
     * <p>The caller may only clear a flag it set itself — a render it forced on a page that
     * reported itself clean. A flag Ultralight raised means it still wants a paint, and clearing
     * that would swallow the first paint of a page that is still coming up, which is the "cards
     * only appear once hovered" failure this whole gate has produced before.</p>
     */
    void clearNeedsPaint();

    /**
     * Whether every render has to repaint the whole view.
     *
     * <p>True for the accelerated renderer, where our GPU driver's handling of Ultralight's damage
     * rectangles is what corrupts changing text. False for the CPU renderer, whose damage tracking
     * is Ultralight's own and correct — and where a full repaint would also throw away the reason
     * that path is affordable, since only the dirty rectangle is uploaded.</p>
     */
    boolean needsFullRepaintEachFrame();

    /**
     * Whether {@code render()} should be called every frame rather than only when the view reports
     * itself dirty.
     *
     * <p>True for the CPU renderer. Its {@code render()} is internally a no-op when nothing has
     * changed, and the signal that something *has* changed is the surface's dirty bounds — which
     * only exist after a render. Gating the render on a dirty flag therefore deadlocks: nothing
     * renders, so nothing is dirty, so nothing renders. In practice the view then only updated when
     * an input event forced a repaint, which looked like cards appearing only once hovered.</p>
     */
    boolean rendersEveryFrame();

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

    /**
     * Clears the accelerated view's render target, so the next render starts from nothing.
     *
     * <p>Call it immediately before a {@link #render} whose result may be an empty page. Ultralight
     * never clears a view's own target, and a page with nothing left on it produces no draw
     * commands at all, so without this the target keeps the last frame that had content — the menu
     * the player just closed, welded to the screen for the life of the process. It cannot be
     * inferred inside the renderer: an empty command list also means "nothing changed this frame",
     * and clearing on that takes a perfectly good menu off the screen the moment it stops
     * animating. Only the host knows which of the two it is.</p>
     *
     * <p>No-op for the CPU renderer, which uploads the whole surface on every paint and cannot
     * hold a stale frame this way.</p>
     */
    void clearTarget();

    /** Valid after {@link #render}: RGBA, premultiplied alpha, top-left origin. */
    int glTextureId();

    /**
     * Which thread {@link #glTextureId} and the two uv scales belong to.
     *
     * <p>True for the accelerated renderer: reading the texture enters the engine and races a
     * resize, the pixels were produced by the UI thread's own GL context, and the call is where
     * the finished frame gets copied out of Ultralight's render target into the texture the game
     * thread is allowed to sample. So the UI thread reads all three after each paint and publishes
     * them, and the game thread's blit uses what was published.</p>
     *
     * <p>False for the CPU renderer, where the opposite holds: {@code glTextureId()} <em>is</em>
     * the upload — it copies the dirty rectangle of the surface into a texture — so it has to run
     * on the thread holding Minecraft's context, and it is the one engine call that may.</p>
     */
    boolean texturePublishedByUiThread();

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
