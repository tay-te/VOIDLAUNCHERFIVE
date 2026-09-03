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

// Frees GL objects. Requires a current context.
void shutdown();

} // namespace gpu
} // namespace voidul
