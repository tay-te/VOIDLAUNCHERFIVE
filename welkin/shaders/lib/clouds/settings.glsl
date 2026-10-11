// VOID Welkin: the voxel clouds' limits per quality (M3). The options themselves (CLOUDS, CLOUD_QUALITY, CLOUD_SHADOWS,
// CLOUD_FAIR) live in lib/settings.glsl with every other option, generated from plumbing's option table.

#ifndef WELKIN_CLOUD_SETTINGS
#define WELKIN_CLOUD_SETTINGS

#include "/lib/settings.glsl"

// CLOUD_RIMS (not an option since the look of 2026-10-11 dropped visible dither): define it here to bring back the
// module's dithered openings along the cloud outlines (lib/clouds/march.glsl wcRimOpen). Off, the edges are hard voxel
// edges and the clouds pass is about 0.13 ms cheaper.

// ---- derived, not options ----

#if CLOUD_QUALITY == 1
	#define WC_STEPS 20         // columns and jumps a ray may take in all
	#define WC_GAP_STEPS 4      // of them, jumps through the gaps between decks (tower search)
	#define WC_RING0 256.0      // the finest cells (12 m) reach this far; each ring after is twice as far
	#define WC_REACH 12000.0    // the DDA's reach; past it the decks are flat sheets
	#define WC_FAR 64000.0      // nothing past this
	#define WC_RIM_LEVELS 2     // rims on the rings up to this one (2: out to 4 x WC_RING0)
#elif CLOUD_QUALITY == 3
	#define WC_STEPS 64
	#define WC_GAP_STEPS 16
	#define WC_RING0 512.0
	#define WC_REACH 32000.0
	#define WC_FAR 120000.0
	#define WC_RIM_LEVELS 4
#else
	#define WC_STEPS 36
	#define WC_GAP_STEPS 8
	#define WC_RING0 384.0
	#define WC_REACH 16000.0
	#define WC_FAR 100000.0
	#define WC_RIM_LEVELS 3
#endif

#endif
