#ifndef WELKIN_HAZE_BRIDGE
#define WELKIN_HAZE_BRIDGE

// The bridge as the haze module reads it (DESIGN 2.1 .. 2.5). Three modes:
//   HZ_LIVE    Gloam and its uniform mixin: void_* uniforms, tiles.
//   HZ_TILES   Gloam's texture is there (magic VOID) but void_bridge == 0 (mixin did not apply): tiles, plus Iris'
//              rainStrength and wetness; haziness comes from the kind under the camera. (The 8-bit header mirror
//              is not read yet: its byte order is Gloam's to fix; see NOTES, open questions.)
//   HZ_NONE    no Gloam (or a newer contract): no fetch past texel (0,0); one temperate haze, Iris rain and wetness.
// The magic is checked before any other fetch, because the missing texture Iris binds is only 16x16.

#define HZ_NONE 0
#define HZ_TILES 1
#define HZ_LIVE 2

// The texel of cell `cell` (any integer, wrapped) in tile `tile` (2.3).
ivec2 hzTexelOf(int tile, ivec2 cell) {
    return ivec2((tile & 3) * 256, 4 + (tile >> 2) * 256) + (cell & 255);
}

ivec4 hzBytes(ivec2 texel) {
    return ivec4(texelFetch(void_data, texel, 0) * 255.0 + 0.5);
}

// As lib/void.glsl's voidMagic: the size first (the missing texture is 16x16), then texel (0, 0) holding V O I D.
bool hzMagic() {
    ivec2 size = textureSize(void_data, 0);
    if (size.x < 1024 || size.y < 516) return false;
    return hzBytes(ivec2(0, 0)) == ivec4(86, 79, 73, 68);
}

// The land under a point along the view ray, from the N tiles (16 m) within their reach and the F tiles (1 km)
// beyond, blended bilinearly between the four nearest cells (hzLandIn), so the haze runs smoothly across cell edges
// and a border between two kinds is a soft blend of both, never a line or a dither.
struct HzLand {
    float ground;       // world y of the ground
    float low;          // lowness 0..1 (Expanse: smoothstep 0..24 blocks below the land around)
    float water;        // 0..1 nearness or share of water
    int   kind;         // 1..6: the kind with the most weight among the four cells
    int   kind2;        // the other kind among them (= kind when there is one)
    float share2;       // kind2's share of the weight, 0..0.5
    int   source;       // 0 fallback, 1 near, 2 far
};

float hzHeight16(int hi, int lo) {
    return float(hi * 256 + lo) / 32.0 - 1024.0;
}

HzLand hzLandDefault(int kind) {
    HzLand d;
    d.ground = HAZE_SEA_LEVEL;
    d.low = 0.5;
    d.water = 0.0;
    d.kind = kind;
    d.kind2 = kind;
    d.share2 = 0.0;
    d.source = 0;
    return d;
}

// The world state the haze needs, read once per pixel (uniform loads plus at most three cached fetches; a program
// may compute it in its vertex shader and pass it flat instead, see NOTES).
struct HzState {
    int   mode;
    float rain;         // 0..1 at the camera
    float wet;          // 0..1 ground wetness
    vec4  haze;         // as void_haze: haziness incl. the daily thinning, dust share, curtain, far thickness
    int   kind;         // haze kind at the camera, 1..6
    float temp;         // -1..1 at the camera
    float snow;         // 0..1: what falls is snow
    float outdoor;      // 0..1: 0 deep underground (the cave fog's job)
    float daily;        // the daily thinning (Haze.daily), for the kinds along the ray
    int   season;       // 0..3, or -1 unknown
    ivec2 nearCentre;
    ivec2 farCentre;
    HzLand here;        // the land in the camera's own N cell (the first ray segment uses it: no fetch per pixel)
};

