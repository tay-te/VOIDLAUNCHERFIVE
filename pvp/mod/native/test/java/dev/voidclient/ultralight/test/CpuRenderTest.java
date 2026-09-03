package dev.voidclient.ultralight.test;

import dev.voidclient.ultralight.Renderer;
import dev.voidclient.ultralight.Ultralight;
import dev.voidclient.ultralight.View;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Function;

import javax.imageio.ImageIO;

/**
 * The M1 evidence: does Ultralight 1.4, driven through this binding, actually hold the card design?
 *
 * <p>Runs on the CPU renderer, so it needs no GL context and no display — which is the only reason
 * it can run in CI at all. Everything it exercises (the classpath file system, the font loader,
 * the JS bridge, event plumbing) is shared with the GPU path; the OpenGL driver itself is the part
 * this cannot reach.
 *
 * <p>Plain {@code main}, no test framework: it has to run from a raw classpath next to a freshly
 * built native library.
 */
public final class CpuRenderTest {

  private static final int WIDTH = 800;
  private static final int HEIGHT = 480;

  // The card is 520x300, centred in the view.
  private static final int CARD_W = 520;
  private static final int CARD_H = 300;
  private static final int CARD_X = (WIDTH - CARD_W) / 2;
  private static final int CARD_Y = (HEIGHT - CARD_H) / 2;

  private static int failures = 0;

