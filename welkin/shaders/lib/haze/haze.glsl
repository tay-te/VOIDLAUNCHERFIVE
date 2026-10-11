#ifndef WELKIN_HAZE
#define WELKIN_HAZE

// VOID Welkin, module "haze" (M2): height haze by country, and the regional rain look, for the `composite` pass.
//
//   #include "/lib/haze/haze.glsl"
//   HzState  st = hzState();
//   WelkinHaze h = welkinHaze(st, rel, n, isSky, split, air, gl_FragCoord.xy);
//   c = welkinHazeBack(c, h);      // haze behind the mist (and the world's edge)
//   c = <valley mist (M1)>;        // tinted with h.air if it likes
//   c = welkinHazeFront(c, h);     // haze between the camera and the mist
//
// The model: exact formulas, no marching. Three media along the view ray, each integrated in closed form:
//   clear air  rho = thickness * clearMul / clearReach * exp((seaLevel - y) / 160)        (VOID LOD's lod.vsh haze)
//   murk       rho = 2.5 (1/reach(h) - 1/1024) * exp((ground - y) / H_kind) * hollows * water * rain
//   rain       rho = r / 660, plus 160 r^2 / 660 at the camera                             (VOID LOD's rain curtain)
// For an exponential layer the optical depth between two points of the ray is exact:
//   tau = rho0 * (t1 - t0) * (e^e1 - e^e0) / (e1 - e0),   e = (base - y) / H at either end (capped, see hzLayer2)
// Clear air and rain are one integral each over the whole ray. The murk changes with the land, so the ray is cut
// into HAZE_SAMPLES segments at fixed distances; each reads the land once (the first is the camera's own cell, the
// rest one N or F tile pair: ground, lowness, water, haze kind) and integrates its murk exactly. The two kinds
// with the most murk (weighted by how much of it is seen past what lies in front) colour the haze.
// The result: one colour and two opacities, in front of and behind the split distance where M1's mist slab begins,
// all smooth (the look of 2026-10-11): worked out at the pixel's own point, the land blended bilinearly between tile
// cells, no steps and no dither.

#include "/lib/haze/haze_config.glsl"
#include "/lib/haze/haze_uniforms.glsl"
#include "/lib/haze/haze_pixel.glsl"
#include "/lib/haze/haze_bridge.glsl"

struct WelkinHaze {
    vec4  back;         // rgb colour, a opacity: from the split to the surface, with the world's edge
    vec4  front;        // rgb colour, a opacity: from the camera to the split
    vec3  air;          // the air colour after the rain look, for other modules (mist, curtains) to match
    float rainLook;     // 0..1, how much of the rain look is on (for debug and for others)
    ivec2 dp;           // the screen pixel (was the dither texel; kept for the debug views)
};

// ------------------------------------------------------------------------------------------------------- reaches
// How far the drawn world reaches, in blocks: VOID LOD's radius (Iris' dhRenderDistance, which LOD feeds), else the
// vanilla render distance.
float hzWorldReach() {
    float r = far;
#ifdef DISTANT_HORIZONS
    if (dhRenderDistance > 0) r = max(r, float(dhRenderDistance));
#endif
    return max(r, 32.0);
}

// The murk's density at haziness h (0..1 on Gloam's log scale): what the old biome fog reached, as an optical depth
// of HZ_MURK at that reach, less what clear air already has (so h = 0 adds nothing).
float hzMurkDensity(float h) {
    float reach = max(HZ_MURK_FLOOR, HZ_MURKIEST * exp2(4.0 * (1.0 - clamp(h, 0.0, 1.0))));
    return HZ_MURK * max(0.0, 1.0 / reach - 1.0 / HZ_CLEAR_END);
}

// The mean of exp(min(e, cap)) as e runs linearly from x to y, given Ex = exp(min(x, cap)) and Ey likewise. Exact
// and branch-free: the part under the cap integrates in closed form, the part over it is flat at exp(cap) (ecap); a
// near-level piece takes the value at its low end.
float hzMeanExp(float x, float y, float Ex, float Ey, float ecap, float cap) {
    float span = abs(y - x);
    float m = (max(Ex, Ey) - min(Ex, Ey) + ecap * abs(max(y, cap) - max(x, cap))) / max(span, 1e-4);
    return span < 1e-4 ? min(Ex, Ey) : m;
}

