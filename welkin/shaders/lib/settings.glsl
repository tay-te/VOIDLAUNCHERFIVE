// VOID Welkin: the pack's options. Iris reads the option lines from here (every program includes this file), shows
// them on the screens laid out in shaders.properties, and names them through lang/en_US.lang.

#ifndef WELKIN_SETTINGS
#define WELKIN_SETTINGS

// ---- look ----
// Colour steps per channel in the final image (0: off). The final pass rounds every colour to one of these levels,
// with a 4x4 Bayer dither between neighbouring levels.
#define COLOR_STEPS 32 // [0 16 24 32 48 64]
// The Bayer dither between colour steps and at the edges of light bands.
#define DITHER
// How many flat steps the sky and block light ramps have.
#define LIGHT_STEPS 5 // [3 4 5 6 8]
// A pixel texture on the far land (the LOD), on the world's 16-pixel grid, coarser with distance.
#define FAR_TEXTURE
// How many screen pixels a far-texture texel spans at least before it doubles in size.
#define FAR_TEXTURE_PIXELS 2 // [1 2 3 4]
// The far land fades into the horizon colour in steps toward the LOD's edge.
#define FAR_FADE

// ---- atmosphere ----
// Valley mist at dawn: a flat slab of mist pooled in the hollows and along water, from VOID Gloam's near land tiles
// (lib/mist.glsl). Without Gloam there is none.
#define VALLEY_MIST
// How many places along each view ray the mist's slab is sampled at, the camera's included (2-4). Between them the
// slab's top and thickness run straight, and the ray meets it exactly.
#define MIST_SAMPLES 3 // [2 3 4]
// How thick the mist is: its opacity per block, as a share of the default.
#define MIST_THICKNESS 1.0 // [0.5 0.75 1.0 1.5 2.0]

// ---- debug ----
// 0 off; 1 depth (chunks warm, LOD cool, bands every doubling of distance, magenta where the LOD stands nearer than
// the chunks); 2 normals; 3 light map (block red, sky green); 4 albedo before lighting; 5 G-buffer flags (lit in
// deferred, emissive, material); 6 VOID bridge status (red: no Gloam data, yellow: data texture only, green: data
// and uniforms); 7 VOID near tiles projected onto the land (red mist potential, green pool weight, blue rise over the basin floor; magenta
// the tiles' centre cell, yellow the camera's, orange and red the 112- and 127-cell rings; white hatching where the
// ground lies under the mist's top).
#define DEBUG_VIEW 0 // [0 1 2 3 4 5 6 7]
// Forces the mist's time of day (void_mistTime) to this share, to see the slab at any hour; 0 leaves Gloam's value.
#define MIST_FORCE 0 // [0 25 50 75 100]
// Draws a magenta line along the join between the chunks and the LOD's land over the finished picture (mist and all),
// to check the atmosphere runs on across it.
//#define DEBUG_JOIN

// The sun's path leans south, as it does over the northern half of the Earth world: south faces get the sun.
const float sunPathRotation = -20.0; // [-40.0 -30.0 -20.0 -10.0 0.0 10.0 20.0 30.0 40.0]

#endif