  public static void main(String[] args) throws Exception {
    File out = new File(args.length > 0 ? args[0] : "test/expected/out.png");

    Ultralight.load();
    System.out.println("ultralight      = " + Ultralight.version());
    System.out.println("webkit          = " + Ultralight.webKitVersion());
    System.out.println("natives         = " + Ultralight.nativeDirectory());

    Renderer renderer = Ultralight.createRenderer("assets/void/ui/");
    View view = renderer.createViewCpu(WIDTH, HEIGHT, true);

    // The bridge has to be installed before the page runs any script.
    view.setMessageHandler(new Function<String, String>() {
      @Override
      public String apply(String json) {
        if (json != null && json.contains("\"c\":\"ping\"")) {
          return "{\"c\":\"ping\",\"returns\":\"pong\"}";
        }
        return "{\"c\":\"unknown\"}";
      }
    });

    view.loadUrl("file:///index.html");

    // Pump until the load settles. Ultralight loads asynchronously even from a synchronous file
    // system: the parse, the font fetch and the first layout each need an update() to progress.
    long deadline = System.currentTimeMillis() + 15_000L;
    while (view.isLoading() && System.currentTimeMillis() < deadline) {
      renderer.update();
      Thread.sleep(4);
    }
    check(!view.isLoading(), "page finished loading");

    // Three frames, as the brief asks. update() runs timers; refreshDisplay() advances animations.
    for (int i = 0; i < 3; i++) {
      renderer.update();
      renderer.refreshDisplay();
      renderer.render();
      Thread.sleep(16);
    }

    // --- DOM / layout ---------------------------------------------------------------------------
    String cardWidth = view.evaluateScript(
        "Math.round(document.getElementById('card').getBoundingClientRect().width)");
    System.out.println("card width      = " + cardWidth);
    check(String.valueOf(CARD_W).equals(cardWidth), "flexbox laid the card out at " + CARD_W + "px");

    String undefinedResult = view.evaluateScript("void 0");
    check("".equals(undefinedResult), "evaluateScript returns \"\" for undefined");

    String thrown = view.evaluateScript("throw new Error('boom')");
    check("".equals(thrown), "evaluateScript returns \"\" when the script throws");

    check("42".equals(view.evaluateScript("6*7")), "evaluateScript returns values");

    // --- @font-face -----------------------------------------------------------------------------
    String fontCount = view.evaluateScript("String(document.fonts ? document.fonts.size : -1)");
    String fontLoaded = view.evaluateScript(
        "String(document.fonts ? document.fonts.check('16px VoidTest') : 'no-api')");
    System.out.println("document.fonts  = size " + fontCount + ", check(VoidTest) " + fontLoaded);
    check("true".equals(fontLoaded),
        "@font-face 'VoidTest' resolved through the ULFileSystem (document.fonts.check)");

    // Text actually measured: a zero-width run would mean the glyphs never arrived.
    String titleWidth = view.evaluateScript(
        "Math.round(document.querySelector('.title').getBoundingClientRect().width)");
    System.out.println("title width     = " + titleWidth);
    check(Integer.parseInt(titleWidth) > 30, "the title text has non-zero measured width");

    // --- CSS transition -------------------------------------------------------------------------
    String before = view.evaluateScript(
        "Math.round(document.getElementById('fill').getBoundingClientRect().width)");
    view.evaluateScript("document.getElementById('fill').classList.add('wide')");
    for (int i = 0; i < 40; i++) {
      renderer.update();
      renderer.refreshDisplay();
      renderer.render();
      Thread.sleep(16);
    }
    String after = view.evaluateScript(
        "Math.round(document.getElementById('fill').getBoundingClientRect().width)");
    System.out.println("meter width     = " + before + " -> " + after);
    check(Integer.parseInt(before) == 120 && Integer.parseInt(after) == 380,
        "the CSS transition ran from 120px to 380px");

    // --- window.__void_native round trip ---------------------------------------------------------
    String reply = view.evaluateScript("window.__void_native('{\"c\":\"ping\"}')");
    System.out.println("bridge reply    = " + reply);
    check("{\"c\":\"ping\",\"returns\":\"pong\"}".equals(reply),
        "window.__void_native round-tripped JS -> Java -> JS");

    check("function".equals(view.evaluateScript("typeof window.__void_native")),
        "window.__void_native is a function on the page");

    // --- input plumbing --------------------------------------------------------------------------
    view.setFocus(true);
    view.fireMouseEvent(View.MOUSE_MOVED, WIDTH / 2, HEIGHT / 2, 0);
    view.fireMouseEvent(View.MOUSE_DOWN, WIDTH / 2, HEIGHT / 2, 1);
    view.fireMouseEvent(View.MOUSE_UP, WIDTH / 2, HEIGHT / 2, 1);
    view.fireScrollEvent(0, -40);
    view.fireKeyEvent(View.KEY_DOWN, 0x41, View.MOD_SHIFT, null);
    view.fireKeyEvent(View.KEY_CHAR, 0, View.MOD_SHIFT, "A");
    view.fireKeyEvent(View.KEY_UP, 0x41, View.MOD_SHIFT, null);
    renderer.update();
    renderer.render();
    check(true, "mouse / key / scroll events dispatched without a crash");

    // --- pixels ----------------------------------------------------------------------------------
    byte[] bgra = view.readPixels();
    check(bgra != null && bgra.length == WIDTH * HEIGHT * 4,
        "readPixels() returned " + WIDTH + "x" + HEIGHT + " BGRA");

    // isDirty() is the lever for painting only on change (PVP_ARCHITECTURE §10), so it has to be
    // honest in both directions: clean straight after a readback, dirty once the DOM moves.
    check(!view.isDirty(), "isDirty() is false immediately after readPixels()");
    view.evaluateScript("document.getElementById('fps').textContent = '999'");
    renderer.update();
    renderer.render();
    check(view.isDirty(), "isDirty() is true after a DOM change repainted the surface");
    view.readPixels();

    BufferedImage image = toImage(bgra, WIDTH, HEIGHT);
    File parent = out.getAbsoluteFile().getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    ImageIO.write(image, "png", out);
    System.out.println("wrote           = " + out.getAbsolutePath());

    int cardCx = CARD_X + CARD_W / 2;
    int cardCy = CARD_Y + CARD_H / 2;
    int centreAlpha = alphaAt(bgra, WIDTH, cardCx, cardCy);
    check(centreAlpha > 200,
        "the card centre is opaque (alpha " + centreAlpha + " at " + cardCx + "," + cardCy + ")");

    int cornerAlpha = alphaAt(bgra, WIDTH, 4, 4);
    check(cornerAlpha == 0, "outside the card is fully transparent (alpha " + cornerAlpha + ")");

    // The rounded corner has to be cut out: 3px inside the card's bounding box, diagonally.
    int roundedAlpha = alphaAt(bgra, WIDTH, CARD_X + 2, CARD_Y + 2);
    check(roundedAlpha < 128,
        "border-radius clipped the card corner (alpha " + roundedAlpha + ")");

    int opaque = countOpaque(bgra, WIDTH, HEIGHT, CARD_X, CARD_Y, CARD_W, CARD_H);
    double coverage = opaque / (double) (CARD_W * CARD_H);
    System.out.printf("card coverage   = %.1f%%%n", coverage * 100.0);
    check(coverage > 0.90, "the card area is filled");

    // A flat fill would show a handful of colours; a gradient plus antialiased text shows hundreds.
    int distinct = countDistinctColours(bgra, WIDTH, CARD_X + 20, CARD_Y + 20, CARD_W - 40,
        CARD_H - 40);
    System.out.println("distinct colours= " + distinct);
    check(distinct > 200, "the gradient and text produced a rich colour histogram");

    int bright = brightPixels(bgra, WIDTH, CARD_X, CARD_Y, CARD_W, CARD_H);
    System.out.println("bright pixels   = " + bright);
    check(bright > 500, "white text was rasterised inside the card");

    // Orientation, and box-shadow in one check. The shadow is `0 24px 60px`, i.e. offset
    // DOWNWARD, so the band below the card must carry far more alpha than the band above it. If
    // readPixels() (or the CPU surface) were bottom-up, this inverts.
    long above = alphaSum(bgra, WIDTH, CARD_X, CARD_Y - 26, CARD_W, 16);
    long below = alphaSum(bgra, WIDTH, CARD_X, CARD_Y + CARD_H + 10, CARD_W, 16);
    System.out.println("shadow abv/blw  = " + above + " / " + below);
    check(below > above * 2 && below > 0,
        "the box-shadow falls below the card (top-left origin, shadow rendered)");

    view.close();
    renderer.close();

    System.out.println();
    System.out.println(failures > 0 ? "FAILED: " + failures + " check(s)" : "PASSED");

    // Exit deliberately instead of returning from main. Letting the launcher thread terminate as a
    // pthread runs WebCore's thread-local destructor, and in 1.4.0b that destructor re-enters
    // WebCore::threadGlobalData() while tearing down the font cache and aborts inside
    // WTFCrashWithInfo. System.exit() leaves the process from inside the thread, so the TSD
    // destructors never run — which is also how Minecraft quits (Minecraft.shutdown ->
    // System.exit(0)), so the game is not exposed to this. See README "Known risks".
    System.exit(failures > 0 ? 1 : 0);
  }

