#ifndef WELKIN_HAZE_CONFIG
#define WELKIN_HAZE_CONFIG

// VOID Welkin, module "haze" (milestone M2): options and constants.
// The options are in lib/settings.glsl (below); the rest are the contract constants, the haze model, the haze-kind
// table and the wet classes. Names are prefixed HAZE_ / WET_ / HZ_ so they cannot clash with other modules.

// ------------------------------------------------------------------------------------------------------- options
// HAZE_ENABLED, HAZE_SAMPLES, HAZE_STRENGTH, HAZE_RAIN, HAZE_ON_SKY, HAZE_SEA_LEVEL, WET_ENABLED,
// WET_PUDDLES, WET_PUDDLE_REACH and HAZE_DEBUG live in lib/settings.glsl with every other option, generated from
// plumbing's option table, so Iris sees each option line once.
#include "/lib/settings.glsl"

// ---------------------------------------------------------------------------------------------- contract version 1
#define HZ_CONTRACT 1               // highest void_version this module reads
#define HZ_T_N0 0
#define HZ_T_N1 1
#define HZ_T_F0 2
#define HZ_T_F1 3
#define HZ_T_W0 4
#define HZ_T_W1 5
#define HZ_T_U  6
#define HZ_NEAR_CELL 16             // blocks per N cell (2.3: 16 m)
#define HZ_NEAR_SHIFT 4
#define HZ_FAR_CELL 1024            // blocks per F cell ("1 km"); must match Gloam's FarTiles cell, see NOTES open question
#define HZ_FAR_SHIFT 10
#define HZ_HANDOFF_START 1792.0     // far weather's WF_NEAR_FADE_START / _END: where its far haze takes over (blocks, horizontal)
#define HZ_HANDOFF_END 2032.0
#define HZ_FADE_IN 112.0            // tile cells: valid to 127, hand-over from 112 (2.3; the debug views' reach)

// -------------------------------------------------------------------------------------------------- the haze model
// Two exponential layers, integrated exactly over each ray segment (no marching):
//   clear air:  density thickness / clearReach at HAZE_SEA_LEVEL, scale height 160 (VOID LOD's lod.vsh haze(), so
//               far land keeps the vista the LOD was tuned for)
//   land murk:  density from the haze kind's haziness (Gloam Haze: reach 64..1024 blocks on a log scale), based on
//               the ground under the sample, with the kind's own scale height, thicker in hollows and by water
// plus VOID LOD's rain curtain: a downpour adds (L + 160 r) r / 660 of optical depth that does not thin with height.
#define HZ_CLEAR_H 160.0            // VoidLod.HAZE_SCALE_HEIGHT
#define HZ_CLEAR_MIN 2048.0         // clear-air reach never shorter than this (vanilla render distances)
#define HZ_CLEAR_SHARE 0.75         // clear-air reach = 0.75 x the world's reach (VoidLod.hazeReach default)
#define HZ_RAIN_REACH 660.0         // VoidLod.RAIN_REACH
#define HZ_RAIN_OFFSET 160.0        // VoidLod.RAIN_OFFSET
#define HZ_MURKIEST 64.0            // Haze.MURKIEST
#define HZ_CLEAR_END 1024.0         // Haze.CLEAR
#define HZ_MURK 1.2                 // optical depth the murk reaches at its own "reach" (on the ground, level ray)
#define HZ_MURK_FLOOR 80.0          // never a wall nearer than this (GloamSky: Haze.reach(haze, 80))
#define HZ_CLEAR_CAP 4.0            // density cap below the clear-air base, as lod.vsh (e^4)
#define HZ_MURK_CAP 1.0             // density cap below the murk's ground (it does not pile up in caves)
#define HZ_DITHER 0.35              // legacy (hzSteps): share of each step that was dithered; the haze is smooth now

// ------------------------------------------------------------------------------------------- haze kinds (table 2.4)
// 0 none/vanilla (read as temperate), 1 temperate, 2 humid_lowland, 3 misty_lakes, 4 forest_damp, 5 dusty,
// 6 clear_alpine; 7..15 reserved (read as temperate).
#define HZ_KINDS 7
#define HZ_K_TEMPERATE 1
#define HZ_K_DUSTY 5
#define HZ_K_ALPINE 6

// A kind's air and look. Branch-free one-hot picks (k is 1..6; hzKindIndex maps 0 and 7..15 to 1): a const array
// indexed at run time is copied into local memory per pixel on some drivers (it cost 3 ms at 1080p on an RTX 4060 in
// a first version), and a switch branches.
//
// Air, per kind (1 temperate, 2 humid_lowland, 3 misty_lakes | 4 forest_damp, 5 dusty, 6 clear_alpine):
//   hazy     haziness on Gloam's log scale, 1 - ln(reach/64)/ln 16, from Haze.HINTS; temperate keeps a faint valley
//            haze, clear_alpine none
//   height   the murk's scale height in blocks: lake mist lies low, dust rises high
//   clearMul how thick the clear air is: alpine air clean, dusty air thick
const vec3 HZ_HAZY_A   = vec3(0.03, 0.3575, 0.4944), HZ_HAZY_B   = vec3(0.4195, 0.4195, 0.0);
const vec3 HZ_HEIGHT_A = vec3(64.0, 48.0, 24.0),     HZ_HEIGHT_B = vec3(40.0, 96.0, 160.0);
const vec3 HZ_CLEAR_A  = vec3(1.00, 1.10, 1.05),     HZ_CLEAR_B  = vec3(1.00, 1.15, 0.65);

