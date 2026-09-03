// JNI entry points for dev.voidclient.ultralight.Native.
//
// Handles are raw pointers boxed as jlong. There is no locking: everything here must be called
// from the thread that created the renderer (Minecraft's render thread), which is also the thread
// Ultralight's JS runs on. That is the whole point — key press to pixel in the same frame.

#include <jni.h>

#include <cstring>
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
  ulConfigSetFontHinting(config, kFontHinting_Normal);
  ulConfigSetForceRepaint(config, false);

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
  if (r) ulUpdate(r);
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
    ulRender(r);
    return;
  }

  if (g_gpu_failed) return;

  gpu::save_gl_state();
  if (!gpu::initialize()) {
    g_gpu_failed = true;
    gpu::restore_gl_state();
    log_error("GPU driver unavailable; accelerated views will not paint");
    return;
  }
  ulRender(r);
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
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 0;
  ULRenderTarget rt = ulViewGetRenderTarget(v);
  return rt.is_empty ? 0 : static_cast<jint>(rt.texture_id);
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewTextureWidth(JNIEnv* e, jclass,
                                                                             jlong handle) {
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 0;
  return static_cast<jint>(ulViewGetRenderTarget(v).texture_width);
}

JNIEXPORT jint JNICALL Java_dev_voidclient_ultralight_Native_viewTextureHeight(JNIEnv* e, jclass,
                                                                              jlong handle) {
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 0;
  return static_cast<jint>(ulViewGetRenderTarget(v).texture_height);
}

JNIEXPORT jfloat JNICALL Java_dev_voidclient_ultralight_Native_viewUvScaleX(JNIEnv* e, jclass,
                                                                           jlong handle) {
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 1.0f;
  return ulViewGetRenderTarget(v).uv_coords.right;
}

JNIEXPORT jfloat JNICALL Java_dev_voidclient_ultralight_Native_viewUvScaleY(JNIEnv* e, jclass,
                                                                           jlong handle) {
  ULView v = view_of(handle);
  if (!v || !ulViewIsAccelerated(v)) return 1.0f;
  return ulViewGetRenderTarget(v).uv_coords.bottom;
}

JNIEXPORT jboolean JNICALL Java_dev_voidclient_ultralight_Native_viewIsDirty(JNIEnv* e, jclass,
                                                                            jlong handle) {
  ViewState* vs = state_of(handle);
  if (!vs || !vs->view) return JNI_FALSE;
  if (vs->accelerated) return ulViewGetNeedsPaint(vs->view) ? JNI_TRUE : JNI_FALSE;
  ULSurface s = ulViewGetSurface(vs->view);
  if (!s) return JNI_FALSE;
  ULIntRect r = ulSurfaceGetDirtyBounds(s);
  return (r.right > r.left && r.bottom > r.top) ? JNI_TRUE : JNI_FALSE;
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
