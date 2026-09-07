package dev.voidpvp.client.ui;

import dev.voidpvp.client.VoidLog;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Creates the one {@link WebView} the mod paints, falling back to
 * {@link NullWebView} when the Ultralight binding is missing or will not load.
 * The failure is logged once and the game is not disturbed (§6.2).
 */
public final class WebViews {

    private static boolean warned;
    private static PrintStream rawStderr;

    private WebViews() {
    }

    /**
     * Whether the accelerated renderer was asked for. It is the default.
     *
     * <p>{@code VOID_UI_RENDERER=cpu} or {@code -Dvoid.ui.renderer=cpu} turns it off; anything
     * other than {@code cpu} leaves it on. Asking is not the same as getting it — the accelerated
     * path needs a GL context on the thread that owns the engine ({@link UiGlContext}) and needs
     * its GLSL 1.20 programs to build on this GPU, and either can say no. {@link WebView#isAccelerated}
     * on the created view is the answer; this is only the question.</p>
     *
     * <p><b>Why the accelerated renderer is the default.</b> The measurement always favoured it,
     * and the cost of the CPU surface is not marginal. In game on an M1 Max, menu open, the same
     * scripted interaction, a 3336x1870 view at 2.28 dp/css: mean frame rate is a tie at the
     * display's 120 Hz ceiling (113 against 115), but the worst game frame is 17-28 ms against
     * 40-47 and the worst UI paint 6-8 ms against 39-42. That gap is the point — the CPU surface's
     * slow paints are what the game thread's upload queues behind, so its worst frame tracks the
     * rasteriser's. Under the heavier page this was first measured against, the same comparison was
     * 87 fps against 77 and 2% of frames over 20 ms against 12%. A later measurement put it more
     * bluntly still: at 3456x1926 — 6.7 megapixels rasterised in software, up to 99 times a second
     * — the player simply experiences the game as laggy, where the GL driver idles the same menu at
     * zero paints and zero presents per second. Output is pixel-comparable: the driver's
     * {@code repeating-linear-gradient} bug is moot since the design dropped gradients entirely.</p>
     *
     * <p><b>What used to hold the flip, and what changed.</b> Not the numbers — the failure mode.
     * The GL driver has executed on exactly one machine, macOS on Apple's 2.1 profile, and never on
     * Windows or Linux. If the GLSL 1.20 programs would not link there, the old code set a
     * process-wide flag inside the renderer and every accelerated render returned without painting:
     * a blank overlay, no visible error, on hardware where the CPU path would have worked fine.
     * That is now a fallback rather than a dead end. {@link UltralightWebView#create} probes the
     * driver before any view exists and builds a CPU view when it will not run, and polls it once a
     * frame afterwards so a driver that dies later is rebuilt on the CPU surface instead of
     * latching. Both routes announce themselves through {@link #announceDowngrade}.</p>
     *
     * <p>The platform coverage that held the flip is still missing — nobody has run this driver on
     * Windows or Linux. What has changed is that finding out costs a slower interface rather than
     * no interface.</p>
     */
    public static boolean acceleratedRequested() {
        String choice = System.getenv("VOID_UI_RENDERER");
        if (choice == null) {
            choice = System.getProperty("void.ui.renderer", "gpu");
        }
        return !"cpu".equalsIgnoreCase(choice);
    }

    /**
     * Forces the accelerated probe to answer no, without asking the driver.
     *
     * <p>{@code VOID_UI_GPU_FAIL=probe} or {@code -Dvoid.ui.gpu.fail=probe}. The point of a
     * fallback is that it runs on machines we do not have, so there has to be a way to make it run
     * on machines we do; this is the Java half of that switch. It exercises everything from the
     * probe's answer outwards — the CPU view, the host's per-frame renderer questions, the blit —
     * on a build whose natives are whatever they are.</p>
     *
     * <p>The native half is the same variable with a different value, read in
     * {@code mod/native/src/gpu_driver_gl.cpp}, and it fails deeper: {@code init} before GL is
     * touched at all, {@code compile} by handing the driver invalid GLSL so the real compile error
     * path runs with the real driver's message, {@code link} by never calling
     * {@code glLinkProgram}, and {@code late[:n]} by killing the driver after it has already
     * painted, which is the only way to reach the mid-session rebuild. Those four are read by the
     * native library, so they need a build of it; {@code probe} is read here and does not.</p>
     */
    public static boolean forcedProbeFailure() {
        String mode = System.getenv("VOID_UI_GPU_FAIL");
        if (mode == null) {
            mode = System.getProperty("void.ui.gpu.fail", "");
        }
        return "probe".equalsIgnoreCase(mode);
    }

