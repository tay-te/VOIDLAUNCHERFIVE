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
// WHAT IT DOES: resolves every family the page names. Ultralight never fetches an @font-face
// itself — the declaration only tells it *which family* to ask for, and the ask lands here — so
// this table is the whole reason the UI is drawn in its own typefaces rather than in Inter. See
// kFaces below for the families that must be present. Anything the design does not ship gets the
// fallback, which also covers the CJK path, where a font missing the glyph still beats a failed
// load.
//
// Deliberate consequence: system fonts are not reachable from the page. `font-family: Arial` gets
// Inter. The UI must bundle what it wants to use.

#include <Ultralight/platform/FontLoader.h>

#include <cctype>
#include <set>
#include <Ultralight/platform/Platform.h>

#include <string>

#include "common.h"

namespace voidul {
namespace {

const char kFallbackFamily[] = "Inter";
const char kFontRelPath[] = "resources/fonts/Inter-Variable.ttf";

// The design's own faces, published by the mod next to the UI bundle (build.gradle) and so
// reachable under the renderer's classpath prefix. They are static instances on purpose: the
// fallback is a *variable* font, and serving it for every family is why nothing in the UI was
// ever bold — the weight axis is never set, so every run came out at 400.
//
// ── THE FAMILIES THAT MUST BE PRESENT ──────────────────────────────────────────────────────
// design/quiet-cell-system.md §2 settles the type as **Outfit only**, at three weights:
//
//     "outfit"  300 Light · 400 Regular · 500 Medium        — and nothing else, ever
//
// `--font-display` and `--font-mono` are aliases of `--font-ui` in the token build, so the page
// resolves to that one family name whatever a rule spells; there is no display face and no
// monospace to serve. Bricolage Grotesque and DM Mono went out with the system they belonged to
// and are no longer bundled — do not re-add either name here without re-adding its file.
//
// Three things have to agree, and nothing checks them for you:
//   1. this table,
//   2. the .ttf files in mod/src/main/resources/assets/void/fonts/ (build.gradle republishes that
//      directory into assets/void/ui/fonts/, which is where `file` is resolved from), and
//   3. the family names the page's CSS asks for.
//
// When they drift, **nothing fails**. A family absent from this table is not an error: Load()
// falls straight through to Inter and the overlay renders in the wrong typeface with nothing in
// the log to say so. That silence is why every served face is announced once in Load() — a
// healthy run prints one line per weight the design uses,
//
//     font_load: 'outfit' weight 300 -> fonts/outfit-300.ttf (36728 bytes)
//     font_load: 'outfit' weight 400 -> fonts/outfit-400.ttf (36696 bytes)
//     font_load: 'outfit' weight 500 -> fonts/outfit-500.ttf (36664 bytes)
//
// and a missing line is the bug. (New faces are cut the way the existing ones were: instance the
// google/fonts variable source at the weight, subset it to the same unicode range as
// packages/ui/scripts/fetch-fonts.mjs, and save as TTF rather than woff2 — FreeType reads TTF.)
//
// Weights match by *nearest*, not exactly, so a page still asking for 600/700 gets the 500
// instance rather than dropping to the fallback. That is deliberate: §2 stops at 500, and a face
// one step light is a far smaller wrong than a different typeface.
struct Face {
  const char* family;  // lower-case, as compared
  int weight;
  const char* file;
};

const Face kFaces[] = {
    {"outfit", 300, "fonts/outfit-300.ttf"},
    {"outfit", 400, "fonts/outfit-400.ttf"},
    {"outfit", 500, "fonts/outfit-500.ttf"},
};

std::string lower(const ultralight::String& s) {
  const char* raw = s.utf8().data();
  if (!raw) return std::string();
  std::string out(raw, s.utf8().length());
  for (char& c : out) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
  return out;
}

/// The bundled file for a family, at the nearest weight it ships. Empty when we do not have it.
std::string face_for(const ultralight::String& family, int weight) {
  std::string name = lower(family);
  const char* best = nullptr;
  int best_distance = 0;
  for (const Face& face : kFaces) {
    if (name != face.family) continue;
    int distance = face.weight > weight ? face.weight - weight : weight - face.weight;
    if (!best || distance < best_distance) {
      best = face.file;
      best_distance = distance;
    }
  }
  return best ? std::string(best) : std::string();
}

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

    // A family the design ships wins over the fallback. Ultralight never fetches an @font-face
    // itself — every family the page names is resolved through this loader — so this is the only
    // place the UI can be made to render in its own typefaces rather than in Inter.
    std::string face = face_for(family, weight);
    if (!face.empty() && !gl.classpath_prefix.empty()) {
      std::string blob;
      if (read_classpath(gl.classpath_prefix + face, &blob)) {
        // Once per face. Which typeface the UI is actually drawn in is otherwise invisible:
        // a family that fails to resolve does not error, it silently comes out as Inter.
        static std::set<std::string> announced;
        if (announced.insert(face).second) {
          log_info("font_load: '%s' weight %d -> %s (%zu bytes)", lower(family).c_str(), weight,
                   face.c_str(), blob.size());
        }
        ultralight::RefPtr<ultralight::Buffer> buffer =
            ultralight::Buffer::CreateFromCopy(blob.data(), blob.size());
        return ultralight::FontFile::Create(buffer);
      }
      log_error("font_load: '%s' weight %d maps to %s, which is not on the classpath under '%s'",
                lower(family).c_str(), weight, face.c_str(), gl.classpath_prefix.c_str());
    }

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
