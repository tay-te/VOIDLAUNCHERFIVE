// void-pvp / mod/native — shared plumbing for the Ultralight JNI binding.
#pragma once

#include <jni.h>

#include <cstdarg>
#include <cstdint>
#include <string>

#include <Ultralight/CAPI.h>

namespace voidul {

// ---------------------------------------------------------------------------------------------
// Process-wide state.  Ultralight's Platform singleton is process-wide too, so there is exactly
// one of these; `Ultralight.createRenderer` is documented as callable once.
// ---------------------------------------------------------------------------------------------
struct Globals {
  JavaVM* vm = nullptr;

  // dev.void.ultralight.Resources — a tiny Java helper we call to read classpath entries.
  jclass resources_class = nullptr;     // global ref
  jmethodID resources_read = nullptr;   // static byte[] read(String)
  jmethodID resources_exists = nullptr; // static boolean exists(String)

  // Classpath prefix for `file:///…` URLs, e.g. "assets/void/ui/". Always ends with '/'.
  std::string classpath_prefix;

  // On-disk directory the natives were extracted to; holds resources/cacert.pem,
  // resources/icudt67l.dat and resources/fonts/. Always ends with a path separator.
  std::string native_dir;

  bool platform_ready = false;
  ULRenderer renderer = nullptr;
};

Globals& g();

// Attaches the calling thread to the JVM as a daemon if it is not attached already. Ultralight
// may call the FileSystem from its own renderer threads, so this has to be safe off-thread.
// The attachment is intentionally never torn down: daemon threads do not hold the JVM open.
JNIEnv* env();

void log_info(const char* fmt, ...);
void log_error(const char* fmt, ...);

// Java <-> std::string. `jstr` may be null (yields an empty string).
std::string to_utf8(JNIEnv* e, jstring jstr);
jstring to_jstring(JNIEnv* e, const std::string& s);

// ULString helpers. `ul_str` creates a new ULString the caller must destroy.
ULString ul_str(const std::string& s);
std::string from_ul(ULString s);

// Reads a classpath resource through dev.void.ultralight.Resources.
// Returns false if the resource does not exist.
bool read_classpath(const std::string& path, std::string* out);
bool exists_classpath(const std::string& path);

// Reads a file from disk. Returns false if it cannot be opened.
bool read_disk(const std::string& path, std::string* out);
bool exists_disk(const std::string& path);

// Installs the Platform handlers (logger, file system, font loader). Idempotent.
void install_platform();

// Builds the ULFileSystem struct (platform.cpp).
ULFileSystem make_file_system();

// Installs the bundled font loader through the C++ Platform singleton (font_loader.cpp).
// The C entry point ulPlatformSetFontLoader does not exist in Ultralight 1.4.0b — see that file.
void install_font_loader();

} // namespace voidul
