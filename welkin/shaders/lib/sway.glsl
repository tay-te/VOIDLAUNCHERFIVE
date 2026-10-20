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
//   vec2 gs = welkinGust(absXZ, t, wind, storm);  // absXZ: absolute world x, z (NOT wrapped); x the gust 0..1, y the
//                                                 // lean signal (the gust less the rebound behind it), -0.5..1
//
// The field is two octaves of value noise in a frame stretched across the wind (WG_ALONG deep, WG_ACROSS long),
// thresholded so the gustier the air (Gloam's gust, the wind's speed, the storm) the more of it is front.
// - Carried with the wind as P - dir * V * t. Over a long clock a change of V or dir would sweep the field (V' * t),
//   so the clock runs in two staggered cycles of WG_PERIOD seconds, cross-faded (a flow map's two phases); each cycle
//   draws a fresh field, so no front repeats. The drift a changing wind can add is bounded by its change over a cycle.
// - The stretched frame is one of 16 fixed headings, the two round the wind blended; fixed, so it never swings round
//   the world's origin as the wind turns (a far camera would see the field race sideways).
// - The blends keep the field's contrast (weights renormalised by their root sum of squares).
// Cost: 4 frames x 2 octaves of value noise per point; the lean signal takes a second point (the rebound).
#define WG_PERIOD 60.0
#define WG_ALONG  26.0  // the field's scale along the wind, blocks (a front's depth and the gap behind it)
#define WG_ACROSS 64.0  // across the wind (how long a front runs)
#define WG_SOFT   0.15  // the threshold's soft edge (in field units: the front's border)
#define WG_TROUGH 7.0   // the rebound sample's distance downwind, blocks
#define WG_REBOUND 0.45 // how far blades swing back behind a front (in gust units)
// the sheen (program/gbuffers_common.glsl, the albedo of gusted plants and leaves, times WIND_SHEEN)
#define WG_SHEEN_LIFT   0.14                    // lighter in a full gust (albedo, display-referred)
#define WG_SHEEN_SILVER 0.30                    // and that much toward a silvery olive grey
#define WG_SHEEN_TINT   vec3(0.99, 1.03, 0.93)  // the silvery olive (times the albedo's luminance)
#define WG_SHEEN_TROUGH 0.20                    // darker per unit of rebound (the rebound is at most about 0.45)
#define WG_SHEEN_LEAVES 0.4                     // a crown's share
#define WG_SHEEN_GROUND 0.85                    // a grass block's top (the meadow's ground: the short grass on it)

float welkinGustVal(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    ivec2 c = ivec2(i);
    uvec4 h = uvec4(uint(c.x), uint(c.x + 1), uint(c.x), uint(c.x + 1)) * 0x8da6b343u
            ^ uvec4(uint(c.y), uint(c.y), uint(c.y + 1), uint(c.y + 1)) * 0xd8163841u;
    h ^= h >> 13u;
    h *= 0x5bd1e995u;
    h ^= h >> 15u;
    vec4 v = vec4(h & 0xffffu) * (1.0 / 65535.0);
    return mix(mix(v.x, v.y, f.x), mix(v.z, v.w, f.x), f.y);
}

// one cycle's field in one fixed frame (heading k of 16): two octaves, the second turned a little
float welkinGustOne(vec2 q, float k, vec2 o) {
    float ang = k * 0.39269908;
    vec2 d = vec2(cos(ang), sin(ang));
    vec2 u = vec2(dot(q, d), dot(q, vec2(-d.y, d.x)));
    float a = welkinGustVal(u * vec2(1.0 / WG_ALONG, 1.0 / WG_ACROSS) + o);
    float b = welkinGustVal(vec2(u.x + 0.3 * u.y, u.y) * vec2(1.0 / (0.45 * WG_ALONG), 1.0 / (0.5 * WG_ACROSS)) + o.yx + 5.3);
    return 0.65 * a + 0.35 * b;
}

// the raw field round 0.5 at absolute xz P (the cycles and headings blended, contrast kept)
float welkinGustRaw(vec2 P, vec2 dir, float V, float t) {
    float hd = atan(dir.y, dir.x) * (8.0 / 3.14159265);
    float k0 = floor(hd);
    float fk = hd - k0;
    float sA = t / WG_PERIOD, sB = sA + 0.5;
    float cA = floor(sA), cB = floor(sB);
    float tA = (sA - cA) * WG_PERIOD, tB = (sB - cB) * WG_PERIOD;
    float wA = 1.0 - abs(2.0 * (sA - cA) - 1.0); // 0 where its cycle restarts, 1 half way
    float wB = 1.0 - wA;
    // each cycle a fresh field (offset by its index), the two cycles apart
    vec2 oA = vec2(fract(cA * 0.7548777), fract(cA * 0.5698403)) * 977.0;
    vec2 oB = vec2(fract(cB * 0.7548777), fract(cB * 0.5698403)) * 977.0 + vec2(431.0, 217.0);
    vec2 qA = P - dir * (V * tA);
    vec2 qB = P - dir * (V * tB);
    float w00 = wA * (1.0 - fk), w01 = wA * fk, w10 = wB * (1.0 - fk), w11 = wB * fk;
    float n = w00 * (welkinGustOne(qA, k0, oA) - 0.5) + w01 * (welkinGustOne(qA, k0 + 1.0, oA) - 0.5)
            + w10 * (welkinGustOne(qB, k0, oB) - 0.5) + w11 * (welkinGustOne(qB, k0 + 1.0, oB) - 0.5);
    return 0.5 + n * inversesqrt(w00 * w00 + w01 * w01 + w10 * w10 + w11 * w11 + 1e-4);
}

// how gusty the air is, 0 calm .. 1 a storm's gale: sets how much of the field is front and how hard it pushes
float welkinGustiness(vec4 wind, float storm) {
    return clamp(WG_BASE + 0.30 * wind.w + 0.03 * clamp(wind.z, 0.0, 20.0) + 0.6 * storm, 0.0, 1.0);
}

vec2 welkinGust(vec2 P, float t, vec4 wind, float storm) {
#if SWAY_FREEZE != 0
    t = float(SWAY_FREEZE);
#endif
    vec2 dir = normalize(wind.xy + vec2(1e-4, 0.0));
    float V = 2.0 + 0.6 * clamp(wind.z, 0.0, 20.0); // the fronts' speed, blocks a second
    float G = welkinGustiness(wind, storm);
    float th = mix(0.67, 0.50, G);
    float amp = mix(0.5, 1.0, G) * WIND_GUSTS;
    float g = smoothstep(th, th + WG_SOFT, welkinGustRaw(P, dir, V, t)) * amp;
    float g2 = smoothstep(th, th + WG_SOFT, welkinGustRaw(P + dir * WG_TROUGH, dir, V, t)) * amp;
    return vec2(g, g - WG_REBOUND * g2 * (1.0 - g));
}

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

#endif
