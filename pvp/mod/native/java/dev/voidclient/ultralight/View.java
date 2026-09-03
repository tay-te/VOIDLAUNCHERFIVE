package dev.voidclient.ultralight;

import java.util.function.Function;

/**
 * One Ultralight view — an HTML page rendered off-screen.
 *
 * <p>All calls must be made on the renderer's thread.
 */
public final class View implements AutoCloseable {

  /** {@link #fireMouseEvent} type: pointer moved. */
  public static final int MOUSE_MOVED = 0;
  /** {@link #fireMouseEvent} type: button pressed. */
  public static final int MOUSE_DOWN = 1;
  /** {@link #fireMouseEvent} type: button released. */
  public static final int MOUSE_UP = 2;

  /** {@link #fireKeyEvent} type: physical key pressed (Ultralight's RawKeyDown). */
  public static final int KEY_DOWN = 0;
  /** {@link #fireKeyEvent} type: physical key released. */
  public static final int KEY_UP = 1;
  /** {@link #fireKeyEvent} type: text produced by a key press. */
  public static final int KEY_CHAR = 2;

  /** {@link #fireKeyEvent} modifier bit: Alt. */
  public static final int MOD_ALT = 1;
  /** {@link #fireKeyEvent} modifier bit: Control. */
  public static final int MOD_CTRL = 2;
  /** {@link #fireKeyEvent} modifier bit: Meta (Command on macOS, Windows key on Windows). */
  public static final int MOD_META = 4;
  /** {@link #fireKeyEvent} modifier bit: Shift. */
  public static final int MOD_SHIFT = 8;

  private long handle;
  private final boolean accelerated;
  private Function<String, String> messageHandler;

  View(long handle, boolean accelerated) {
    this.handle = handle;
    this.accelerated = accelerated;
  }

  /**
   * Loads a URL. {@code file:///index.html} resolves against the classpath prefix passed to
   * {@link Ultralight#createRenderer(String)} — the page is read out of the mod JAR through a
   * {@code ULFileSystem} backed by {@code ClassLoader.getResourceAsStream}, so there is nothing on
   * disk to ship. Three slashes: {@code file:///}, not {@code file://}.
   */
  public void loadUrl(String url) {
    checkOpen();
    Native.viewLoadUrl(handle, url);
  }

  /** Loads a literal HTML string. Relative URLs inside it resolve like {@link #loadUrl}. */
  public void loadHtml(String html) {
    checkOpen();
    Native.viewLoadHtml(handle, html);
  }

  public void resize(int w, int h) {
    checkOpen();
    Native.viewResize(handle, w, h);
  }

  /**
   * Sets the device scale factor: Minecraft's GUI scale multiplied by the window DPI scale
   * (PVP_ARCHITECTURE §6.2). 1.0 means one CSS pixel per framebuffer pixel.
   */
  public void setDeviceScale(double s) {
    checkOpen();
    Native.viewSetDeviceScale(handle, s);
  }

  /**
   * The OpenGL texture holding this view's pixels. Valid after {@link Renderer#render()}, and only
   * for a view made by {@link Renderer#createView}.
   *
   * <p>Format: RGBA8, <b>premultiplied</b> alpha, top-left origin — draw it with {@code v = 0} at
   * the top and blend with {@code GL_ONE, GL_ONE_MINUS_SRC_ALPHA}.
   *
   * <p>The texture may be larger than the view; sample the sub-rectangle given by
   * {@link #uvScaleX()} / {@link #uvScaleY()}.
   *
   * @return the texture name, or 0 if nothing has been rendered yet.
   */
  public int glTextureId() {
    checkOpen();
    return Native.viewTextureId(handle);
  }

  /** Width of the backing texture in pixels (>= view width). */
  public int textureWidth() {
    checkOpen();
    return Native.viewTextureWidth(handle);
  }

  /** Height of the backing texture in pixels (>= view height). */
  public int textureHeight() {
    checkOpen();
    return Native.viewTextureHeight(handle);
  }

  /** Right edge of the view within the backing texture, in UV space (usually 1.0). */
  public float uvScaleX() {
    checkOpen();
    return Native.viewUvScaleX(handle);
  }

  /** Bottom edge of the view within the backing texture, in UV space (usually 1.0). */
  public float uvScaleY() {
    checkOpen();
    return Native.viewUvScaleY(handle);
  }

  /**
   * Whether the view has changed since the last paint.
   *
   * <p>Use it to skip {@link Renderer#render()} entirely on frames where the HUD is static — the
   * first lever if the paint budget in PVP_ARCHITECTURE §10 is missed.
   *
   * <p>For accelerated views this is Ultralight's "needs paint" flag, checked before rendering.
   * For CPU views it reports whether the surface has a non-empty dirty rect, checked after.
   */
  public boolean isDirty() {
    checkOpen();
    return Native.viewIsDirty(handle);
  }

