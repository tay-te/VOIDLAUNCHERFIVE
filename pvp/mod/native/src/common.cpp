#include "common.h"

#include <cstdio>
#include <cstring>
#include <fstream>
#include <vector>

namespace voidul {

Globals& g() {
  static Globals instance;
  return instance;
}

std::mutex& surface_lock() {
  static std::mutex m;
  return m;
}

namespace {

// The per-thread JNIEnv, and the detach that has to happen when the thread ends.
//
// `owner` is the "we attached this one" flag as well as the VM handle: it is left null for a Java
// thread, whose attachment belongs to the JVM for the thread's whole life, and set only when this
// code called AttachCurrentThreadAsDaemon itself. Holding the JavaVM* here rather than reaching
// for g() in the destructor keeps the teardown independent of static destruction order — a
// renderer thread can outlive Globals.
struct ThreadEnv {
  JNIEnv* env = nullptr;
  JavaVM* owner = nullptr;

  ~ThreadEnv() {
    if (!owner) return;
    JavaVM* vm = owner;
    // Cleared before the detach, not after: if anything reaches env() again while the thread is
    // being torn down (Ultralight's own thread-local destructors run in an order we do not
    // control), it must see "not attached" rather than a JNIEnv for a thread that is half gone.
    owner = nullptr;
    env = nullptr;
    vm->DetachCurrentThread();
  }
};

thread_local ThreadEnv t_env;

} // namespace

JNIEnv* env() {
  if (t_env.env) return t_env.env;

  JavaVM* vm = g().vm;
  if (!vm) return nullptr;

  JNIEnv* e = nullptr;
  jint rc = vm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6);
  if (rc == JNI_OK) {
    // A Java thread — the UI thread that drives the Renderer, or Minecraft's render thread. It is
    // attached for as long as it lives, so cache it and never detach it.
    t_env.env = e;
    return e;
  }
  if (rc != JNI_EDETACHED) return nullptr;

  // A bare pthread: one of Ultralight's renderer threads reaching the ULFileSystem or the font
  // loader. Daemon, so the attachment cannot stop the JVM exiting.
#ifdef __ANDROID__
  if (vm->AttachCurrentThreadAsDaemon(&e, nullptr) != JNI_OK) return nullptr;
#else
  if (vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&e), nullptr) != JNI_OK)
    return nullptr;
#endif
  t_env.env = e;
  t_env.owner = vm;
  return e;
}

static void vlog(const char* level, const char* fmt, va_list ap) {
  char buf[2048];
  vsnprintf(buf, sizeof(buf), fmt, ap);
  fprintf(stderr, "[voidultralight/%s] %s\n", level, buf);
  fflush(stderr);
}

void log_info(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  vlog("info", fmt, ap);
  va_end(ap);
}

void log_error(const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  vlog("error", fmt, ap);
  va_end(ap);
}

std::string to_utf8(JNIEnv* e, jstring jstr) {
  if (!e || !jstr) return std::string();
  const char* chars = e->GetStringUTFChars(jstr, nullptr);
  if (!chars) return std::string();
  std::string out(chars);
  e->ReleaseStringUTFChars(jstr, chars);
  return out;
}

jstring to_jstring(JNIEnv* e, const std::string& s) { return e->NewStringUTF(s.c_str()); }

ULString ul_str(const std::string& s) { return ulCreateStringUTF8(s.data(), s.size()); }

std::string from_ul(ULString s) {
  if (!s) return std::string();
  const char* data = ulStringGetData(s);
  size_t len = ulStringGetLength(s);
  return data ? std::string(data, len) : std::string();
}

bool read_classpath(const std::string& path, std::string* out) {
  Globals& gl = g();
  if (!gl.resources_class || !gl.resources_read) return false;
  JNIEnv* e = env();
  if (!e) return false;

  jstring jpath = e->NewStringUTF(path.c_str());
  if (!jpath) return false;
  jobject result =
      e->CallStaticObjectMethod(gl.resources_class, gl.resources_read, jpath);
  e->DeleteLocalRef(jpath);
  if (e->ExceptionCheck()) {
    e->ExceptionDescribe();
    e->ExceptionClear();
    return false;
  }
  if (!result) return false;

  jbyteArray arr = static_cast<jbyteArray>(result);
  jsize len = e->GetArrayLength(arr);
  if (out) {
    out->resize(static_cast<size_t>(len));
    if (len > 0) e->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&(*out)[0]));
  }
  e->DeleteLocalRef(result);
  return true;
}

bool exists_classpath(const std::string& path) {
  Globals& gl = g();
  if (!gl.resources_class || !gl.resources_exists) return false;
  JNIEnv* e = env();
  if (!e) return false;
  jstring jpath = e->NewStringUTF(path.c_str());
  jboolean found = e->CallStaticBooleanMethod(gl.resources_class, gl.resources_exists, jpath);
  e->DeleteLocalRef(jpath);
  if (e->ExceptionCheck()) {
    e->ExceptionDescribe();
    e->ExceptionClear();
    return false;
  }
  return found == JNI_TRUE;
}

bool read_disk(const std::string& path, std::string* out) {
  std::ifstream f(path.c_str(), std::ios::binary);
  if (!f.good()) return false;
  f.seekg(0, std::ios::end);
  std::streamoff size = f.tellg();
  if (size < 0) return false;
  f.seekg(0, std::ios::beg);
  if (out) {
    out->resize(static_cast<size_t>(size));
    if (size > 0) f.read(&(*out)[0], size);
    if (!f) return false;
  }
  return true;
}

bool exists_disk(const std::string& path) {
  std::ifstream f(path.c_str(), std::ios::binary);
  return f.good();
}

} // namespace voidul
