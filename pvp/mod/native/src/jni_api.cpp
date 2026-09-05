// JNI entry points for dev.voidclient.ultralight.Native.
//
// Handles are raw pointers boxed as jlong.
//
// -----------------------------------------------------------------------------------------------
// Thread affinity
//
// Two threads, and the split is not symmetric.
//
//   UI thread — every entry point in this file except viewTextureId. It must be ONE thread,
//   whichever one it is, for the life of the process: whatever calls createRenderer is what must
//   go on to call rendererUpdate, rendererRefreshDisplay, rendererRender, rendererPurgeMemory,
//   createView, destroyView, viewLoadUrl/LoadHtml, viewResize, viewSetDeviceScale,
//   viewSetNeedsPaint, viewIsDirty, viewFireMouse/Key/ScrollEvent, viewEvaluateScript,
//   viewSetMessageHandler, viewSetFocus, viewHasInputFocus, viewIsLoading, viewWidth/Height,
//   viewTextureWidth/Height, viewReadPixels and destroyRenderer.
//
//   Not "one at a time" — the same one, and this is the part that is easy to get wrong. WebCore
//   keeps per-thread globals (ThreadGlobalData: the font cache, the main-thread shared timer) that
//   are constructed on the thread that first touches them, and JavaScriptCore's VM belongs to the
//   thread that created it. A second thread entering later does not queue behind the first, it
//   builds its own copy of that state — a different font cache, a timer nobody services — or
//   trips an assertion on the way. A mutex around these calls does not make them interchangeable;
//   only pinning them does.
//
//   GL thread — viewTextureId, and nothing else that touches the engine. It may be a different
//   thread, and in game it is: Minecraft's render thread, the only one with the GL context
//   current. viewUvScaleX and viewUvScaleY may be called from it too, but only because for a CPU
//   view they answer 1.0f from an immutable flag without entering Ultralight at all — which is
//   what UiHost.paint() already relies on. For an accelerated view they read the render target and
//   belong to the UI thread like everything else.
//
//   Everything else that looks harmless is not: viewTextureWidth/Height, viewWidth/Height and
//   viewIsDirty all read live View state and race a resize or a render. If the blit needs the
//   surface size, the UI thread should publish it to Java after a resize rather than the GL thread
//   asking for it here.
//
// Why the split exists at all. Measured: ulUpdate -> ulRefreshDisplay -> ulRender costs 6-28 ms on
// an animation frame, on top of a game frame that is already 11-15 ms, and none of that work
// touches GL. What does touch GL is only the tail — read the surface's dirty rect,
// glTexSubImage2D it into a texture, blit — so that is the only thing that has to stay on the
// thread holding the context. Everything else can run at its own pace on a thread of its own.
//
// This replaces the rule this file used to state ("everything here must be called from the thread
// that created the renderer (Minecraft's render thread)"), which is now half true: the renderer
// still has exactly one owning thread, but it no longer has to be Minecraft's, and the upload
// deliberately is not on it.
//
// Four consequences that do not follow from reading the code:
//
//   * The UI thread must never exit. A thread that has touched WebCore aborts the process on the
//     way out — README "Known risks" #7, reproduced under gdb: the pthread's TSD destructors run
//     ~ThreadGlobalData, which re-enters threadGlobalData() from inside its own destructor.
//     Minecraft's render thread is exposed to this too and gets away with it only because the
//     process leaves through System.exit(0) from inside it. A dedicated UI thread that returns
//     from run() takes the game down with it; it has to park until the process ends.
//
//   * createRenderer must run on a thread with a Java frame beneath it. It caches
//     dev.voidclient.ultralight.Resources with FindClass, which resolves through the class loader
//     of the nearest Java frame — the mod's, which is the one that can see the UI bundle. Called
//     from a bare native thread attached by common.cpp there is no such frame, FindClass falls
//     back to the system loader and the lookup fails. A UI thread created in Java is fine; a
//     std::thread is not.
//
//   * Everything Ultralight calls back into Java now arrives on the UI thread rather than the
//     render thread: the window.__void_native message handler (js_bridge.cpp), console messages,
//     load failures. Whatever those touch on the Minecraft side has to expect it.
//
//   * Accelerated views cannot use the split. rendererRender does the GL work itself for them
//     (gpu::initialize / draw_command_list / save+restore state), so with any view from
//     createView alive the UI thread has to *be* the GL thread and there is nothing to gain. The
//     CPU path — createViewCpu, which is the in-game default — is what this is for.
//
// The one object the two threads share is the CPU view's bitmap surface; surface_lock() in
// common.h is what makes that safe, and every use of it below says what it is covering.
// -----------------------------------------------------------------------------------------------

