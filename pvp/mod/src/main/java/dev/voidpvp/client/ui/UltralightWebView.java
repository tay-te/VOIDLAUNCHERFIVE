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
 *
 * <h2>The renderer this ends up on is not the one that was asked for</h2>
 *
 * <p>The accelerated path is the default because it is the cheap one: the CPU
 * surface rasterises the whole page in software, measured at 3456x1926 — 6.7
 * megapixels — up to 99 times a second, which the player feels as the game
 * lagging. The same menu on the GL driver idles at zero paints and zero
 * presents per second.</p>
 *
 * <p>But that driver has run on exactly one machine: macOS, Apple's OpenGL 2.1
 * profile. It has never executed on Windows or Linux. Its GLSL 1.20 programs
 * are compiled and linked by whatever driver the player has, and if they do not
 * build there is nothing to paint with. That used to be the end of the story —
 * a process-wide flag was set, every accelerated render returned early, and the
 * player got an empty screen with no error on hardware where the CPU surface
 * would have worked perfectly. This class is what makes it degrade instead, in
 * two places:</p>
 *
 * <ul>
 *   <li><b>Before any view exists.</b> {@link #create} asks
 *       {@link Renderer#probeAccelerated()}, which builds the driver and reports
 *       whether it works, and creates a CPU view instead when it does not. This
 *       is the case that matters, because every failure mode we actually know of
 *       — shaders that will not compile, a program that will not link, a missing
 *       entry point, an unusable context — is decided at that moment.</li>
 *   <li><b>After the view exists.</b> {@link #update} polls
 *       {@link Renderer#acceleratedDriverFailed()} once a frame and rebuilds the
 *       view on the CPU path if the driver has died since. See
 *       {@link #fallBackToCpu()} for what that costs and why it is still here
 *       when no real failure is known to reach it.</li>
 * </ul>
 *
 * <p>Both are announced through {@link WebViews#announceDowngrade}. A silent
 * downgrade that costs the player 60 fps would be its own bug, and this codebase
 * has already been bitten once by a fallback nobody could see.</p>
 *
 * <h2>What a mid-session swap is allowed to touch</h2>
 *
 * <p>{@link #view} and {@link #accelerated} are mutable and volatile, which the
 * threading rules permit only in one direction. The UI thread owns the engine
 * and is the only thread that may create or destroy a view. The game thread
 * touches this object in exactly two places — {@link UiHost#paint}, which reads
 * {@link #texturePublishedByUiThread()} and then, only if that is false,
 * {@link #glTextureId()} and the uv scales; and {@link #isAvailable()}. Every
 * input event is posted to the UI thread rather than fired here.</p>
 *
 * <p>So the swap publishes {@link #view} first and {@link #accelerated} second.
 * A game thread that reads {@code accelerated == false} is guaranteed by the
 * volatile write ordering to see the CPU view in {@link #view}, and a game
 * thread that reads {@code true} does not look at {@link #view} at all. The old
 * view is then destroyed a frame later rather than immediately, for the one
 * remaining race — see {@link #fallBackToCpu()}.</p>
 */
final class UltralightWebView implements WebView {

    /** Where {@code Ultralight.load()} extracts natives from and resolves URLs against. */
    static final String RESOURCE_PREFIX = "assets/void/ui/";

    /**
     * The cap on the integer supersample factor while this view is on the CPU surface.
     *
     * <p>2 is enough to reach the host's target density at every fit scale a HiDPI window
     * produces (1.17 upwards, where the factor asked for is 2 or 1), so this cap does not
     * currently bind there. It binds on a client launched without the HiDPI flag, and it stays
     * where it is, because the CPU rasteriser's cost is quadratic in the factor: measured in game
     * at a fit scale of 2.28, one step up — a 6672x3740 raster — took the worst UI paint from
     * 44-56 ms to 123-128 ms and the worst game frame from ~51 ms to ~131 ms.</p>
     */
    private static final int MAX_SUPERSAMPLE_CPU = 2;
    /**
     * The same cap while this view is on the accelerated renderer, where the raster runs on the
     * GPU.
     *
     * <p>Higher because the GPU can afford it: the same step that costs the CPU surface an order
     * of magnitude cost the GL driver 0.4-0.9 ms mean paint against 0.7-1.5 ms, and ~7% of mean
     * frame rate. It is a ceiling, not a target — the host's target density still decides, so
     * this only bites below a fit scale of 0.67, which is where a client launched without the
     * HiDPI flag sits. There it is the difference between 1.17 and 2.34 dp/css.</p>
     */
    private static final int MAX_SUPERSAMPLE_GPU = 4;

    private final Renderer renderer;

    /**
     * The live view. Replaced, once at most, when an accelerated driver dies mid-session.
     *
     * <p>Volatile because {@link #fallBackToCpu()} writes it on the UI thread and
     * {@link #glTextureId()} reads it on the game thread. It is written before {@link #accelerated}
     * so that a game thread which has observed the new value of that flag is guaranteed to see this
     * one too.</p>
     */
    private volatile View view;

    /**
     * Which renderer is in force <em>now</em>. True to false at most once, never back.
     *
     * <p>Everything downstream that behaves differently per renderer reads it through
     * {@link #needsFullRepaintEachFrame()}, {@link #rendersEveryFrame()} and
     * {@link #texturePublishedByUiThread()}, and {@link UiHost} re-reads all three every frame —
     * which is what makes a mid-session change safe rather than a set of stale assumptions.</p>
     */
    private volatile boolean accelerated;

    /**
     * How many documents this view has been told to load. See {@link #documentGeneration()}.
     *
     * <p>Bumped by {@link #loadUrl} and by the reload {@link #fallBackToCpu()} performs, which are
     * the only two ways a document ever gets into a view here. Volatile for the same reason
     * {@link #view} is: the swap is the interesting write and it happens on the UI thread.</p>
     */
    private volatile int documentGeneration;

    /** Volatile: written by the UI thread in {@link #close()}, read by the game thread's blit. */
    private volatile boolean closed;
    /** So the downgrade is attempted once and announced once, whatever the driver does after. */
    private boolean fellBack;

    /**
     * Everything needed to rebuild the view, recorded as the host sets it.
     *
     * <p>A replacement view is a new view: it has no size, no device scale, no message handler, no
     * focus and no page. The host does not know it happened and will not re-issue any of that, so
     * this class has to remember it. Sizes are the only fields the game thread could ever read, and
     * it does not; all of these are written and read on the UI thread.</p>
     */
    private int width;
    private int height;
    private double deviceScale = 1;
    private String url;
    private Function<String, String> handler;
    private boolean focused;

    /**
     * The dead accelerated view, waiting one frame to be destroyed. See {@link #fallBackToCpu()}.
     */
    private View pendingClose;

    private UltralightWebView(Renderer renderer, View view, boolean accelerated,
                              int width, int height) {
        this.renderer = renderer;
        this.view = view;
        this.accelerated = accelerated;
        this.width = width;
        this.height = height;
    }

    /**
     * Creates the renderer and its one view, on the renderer this machine can actually run.
     *
     * <p>{@code accelerated} is a request, not an instruction. The caller has already established
     * that the accelerated path is wanted and that this thread has a GL context; what it cannot
     * know is whether the driver builds here. That question is asked once, before any view exists,
     * and answered by dropping to a CPU view — which is a slower interface, not a missing one.</p>
     *
     * @throws UnsatisfiedLinkError when the natives are missing or will not load
     * @throws NoClassDefFoundError when the JAR was built without {@code native/java}
     */
    static UltralightWebView create(int width, int height, boolean accelerated) {
        Ultralight.load();
        Renderer renderer = Ultralight.createRenderer(RESOURCE_PREFIX);
        if (renderer == null) {
            throw new UnsatisfiedLinkError("Ultralight.createRenderer returned null");
        }
        int w = Math.max(1, width);
        int h = Math.max(1, height);

        boolean gpu = accelerated;
        if (gpu) {
            // The probe runs the whole of the driver's start-up — entry points, both GLSL 1.20
            // programs, compile and link — on this thread's context, and answers before a view has
            // been created. It logs its own reasons on stderr, natively, which is the only channel
            // in this process that a broken log4j config cannot silence.
            if (WebViews.forcedProbeFailure()) {
                gpu = false;
                WebViews.announceDowngrade("VOID_UI_GPU_FAIL=probe: pretending the accelerated "
                        + "renderer is unavailable, without asking the driver");
            } else if (!renderer.probeAccelerated()) {
                gpu = false;
                WebViews.announceDowngrade("this machine's OpenGL driver will not run the "
                        + "accelerated renderer (see the gpu: errors above)");
            }
        }

        // Which renderer to build is decided here rather than by the caller, because only here is
        // the answer known: the request (VOID_UI_RENDERER / -Dvoid.ui.renderer), the GL context the
        // accelerated path needs (WebViews.acceleratedRequested, UiGlContext) and whether the
        // driver actually builds all have to agree, and the last of the three cannot be asked until
        // the renderer exists.
        View view = gpu
                ? renderer.createView(w, h, true)
                : renderer.createViewCpu(w, h, true);
        if (view == null) {
            throw new UnsatisfiedLinkError("Ultralight createView returned null");
        }
        VoidLog.info("Ultralight " + Ultralight.version() + " (WebKit "
                + Ultralight.webKitVersion() + ") ready");
        VoidLog.info("in-game renderer: " + (gpu ? "accelerated (GL driver)" : "cpu surface")
                + (accelerated && !gpu ? " [fell back from accelerated]" : ""));
        return new UltralightWebView(renderer, view, gpu, w, h);
    }

    @Override
    public boolean isAvailable() {
        return !closed;
    }

    @Override
    public boolean isAccelerated() {
        return accelerated;
    }

    /**
     * Read off the same field as {@link #isAccelerated()}, in the same expression, so the two can
     * never disagree — see {@link WebView#maxSupersample()} for why that is the whole point.
     */
    @Override
    public int maxSupersample() {
        return accelerated ? MAX_SUPERSAMPLE_GPU : MAX_SUPERSAMPLE_CPU;
    }

    @Override
    public int documentGeneration() {
        return documentGeneration;
    }

    @Override
    public boolean needsFullRepaintEachFrame() {
        return accelerated;
    }

    @Override
    public boolean rendersEveryFrame() {
        return !accelerated;
    }

    @Override
    public void setNeedsPaint() {
        view.setNeedsPaint(true);
    }

    @Override
    public void clearNeedsPaint() {
        view.setNeedsPaint(false);
    }

    @Override
    public void loadUrl(String url) {
        this.url = url;
        documentGeneration++;
        view.loadUrl(url);
    }

    @Override
    public void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        view.resize(this.width, this.height);
    }

    @Override
    public void setDeviceScale(double scale) {
        this.deviceScale = scale;
        view.setDeviceScale(scale);
    }

    @Override
    public void update() {
        // The one call the host makes every frame unconditionally, which is why the driver's health
        // is checked here rather than in render(). On the accelerated path render() is gated on the
        // view being dirty, so a driver that dies while the page is idle would otherwise not be
        // noticed until the player next moved something.
        maybeFallBackToCpu();
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
    public void clearTarget() {
        // The binding answers false for a CPU view rather than erroring, so this needs no guard.
        view.clearTarget();
    }

    @Override
    public int glTextureId() {
        return view.glTextureId();
    }

    @Override
    public boolean texturePublishedByUiThread() {
        return accelerated;
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
        this.handler = handler;
        view.setMessageHandler(handler);
    }

    @Override
    public void setFocus(boolean focused) {
        this.focused = focused;
        view.setFocus(focused);
    }

    // -- the mid-session fallback -----------------------------------------------------------

    /**
     * UI thread, once a frame: destroy last frame's dead view, and start the swap if the driver
     * has just died.
     */
    private void maybeFallBackToCpu() {
        if (pendingClose != null) {
            View dead = pendingClose;
            pendingClose = null;
            try {
                dead.close();
            } catch (RuntimeException e) {
                VoidLog.warn("in-game UI: closing the accelerated view failed: " + e);
            }
        }
        if (closed || fellBack || !accelerated) {
            return;
        }
        if (renderer.acceleratedDriverFailed()) {
            fallBackToCpu();
        }
    }

    /**
     * Rebuilds this view on the CPU surface after the GL driver has died. UI thread only.
     *
     * <p><b>Why this exists when the probe already covers every known failure.</b> It does not
     * exist because a mid-session death has been observed — none has. It exists because the
     * alternative, which is what the code did before, is a process-wide flag that latches on and
     * makes every subsequent accelerated render return without painting: the overlay simply stops
     * existing, forever, with no error the player can see. A dead flag is not a failure mode
     * anybody can act on. This is, and it is reachable and testable on demand through
     * {@code VOID_UI_GPU_FAIL=late} (see {@code gpu_driver_gl.cpp}).</p>
     *
     * <p><b>What it costs.</b> The page reloads. Whatever the player had open in the menu resets to
     * the entry URL, because a replacement view is a new view with no history and there is no way
     * to move a loaded document between them. Device scale, size, message handler and focus are
     * re-applied from what this class recorded.</p>
     *
     * <p><b>What this class cannot re-apply, and therefore has to report.</b> Everything above is
     * a property of the <em>view</em>, and this class owns all of it. The document is not: the
     * new page has no bridge shim, no loadout, no library and no menu, and this class has no
     * business knowing that those things exist. So it does the one thing it can honestly do —
     * bump {@link #documentGeneration()} — and {@link UiHost} notices and re-pushes the session.
     * Without that the mod goes on talking to a page it has lost: the menu vanishes, the host
     * keeps painting an empty document at full rate, and because the screen is still open the
     * player is left with the mouse ungrabbed and nothing on screen. Measured before the fix, at
     * {@code VOID_UI_GPU_FAIL=late}: 80 paints/s into a 3416x1920 surface with a damage bounding
     * box of 0%, forever.</p>
     *
     * <p><b>Why the old view is destroyed a frame later.</b> The game thread's blit reads
     * {@link #texturePublishedByUiThread()} and then, if it was true, a texture id that
     * {@link UiHost} published from this view. Destroying the accelerated view deletes that texture.
     * A game thread that read the flag microseconds before the swap and the texture id
     * microseconds after the destroy would bind a deleted name — one frame of an incomplete
     * texture, which samples white, which is a full-screen flash. Deferring the destroy to the next
     * UI iteration puts a whole frame period between the two reads. It is not a formal handshake —
     * there is deliberately no lock here, because a lock is the game thread waiting on a UI-thread
     * paint, the one thing this threading split exists to prevent — but it turns a plausible race
     * into an implausible one on a path that only runs when the GPU has already failed. The cost is
     * one frame in which the accelerated view still counts as live and nothing paints.</p>
     */
    private void fallBackToCpu() {
        View replacement;
        try {
            replacement = renderer.createViewCpu(width, height, true);
        } catch (RuntimeException e) {
            // Nothing better is available: keep the accelerated view rather than leaving the host
            // holding nothing. It will not paint, which is the old behaviour, but it is also the
            // only remaining option and it is now said out loud.
            fellBack = true;
            VoidLog.error("in-game UI: the GL driver failed and a CPU view could not be created "
                    + "either; the overlay is gone for this session", e);
            return;
        }
        fellBack = true;

        replacement.setDeviceScale(deviceScale);
        if (handler != null) {
            replacement.setMessageHandler(handler);
        }
        replacement.setFocus(focused);
        replacement.setNeedsPaint(true);
        if (url != null) {
            // The bump is what tells the host this happened. It is not bookkeeping: a fresh
            // document has none of the session on it — no bridge shim, no loadout, no menu — and
            // the host has no other way to find out. Before this existed the mod went on talking
            // to the page it had lost, and the player was left inside an open screen that drew
            // nothing. See {@link WebView#documentGeneration()}.
            documentGeneration++;
            replacement.loadUrl(url);
        }

        // Order matters and is the whole safety argument. The volatile write to `view` is published
        // before the volatile write to `accelerated`, so a game thread that observes the flag as
        // false is guaranteed to observe the CPU view; one that still observes true never reads
        // `view` at all.
        View dead = view;
        view = replacement;
        accelerated = false;
        pendingClose = dead;

        WebViews.announceDowngrade("the OpenGL driver failed after the view was created; the "
                + "in-game UI has been rebuilt on the CPU surface and the page has reloaded");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (pendingClose != null) {
                pendingClose.close();
                pendingClose = null;
            }
            view.close();
            renderer.close();
        } catch (RuntimeException e) {
            VoidLog.warn("Ultralight close failed: " + e);
        }
    }
}
