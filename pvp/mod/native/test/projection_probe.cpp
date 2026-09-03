// Determines what ulApplyProjection's `flip_y` flag actually does, so that gpu_driver_gl.cpp's
// kFlipY is a measured constant rather than a guess.
//
// The question the driver has to answer is: after rendering a view into an FBO-backed texture,
// does row 0 of that texture hold the TOP of the page or the BOTTOM? GL texture memory is
// bottom-left origin; Ultralight's coordinate space is top-left. `flip_y` is the only knob.
//
// This needs no GL context — ulApplyProjection is pure arithmetic in libUltralight.
//
//   c++ -std=c++17 -I<sdk>/include projection_probe.cpp -L<sdk>/bin -lUltralight -o probe
//
// Output is read as: for a viewport of h pixels, where does Ultralight y=0 land in NDC?
//   +1 -> top of the framebuffer   -> texture row (h-1) -> texture is BOTTOM-up  -> flip needed
//   -1 -> bottom of the framebuffer-> texture row 0     -> texture is TOP-down   -> no flip

#include <cstdio>

#include <Ultralight/CAPI.h>

static ULMatrix4x4 identity() {
  ULMatrix4x4 m{};
  m.data[0] = m.data[5] = m.data[10] = m.data[15] = 1.0f;
  return m;
}

static void probe(bool flip_y) {
  const float w = 100.0f, h = 50.0f;
  ULMatrix4x4 p = ulApplyProjection(identity(), w, h, flip_y);

  // Column-major 4x4, as GL wants it: ndc = M * (x, y, 0, 1).
  auto apply = [&](float x, float y, float* ox, float* oy) {
    *ox = p.data[0] * x + p.data[4] * y + p.data[12];
    *oy = p.data[1] * x + p.data[5] * y + p.data[13];
  };

  float tx, ty, bx, by;
  apply(0.0f, 0.0f, &tx, &ty);  // Ultralight's top-left
  apply(0.0f, h, &bx, &by);     // Ultralight's bottom-left

  printf("flip_y=%-5s  ul(0,0) -> ndc(%+.2f, %+.2f)   ul(0,%.0f) -> ndc(%+.2f, %+.2f)   => %s\n",
         flip_y ? "true" : "false", tx, ty, h, bx, by,
         ty > by ? "y=0 at TOP of framebuffer (texture is bottom-up: sample with v flipped, or "
                   "render with the other flip_y)"
                 : "y=0 at BOTTOM of framebuffer (texture row 0 = page top: top-left origin)");
}

int main() {
  printf("Ultralight %s\n", ulVersionString());
  probe(false);
  probe(true);
  return 0;
}
