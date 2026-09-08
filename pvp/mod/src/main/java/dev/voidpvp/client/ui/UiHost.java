package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.input.InputLatency;
import dev.voidpvp.client.render.GlBlit;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
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

    /** Frames painted unconditionally after a create, load or resize. */
    private static final int FORCED_RENDERS = 3;
    /**
     * How long to keep rendering after any input reaches the view.
     *
     * <p>Long enough to cover any of the page's own movements — the 170 ms grid contraction and
     * properties entrance, the 110 ms open fade, the 80 ms close fade — because the dirty flag
     * cannot be trusted to carry one: it only becomes true *after* a render, so a view that is
     * mid-transition and idle stops waking itself. This is what lets the menu stop rendering
     * unconditionally — you pay the full frame cost while you are actually touching the UI and
     * 0.07 ms while you are not.</p>
     *
     * <p>A duration and not a frame count, which it used to be. The count was 20, chosen as a
     * third of a second at the 60 Hz the loop then ran at flat; input can now pull an iteration
     * forward ({@link #uiLoop}), so a burst of clicks would have spent the same 20 frames in half
     * the time and cut a transition off halfway. What the window has to cover is measured in
     * milliseconds, so it is written in milliseconds.</p>
     */
    private static final long INPUT_RENDER_NANOS = 350L * 1_000_000L;

    /**
     * How long after {@link #requestRender} the view's target may be blanked.
     *
     * <p>Long enough to outlast anything the page does in reply to being told its content is
     * going away. The longest of those is the menu close: a 60 ms fade that cannot start until
     * React has committed the class and Ultralight has styled it — measured at ~50 ms — and an
     * unmount on the fade's own {@code animationend}, with {@code App.tsx}'s
     * {@code EXIT_FALLBACK_MS} of 400 ms as the backstop for when that event never comes. 450 ms
     * covers the backstop with a frame to spare.</p>
     */
    private static final long CLEAR_WINDOW_NANOS = 450L * 1_000_000L;


    /** The UI thread's idle beat: 60 Hz, the rate CSS animations are authored at. */
    private static final long PERIOD_NANOS = 1_000_000_000L / 60L;
    /**
     * The closest two UI iterations may be, however much input arrives.
     *
     * <p>Input pulls the next iteration forward (see {@link #uiLoop}); this stops that from
     * becoming a free-running loop when the game thread is delivering events faster than 60 Hz,
     * which it does whenever the game is running uncapped.</p>
     */
    private static final long MIN_PERIOD_NANOS = 1_000_000_000L / 120L;

    /**
     * Frame diagnostics, off unless {@code VOID_UI_PROFILE} is set.
     *
     * <p>Kept in the tree because "the overlay feels laggy" is not a number, and turning it into
     * one needs the payload that preceded a slow frame, not just the slow frame. The native side
     * prints the stall histogram; this names what the page was asked to do.</p>
     */
    private static final boolean PROFILE = System.getenv("VOID_UI_PROFILE") != null;
    /**
     * Self-test: clicks a real switch in the mods grid on a timer, so the expensive interaction can
     * be measured without a person driving it. It goes through the real path — React, the store,
     * the bridge call into Java — and clicks the same control repeatedly, so the loadout ends where
     * it started.
     */
    private static final boolean SELFTEST = System.getenv("VOID_UI_SELFTEST") != null;
    /**
     * Profiling control, off unless {@code VOID_UI_NOHUD} is set: drop the HUD layer out of the
     * page once, so the overlay draws nothing at all. It is the only way to measure the game's own
     * frame cost through the same counter that measures ours — the alternative, running without
     * the mod, changes the thing being measured as well as the number.
     */
    private static final boolean NOHUD = System.getenv("VOID_UI_NOHUD") != null;
    /**
     * Self-test, off unless {@code VOID_UI_CLICKTEST} is set: click a real control with real
     * pointer events, on the real path — {@code VoidMenuScreen.mouseClicked} -> {@link #mouseDown}
     * -> the queue -> the view -> render -> publish -> the game thread's blit. The JS self-test
     * above calls {@code element.click()} and so measures everything <em>except</em> that path,
     * which is the one input latency lives in.
     */
    private static final boolean CLICKTEST = System.getenv("VOID_UI_CLICKTEST") != null;
    /**
     * Scroll tracing, off unless {@code VOID_UI_SCROLLLOG} is set: every wheel delta as it
     * arrives, every slice paid out, and what the page's scrollers actually did with it. This is
     * the only way to tell "the event never arrived" from "the event arrived and moved nothing".
     */
    public static final boolean SCROLLLOG = System.getenv("VOID_UI_SCROLLLOG") != null;
    private boolean nohudApplied;
    private long lastSelfTestAt;
    private String lastScript;
    private double lastScriptMs;

    /**
     * Device pixels per CSS pixel to rasterise at, and the most supersampling allowed to reach it.
     *
     * <p>The framebuffer alone does not give browser-like density. The design is a fixed 1300-wide
     * canvas fitted to the window, so in a small window the page lays out at ~1459 CSS pixels with
     * only ~1708 device pixels to draw into — about 1.17 per CSS pixel, where a browser on a 2x
     * display gets 2.0. The antialiasing is not wrong at that point, there is simply not enough of
     * it, which is what reads as jagged. Rendering the view larger than the framebuffer and letting
     * the blit minify recovers the samples. A window that already clears the target pays nothing,
     * which is also why the factor is capped: at full screen the framebuffer is already dense.</p>
     *
     * <p><b>Check the HiDPI flag before blaming this.</b> The launcher starts the JVM with
     * {@code -Dorg.lwjgl.opengl.Display.enableHighDPI=true}, and that is what makes Minecraft's
     * framebuffer the display's real pixel grid ({@link dev.voidpvp.client.HiDpi}). Without it
     * — {@code ./gradlew runClient} does not set it — the whole game, not just this overlay,
     * renders at 1x into a 2x drawable and the compositor stretches it, and every scale below is
     * halved: measured, the same window reports 1.17 dp/css without the flag and 2.28-2.35 with
     * it. Supersampling cannot recover that, and the numbers here are only meaningful with the
     * flag set.</p>
     */
    private static final double TARGET_DENSITY = 2.0;
    /**
     * {@code VOID_UI_SUPERSAMPLE} pins the factor outright — past the cap and past
     * {@link #TARGET_DENSITY} — so the cost of each step can be measured without a rebuild. It has
     * to override the target as well as the cap: at the fit scales a HiDPI window produces the
     * target is already met at 1, so a knob that only raised the ceiling could not move anything.
     * Out of range values are ignored rather than clamped, because a typo should not quietly
     * change what is being measured.
     */
    private static final int SUPERSAMPLE_OVERRIDE = supersampleOverride();

    private static int supersampleOverride() {
        String raw = System.getenv("VOID_UI_SUPERSAMPLE");
        if (raw == null) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 1 && value <= 8 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private final VoidBridge bridge;
    /**
     * Volatile because the UI thread creates it and the game thread reads it in {@link #paint}.
     * {@code glTextureId()} and {@code uvScale*()} are the only two calls the binding permits from
     * a second thread, and {@link #paint} uses exactly those.
     */
    private volatile WebView view = new NullWebView();
    private boolean started;

    // -- the UI thread -----------------------------------------------------
    //
    // Ultralight's CPU renderer runs synchronously wherever it is called, so while update/render
    // lived on Minecraft's render thread every millisecond of UI work was a millisecond of game
    // frame — animation frames of 6-28 ms landing on a frame that was already 11-15 ms. Moving the
    // engine to its own thread makes those concurrent instead of additive; the game thread keeps
    // only the GL work, which is where its context is.
    //
    // The binding's rules (mod/native/src/jni_api.cpp) shape everything here:
    //   · one thread owns the renderer and every view for the life of the process — pinning, not
    //     locking, because WebCore builds per-thread globals on first touch;
    //   · that thread must be a *Java* thread, since createRenderer resolves Resources through the
    //     class loader of the nearest Java frame;
    //   · it must never return from run(), because a thread that has touched WebCore aborts the
    //     process on the way out. It parks instead.
    private volatile Thread uiThread;
    /** Work the game thread hands to the UI thread: input, scripts, lifecycle. */
    private final ConcurrentLinkedQueue<Runnable> uiWork = new ConcurrentLinkedQueue<Runnable>();
    /**
     * Mouse movement is coalesced rather than queued. It arrives once per game frame and only the
     * newest position matters; queueing each one would make a slow UI iteration replay a stale
     * trail of positions instead of jumping to where the cursor actually is.
     */
    private volatile boolean pendingMouseMove;
    private volatile int pendingMouseX;
    private volatile int pendingMouseY;
    /** Tracing only ({@code VOID_UI_INPUTLOG}): when the newest pending position was handed over. */
    private volatile long pendingMouseStamp;
    /**
     * The last position actually delivered to the view, so a cursor that has not moved does not
     * keep the page awake.
     *
     * <p>{@code VoidMenuScreen.render} forwards the pointer once per game frame whether or not it
     * moved — it has no event to key off, only the current position. Every one of those reached
     * {@link #wake}, so with the menu open the view repainted on ~99% of UI frames while standing
     * perfectly still: measured at 50 paints/s against 0-2 presents/s, i.e. a full-view repaint
     * fifty times a second to publish nothing. Filtering here rather than at the call site keeps
     * the drag path (which calls the same method) on the same rule.</p>
     *
     * <p>Not the same as dropping the event: a move to a new position still fires and still wakes.
     * {@code Integer.MIN_VALUE} is the "nothing sent yet" marker, so the first frame always
     * delivers.</p>
     */
    private int sentMouseX = Integer.MIN_VALUE;
    private int sentMouseY = Integer.MIN_VALUE;
    /** What the game thread wants; the UI thread applies it when it next comes round. */
    private volatile int wantFbWidth;
    private volatile int wantFbHeight;
    private volatile double wantScale = 1;
    /**
     * Cached on the UI thread so the game thread never has to ask the view a question.
     *
     * <p>False is the safe answer and is what a page that has not answered yet gets: Escape then
     * closes the menu, which is the behaviour with no page at all.</p>
     */
    private volatile boolean escapeHandledByPage;

    // Written by the UI thread, read by the game thread every frame (VoidMenuScreen sizes the GL
    // shadow pass from logicalWidth).
    private volatile int logicalWidth;
    private volatile int logicalHeight;

    private volatile int framebufferWidth;
    private volatile int framebufferHeight;
    private volatile double deviceScale = 1;
    /** Whether the page has finished loading far enough to have installed {@code window.void}. */
    private boolean bridgeReady;

    /**
     * Whether the accelerated renderer was <em>asked</em> for and this thread could take a GL
     * context — resolved once, on the UI thread, because taking the context is what answers it.
     *
     * <p><b>This is the request, not the result.</b> The accelerated renderer can still fail
     * inside {@code WebViews.create}: the GL driver has to build its GLSL programs on the actual
     * GPU, and if it will not, {@code create} returns a CPU view instead — and it can fail again
     * later, mid-session, after having worked. So this may be read at exactly one place, the
     * argument handed to {@code create}, and it is. Nothing is sized from it any more: the first
     * view used to be, because a supersample factor had to be chosen before there was a view to
     * ask, and {@link #applySize} no longer chooses one until there is.</p>
     */
    private boolean acceleratedRequested;
    private boolean acceleratedProbed;

    /**
     * Which renderer is actually in use. Not a field, and there is no longer one anywhere.
     *
     * <p>Diagnostics only now — the supersample factor, which is what this answer used to be
     * <em>for</em>, no longer goes through here at all; see {@link #supersample()}. It asks the
     * live view, which is the object the renderer belongs to, so it cannot be stale.</p>
     */
    private boolean accelerated() {
        return view.isAccelerated();
    }

    /**
     * The integer supersample factor this view should be rasterised at, right now.
     *
     * <p><b>This is the only expression in the mod that produces a supersample factor, and its
     * only input is the live view.</b> That is deliberate and it is the third attempt at it. The
     * cap belongs to the renderer — the CPU rasteriser's cost is quadratic in this number and the
     * GPU's is nearly free — and the renderer can change mid-session, so every previous shape
     * stored the answer somewhere and every one of them went stale: a host field set once behind
     * a {@code maxSupersample == 0} guard, then a host field holding both the request and the
     * result, then {@code WebViews.acceleratedInUse}, a static written by {@code create} and
     * never by the mid-session fallback. Each was a correct read of a value that had stopped
     * being true. {@code design/rendering-invariants.md} names this cap as one of its four
     * examples of the same failure.</p>
     *
     * <p>There is nothing left to keep in sync. {@link WebView#maxSupersample()} is answered off
     * the same volatile field as {@link WebView#isAccelerated()}, in the same expression, and
     * {@link #applySize} calls this <em>after</em> the view exists — including on the very frame
     * the view is created, which is why nothing here has to guess a cap before there is a
     * renderer to ask.</p>
     */
    private int supersample(double scale) {
        if (SUPERSAMPLE_OVERRIDE > 0) {
            return SUPERSAMPLE_OVERRIDE;
        }
        // An integer factor only: a fractional one puts glyphs on a non-integer grid relative to
        // the screen and trades one kind of shimmer for another.
        int wanted = (int) Math.ceil(TARGET_DENSITY / scale);
        return Math.max(1, Math.min(view.maxSupersample(), wanted));
    }

    /**
     * The supersample factor the current view was last sized at.
     *
     * <p>Compared against the factor {@link #supersample()} computes now, so a view whose cap has
     * changed under it — which is exactly what a mid-session fallback does — is resized without
     * anyone having to remember to invalidate anything. The logical size cannot carry this:
     * supersampling changes only how densely the page is rasterised, not how large it lays out, so
     * {@code sizeChanged} is false across exactly the change that matters here.</p>
     */
    private int viewSupersample;

    /**
     * The device scale the view was actually told, i.e. {@link #deviceScale} times
     * {@link #viewSupersample}.
     *
     * <p>Tracked separately from {@link #deviceScale}, which is the logical scale the game thread
     * reads, because the two move independently and the view only ever hears this one. A
     * supersample change with no scale change is the case that matters and it is not
     * hypothetical: it is precisely what a mid-session fallback produces. Ultralight allocates a
     * render target from size over device scale, so resizing to {@code fb * 2} while the view
     * still believed in {@code s * 4} would have laid the page out in half the CSS width — every
     * glyph and every box twice the size it should be. The old condition compared the logical
     * scale, which does not change across that, so the {@code setDeviceScale} was skipped.</p>
     */
    private double appliedViewScale = 1;

    /**
     * The document this host believes the view is showing.
     *
     * <p>Set from the view at the moment this host loads a page into it, and compared against
     * {@link WebView#documentGeneration()} once a frame. A difference means the view loaded
     * something the host did not ask for — today that is the reload
     * {@link UltralightWebView#fallBackToCpu} performs when the GL driver dies, and tomorrow it is
     * whatever else reloads a page. UI thread only.</p>
     */
    private int documentGeneration;

    /**
     * The accelerated view's texture and uv extent, read on the UI thread after each paint.
     *
     * <p>The game thread must not ask the view for these: for an accelerated view both are engine
     * calls that race a resize, and asking would also be a renderer call from the wrong thread.
     * The CPU path is the other way round and keeps asking directly — there {@code glTextureId()}
     * <em>is</em> the upload into Minecraft's context. See {@link WebView#texturePublishedByUiThread}.</p>
     */
    private volatile int publishedTexture;
    private volatile float publishedUvX = 1f;
    private volatile float publishedUvY = 1f;
    /**
     * How many painted frames this thread has published, so the game thread can tell whether the
     * pixels it is about to blit are older or newer than a given moment. Diagnostics only — the
     * blit itself never looks at it.
     */
    private volatile long publishSeq;
    /** While true, render every frame rather than only when the view reports itself dirty. */
    private volatile boolean continuous;
    /**
     * UI thread: until this nanoTime, blank the view's target before any render that is going to
     * repaint it. Opened by {@link #requestRender}.
     */
    private long clearUntilNanos;
    /** UI thread: keep rendering until this nanoTime, whatever the dirty flag says. See {@link #wake}. */
    private long inputUntilNanos;
    /**
     * Wheel travel that arrived since the last UI frame, in CSS pixels. UI thread only.
     *
     * <p>A one-frame mailbox, not a budget. Minecraft pumps a screen's input once per <em>tick</em>
     * — 20 Hz — so a trackpad's stream reaches {@link #scroll} in bursts of five or six events at
     * a time; this adds them up so the frame hands the view one wheel event rather than six, and
     * {@link #driveScroll} empties it completely every frame. Nothing is ever carried over, which
     * is the property that matters: see {@link #driveScroll} for what carrying it over did.</p>
     */
    private int scrollPendingX;
    private int scrollPendingY;
    /** Scroll tracing ({@code VOID_UI_SCROLLLOG}) only: the last scroller offsets that were read. */
    private String scrollTraceBefore = "";
    private long scrollTraceSettleAt;
    private long scrollTraceStart;
    /** Whether the menu screen is up, i.e. whether the view has input focus. */
    private volatile boolean menuFocused;
    private int forcedRenders;

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
        // The accelerated renderer needs a GL context on the UI thread, and a context can only be
        // shared from the thread that already has one — which is this one. Doing it here, before
        // the size the UI thread waits for is published, is what orders it ahead of the view
        // creation that needs it. Cheap and idempotent after the first call.
        if (WebViews.acceleratedRequested()) {
            UiGlContext.prepare();
        }
        // Game thread. Record what the window is now and let the UI thread act on it: creating or
        // resizing a view is a renderer call, and those are pinned to the thread that owns the
        // renderer.
        wantFbWidth = fbWidth;
        wantFbHeight = fbHeight;
        wantScale = scale <= 0 ? 1 : scale;
        startUiThread();
    }

    /**
     * Applies whatever the game thread last asked for. UI thread only.
     *
     * <p>The supersample maths is unchanged; what moved is when it runs. It reads the request
     * fields rather than parameters because the game thread may have changed the window several
     * times since the UI thread last came round, and only the newest size is worth acting on.</p>
     */
    private void applySize() {
        int fbWidth = wantFbWidth;
        int fbHeight = wantFbHeight;
        double s = wantScale;
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }
        if (!acceleratedProbed) {
            // First time round, and the first moment this thread may take a GL context: the
            // accelerated renderer is only available if the game thread managed to share its
            // context and this thread can make it current. Whether it then actually starts is
            // decided inside WebViews.create, and is not this flag's to say — see accelerated().
            acceleratedRequested = WebViews.acceleratedRequested() && UiGlContext.makeCurrent();
            acceleratedProbed = true;
        }
        int lw = Math.max(1, (int) Math.ceil(fbWidth / s));
        int lh = Math.max(1, (int) Math.ceil(fbHeight / s));

        boolean created = false;
        if (!started) {
            started = true;
            // Created at one view pixel per framebuffer pixel — no supersampling — because the
            // factor is capped by the renderer's cost and which renderer this view runs is not
            // knowable until it exists. Nothing is guessed here: the sizing below asks the view
            // that was actually built, and it is reached on this same call, before the first
            // paint. That is what makes there be exactly one place a supersample factor is ever
            // computed, and one input to it.
            //
            // Ultralight sizes a view in DEVICE PIXELS; the CSS viewport it lays out in is that
            // size divided by the device scale. So the framebuffer goes in as-is and `s` does the
            // dividing — passing the already-divided logical size instead would land the page on
            // fb / s^2, i.e. everything drawn `s` times too large.
            WebView fresh = WebViews.create(fbWidth, fbHeight, acceleratedRequested);
            if (!fresh.isAvailable()) {
                view = fresh;
                return;
            }
            fresh.setDeviceScale(s);
            fresh.setMessageHandler(new Function<String, String>() {
                @Override
                public String apply(String request) {
                    // window.__void_native(json): JS to Java. This now arrives on the UI thread,
                    // not the render thread — VoidBridge marshals anything that has to touch the
                    // game back to the game thread.
                    return bridge.dispatch(request);
                }
            });
            fresh.loadUrl(ENTRY_URL);
            // The document this host asked for. Anything else the view goes on to show is a
            // document it loaded by itself, which is a thing the page has to be told about — see
            // adoptDocumentIfChanged().
            documentGeneration = fresh.documentGeneration();
            logicalWidth = lw;
            logicalHeight = lh;
            framebufferWidth = fbWidth;
            framebufferHeight = fbHeight;
            deviceScale = s;
            viewSupersample = 1;
            appliedViewScale = s;
            forcedRenders = FORCED_RENDERS;
            view = fresh;
            created = true;
        }
        if (!view.isAvailable()) {
            return;
        }
        // Asked of the view, every time, and never stored. This is the line the whole of
        // supersample()'s javadoc is about.
        int ss = supersample(s);
        int vw = Math.max(1, fbWidth * ss);
        int vh = Math.max(1, fbHeight * ss);
        // The page still lays out in fb / s CSS pixels; only the rasterisation gets denser.
        double viewScale = s * ss;

        boolean sizeChanged = lw != logicalWidth || lh != logicalHeight;
        // Against the scale the *view* was told, not the logical one. They come apart exactly when
        // the supersample factor moves on its own, which is what a mid-session fallback does and
        // what the first sizing of a freshly created view does — see appliedViewScale.
        boolean scaleChanged = viewScale != appliedViewScale;
        // The view is created at 1 and a fallback halves the cap under it; this is where either
        // gets corrected, from the renderer that is actually running. It costs nothing when the
        // factor is already right.
        boolean supersampleChanged = ss != viewSupersample;
        // Scale first, then size. The render target is allocated from logical size x device
        // scale, so resizing while the old scale is still set sizes the texture for the wrong
        // number of pixels; the view then draws into a target that does not match it and the
        // blit samples the wrong fraction of it.
        if (scaleChanged) {
            view.setDeviceScale(viewScale);
            appliedViewScale = viewScale;
        }
        if (sizeChanged || scaleChanged || supersampleChanged) {
            logicalWidth = lw;
            logicalHeight = lh;
            framebufferWidth = fbWidth;
            framebufferHeight = fbHeight;
            deviceScale = s;
            viewSupersample = ss;
            view.resize(vw, vh);
            // The render target now holds pixels drawn at the old size. Ultralight repaints only
            // what it believes changed, so without this the untouched areas keep showing them —
            // which is the ghosted, oversized text that survived every resize.
            view.setNeedsPaint();
            forcedRenders = FORCED_RENDERS;
        } else if (!created) {
            return;
        }
        // One line, after the correction rather than before it, so the numbers it prints are the
        // ones the view ended up with. Logged on every resize and not only at creation: the window
        // this starts in is not the window it ends up in — the Retina fixup alone changes the
        // framebuffer within a second of launch — so a line printed once is a number that stops
        // being true almost immediately, which is exactly how the raster density came to be
        // misread.
        VoidLog.info("in-game UI " + (created ? "started at " : "resized to ") + lw + "x" + lh
                + " (scale " + s + ", rasterised " + vw + "x" + vh + " at " + viewScale
                + " dp/css)");
    }

    /**
     * Starts the one thread that owns the engine, if it is not already up.
     *
     * <p>Daemon so it cannot keep the JVM alive, but it never finishes either way: a thread that
     * has touched WebCore aborts the process when it returns from {@code run()}, so the loop parks
     * forever rather than exiting. Priority is left alone — this competes with the render thread
     * for a core, and starving either is worse than sharing.</p>
     */
    private synchronized void startUiThread() {
        if (uiThread != null) {
            return;
        }
        uiThread = new Thread(new Runnable() {
            @Override
            public void run() {
                uiLoop();
            }
        }, "void-ui");
        uiThread.setDaemon(true);
        uiThread.start();
    }

    /**
     * The UI thread. Owns the renderer and every view for the life of the process.
     *
     * <p>Paced rather than free-running: rendering faster than the game blits only burns a core,
     * because the game takes the newest surface whenever it gets there. 60 Hz is the rate CSS
     * animations are authored at and the rate {@code ulRefreshDisplay} expects to be told about.</p>
     *
     * <p><b>Parked, not slept.</b> A fixed 16.7 ms sleep is 16.7 ms of latency on every discrete
     * input, because a click posted just after this thread went to sleep is not even looked at
     * until the sleep runs out — measured at 8 ms mean, 17 ms worst, before anything is rendered
     * or blitted. {@link #post} unparks, so a click is picked up as soon as it arrives. The
     * {@link #MIN_PERIOD_NANOS} floor is what keeps that from turning into a free-running loop:
     * input can pull an iteration forward, but never closer than 8.3 ms to the last one, so the
     * idle rate stays 60 Hz and a burst tops out at 120.</p>
     */
    private void uiLoop() {
        while (true) {
            long began = System.nanoTime();
            try {
                applySize();
                drainWork();
                frameOnUiThread();
            } catch (RuntimeException e) {
                VoidLog.error("in-game UI frame failed, disabling", e);
                disable();
            } catch (Error e) {
                // A LinkageError here means the natives are gone; the loop must still not exit.
                VoidLog.error("in-game UI stopped", new RuntimeException(e));
                disable();
            }
            long deadline = began + PERIOD_NANOS;
            long floor = began + MIN_PERIOD_NANOS;
            while (true) {
                long now = System.nanoTime();
                if (now >= deadline) {
                    break;
                }
                // Re-read every time round: the permit that woke us may be stale, and work may
                // have arrived while we were checking.
                boolean pending = !uiWork.isEmpty() || pendingMouseMove;
                if (pending && now >= floor) {
                    break;
                }
                long until = pending ? Math.min(floor, deadline) : deadline;
                if (until <= now) {
                    break;
                }
                LockSupport.parkNanos(until - now);
            }
        }
    }

    /** Runs everything the game thread queued. UI thread only. */
    private void drainWork() {
        if (pendingMouseMove) {
            pendingMouseMove = false;
            int x = pendingMouseX;
            int y = pendingMouseY;
            if (x != sentMouseX || y != sentMouseY) {
                sentMouseX = x;
                sentMouseY = y;
                InputLatency.dispatched("move", pendingMouseStamp);
                wake();
                if (view.isAvailable()) {
                    view.fireMouseEvent(0, x, y, 0);
                    InputLatency.sent("move");
                }
            }
        }
        Runnable job;
        while ((job = uiWork.poll()) != null) {
            job.run();
        }
        // After the queue, so a notch that arrived this frame is banked before any of it is paid
        // out — a scroll and the click that follows it stay in the order they were made.
        driveScroll();
        if (InputLatency.ON) {
            probeInputArrivals();
        }
        if (SCROLLLOG) {
            traceScroll();
        }
    }

    /**
     * Tracing only ({@code VOID_UI_INPUTLOG}): what the page itself saw.
     *
     * <p>The listener is installed by script rather than shipped in the bundle, so this measures
     * the page as it is, and adds nothing to a client that is not being traced. Capture phase and
     * {@code window}, so it counts an event whether or not anything in the app handles it — the
     * question here is arrival, not handling.</p>
     *
     * <p>Rate-limited hard: {@code evaluateScript} is a full JS entry and this runs inside the
     * drain, which is on the critical path of every input.</p>
     */
    private long lastArrivalProbeNanos;

    private void probeInputArrivals() {
        if (!bridgeReady || !view.isAvailable()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastArrivalProbeNanos < 500_000_000L) {
            return;
        }
        lastArrivalProbeNanos = now;
        String r = view.evaluateScript(
                "(function(){var s=window.__voidIn;if(!s){s=window.__voidIn={press:0,release:0,"
                        + "move:0,keydown:0,keyup:0,wheel:0};"
                        + "var b=function(t,k){window.addEventListener(t,function(){s[k]++;},true);};"
                        + "b('mousedown','press');b('mouseup','release');b('mousemove','move');"
                        + "b('keydown','keydown');b('keyup','keyup');b('wheel','wheel');}"
                        + "return 'press='+s.press+' release='+s.release+' move='+s.move"
                        + "+' keydown='+s.keydown+' keyup='+s.keyup+' wheel='+s.wheel;})()");
        InputLatency.page(r == null ? "?" : r.trim());
    }

    /** Scroll tracing: milliseconds since a nanoTime, to one decimal. */
    private static String ms(long since) {
        return String.valueOf(Math.round((System.nanoTime() - since) / 100_000.0) / 10.0);
    }

    /** Scroll tracing: every scroller's offset and range, so "did the page move" is a number. */
    private String scrollProbe() {
        if (!bridgeReady || !view.isAvailable()) {
            return "?";
        }
        String r = view.evaluateScript(
                "(function(){var f=function(q){var e=document.querySelector(q);return e?"
                        + "(Math.round(e.scrollTop)+'/'+Math.round(e.scrollHeight-e.clientHeight))"
                        + ":'-';};return f('.mods-grid')+'|'+f('.mprops')+'|'"
                        + "+f('.modlist__rows');})()");
        return r == null ? "?" : r.trim();
    }

    /** Scroll tracing: what the wheel will hit, and the first scrollable box above it. */
    private String hitProbe() {
        if (!bridgeReady || !view.isAvailable()) {
            return "?";
        }
        String r = view.evaluateScript(
                "(function(){var e=document.elementFromPoint(" + sentMouseX + "," + sentMouseY
                        + ");if(!e)return 'nothing under cursor';var p=e,path='';"
                        + "while(p){var st=window.getComputedStyle(p);"
                        + "var sc=(st.overflowY=='auto'||st.overflowY=='scroll')"
                        + "&&p.scrollHeight>p.clientHeight+1;"
                        + "path+=(p.className&&p.className.split?p.className.split(' ')[0]"
                        + ":p.tagName)+(sc?'<<SCROLLS>>':'')+' / ';"
                        + "if(sc)return path;p=p.parentElement;}"
                        + "return path+'NO SCROLLABLE ANCESTOR';})()");
        return r == null ? "?" : r.trim();
    }

    /**
     * Scroll tracing: what the page's scrollers did with what they were given, every frame for
     * half a second after the last wheel event. "The event never arrived" and "the event arrived
     * and moved nothing" look identical from the outside, and only this tells them apart.
     */
    private void traceScroll() {
        if (scrollTraceSettleAt == 0L) {
            return;
        }
        String now = scrollProbe();
        VoidLog.info("drive: t+" + ms(scrollTraceStart) + "ms tops " + now
                + (now.equals(scrollTraceBefore) ? " ." : ""));
        scrollTraceBefore = now;
        if (System.nanoTime() >= scrollTraceSettleAt) {
            scrollTraceSettleAt = 0L;
            VoidLog.info("drive: t+" + ms(scrollTraceStart) + "ms at rest " + now);
        }
    }

    /** Keeps the view rendering for a short window after input. UI thread only. */
    private void wake() {
        long until = System.nanoTime() + INPUT_RENDER_NANOS;
        if (until > inputUntilNanos) {
            inputUntilNanos = until;
        }
    }

    /** Queues a view call for the thread that owns the view. */
    private void post(Runnable job) {
        // Bounded so a stalled UI thread cannot grow this without limit. Input that old is not
        // worth replaying anyway — the newest click matters, a click from four seconds ago does not.
        if (uiWork.size() < 256) {
            uiWork.add(job);
        }
        startUiThread();
        // The whole point of the queue is that this thread does not wait; unparking is the other
        // half of that — it hands the work over *now* instead of leaving it in the queue until the
        // UI thread's next scheduled tick. unpark never blocks the caller, even if the UI thread is
        // mid-frame: it leaves a permit the park below consumes.
        Thread ui = uiThread;
        if (ui != null) {
            LockSupport.unpark(ui);
        }
    }


    /**
     * Whether to render every frame, regardless of what the view reports.
     *
     * <p>On for the menu, off for the HUD. The CPU renderer's dirty flag only becomes true *after*
     * a render, so gating purely on it means an idle view never wakes up — which showed as menu
     * cards appearing only once hovered. Rendering unconditionally fixes that but measured 2.4 ms
     * a frame against 0.07 ms on demand, and the HUD cannot afford it: §10 budgets 0.5 ms there and
     * 2 ms with the menu open, where frame rate is explicitly not a PVP concern.</p>
     */
    public void setContinuous(boolean continuous) {
        this.continuous = continuous;
    }

    /**
     * Forces the next few frames to paint, whatever the dirty flag says, and opens the window in
     * which the view's render target may be blanked.
     *
     * <p>Needed whenever the page's content changes at a moment the view is about to stop
     * rendering. Closing the menu is exactly that: `setContinuous(false)` lands in the same frame
     * the page unmounts the menu, and because the CPU renderer's dirty flag only goes true
     * *after* a render, nothing ever repaints — so the last painted frame, still showing the
     * menu, keeps being composited over a game that has already taken focus back. That is the
     * "the game focuses but the menu does not close" symptom: the close worked, the pixels
     * never moved.</p>
     *
     * <p><b>The clear is a window, not a one-shot, and it is spent only on a frame the page
     * itself asked to repaint.</b> Both halves of that are load-bearing and both were got wrong
     * before.</p>
     *
     * <p>{@code setNeedsPaint(true)} is a <em>request</em>, not a command: Ultralight honours it
     * only if something in the page changed, and otherwise submits no command list at all — which
     * {@code draw_command_list} rightly refuses to interpret (see {@code mod/native/README.md}).
     * So "clear the target, then force a repaint" does not hold together: on a frame where the
     * page has nothing new, the clear lands and the repaint does not, and the blanked target is
     * what gets published. Measured in game with a per-frame probe: a clear armed on the frame
     * {@code menu:false} was emitted fired 6 ms later against a page React had not committed to
     * yet ({@code dirty=false}), the whole overlay — menu, HUD and all — went blank one frame
     * after the keypress, and ~50 ms later the page's exit fade finally painted and put the menu
     * back on screen at 40% and then 12% opacity for two frames before it left for good. The
     * player sees the menu vanish and then flash. That was the close glitch.</p>
     *
     * <p>Gating on {@code isDirty()} removes it, because a dirty page is exactly a page whose
     * render <em>will</em> repaint: if it still has content the whole view is redrawn over the
     * blank target (and {@code draw_command_list} would have cleared it anyway, so the clear is
     * free), and if its content has gone the render draws nothing and the blank target is the
     * right answer. A frame that is not dirty is never blanked, which is also why the HUD no
     * longer disappears after a close in a still world — it used to be wiped by the delayed clear
     * and stay wiped until some chip's value happened to change.</p>
     *
     * <p>And a window rather than one shot, because the frame that matters is not this one. The
     * page fades the menu out over 60 ms and unmounts at the end, so the frame whose content
     * becomes nothing is ~130 ms away; a single clear would be spent on the first frame of the
     * fade and be gone by then. The window covers the whole close — {@link #CLEAR_WINDOW_NANOS}
     * outlasts {@code App.tsx}'s 400 ms {@code EXIT_FALLBACK_MS} — and costs one extra
     * {@code glClear} on the handful of dirty frames inside it, on the thread that is not the
     * game's. It publishes nothing extra: those frames present anyway.</p>
     */
    public void requestRender() {
        post(new Runnable() {
            @Override
            public void run() {
                long until = System.nanoTime() + CLEAR_WINDOW_NANOS;
                if (until > clearUntilNanos) {
                    clearUntilNanos = until;
                }
                wake();
            }
        });
    }

    /**
     * Hands the view the wheel travel that arrived this frame, whole. UI thread only.
     *
     * <p><b>Do not put an easing curve here.</b> One was tried — the delta went into a budget and
     * each UI frame handed over ~34% of what was left — on the theory that the engine applies a
     * wheel event as a single jump and needs interpolating. It does the opposite: WebCore runs its
     * own ease-out for every wheel event, and handing it a fresh one every frame <em>restarts</em>
     * that animation before it has gone anywhere. The page froze for as long as the payout lasted
     * and then jumped the accumulated total in one go.</p>
     *
     * <p>On a mouse that was ~130 ms of stall per detent. On a trackpad it was the bug reported as
     * "it jumps to the bottom": macOS delivers one flick as a decaying stream of dozens of deltas
     * over ~400 ms, they arrive far faster than a third a frame drains, and the budget integrates
     * the whole gesture. Measured on one ordinary flick, the budget climbed 14 → 486 → 1126 CSS
     * pixels while the page moved <em>zero</em> pixels for 413 ms, and then the list ran 0 → 428
     * — its entire range — in the 211 ms after the last slice.</p>
     *
     * <p>The engine's own ease is cancelled too, on the page side: {@code packages/ingame},
     * {@code menu/wheel.ts} applies the delta to the box under the pointer itself and calls
     * {@code preventDefault}. A trackpad has already done the easing and a second one only puts
     * the page behind the fingers. So nothing between the device and the scroller interprets the
     * number any more: {@code VoidMenuScreen.pollWheel} converts units to pixels and banks the
     * sub-pixel, this hands the frame's total across, and the page applies it.</p>
     *
     * <p>What is left here is coalescing and ordering. The travel of one frame goes over as one
     * event rather than five, and a scroll and the click that follows it stay in the order they
     * were made, because both pass through the same queue on the same thread.</p>
     */
    private void driveScroll() {
        int dx = scrollPendingX;
        int dy = scrollPendingY;
        if (dx == 0 && dy == 0) {
            return;
        }
        // Cleared before the fire, not after: an exception below must not leave travel banked for
        // a later frame to deliver as a jump.
        scrollPendingX = 0;
        scrollPendingY = 0;
        if (!view.isAvailable()) {
            return;
        }
        view.fireScrollEvent(dx, dy);
        // A scroll is only *visible* if the frames it moves over are painted, and the dirty flag
        // cannot ask for them: it only goes true after a render. Without this the page would move
        // and nothing would be drawn until something else woke the view.
        wake();
        if (SCROLLLOG) {
            VoidLog.info("drive: t+" + ms(scrollTraceStart) + "ms fired dy=" + dy + " (dx=" + dx
                    + ") tops@start " + scrollTraceBefore
                    + "  cursor " + sentMouseX + "," + sentMouseY);
            scrollTraceSettleAt = System.nanoTime() + 500_000_000L;
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
        // Game thread. The work this used to do now happens on the UI thread; all that is left
        // here is making sure that thread exists. Kept as a method so the call sites in
        // VoidClient and VoidMenuScreen read the same as before.
        startUiThread();
    }

    /**
     * Notices that the view is showing a page this host did not load, and puts the session back
     * onto it. UI thread only.
     *
     * <p><b>What produces one.</b> The GL driver dying mid-session. {@link UltralightWebView}
     * rebuilds the view on the CPU surface, and a replacement view is a new view — it has no
     * document, so the entry URL is loaded again. That is the correct thing for it to do and it
     * cannot do anything else; there is no way to move a loaded document between views.</p>
     *
     * <p><b>Why the host has to do this and the view cannot.</b> Everything the reload destroys
     * belongs to layers the view knows nothing about: the bridge shim, the loadout, the loadout
     * library, whether the menu is open, the DOM this class had modified for a profiling switch.
     * {@link UltralightWebView} re-applies what is genuinely its own — size, device scale, message
     * handler, focus — and reports the rest by bumping {@link WebView#documentGeneration()}. It
     * does not reach up into {@link VoidBridge} to fix it, and it should not.</p>
     *
     * <p><b>What it looked like without this.</b> The mod went on talking to a page it had lost.
     * {@code bridgeReady} was still true, so every frame evaluated {@code window.void.__hasFocus()}
     * against a document with no {@code window.void} and threw; the queued state had already been
     * drained into the dead document and nothing re-sent it, so the page stayed empty; and the
     * host kept painting that empty document at full rate — measured at 80 paints/s into a
     * 3416x1920 surface with a damage bounding box of 0%. Worst of all it was silent and
     * inescapable: {@code VoidMenuScreen} was still the current screen, so the mouse stayed
     * ungrabbed and movement stayed dead, with nothing drawn to say why. That is
     * {@code design/rendering-invariants.md} §9's "blank overlay with no visible error" reached
     * through the fallback instead of through the latch it was written about.</p>
     *
     * <p>The state itself comes from {@link VoidBridge#pushWholeState()}, which is the same method
     * the first push at start-up and the launcher's {@code init} go through — one definition of
     * what a fresh page needs, so a channel cannot be remembered in one place and forgotten in
     * another. The push only queues; the batch is delivered by the ordinary drain above, once the
     * new page reports its shim is up.</p>
     */
    private void adoptDocumentIfChanged() {
        int generation = view.documentGeneration();
        if (generation == documentGeneration) {
            return;
        }
        documentGeneration = generation;
        // Everything below belongs to the document rather than to the view, which is exactly why
        // the view could not have restored it.
        bridgeReady = false;
        nohudApplied = false;
        clickTargetStale = true;
        escapeHandledByPage = false;
        // So the first pointer position the new page is given is delivered rather than filtered
        // out as "the cursor has not moved" — the new document has never been told where it is.
        sentMouseX = Integer.MIN_VALUE;
        sentMouseY = Integer.MIN_VALUE;
        forcedRenders = FORCED_RENDERS;
        bridge.pushWholeState();
        wake();
        VoidLog.info("in-game UI: the view reloaded (document " + generation
                + "); the session's state has been re-queued for the new page");
    }

    /** The old frame(), now on the thread that owns the view. */
    private void frameOnUiThread() {
        if (!view.isAvailable()) {
            return;
        }
        double renderMs = 0;
        double jsMs = 0;
        updateMs = 0;
        boolean painted = false;
        boolean menuScript = false;
        try {
            // The shim installs window.void during page load, which finishes some frames after
            // the view is created. drainScript() empties the queue before the script runs, so
            // emitting early does not just fail — it throws away the events, and the first batch
            // is the registry and loadout that populate the mods grid. Losing it leaves the menu
            // permanently empty. So hold everything queued until the page can actually receive it.
            if (!bridgeReady) {
                String probe = view.evaluateScript("!!(window.void && window.void.__emit)");
                bridgeReady = "true".equalsIgnoreCase(probe == null ? "" : probe.trim());
                if (bridgeReady) {
                    // The page can receive state from this frame on, and the batch that carries it
                    // is drained just below. Nothing else would necessarily paint it: on the CPU
                    // surface the dirty flag only goes true *after* a render, and a HUD-only
                    // session is not rendering continuously, so a page that has just come up would
                    // sit there having been told everything and having drawn none of it.
                    forcedRenders = FORCED_RENDERS;
                    wake();
                }
            }
            if (bridgeReady) {
                String script = bridge.drainScript();
                if (script != null) {
                    // Which frame carried the menu open, for the open-to-first-paint number below.
                    menuScript = MENU_OPEN_ENVELOPE != null && script.contains(MENU_OPEN_ENVELOPE);
                    if (!PROFILE) {
                        view.evaluateScript(script);
                    } else {
                        // React runs here, not in update() or render(). Timing only render() made
                        // every slow frame look like rasterisation; it may be style and layout.
                        long js0 = System.nanoTime();
                        view.evaluateScript(script);
                        lastScriptMs = (System.nanoTime() - js0) / 1_000_000.0;
                        jsMs = lastScriptMs;
                        lastScript = script;
                    }
                }
            }
            if (NOHUD && bridgeReady && !nohudApplied) {
                // Retried until it finds the element: the bridge is ready several frames before
                // React has mounted anything, so a single attempt lands on an empty page.
                String hid = view.evaluateScript(
                        "(function(){var h=document.querySelector('.hud-layer');"
                                + "if(!h)return false;h.style.display='none';return true;})()");
                nohudApplied = "true".equalsIgnoreCase(hid == null ? "" : hid.trim());
            }
            if (SELFTEST && menuFocused) {
                long selfNow = System.currentTimeMillis();
                if (selfNow - lastSelfTestAt >= 700L) {
                    lastSelfTestAt = selfNow;
                    // Cycle through mods, and alternate what is clicked on each one: the tile
                    // body moves the selection, which repaints the outgoing tile, the incoming
                    // tile and the properties pane on the far side of the panel; the switch flips
                    // that mod. Selection and toggling were deliberately decoupled in the page, so
                    // one kind of click no longer reproduces the other's damage on its own.
                    view.evaluateScript(
                            "(function(){var t=document.querySelectorAll('.modcell,.modrow');"
                                    + "if(!t.length)return;"
                                    + "var n=window.__vt=((window.__vt||0)+1);"
                                    + "var e=t[n%Math.min(t.length,6)];"
                                    + "var s=(n%2)?e.querySelector('.modcell__select,.modrow__select')"
                                    + ":e.querySelector('.v-toggle');"
                                    + "if(s){s.click();}})()");
                }
            }
            if (CLICKTEST && menuFocused && bridgeReady && clickTargetStale
                    && System.currentTimeMillis() - lastClickTestAt >= 400L) {
                // Not on the frame the menu opens. getBoundingClientRect() forces a synchronous
                // layout, and a probe that makes the frame it is measuring more expensive is not
                // measuring that frame.
                // Only the position is worked out here, because only this thread may ask the page
                // a question. The click itself is fired by the game thread, from the same call
                // VoidMenuScreen.mouseClicked makes, so the measurement covers the whole path.
                String at = view.evaluateScript(
                        "(function(){var t=document.querySelectorAll('.modcell .v-toggle,"
                                + ".modrow .v-toggle');if(!t.length)return '';"
                                + "var n=window.__vc=((window.__vc||0)+1);"
                                + "var r=t[n%Math.min(t.length,6)].getBoundingClientRect();"
                                + "return Math.round(r.left+r.width/2)+','"
                                + "+Math.round(r.top+r.height/2);})()");
                int comma = at == null ? -1 : at.indexOf(',');
                if (comma > 0) {
                    try {
                        clickTargetX = Integer.parseInt(at.substring(0, comma).trim());
                        clickTargetY = Integer.parseInt(at.substring(comma + 1).trim());
                        clickTargetStale = false;
                    } catch (NumberFormatException ignored) {
                        // The grid is not up yet; try again next frame.
                    }
                }
            }
            long update0 = PROFILE ? System.nanoTime() : 0L;
            view.update();
            // Here and nowhere else, because update() is the only call that can change the
            // document: it is where a dead GL driver is noticed and the view is rebuilt, and a
            // replacement view reloads the page. Checking it on the next frame instead would be
            // one frame too late — everything below this line that touches JavaScript would run
            // against a document that has not installed the shim yet, which is where the
            // `undefined is not an object (evaluating 'window.void.__hasFocus')` came from.
            adoptDocumentIfChanged();
            // refreshDisplay() is what advances CSS animations and transitions;
            // it must run every frame, before render(), or the UI is static
            // (CONTRACTS.md, "Rules the mod must follow", rule 3).
            view.refreshDisplay();
            if (PROFILE) {
                // Timed because it is not free and it is not the render: update() is where style
                // recalc and layout happen, so a frame that changed the shape of the page pays
                // here and a frame that only changed its colours does not. Attributing all of it
                // to render() is how a layout cost gets mistaken for a rasterisation cost.
                updateMs = (System.nanoTime() - update0) / 1_000_000.0;
            }
            // The binding asks us to skip render() on frames where nothing
            // changed — the first lever against the paint budget of §10. The
            // forced frames after a create or a resize cover the window where
            // the dirty flag has not caught up with the new size yet.
            // Read once: the branch below and the clear after the render must agree about *why*
            // this frame is painting, and the flag can be changed by the render itself.
            boolean pageDirty = view.isDirty();
            // The clear is spent here and only here, on a frame the *page* asked to repaint. See
            // {@link #requestRender} for why nothing else is safe to spend it on.
            boolean clearNow = pageDirty && System.nanoTime() < clearUntilNanos;
            boolean willRender = (continuous && view.rendersEveryFrame()) || pageDirty
                    || forcedRenders > 0 || System.nanoTime() < inputUntilNanos;
            if (willRender) {
                if (forcedRenders > 0) {
                    forcedRenders--;
                }
                // Repaint the whole view, not just the regions Ultralight damaged. Its incremental
                // path leaves the changed region uncleared, and glyphs are composited premultiplied
                // over whatever is already there, so anything that updates — the fps readout, the
                // CPS counter — piles successive frames on top of each other until it is unreadable.
                // Config-level ForceRepaint fixes it too but marks the view dirty every frame, which
                // measured 22 ms against 0.5 ms; this pays the full repaint only on the frames that
                // actually changed.
                if (clearNow) {
                    // Before setNeedsPaint and the render, so this frame draws onto an empty
                    // target. Whatever the page still has is redrawn over it; whatever it no
                    // longer has is gone, including the case where that is everything.
                    view.clearTarget();
                }
                if (view.needsFullRepaintEachFrame()) {
                    view.setNeedsPaint();
                }
                painted = true;
                if (!PROFILE) {
                    view.render();
                } else {
                    long t0 = System.nanoTime();
                    view.render();
                    double ms = (System.nanoTime() - t0) / 1_000_000.0;
                    renderMs = ms;
                    if (ms > 10.0 || lastScriptMs > 10.0) {
                        String s = lastScript == null ? "(no push this frame)" : lastScript;
                        VoidLog.info("stall: js " + Math.round(lastScriptMs) + "ms + render "
                                + Math.round(ms) + "ms; push["
                                + (lastScript == null ? 0 : s.length()) + "]: "
                                + s.substring(0, Math.min(150, s.length())).replace('\n', ' '));
                        lastScript = null;
                    }
                    lastScriptMs = 0;
                }
                if (!pageDirty && view.needsFullRepaintEachFrame()) {
                    // Put back the flag *we* set. On the accelerated path setNeedsPaint above is
                    // not a report that the page changed — it is how a render is made to repaint
                    // the whole view — and Ultralight only clears it as part of a paint that drew
                    // something. So a forced render of a page with nothing new left our own flag
                    // standing, the next frame read it back as "dirty", set it again, and the view
                    // rendered every frame for the rest of the process: measured with the menu
                    // open and the cursor still at 50 paints/s against 0-2 presents/s.
                    //
                    // Only in the !pageDirty case. A flag Ultralight raised is never cleared here:
                    // if it wanted a paint and did not get one this frame, it must still be
                    // outstanding on the next, or the first paint of a page that is still coming
                    // up would be swallowed — the "cards only appear once hovered" failure.
                    view.clearNeedsPaint();
                }
            }
            // Same reason, for the blit. An accelerated view's texture id and uv extent are
            // render-target reads, so they belong to this thread; the game thread gets whatever
            // this frame published. This is also where the finished frame is copied into the
            // texture the game thread will sample — glTextureId() does that copy, on the frames
            // that painted, and hands back the completed side of the double buffer. Read every
            // frame rather than only after a render, because a resize replaces the render target
            // without anything being painted into it yet.
            if (view.texturePublishedByUiThread()) {
                publishedTexture = view.glTextureId();
                publishedUvX = view.uvScaleX();
                publishedUvY = view.uvScaleY();
            }
            if (painted) {
                // Last, and after the publish it describes: the game thread reads the counter to
                // decide whether the texture it is holding already carries a given change, so it
                // must never see a bumped counter for pixels that are not out yet.
                publishSeq++;
                noteUiPaint(menuScript, jsMs, renderMs);
            }
            // Asked here, once a frame, so the game thread never has to. keepsEscape() is a
            // synchronous JS call and JS belongs to this thread; letting the render thread ask
            // directly would make it wait on this one, which is the deadlock this whole split
            // exists to avoid. One frame of staleness on an Escape press is not perceptible.
            //
            // `__keepsEscape` where the page defines it, `__hasFocus` where it does not. The
            // question Java has ever actually asked here is "will the page deal with Escape
            // itself" — a focused text field was simply the only reason there had ever been, and
            // `__hasFocus` was that reason answering for the question. It is no longer the only
            // one: Escape now means *up one level*, so a properties page consumes it to go back
            // to the grid and only the grid lets it close the menu. `__hasFocus` keeps its exact
            // documented meaning and the fallback keeps an older bundle working; one eval either
            // way, so the frame costs what it always did.
            if (bridgeReady) {
                String keeps = view.evaluateScript(
                        "(function(){var v=window['void'];"
                        + "return String(!!v&&(v.__keepsEscape?v.__keepsEscape():v.__hasFocus()));"
                        + "})()");
                escapeHandledByPage = "true".equalsIgnoreCase(keeps == null ? "" : keeps.trim());
            }
            countUiFrame(renderMs);
        } catch (RuntimeException e) {
            VoidLog.error("in-game UI frame failed, disabling", e);
            disable();
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
        WebView v = view;
        if (!v.isAvailable()) {
            return;
        }
        int texture;
        float uvX;
        float uvY;
        if (v.texturePublishedByUiThread()) {
            // The accelerated view's frame, finished by the UI thread on its own context and copied
            // out of Ultralight's render target into one of two textures the binding owns. Sampling
            // the render target itself instead was tried and flickers continuously: this thread
            // blits faster than that one paints, so it lands inside a half-replayed command list
            // most frames. Nothing is waited on here either way — the copy happens over there.
            texture = publishedTexture;
            uvX = publishedUvX;
            uvY = publishedUvY;
        } else {
            // The CPU view's surface is uploaded by this call, on this thread, into Minecraft's
            // context — which is why it is the one engine call the game thread is allowed to make.
            texture = v.glTextureId();
            uvX = v.uvScaleX();
            uvY = v.uvScaleY();
        }
        if (texture == 0) {
            return;
        }
        if (PROFILE) {
            noteBlit();
        }
        GlBlit.begin2d(screenWidth, screenHeight);
        try {
            // flipV = false: the GPU driver already renders with ulApplyProjection's flip_y, so
            // glTextureId() hands back a texture whose v = 0 is the top. Flipping again here
            // would stand the whole UI on its head.
            GlBlit.drawTexture(texture, 0, 0, screenWidth, screenHeight,
                    PREMULTIPLIED, false, 1f, uvX, uvY);
        } finally {
            GlBlit.end2d();
        }
    }

    // -- frame rates -------------------------------------------------------
    //
    // The native profiler counts game frames inside viewTextureId, which only the CPU path calls
    // from the game thread; on the accelerated path that call moves to the UI thread and the count
    // would silently vanish along with it. The two rates the choice between the renderers actually
    // turns on are therefore measured here, on the thread each of them belongs to, and reported
    // separately so nothing has to be shared between the threads to print them.

    private long gameWindowNanos;
    private long lastGameFrameNanos;
    private int gameFrames;
    private int gameFramesOver20ms;
    private double worstGameFrameMs;

    private long uiWindowNanos;
    private int uiFrames;
    private int uiRenders;
    private double uiRenderMs;
    private double worstUiRenderMs;

    /**
     * Game thread, once per game frame.
     *
     * <p>Called from {@code VoidClient.onRenderOverlay}, not from {@link #paint}: paint returns
     * early for an unavailable view and is skipped entirely while the menu screen is up, so
     * counting there measured the game only in the configurations where the UI was drawing. The
     * question this number exists to answer — how much of a frame is the game's and how much is
     * ours — needs the same counter running in the configuration where the UI draws nothing.</p>
     */
    public void countGameFrame() {
        if (!PROFILE) {
            return;
        }
        long now = System.nanoTime();
        gameFrameSeq++;
        if (lastGameFrameNanos != 0) {
            double ms = (now - lastGameFrameNanos) / 1_000_000.0;
            if (ms > worstGameFrameMs) {
                worstGameFrameMs = ms;
            }
            if (ms > 20.0) {
                gameFramesOver20ms++;
            }
        }
        lastGameFrameNanos = now;
        gameFrames++;
        if (gameWindowNanos == 0) {
            gameWindowNanos = now;
            return;
        }
        double secs = (now - gameWindowNanos) / 1_000_000_000.0;
        if (secs < 2.0) {
            return;
        }
        VoidLog.info(String.format("game: %.0f fps, worst frame %.0f ms, %d of %d over 20 ms (%s)",
                gameFrames / secs, worstGameFrameMs, gameFramesOver20ms, gameFrames,
                accelerated() ? "gpu" : "cpu"));
        gameWindowNanos = now;
        gameFrames = 0;
        gameFramesOver20ms = 0;
        worstGameFrameMs = 0;
    }

    /** UI thread, once per loop iteration; {@code renderMs} is 0 on a frame that did not paint. */
    private void countUiFrame(double renderMs) {
        if (!PROFILE) {
            return;
        }
        uiFrames++;
        if (renderMs > 0) {
            uiRenders++;
            uiRenderMs += renderMs;
            if (renderMs > worstUiRenderMs) {
                worstUiRenderMs = renderMs;
            }
        }
        long now = System.nanoTime();
        if (uiWindowNanos == 0) {
            uiWindowNanos = now;
            return;
        }
        double secs = (now - uiWindowNanos) / 1_000_000_000.0;
        if (secs < 2.0) {
            return;
        }
        VoidLog.info(String.format(
                "ui: %.0f frames/s, %.0f paints/s, render %.2f ms mean, %.1f ms peak (%s)",
                uiFrames / secs, uiRenders / secs,
                uiRenders == 0 ? 0.0 : uiRenderMs / uiRenders, worstUiRenderMs,
                // Which configuration the window measured. Two lines from the same run mean two
                // different things depending on this, and reading them as one number was how the
                // HUD's cost and the menu's got mixed up in the first place.
                menuFocused ? "menu" : "hud"));
        uiWindowNanos = now;
        uiFrames = 0;
        uiRenders = 0;
        uiRenderMs = 0;
        worstUiRenderMs = 0;
    }

    // -- interaction latency ----------------------------------------------
    //
    // Two numbers, both PROFILE-only, both measured end to end rather than inside one thread:
    //
    //   menu open  — from the game thread deciding to open to the UI thread finishing the paint
    //                that first has the menu in it. That is the hitch a player sees on Right Shift.
    //   click      — from the game thread posting the click to the game thread blitting a texture
    //                that contains its result, counted in game frames as well as milliseconds,
    //                because frames are what the eye gets.
    //
    // Nothing here is on the blit's critical path: the game thread reads two volatiles it would
    // have read anyway and compares two longs.

    /** The envelope {@code VoidBridge.emit} writes for {@code menu:true}; Gson emits no spaces. */
    private static final String MENU_OPEN_ENVELOPE = "\"e\":\"menu\",\"payload\":true";

    private volatile long menuOpenNanos;
    private volatile boolean menuOpenPending;

    /** Game-thread frame counter, so a click's cost can be reported in frames. */
    private long gameFrameSeq;
    private volatile long clickNanos;
    private volatile long clickGameFrame;
    /** Set by the UI thread when it has actually delivered the click to the view. */
    private volatile boolean clickInFlight;
    /** The publish this click's paint produced; 0 when there is nothing outstanding. */
    private volatile long clickPublishSeq;
    private volatile double clickUiMs;
    private volatile double clickRenderMs;
    /** UI thread: how long {@code update()} + {@code refreshDisplay()} took this frame. */
    private double updateMs;

    /** Click self-test: where to click next, decided on the UI thread, used by the game thread. */
    private volatile int clickTargetX;
    private volatile int clickTargetY;
    private volatile boolean clickTargetStale = true;
    /** Volatile: the game thread stamps it, the UI thread reads it to stay off the open frame. */
    private volatile long lastClickTestAt;

    /** Game thread: the menu is about to be opened. Called before the event is emitted. */
    public void noteMenuOpening() {
        if (!PROFILE) {
            return;
        }
        menuOpenNanos = System.nanoTime();
        menuOpenPending = true;
    }

    /** UI thread, at the end of a frame that painted. */
    private void noteUiPaint(boolean menuScript, double jsMs, double renderMs) {
        if (!PROFILE) {
            return;
        }
        if (menuOpenPending && menuScript) {
            menuOpenPending = false;
            double ms = (System.nanoTime() - menuOpenNanos) / 1_000_000.0;
            VoidLog.info(String.format(
                    "menu open: %.1f ms to first paint (js %.1f ms, layout %.1f ms, render %.1f ms)",
                    ms, jsMs, updateMs, renderMs));
        }
        if (clickInFlight) {
            clickInFlight = false;
            clickUiMs = (System.nanoTime() - clickNanos) / 1_000_000.0;
            clickRenderMs = renderMs;
            // Last: the game thread treats a non-zero value as "the two above are readable".
            clickPublishSeq = publishSeq;
        }
    }

    /** Game thread, from {@link #paint}: has the frame carrying the last click reached the screen? */
    private void noteBlit() {
        long want = clickPublishSeq;
        if (want == 0 || publishSeq < want) {
            return;
        }
        clickPublishSeq = 0;
        VoidLog.info(String.format(
                "click: %d game frames, %.1f ms to pixel (post->paint %.1f ms, render %.2f ms)",
                gameFrameSeq - clickGameFrame, (System.nanoTime() - clickNanos) / 1_000_000.0,
                clickUiMs, clickRenderMs));
    }

    /**
     * Game thread, once per menu frame: fire a real click at a real control on a timer.
     *
     * <p>Called from {@code VoidMenuScreen.render}, so it enters {@link #mouseDown} exactly where
     * {@code mouseClicked} does. Down and up go in the same frame — a human holds the button for
     * several, but the pixel a click produces lands on the release, so the release is where the
     * clock that matters starts.</p>
     */
    public void driveClickTest() {
        if (!CLICKTEST || clickTargetStale) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClickTestAt < 700L) {
            return;
        }
        lastClickTestAt = now;
        int x = clickTargetX;
        int y = clickTargetY;
        clickTargetStale = true;
        mouseDown(x, y, 0);
        mouseUp(x, y, 0);
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

    /**
     * Will the page deal with Escape itself?
     *
     * <p>True when a text field has the keyboard, when the quick palette is up, or when the
     * overlay is somewhere Escape means "up one level" rather than "close" — see
     * {@code keepsEscape()} in `packages/ingame/src/bridge/connect.ts`, which is where the answer
     * is actually decided. False on the grid, and false whenever the bridge is not ready, so a
     * page that has broken can never trap the player inside the menu.</p>
     */
    public boolean keepsEscape() {
        // The answer the UI thread cached on its last frame. Asking the view directly from here
        // would be a renderer call from the wrong thread, and waiting for the UI thread to answer
        // would be the game thread blocking on it.
        return escapeHandledByPage;
    }

    // -- input forwarding (menu mode only, §6.3) -------------------------

    // Every one of these is a view call, so every one crosses to the thread that owns the view.
    // The cost is at most one UI frame of latency — against the whole-frame stall that firing them
    // inline used to add to the game, which is the trade this split is making.

    public void mouseMoved(int x, int y) {
        // Coalesced, not queued: this arrives once per game frame and only the newest position
        // means anything. Replaying a trail of stale positions would be worse than skipping them.
        pendingMouseX = x;
        pendingMouseY = y;
        pendingMouseStamp = InputLatency.stamp();
        pendingMouseMove = true;
        startUiThread();
    }

    public void mouseDown(final int x, final int y, final int button) {
        if (PROFILE) {
            clickNanos = System.nanoTime();
            clickGameFrame = gameFrameSeq;
        }
        final long at = InputLatency.stamp();
        post(new Runnable() {
            @Override
            public void run() {
                InputLatency.dispatched("press", at);
                if (PROFILE) {
                    // Set here, not at the call site: this runs inside drainWork, which is ahead
                    // of the render in the same UI iteration, so the flag can only be read by a
                    // frame that really did deliver the click.
                    clickInFlight = true;
                }
                if (view.isAvailable()) {
                    // Ordering matters: a click has to land at the position the cursor was at when
                    // it happened, not wherever the coalesced move left things. Unconditional:
                    // the move filter above is about idling, and a click is not idle.
                    sentMouseX = x;
                    sentMouseY = y;
                    view.fireMouseEvent(0, x, y, 0);
                    view.fireMouseEvent(1, x, y, mouseButton(button));
                    InputLatency.sent("press");
                    wake();
                }
            }
        });
    }

    public void mouseUp(final int x, final int y, final int button) {
        final long at = InputLatency.stamp();
        post(new Runnable() {
            @Override
            public void run() {
                InputLatency.dispatched("release", at);
                if (view.isAvailable()) {
                    view.fireMouseEvent(2, x, y, mouseButton(button));
                    InputLatency.sent("release");
                    wake();
                }
            }
        });
    }

    /**
     * A wheel delta, in CSS pixels, from the game thread's input pump.
     *
     * <p>Added to this frame's total rather than fired here, because this is not the thread that
     * owns the view. {@link #driveScroll} hands the total over at the end of the same frame's
     * drain — not a later one; see there for why that distinction is the whole bug.</p>
     */
    public void scroll(final int dx, final int dy) {
        final long at = SCROLLLOG ? System.nanoTime() : 0L;
        final long inAt = InputLatency.stamp();
        post(new Runnable() {
            @Override
            public void run() {
                InputLatency.dispatched("wheel", inAt);
                InputLatency.sent("wheel");
                if (SCROLLLOG) {
                    if (scrollTraceSettleAt == 0L && scrollPendingY == 0) {
                        scrollTraceStart = at;
                    }
                    VoidLog.info("scroll: +" + dy + " (frame total " + (scrollPendingY + dy) + ")"
                            + (scrollPendingY == 0 ? ", under " + hitProbe() : ""));
                }
                scrollPendingX += dx;
                scrollPendingY += dy;
            }
        });
    }

    public void keyDown(final int virtualKey, final int modifiers) {
        final long at = InputLatency.stamp();
        post(new Runnable() {
            @Override
            public void run() {
                InputLatency.dispatched("keydown", at);
                if (view.isAvailable()) {
                    view.fireKeyEvent(0, virtualKey, modifiers, "");
                    InputLatency.sent("keydown");
                    wake();
                }
            }
        });
    }

    public void keyUp(final int virtualKey, final int modifiers) {
        final long at = InputLatency.stamp();
        post(new Runnable() {
            @Override
            public void run() {
                InputLatency.dispatched("keyup", at);
                if (view.isAvailable()) {
                    view.fireKeyEvent(1, virtualKey, modifiers, "");
                    InputLatency.sent("keyup");
                    wake();
                }
            }
        });
    }

    public void keyChar(final String text, final int modifiers) {
        if (text == null || text.isEmpty()) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    view.fireKeyEvent(2, 0, modifiers, text);
                    wake();
                }
            }
        });
    }

    public void setFocus(final boolean focused) {
        // Also the only signal this class gets that the menu is open or shut: focus is given when
        // VoidMenuScreen initialises and taken when it is removed. The self-test harness used to
        // gate on setContinuous instead, which stopped being true for the menu when rendering
        // became demand-driven, and it has been silently doing nothing since.
        menuFocused = focused;
        if (CLICKTEST) {
            // Start the click clock at the open, so the first click of a session does not land in
            // the same UI frame as the open's own paint and take that frame's cost with it.
            lastClickTestAt = System.currentTimeMillis();
            clickTargetStale = true;
        }
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    view.setFocus(focused);
                }
            }
        });
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
        publishedTexture = 0;
        try {
            old.close();
        } catch (RuntimeException ignored) {
            // Already broken; nothing useful to do with a second failure.
        }
    }

    /**
     * Called when the game is going away.
     *
     * <p>Deliberately does not close the view. Closing is a renderer call and belongs to the UI
     * thread, and that thread is not allowed to finish — a thread that has touched WebCore aborts
     * the process on the way out (mod/native README, known risks). The process is about to leave
     * through {@code System.exit} regardless, which is the exit path the binding is built around,
     * so the honest thing is to stop drawing and let it go.</p>
     */
    public void shutdown() {
        view = new NullWebView();
        publishedTexture = 0;
    }
}
