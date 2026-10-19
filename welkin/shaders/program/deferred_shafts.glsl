// VOID Welkin: deferred_shafts, run as Iris's deferred5 (tools/wrappers.sh maps it), at half resolution (colortex13 is
// half size, so Iris draws the pass at that size). Workstream D (air and tone, 2026-10-13): the sun shafts' raw term
// (lib/shafts.glsl): r the sunlit share of the near air along the view ray (noisy, 4x4-tiled), a the surface's view
// distance; composite2 resolves it depth-aware and lays it on as light. Overworld only, with SUN_SHAFTS and SHADOWS.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/depth.glsl"
#include "/lib/shadow.glsl"
#include "/lib/noise.glsl"
#include "/lib/shafts.glsl"
// ---- atmos (welkin-atmos): the volumetric haze's walk (lib/haze/volume.glsl), which also gives the shafts' term
#ifdef HAZE_VOLUME
#ifndef WU_cameraPosition
#define WU_cameraPosition
uniform vec3 cameraPosition;
#endif
#include "/lib/terrain.glsl"
#define HV_WALK
#include "/lib/haze/volume.glsl"
#endif
// ---- end atmos

in vec2 texcoord;

#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 13,9 */
layout(location = 0) out vec4 outShaft;
// (look5) colortex9 (half size, free between the occlusion's resolve and the bloom's gather): x the cloud lanes'
// sunlit air, y all of it (lib/haze/volume.glsl hvCloudLanes), z the near air's sunlit length over SHAFT_NEAR_R
// (hvWalk's nearLit), a the view distance (for the resolve)
layout(location = 1) out vec4 outLanes;

void main() {
	ivec2 q = ivec2(gl_FragCoord.xy); // the half-resolution texel
	outShaft = vec4(0.0, 0.0, 0.0, 1e4);
	outLanes = vec4(0.0, 0.0, 0.0, 1e4);
#ifdef HAZE_VOLUME
	// atmos (welkin-atmos): one walk for the volumetric haze and the plain shafts (their term within SHAFT_REACH)
	{
		vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
		if (sunW.y < -0.10) {
			return; // night: the moon's air is the country haze's (a uniform branch)
		}
		ivec2 full = clamp(q * 2 + 1, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
		vec2 uv = (vec2(full) + 0.5) / vec2(viewWidth, viewHeight);
		Surface s = surfaceAt(full, uv);
		vec3 view = s.sky ? viewFromDepth(uv, 1.0, gbufferProjectionInverse) : s.view;
		float dist = s.sky ? 1e4 : length(view);
		vec3 dirP = mat3(gbufferModelViewInverse) * (view / max(length(view), 1e-4));
		bool shaftsOn = false;
#if defined SUN_SHAFTS && defined WELKIN_SHADOWS_LIVE
		shaftsOn = shaftGate(sunW) > 0.0 && sunAngle < 0.5;
#endif
		float nearLit;
		outShaft = hvWalk(q, dirP, dist, s.sky, shaftsOn, nearLit);
		outLanes = vec4(0.0, 0.0, shaftsOn ? nearLit / SHAFT_NEAR_R : 0.0, dist);
#ifdef HV_CLOUDS
		if (shaftsOn) {
			outLanes.xy = hvCloudLanes(q, dirP, dist, s.sky);
		}
#endif
		return;
	}
#endif
#if defined SUN_SHAFTS && defined WELKIN_SHADOWS_LIVE
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	if (shaftGate(sunW) <= 0.0 || sunAngle > 0.5) {
		return; // noon's trace and the night: nothing to walk (a uniform branch)
	}
	ivec2 full = clamp(q * 2 + 1, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
	vec2 uv = (vec2(full) + 0.5) / vec2(viewWidth, viewHeight);
	Surface s = surfaceAt(full, uv);
	vec3 view = s.sky ? viewFromDepth(uv, 1.0, gbufferProjectionInverse) : s.view;
	float dist = s.sky ? 1e4 : length(view);
	vec3 dirP = mat3(gbufferModelViewInverse) * (view / max(length(view), 1e-4));
	float len = min(min(dist, SHAFT_REACH), 0.98 * shadowReach());
	float u = lightNoise(q, lightFrame());
	float lit = 0.0;
	for (int i = 0; i < SHAFT_STEPS; i++) {
		float t = (float(i) + u) / float(SHAFT_STEPS) * len;
		vec3 uvz = shadowMapPos(dirP * t);
		lit += texture(shadowtex1, uvz);
	}
	outShaft = vec4(lit / float(SHAFT_STEPS) * len / SHAFT_REACH, 0.0, 0.0, dist);
#endif
}

#endif
