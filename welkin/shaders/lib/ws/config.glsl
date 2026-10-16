// VOID Welkin water (M6 water_shadows, the water half): constants and the option fallbacks.
//
// The options themselves (WS_RIPPLES, WS_WATER_TEXTURE, WS_CAUSTICS, WS_WATER_SSR, WS_SHORE_WET, WS_FROST,
// WS_SEASON_LIGHT, WS_DEBUG) are lines of the one option table (lib/settings.glsl, tools/options.py). The ws code reads
// them through the WS_*_MODE names below, so a build whose table leaves the water module out still compiles (Iris
// would see a second #define of an option as a second option; these names are not options).
//
// Every name here starts with ws / WS_. The shadows half of the module is the pack's own lib/shadow.glsl and
// program/shadow.glsl (the light step); this half only adds the caustics to the shadow pass's water.

#ifndef WELKIN_WS_CONFIG
#define WELKIN_WS_CONFIG

#include "/lib/settings.glsl"

#ifdef WS_RIPPLES
#define WS_RIPPLE_MODE WS_RIPPLES
#else
#define WS_RIPPLE_MODE 2
#endif
#ifdef WS_WATER_TEXTURE
#define WS_TEXTURE_SHARE (float(WS_WATER_TEXTURE) * 0.01)
#else
#define WS_TEXTURE_SHARE 0.5
#endif
#ifdef WS_DEBUG
#define WS_DEBUG_VIEW WS_DEBUG
#else
#define WS_DEBUG_VIEW 0
#endif

// ---- the look (2026-10-11): smooth, no steps, no dither; tuned against the pack's light ramp and sky table

// Opacity of the water body from the depth of water the view crosses: clear water loses this share per block, more
// when rain stirs it up. The shallowest film still tints a little; deep water is almost opaque.
const float WS_ABSORB = 0.30;
const float WS_ALPHA_MIN = 0.20;
const float WS_ALPHA_MAX = 0.94;
// Water running down a slope or a fall: churned with air, nearly opaque (merged onto the lighting, 2026-10-16).
// (the defects pass, 2026-10-16: 0.86 with a pale albedo read as ice; a fall is a clear sheet, the foam on it opaque)
const float WS_ALPHA_FALL = 0.62;
// Far water (VOID LOD) where the bed behind is unknown.
const float WS_ALPHA_FAR = 0.88;
// The pale band where water thins out over a bank (vertical depth in blocks, and how strong it is).
const float WS_BANK_DEPTH = 0.22;
const float WS_BANK_STRENGTH = 0.28;
// (the defects pass, 2026-10-16) how much light the body throws back, in the shallows and where it is deep (a share of
// its lit tint), the shore foam's strength, and the glitter path: its spread (radians squared, before the wind), the
// share of the water's sparkle cells lit at its heart, and how bright a sparkle is (times the direct light).
const float WS_SCATTER_SHALLOW = 0.42;
const float WS_SCATTER_DEEP = 0.82;
const float WS_SHORE_FOAM = 0.8;
const float WS_GLITTER_SPREAD = 0.018;
const float WS_GLITTER_SHARE = 0.32;
const float WS_GLITTER_GAIN = 1.6;
// Fresnel at normal incidence for water, glass and ice.
const float WS_F0_WATER = 0.02;
const float WS_F0_GLASS = 0.04;
const float WS_F0_ICE = 0.05;
// The sun's (or moon's) image in the water: its angular radius (radians) on calm water, and the most it may add,
// as a multiple of the direct light. Glints are the only thing the water draws above 1.0 (final's shoulder takes them).
const float WS_GLINT_RADIUS = 0.035;
const float WS_GLINT_GAIN = 3.0;
// Screen-space reflections (WS_WATER_SSR, MEDIUM and HIGH): none past this distance (near and far water).
const float WS_SSR_MAX_DIST = 1024.0;
// (the defects pass) the march: steps, and the reach they cover (0.5-block first step growing 1.45x)
#define WS_SSR_STEPS 18
const float WS_SSR_REACH = 1500.0;

// The info target's water kinds (colortex4.a).
const float WS_KIND_WATER = 1.0;
const float WS_KIND_FAR_WATER = 0.66;
const float WS_KIND_OTHER = 0.33;

#endif