// Gloam's Haze.daily: all of the haze through night and morning, thinning in the warm middle of the day; the
// murkiest lands keep most of theirs.
float hzDaily(float haziness, float hour) {
    float midday = smoothstep(8.0, 12.0, hour) * (1.0 - smoothstep(15.0, 19.0, hour));
    float keeps = 0.5 + 0.4 * smoothstep(0.75, 0.95, haziness);
    return 1.0 - (1.0 - keeps) * midday;
}

int hzKindIndex(int kind) {
    return (kind >= 1 && kind < HZ_KINDS) ? kind : HZ_K_TEMPERATE;
}

HzState hzState() {
    HzState s;
    s.mode = HZ_NONE;
    if (hzMagic()) {
        int version = hzBytes(ivec2(1, 0)).r;
        if (version >= 1 && version <= HZ_CONTRACT) {
            s.mode = (void_bridge == 1 && void_version >= 1 && void_version <= HZ_CONTRACT) ? HZ_LIVE : HZ_TILES;
        }
    }
    float hour = mod(float(worldTime) / 1000.0 + 6.0, 24.0);
    float camY = float(cameraPositionInt.y) + cameraPositionFract.y;
    // out of the sky's reach: little sky light at the eye and well under the sea level line
    float eyeSky = float(eyeBrightnessSmooth.y) / 240.0;
    float roughOutdoor = max(smoothstep(0.05, 0.4, eyeSky), smoothstep(HAZE_SEA_LEVEL - 24.0, HAZE_SEA_LEVEL, camY));
    s.season = -1;
    s.nearCentre = cameraPositionInt.xz >> HZ_NEAR_SHIFT;
    s.farCentre = cameraPositionInt.xz >> HZ_FAR_SHIFT;
    s.snow = biome_precipitation == 2 ? 1.0 : 0.0;

    if (s.mode == HZ_LIVE) {
#ifdef VOID_GLOAM
        s.rain = clamp(welkin_rainS, 0.0, 1.0);      // the pack's smooth() of void_rain (shaders.properties)
#else
        s.rain = clamp(void_rain, 0.0, 1.0);
#endif
        s.wet = clamp(void_wetness, 0.0, 1.0);
        s.kind = hzKindIndex(void_hazeKind);
        s.temp = void_climate.x;
        s.outdoor = 1.0 - clamp(void_underground.x, 0.0, 1.0);
        s.season = void_season;
        s.nearCentre = void_nearCentre;
        s.farCentre = void_farCentre;
        s.daily = hzDaily(hzKindHazy(s.kind), hour);
        // Gloam's haziness already holds the biome's own murk and the daily thinning; never below the kind's
        s.haze = vec4(max(void_haze.x, hzKindHazy(s.kind) * s.daily), clamp(void_haze.y, 0.0, 1.0),
                      clamp(void_haze.z, 0.0, 1.0), void_haze.w > 0.0 ? clamp(void_haze.w, 0.6, 2.0) : 1.0);
        float snowline = void_seasonState.w;
        if (snowline != 0.0 && camY > snowline) s.snow = 1.0;
    } else {
        s.rain = clamp(rainStrength, 0.0, 1.0);
        s.wet = clamp(wetness, 0.0, 1.0);
        s.kind = HZ_K_TEMPERATE;
        s.temp = 0.5;
        s.outdoor = roughOutdoor;
        if (s.mode == HZ_TILES) {
            // the kind and temperature in the N1 cell under the camera
            ivec4 n1 = hzBytes(hzTexelOf(HZ_T_N1, cameraPositionInt.xz >> HZ_NEAR_SHIFT));
            if ((n1.a & 1) != 0) {
                s.kind = hzKindIndex(n1.a >> 4);
                s.temp = float(n1.g) / 127.5 - 1.0;
            }
        }
        s.daily = hzDaily(hzKindHazy(s.kind), hour);
        s.haze = vec4(hzKindHazy(s.kind) * s.daily, s.kind == HZ_K_DUSTY ? 1.0 : 0.0, 0.0, 1.0);
    }
    s.snow = max(s.snow, 1.0 - smoothstep(-0.45, -0.25, s.temp));
    s.here = hzLandDefault(s.kind);
    if (s.mode != HZ_NONE) {
        // the same two texels for every pixel of the frame: served from cache
        ivec2 cell = cameraPositionInt.xz >> HZ_NEAR_SHIFT;
        ivec4 g = hzBytes(hzTexelOf(HZ_T_N0, cell));
        ivec4 k = hzBytes(hzTexelOf(HZ_T_N1, cell));
        if ((k.a & 1) != 0) {
            s.here.ground = hzHeight16(g.r, g.g);
            s.here.low = float(g.b) / 255.0;
            s.here.water = 1.0 - smoothstep(0.0, 16.0, float(g.a));
            s.here.kind = hzKindIndex(k.a >> 4);
            s.here.source = 1;
        }
    }
    return s;
}