// Optical depth of one exponential layer (density rho0 * exp((base - y) / H), capped `cap` e-foldings over rho0 so a
// ray running down into a valley below the base or into a cave cannot blow up) along dir from ta to tc and from ta
// to tb: x the piece before the mist's split, y the whole. Exact, with three exponentials for both, written with the
// exponents at the ends so nothing overflows however long the ray or steep its slope.
vec2 hzLayer2(float rho0, float base, float H, float ta, float tc, float tb, vec3 dir, float camY, float cap) {
    float ea = (base - (camY + dir.y * ta)) / H;
    float k = dir.y / H;
    float ec = ea - k * (tc - ta);
    float eb = ea - k * (tb - ta);
    float Ea = exp(min(ea, cap)), Eb = exp(min(eb, cap));
    float ecap = exp(cap);
    float whole = (tb - ta) * hzMeanExp(ea, eb, Ea, Eb, ecap, cap);
    // most rays are not cut by the mist (a coherent branch: only the slab's edge on screen differs)
    float piece = whole;
    if (tc < tb) {
        float Ec = exp(min(ec, cap));
        piece = (tc - ta) * hzMeanExp(ea, ec, Ea, Ec, ecap, cap);
    }
    return rho0 * vec2(piece, whole);
}

// The world's edge: land sinks fully into the air before the drawn world ends, smoothly. The same curve as
// M0's farFade (lib/fade.glsl: horizontal distance, from half the LOD's reach, or the last fifth of the render
// distance without it), which this replaces: with the haze on, deferred skips its own FAR_FADE (NOTES step 4) and
// the edge takes the haze's air colour, over water too.
float hzEdgeFade(float horizontal) {
#ifdef DISTANT_HORIZONS
    float edge = max(float(dhRenderDistance), far);
    float t = clamp((horizontal - edge * 0.5) / (edge * 0.5), 0.0, 1.0);
#else
    float t = clamp((horizontal - far * 0.8) / (far * 0.2), 0.0, 1.0);
#endif
    return t * t;
}

// ------------------------------------------------------------------------------------------------------- colours
// The air colour under rain or snow: darker and cooler (Gloam Palette.OVERCAST_FOG, darker), flat over the frame;
// it changes only as the rain at the camera does.
vec3 hzRainAir(vec3 air, HzState s) {
    if (s.rain * s.outdoor <= 0.0) return air;          // the frame-wide common case, a uniform branch
    vec3 wetAir = mix(hzTone(HZ_RAIN_AIR, air, 0.82), hzTone(HZ_SNOW_AIR, air, 1.0), s.snow);
    return mix(air, wetAir, 0.75 * s.rain * s.outdoor);
}

// How much the kinds tint the air now: fully by day, a quarter at night (the murk is still there, but at night it
// is the dark air's colour), and little in rain, where everything greys.
float hzKindStrength(HzState s) {
    float day = smoothstep(-0.05, 0.12, sin(6.2831853 * sunAngle));
    return (0.25 + 0.75 * day) * (1.0 - 0.7 * s.rain);
}

// A kind's toned tint with the season's touch (autumn warmer, winter cooler) on all but dust.
vec3 hzSeasonTint(vec3 toned, int k, HzState s) {
    if (k == HZ_K_DUSTY) return toned;
    return s.season == 2 ? mix(toned, HZ_AUTUMN, 0.3) : (s.season == 3 ? mix(toned, HZ_WINTER, 0.25) : toned);
}

// ------------------------------------------------------------------------------------------------------ the haze
// The murk along the ray is cut into segments at fixed distances, each reading the land once. The layout puts each
// segment's sample inside a known tile, so no per-pixel reach checks are needed: the first segment is the camera's
// own cell (no fetch), middle ones sample N (16 m cells, sample under 1.8 km), the last samples F (1 km cells, from
// 2 km out). Edges in blocks:
//   HAZE_SAMPLES 2 (LOW)     0 | 2048 F | ...
//   HAZE_SAMPLES 3 (MEDIUM)  0 | 192 N | 2048 F | ...
//   HAZE_SAMPLES 4 (HIGH)    0 | 96 N | 640 N | 4096 F | ...
// A sample sits at the geometric middle of its segment (the arithmetic middle for the first), so a long far segment
// reads the land a fifth of the way out, not halfway to the horizon.

