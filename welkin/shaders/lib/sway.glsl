// VOID Welkin sway: wind movement for leaves and plants, driven by the material class's sway kind.
// Call from gbuffers_terrain.vsh AND shadow.vsh with the same inputs so shadows move with the leaves.
//
//   vec3 off = welkinSway(m, worldPos, at_midBlock.xyz, frameTimeCounter, wind);
//   worldPos += off;      // worldPos = absolute block-space position (playerPos + cameraPosition)
//
// wind: void_wind when the bridge is live (unit x, unit z, blocks per second, gust 0..1),
//       otherwise welkinWindFallback(rainStrength).
// Cost: one sin/cos pair and a handful of multiplies per vertex, only for swaying classes.
// Needs lib/settings.glsl and lib/materials.glsl first.
#ifndef WELKIN_SWAY
#define WELKIN_SWAY

vec4 welkinWindFallback(float rain) {
    return vec4(0.8, 0.6, 2.5 + 6.0 * rain, 0.25 + 0.5 * rain);
}

// ---- D11: travelling gust fronts -----------------------------------------------------------------------------------
// A world-space gust field that rolls along the wind: broad, soft-edged bands across the wind, a few to tens of blocks
// deep, with calm between them, carried downwind at about the wind's speed. Neighbouring blocks share it, so a front
// bends a whole swathe of meadow together, and the crowns heave as it passes.
//
//   vec2 gs = welkinGust(absXZ, t, wind, storm, coarse);  // absXZ: absolute world x, z (NOT wrapped); x the gust 0..1, y the
//                                                 // lean signal (the gust less the rebound behind it), -0.5..1
//
// The field is two octaves of value noise in a frame stretched across the wind (WG_ALONG deep, WG_ACROSS long),
// thresholded so the gustier the air (Gloam's gust, the wind's speed, the storm) the more of it is front.
// - Carried with the wind as P - dir * V * t. Over a long clock a change of V or dir would sweep the field (V' * t),
//   so the clock runs in two staggered cycles of WG_PERIOD seconds, cross-faded (a flow map's two phases, each alone
//   for 60 % of its cycle, handing over in 12 s); each cycle draws a fresh field, so no front repeats. The drift a
//   changing wind can add is bounded by its change over a cycle.
// - The stretched frame is one of 16 fixed headings (the nearest, the two round the wind blended in the middle 40 %
//   between them); fixed, so it never swings round the world's origin as the wind turns (a far camera would see the
//   field race sideways).
// - The blends keep the field's contrast (weights renormalised by their root sum of squares).
// Cost: 2 octaves of value noise per vertex most of the time, up to 4 frames x 2 during a hand-over (the trough from the
// noise's own slope, no second point; crowns the first octave only).
#define WG_PERIOD 60.0
#define WG_ALONG  22.0  // the field's scale along the wind, blocks (a front's depth and the gap behind it)
#define WG_ACROSS 90.0  // across the wind (how long a front runs)
#define WG_SOFT   0.15  // the threshold's soft edge (in field units: the front's border)
#define WG_TROUGH 0.16  // the trough behind a front: how far under the threshold it reaches (field units)
#define WG_REBOUND 0.45 // how far blades swing back behind a front (in gust units)
// the sheen (program/gbuffers_common.glsl, the albedo of gusted plants and leaves, times WIND_SHEEN)
#define WG_SHEEN_LIFT   0.14                    // lighter in a full gust (albedo, display-referred)
#define WG_SHEEN_SILVER 0.30                    // and that much toward a silvery olive grey
#define WG_SHEEN_TINT   vec3(0.99, 1.03, 0.93)  // the silvery olive (times the albedo's luminance)
#define WG_SHEEN_TROUGH 0.20                    // darker per unit of rebound (the rebound is at most about 0.45)
#define WG_SHEEN_LEAVES 0.55                    // a crown's share
#define WG_SHEEN_GROUND 0.85                    // a grass block's top (the meadow's ground: the short grass on it)

// value noise and its slope along x (per cell): two hashes' worth of ALU per corner, the slope from the same corners
vec2 welkinGustVal(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float du = 30.0 * f.x * f.x * (f.x * (f.x - 2.0) + 1.0);
    ivec2 c = ivec2(i);
    uvec4 h = uvec4(uint(c.x), uint(c.x + 1), uint(c.x), uint(c.x + 1)) * 0x8da6b343u
            ^ uvec4(uint(c.y), uint(c.y), uint(c.y + 1), uint(c.y + 1)) * 0xd8163841u;
    h ^= h >> 13u;
    h *= 0x5bd1e995u;
    h ^= h >> 15u;
    vec4 v = vec4(h & 0xffffu) * (1.0 / 65535.0);
    float k = v.x - v.y - v.z + v.w;
    return vec2(v.x + (v.y - v.x) * u.x + (v.z - v.x) * u.y + k * u.x * u.y, du * ((v.y - v.x) + k * u.y));
}

