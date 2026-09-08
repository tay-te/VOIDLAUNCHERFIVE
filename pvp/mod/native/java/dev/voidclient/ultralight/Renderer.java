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
   * Builds the OpenGL driver and reports whether this machine can run it, before any accelerated
   * view exists.
   *
   * <p><b>Why this is separate from {@link #createView}.</b> The driver's two GLSL 1.20 programs
   * are compiled and linked by the GPU, and whether they build is a property of the driver the
   * user happens to have. It has been verified on one: macOS, Apple's OpenGL 2.1 profile. Until
   * this method existed the only way to find out was to create an accelerated view and render it;
   * a failure there left the view alive and permanently unpainted, which the player sees as an
   * empty screen with no error and no way back. Ask first, and a machine that cannot run the
   * driver gets {@link #createViewCpu} instead.</p>
   *
   * <p>Call it on the thread that owns this renderer, with that thread's GL context current. The
   * driver resolves its entry points through {@code glGetString}, which segfaults on a thread with
   * no context, so this has the same precondition as the render it stands in for.</p>
   *
   * <p>Idempotent and sticky: a driver that has come up answers true without touching GL, and one
   * that has failed answers false without retrying. Any reason it failed has already been logged
   * at error level on stderr.</p>
   *
   * @return true if accelerated views will paint on this machine
   */
  public boolean probeAccelerated() {
    checkOpen();
    return Native.rendererProbeAccelerated(handle);
  }

  /**
   * Whether the OpenGL driver has died in this process.
   *
   * <p>False until something goes wrong, true forever afterwards. It covers the failure
   * {@link #probeAccelerated} cannot: a driver that built its programs, painted, and then stopped
   * being usable. An accelerated view whose driver has failed will never paint again, so the host
   * has to notice and rebuild the view on the CPU path rather than let it sit there blank.</p>
   *
   * <p>Readable from any thread — it enters neither Ultralight nor GL — and cheap enough to poll
   * once a frame, which is what the mod does.</p>
   */
  public boolean acceleratedDriverFailed() {
    return Native.gpuDriverFailed();
  }

  /**
   * Creates a GPU-accelerated view. Its content is available as an OpenGL texture from
   * {@link View#glTextureId()} after {@link #render()}.
   *
   * <p>Call {@link #probeAccelerated()} first. This method does not check whether the driver works
   * — it cannot, since a view is created before anything is ever rendered through it — and an
   * accelerated view on a machine whose driver will not build is a view that never paints.</p>
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
   * Creates a CPU-rendered view: Ultralight rasterises into a bitmap surface, and
   * {@link View#glTextureId()} uploads that surface's dirty rectangle into a GL texture on
   * whichever thread holds the game's context.
   *
   * <p>Two callers. The test harness and any headless tooling, which read the pixels back with
   * {@link View#readPixels()} and need no GL context at all; and the in-game fallback, for a
   * machine where {@link #probeAccelerated()} says no. Only the dirty rectangle is uploaded, so
   * this is not the full-frame readback PVP_ARCHITECTURE §6.2 rules out — but the rasterisation
   * itself is software, and at a Retina framebuffer that is millions of pixels per frame on the
   * CPU. It is the safe path, not the fast one.
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
