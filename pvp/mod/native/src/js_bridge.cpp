// window.__void_native — the synchronous JS -> Java call the in-game bridge is built on.
//
// Ultralight runs inside the JVM, so this is a plain function call across the JNI boundary, on
// whichever thread invoked update()/render(). No queue, no ack, no request id (PVP_ARCHITECTURE
// §6.5): JS calls, Java answers, the answer is the return value.

#include "view_state.h"

#include <vector>

#include "common.h"

namespace voidul {
namespace {

std::string js_to_utf8(JSContextRef ctx, JSValueRef value) {
  if (!value || JSValueIsUndefined(ctx, value) || JSValueIsNull(ctx, value)) return std::string();
  JSValueRef exc = nullptr;
  JSStringRef str = JSValueToStringCopy(ctx, value, &exc);
  if (!str) return std::string();
  size_t max = JSStringGetMaximumUTF8CStringSize(str);
  std::vector<char> buf(max + 1, 0);
  size_t written = JSStringGetUTF8CString(str, buf.data(), buf.size());
  JSStringRelease(str);
  // `written` includes the null terminator.
  return std::string(buf.data(), written > 0 ? written - 1 : 0);
}

JSValueRef make_js_string(JSContextRef ctx, const std::string& s) {
  JSStringRef js = JSStringCreateWithUTF8CString(s.c_str());
  JSValueRef v = JSValueMakeString(ctx, js);
  JSStringRelease(js);
  return v;
}

// The JS -> Java call itself. Runs on the caller's thread, which is a Java thread (the render
// thread) whenever this is reached from update()/render().
JSValueRef on_native_call(JSContextRef ctx, JSObjectRef function, JSObjectRef this_object,
                          size_t argc, const JSValueRef args[], JSValueRef* exception) {
  ViewState* vs = static_cast<ViewState*>(JSObjectGetPrivate(function));
  if (!vs || !vs->handler) return JSValueMakeUndefined(ctx);

  std::string payload = argc > 0 ? js_to_utf8(ctx, args[0]) : std::string();

  JNIEnv* e = env();
  if (!e) {
    log_error("__void_native: no JNIEnv on this thread");
    return JSValueMakeUndefined(ctx);
  }

  jstring jin = e->NewStringUTF(payload.c_str());
  jobject jout = e->CallObjectMethod(vs->handler, vs->handler_apply, jin);
  e->DeleteLocalRef(jin);

  if (e->ExceptionCheck()) {
    // A throwing handler must not take the render thread down with it: report and answer null.
    e->ExceptionDescribe();
    e->ExceptionClear();
    if (jout) e->DeleteLocalRef(jout);
    JSStringRef msg = JSStringCreateWithUTF8CString("void: native message handler threw");
    if (exception) *exception = JSValueMakeString(ctx, msg);
    JSStringRelease(msg);
    return JSValueMakeUndefined(ctx);
  }

  if (!jout) return JSValueMakeNull(ctx);
  std::string reply = to_utf8(e, static_cast<jstring>(jout));
  e->DeleteLocalRef(jout);
  return make_js_string(ctx, reply);
}

} // namespace

void install_message_bridge(ViewState* vs) {
  if (!vs || !vs->view || !vs->handler) return;

  JSContextRef ctx = ulViewLockJSContext(vs->view);
  if (!ctx) return;

  if (!vs->bridge_class) {
    JSClassDefinition def = kJSClassDefinitionEmpty;
    def.className = "VoidNativeBridge";
    def.callAsFunction = on_native_call;
    vs->bridge_class = JSClassCreate(&def);
  }

  JSObjectRef fn = JSObjectMake(ctx, vs->bridge_class, vs);
  JSStringRef name = JSStringCreateWithUTF8CString("__void_native");
  JSObjectRef global = JSContextGetGlobalObject(ctx);
  JSValueRef exc = nullptr;
  JSObjectSetProperty(ctx, global, name, fn,
                      kJSPropertyAttributeDontDelete | kJSPropertyAttributeReadOnly, &exc);
  JSStringRelease(name);
  if (exc) log_error("install_message_bridge: could not define window.__void_native");

  ulViewUnlockJSContext(vs->view);
}

void release_message_bridge(ViewState* vs) {
  if (!vs) return;
  if (vs->bridge_class) {
    JSClassRelease(vs->bridge_class);
    vs->bridge_class = nullptr;
  }
  if (vs->handler) {
    JNIEnv* e = env();
    if (e) e->DeleteGlobalRef(vs->handler);
    vs->handler = nullptr;
    vs->handler_apply = nullptr;
  }
}

std::string evaluate_script(ViewState* vs, const std::string& js) {
  if (!vs || !vs->view) return std::string();

  JSContextRef ctx = ulViewLockJSContext(vs->view);
  if (!ctx) return std::string();

  JSStringRef script = JSStringCreateWithUTF8CString(js.c_str());
  JSValueRef exc = nullptr;
  JSValueRef result = JSEvaluateScript(ctx, script, nullptr, nullptr, 0, &exc);
  JSStringRelease(script);

  std::string out;
  if (exc) {
    // Contract: exceptions never propagate to Java. Log and answer "".
    log_error("evaluateScript threw: %s", js_to_utf8(ctx, exc).c_str());
  } else if (result && !JSValueIsUndefined(ctx, result)) {
    out = js_to_utf8(ctx, result);
  }

  ulViewUnlockJSContext(vs->view);
  return out;
}

} // namespace voidul