// one cycle's field in one fixed frame (d: heading, o: the cycle's offset): two octaves, the second sheared a little.
// x the value, y its slope along the heading (per WG_ALONG)
vec2 welkinGustOne(vec2 q, vec2 d, vec2 o, bool coarse) {
    vec2 u = vec2(dot(q, d), dot(q, vec2(-d.y, d.x)));
    vec2 a = welkinGustVal(u * vec2(1.0 / WG_ALONG, 1.0 / WG_ACROSS) + o);
    if (coarse) return vec2(0.5 + 1.15 * (a.x - 0.5), 1.15 * a.y); // (the first octave alone, at about the pair's contrast)
    vec2 b = welkinGustVal(vec2(u.x + 0.3 * u.y, u.y) * vec2(1.0 / (0.45 * WG_ALONG), 1.0 / (0.5 * WG_ACROSS)) + o.yx + 5.3);
    return vec2(0.65 * a.x + 0.35 * b.x, 0.65 * a.y + 0.35 * b.y * (1.0 / 0.45));
}

// the raw field round 0.5 at absolute xz P (the cycles and headings blended, contrast kept), and its slope downwind
vec2 welkinGustRaw(vec2 P, vec2 dir, float V, float t, bool coarse) {
    float hd = atan(dir.y, dir.x) * (8.0 / 3.14159265);
    float k0 = floor(hd);
    float fk = hd - k0;
    vec2 d0 = vec2(cos(k0 * 0.39269908), sin(k0 * 0.39269908));
    vec2 d1 = vec2(cos((k0 + 1.0) * 0.39269908), sin((k0 + 1.0) * 0.39269908));
    float sA = t / WG_PERIOD, sB = sA + 0.5;
    float cA = floor(sA), cB = floor(sB);
    float tA = (sA - cA) * WG_PERIOD, tB = (sB - cB) * WG_PERIOD;
    // A shows alone for the middle of its cycle and hands over to B round its restart (and back), so most of the time
    // only one cycle is worked out; likewise the headings blend only near the middle between two (an uniform branch:
    // the clock and the wind are the same for every vertex, so it never diverges)
    float xA = sA - cA;
    float wA = smoothstep(0.0, 0.2, xA) * (1.0 - smoothstep(0.8, 1.0, xA));
    float wB = 1.0 - wA;
    fk = smoothstep(0.3, 0.7, fk);
    // each cycle a fresh field (offset by its index), the two cycles apart
    vec2 oA = vec2(fract(cA * 0.7548777), fract(cA * 0.5698403)) * 977.0;
    vec2 oB = vec2(fract(cB * 0.7548777), fract(cB * 0.5698403)) * 977.0 + vec2(431.0, 217.0);
    vec2 qA = P - dir * (V * tA);
    vec2 qB = P - dir * (V * tB);
    float w00 = wA * (1.0 - fk), w01 = wA * fk, w10 = wB * (1.0 - fk), w11 = wB * fk;
    vec2 n = vec2(0.0);
    if (w00 > 0.0) n += w00 * (welkinGustOne(qA, d0, oA, coarse) - vec2(0.5, 0.0));
    if (w01 > 0.0) n += w01 * (welkinGustOne(qA, d1, oA, coarse) - vec2(0.5, 0.0));
    if (w10 > 0.0) n += w10 * (welkinGustOne(qB, d0, oB, coarse) - vec2(0.5, 0.0));
    if (w11 > 0.0) n += w11 * (welkinGustOne(qB, d1, oB, coarse) - vec2(0.5, 0.0));
    return vec2(0.5, 0.0) + n * inversesqrt(w00 * w00 + w01 * w01 + w10 * w10 + w11 * w11 + 1e-4);
}

// how gusty the air is, 0 calm .. 1 a storm's gale: sets how much of the field is front and how hard it pushes
float welkinGustiness(vec4 wind, float storm) {
    return clamp(WG_BASE + 0.30 * wind.w + 0.03 * clamp(wind.z, 0.0, 20.0) + 0.6 * storm, 0.0, 1.0);
}

