package dev.voidpvp.client.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Which renderer the mod asks for, and how the forced-failure switch is read.
 *
 * <p>These are the two decisions that can be made without a GL context, a window or the natives,
 * so they are the two that can be tested here. Everything past them — the probe, the CPU view, the
 * pixels — needs at minimum the native library, and is covered by
 * {@code mod/native/test/java/.../AcceleratedFallbackTest.java} under {@code ctest}.
 *
 * <p>The environment variable takes precedence over the system property in both methods, and this
 * JVM cannot set an environment variable for itself, so every case here goes through the property.
 * A run that already has the variable set is skipped rather than reporting a false failure.
 */
class WebViewsTest {

    private static final String RENDERER = "void.ui.renderer";
    private static final String FAIL = "void.ui.gpu.fail";

    @AfterEach
    void clearProperties() {
        System.clearProperty(RENDERER);
        System.clearProperty(FAIL);
    }

    @Test
    @DisplayName("the accelerated renderer is the default")
    void acceleratedByDefault() {
        assumeTrue(System.getenv("VOID_UI_RENDERER") == null);
        System.clearProperty(RENDERER);
        // The whole point of the change: the CPU surface rasterises millions of pixels per frame in
        // software, and it is no longer what a player gets unless they ask for it.
        assertTrue(WebViews.acceleratedRequested());
    }

    @Test
    @DisplayName("cpu is the one value that turns it off, in any case")
    void cpuOptsOut() {
        assumeTrue(System.getenv("VOID_UI_RENDERER") == null);
        System.setProperty(RENDERER, "cpu");
        assertFalse(WebViews.acceleratedRequested());
        System.setProperty(RENDERER, "CPU");
        assertFalse(WebViews.acceleratedRequested());
    }

    @Test
    @DisplayName("anything else means the accelerated renderer, which now degrades on its own")
    void unknownValuesStayAccelerated() {
        assumeTrue(System.getenv("VOID_UI_RENDERER") == null);
        System.setProperty(RENDERER, "gpu");
        assertTrue(WebViews.acceleratedRequested());
        // A typo used to mean "quietly use the CPU surface". It now means "try the fast one", which
        // is the safe direction to fail in only because the attempt falls back by itself.
        System.setProperty(RENDERER, "opengl");
        assertTrue(WebViews.acceleratedRequested());
    }

    @Test
    @DisplayName("VOID_UI_GPU_FAIL=probe forces the Java half of the fallback")
    void forcedProbeFailure() {
        assumeTrue(System.getenv("VOID_UI_GPU_FAIL") == null);
        System.clearProperty(FAIL);
        assertFalse(WebViews.forcedProbeFailure());
        System.setProperty(FAIL, "probe");
        assertTrue(WebViews.forcedProbeFailure());
        System.setProperty(FAIL, "PROBE");
        assertTrue(WebViews.forcedProbeFailure());
    }

    @Test
    @DisplayName("the deeper forced-failure modes belong to the native side, not this one")
    void nativeModesAreNotHandledHere() {
        assumeTrue(System.getenv("VOID_UI_GPU_FAIL") == null);
        // init / compile / link / late are read by mod/native/src/gpu_driver_gl.cpp, where they can
        // fail inside the driver rather than in front of it. If this class also short-circuited on
        // them, the real compile and link paths would never run and the switch would be testing
        // itself instead of the driver.
        for (String mode : new String[] {"init", "compile", "link", "late", "late:60"}) {
            System.setProperty(FAIL, mode);
            assertFalse(WebViews.forcedProbeFailure(), mode + " is the native side's to handle");
        }
    }

    @Test
    @DisplayName("a view that will not load is not accelerated either")
    void nullViewIsNotAccelerated() {
        assertFalse(new NullWebView().isAccelerated());
        assertFalse(new NullWebView().needsFullRepaintEachFrame());
        assertFalse(new NullWebView().rendersEveryFrame());
    }
}