// One cell's land for the bilinear blend; a cell without data reads as the fallback (sea-level ground, the camera's
// kind), weighted like any other, so the land eases into the fallback instead of stopping at a line.
void hzLandCell(HzState s, ivec2 cell, bool far, out vec3 glw, out int kind, out bool ok) {
    ivec4 g = hzBytes(hzTexelOf(far ? HZ_T_F0 : HZ_T_N0, cell));
    ivec4 k = hzBytes(hzTexelOf(far ? HZ_T_F1 : HZ_T_N1, cell));
    ok = (k.a & 1) != 0;
    glw = ok ? vec3(hzHeight16(g.r, g.g), float(g.b) / 255.0,
                    far ? float(g.a) / 255.0 : 1.0 - smoothstep(0.0, 16.0, float(g.a)))
             : vec3(HAZE_SEA_LEVEL, 0.5, 0.0);
    kind = ok ? hzKindIndex(k.a >> 4) : s.kind;
}

// The land in the N (far = false) or F (far = true) tiles at a point, without the reach check: for ray segments whose
// sample distance is known to lie inside that tile's reach (welkinHaze's segment layout guarantees it). Ground,
// lowness and water are blended bilinearly between the four nearest cells (8 fetches, neighbouring pixels share
// them); the kinds become the heaviest one and, if a second is present, the other with its share of the weight.
HzLand hzLandIn(HzState s, vec2 local, bool far) {
    if (s.mode == HZ_NONE) return hzLandDefault(s.kind);
    int size = far ? HZ_FAR_CELL : HZ_NEAR_CELL;
    int shift = far ? HZ_FAR_SHIFT : HZ_NEAR_SHIFT;
    ivec2 cam = cameraPositionInt.xz;
    vec2 f = (vec2(cam & (size - 1)) + local) / float(size) - 0.5;
    vec2 f0 = floor(f);
    vec2 t = f - f0;
    ivec2 c0 = (cam >> shift) + ivec2(f0);
    vec4 w = vec4((1.0 - t.x) * (1.0 - t.y), t.x * (1.0 - t.y), (1.0 - t.x) * t.y, t.x * t.y);
    vec3 a, b, c, d;
    int ka, kb, kc, kd;
    bool oa, ob, oc, od;
    hzLandCell(s, c0, far, a, ka, oa);
    hzLandCell(s, c0 + ivec2(1, 0), far, b, kb, ob);
    hzLandCell(s, c0 + ivec2(0, 1), far, c, kc, oc);
    hzLandCell(s, c0 + ivec2(1, 1), far, d, kd, od);
    HzLand l;
    vec3 glw = a * w.x + b * w.y + c * w.z + d * w.w;
    l.ground = glw.x;
    l.low = glw.y;
    l.water = glw.z;
    // the first kind is the heaviest corner's; the second is any other kind there, with the weight of all corners
    // holding it (a third kind in one square is rare and counts as the first)
    int k1 = ka;
    float best = w.x;
    if (w.y > best) { best = w.y; k1 = kb; }
    if (w.z > best) { best = w.z; k1 = kc; }
    if (w.w > best) { best = w.w; k1 = kd; }
    int k2 = ka != k1 ? ka : (kb != k1 ? kb : (kc != k1 ? kc : kd));
    float w2 = (ka == k2 && ka != k1 ? w.x : 0.0) + (kb == k2 && kb != k1 ? w.y : 0.0)
             + (kc == k2 && kc != k1 ? w.z : 0.0) + (kd == k2 && kd != k1 ? w.w : 0.0);
    l.kind = k1;
    l.kind2 = k2 != k1 ? k2 : k1;
    l.share2 = k2 != k1 ? w2 : 0.0;
    l.source = (oa || ob || oc || od) ? (far ? 2 : 1) : 0;
    return l;
}

