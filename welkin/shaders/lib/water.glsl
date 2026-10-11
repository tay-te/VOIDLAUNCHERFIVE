// VOID Welkin: water fog, for composite. Looking into water, the bed fades toward the water's own lit colour with the
// depth of water in front of it, in four flat steps dithered on the water surface's world grid; under water, the
// whole view fades to deep water with distance. The same for the chunks' water and the LOD's: gbuffers_water and
// dh_water mark their pixels in colortex3 with their lit colour (alpha: how much fog the far fade leaves).

#ifndef WELKIN_WATER
#define WELKIN_WATER

#include "/lib/depth.glsl"
#include "/lib/grid.glsl"
#include "/lib/light.glsl"

uniform sampler2D colortex3;
uniform int isEyeInWater;
uniform mat4 gbufferProjection;
uniform float viewHeight;

vec3 waterFog(ivec2 p, vec2 uv, vec3 scene) {
	Surface front = surfaceAt(p, uv);
	float frontDist = length(front.view);
	vec3 eye = mat3(gbufferModelViewInverse) * front.view;
	float d = 0.5;
#ifdef DITHER
	int level = gridLevel(blocksPerPixelAt(frontDist, gbufferProjection[1][1], viewHeight), 1.0);
	d = worldDither(eye, vec3(0.0, 1.0, 0.0), level);
#endif
	if (isEyeInWater == 1) {
		vec3 deep = vec3(0.04, 0.13, 0.19) * mix(0.2, 1.0, dayAmount());
		float dist = front.sky ? 256.0 : frontDist;
		return mix(scene, deep, steps(1.0 - exp(-dist * 0.045), 5.0, d));
	}
	vec4 w = texelFetch(colortex3, p, 0);
	if (w.a <= 0.0 || front.sky) {
		return scene;
	}
	Surface back = surfaceBehind(p, uv);
	float thick = back.sky ? 128.0 : max(length(back.view) - frontDist, 0.0);
	float f = steps(1.0 - exp(-thick * 0.07), 4.0, d) * 0.85 * w.a;
	return mix(scene, w.rgb * 0.6, f);
}

#endif
