package dev.voidclient.ultralight;

/**
 * Entry point to the Ultralight 1.4 binding.
 *
 * <p>PACKAGE NAME: the contract asked for {@code dev.void.ultralight}. That is not a legal Java
 * package — {@code void} is a keyword and cannot be an identifier, so neither the declaration nor
 * an {@code import} of it compiles. The package is {@code dev.voidclient.ultralight} instead,
 * mirroring the mod's artifact name ({@code void-client}). Nothing else about the API changed.
 * See pvp/CONTRACTS.md § "Ultralight binding API".
 *
 * <h2>Threading</h2>
 * Every method on {@link Renderer} and {@link View} must be called from the one thread that called
 * {@link #createRenderer}: Minecraft's render thread. Ultralight is not thread-safe across calls
 * and there is no dispatch queue here on purpose — the whole point of running the UI in-process is
 * that a key press reaches a pixel in the same frame (PVP_ARCHITECTURE §6.1).
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   Ultralight.load();                                   // once, at mod init
 *   Renderer r = Ultralight.createRenderer("assets/void/ui/");
 *   View v = r.createView(width, height, true);
 *   v.loadUrl("file:///index.html");
 *   // per tick:  r.update();
 *   // per frame: r.render();  int tex = v.glTextureId();
 * </pre>
 */
public final class Ultralight {

  private Ultralight() {}

  private static volatile boolean loaded;
  private static volatile Renderer renderer;

  /**
   * Extracts the natives from the classpath to a temporary directory and loads them, Ultralight's
   * libraries first (they must already be in the process when our JNI library resolves its
   * imports, which is what makes this work on Windows without touching PATH).
   *
   * <p>Idempotent. Throws {@link UnsatisfiedLinkError} if the platform is unsupported, the natives
   * are missing from the classpath, or a library fails to load.
   */
  public static synchronized void load() throws UnsatisfiedLinkError {
    if (loaded) {
      return;
    }
    NativeLoader.load();
    loaded = true;
  }

  /**
   * Creates the process-wide renderer.
   *
   * @param resourcePathPrefix classpath prefix that {@code file:///…} URLs resolve against, e.g.
   *     {@code "assets/void/ui/"}. A trailing slash is added if missing. May be null or empty, in
   *     which case only the extracted natives directory is searched.
   *     <p>Ultralight's own runtime files ({@code resources/cacert.pem},
   *     {@code resources/icudt67l.dat}) are looked up under this prefix first and then in the
   *     natives directory, which is where they actually ship.
   * @return the renderer. Ultralight supports exactly one per process; calling this twice returns
   *     the existing instance.
   */
  public static synchronized Renderer createRenderer(String resourcePathPrefix) {
    load();
    if (renderer != null && !renderer.isClosed()) {
      return renderer;
    }
    String prefix = resourcePathPrefix == null ? "" : resourcePathPrefix;
    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    long handle = Native.createRenderer(prefix, NativeLoader.nativeDir());
    if (handle == 0L) {
      throw new IllegalStateException("Ultralight renderer could not be created; see the log");
    }
    renderer = new Renderer(handle);
    return renderer;
  }

  /** Ultralight's version string, e.g. {@code "1.4.0"}. Requires {@link #load()}. */
  public static String version() {
    load();
    return Native.version();
  }

  /** The WebKit version Ultralight is built on, for the About/credits screen. */
  public static String webKitVersion() {
    load();
    return Native.webKitVersion();
  }

  /**
   * The credit line the Ultralight licence requires in an About/credits screen
   * (LICENSE.txt §4.4 "Marking"). PVP_ARCHITECTURE §13.
   */
  public static String licenceNotice() {
    return "Ultralight (c) 2024 Ultralight, Inc. All rights reserved. "
        + "Ultralight is a trademark of Ultralight, Inc. "
        + "Portions of this software are licensed from third parties; "
        + "see the accompanying NOTICES.md for full text.";
  }

  /** Absolute path of the directory the natives were extracted to. Null before {@link #load()}. */
  public static String nativeDirectory() {
    return NativeLoader.nativeDir();
  }
}
