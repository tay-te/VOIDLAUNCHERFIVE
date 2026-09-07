package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.SharedDrawable;

/**
 * A second OpenGL context for the {@code void-ui} thread, in the same share group as Minecraft's.
 *
 * <p><b>Why this has to exist.</b> The accelerated renderer runs Ultralight's paint through our own
 * {@code ULGPUDriver} ({@code mod/native/src/gpu_driver_gl.cpp}), and that driver is called from
 * inside {@code ulRender} — on whichever thread called it. Since the engine moved to its own
 * thread, that is the {@code void-ui} thread, which has no GL context, and the driver's first act
 * is {@code glGetString}: a segfault, immediately, every time. Marshalling the paint back to the
 * render thread would put the whole cost of the UI back on the game frame, which is the thing the
 * split exists to prevent. So the UI thread gets a context of its own instead.</p>
 *
 * <p><b>Why a shared one.</b> Textures are shared objects in GL: two contexts in one share group
 * see the same texture names and the same texels. The UI thread paints into an FBO on its context,
 * the game thread binds the resulting texture on Minecraft's, and nothing is copied between them.
 * Containers — FBOs and VAOs — are <em>not</em> shared, and nothing needs them to be: every one the
 * driver creates is created and used on the UI thread.</p>
 *
 * <p><b>The mechanism.</b> Minecraft 1.8.9 is LWJGL 2, where {@code SharedDrawable} is the API for
 * this ({@code 2.9.4+legacyfabric.15} on this classpath, checked). Its constructor calls
 * {@code createSharedContext()} on the display's drawable, which builds a context against the same
 * peer info and pixel format with Minecraft's as the share source; {@code makeCurrent()} then binds
 * it to the calling thread. On macOS that {@code makeCurrent} also re-issues the platform's
 * {@code setView} against the game's window — which sounds alarming and is not: LWJGL 2 there
 * already runs its GL from "Client thread" with AppKit on a thread of its own, so this is the same
 * call from the same kind of thread the game itself makes it from.</p>
 *
 * <p><b>Order.</b> {@link #prepare()} runs on the game thread, where Minecraft's context is
 * current, because the shared context is created <em>from</em> it. {@link #makeCurrent()} runs once
 * on the UI thread before any Ultralight GL work. Neither is ever called again, and the context is
 * never released: the UI thread parks for the life of the process and the drawable dies with it.</p>
 *
 * <p><b>Failure is not fatal.</b> Anything that goes wrong here logs once and answers {@code
 * false}, and the host then creates a CPU view. The accelerated renderer never runs without a
 * context — that combination is the crash this class exists to remove.</p>
 */
public final class UiGlContext {

    private static final int UNTRIED = 0;
    private static final int READY = 1;
    private static final int FAILED = 2;

    /** Game thread writes it in {@link #prepare()}, UI thread reads it in {@link #makeCurrent()}. */
    private static volatile int state = UNTRIED;
    private static volatile SharedDrawable shared;
    private static volatile boolean current;

    private UiGlContext() {
    }

    /**
     * Builds the shared context. <b>Game thread only</b>, with Minecraft's context current.
     *
     * <p>Idempotent and cheap after the first call, so the per-frame call site does not have to
     * remember whether it has already asked.</p>
     *
     * @return whether a context now exists for the UI thread to adopt
     */
    public static boolean prepare() {
        int seen = state;
        if (seen != UNTRIED) {
            return seen == READY;
        }
        try {
            Drawable drawable = Display.getDrawable();
            if (drawable == null) {
                fail("Minecraft's display has no drawable yet");
                return false;
            }
            shared = new SharedDrawable(drawable);
            state = READY;
            VoidLog.info("in-game UI: created a GL context sharing Minecraft's");
            return true;
        } catch (Throwable t) {
            // Throwable, not LWJGLException: a LinkageError here means this build of LWJGL has no
            // SharedDrawable at all, and that has to degrade to the CPU renderer like anything
            // else rather than take the game down.
            fail("could not share Minecraft's GL context: " + t);
            return false;
        }
    }

    /**
     * Makes the shared context current on the calling thread. <b>UI thread only</b>, once.
     *
     * @return whether this thread now has a context and the accelerated renderer may run
     */
    public static boolean makeCurrent() {
        if (current) {
            return true;
        }
        if (state != READY) {
            return false;
        }
        try {
            shared.makeCurrent();
            current = true;
            VoidLog.info("in-game UI: GL context current on the "
                    + Thread.currentThread().getName() + " thread");
            return true;
        } catch (Throwable t) {
            fail("could not make the shared GL context current: " + t);
            return false;
        }
    }

    /** Whether the calling process has a UI-thread context; false means the CPU path. */
    public static boolean isCurrent() {
        return current;
    }

    private static void fail(String message) {
        state = FAILED;
        shared = null;
        VoidLog.warn("in-game UI: " + message + "; falling back to the CPU renderer");
    }
}