// The running sums of the murk: optical depth before the split and in all (tm), and the two kinds seen most.
struct HzMurk {
    vec2  tm;
    int   k1;
    float w1;
    int   k2;
    float w2;
};

// Counts `seen` murk of kind k into the two kinds seen most (a third on one ray is rare: it takes the second's place
// only if it outweighs it).
void hzMurkAddKind(inout HzMurk m, int k, float seen) {
    bool in1 = k == m.k1;
    bool in2 = !in1 && (k == m.k2 || m.w2 <= 0.0);
    bool take = !in1 && !in2 && seen > m.w2;
    m.w1 += in1 ? seen : 0.0;
    m.k2 = (in2 || take) ? k : m.k2;
    m.w2 = in2 ? m.w2 + seen : (take ? seen : m.w2);
    bool swap = m.w2 > m.w1;
    int kk = m.k1;
    m.k1 = swap ? m.k2 : m.k1;
    m.k2 = swap ? kk : m.k2;
    float ww = m.w1;
    m.w1 = swap ? m.w2 : m.w1;
    m.w2 = swap ? ww : m.w2;
}

void hzMurkSegment(inout HzMurk m, HzState s, HzLand land, float a, float b, float split, vec3 dir, float camY,
                   float murkMul, float seenBy) {
    if (b <= a) return;
    int k = land.kind;
    // the air of the land's kinds, blended by their shares, so density runs smoothly over a border between kinds
    vec3 ka = mix(hzKindAir(k), hzKindAir(land.kind2), land.share2);
    // its haziness thinned by the hour, or the camera's own (a cloud forest in a clear country) near the camera
    float tS = a <= 0.0 ? 0.5 * b : sqrt(a * b);
    float hz = max(ka.x * s.daily, s.haze.x * exp2(-tS / 1024.0));
    float rho = hzMurkDensity(hz) * murkMul * (0.75 + 0.5 * land.low) * (1.0 + 0.3 * land.water);
    vec2 t = hzLayer2(rho, land.ground, ka.y, a, clamp(split, a, b), b, dir, camY, HZ_MURK_CAP);
    // how much of this murk is seen, past everything in front of it (1 / (1 + x + x^2/2) for e^-x: it only ranks
    // the kinds, and spares the exponential)
    float x = m.tm.y + seenBy * a;
    float seen = t.y / (1.0 + x * (1.0 + 0.5 * x));
    m.tm += t;
    // Far off, the air's colour is the sky's in that direction, whatever country lies there: a far country's kind
    // tints its land less with distance (from 3 km, gone by 16 km) and the camera's own kind takes its share. Else a
    // dusty country's land far off, fully hazed in ochre against the blue sky over it, stood up as glowing columns.
    float own = smoothstep(3000.0, 16000.0, b);
    hzMurkAddKind(m, k, seen * (1.0 - land.share2) * (1.0 - own));
    if (land.share2 > 0.0) hzMurkAddKind(m, land.kind2, seen * land.share2 * (1.0 - own));
    if (own > 0.0) hzMurkAddKind(m, s.here.kind, seen * own);
}

// The land for a segment's sample: N or F, at distance tS along the ray. The sky reads the camera's own land for
// every segment: a sky ray climbs out of the murk within a few hundred blocks, and the land far under it would only
// print its 1 km cells into the sky as blocks of colour (and cost fetches).
HzLand hzSegmentLand(HzState s, bool sky, vec3 dir, float a, float b, bool far) {
    if (sky) return s.here;
    // The far segment (2 km out to the land, 1 km cells) reads the land where the ray ends, the land seen. Read at the
    // segment's geometric middle, the far land's pixels took the ground and kind of a cell tens of km in front of
    // them: wherever that point fell on high ground (a ridge, a plateau) its murk lay over the whole segment, and the
    // pixels whose middles shared that cell stood up as a bright column of haze over the land behind it. Read at the
    // end, the haze follows the land seen, smoothly. The near segments keep the middle (16 m cells, short spans).
    float tS = far ? b : sqrt(a * max(b, a));
    return hzLandIn(s, cameraPositionFract.xz + dir.xz * tS, far);
}

