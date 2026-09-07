#pragma once

#include <Ultralight/CAPI.h>

namespace voidul {

// Ultralight's ULGPUDriver implemented on OpenGL 2.1 / GLSL 1.20, inside Minecraft's context.
//
// The C API's driver struct carries no user_data, so there is exactly one driver per process and
// the callbacks forward to it. It is created lazily on the first render() of an accelerated view.
namespace gpu {

// Fills in an ULGPUDriver whose callbacks target the process-wide driver.
ULGPUDriver make_driver();

// Resolves GL entry points, compiles the two programs. Must be called with Minecraft's context
// current. Returns false (and logs) if the context cannot support the driver.
bool initialize();

bool initialized();

// Runs the command list Ultralight recorded during ulRender(). Call immediately after ulRender(),
// with the same context current, between save_gl_state()/restore_gl_state().
void draw_command_list();

// How many times the driver has been through a render. Its only use is telling "this view has a
// new frame in it" from "nothing has rendered since you last asked", so the presentation copy can
// skip the frames that changed nothing.
//
// It counts renders, not command lists, and the distinction is load-bearing: a page with nothing
// left to draw produces no commands at all, and a counter that only moved when commands were
// replayed froze exactly when the overlay had to be cleared — the menu a player had just closed
// stayed composited over the game indefinitely.
unsigned long long render_serial();

// Clears one render buffer to transparent and counts it as a frame to present.
//
// The host calls this, through View.clearTarget(), at the moments it knows the page's content is
// about to go away — the menu closing, the last HUD widget being turned off. It exists because
// Ultralight never clears a view's own target and submits no commands at all for a page with
// nothing to draw, so the target would otherwise keep the last frame that had content and the
// overlay would never go away. It is deliberately *not* inferred from an empty command list: an
// empty list also means "nothing changed this frame", and clearing on that takes a perfectly good
// menu off the screen.
bool clear_render_buffer(unsigned int render_buffer_id);

// Copies a finished render buffer into a texture the caller owns, allocating or resizing it to
// `width` x `height` first. This is the back half of the double buffer: Ultralight keeps drawing
// into its own render target while the game thread samples the copy, so the game never sees a
// partially drawn frame and never has to wait for one. Requires the producing context to be
// current, and must be called after draw_command_list().
bool copy_render_buffer(unsigned int render_buffer_id, unsigned int width, unsigned int height,
                        unsigned int* texture, unsigned int* texture_width,
                        unsigned int* texture_height);

// Deletes a texture from copy_render_buffer and zeroes the handle. Needs a current context.
void delete_texture(unsigned int* texture);

// Publishes everything this context has drawn to the other contexts sharing its objects.
//
// The UI thread paints into its own context (jni_api.cpp, "Thread affinity") and the game thread
// samples the resulting texture from Minecraft's. GL only guarantees a shared object is up to date
// in a second context once the producing context has been flushed, so this is what makes the paint
// visible rather than an optimisation.
void flush();

// Saves / restores every piece of GL state the driver touches. Minecraft's immediate-mode renderer
// assumes its state survives across our paint, so this is not optional.
void save_gl_state();
void restore_gl_state();

// Resolves an Ultralight texture id — the id the driver handed out, which is what
// ULRenderTarget::texture_id carries — to the GL texture name backing it. They are not the
// same number, and the Ultralight one is small, so passing it to glBindTexture binds whatever
// unrelated texture the game happens to have under that name. Returns 0 if unknown.
unsigned int gl_texture_for(unsigned int texture_id);

// Uploads a CPU-rendered view's surface into a GL texture, creating it when `texture` is 0.
// Only `dirty` is re-uploaded; pass the full bitmap bounds to refresh everything. Returns the GL
// texture name, or 0 if GL is unavailable. Requires a current context.
unsigned int upload_surface(unsigned int texture, unsigned int& texture_width,
                            unsigned int& texture_height, ULBitmap bitmap, ULIntRect dirty);

// Frees GL objects. Requires a current context.
void shutdown();

} // namespace gpu
} // namespace voidul
