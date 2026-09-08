#pragma once

#include <jni.h>

#include <string>

#include <JavaScriptCore/JavaScript.h>
#include <Ultralight/CAPI.h>

namespace voidul {

// One per dev.void.ultralight.View. The Java object holds the address as a long.
struct ViewState {
  ULView view = nullptr;
  bool accelerated = true;

  // Accelerated path only: the double buffer. Ultralight paints into its own render target on the
  // UI thread while the game thread samples one of these, so the game never sees a frame that is
  // half drawn and never waits for one to finish — the two textures alternate, so the one being
  // copied into is never the one that was last published. A mutex would have been the other way to
  // do it and is the one thing the threading split forbids: it would stop the game thread dead
  // inside a UI-thread paint.
  unsigned int present_texture[2] = {0, 0};
  unsigned int present_index = 0;
  // Per texture, not shared. Sharing one size record leaves the buffer that is not copied into on
  // the frame a resize lands still allocated at the old size: the copy then fills its top-left
  // corner and the blit, which samples the whole texture, shows that frame very slightly scaled.
  // Alternating with a correct one, that is a subtle constant flicker — the exact symptom this
  // double buffer exists to remove.
  unsigned int present_width[2] = {0, 0};
  unsigned int present_height[2] = {0, 0};
  // Which command list the published texture holds, so a still menu copies nothing.
  unsigned long long presented_serial = 0;

  // CPU path only: the GL texture the view's surface is uploaded into, owned by the binding so
  // that glTextureId() means the same thing to Java whichever renderer is in use.
  unsigned int cpu_texture = 0;
  // The size that texture was allocated at. A resize changes the surface bitmap but not the
  // texture, and a dirty rectangle from the new size then falls outside the old allocation —
  // glTexSubImage2D rejects it with GL_INVALID_VALUE and the texture keeps its stale pixels,
  // which looks like the interface freezing.
  unsigned int cpu_texture_width = 0;
  unsigned int cpu_texture_height = 0;

  // java.util.function.Function<String,String> set via View.setMessageHandler, as a global ref.
  jobject handler = nullptr;
  jmethodID handler_apply = nullptr;

  // JSClass backing window.__void_native. Created lazily, released with the view.
  JSClassRef bridge_class = nullptr;
};

// Installs window.__void_native on the view's window object. Safe to call repeatedly; must be
// called on every WindowObjectReady (the window object is replaced on each navigation).
void install_message_bridge(ViewState* vs);

// Releases the JSClass and the global ref to the Java handler.
void release_message_bridge(ViewState* vs);

// Evaluates `js` in the view and returns its string form ("" for undefined or on exception).
std::string evaluate_script(ViewState* vs, const std::string& js);

} // namespace voidul