// The haze for one pixel.
//   s      hzState(), once per pixel (or flat from the vertex shader)
//   rel    the surface point relative to the camera (eye), world axes; for the sky, any point along the view ray
//   n      the surface's world normal (any sign; hzScreenNormal(rel) if the program has none); ignored on the sky
//   isSky  nothing was drawn here (both depths at the far plane)
//   split  where M1's mist slab begins along this ray, in blocks; < 0 or >= the surface distance for "no mist"
//   air    the sky's colour toward the horizon in this view direction (the sky table's horizon row at this azimuth,
//          skyHazeHorizon); the haze converges to it, so far land meets the sky without a seam
//   frag   gl_FragCoord.xy
//   edge   whether the world's far edge applies (false for a cloud, which stands in the sky past the land's edge)
WelkinHaze welkinHazeEx(HzState s, vec3 rel, vec3 n, bool isSky, float split, vec3 air, vec2 frag, bool edge) {
    WelkinHaze h;
    h.air = air;
    h.rainLook = 0.0;
    h.dp = ivec2(frag);
    h.back = vec4(air, 0.0);
    h.front = vec4(air, 0.0);
#ifdef HAZE_ENABLED
#ifndef HAZE_ON_SKY
    if (isSky) return h;
#endif
    // under the ground the cave fog (M5) owns the air
    if (s.outdoor <= 0.0) return h;
    float L = length(rel);
    vec3 dir = rel / max(L, 1e-4);
    float reach = hzWorldReach();
    float camY = float(cameraPositionInt.y) + cameraPositionFract.y;

    // worked out at the pixel's own point (no texel snapping: the haze is a smooth gradient over the land)
    if (isSky) {
        L = reach;
    }
    // the world's edge is measured at the real surface, whoever owns the haze past the near tiles
    float edgeH = length(dir.xz) * L;
#if defined WF_FAR_HAZE && defined DISTANT_HORIZONS
    // With far weather's far haze on (WF_FAR_HAZE, an option in the shared lib/settings.glsl), far weather
    // (composite1, wf_haze.glsl) integrates the same clear air from the near tiles' fade band outwards
    // (1792..2032 blocks horizontal); this pass then owns only the ray up to the band, so the two add up to one
    // integral and LOD land is never hazed twice (wave-2 cross-check, INTEGRATION.md). The hand-over is at the band's
    // middle, with no dither: far weather must start its integral at the same line (see INTEGRATION.md's note).
    if (!isSky) L = min(L, 0.5 * (HZ_HANDOFF_START + HZ_HANDOFF_END) / max(length(dir.xz), 1e-4));
#endif
    split = (split < 0.0) ? L : clamp(split, 0.0, L);

    vec3 airR = hzRainAir(air, s);
    h.air = airR;
    h.rainLook = s.rain * s.outdoor;

    // clear air: one exact integral over the whole ray, in the camera's kind of air; and the rain curtain, which
    // starts behind the camera (VOID LOD's offset, in the front part when there is one)
    vec2 tc = hzLayer2(s.haze.w * hzKindAir(s.here.kind).z / max(HZ_CLEAR_MIN, HZ_CLEAR_SHARE * reach),
                       HAZE_SEA_LEVEL, HZ_CLEAR_H, 0.0, split, L, dir, camY, HZ_CLEAR_CAP);
    float rainRho = s.rain * s.outdoor * HAZE_RAIN * mix(1.0, 1.4, s.snow) / HZ_RAIN_REACH;
    float offset = HZ_RAIN_OFFSET * s.rain;
    vec2 tr = rainRho * vec2(split > 0.0 ? split + offset : 0.0, L + offset);
    float seenBy = (tc.y + tr.y) / max(L, 1e-4);        // clear air and rain per block, for the kinds' weights

    // the murk, segment by segment
    float murkMul = HAZE_STRENGTH * s.outdoor * (1.0 + 0.6 * s.rain);
    HzMurk m = HzMurk(vec2(0.0), s.here.kind, 0.0, s.here.kind, 0.0);
#if HAZE_SAMPLES == 2
    hzMurkSegment(m, s, s.here, 0.0, min(2048.0, L), split, dir, camY, murkMul, seenBy);
    if (L > 2048.0) hzMurkSegment(m, s, hzSegmentLand(s, isSky, dir, 2048.0, L, true), 2048.0, L, split, dir, camY, murkMul, seenBy);
#elif HAZE_SAMPLES == 3
    hzMurkSegment(m, s, s.here, 0.0, min(192.0, L), split, dir, camY, murkMul, seenBy);
    if (L > 192.0) {
        float e = min(2048.0, L);
        hzMurkSegment(m, s, hzSegmentLand(s, isSky, dir, 192.0, e, false), 192.0, e, split, dir, camY, murkMul, seenBy);
    }
    if (L > 2048.0) hzMurkSegment(m, s, hzSegmentLand(s, isSky, dir, 2048.0, L, true), 2048.0, L, split, dir, camY, murkMul, seenBy);
#else
    hzMurkSegment(m, s, s.here, 0.0, min(96.0, L), split, dir, camY, murkMul, seenBy);
    if (L > 96.0) {
        float e = min(640.0, L);
        hzMurkSegment(m, s, hzSegmentLand(s, isSky, dir, 96.0, e, false), 96.0, e, split, dir, camY, murkMul, seenBy);
    }
    if (L > 640.0) {
        float e = min(4096.0, L);
        hzMurkSegment(m, s, hzSegmentLand(s, isSky, dir, 640.0, e, false), 640.0, e, split, dir, camY, murkMul, seenBy);
    }
    if (L > 4096.0) hzMurkSegment(m, s, hzSegmentLand(s, isSky, dir, 4096.0, L, true), 4096.0, L, split, dir, camY, murkMul, seenBy);
#endif
    vec2 tm = m.tm;
    int k1 = m.k1, k2 = m.k2;
    float w1 = m.w1, w2 = m.w2;

    // one colour: the air, pulled toward the ray's two kinds (by their shares), as far as the murk's share of the
    // haze goes
    float tauF = tc.x + tr.x + tm.x;
    float tauW = tc.y + tr.y + tm.y;
    vec3 col = airR;
    if (tm.y > 1e-5) {
        float t = w2 / max(w1 + w2, 1e-9);
        vec4 look = hzKindLook(k1);
        vec3 tint = hzSeasonTint(look.rgb, k1, s);
        if (t > 0.0) {
            vec4 look2 = hzKindLook(k2);
            tint = mix(tint, hzSeasonTint(look2.rgb, k2, s), t);
            look.a = mix(look.a, look2.a, t);
        }
        // dust on the wind (Gloam's dust share) over a land whose own kind is not dust
        if (s.haze.y > 0.0 && k1 != HZ_K_DUSTY) {
            tint = mix(tint, hzKindLook(HZ_K_DUSTY).rgb, clamp(s.haze.y * 0.6, 0.0, 1.0));
        }
        vec3 kc = clamp(tint * hzLuma(airR), 0.0, 1.0);
        col = mix(airR, kc, clamp(tm.y / max(tauW, 1e-9), 0.0, 1.0) * look.a * hzKindStrength(s));
    }

    // two opacities: the camera to the split, then the split to the surface; the world's edge under the back
    float aFront = 1.0 - exp(-tauF);
    float aBack = 1.0 - exp(tauF - tauW);
    float e = isSky || !edge ? 0.0 : hzEdgeFade(edgeH);
    float A = 1.0 - (1.0 - e) * (1.0 - aBack);
    h.front = vec4(col, aFront);
    h.back = vec4(A > 1e-4 ? (airR * e * (1.0 - aBack) + col * aBack) / A : col, A);
#endif
    return h;
}

WelkinHaze welkinHaze(HzState s, vec3 rel, vec3 n, bool isSky, float split, vec3 air, vec2 frag) {
    return welkinHazeEx(s, rel, n, isSky, split, air, frag, true);
}

vec3 welkinHazeBack(vec3 c, WelkinHaze h) {
    return mix(c, h.back.rgb, h.back.a);
}

vec3 welkinHazeFront(vec3 c, WelkinHaze h) {
    return mix(c, h.front.rgb, h.front.a);
}

// Without M1's mist (or before it lands): the whole haze at once.
vec3 welkinHazeAll(vec3 c, WelkinHaze h) {
    return welkinHazeFront(welkinHazeBack(c, h), h);
}

#endif