// x the gust 0..1, y the lean signal: the gust, less the rebound just behind a front (upwind of it, where the field
// climbs downwind toward the band), found from the field's own slope, so one point is enough
// coarse: the first octave only (crowns: they heave as a whole, and a tree's leaves are the most vertices by far)
vec2 welkinGust(vec2 P, float t, vec4 wind, float storm, bool coarse) {
#if SWAY_FREEZE != 0
    t = float(SWAY_FREEZE);
#endif
    vec2 dir = normalize(wind.xy + vec2(1e-4, 0.0));
    float V = 2.0 + 0.6 * clamp(wind.z, 0.0, 20.0); // the fronts' speed, blocks a second
    float G = welkinGustiness(wind, storm);
    float th = mix(0.67, 0.50, G);
    float amp = mix(0.5, 1.0, G) * WIND_GUSTS;
    vec2 n = welkinGustRaw(P, dir, V, t, coarse);
    float g = smoothstep(th, th + WG_SOFT, n.x);
    float behind = (1.0 - g) * smoothstep(th - WG_TROUGH, th - 0.02, n.x) * clamp(n.y * 2.0, 0.0, 1.0);
    return vec2(g, g - WG_REBOUND * behind) * amp;
}

// ---- D11: the gust map --------------------------------------------------------------------------------------------
// The field costs too much to work out per vertex in gbuffers_terrain (some 0.3 ms at the plain: the inlined noise
// slowed every terrain vertex, gusted or not), so prepare works it out once a frame on a world-anchored grid of
// WG_MAP_CELL-block cells, 128 x 128 wrapping (+-256 blocks round the camera), into colortex5 beside the sky table
// (x WG_MAP_X.., x the gust, y the lean signal); the terrain reads it with four texel fetches and a bilinear blend.
// (A 256 x 256 map of 2-block cells cost 0.2 ms more: the prepare pass's area, whatever it writes.)
// The shadow pass runs before prepare and still works the field out itself (few vertices there).
#define WG_MAP_X    64
#define WG_MAP_CELL 4.0

// the world cell a map texel holds this frame (cam: cameraPositionInt.xz), and the texel of a world cell
ivec2 welkinGustCellOf(ivec2 texel, ivec2 cam) {
    ivec2 base = (cam >> 2) - 64;
    return base + ((texel - base) & 127);
}
ivec2 welkinGustTexel(ivec2 cell) { return (cell & 127) + ivec2(WG_MAP_X, 0); }

#ifdef WELKIN_GUST_MAP
vec2 welkinGustMap(vec2 absXZ) {
    vec2 f = absXZ * (1.0 / WG_MAP_CELL);
    vec2 i = floor(f);
    f -= i;
    ivec2 c = ivec2(i);
    vec2 a = texelFetch(colortex5, welkinGustTexel(c), 0).xy;
    vec2 b = texelFetch(colortex5, welkinGustTexel(c + ivec2(1, 0)), 0).xy;
    vec2 d = texelFetch(colortex5, welkinGustTexel(c + ivec2(0, 1)), 0).xy;
    vec2 e = texelFetch(colortex5, welkinGustTexel(c + ivec2(1, 1)), 0).xy;
    return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}
#endif

// at_midBlock.xyz is (block centre - vertex) * 64, so a vertex above the centre has y < 0.
bool welkinTopVertex(vec3 midBlock)    { return midBlock.y < 0.0; }

// D11: the push a front gives: WG_LEAN blocks at a plant's tip in a full gust (about two texture pixels, on top of the
// flutter's two), a quick flutter WG_FLUTTER deep while the front is over it, and the crowns' heave WG_LEAF_PUSH of
// the plants' lean (a crown moves as one, so it shifts rather than bends)
#define WG_LEAN      0.13
#define WG_FLUTTER   0.035
#define WG_LEAF_PUSH 0.6

