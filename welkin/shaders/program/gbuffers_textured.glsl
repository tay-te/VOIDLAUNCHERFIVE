// VOID Welkin: gbuffers_textured. Textured geometry, particles among it (textured_lit, particles and their translucent pass fall back here): lit here.
#define G_TEXTURED
#define G_PARTICLE // (review1-b) water drops in the sky's light
#define G_FLAG
#define G_NEARCUT
// (forest2, 2026-10-18: particles take the near shadow map's sun, as entities do: falling leaves under a canopy
// glowed sunlit lime against the wood's shade)
#define G_SHADOW
#include "/program/gbuffers_common.glsl"