#include <jni.h>

#include <chrono>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "common.h"
#include "gpu_driver_gl.h"
#include "view_state.h"

using namespace voidul;

namespace {

ULView view_of(jlong handle) {
  ViewState* vs = reinterpret_cast<ViewState*>(handle);
  return vs ? vs->view : nullptr;
}

ViewState* state_of(jlong handle) { return reinterpret_cast<ViewState*>(handle); }

// -----------------------------------------------------------------------------------------------
// Frame profiler, off unless VOID_UI_PROFILE is set.
//
// Kept in the tree rather than rebuilt each time the overlay "feels slow": the numbers that matter
// are a stall histogram, not an average, and that took several rebuilds to learn. Wall clock only —
// an earlier version used clock(), which is CPU time across Ultralight's thread pool and made every
// rate it printed a fiction.
//
// Every field below is now written from two threads — update_ms and the render fields from the UI
// thread, the paint/upload fields and reset() from the GL thread — so every access to g_profile is
// made under surface_lock(). It is not atomics: the counters are read together to print one line,
// and a set of individually-atomic counters sampled at different instants is a line that never
// described any real frame.
// -----------------------------------------------------------------------------------------------
struct Profile {
  using Clock = std::chrono::steady_clock;

  bool enabled = getenv("VOID_UI_PROFILE") != nullptr;
  // VOID_UI_PROFILE=rects logs every damaged rectangle, not just the slow ones — the only way to
  // see which elements animate together, since the damage is a union and the union is the cost.
  bool all_rects = enabled && strcmp(getenv("VOID_UI_PROFILE"), "rects") == 0;
  Clock::time_point window_start{};
  int paints = 0;
  int uploads = 0;
  double update_ms = 0;
  double render_ms = 0;
  double upload_ms = 0;
  double peak_render_ms = 0;
  double dirty_coverage = 0;
  ULIntRect worst_dirty{0, 0, 0, 0};
  double worst_area = 0;
  std::vector<double> slow_frames;
  // Set when a render stalls (UI thread), read by the upload that follows it (GL thread). With the
  // two decoupled this is "the last slow render before this upload", which is no longer guaranteed
  // to be the render that produced the pixels being uploaded. Close enough to point at the
  // element; not evidence on its own.
  double pending_slow_ms = 0;
  // The wall time between one viewTextureId and the next: the game's frame, measured on the GL
  // thread. It used to include the UI work, because ulRender ran inline on this thread. It no
  // longer does, and the gap between this and peak_render_ms is exactly what moving the renderer
  // off the game thread bought. If the two converge again the UI thread is blocking the GL thread
  // on surface_lock() — i.e. it is rendering faster than the game can upload, and the fix is to
  // pace the UI loop, not to touch the lock.
  Clock::time_point last_paint{};
  double worst_frame_ms = 0;
  int frames_over_20ms = 0;

  static double ms_since(Clock::time_point t) {
    return std::chrono::duration<double, std::milli>(Clock::now() - t).count();
  }

  double due() {
    if (window_start == Clock::time_point{}) {
      window_start = Clock::now();
      return 0;
    }
    double elapsed = ms_since(window_start) / 1000.0;
    return elapsed >= 2.0 ? elapsed : 0;
  }

  void reset() {
    window_start = Clock::now();
    paints = uploads = 0;
    update_ms = render_ms = upload_ms = peak_render_ms = dirty_coverage = 0;
    worst_dirty = ULIntRect{0, 0, 0, 0};
    worst_area = 0;
    worst_frame_ms = 0;
    frames_over_20ms = 0;
    slow_frames.clear();
  }
};

Profile g_profile;

// Fires on every navigation: the window object is brand new, so the bridge has to go back on.
void on_window_object_ready(void* user_data, ULView caller, unsigned long long frame_id,
                            bool is_main_frame, ULString url) {
  if (!is_main_frame) return;
  install_message_bridge(static_cast<ViewState*>(user_data));
}

void on_console_message(void* user_data, ULView caller, ULMessageSource source, ULMessageLevel level,
                        ULString message, unsigned int line_number, unsigned int column_number,
                        ULString source_id) {
  // There are no devtools in game (PVP_ARCHITECTURE §9), so console output is the only channel
  // the UI has. Route it to the same log as everything else.
  const char* tag = level == kMessageLevel_Error     ? "error"
                    : level == kMessageLevel_Warning ? "warn"
                                                     : "log";
  fprintf(stderr, "[voidultralight/console/%s] %s (%s:%u)\n", tag, ulStringGetData(message),
          ulStringGetData(source_id), line_number);
  fflush(stderr);
}

void on_fail_loading(void* user_data, ULView caller, unsigned long long frame_id, bool is_main_frame,
                     ULString url, ULString description, ULString error_domain, int error_code) {
  log_error("load failed: %s (%s / %s, code %d)", ulStringGetData(url),
            ulStringGetData(description), ulStringGetData(error_domain), error_code);
}

// Number of live accelerated views. Zero means render() never has to touch GL.
int g_accelerated_views = 0;
bool g_gpu_failed = false;

} // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
  g().vm = vm;
  return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) { g().vm = nullptr; }

