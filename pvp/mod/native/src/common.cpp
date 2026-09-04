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

JNIEnv* env() {
  Globals& gl = g();
  if (!gl.vm) return nullptr;
  JNIEnv* e = nullptr;
  jint rc = gl.vm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6);
  if (rc == JNI_EDETACHED) {
    // Ultralight's renderer threads are not Java threads; attach as daemon so the JVM can still
    // exit, and leave them attached (attach/detach per call would cost more than the read).
#ifdef __ANDROID__
    if (gl.vm->AttachCurrentThreadAsDaemon(&e, nullptr) != JNI_OK) return nullptr;
#else
    if (gl.vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&e), nullptr) != JNI_OK)
      return nullptr;
#endif
  } else if (rc != JNI_OK) {
    return nullptr;
  }
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