// The kinds of a near segment's land blended over 128 blocks instead of N's 16 (4 fetches, on a lattice of every
// eighth N cell): every far pixel reads its middle segment's land at the same distance (about 600 blocks at MEDIUM), so
// a border between kinds crossing there switched the whole segment's murk over 16 blocks, a few pixels, and stood up
// as a vertical wall of dust (or of clear air) over the far land. Over 128 blocks the kinds blend across a wide arc of
// the view. `ground` (0..1) takes the ground, lowness and water over the same lattice as well (4 more fetches): the
// one point the long middle segment reads stands for over a kilometre of land, and where it fell on a knoll or a
// ridge its murk (which grows with the ground's height) lay over the whole segment, so the far land behind it stood
// up in narrow bright columns of haze; blended over 128 blocks the land under the segment varies only gently across
// the view.
#define HZ_KIND_LATTICE_SHIFT 7
void hzSmoothKinds(HzState s, vec2 local, inout HzLand l, float ground) {
    if (s.mode == HZ_NONE) return;
    const int size = 1 << HZ_KIND_LATTICE_SHIFT;
    ivec2 cam = cameraPositionInt.xz;
    vec2 f = (vec2(cam & (size - 1)) + local) / float(size) - 0.5;
    vec2 f0 = floor(f);
    vec2 t = f - f0;
    ivec2 c0 = (cam >> HZ_KIND_LATTICE_SHIFT) + ivec2(f0);
    vec4 w = vec4((1.0 - t.x) * (1.0 - t.y), t.x * (1.0 - t.y), (1.0 - t.x) * t.y, t.x * t.y);
    // the N cell at the middle of each lattice square
    int sh = HZ_KIND_LATTICE_SHIFT - HZ_NEAR_SHIFT;
    ivec2 mid = ivec2(1 << (sh - 1));
    ivec4 ra = hzBytes(hzTexelOf(HZ_T_N1, (c0 << sh) + mid));
    ivec4 rb = hzBytes(hzTexelOf(HZ_T_N1, ((c0 + ivec2(1, 0)) << sh) + mid));
    ivec4 rc = hzBytes(hzTexelOf(HZ_T_N1, ((c0 + ivec2(0, 1)) << sh) + mid));
    ivec4 rd = hzBytes(hzTexelOf(HZ_T_N1, ((c0 + ivec2(1, 1)) << sh) + mid));
    if (((ra.a & rb.a & rc.a & rd.a) & 1) == 0) return; // the near tile does not cover it all: keep the point's
    if (ground > 0.0) {
        ivec4 ga = hzBytes(hzTexelOf(HZ_T_N0, (c0 << sh) + mid));
        ivec4 gb = hzBytes(hzTexelOf(HZ_T_N0, ((c0 + ivec2(1, 0)) << sh) + mid));
        ivec4 gc = hzBytes(hzTexelOf(HZ_T_N0, ((c0 + ivec2(0, 1)) << sh) + mid));
        ivec4 gd = hzBytes(hzTexelOf(HZ_T_N0, ((c0 + ivec2(1, 1)) << sh) + mid));
        float g = hzHeight16(ga.r, ga.g) * w.x + hzHeight16(gb.r, gb.g) * w.y + hzHeight16(gc.r, gc.g) * w.z + hzHeight16(gd.r, gd.g) * w.w;
        vec4 lowness = vec4(ga.b, gb.b, gc.b, gd.b) / 255.0;
        vec4 water = 1.0 - smoothstep(vec4(0.0), vec4(16.0), vec4(ga.a, gb.a, gc.a, gd.a));
        l.ground = mix(l.ground, g, ground);
        l.low = mix(l.low, dot(lowness, w), ground);
        l.water = mix(l.water, dot(water, w), ground);
    }
    int ka = hzKindIndex(ra.a >> 4), kb = hzKindIndex(rb.a >> 4), kc = hzKindIndex(rc.a >> 4), kd = hzKindIndex(rd.a >> 4);
    int k1 = ka;
    float best = w.x;
    if (w.y > best) { best = w.y; k1 = kb; }
    if (w.z > best) { best = w.z; k1 = kc; }
    if (w.w > best) { best = w.w; k1 = kd; }
    int k2 = ka != k1 ? ka : (kb != k1 ? kb : (kc != k1 ? kc : kd));
    float w2 = (ka == k2 && ka != k1 ? w.x : 0.0) + (kb == k2 && kb != k1 ? w.y : 0.0)
             + (kc == k2 && kc != k1 ? w.z : 0.0) + (kd == k2 && kd != k1 ? w.w : 0.0);
    l.kind = k1;
    l.kind2 = k2 != k1 ? k2 : k1;
    l.share2 = k2 != k1 ? w2 : 0.0;
}