// Look, per kind: the tint (edit these), its brightness against the air, how strongly it tints the air.
//   temperate      neutral, faintly blue-grey     humid_lowland  pale blue-grey     misty_lakes   cool white
//   forest_damp    green-grey                     dusty          warm ochre         clear_alpine  thin and blue
// The tint is stored divided by its own brightness (times the kind's), so its colour at the air's brightness, Gloam's
// Palette.tone, is just tint * luma(air): night and dusk stay right without a palette per hour.
const vec3 HZ_LUMA = vec3(0.3, 0.59, 0.11);
#define HZ_TONED(t, b) ((t) * ((b) / dot((t), HZ_LUMA)))
const mat3 HZ_TINT_A = mat3(HZ_TONED(vec3(0.80, 0.84, 0.88), 1.00), HZ_TONED(vec3(0.76, 0.83, 0.90), 1.04),
                            HZ_TONED(vec3(0.88, 0.92, 0.96), 1.10));
const mat3 HZ_TINT_B = mat3(HZ_TONED(vec3(0.70, 0.79, 0.72), 0.94), HZ_TONED(vec3(0.90, 0.76, 0.56), 1.02),
                            HZ_TONED(vec3(0.66, 0.78, 0.98), 1.00));
const vec3 HZ_AMOUNT_A = vec3(0.20, 0.50, 0.55),     HZ_AMOUNT_B = vec3(0.50, 0.60, 0.35);

// the air of kind k: haziness, murk scale height, clear-air thickness
vec3 hzKindAir(int k) {
    vec3 a = vec3(equal(ivec3(k), ivec3(1, 2, 3)));
    vec3 b = vec3(equal(ivec3(k), ivec3(4, 5, 6)));
    return vec3(dot(a, HZ_HAZY_A) + dot(b, HZ_HAZY_B), dot(a, HZ_HEIGHT_A) + dot(b, HZ_HEIGHT_B),
                dot(a, HZ_CLEAR_A) + dot(b, HZ_CLEAR_B));
}

// the look of kind k: rgb the toned tint, a how strongly it tints the air
vec4 hzKindLook(int k) {
    vec3 a = vec3(equal(ivec3(k), ivec3(1, 2, 3)));
    vec3 b = vec3(equal(ivec3(k), ivec3(4, 5, 6)));
    return vec4(HZ_TINT_A * a + HZ_TINT_B * b, dot(a, HZ_AMOUNT_A) + dot(b, HZ_AMOUNT_B));
}

float hzKindHazy(int k) {
    return hzKindAir(k).x;
}

// Weather and season tints (raw; toned where used). Gloam Palette names in brackets.
const vec3 HZ_RAIN_AIR   = vec3(0.62, 0.68, 0.77);  // rain: darker, cooler than OVERCAST_FOG
const vec3 HZ_SNOW_AIR   = vec3(0.84, 0.88, 0.94);  // falling snow: pale, cool
const vec3 HZ_AUTUMN     = HZ_TONED(vec3(0.92, 0.84, 0.70), 1.0);  // autumn haze warmth (GOLDEN_HAZE, toned down)
const vec3 HZ_WINTER     = HZ_TONED(vec3(0.80, 0.86, 0.96), 1.0);  // low winter air
const vec3 HZ_PUDDLE_ICE = vec3(0.78, 0.86, 0.96);

// ------------------------------------------------------------------------------------------------------ wet ground
// Wet classes: how a surface takes the rain. Map the pack's material ids to these in hzWetClass (wet.glsl).
#define WET_DRY 0       // never wet: snow, ice, glass, water, lava, lights, portals
#define WET_SOLID 1     // stone, masonry, ore, concrete: darkens a little, holds puddles
#define WET_POROUS 2    // soil, grass block, sand, gravel, clay, wood, cloth, moss: darkens most, holds puddles
#define WET_PLANT 3     // leaves and plants: darkens a touch, a grazing sheen, no puddles
#define WET_SHINY 4     // metal, gems: no darkening, sheen, holds puddles
// soaked through: soil and grass about a quarter darker, plants a fifth, stone a sixth (scaled by the wetness)
float hzWetDark(int c) { return c == WET_POROUS ? 0.30 : (c == WET_SOLID ? 0.17 : (c == WET_PLANT ? 0.24 : 0.0)); }
float hzWetSheen(int c) { return c == WET_SHINY ? 0.45 : (c == WET_PLANT ? 0.35 : (c == WET_SOLID ? 0.28 : (c == WET_POROUS ? 0.22 : 0.0))); }

#endif
