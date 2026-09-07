// Does the GL driver actually build, and does it fail the way it claims to when it cannot?
//
// The OpenGL driver has always been "the half of this binding that cannot be tested off a real
// game" (gpu_driver_gl.cpp, first line). That was true while the only GL context in reach belonged
// to Minecraft. It is not quite true on macOS: CGL will hand out an offscreen context in exactly
// the profile LWJGL 2 gives the game — GL 2.1, GLSL 1.20, no drawable — which is enough to compile
// and link shaders. So this runs the driver's real start-up path with no window and no display:
//
//   gpu_driver_builds        no forced failure  -> probe() succeeds, both GLSL 1.20 programs built
//   gpu_driver_fails_compile VOID_UI_GPU_FAIL=compile -> probe() fails, driver marked dead
//   gpu_driver_fails_link    VOID_UI_GPU_FAIL=link    -> probe() fails, driver marked dead
//
// The first is the one that could not be run before at all: proof, on every build, that the ported
// shaders survive a real compiler. The other two are what make the CPU fallback something anybody
// can watch happen rather than something argued for in a comment — they are the closest reachable
// stand-in for the failure this whole fallback exists for, which is a Windows or Linux driver
// rejecting these programs.
//
// What it still does not cover. The offscreen context is *this* machine's driver, so a pass here
// says nothing about anyone else's; that is the point of the fallback, not a gap this can close.
// It cannot reach VOID_UI_GPU_FAIL=late either, which needs a live view and a paint. And it is
// macOS-only: CGL is the only offscreen path written here. GLX pbuffers on Linux and a hidden
// WGL window on Windows would both work the same way, and are the obvious extension — the day
// somebody has that hardware, this file is where the coverage goes.
//
// Note the forced-failure mode is read once per process, into a function-local static, so each
// mode is a separate ctest entry rather than a loop.

#include <cstdio>
#include <cstring>

#include "gpu_driver_gl.h"

#if defined(__APPLE__)
#include <OpenGL/OpenGL.h>

namespace {

// A context with no drawable. Shader compilation, linking and every glGet the driver's start-up
// makes are all legal against one; only actual rasterisation would not be, and nothing here
// rasterises.
bool make_offscreen_context() {
  CGLPixelFormatAttribute attrs[] = {kCGLPFAAccelerated, kCGLPFAOpenGLProfile,
                                     static_cast<CGLPixelFormatAttribute>(kCGLOGLPVersion_Legacy),
                                     static_cast<CGLPixelFormatAttribute>(0)};
  CGLPixelFormatObj pixel_format = nullptr;
  GLint formats = 0;
  if (CGLChoosePixelFormat(attrs, &pixel_format, &formats) != kCGLNoError || !pixel_format) {
    printf("  FAIL could not choose a legacy (2.1) pixel format\n");
    return false;
  }
  CGLContextObj context = nullptr;
  CGLError err = CGLCreateContext(pixel_format, nullptr, &context);
  CGLDestroyPixelFormat(pixel_format);
  if (err != kCGLNoError || !context) {
    printf("  FAIL could not create an offscreen GL context (CGL error %d)\n", err);
    return false;
  }
  CGLSetCurrentContext(context);
  return true;
}

} // namespace

int main(int argc, char** argv) {
  // "ok" — the driver must build. "fail" — it must refuse, and say so.
  const bool expect_ok = argc < 2 || strcmp(argv[1], "fail") != 0;
  printf("gpu_driver_probe: expecting the driver to %s\n", expect_ok ? "build" : "refuse");

  if (!make_offscreen_context()) return 1;

  int failures = 0;
  bool built = voidul::gpu::probe();
  printf("%s probe() returned %s\n", built == expect_ok ? "  ok  " : "  FAIL",
         built ? "true" : "false");
  if (built != expect_ok) failures++;

  // The other half of the contract. A driver that answered false must be marked dead — that flag is
  // what the mod polls to rebuild its view on the CPU surface — and one that answered true must
  // not be.
  bool dead = voidul::gpu::failed();
  printf("%s failed() returned %s\n", dead == !expect_ok ? "  ok  " : "  FAIL",
         dead ? "true" : "false");
  if (dead == expect_ok) failures++;

  // Sticky in both directions: a second ask must not recompile anything, and must not change its
  // mind. On the failing runs this is the check that a broken machine is not made to rebuild two
  // shader programs on every frame.
  bool again = voidul::gpu::probe();
  printf("%s a second probe() returned %s\n", again == built ? "  ok  " : "  FAIL",
         again ? "true" : "false");
  if (again != built) failures++;

  printf("%s\n", failures ? "FAILED" : "PASSED");
  return failures ? 1 : 0;
}

#else

int main() {
  // Not a silent pass. If this ever runs somewhere it was not expected to, say so.
  printf("gpu_driver_probe: no offscreen GL path on this platform; nothing was tested.\n");
  printf("GLX pbuffers (Linux) and a hidden WGL window (Windows) would both work here.\n");
  return 0;
}

#endif
