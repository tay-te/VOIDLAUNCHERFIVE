package dev.voidclient.ultralight.test;

import dev.voidclient.ultralight.Renderer;
import dev.voidclient.ultralight.Ultralight;
import dev.voidclient.ultralight.View;

/**
 * The evidence for the CPU fallback: when the OpenGL driver will not run, does the binding say so,
 * and does a CPU view still produce a page?
 *
 * <p>This exists because the failure it covers happens on hardware nobody here has. The GL driver
 * has executed on exactly one machine — macOS, Apple's OpenGL 2.1 profile — and the risk it carries
 * is a Windows or Linux driver refusing its GLSL 1.20 programs. Until this test the fallback was an
 * argument, not an observation: a code path that had never once executed, guarding a case nobody
 * could reproduce. {@code VOID_UI_GPU_FAIL} is what makes it reproducible, and this is what runs it.
 *
 * <h2>What it can and cannot reach</h2>
 *
 * <p>Headless, no display, no GL context — the same constraint that lets {@link CpuRenderTest} run
 * in CI. So it uses {@code VOID_UI_GPU_FAIL=init}, the one mode that fails <em>before</em> any GL
 * entry point is resolved. The three deeper modes need a real context and therefore a real game
 * window:
 *
 * <ul>
 *   <li>{@code compile} — invalid GLSL appended to every fragment shader, so the driver's own
 *       compile error path runs with the driver's own message.</li>
 *   <li>{@code link} — {@code glLinkProgram} never called, so {@code GL_LINK_STATUS} comes back
 *       false from the driver.</li>
 *   <li>{@code late[:n]} — the driver dies after it has already painted, which is the only way to
 *       reach the mid-session rebuild in {@code UltralightWebView.fallBackToCpu}.</li>
 * </ul>
 *
 * <p>So: this proves the decision and the CPU path. It does not prove the pixels a real GPU
 * failure would produce, and nothing running without a window can.
 */
public final class AcceleratedFallbackTest {

  private static final int WIDTH = 800;
  private static final int HEIGHT = 480;

  private static int failures = 0;

  public static void main(String[] args) throws Exception {
    String forced = System.getenv("VOID_UI_GPU_FAIL");
    System.out.println("VOID_UI_GPU_FAIL = " + forced);
    if (!"init".equals(forced)) {
      // Not a soft skip. Without the switch this test would ask the driver a question it can only
      // answer by calling glGetString on a thread with no context, which segfaults rather than
      // returning false — so a run without it is not a weaker test, it is a crash.
      System.out.println("FAILED: this test must run with VOID_UI_GPU_FAIL=init");
      System.exit(1);
    }

    Ultralight.load();
    Renderer renderer = Ultralight.createRenderer("assets/void/ui/");

    check(!renderer.acceleratedDriverFailed(),
        "the driver is not marked failed before anything has asked it");

    // --- the probe ------------------------------------------------------------------------------
    boolean accelerated = renderer.probeAccelerated();
    check(!accelerated, "probeAccelerated() reported the accelerated renderer as unusable");
    check(renderer.acceleratedDriverFailed(),
        "the failure is visible afterwards through acceleratedDriverFailed()");
    check(!renderer.probeAccelerated(),
        "a second probe answers false without retrying (the failure is sticky)");

    // --- what the host does with that answer -----------------------------------------------------
    //
    // This is the line the whole change turns on. Before it, the host created an accelerated view
    // regardless, and a driver that could not run made every render return without painting: a
    // blank overlay, no error, on a machine where this path would have worked perfectly.
    View view = accelerated
        ? renderer.createView(WIDTH, HEIGHT, true)
        : renderer.createViewCpu(WIDTH, HEIGHT, true);
    check(!view.isAccelerated(), "the fallback view is a CPU surface");

    view.loadUrl("file:///index.html");
    long deadline = System.currentTimeMillis() + 15_000L;
    while (view.isLoading() && System.currentTimeMillis() < deadline) {
      renderer.update();
      Thread.sleep(4);
    }
    check(!view.isLoading(), "the page loaded into the fallback view");

    // A dead GL driver must not poison the CPU path. Inside the binding the CPU branch of
    // rendererRender is taken on "no accelerated views exist" and is reached *before* the
    // driver-failed guard; if those two were ever reordered, this render would silently do nothing
    // and the fallback would be as blank as the bug it replaces.
    for (int i = 0; i < 3; i++) {
      renderer.update();
      renderer.refreshDisplay();
      renderer.render();
      Thread.sleep(16);
    }

    check("520".equals(view.evaluateScript(
            "String(Math.round(document.getElementById('card').getBoundingClientRect().width))")),
        "the page laid out in the fallback view");

    byte[] bgra = view.readPixels();
    check(bgra != null && bgra.length == WIDTH * HEIGHT * 4,
        "readPixels() returned " + WIDTH + "x" + HEIGHT + " BGRA from the fallback view");

    // The point of the whole exercise: pixels, not a blank screen. The card is 520x300 centred, so
    // its middle must be opaque and the corner of the view must not be.
    int centre = alphaAt(bgra, WIDTH, WIDTH / 2, HEIGHT / 2);
    check(centre > 200, "the fallback view actually rendered content (centre alpha " + centre + ")");
    check(alphaAt(bgra, WIDTH, 4, 4) == 0, "and left the rest of the overlay transparent");

    int opaque = 0;
    for (int y = (HEIGHT - 300) / 2; y < (HEIGHT + 300) / 2; y++) {
      for (int x = (WIDTH - 520) / 2; x < (WIDTH + 520) / 2; x++) {
        if (alphaAt(bgra, WIDTH, x, y) > 200) {
          opaque++;
        }
      }
    }
    check(opaque > (520 * 300) * 0.9,
        "the fallback filled the card area (" + opaque + " of " + (520 * 300) + " pixels)");

    view.close();
    renderer.close();

    System.out.println();
    System.out.println(failures > 0 ? "FAILED: " + failures + " check(s)" : "PASSED");
    // System.exit rather than returning: a thread that has touched WebCore aborts the process when
    // its thread-local destructors run. See CpuRenderTest for the full note.
    System.exit(failures > 0 ? 1 : 0);
  }

  private static void check(boolean condition, String what) {
    System.out.println((condition ? "  ok   " : "  FAIL ") + what);
    if (!condition) {
      failures++;
    }
  }

  private static int alphaAt(byte[] bgra, int width, int x, int y) {
    return bgra[(y * width + x) * 4 + 3] & 0xFF;
  }
}
