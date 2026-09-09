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
#include <utility>

#include "gpu_driver_gl.h"

#if defined(__APPLE__)
#include <OpenGL/OpenGL.h>

namespace {

/// ctest's skip code (`SKIP_RETURN_CODE` in CMakeLists.txt), and the autotools convention.
///
/// A machine with no legacy GL context is not a failing driver, and reporting it as one is worse
/// than useless: it makes CI red for a reason nobody can fix, which trains people to ignore the
/// colour. That is exactly what happened — GitHub's `macos-14` arm64 runners are VMs with no GPU,
/// so all three of these tests failed on every run of this workflow, on `main` included.
constexpr int kSkip = 77;

/// Outcome of trying to get a context, kept apart from "the driver misbehaved".
enum class Context { Ready, Unavailable };

// A context with no drawable. Shader compilation, linking and every glGet the driver's start-up
// makes are all legal against one; only actual rasterisation would not be, and nothing here
// rasterises.
//
// Two pixel formats are tried, in order, and the order is the point:
//
//   1. `kCGLPFAAccelerated` — the real driver, which is what this test is *for*. A pass here is
//      the claim the header makes: the ported GLSL 1.20 survives a hardware shader compiler.
//   2. no accelerator flag — Apple's software GL. Still a genuine GL 2.1 front end, so it still
//      compiles and links the programs and still exercises the driver's whole start-up path; it
//      just is not anybody's real driver. Weaker evidence, and worth having rather than nothing.
//
// If neither is available there is no GL on this machine at all, and the run is SKIPPED. That
// distinction is the whole fix: `Unavailable` means "ask a different machine", not "the shaders
// are broken", and only a real refusal from a context that exists is a failure.
Context make_offscreen_context() {
  const CGLPixelFormatAttribute profile = static_cast<CGLPixelFormatAttribute>(kCGLOGLPVersion_Legacy);
  CGLPixelFormatAttribute accelerated[] = {kCGLPFAAccelerated, kCGLPFAOpenGLProfile, profile,
                                           static_cast<CGLPixelFormatAttribute>(0)};
  CGLPixelFormatAttribute software[] = {kCGLPFAOpenGLProfile, profile,
                                        static_cast<CGLPixelFormatAttribute>(0)};

  for (const auto& attempt : {std::make_pair(&accelerated[0], "accelerated"),
                              std::make_pair(&software[0], "software")}) {
    CGLPixelFormatObj pixel_format = nullptr;
    GLint formats = 0;
    if (CGLChoosePixelFormat(attempt.first, &pixel_format, &formats) != kCGLNoError ||
        !pixel_format) {
      printf("  ....  no legacy (2.1) %s pixel format on this machine\n", attempt.second);
      continue;
    }
    CGLContextObj context = nullptr;
    CGLError err = CGLCreateContext(pixel_format, nullptr, &context);
    CGLDestroyPixelFormat(pixel_format);
    if (err != kCGLNoError || !context) {
      printf("  ....  %s pixel format exists but no context (CGL error %d)\n", attempt.second, err);
      continue;
    }
    CGLSetCurrentContext(context);
    printf("  ok    GL 2.1 context, %s\n", attempt.second);
    return Context::Ready;
  }

  printf("  SKIP  no legacy (2.1) GL context available — nothing to test the driver against\n");
  return Context::Unavailable;
}

} // namespace

int main(int argc, char** argv) {
  // "ok" — the driver must build. "fail" — it must refuse, and say so.
  const bool expect_ok = argc < 2 || strcmp(argv[1], "fail") != 0;
  printf("gpu_driver_probe: expecting the driver to %s\n", expect_ok ? "build" : "refuse");

  if (make_offscreen_context() == Context::Unavailable) return kSkip;

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