// gs: welkinGust(...) at this vertex (vec2(0.0) for none)
vec3 welkinSway(int m, vec3 worldPos, vec3 midBlock, float t, vec4 wind, vec2 gs) {
    int kind = matSway(m);
    if (kind == SWAY_NONE) return vec3(0.0);
#if SWAY_FREEZE != 0
    t = float(SWAY_FREEZE); // (Look D2) a test clock: the sway held, so a shot repeats (lib/settings.glsl)
#endif
#ifndef WAVING_LEAVES
    if (kind == SWAY_LEAVES) return vec3(0.0);
#endif
#ifndef WAVING_PLANTS
    if (kind == SWAY_PLANT || kind == SWAY_TALL_LOWER || kind == SWAY_TALL_UPPER || kind == SWAY_HANGING ||
        kind == SWAY_STALK || kind == SWAY_WALL || kind == SWAY_CROP) return vec3(0.0);
#endif
#ifndef WAVING_WATER_PLANTS
    if (kind == SWAY_UNDERWATER || kind == SWAY_FLOAT) return vec3(0.0);
#endif
    vec2 dir = normalize(wind.xy + vec2(1e-4, 0.0));
    float speed = clamp(wind.z, 0.0, 20.0);
    // Phase runs along the wind, so gusts visibly roll across a field.
    float phase = dot(worldPos.xz, dir) * 0.35 + worldPos.y * 0.2 - t * (0.9 + 0.12 * speed);
    float gust = 1.0 + wind.w * sin(phase * 0.23 - t * 0.31);
    // Amplitude in blocks: at most 2 texture pixels, so blocks never look rubbery.
    float amp = min((0.025 + 0.010 * speed) * gust * WAVE_STRENGTH, 0.125);
    float s = sin(phase), c = cos(phase * 1.3 + 1.7);
    vec3 dir3 = vec3(dir.x, 0.0, dir.y);
    vec3 side = vec3(-dir.y, 0.0, dir.x);
    vec3 lean = dir3 * (0.6 + 0.4 * s) + side * 0.3 * c;
    // D11: the front. A lean downwind that grows as it arrives and swings back a little behind it (gs.y), the tip
    // dipping as it bends; and while it is over the plant a quicker, deeper flutter.
    float quick = sin(phase * 1.9 + t * 5.3) * gs.x * WG_FLUTTER * WAVE_STRENGTH;
    vec3 push = (dir3 - vec3(0.0, 0.3 * max(gs.y, 0.0), 0.0)) * (gs.y * WG_LEAN * WAVE_STRENGTH)
              + (dir3 * 0.5 + side * 0.5 * c + vec3(0.0, 0.3, 0.0)) * quick;

    bool top = welkinTopVertex(midBlock);
    float w = 0.0;
    if (kind == SWAY_LEAVES) {
        return (lean * 0.55 + vec3(0.0, 0.25 * c, 0.0)) * amp * (1.0 + 0.5 * gs.x) + push * WG_LEAF_PUSH;
    }
    else if (kind == SWAY_PLANT)      w = top ? 1.0 : 0.0;
    else if (kind == SWAY_CROP)       w = top ? 0.6 : 0.0;
    else if (kind == SWAY_TALL_LOWER) w = top ? 0.5 : 0.0;
    else if (kind == SWAY_TALL_UPPER) w = top ? 1.0 : 0.5;
    else if (kind == SWAY_HANGING)    w = top ? 0.0 : 0.8;
    else if (kind == SWAY_STALK)      w = top ? 0.35 : 0.0;
    else if (kind == SWAY_WALL)       { w = 0.25; push = vec3(0.0); }
    else if (kind == SWAY_UNDERWATER) {
        // Slow current, not wind: kelp keeps moving on a still day.
        float u = sin(dot(worldPos.xz, vec2(0.31, 0.27)) - t * 0.8);
        return vec3(u, 0.0, 0.6 * cos(u + t * 0.5)) * (top ? 0.08 : 0.0) * WAVE_STRENGTH;
    } else if (kind == SWAY_FLOAT) {
        return vec3(0.02 * c, 0.015 * s, 0.02 * s) * WAVE_STRENGTH;
    }
    return (lean * amp + push) * w;
}

// D11: whether a material takes the gust field (leaves and the land's plants; not water plants or wall flutter)
bool welkinGusted(int m) {
    int kind = matSway(m);
    return kind != SWAY_NONE && kind != SWAY_UNDERWATER && kind != SWAY_FLOAT && kind != SWAY_WALL;
}


// D11: whether this vertex moves with a front (a plant's anchored vertices do not: no field is worked out for them)
bool welkinGustedVertex(int m, vec3 midBlock) {
    int kind = matSway(m);
    bool top = welkinTopVertex(midBlock);
    if (kind == SWAY_LEAVES || kind == SWAY_TALL_UPPER) return true;
    if (kind == SWAY_PLANT || kind == SWAY_CROP || kind == SWAY_TALL_LOWER || kind == SWAY_STALK) return top;
    if (kind == SWAY_HANGING) return !top;
    return false;
}

#endif
