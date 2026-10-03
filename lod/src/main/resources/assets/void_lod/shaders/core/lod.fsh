#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <void_lod:lodinfo.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 horizontal;
layout(location = 4) flat in float tileFade;
#ifdef LOD_SMOOTH
layout(location = 2) in vec3 sideColor;
layout(location = 3) in vec4 stair;
#else
layout(location = 2) in vec3 blockPos;
layout(location = 3) in vec2 occlusion;
#endif

layout(location = 0) out vec4 fragColor;

#ifdef LOD_SMOOTH
// How much of [y - w/2, y + w/2] lies in the risers, when the bottom r of every block of height is riser: the
// exact box filter of the stair pattern, so the steps stay crisp where a block is pixels tall and melt into
// their average where it is under one, with no shimmer in between.
float riserIntegral(float y, float r) {
    return floor(y) * r + min(fract(y), r);
}

float riserCoverage(float y, float w, float r) {
    w = max(w, 1e-3);
    return (riserIntegral(y + 0.5 * w, r) - riserIntegral(y - 0.5 * w, r)) / w;
}
#endif

uint hash2(ivec2 p) {
    uint h = uint(p.x) * 0x8da6b343u ^ uint(p.y) * 0xd8163841u;
    h = (h ^ (h >> 15u)) * 0x2c1b3c6du;
    return h ^ (h >> 12u);
}

// Value noise in [0, 1] on a lattice of 2^shift blocks, anchored to the world, not the camera: the lattice cell is
// the camera's block shifted down plus the offset from it, in integers, so it is exact anywhere in the world.
float lattice(vec2 rel, int shift) {
    float size = float(1 << shift);
    vec2 local = (rel + vec2(CameraBlockPos.xz & ((1 << shift) - 1))) / size;
    ivec2 cell = (CameraBlockPos.xz >> shift) + ivec2(floor(local));
    vec2 f = fract(local);
    f = f * f * (3.0 - 2.0 * f);
    float a = float(hash2(cell) & 1023u), b = float(hash2(cell + ivec2(1, 0)) & 1023u);
    float c = float(hash2(cell + ivec2(0, 1)) & 1023u), d = float(hash2(cell + ivec2(1, 1)) & 1023u);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y) / 1023.0;
}

// Ground detail at every scale that is still a couple of pixels across: the patchiness grass, scree and snow have
// in the chunks, without the flat, clean look of a smooth surface. Octaves under two pixels are left out, so it
// never shimmers.
float detail(vec2 rel, float blocksPerPixel, int first) {
    float sum = 0.0;
    float amp = 1.0;
    for (int o = first; o < 7; o++) {
        float fade = clamp(float(1 << o) / (2.0 * blocksPerPixel) - 1.0, 0.0, 1.0);
        sum += fade * amp * (lattice(rel, o) - 0.5);
        amp *= 0.82;
    }
    return sum;
}

#ifdef LOD_CLIP
// Interleaved gradient noise: a dither with no visible grid, for the seam to vanilla's chunks and for cross-fades.
float dither(vec2 p) {
    return fract(52.9829189 * fract(dot(floor(p), vec2(0.06711056, 0.00583715))));
}
#endif

void main() {
#ifdef LOD_CLIP
    // Only tiles in the seam with vanilla's chunks, or fading between levels, use this variant: everywhere else
    // the shader has no discard, so the GPU keeps its early depth test for the bulk of the LOD. The seam fades
    // in over LodClip.y blocks; a fade keeps the pixels its complement does not.
    float n = dither(gl_FragCoord.xy);
    float t = (length(horizontal) - LodClip.x) / max(LodClip.y, 1.0);
    if (t < n || (tileFade > 0.0 && n >= tileFade) || (tileFade < 0.0 && n < -tileFade)) {
        discard;
    }
#endif
    vec3 color = vertexColor.rgb;
#ifdef LOD_SMOOTH
    // Steps a block tall, each block column flat at the surface's height over its centre, as the chunks would
    // build it: so step edges follow the block grid, zig-zagging along the contours, instead of drawing them.
    // Where a block is under a couple of pixels, the risers' average share instead.
    vec2 planar = horizontal - CameraOffset.xz;
    float column = stair.y + dot(stair.zw, floor(planar) + 0.5 - planar);
    float steps = riserCoverage(column, fwidth(stair.y), stair.x);
    float blocksPerPixel = max(fwidth(planar.x), fwidth(planar.y));
    color = mix(color, sideColor, mix(stair.x, steps, smoothstep(0.6, 0.3, blocksPerPixel)));
    color *= 1.0 + 0.09 * detail(planar, blocksPerPixel, 1);
#else
    // Vanilla's occlusion: corners on the tops, and the lowest block of every wall where it meets the floor.
    color *= occlusion.x * mix(0.8, 1.0, clamp(occlusion.y, 0.0, 1.0));
    // A grain of one block: each block a shade lighter or darker, as its texture makes it in the chunks.
    // It fades out as blocks shrink under a pixel, where it would only shimmer.
    ivec3 b = ivec3(floor(blockPos)) + CameraBlockPos;
    uint h = uint(b.x) * 73856093u ^ uint(b.y) * 19349663u ^ uint(b.z) * 83492791u;
    h = (h ^ (h >> 13u)) * 0x5bd1e995u;
    h ^= h >> 15u;
    vec3 w = fwidth(blockPos);
    float fade = clamp(1.8 - 1.4 * max(max(w.x, w.y), w.z), 0.0, 1.0);
    color *= 1.0 + (float(h & 1023u) / 1023.0 - 0.5) * 0.11 * fade;
    vec2 planar = horizontal - CameraOffset.xz;
    color *= 1.0 + 0.07 * detail(planar, max(fwidth(planar.x), fwidth(planar.y)), 2);
#endif
    fragColor = vec4(mix(color, FogColor.rgb, vertexColor.a * FogColor.a), 1.0);
}
