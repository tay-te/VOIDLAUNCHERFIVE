// void-pvp / mod/native — shared plumbing for the Ultralight JNI binding.
#pragma once

#include <jni.h>

#include <cstdarg>
#include <cstdint>
#include <mutex>
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

// The JNIEnv for the calling thread, attaching it to the JVM as a daemon on first use.
//
// Four kinds of thread reach this and they need different treatment. The UI thread that owns the
// Renderer and Minecraft's render thread are Java threads: already attached, and detaching them
// would be pulling an attachment out from under the JVM. Ultralight's own renderer threads
// (Config.num_renderer_threads, auto — 3 on a 4-core box) call the ULFileSystem, and the font
// loader can run on any of them; those are bare pthreads and have to be attached here.
//
// Daemon, because a normal attachment keeps the JVM alive and those threads outlive anything we
// control. Cached thread_local, because the alternative — attach/detach per call — costs more than
// the classpath read it wraps. Detached when the thread exits, and only for the threads we
// attached ourselves: an attached native thread that dies still attached leaves the JVM holding a
// JNIEnv for a thread that no longer exists, which surfaces later as a crash somewhere unrelated.
JNIEnv* env();

// ---------------------------------------------------------------------------------------------
// The surface lock — held whenever Ultralight's pixels are being written or read.
//
// Ultralight's CPU renderer rasterises into each View's bitmap surface. With ulRender on a
// dedicated UI thread and the texture upload still on the thread that owns the GL context, those
// two touch the same bitmap concurrently. Both halves of that go wrong quietly: an upload that
// reads mid-render tears (a band of the previous frame inside an otherwise current one), and a
// ulSurfaceClearDirtyBounds that lands while a render is in flight discards damage that was never
// uploaded — that region then stops updating entirely until something else happens to dirty it,
// which reads as a frozen widget rather than as a glitch.
//
// One mutex for the whole process rather than one per View, because ulRender paints *every* view
// in a single call. A per-view lock would have to be acquired as the union of them all before
// ulRender and released after it, which is this mutex with more bookkeeping.
std::mutex& surface_lock();

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
