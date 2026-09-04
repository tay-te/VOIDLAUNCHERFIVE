// The bundled font loader — the one place this binding reaches past the C API.
//
// WHY: `ulPlatformSetFontLoader` is declared in CAPI_FontLoader.h but is **not exported** by
// Ultralight 1.4.0b (rev 081c48b) on any platform — verified against libUltralight.so, the mac
// dylibs and Ultralight.dll. A font loader is nevertheless mandatory; without one every
// ulCreateView fails with:
//
//     Error, tried to create a View but FontLoader was NULL.
//
// Two ways out. Linking AppCore for `ulEnablePlatformFontLoader` was rejected: it drags GTK3 in on
// Linux and D3D11 + DirectWrite on Windows, and we are an OpenGL guest inside Minecraft's process.
// So we implement `ultralight::FontLoader` against the C++ headers and install it through
// `ultralight::Platform::instance()`, which *is* exported (from UltralightCore, on all three
// platforms). That is exactly what AppCore does internally.
//
// The C++ ABI surface this costs us is three symbols — Platform::instance, FontFile::Create and
// String's constructor — all resolved from the same SDK we compile the headers from. If a future
// SDK exports the C entry point, delete this file and go back to ulPlatformSetFontLoader.
//
// WHAT IT DOES: hands back one bundled font for every family. The in-game UI is a fixed design
// that has to look identical on every machine, so it declares its typefaces with @font-face, which
// is served by our ULFileSystem, not by this. This only has to guarantee that *something* renders
// for an unknown family — including the CJK fallback, where a font missing the glyph still beats a
// failed load.
//
// Deliberate consequence: system fonts are not reachable from the page. `font-family: Arial` gets
// Inter. The UI must bundle what it wants to use.

#include <Ultralight/platform/FontLoader.h>
#include <Ultralight/platform/Platform.h>

#include <string>

#include "common.h"

namespace voidul {
namespace {

const char kFallbackFamily[] = "Inter";
const char kFontRelPath[] = "resources/fonts/Inter-Variable.ttf";

class BundledFontLoader : public ultralight::FontLoader {
 public:
  ultralight::String fallback_font() const override { return ultralight::String(kFallbackFamily); }

  ultralight::String fallback_font_for_characters(const ultralight::String& characters, int weight,
                                                  bool italic) const override {
    return ultralight::String(kFallbackFamily);
  }

  ultralight::RefPtr<ultralight::FontFile> Load(const ultralight::String& family, int weight,
                                                bool italic) override {
    Globals& gl = g();

    // Preferred: the file on disk next to the extracted natives. FreeType can mmap it instead of
    // us holding ~900 KB of font in the heap.
    if (!gl.native_dir.empty()) {
      std::string disk = gl.native_dir + kFontRelPath;
      if (exists_disk(disk)) {
        return ultralight::FontFile::Create(ultralight::String(disk.c_str()));
      }
    }

    // Dev fallback: running out of a build tree with the font on the classpath.
    std::string blob;
    if (read_classpath(std::string("dev/voidclient/ultralight/") + kFontRelPath, &blob) ||
        (!gl.classpath_prefix.empty() &&
         read_classpath(gl.classpath_prefix + kFontRelPath, &blob))) {
      ultralight::RefPtr<ultralight::Buffer> buffer =
          ultralight::Buffer::CreateFromCopy(blob.data(), blob.size());
      return ultralight::FontFile::Create(buffer);
    }

    log_error("font_load: no bundled font (looked for %s under the natives dir '%s')", kFontRelPath,
              gl.native_dir.c_str());
    return nullptr;
  }
};

} // namespace

// The Platform singleton keeps a raw pointer, so the loader has to outlive it. Static storage,
// never destroyed: teardown order against a library we do not own is not a fight worth having.
void install_font_loader() {
  static BundledFontLoader* loader = new BundledFontLoader();
  ultralight::Platform::instance().set_font_loader(loader);
}

} // namespace voidul
