// VOID Welkin: contact occlusion. program/deferred_ao.glsl writes a raw term (colortex9: r the term, g the view
// distance) turned by each pixel's place in a 4x4 tile; welkinContactAo averages a 4x4 window round the pixel (which
// always holds the tile's sixteen turns once each), weighted by depth so a near edge does not bleed onto the land
// behind it. The result is smooth, without pattern or noise, and the same every frame. It darkens the sky's fill
// and the torch light in the corners and at plants' feet, the sun a little (a storybook "grounding"), on top of
// Minecraft's own corner shading.

#ifndef WELKIN_AO
#define WELKIN_AO

// colortex9 (the raw term and the view distance), read by Iris from the light pass, which always runs
/*
const int colortex9Format = RG16F;
const bool colortex9Clear = false;
*/

// the radius round a point the term looks at, blocks
#define AO_RADIUS 0.6
// no term past this view distance (blocks); it fades out over the last 40 %
#define AO_FAR 72.0

#if defined CONTACT_AO && !defined WELKIN_AO_PASS
#ifndef WU_colortex9
#define WU_colortex9
uniform sampler2D colortex9;
#endif

// The contact occlusion at texel p (view distance z), 1 for none.
float welkinContactAo(ivec2 p, float z) {
	if (z > AO_FAR) {
		return 1.0;
	}
	ivec2 hi = textureSize(colortex9, 0) - 1;
	float tol = 0.04 + 0.015 * z;
	float sum = 0.0;
	float wsum = 0.0;
	for (int y = -2; y <= 1; y++) {
		for (int x = -2; x <= 1; x++) {
			vec2 t = texelFetch(colortex9, clamp(p + ivec2(x, y), ivec2(0), hi), 0).rg;
			float dz = (t.g - z) / tol;
			float w = exp(-dz * dz);
			sum += t.r * w;
			wsum += w;
		}
	}
	float ao = clamp(sum / max(wsum, 1e-4), 0.0, 1.0);
	return mix(1.0, ao, 1.0 - smoothstep(0.6 * AO_FAR, AO_FAR, z));
}
#endif

#endif
