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
#if WD_HAZE || WD_SHAFTS
#ifndef WU_cameraPosition
#define WU_cameraPosition
uniform vec3 cameraPosition;
#endif
#include "/lib/depthfx.glsl"
#include "/lib/terrain.glsl"
#if defined CLOUDS && CLOUD_SHADOWS > 0
#include "/lib/clouds/shadow.glsl"
#define WD_VOL_CLOUDS
#endif
#endif

in vec2 texcoord;

#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 13 */
layout(location = 0) out vec4 outShaft;

#if (WD_HAZE || WD_SHAFTS) && defined WELKIN_SHADOWS_LIVE
// The depth prototypes HAZE and SHAFTS (2026-10-16): one walk along the view ray, near and far, for both.
//   near: from the eye to the shadow map's reach, WD_NEAR_STEPS even steps, each one compare of the shadow map
//   far:  from there to the surface (or 3 km for the sky), WD_FAR_STEPS steps growing with the distance, each lit by
//         the terrain's shadow line (lib/terrain.glsl: the ridges' shadows, from land off the screen too)
//   both: by the clouds' shadow over the point (lib/clouds/shadow.glsl: the gaps between clouds)
// Out: r the shafts' sunlit air (exp-weighted with distance, 0..1), g the haze's sunlit in-scatter and b its whole
// in-scatter (both integrals of the relative density wdHazeDensity over the ray, blocks, with the haze's own
// transmittance), a the view distance. The start of the walk is offset by the 4x4 lighting noise; composite2 resolves
// it depth-aware over the 4x4 texels (lib/shafts.glsl wdVolResolve), so no grain shows.
#define WD_NEAR_STEPS 14
#define WD_FAR_STEPS 10
#define WD_SHAFT_FALLOFF 600.0
#define WD_HAZE_SIGMA_MARCH 0.0009

float wdFarVis(vec3 pp) {
#ifdef WELKIN_TERRAIN_LIVE
	TerrainCell tc = terrainAt(pp);
	float y = cameraPosition.y + pp.y;
	return mix(1.0, terrainLineVis(y, tc.line.x, tc.line.y), tc.w);
#else
	return 1.0;
#endif
}

vec4 wdVolume(ivec2 q, vec3 dirP, float dist, bool sky) {
	float tEnd = sky ? 3000.0 : min(dist, 3000.0);
	float nearEnd = min(tEnd, 0.97 * shadowReach());
	float u = lightNoise(q, lightFrame());
#ifdef WD_VOL_CLOUDS
	CloudEnv ce = welkinCloudEnv();
#endif
	float shaft = 0.0, hzSun = 0.0, hzAll = 0.0, tau = 0.0;
	// the plain SUN_SHAFTS term (its sunlit length within SHAFT_REACH), for when SHAFTS is off and HAZE on
	float oldLen = min(min(tEnd, SHAFT_REACH), 0.98 * shadowReach()), oldLit = 0.0;
	for (int i = 0; i < WD_NEAR_STEPS + WD_FAR_STEPS; i++) {
		float t, dt;
		bool nearStep = i < WD_NEAR_STEPS;
		if (nearStep) {
			dt = nearEnd / float(WD_NEAR_STEPS);
			t = (float(i) + u) * dt;
		} else {
			if (tEnd <= nearEnd + 1.0) {
				break;
			}
			// steps growing with the distance (geometric between the near end and the surface)
			float a = (float(i - WD_NEAR_STEPS) + u) / float(WD_FAR_STEPS);
			float b = (float(i - WD_NEAR_STEPS) + 1.0) / float(WD_FAR_STEPS);
			float a0 = float(i - WD_NEAR_STEPS) / float(WD_FAR_STEPS);
			float r = tEnd / max(nearEnd, 1.0);
			t = nearEnd * pow(r, a);
			dt = nearEnd * (pow(r, b) - pow(r, a0));
		}
		vec3 pp = dirP * t;
		float vis = nearStep ? texture(shadowtex1, shadowMapPos(pp)) : wdFarVis(pp);
#ifdef WD_VOL_CLOUDS
		vis *= welkinCloudShadow(pp, ce);
#endif
		shaft += vis * exp(-t / WD_SHAFT_FALLOFF) * dt;
		oldLit += nearStep && t < oldLen ? vis * min(dt, oldLen - t + 0.5 * dt) : 0.0;
		float sig = wdHazeDensity(cameraPosition.y + pp.y);
		float tr = exp(-tau);
		hzSun += sig * vis * dt * tr;
		hzAll += sig * dt * tr;
		tau += sig * dt * WD_HAZE_SIGMA_MARCH;
	}
#if WD_SHAFTS
	return vec4(shaft / WD_SHAFT_FALLOFF, hzSun, hzAll, dist);
#else
	return vec4(oldLit / SHAFT_REACH, hzSun, hzAll, dist);
#endif
}
#endif

void main() {
	ivec2 q = ivec2(gl_FragCoord.xy); // the half-resolution texel
	outShaft = vec4(0.0, 0.0, 0.0, 1e4);
#if (WD_HAZE || WD_SHAFTS) && defined WELKIN_SHADOWS_LIVE
	{
		vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
		if (sunW.y < -0.08) {
			return; // night: the moon's air is left to the plain haze
		}
		ivec2 full = clamp(q * 2 + 1, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
		vec2 uv = (vec2(full) + 0.5) / vec2(viewWidth, viewHeight);
		Surface s = surfaceAt(full, uv);
		vec3 view = s.sky ? viewFromDepth(uv, 1.0, gbufferProjectionInverse) : s.view;
		float dist = s.sky ? 1e4 : length(view);
		vec3 dirP = mat3(gbufferModelViewInverse) * (view / max(length(view), 1e-4));
		outShaft = wdVolume(q, dirP, dist, s.sky);
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
