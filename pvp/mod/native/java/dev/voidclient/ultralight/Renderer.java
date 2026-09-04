package dev.voidclient.ultralight;

/**
 * Ultralight's core renderer. One per process; obtained from
 * {@link Ultralight#createRenderer(String)}.
 *
 * <p>All calls must be made on the thread that created it.
 */
public final class Renderer implements AutoCloseable {

  private long handle;

  Renderer(long handle) {
    this.handle = handle;
  }

  /**
   * Runs timers and dispatches JavaScript / network callbacks. Call once per game tick
   * (PVP_ARCHITECTURE §6.2). Cheap when nothing is pending.
   */
  public void update() {
    checkOpen();
    Native.rendererUpdate(handle);
  }

  /**
   * Paints every dirty view. Call once per frame, with Minecraft's GL context current.
   *
   * <p>For accelerated views this drives the OpenGL driver: the driver saves the GL state it is
   * about to touch, renders into each view's FBO, and restores that state before returning, so
   * Minecraft's immediate-mode renderer sees no change.
   */
  public void render() {
    checkOpen();
    Native.rendererRender(handle);
  }

  /**
   * Creates a GPU-accelerated view. Its content is available as an OpenGL texture from
   * {@link View#glTextureId()} after {@link #render()}.
   *
   * @param transparent true for a transparent page background — what the HUD needs.
   */
  public View createView(int w, int h, boolean transparent) {
    checkOpen();
    long v = Native.createView(handle, w, h, transparent, true);
    if (v == 0L) {
      throw new IllegalStateException("could not create view " + w + "x" + h);
    }
    return new View(v, true);
  }

  /**
   * Creates a CPU-rendered view whose pixels are read back with {@link View#readPixels()}.
   *
   * <p>Used by the test harness (and any headless tooling): it needs no GL context at all. Not for
   * in-game use — full-frame readback is exactly what PVP_ARCHITECTURE §6.2 forbids.
   */
  public View createViewCpu(int w, int h, boolean transparent) {
    checkOpen();
    long v = Native.createView(handle, w, h, transparent, false);
    if (v == 0L) {
      throw new IllegalStateException("could not create CPU view " + w + "x" + h);
    }
    return new View(v, false);
  }

  /**
   * Tells Ultralight a display refreshed. Drives CSS animations, smooth scroll and
   * {@code requestAnimationFrame}. Call once per frame before {@link #render()}.
   */
  public void refreshDisplay() {
    checkOpen();
    Native.rendererRefreshDisplay(handle, 0);
  }

  /** Asks Ultralight to release as much memory as it can. Never call this from a callback. */
  public void purgeMemory() {
    checkOpen();
    Native.rendererPurgeMemory(handle);
  }

  boolean isClosed() {
    return handle == 0L;
  }

  @Override
  public void close() {
    if (handle != 0L) {
      Native.destroyRenderer(handle);
      handle = 0L;
    }
  }

  private void checkOpen() {
    if (handle == 0L) {
      throw new IllegalStateException("Renderer is closed");
    }
  }
}