  /**
   * Delivers a mouse event.
   *
   * @param type {@link #MOUSE_MOVED}, {@link #MOUSE_DOWN} or {@link #MOUSE_UP}
   * @param x view-relative x, in device-independent pixels
   * @param y view-relative y, in device-independent pixels
   * @param button 0 none, 1 left, 2 middle, 3 right
   */
  public void fireMouseEvent(int type, int x, int y, int button) {
    checkOpen();
    Native.viewFireMouseEvent(handle, type, x, y, button);
  }

  /**
   * Delivers a key event.
   *
   * <p>A typed character needs <b>two</b> events: {@link #KEY_DOWN} with the virtual key, then
   * {@link #KEY_CHAR} with the text. Ultralight will not insert text from a key-down alone.
   *
   * @param type {@link #KEY_DOWN}, {@link #KEY_UP} or {@link #KEY_CHAR}
   * @param virtualKey a Windows virtual-key code (Ultralight's {@code GK_*}); the caller maps from
   *     LWJGL. Ignored for {@link #KEY_CHAR}.
   * @param modifiers OR of {@link #MOD_ALT}, {@link #MOD_CTRL}, {@link #MOD_META},
   *     {@link #MOD_SHIFT}
   * @param text the text the key produced; required for {@link #KEY_CHAR}, may be null otherwise
   */
  public void fireKeyEvent(int type, int virtualKey, int modifiers, String text) {
    checkOpen();
    Native.viewFireKeyEvent(handle, type, virtualKey, modifiers, text);
  }

  /** Scrolls by a pixel delta. Positive {@code dy} scrolls the content down. */
  public void fireScrollEvent(int dx, int dy) {
    checkOpen();
    Native.viewFireScrollEvent(handle, dx, dy);
  }

  /**
   * Evaluates JavaScript in the page and returns the result as a string.
   *
   * @return the value's string form; {@code ""} if it is undefined or if the script threw (the
   *     exception is logged, never propagated — a broken UI must not take the render thread down)
   */
  public String evaluateScript(String js) {
    checkOpen();
    return Native.viewEvaluateScript(handle, js);
  }

  /**
   * Installs {@code window.__void_native(json) -> String}, the synchronous JS to Java call the
   * {@code window.void} bridge is built on (PVP_ARCHITECTURE §6.5).
   *
   * <p>The handler runs on whichever thread called {@link Renderer#update()} or
   * {@link Renderer#render()} — i.e. the render thread — and its return value is the JavaScript
   * return value. Returning null yields {@code null} in JS. A handler that throws logs and yields
   * a JS exception; it never escapes into Java.
   *
   * <p>The function is re-installed automatically on every navigation. Pass null to remove it.
   */
  public void setMessageHandler(Function<String, String> handler) {
    checkOpen();
    this.messageHandler = handler;
    Native.viewSetMessageHandler(handle, handler);
  }

  /** The handler set by {@link #setMessageHandler}, or null. */
  public Function<String, String> messageHandler() {
    return messageHandler;
  }

  /**
   * Gives or takes keyboard focus. The view must be focused for typing and for CSS {@code :focus}
   * to work; unfocus it when {@code VoidMenuScreen} closes.
   */
  public void setFocus(boolean f) {
    checkOpen();
    Native.viewSetFocus(handle, f);
  }

  /** Whether the page currently has a focused text input — Escape must be forwarded when it does. */
  public boolean hasInputFocus() {
    checkOpen();
    return Native.viewHasInputFocus(handle);
  }

  /** Whether a navigation is still in flight. */
  public boolean isLoading() {
    checkOpen();
    return Native.viewIsLoading(handle);
  }

  /**
   * Copies the CPU surface into a byte array: BGRA, premultiplied, top-left origin, tightly packed
   * at {@code width * height * 4} bytes.
   *
   * <p>Only valid for a view from {@link Renderer#createViewCpu}. Test-only.
   */
  public byte[] readPixels() {
    checkOpen();
    if (accelerated) {
      throw new IllegalStateException("readPixels() requires a view from createViewCpu()");
    }
    return Native.viewReadPixels(handle);
  }

  /** View width in device-independent pixels. */
  public int width() {
    checkOpen();
    return Native.viewWidth(handle);
  }

  /** View height in device-independent pixels. */
  public int height() {
    checkOpen();
    return Native.viewHeight(handle);
  }

  /** True if this view renders through the OpenGL driver rather than the CPU surface. */
  public boolean isAccelerated() {
    return accelerated;
  }

  @Override
  public void close() {
    if (handle != 0L) {
      Native.destroyView(handle);
      handle = 0L;
      messageHandler = null;
    }
  }

  private void checkOpen() {
    if (handle == 0L) {
      throw new IllegalStateException("View is closed");
    }
  }
}
