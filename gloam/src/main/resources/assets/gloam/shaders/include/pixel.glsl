#ifndef GLOAM_PIXEL_GLSL
#define GLOAM_PIXEL_GLSL

// The pixel-true look (docs/STYLE.md): world-space texels at Minecraft's 16 per block, coarser with distance as VOID
// LOD's blocks are; opacity in a few flat steps; fades as an ordered 4x4 Bayer dither on the texel grid.

// The 4x4 Bayer matrix, 0..1, at an integer texel.
float bayer4(ivec2 p) {
    int x = p.x & 3, y = p.y & 3;
    int m = (x & 1) * 2 + (y & 1);
    int n = ((x >> 1) & 1) * 2 + ((y >> 1) & 1);
    // the 4x4 matrix from the 2x2 one, ((0 2) (3 1)) at both scales
    int b2[4] = int[4](0, 3, 2, 1);
    return (float(b2[m] * 4 + b2[n]) + 0.5) / 16.0;
}

// How many blocks a texel is at a place whose world position changes by {@code footprint} blocks a screen pixel: 1/16
// near, 1/8, 1/4 ... further, always at least about two pixels, never smooth.
float texelBlocks(float footprint) {
    return exp2(max(-4.0, ceil(log2(max(footprint * 2.0, 1e-4)))));
}

// v (0..1) as one of {@code levels} flat steps (0, 1/levels .. 1), the step chosen with the Bayer dither at texel p, so
// a value between two steps comes out as a pattern of both.
float steps(float v, float levels, ivec2 p) {
    return clamp(floor(v * levels + bayer4(p)) / levels, 0.0, 1.0);
}

#endif
