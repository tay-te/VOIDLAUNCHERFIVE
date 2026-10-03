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
layout(location = 5) in float surfaceY;
#ifdef LOD_CLIP
layout(location = 6) in float solid;
#endif
#else
layout(location = 2) in vec3 blockPos;
layout(location = 3) in vec2 occlusion;
#endif

layout(location = 0) out vec4 fragColor;

#ifdef LOD_SMOOTH
// How much of [y - w/2, y + w/2] lies in the first r of each unit period: the exact box filter of a strip
// pattern, so strips stay crisp where they are pixels wide and melt into their average share where they are
// under one, with no shimmer in between.
float stripIntegral(float y, float r) {
    return floor(y) * r + min(fract(y), r);
}

float stripCoverage(float y, float w, float r) {
    w = max(w, 1e-4);
    return (stripIntegral(y + 0.5 * w, r) - stripIntegral(y - 0.5 * w, r)) / w;
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

#ifdef LOD_SMOOTH
vec3 voxels(vec2 planar, int shift, vec3 top, vec3 side) {
    int size = 1 << shift;
    vec2 local = (planar + vec2(CameraBlockPos.xz & (size - 1))) / float(size);
    vec2 w = fwidth(local);
    ivec2 cell = (CameraBlockPos.xz >> shift) + ivec2(floor(local));
    // The column's height over its centre, and how many block boundaries lie between it and its downhill
    // neighbour along each axis: a riser shows only where the land crosses a whole block, so on gentle ground
    // the steps follow the contours every few columns, and on steep ground every column has its own. Sized so
    // the share of riser averages out to what the camera sees of the slope.
    float centre = surfaceY + dot(stair.zw, (0.5 - fract(local)) * float(size));
    vec2 drop = abs(stair.zw) * float(size);
    vec2 crossings = floor(centre) - floor(centre - drop);
    vec2 strip = clamp(stair.xy * crossings / max(drop, vec2(1e-3)), 0.0, 1.0);
    // a riser faces downhill: the low side of the column along each axis
    float cx = stripCoverage(stair.z > 0.0 ? local.x : -local.x, w.x, strip.x);
    float cz = stripCoverage(stair.w > 0.0 ? local.y : -local.y, w.y, strip.y);
    vec3 c = top * (1.0 - cx) * (1.0 - cz) + side * (0.6 * cx + 0.8 * cz * (1.0 - cx));
    uint h = hash2(cell * 3 + ivec2(shift * 7919, shift * 104729));
    float shade = float(h & 1023u) / 1023.0 - 0.5;
    float visible = clamp(1.0 / max(max(w.x, w.y), 1e-4) - 1.0, 0.0, 1.0);
    return c * (1.0 + 0.12 * shade * visible);
}
#endif

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
#ifdef LOD_SMOOTH
    // Over the void (between End islands): a triangle whose corners are all void has nothing to show.
    if (solid < 1e-3) {
        discard;
    }
#endif
#endif
    vec3 color = vertexColor.rgb;
#ifdef LOD_SMOOTH
    // Columns of blocks, the way Minecraft looks from far away: each column its top, and a strip of riser along
    // each downhill edge as wide as the share of riser the camera sees there. Columns are about three pixels
    // across at every distance (one block close in, then 2, 4, 8... blocks, blended between sizes), anchored to
    // the world's block grid, each a shade of its own, so far land has the crunch of blocks instead of the
    // gloss of a smooth surface. Where even a block is under a pixel, the strips melt into their average.
    vec2 planar = horizontal - CameraOffset.xz;
    float blocksPerPixel = max(fwidth(planar.x), fwidth(planar.y));
    float scale = clamp(log2(blocksPerPixel * 3.0), 0.0, 10.0);
    int s0 = int(scale);
    color = mix(voxels(planar, s0, color, sideColor), voxels(planar, s0 + 1, color, sideColor), fract(scale));
    color *= 1.0 + 0.07 * detail(planar, blocksPerPixel, 3);
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
