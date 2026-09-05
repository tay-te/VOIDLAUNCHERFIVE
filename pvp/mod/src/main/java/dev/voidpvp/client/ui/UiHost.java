package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.bridge.VoidBridge;
import dev.voidpvp.client.render.GlBlit;

import java.util.concurrent.ConcurrentLinkedQueue;
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
     */
    private static final double TARGET_DENSITY = 2.0;
    private static final int MAX_SUPERSAMPLE = 2;

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
    private Thread uiThread;
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
    /** What the game thread wants; the UI thread applies it when it next comes round. */
    private volatile int wantFbWidth;
    private volatile int wantFbHeight;
    private volatile double wantScale = 1;
    /** Cached on the UI thread so the game thread never has to ask the view a question. */
    private volatile boolean focusedInput;

    // Written by the UI thread, read by the game thread every frame (VoidMenuScreen sizes the GL
    // shadow pass from logicalWidth).
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private volatile int framebufferWidth;
    private volatile int framebufferHeight;
    private volatile double deviceScale = 1;
    /** Whether the page has finished loading far enough to have installed {@code window.void}. */
    private boolean bridgeReady;
    /** While true, render every frame rather than only when the view reports itself dirty. */
    private volatile boolean continuous;
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
        int lw = Math.max(1, (int) Math.ceil(fbWidth / s));
        int lh = Math.max(1, (int) Math.ceil(fbHeight / s));
        // An integer factor only: a fractional one puts glyphs on a non-integer grid relative to
        // the screen and trades one kind of shimmer for another.
        int ss = Math.max(1, Math.min(MAX_SUPERSAMPLE, (int) Math.ceil(TARGET_DENSITY / s)));
        int vw = Math.max(1, fbWidth * ss);
        int vh = Math.max(1, fbHeight * ss);
        // The page still lays out in fb / s CSS pixels; only the rasterisation gets denser.
        double viewScale = s * ss;

        if (!started) {
            started = true;
            logicalWidth = lw;
            logicalHeight = lh;
            framebufferWidth = fbWidth;
            framebufferHeight = fbHeight;
            deviceScale = s;
            // Ultralight sizes a view in DEVICE PIXELS; the CSS viewport it lays out in is that
            // size divided by the device scale. So the framebuffer goes in as-is and `s` does the
            // dividing — passing the already-divided logical size instead would land the page on
            // fb / s^2, i.e. everything drawn `s` times too large.
            WebView created = WebViews.create(vw, vh);
            if (!created.isAvailable()) {
                view = created;
                return;
            }
            created.setDeviceScale(viewScale);
            created.setMessageHandler(new Function<String, String>() {
                @Override
                public String apply(String request) {
                    // window.__void_native(json): JS to Java. This now arrives on the UI thread,
                    // not the render thread — VoidBridge marshals anything that has to touch the
                    // game back to the game thread.
                    return bridge.dispatch(request);
                }
            });
            created.loadUrl(ENTRY_URL);
            forcedRenders = FORCED_RENDERS;
            view = created;
            VoidLog.info("in-game UI started at " + lw + "x" + lh + " (scale " + s
                    + ", rasterised " + vw + "x" + vh + " at " + viewScale + " dp/css)");
            return;
        }
        if (!view.isAvailable()) {
            return;
        }
        boolean sizeChanged = lw != logicalWidth || lh != logicalHeight;
        boolean scaleChanged = s != deviceScale;
        // Scale first, then size. The render target is allocated from logical size x device
        // scale, so resizing while the old scale is still set sizes the texture for the wrong
        // number of pixels; the view then draws into a target that does not match it and the
        // blit samples the wrong fraction of it. A scale change therefore has to re-issue the
        // resize as well, even when the logical size is unchanged.
        if (scaleChanged) {
            deviceScale = s;
            view.setDeviceScale(viewScale);
        }
        if (sizeChanged || scaleChanged) {
            logicalWidth = lw;
            logicalHeight = lh;
            framebufferWidth = fbWidth;
            framebufferHeight = fbHeight;
            view.resize(vw, vh);
            // The render target now holds pixels drawn at the old size. Ultralight repaints only
            // what it believes changed, so without this the untouched areas keep showing them —
            // which is the ghosted, oversized text that survived every resize.
            view.setNeedsPaint();
            forcedRenders = FORCED_RENDERS;
        }
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
     */
    private void uiLoop() {
        final long periodNanos = 1_000_000_000L / 60L;
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
            long spent = System.nanoTime() - began;
            long remaining = periodNanos - spent;
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } catch (InterruptedException e) {
                    // Nothing interrupts this thread on purpose, and it must not exit; carry on.
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Runs everything the game thread queued. UI thread only. */
    private void drainWork() {
        if (pendingMouseMove) {
            pendingMouseMove = false;
            int x = pendingMouseX;
            int y = pendingMouseY;
            if (view.isAvailable()) {
                view.fireMouseEvent(0, x, y, 0);
            }
        }
        Runnable job;
        while ((job = uiWork.poll()) != null) {
            job.run();
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

    /** The old frame(), now on the thread that owns the view. */
    private void frameOnUiThread() {
        if (!view.isAvailable()) {
            return;
        }
        try {
            // The shim installs window.void during page load, which finishes some frames after
            // the view is created. drainScript() empties the queue before the script runs, so
            // emitting early does not just fail — it throws away the events, and the first batch
            // is the registry and loadout that populate the mods grid. Losing it leaves the menu
            // permanently empty. So hold everything queued until the page can actually receive it.
            if (!bridgeReady) {
                String probe = view.evaluateScript("!!(window.void && window.void.__emit)");
                bridgeReady = "true".equalsIgnoreCase(probe == null ? "" : probe.trim());
            }
            if (bridgeReady) {
                String script = bridge.drainScript();
                if (script != null) {
                    if (!PROFILE) {
                        view.evaluateScript(script);
                    } else {
                        // React runs here, not in update() or render(). Timing only render() made
                        // every slow frame look like rasterisation; it may be style and layout.
                        long js0 = System.nanoTime();
                        view.evaluateScript(script);
                        lastScriptMs = (System.nanoTime() - js0) / 1_000_000.0;
                        lastScript = script;
                    }
                }
            }
            if (SELFTEST && continuous) {
                long selfNow = System.currentTimeMillis();
                if (selfNow - lastSelfTestAt >= 700L) {
                    lastSelfTestAt = selfNow;
                    // Cycle through tiles rather than hammering one: clicking an unselected tile
                    // both selects it and toggles it, so the settings pane on the far side of the
                    // panel changes in the same frame. That union is the expensive case, and a
                    // single tile never reproduces it.
                    view.evaluateScript(
                            "(function(){var t=document.querySelectorAll('.v-modtile');"
                                    + "if(!t.length)return;"
                                    + "window.__vt=((window.__vt||0)+1)%Math.min(t.length,6);"
                                    + "var s=t[window.__vt].querySelector('.v-toggle');"
                                    + "if(s){s.click();}})()");
                }
            }
            view.update();
            // refreshDisplay() is what advances CSS animations and transitions;
            // it must run every frame, before render(), or the UI is static
            // (CONTRACTS.md, "Rules the mod must follow", rule 3).
            view.refreshDisplay();
            // The binding asks us to skip render() on frames where nothing
            // changed — the first lever against the paint budget of §10. The
            // forced frames after a create or a resize cover the window where
            // the dirty flag has not caught up with the new size yet.
            if ((continuous && view.rendersEveryFrame()) || view.isDirty() || forcedRenders > 0) {
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
                if (view.needsFullRepaintEachFrame()) {
                    view.setNeedsPaint();
                }
                if (!PROFILE) {
                    view.render();
                } else {
                    long t0 = System.nanoTime();
                    view.render();
                    double ms = (System.nanoTime() - t0) / 1_000_000.0;
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
            }
            // Asked here, once a frame, so the game thread never has to. hasFocusedInput() is a
            // synchronous JS call and JS belongs to this thread; letting the render thread ask
            // directly would make it wait on this one, which is the deadlock this whole split
            // exists to avoid. One frame of staleness on an Escape press is not perceptible.
            if (bridgeReady) {
                String focus = view.evaluateScript("window.void.__hasFocus()");
                focusedInput = "true".equalsIgnoreCase(focus == null ? "" : focus.trim());
            }
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
        if (!view.isAvailable()) {
            return;
        }
        int texture = view.glTextureId();
        if (texture == 0) {
            return;
        }
        GlBlit.begin2d(screenWidth, screenHeight);
        try {
            // flipV = false: the GPU driver already renders with ulApplyProjection's flip_y, so
            // glTextureId() hands back a texture whose v = 0 is the top. Flipping again here
            // would stand the whole UI on its head.
            GlBlit.drawTexture(texture, 0, 0, screenWidth, screenHeight,
                    PREMULTIPLIED, false, 1f, view.uvScaleX(), view.uvScaleY());
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
        // The answer the UI thread cached on its last frame. Asking the view directly from here
        // would be a renderer call from the wrong thread, and waiting for the UI thread to answer
        // would be the game thread blocking on it.
        return focusedInput;
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
        pendingMouseMove = true;
        startUiThread();
    }

    public void mouseDown(final int x, final int y, final int button) {
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    // Ordering matters: a click has to land at the position the cursor was at when
                    // it happened, not wherever the coalesced move left things.
                    view.fireMouseEvent(0, x, y, 0);
                    view.fireMouseEvent(1, x, y, mouseButton(button));
                }
            }
        });
    }

    public void mouseUp(final int x, final int y, final int button) {
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    view.fireMouseEvent(2, x, y, mouseButton(button));
                }
            }
        });
    }

    public void scroll(final int dx, final int dy) {
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    view.fireScrollEvent(dx, dy);
                }
            }
        });
    }

    public void keyDown(final int virtualKey, final int modifiers) {
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    view.fireKeyEvent(0, virtualKey, modifiers, "");
                }
            }
        });
    }

    public void keyUp(final int virtualKey, final int modifiers) {
        post(new Runnable() {
            @Override
            public void run() {
                if (view.isAvailable()) {
                    view.fireKeyEvent(1, virtualKey, modifiers, "");
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
                }
            }
        });
    }

    public void setFocus(final boolean focused) {
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
    }
}
