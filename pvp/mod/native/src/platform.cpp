// Platform handlers: the logger and the file system. (The font loader lives in font_loader.cpp;
// its C entry point is missing from the 1.4.0b SDK, so it takes a different route.)
//
// AppCore ships stock implementations of all of these, but we do not link AppCore: on Linux it
// pulls in GTK3, on Windows D3D11 + DirectWrite, and on every platform it owns window and run-loop
// creation — which is precisely what Minecraft already provides. So these are ours.

#include "common.h"

#include <algorithm>
#include <cstring>
#include <string>

namespace voidul {
namespace {

// ---------------------------------------------------------------------------------------------
// Logger
// ---------------------------------------------------------------------------------------------
void on_log_message(ULLogLevel level, ULString message) {
  const char* tag = level == kLogLevel_Error     ? "ul/error"
                    : level == kLogLevel_Warning ? "ul/warn"
                                                 : "ul/info";
  fprintf(stderr, "[voidultralight/%s] %s\n", tag, ulStringGetData(message));
  fflush(stderr);
}

// ---------------------------------------------------------------------------------------------
// File system
//
// Ultralight asks for two disjoint kinds of path:
//
//   * `resources/…`  — its own runtime data (cacert.pem, icudt67l.dat). Set by
//                      ulConfigSetResourcePathPrefix; we leave it at the default "resources/".
//   * everything else — whatever `file:///…` URL the page loaded, relative to the classpath
//                      prefix the host passed to Ultralight.createRenderer (e.g. "assets/void/ui/").
//
// Both are served here. The classpath is tried first (that is where the in-game bundle lives),
// then the directory the natives were extracted to (that is where the SDK resources and the
// fallback font live). Either can satisfy either kind of path, which keeps dev setups — running
// straight out of a build tree with no JAR — working.
// ---------------------------------------------------------------------------------------------

std::string normalize(ULString path) {
  std::string p = from_ul(path);
  // Ultralight hands us the URL path; depending on the URL form it may or may not be rooted.
  while (!p.empty() && (p[0] == '/' || p[0] == '\\')) p.erase(0, 1);
  if (p.rfind("./", 0) == 0) p.erase(0, 2);
  // Refuse traversal out of the prefix: a page must not be able to read arbitrary files.
  if (p.find("..") != std::string::npos) return std::string();
  return p;
}

enum class Where { None, Classpath, Disk };

Where resolve(const std::string& rel, std::string* full) {
  Globals& gl = g();
  if (rel.empty()) return Where::None;
  if (!gl.classpath_prefix.empty()) {
    std::string cp = gl.classpath_prefix + rel;
    if (exists_classpath(cp)) {
      if (full) *full = cp;
      return Where::Classpath;
    }
  }
  if (!gl.native_dir.empty()) {
    std::string disk = gl.native_dir + rel;
    if (exists_disk(disk)) {
      if (full) *full = disk;
      return Where::Disk;
    }
  }
  return Where::None;
}

bool fs_file_exists(ULString path) {
  std::string rel = normalize(path);
  return resolve(rel, nullptr) != Where::None;
}

const char* mime_for(const std::string& path) {
  size_t dot = path.find_last_of('.');
  if (dot == std::string::npos) return "application/unknown";
  std::string ext = path.substr(dot + 1);
  std::transform(ext.begin(), ext.end(), ext.begin(),
                 [](unsigned char c) { return static_cast<char>(::tolower(c)); });
  if (ext == "html" || ext == "htm") return "text/html";
  if (ext == "css") return "text/css";
  if (ext == "js" || ext == "mjs") return "application/javascript";
  if (ext == "json" || ext == "map") return "application/json";
  if (ext == "png") return "image/png";
  if (ext == "jpg" || ext == "jpeg") return "image/jpeg";
  if (ext == "gif") return "image/gif";
  if (ext == "webp") return "image/webp";
  if (ext == "svg") return "image/svg+xml";
  if (ext == "ico") return "image/x-icon";
  if (ext == "woff") return "font/woff";
  if (ext == "woff2") return "font/woff2";
  if (ext == "ttf") return "font/ttf";
  if (ext == "otf") return "font/otf";
  if (ext == "txt") return "text/plain";
  if (ext == "xml") return "text/xml";
  if (ext == "wasm") return "application/wasm";
  if (ext == "pem" || ext == "dat") return "application/octet-stream";
  return "application/unknown";
}

ULString fs_get_mime_type(ULString path) { return ulCreateString(mime_for(from_ul(path))); }

ULString fs_get_charset(ULString path) { return ulCreateString("utf-8"); }

void destroy_heap_buffer(void* user_data, void* data) {
  delete static_cast<std::string*>(user_data);
}

ULBuffer fs_open_file(ULString path) {
  std::string rel = normalize(path);
  std::string full;
  Where where = resolve(rel, &full);
  if (where == Where::None) return nullptr;

  // Owned by the ULBuffer; freed in destroy_heap_buffer.
  std::string* blob = new std::string();
  bool ok = (where == Where::Classpath) ? read_classpath(full, blob) : read_disk(full, blob);
  if (!ok) {
    delete blob;
    log_error("open_file: %s resolved to %s but could not be read", rel.c_str(), full.c_str());
    return nullptr;
  }
  return ulCreateBuffer(blob->empty() ? const_cast<char*>("") : &(*blob)[0], blob->size(), blob,
                        destroy_heap_buffer);
}

} // namespace

ULFileSystem make_file_system() {
  ULFileSystem fs;
  fs.file_exists = fs_file_exists;
  fs.get_file_mime_type = fs_get_mime_type;
  fs.get_file_charset = fs_get_charset;
  fs.open_file = fs_open_file;
  return fs;
}

void install_platform() {
  Globals& gl = g();
  if (gl.platform_ready) return;

  ULLogger logger;
  logger.log_message = on_log_message;
  ulPlatformSetLogger(logger);

  ULFileSystem fs = make_file_system();
  ulPlatformSetFileSystem(fs);

  install_font_loader();

  gl.platform_ready = true;
}

} // namespace voidul
