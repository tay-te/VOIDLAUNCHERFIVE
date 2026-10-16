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

vec3 waterFog(ivec2 p, vec2 uv, vec3 scene) {
	Surface front = surfaceAt(p, uv);
	float frontDist = length(front.view);
	if (isEyeInWater == 1) {
		vec3 deep = vec3(0.04, 0.13, 0.19) * mix(0.2, 1.0, dayAmount());
		float dist = front.sky ? 256.0 : frontDist;
		return mix(scene, deep, 1.0 - exp(-dist * 0.045));
	}
	// ---- M6 water (welkin-water): looking into water from above, the water's own body already covers the bed by the
	// depth of water the view crosses (gbuffers_water and dh_water, lib/ws/water.glsl), so there is no fog to add here.
	// colortex3 still marks the water (its lit colour, alpha 1) for the valley mist.
	return scene;
}

#endif