    /**
     * Builds the one view, on the renderer this machine can actually run.
     *
     * <p><b>There is deliberately no static here saying what came back.</b> There used to be —
     * {@code acceleratedInUse}, written on this line and nowhere else — and it is the third cache
     * of that fact to go stale and take the supersample cap with it. It was written by
     * {@code create} and never by {@link UltralightWebView#fallBackToCpu}, so after a mid-session
     * fallback {@link UiHost} still believed it was accelerated and handed a software rasteriser
     * the GPU's cap of 4: measured in game at {@code VOID_UI_GPU_FAIL=late} without the HiDPI
     * flag, a 3416x1920 — 6.6 megapixel — software raster where the cap is 2, on the machine that
     * had just lost its GPU.</p>
     *
     * <p>The answer lives on the returned view, as {@link WebView#isAccelerated()} and
     * {@link WebView#maxSupersample()}, and it keeps being the answer when the renderer changes
     * underneath. Ask the object, and there is nothing to keep in sync.</p>
     */
    public static WebView create(int width, int height, boolean accelerated) {
        try {
            return UltralightWebView.create(width, height, accelerated);
        } catch (UnsatisfiedLinkError e) {
            warnOnce("Ultralight is unavailable, in-game UI disabled: " + e.getMessage());
        } catch (RuntimeException e) {
            warnOnce("Ultralight failed to start, in-game UI disabled: " + e);
        } catch (LinkageError e) {
            warnOnce("Ultralight failed to link, in-game UI disabled: " + e);
        }
        return new NullWebView();
    }

    /**
     * Says, unmissably, that the interface has dropped from the accelerated renderer to the CPU
     * surface and what that means.
     *
     * <p>A downgrade that happens quietly is its own bug: the player loses frame rate for a reason
     * nobody can name, and the report that comes back is "the game is laggy" with nothing to act
     * on. So this is loud, and it goes out twice.</p>
     *
     * <p><b>Once through log4j</b>, which is the right channel in a shipped client. <b>And once
     * straight to file descriptor 2</b>, which is not paranoia. This exact class of failure has
     * already cost a day here: the Ultralight natives were left out of the JAR,
     * {@code Ultralight.load()} threw, the mod fell back to {@link NullWebView} — correctly, and
     * with a warning — and the warning was invisible because Loom's generated log4j config is
     * broken on 1.8.9 and silences every logger in the process. Minecraft also replaces
     * {@code System.err} with a log4j stream during bootstrap, so that is the same channel wearing
     * a different name. A stream opened on {@link FileDescriptor#err} is the one thing in this JVM
     * that no logging configuration can turn off, and it is where the native side's own errors
     * ({@code [voidultralight/error] gpu: ...}) come out, so the two halves of the story land in
     * the same place.</p>
     */
    static void announceDowngrade(String reason) {
        String message = "in-game UI: falling back to the CPU renderer because " + reason
                + ". The interface still works; it costs noticeably more frame time. "
                + "Set VOID_UI_RENDERER=cpu to skip the accelerated attempt entirely.";
        VoidLog.error(message);
        rawStderr().println("[void/error] " + message);
    }

    private static synchronized PrintStream rawStderr() {
        if (rawStderr == null) {
            // Never closed: closing it would close the process's stderr.
            rawStderr = new PrintStream(new FileOutputStream(FileDescriptor.err), true);
        }
        return rawStderr;
    }

    private static void warnOnce(String message) {
        if (!warned) {
            warned = true;
            VoidLog.warn(message);
        }
    }
}
