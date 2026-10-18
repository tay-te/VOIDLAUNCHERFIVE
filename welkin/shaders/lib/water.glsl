// VOID Welkin: water fog, for composite. Under water, the whole view fades to deep water with distance. Looking into
// water from above, the water's body does it itself (M6, lib/ws/water.glsl: its opacity from the depth crossed).
// gbuffers_water and dh_water mark their pixels in colortex3 with their lit colour (alpha: how much the far fade
// leaves), which the valley mist reads.

#ifndef WELKIN_WATER
#define WELKIN_WATER

#include "/lib/depth.glsl"
#include "/lib/grid.glsl"
#include "/lib/light.glsl"

#ifndef WU_colortex3
#define WU_colortex3
uniform sampler2D colortex3;
#endif
#ifndef WU_isEyeInWater
#define WU_isEyeInWater
uniform int isEyeInWater;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

#ifndef WU_colortex4
#define WU_colortex4
uniform sampler2D colortex4;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
// (water2) how fast clear water takes each colour out of the bed's light, per block of path
const vec3 WATER_BED_SIGMA = vec3(0.085, 0.032, 0.03);

vec3 waterFog(ivec2 p, vec2 uv, vec3 scene) {
	Surface front = surfaceAt(p, uv);
	float frontDist = length(front.view);
	if (isEyeInWater == 1) {
		vec3 deep = vec3(0.04, 0.13, 0.19) * mix(0.2, 1.0, dayAmount());
		float dist = front.sky ? 256.0 : frontDist;
		return mix(scene, deep, 1.0 - exp(-dist * 0.045));
	}
#ifdef WELKIN_DEPTH_BEHIND
	// ---- (water2, 2026-10-18) the bed's colour under the water: clear water takes the red out first, then the green,
	// along the light's path down to the bed and back up the view, so a shore's shallows run from clear sand over
	// green-teal to blue as they deepen, instead of a pale veil over a bed lit as if in the air. The water's own
	// surface (its lit colour in colortex3, by the opacity gbuffers_water gave it) is left as it is; only the share of
	// the bed seen through it is tinted.
	vec4 mark = texelFetch(colortex3, p, 0);
	if (mark.a > 0.75 && !front.sky) {
		Surface back = surfaceBehind(p, uv);
		float thick = back.sky ? 64.0 : max(length(back.view) - frontDist, 0.0);
		vec4 info = texelFetch(colortex4, p, 0);
		// the water's whole opacity: near water carries it in the info's kind (0.75 to 1), the far water's is fixed
		float aOut = info.a > 0.74 ? clamp((info.a - 0.75) * 4.0, 0.0, 1.0) : 1.0 - 0.12 * (1.0 - info.b);
		// what the bed adds through it; the mark is clipped at 1, so where the reflection is bright it is bounded by
		// what a bed could add at all
		vec3 bed = clamp(scene - mark.rgb * aOut, vec3(0.0), vec3(1.2 * (1.0 - aOut)));
		vec3 dirV = normalize(mat3(gbufferModelViewInverse) * front.view);
		float path = thick * (1.0 + abs(dirV.y));
		vec3 T = exp(-path * WATER_BED_SIGMA);
		scene -= bed * (1.0 - T);
	}
#endif
	// ---- M6 water (welkin-water): looking into water from above, the water's own body already covers the bed by the
	// depth of water the view crosses (gbuffers_water and dh_water, lib/ws/water.glsl), so there is no fog to add here.
	// colortex3 still marks the water (its lit colour, alpha 1) for the valley mist.
	return scene;
}

#endif