  // ------------------------------------------------------------------------------------------------
  private static void check(boolean condition, String what) {
    System.out.println((condition ? "  ok   " : "  FAIL ") + what);
    if (!condition) {
      failures++;
    }
  }

  private static int alphaAt(byte[] bgra, int width, int x, int y) {
    return bgra[(y * width + x) * 4 + 3] & 0xFF;
  }

  private static int countOpaque(byte[] bgra, int width, int height, int x0, int y0, int w, int h) {
    int n = 0;
    for (int y = y0; y < y0 + h && y < height; y++) {
      for (int x = x0; x < x0 + w && x < width; x++) {
        if (alphaAt(bgra, width, x, y) > 200) {
          n++;
        }
      }
    }
    return n;
  }

  private static int countDistinctColours(byte[] bgra, int width, int x0, int y0, int w, int h) {
    java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
    for (int y = y0; y < y0 + h; y++) {
      for (int x = x0; x < x0 + w; x++) {
        int i = (y * width + x) * 4;
        seen.add(((bgra[i + 2] & 0xFF) << 16) | ((bgra[i + 1] & 0xFF) << 8) | (bgra[i] & 0xFF));
      }
    }
    return seen.size();
  }

  private static long alphaSum(byte[] bgra, int width, int x0, int y0, int w, int h) {
    long total = 0;
    for (int y = y0; y < y0 + h; y++) {
      for (int x = x0; x < x0 + w; x++) {
        total += alphaAt(bgra, width, x, y);
      }
    }
    return total;
  }

  private static int brightPixels(byte[] bgra, int width, int x0, int y0, int w, int h) {
    int n = 0;
    for (int y = y0; y < y0 + h; y++) {
      for (int x = x0; x < x0 + w; x++) {
        int i = (y * width + x) * 4;
        int b = bgra[i] & 0xFF;
        int g = bgra[i + 1] & 0xFF;
        int r = bgra[i + 2] & 0xFF;
        if (r > 170 && g > 170 && b > 170) {
          n++;
        }
      }
    }
    return n;
  }

  /** BGRA, premultiplied -> straight-alpha ARGB, so the PNG looks right in any viewer. */
  private static BufferedImage toImage(byte[] bgra, int width, int height) {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = (y * width + x) * 4;
        int b = bgra[i] & 0xFF;
        int g = bgra[i + 1] & 0xFF;
        int r = bgra[i + 2] & 0xFF;
        int a = bgra[i + 3] & 0xFF;
        if (a != 0 && a != 255) {
          r = Math.min(255, r * 255 / a);
          g = Math.min(255, g * 255 / a);
          b = Math.min(255, b * 255 / a);
        }
        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
      }
    }
    return img;
  }
}
