package dev.voidclient.ultralight;

import java.util.function.Function;

/**
 * Every JNI entry point, in one place. Package-private: {@link Ultralight}, {@link Renderer} and
 * {@link View} are the API.
 *
 * <p>Handles are native pointers boxed as longs. Passing a stale handle is undefined behaviour, so
 * the wrapper classes null theirs on close and check before every call.
 */
final class Native {

  private Native() {}

  // --- library ---------------------------------------------------------------------------------
  static native String version();

  static native String webKitVersion();

  static native String bindingVersion();

  // --- renderer --------------------------------------------------------------------------------
  static native long createRenderer(String classpathPrefix, String nativeDir);

  static native void destroyRenderer(long renderer);

  static native void rendererUpdate(long renderer);

  static native void rendererRender(long renderer);

  static native void rendererRefreshDisplay(long renderer, int displayId);

  static native void rendererPurgeMemory(long renderer);

  static native long createView(long renderer, int width, int height, boolean transparent,
      boolean accelerated);

  // --- view ------------------------------------------------------------------------------------
  static native void destroyView(long view);

  static native void viewLoadUrl(long view, String url);

  static native void viewLoadHtml(long view, String html);

  static native void viewResize(long view, int width, int height);

  static native void viewSetDeviceScale(long view, double scale);

  static native int viewTextureId(long view);

  static native int viewTextureWidth(long view);

  static native int viewTextureHeight(long view);

  static native float viewUvScaleX(long view);

  static native float viewUvScaleY(long view);

  static native boolean viewIsDirty(long view);

  static native void viewFireMouseEvent(long view, int type, int x, int y, int button);

  static native void viewFireKeyEvent(long view, int type, int virtualKey, int modifiers,
      String text);

  static native void viewFireScrollEvent(long view, int dx, int dy);

  static native String viewEvaluateScript(long view, String js);

  static native void viewSetMessageHandler(long view, Function<String, String> handler);

  static native void viewSetFocus(long view, boolean focus);

  static native boolean viewHasInputFocus(long view);

  static native boolean viewIsLoading(long view);

  static native byte[] viewReadPixels(long view);

  static native int viewWidth(long view);

  static native int viewHeight(long view);
}