// local: the point's x, z relative to the camera's block corner (cameraPositionFract.xz + offset), in blocks.
// The nearest cell, for the debug views only (HAZE_DEBUG 1 and 3): one tile pair is chosen (near inside its reach,
// else far), then both of its texels are fetched. A cell without data (flag bit 0 clear) reads as the fallback: the
// camera's kind over sea-level ground. `dp` is no longer used (it was the dither texel).
HzLand hzLand(HzState s, vec2 local, ivec2 dp) {
    if (s.mode == HZ_NONE) return hzLandDefault(s.kind);
    vec2 b = vec2(0.5);
    ivec2 cam = cameraPositionInt.xz;

    vec2 nf = (vec2(cam & (HZ_NEAR_CELL - 1)) + local) / float(HZ_NEAR_CELL) - 0.5;
    vec2 n0 = floor(nf);
    ivec2 cell = (cam >> HZ_NEAR_SHIFT) + ivec2(n0) + ivec2(step(b, nf - n0));
    ivec2 off = abs(cell - s.nearCentre);
    bool near = float(max(off.x, off.y)) < HZ_FADE_IN + 16.0 * b.x;
    if (!near) {
        vec2 ff = (vec2(cam & (HZ_FAR_CELL - 1)) + local) / float(HZ_FAR_CELL) - 0.5;
        vec2 f0 = floor(ff);
        cell = (cam >> HZ_FAR_SHIFT) + ivec2(f0) + ivec2(step(b, ff - f0));
        off = abs(cell - s.farCentre);
        if (float(max(off.x, off.y)) >= HZ_FADE_IN + 16.0 * b.y) return hzLandDefault(s.kind);
    }
    ivec4 g = hzBytes(hzTexelOf(near ? HZ_T_N0 : HZ_T_F0, cell));
    ivec4 k = hzBytes(hzTexelOf(near ? HZ_T_N1 : HZ_T_F1, cell));
    if ((k.a & 1) == 0) return hzLandDefault(s.kind);
    HzLand d;
    d.ground = hzHeight16(g.r, g.g);
    d.low = float(g.b) / 255.0;
    // near: water distance in 4 m steps (within 64 m counts); far: the cell's water fraction
    d.water = near ? 1.0 - smoothstep(0.0, 16.0, float(g.a)) : float(g.a) / 255.0;
    d.kind = hzKindIndex(k.a >> 4);
    d.kind2 = d.kind;
    d.share2 = 0.0;
    d.source = near ? 1 : 2;
    return d;
}

#endif