// ------------------------------------------------------------------------------------------------
// Library
// ------------------------------------------------------------------------------------------------
JNIEXPORT jstring JNICALL Java_dev_voidclient_ultralight_Native_version(JNIEnv* e, jclass) {
  return e->NewStringUTF(ulVersionString());
}

JNIEXPORT jstring JNICALL Java_dev_voidclient_ultralight_Native_webKitVersion(JNIEnv* e, jclass) {
  return e->NewStringUTF(ulWebKitVersionString());
}

JNIEXPORT jstring JNICALL Java_dev_voidclient_ultralight_Native_bindingVersion(JNIEnv* e, jclass) {
  return e->NewStringUTF(VOIDUL_VERSION " (Ultralight SDK " VOIDUL_ULTRALIGHT_SDK_VERSION ")");
}

// ------------------------------------------------------------------------------------------------
// Renderer
// ------------------------------------------------------------------------------------------------
JNIEXPORT jlong JNICALL Java_dev_voidclient_ultralight_Native_createRenderer(
    JNIEnv* e, jclass, jstring jprefix, jstring jnative_dir) {
  Globals& gl = g();
  if (gl.renderer) return reinterpret_cast<jlong>(gl.renderer);

  if (!gl.vm) e->GetJavaVM(&gl.vm);

  gl.classpath_prefix = to_utf8(e, jprefix);
  gl.native_dir = to_utf8(e, jnative_dir);
  if (!gl.native_dir.empty()) {
    char last = gl.native_dir[gl.native_dir.size() - 1];
    if (last != '/' && last != '\\') gl.native_dir += '/';
  }

  // Cache the classpath reader. FindClass here resolves through the calling class's loader, which
  // is the mod's — exactly the one that can see the UI bundle.
  if (!gl.resources_class) {
    jclass local = e->FindClass("dev/voidclient/ultralight/Resources");
    if (!local) {
      log_error("createRenderer: dev.voidclient.ultralight.Resources not found");
      return 0;
    }
    gl.resources_class = static_cast<jclass>(e->NewGlobalRef(local));
    e->DeleteLocalRef(local);
    gl.resources_read = e->GetStaticMethodID(gl.resources_class, "read", "(Ljava/lang/String;)[B");
    gl.resources_exists =
        e->GetStaticMethodID(gl.resources_class, "exists", "(Ljava/lang/String;)Z");
    if (!gl.resources_read || !gl.resources_exists) {
      log_error("createRenderer: Resources.read/exists not found");
      return 0;
    }
  }

  install_platform();

  // The GL driver is registered up front but only initialised on the first accelerated render:
  // its entry points come from the current context, and there is no context yet at mod init.
  ULGPUDriver driver = gpu::make_driver();
  ulPlatformSetGPUDriver(driver);

  ULConfig config = ulCreateConfig();
  ULString resource_prefix = ul_str("resources/");
  ulConfigSetResourcePathPrefix(config, resource_prefix);
  ulDestroyString(resource_prefix);
  // Counter-clockwise is Ultralight's default and matches GL's default front face.
  ulConfigSetFaceWinding(config, kFaceWinding_CounterClockwise);
  // Smooth, not Normal. Normal is full hinting, which the SDK documents as a balance struck "at
  // smaller font sizes": it distorts outlines onto the pixel grid, which on a 2x display is both
  // unnecessary and wrong — macOS itself does no hinting, which is why browser text looks the way
  // it does. It also snaps every glyph horizontally, so a run that shifts sub-pixel during a
  // transition re-snaps and the text visibly jumps. Smooth snaps vertically only and preserves
  // inter-glyph spacing.
  ulConfigSetFontHinting(config, kFontHinting_Smooth);
  // Incremental painting. A full repaint every frame was measured at 22 ms against 1-5 ms for
  // the incremental path — enough to take the game from ~100 fps to 33, which reads as the whole
  // interface being sluggish. If damage-rectangle artefacts reappear, fix the driver's handling
  // of them rather than paying this.
  ulConfigSetForceRepaint(config, false);
  // The timer knobs are deliberately left at their defaults, and this note exists so the next
  // person to chase "the menu feels like 20 fps" does not spend the afternoon here as well.
  // ulConfigSetMaxUpdateTime (default 1/200) and ulConfigSetAnimationTimerDelay (default 1/60)
  // both look like the culprit and neither is: raised to 1/60 and 1/120 respectively, measured in
  // game, the repaint rate did not move. The engine was never the limit — with a small damage
  // region this view sustains 74 repaints/s, and with game state pushes frozen it idles at 114 fps
  // with a 0.00 ms render. The stalls came from the page being told to re-render 20 times a second
  // by ticks whose values had not changed; the fix is in packages/ingame's store, not here.

  gl.renderer = ulCreateRenderer(config);
  ulDestroyConfig(config);

  if (!gl.renderer) {
    log_error("ulCreateRenderer failed (font loader or file system rejected?)");
    return 0;
  }
  log_info("Ultralight %s (WebKit %s) renderer up; classpath prefix '%s', natives '%s'",
           ulVersionString(), ulWebKitVersionString(), gl.classpath_prefix.c_str(),
           gl.native_dir.c_str());
  return reinterpret_cast<jlong>(gl.renderer);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_destroyRenderer(JNIEnv* e, jclass,
                                                                            jlong handle) {
  Globals& gl = g();
  ULRenderer r = reinterpret_cast<ULRenderer>(handle);
  if (!r) return;
  if (gpu::initialized()) gpu::shutdown();
  ulDestroyRenderer(r);
  if (gl.renderer == r) gl.renderer = nullptr;
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_rendererUpdate(JNIEnv* e, jclass,
                                                                           jlong handle) {
  ULRenderer r = reinterpret_cast<ULRenderer>(handle);
  if (!r) return;
  if (!g_profile.enabled) {
    ulUpdate(r);
    return;
  }
  auto t0 = Profile::Clock::now();
  ulUpdate(r);
  double ms = Profile::ms_since(t0);
  // ulUpdate writes no pixels, so it stays outside surface_lock(); only the counter needs it, and
  // only when profiling. Taking the lock around ulUpdate itself would stall the game thread's
  // upload behind timer and JS work that has nothing to do with the surface.
  std::lock_guard<std::mutex> guard(surface_lock());
  g_profile.update_ms += ms;
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_rendererRefreshDisplay(
    JNIEnv* e, jclass, jlong handle, jint display_id) {
  ULRenderer r = reinterpret_cast<ULRenderer>(handle);
  if (r) ulRefreshDisplay(r, static_cast<unsigned int>(display_id));
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_rendererRender(JNIEnv* e, jclass,
                                                                           jlong handle) {
  ULRenderer r = reinterpret_cast<ULRenderer>(handle);
  if (!r) return;

  if (g_accelerated_views == 0) {
    // CPU-only: no GL is touched at all, which is what the headless tests exercise.
    //
    // ulRender rasterises into every view's bitmap surface and updates its dirty bounds, which is
    // precisely what viewTextureId reads on the GL thread. The whole call is inside the lock; a
    // narrower region does not exist, because ulRender gives no hook between "started writing this
    // view" and "finished".
    std::lock_guard<std::mutex> guard(surface_lock());
    if (!g_profile.enabled) {
      ulRender(r);
      return;
    }
    auto t0 = Profile::Clock::now();
    ulRender(r);
    double ms = Profile::ms_since(t0);
    g_profile.render_ms += ms;
    if (ms > g_profile.peak_render_ms) g_profile.peak_render_ms = ms;
    // A full repaint of this surface measures ~49 ms against 2-5 ms incremental. Off the game
    // thread that is a dropped UI frame rather than a visible freeze, but it is still ~49 ms with
    // the lock held, so the next upload waits behind it: a stall here shows up on the game thread
    // as one late blit. The histogram is the signal; the mean hides it completely.
    if (ms > 10.0) {
      g_profile.slow_frames.push_back(ms);
      g_profile.pending_slow_ms = ms;
    }
    return;
  }

  if (g_gpu_failed) return;

  // Accelerated views render through our GL driver, so this whole branch already requires the GL
  // context and therefore the GL thread — the UI-thread split above does not apply to it. The lock
  // is still taken around ulRender so the two paths cannot disagree about what it protects if a
  // CPU and an accelerated view are ever alive at once.
  gpu::save_gl_state();
  if (!gpu::initialize()) {
    g_gpu_failed = true;
    gpu::restore_gl_state();
    log_error("GPU driver unavailable; accelerated views will not paint");
    return;
  }
  {
    std::lock_guard<std::mutex> guard(surface_lock());
    ulRender(r);
  }
  gpu::draw_command_list();
  gpu::restore_gl_state();
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_rendererPurgeMemory(JNIEnv* e, jclass,
                                                                                jlong handle) {
  ULRenderer r = reinterpret_cast<ULRenderer>(handle);
  if (r) ulPurgeMemory(r);
}

JNIEXPORT jlong JNICALL Java_dev_voidclient_ultralight_Native_createView(
    JNIEnv* e, jclass, jlong handle, jint width, jint height, jboolean transparent,
    jboolean accelerated) {
  ULRenderer r = reinterpret_cast<ULRenderer>(handle);
  if (!r) return 0;

  ULViewConfig cfg = ulCreateViewConfig();
  ulViewConfigSetIsAccelerated(cfg, accelerated == JNI_TRUE);
  ulViewConfigSetIsTransparent(cfg, transparent == JNI_TRUE);
  ulViewConfigSetInitialDeviceScale(cfg, 1.0);
  ulViewConfigSetInitialFocus(cfg, false);
  // ulRefreshDisplay(renderer, 0) is what advances CSS animations, and it only reaches views
  // whose display id matches. Pin it rather than relying on the default.
  ulViewConfigSetDisplayId(cfg, 0);
  ulViewConfigSetEnableImages(cfg, true);
  ulViewConfigSetEnableJavaScript(cfg, true);

  // Only one font ships with the binding, so point every generic family at it; anything else the
  // UI wants, it declares with @font-face and we serve from the classpath.
  ULString inter = ul_str("Inter");
  ulViewConfigSetFontFamilyStandard(cfg, inter);
  ulViewConfigSetFontFamilySansSerif(cfg, inter);
  ulViewConfigSetFontFamilySerif(cfg, inter);
  ulViewConfigSetFontFamilyFixed(cfg, inter);
  ulDestroyString(inter);

  ULView view = ulCreateView(r, static_cast<unsigned int>(width), static_cast<unsigned int>(height),
                             cfg, nullptr);
  ulDestroyViewConfig(cfg);
  if (!view) return 0;

  ViewState* vs = new ViewState();
  vs->view = view;
  vs->accelerated = accelerated == JNI_TRUE;
  if (vs->accelerated) ++g_accelerated_views;

  ulViewSetWindowObjectReadyCallback(view, on_window_object_ready, vs);
  ulViewSetAddConsoleMessageCallback(view, on_console_message, vs);
  ulViewSetFailLoadingCallback(view, on_fail_loading, vs);
  return reinterpret_cast<jlong>(vs);
}

// ------------------------------------------------------------------------------------------------
// View
// ------------------------------------------------------------------------------------------------
JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_destroyView(JNIEnv* e, jclass,
                                                                        jlong handle) {
  ViewState* vs = state_of(handle);
  if (!vs) return;
  release_message_bridge(vs);
  if (vs->view) ulDestroyView(vs->view);
  if (vs->accelerated && g_accelerated_views > 0) --g_accelerated_views;
  delete vs;
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewLoadUrl(JNIEnv* e, jclass,
                                                                        jlong handle, jstring jurl) {
  ULView v = view_of(handle);
  if (!v) return;
  ULString url = ul_str(to_utf8(e, jurl));
  ulViewLoadURL(v, url);
  ulDestroyString(url);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewLoadHtml(JNIEnv* e, jclass,
                                                                         jlong handle,
                                                                         jstring jhtml) {
  ULView v = view_of(handle);
  if (!v) return;
  ULString html = ul_str(to_utf8(e, jhtml));
  ulViewLoadHTML(v, html);
  ulDestroyString(html);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewResize(JNIEnv* e, jclass,
                                                                       jlong handle, jint w,
                                                                       jint h) {
  ULView v = view_of(handle);
  if (v) ulViewResize(v, static_cast<unsigned int>(w), static_cast<unsigned int>(h));
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewSetDeviceScale(JNIEnv* e, jclass,
                                                                               jlong handle,
                                                                               jdouble scale) {
  ULView v = view_of(handle);
  if (v) ulViewSetDeviceScale(v, scale);
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewTextureId(JNIEnv* e, jclass,
                                                                          jlong handle) {
  ViewState* vs = state_of(handle);
  ULView v = view_of(handle);
  if (!v) return 0;
  if (!ulViewIsAccelerated(v)) {
    // CPU path: Ultralight rasterised into a bitmap surface; hand Java a GL texture holding it.
    //
    // This is the one entry point in the file that may run on a thread other than the one driving
    // the renderer, so everything from here to ulSurfaceClearDirtyBounds is under the lock: the
    // dirty-bounds read, the profile counters, the glTexSubImage2D that reads the bitmap, and the
    // clear. The upload is inside it deliberately — it is the read that must not see a half-drawn
    // surface — which means a UI-thread ulRender waits for the upload. That is the intended trade:
    // the upload is bounded by the dirty rectangle and small, the render is not.
    std::lock_guard<std::mutex> guard(surface_lock());
    ULSurface surface = ulViewGetSurface(v);
    if (!surface) return 0;
    ULBitmap bitmap = ulBitmapSurfaceGetBitmap(surface);
    if (!bitmap) return 0;
    ULIntRect dirty = ulSurfaceGetDirtyBounds(surface);
    bool first = vs->cpu_texture == 0;
    bool dirty_now = first || !ulIntRectIsEmpty(dirty);
    if (g_profile.enabled) {
      unsigned w = ulBitmapGetWidth(bitmap), h = ulBitmapGetHeight(bitmap);
      ++g_profile.paints;
      if (g_profile.last_paint != Profile::Clock::time_point{}) {
        double frame = Profile::ms_since(g_profile.last_paint);
        if (frame > g_profile.worst_frame_ms) g_profile.worst_frame_ms = frame;
        if (frame > 20.0) ++g_profile.frames_over_20ms;
      }
      g_profile.last_paint = Profile::Clock::now();
      if (dirty_now) {
        ++g_profile.uploads;
        if (g_profile.all_rects) {
          log_info("  damage %dx%d at (%d,%d)", dirty.right - dirty.left,
                   dirty.bottom - dirty.top, dirty.left, dirty.top);
        }
        if (g_profile.pending_slow_ms > 0) {
          // The rectangle the stall actually redrew. A small text change has no business
          // dirtying anything but its own chip; anything panel-sized here is the real bug.
          log_info("  stall %.0fms redrew %dx%d at (%d,%d)", g_profile.pending_slow_ms,
                   dirty.right - dirty.left, dirty.bottom - dirty.top, dirty.left, dirty.top);
          g_profile.pending_slow_ms = 0;
        }
        // NB: this is a bounding box, not a region. Two small changes at opposite corners read
        // as near-full coverage, so treat it as "how far apart the damage is", never as area.
        double area = double(dirty.right - dirty.left) * double(dirty.bottom - dirty.top);
        if (w && h) g_profile.dirty_coverage += area / (double(w) * double(h));
        if (area > g_profile.worst_area) {
          g_profile.worst_area = area;
          g_profile.worst_dirty = dirty;
        }
      }
      if (double secs = g_profile.due()) {
        const Profile& p = g_profile;
        log_info("surface %ux%u: %.0f paints/s, %.0f uploads/s | update %.2f, render %.2f "
                 "(peak %.1f), upload %.2f ms/frame | damage bbox %.0f%%",
                 w, h, p.paints / secs, p.uploads / secs,
                 p.paints ? p.update_ms / p.paints : 0.0,
                 p.paints ? p.render_ms / p.paints : 0.0, p.peak_render_ms,
                 p.paints ? p.upload_ms / p.paints : 0.0,
                 p.uploads ? 100.0 * p.dirty_coverage / p.uploads : 0.0);
        log_info("  game frame: worst %.0f ms, %d of %d over 20 ms (%.0f fps mean)",
                 p.worst_frame_ms, p.frames_over_20ms, p.paints, p.paints / secs);
        if (!p.slow_frames.empty()) {
          std::string list;
          for (size_t i = 0; i < p.slow_frames.size() && i < 14; ++i) {
            char buf[24];
            snprintf(buf, sizeof(buf), "%s%.0f", i ? " " : "", p.slow_frames[i]);
            list += buf;
          }
          log_info("  stalls over 10ms: %zu of %d frames [%s]", p.slow_frames.size(), p.paints,
                   list.c_str());
        }
        g_profile.reset();
      }
    }
    if (dirty_now) {
      auto t0 = Profile::Clock::now();
      vs->cpu_texture = gpu::upload_surface(vs->cpu_texture, vs->cpu_texture_width,
                                            vs->cpu_texture_height, bitmap, dirty);
      if (g_profile.enabled) g_profile.upload_ms += Profile::ms_since(t0);
      ulSurfaceClearDirtyBounds(surface);
    }
    return static_cast<jint>(vs->cpu_texture);
  }
  ULRenderTarget rt = ulViewGetRenderTarget(v);
  if (rt.is_empty) return 0;
  {
    // One line per distinct render target. If the texture is bigger than the view, the uv
    // coordinates are the only thing that keeps the blit from magnifying a sub-rectangle.
    static unsigned last_w = 0, last_h = 0;
    if (rt.width != last_w || rt.height != last_h) {
      last_w = rt.width;
      last_h = rt.height;
      log_info("render target: view %ux%u, texture %ux%u, uv %.4f x %.4f", rt.width, rt.height,
               rt.texture_width, rt.texture_height, rt.uv_coords.right, rt.uv_coords.bottom);
    }
  }
  // rt.texture_id is the driver's own id, not a GL name — Java binds the result directly.
  return static_cast<jint>(voidul::gpu::gl_texture_for(rt.texture_id));
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewTextureWidth(JNIEnv* e, jclass,
                                                                             jlong handle) {
  ULView v = view_of(handle);
  if (!v) return 0;
  if (!ulViewIsAccelerated(v)) return static_cast<jint>(ulViewGetWidth(v));
  return static_cast<jint>(ulViewGetRenderTarget(v).texture_width);
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewTextureHeight(JNIEnv* e, jclass,
                                                                              jlong handle) {
  ULView v = view_of(handle);
  if (!v) return 0;
  if (!ulViewIsAccelerated(v)) return static_cast<jint>(ulViewGetHeight(v));
  return static_cast<jint>(ulViewGetRenderTarget(v).texture_height);
}

// uvScaleX/Y are the exception to "UI thread only" for CPU views: ulViewIsAccelerated reads a flag
// fixed at creation, so the early return never enters the engine and the GL thread may ask.
JNIEXPORT jfloat JNICALL Java_dev_voidclient_ultralight_Native_viewUvScaleX(JNIEnv* e, jclass,
                                                                           jlong handle) {
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 1.0f;
  if (!ulViewIsAccelerated(v)) return 1.0f;
  return ulViewGetRenderTarget(v).uv_coords.right;
}

JNIEXPORT jfloat JNICALL Java_dev_voidclient_ultralight_Native_viewUvScaleY(JNIEnv* e, jclass,
                                                                           jlong handle) {
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 1.0f;
  if (!ulViewIsAccelerated(v)) return 1.0f;
  return ulViewGetRenderTarget(v).uv_coords.bottom;
}

JNIEXPORT jboolean JNICALL Java_dev_voidclient_ultralight_Native_viewIsDirty(JNIEnv* e, jclass,
                                                                            jlong handle) {
  ViewState* vs = state_of(handle);
  if (!vs || !vs->view) return JNI_FALSE;
  if (vs->accelerated) return ulViewGetNeedsPaint(vs->view) ? JNI_TRUE : JNI_FALSE;
  // Either the page wants repainting, or it has already painted pixels we have not uploaded.
  // Only the first can be true before render(); only the second after it.
  if (ulViewGetNeedsPaint(vs->view)) return JNI_TRUE;
  ULSurface s = ulViewGetSurface(vs->view);
  if (!s) return JNI_FALSE;
  ULIntRect r = ulSurfaceGetDirtyBounds(s);
  return (r.right > r.left && r.bottom > r.top) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewSetNeedsPaint(JNIEnv* e, jclass,
                                                                              jlong handle,
                                                                              jboolean needsPaint) {
  ULView v = view_of(handle);
  if (v) ulViewSetNeedsPaint(v, needsPaint == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewFireMouseEvent(
    JNIEnv* e, jclass, jlong handle, jint type, jint x, jint y, jint button) {
  ULView v = view_of(handle);
  if (!v) return;
  ULMouseEventType t = type == 1   ? kMouseEventType_MouseDown
                       : type == 2 ? kMouseEventType_MouseUp
                                   : kMouseEventType_MouseMoved;
  ULMouseButton b = button == 1   ? kMouseButton_Left
                    : button == 2 ? kMouseButton_Middle
                    : button == 3 ? kMouseButton_Right
                                  : kMouseButton_None;
  ULMouseEvent ev = ulCreateMouseEvent(t, x, y, b);
  ulViewFireMouseEvent(v, ev);
  ulDestroyMouseEvent(ev);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewFireKeyEvent(
    JNIEnv* e, jclass, jlong handle, jint type, jint virtual_key, jint modifiers, jstring jtext) {
  ULView v = view_of(handle);
  if (!v) return;

  // 0 -> RawKeyDown, not KeyDown: KeyDown does not trigger accelerator commands in WebCore, and
  // RawKeyDown is what a physical press is supposed to be.
  ULKeyEventType t = type == 1   ? kKeyEventType_KeyUp
                     : type == 2 ? kKeyEventType_Char
                                 : kKeyEventType_RawKeyDown;

  std::string text = to_utf8(e, jtext);
  ULString ul_text = ul_str(text);
  // Modifier bits are identical on both sides (1 alt, 2 ctrl, 4 meta, 8 shift), so no mapping.
  ULKeyEvent ev = ulCreateKeyEvent(t, static_cast<unsigned int>(modifiers), virtual_key,
                                   /*native_key_code=*/0, ul_text, ul_text,
                                   /*is_keypad=*/false, /*is_auto_repeat=*/false,
                                   /*is_system_key=*/false);
  ulViewFireKeyEvent(v, ev);
  ulDestroyKeyEvent(ev);
  ulDestroyString(ul_text);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewFireScrollEvent(JNIEnv* e, jclass,
                                                                                jlong handle,
                                                                                jint dx, jint dy) {
  ULView v = view_of(handle);
  if (!v) return;
  ULScrollEvent ev = ulCreateScrollEvent(kScrollEventType_ScrollByPixel, dx, dy);
  ulViewFireScrollEvent(v, ev);
  ulDestroyScrollEvent(ev);
}

JNIEXPORT jstring JNICALL Java_dev_voidclient_ultralight_Native_viewEvaluateScript(JNIEnv* e, jclass,
                                                                                  jlong handle,
                                                                                  jstring jjs) {
  ViewState* vs = state_of(handle);
  if (!vs) return e->NewStringUTF("");
  std::string result = evaluate_script(vs, to_utf8(e, jjs));
  return e->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewSetMessageHandler(
    JNIEnv* e, jclass, jlong handle, jobject handler) {
  ViewState* vs = state_of(handle);
  if (!vs) return;

  if (vs->handler) {
    e->DeleteGlobalRef(vs->handler);
    vs->handler = nullptr;
    vs->handler_apply = nullptr;
  }
  if (!handler) return;

  vs->handler = e->NewGlobalRef(handler);
  jclass fn = e->FindClass("java/util/function/Function");
  if (!fn) {
    log_error("setMessageHandler: java.util.function.Function not found");
    return;
  }
  vs->handler_apply = e->GetMethodID(fn, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
  e->DeleteLocalRef(fn);
  if (!vs->handler_apply) {
    log_error("setMessageHandler: Function.apply not found");
    return;
  }
  // Install now as well as on the next navigation: the page may already be loaded.
  install_message_bridge(vs);
}

JNIEXPORT void JNICALL Java_dev_voidclient_ultralight_Native_viewSetFocus(JNIEnv* e, jclass,
                                                                         jlong handle,
                                                                         jboolean focus) {
  ULView v = view_of(handle);
  if (!v) return;
  if (focus == JNI_TRUE)
    ulViewFocus(v);
  else
    ulViewUnfocus(v);
}

JNIEXPORT jboolean JNICALL Java_dev_voidclient_ultralight_Native_viewHasInputFocus(JNIEnv* e, jclass,
                                                                                  jlong handle) {
  ULView v = view_of(handle);
  return (v && ulViewHasInputFocus(v)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_voidclient_ultralight_Native_viewIsLoading(JNIEnv* e, jclass,
                                                                              jlong handle) {
  ULView v = view_of(handle);
  return (v && ulViewIsLoading(v)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewWidth(JNIEnv* e, jclass,
                                                                      jlong handle) {
  ULView v = view_of(handle);
  return v ? static_cast<jint>(ulViewGetWidth(v)) : 0;
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewHeight(JNIEnv* e, jclass,
                                                                       jlong handle) {
  ULView v = view_of(handle);
  return v ? static_cast<jint>(ulViewGetHeight(v)) : 0;
}

JNIEXPORT jbyteArray JNICALL Java_dev_voidclient_ultralight_Native_viewReadPixels(JNIEnv* e, jclass,
                                                                                 jlong handle) {
  ULView v = view_of(handle);
  if (!v) return nullptr;
  ULSurface surface = ulViewGetSurface(v);
  if (!surface) return nullptr;

  unsigned int w = ulSurfaceGetWidth(surface);
  unsigned int h = ulSurfaceGetHeight(surface);
  unsigned int row_bytes = ulSurfaceGetRowBytes(surface);
  if (w == 0 || h == 0) return nullptr;

  unsigned char* pixels = static_cast<unsigned char*>(ulSurfaceLockPixels(surface));
  if (!pixels) return nullptr;

  const jsize out_size = static_cast<jsize>(w) * static_cast<jsize>(h) * 4;
  jbyteArray out = e->NewByteArray(out_size);
  if (out) {
    // Repack from the surface's (possibly padded) stride to a tight width*4 stride.
    for (unsigned int y = 0; y < h; ++y) {
      e->SetByteArrayRegion(out, static_cast<jsize>(y) * static_cast<jsize>(w) * 4,
                            static_cast<jsize>(w) * 4,
                            reinterpret_cast<const jbyte*>(pixels + y * row_bytes));
    }
  }
  ulSurfaceUnlockPixels(surface);
  ulSurfaceClearDirtyBounds(surface);
  return out;
}

} // extern "C"
