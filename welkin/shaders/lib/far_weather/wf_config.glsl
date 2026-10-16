// VOID Welkin far weather (P5, from the staged module M4): constants. The options (WF_CURTAINS, WF_CURTAIN_STEPS,
// WF_CURTAIN_REACH, WF_FAR_MIST, WF_FAR_HAZE, WF_DEBUG) live in lib/settings.glsl with every other option, generated
// from plumbing's option table.

#ifndef WELKIN_WF_CONFIG
#define WELKIN_WF_CONFIG

#include "/lib/settings.glsl"

// Where the near atmosphere hands the ray over (horizontal blocks): the middle of the near tiles' fade band, the same
// line lib/haze/haze.glsl stops its haze at (0.5 * (HZ_HANDOFF_START + HZ_HANDOFF_END) = 1912). The far haze starts its
// integral exactly here, so the two segments add up to one with no ring. (Expands where lib/haze/haze_config.glsl is in.)
#define WF_SEAM (0.5 * (HZ_HANDOFF_START + HZ_HANDOFF_END))

// The far terms that do not thin with height (a hazy country's "curtain", void_haze.z, and the grey of far rain, W1
// rain at the ray's middle) ease in over this many blocks past the seam, so the far air's density has no step there.
#define WF_RAMP 2048.0
#define WF_HAZE_CURTAIN_K (1.0 / 3000.0)
#define WF_HAZE_RAIN_K (1.0 / 6000.0)

// Curtains: where they start (horizontal blocks: vanilla's rain and the near air own the rest) and fade in, the least
// rain that hangs one, their optical depth per block at rain 1, and the opacity of the thickest.
#define WF_CURTAIN_START 384.0
#define WF_CURTAIN_FADE_IN 640.0
#define WF_MIN_RAIN 0.12
#define WF_CURTAIN_DENSITY 0.0018
#define WF_CURTAIN_ALPHA 0.9
// How far up into the deck a curtain reaches (blocks over the deck's base), so no sky shows between rain and cloud.
#define WF_CURTAIN_INTO_DECK 2.0
// The lowest a curtain's foot reaches under the far ground (it ends at the land in the depth buffer anyway).
#define WF_CURTAIN_FOOT 24.0
// The ray's floor for the curtain walk (world y): nothing hangs lower.
#define WF_WORLD_FLOOR -64.0

#endif
