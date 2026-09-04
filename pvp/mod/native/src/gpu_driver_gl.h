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
