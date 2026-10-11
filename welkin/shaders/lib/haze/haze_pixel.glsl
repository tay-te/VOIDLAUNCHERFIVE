#ifndef WELKIN_HAZE_PIXEL
#define WELKIN_HAZE_PIXEL

// The pixel-true look (STYLE.md, Gloam's pixel.glsl): world-space texels at 16 a block near, coarser with distance
// as VOID LOD's blocks are; values in a few flat steps; the edge between two steps an ordered 4x4 Bayer dither on
// that texel grid, so the pattern sticks to the world and does not crawl when the camera turns.
// Prefixed hz so it cannot clash with the pack's own copy; swap for the shared one if names and maths match.

// The 4x4 Bayer matrix, 0..1, at an integer texel: the same values as Gloam's bayer4 (rows 0 8 2 10 / 12 4 14 6 /
// 3 11 1 9 / 15 7 13 5), as bit arithmetic: the 2x2 matrix ((0 2) (3 1)) is 2 ((x ^ y) & 1) + (y & 1), taken at
// both scales. A handful of integer ops (a lookup array can land in local memory).
float hzBayer(ivec2 p) {
    int t = p.x ^ p.y;
    int v = ((t & 1) << 3) | ((p.y & 1) << 2) | (t & 2) | ((p.y >> 1) & 1);
    return (float(v) + 0.5) * (1.0 / 16.0);
}

// v (0..1) as one of `levels` flat steps (0, 1/levels .. 1). Only the HZ_DITHER share of the way between two steps
// is dithered; the rest is flat, so a slow ramp reads as bands with pixel-dithered borders, never as a gradient.
float hzSteps(float v, float levels, ivec2 p) {
    float b = (hzBayer(p) - 0.5) * HZ_DITHER;
    return clamp(floor(clamp(v, 0.0, 1.0) * levels + 0.5 + b), 0.0, levels) / levels;
}

// The same with the Bayer threshold already looked up (b = hzBayer(p)), for several values stepped at one texel.
float hzStepsB(float v, float levels, float b) {
    return clamp(floor(clamp(v, 0.0, 1.0) * levels + 0.5 + (b - 0.5) * HZ_DITHER), 0.0, levels) / levels;
}

// A second and third decorrelated threshold at the same texel, for values stepped together.
ivec2 hzAlt1(ivec2 p) { return p + ivec2(2, 1); }
ivec2 hzAlt2(ivec2 p) { return p + ivec2(1, 3); }

// How many blocks one world texel is where one screen pixel covers `footprint` blocks: 1/16 near, then 1/8, 1/4 ..
// so a texel is always about two pixels or more and never smooth; capped at 64 blocks for the far LOD.
float hzTexelBlocks(float footprint) {
    return exp2(clamp(ceil(log2(max(footprint * 2.0, 1e-5))), -4.0, 6.0));
}

// Blocks one screen pixel covers at distance L (looking at it square on).
float hzFootprint(float L) {
    return 2.0 * L / max(gbufferProjection[1][1] * viewHeight, 1.0);
}

// A face normal from screen derivatives, for programs without a normal buffer. Garbage on silhouettes, which only
// picks the wrong texel axes for one pixel there.
vec3 hzScreenNormal(vec3 rel) {
    return normalize(cross(dFdx(rel), dFdy(rel)) + vec3(0.0, 1e-6, 0.0));
}

// The world texel a surface point falls in. rel: the point relative to the camera; n: the face normal (world). The
// two axes along the face are snapped to the texel grid, the axis across it is left as it is (so a face exactly on a
// block boundary never flickers between two texels). Returns the texel's integer coordinates on the face (for the
// Bayer dither and hashes) and, in relSnap, the texel centre relative to the camera. Exact at any distance from the
// origin: integer maths on cameraPositionInt, floats only for the camera-local part.
struct HzTexel {
    ivec2 id;           // integer texel on the face, global (the same texel has the same id from anywhere)
    vec3  relSnap;      // texel centre relative to the camera
    float size;         // blocks per texel
};

HzTexel hzTexel(vec3 rel, vec3 n, float size) {
    vec3 an = abs(n);
    // in-plane axes: top/bottom faces x,z; x faces z,y; z faces x,y
    ivec2 ax = an.y >= max(an.x, an.z) ? ivec2(0, 2) : (an.x >= an.z ? ivec2(2, 1) : ivec2(0, 1));
    vec3 local = cameraPositionFract + rel;                     // relative to the camera's block corner
    vec2 l = vec2(local[ax.x], local[ax.y]);
    ivec2 ci = ivec2(cameraPositionInt[ax.x], cameraPositionInt[ax.y]);
    HzTexel t;
    t.size = size;
    vec2 snapped;
    if (size < 1.0) {
        int inv = int(1.0 / size + 0.5);                        // 2..16 texels a block
        vec2 f = floor(l * float(inv));
        t.id = ci * inv + ivec2(f);
        snapped = (f + 0.5) / float(inv);
    } else {
        int s = int(size + 0.5);                                // 1..64 blocks a texel, a power of two
        int sh = int(log2(float(s)) + 0.5);
        ivec2 r = ci & (s - 1);
        vec2 f = floor((vec2(r) + l) / float(s));
        t.id = (ci >> sh) + ivec2(f);
        snapped = (f + 0.5) * float(s) - vec2(r);
    }
    vec3 c = local;
    c[ax.x] = snapped.x;
    c[ax.y] = snapped.y;
    t.relSnap = c - cameraPositionFract;
    return t;
}

// An integer hash, 0..1 (PCG-like, as Gloam's ground mist).
float hzHash(ivec2 p, int salt) {
    uint h = uint(p.x) * 0x8da6b343u ^ uint(p.y) * 0xd8163841u ^ uint(salt) * 0xcb1ab31fu;
    h = (h ^ (h >> 15u)) * 0x2c1b3c6du;
    h = (h ^ (h >> 12u)) * 0x297a2d39u;
    return float((h ^ (h >> 15u)) & 0xffffu) / 65535.0;
}

// Value noise on the world's block lattice at 2^shift blocks a cell, exact at any distance: cameraBlock is the
// camera's block (x, z), local the point relative to it in blocks.
float hzWorldNoise(ivec2 cameraBlock, vec2 local, int shift, int salt) {
    int s = 1 << shift;
    vec2 p = (vec2(cameraBlock & (s - 1)) + local) / float(s);
    vec2 i = floor(p);
    vec2 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    ivec2 c = (cameraBlock >> shift) + ivec2(i);
    float a = hzHash(c, salt), b = hzHash(c + ivec2(1, 0), salt);
    float d = hzHash(c + ivec2(0, 1), salt), e = hzHash(c + ivec2(1, 1), salt);
    return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

float hzLuma(vec3 c) {
    return dot(c, vec3(0.3, 0.59, 0.11));
}

// A palette colour at the brightness of `like` (times `brightness`), as Gloam's Palette.tone: the tint keeps its hue,
// the scene decides how bright it is, so dusk and dark nights stay right without a palette per hour.
vec3 hzTone(vec3 tint, vec3 like, float brightness) {
    return clamp(tint * (hzLuma(like) * brightness / max(hzLuma(tint), 1e-4)), 0.0, 1.0);
}

#endif
