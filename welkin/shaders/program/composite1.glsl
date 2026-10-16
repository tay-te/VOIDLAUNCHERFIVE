// VOID Welkin: composite1, the depth prototype SHAFTS' screen-space part (the depth A/B sheet, 2026-10-16). Half
// resolution, into colortex12 (free after the light pass). While the sun stands in or near the view, each texel walks
// toward the sun's place on the screen and counts how much of the way the open sky shows (no land, no LOD, no cloud:
// colortex6 holds the clouds' distance since composite), each step fading with its distance from the texel, so the
// light pouring through gaps in a crown, between clouds or over a ridge's outline streams toward the eye as beams.
// The start is offset per texel by the 4x4 lighting noise and composite2 gathers the 4x4 texels round each pixel, so
// no grain shows. With the 3D walk (deferred_shafts) it is SHAFTS: the 3D walk carries the air's shadows off the sun's
// axis, this the beams looking into the sun. r the beams' strength (0..1).

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

in vec2 texcoord;

#if WD_SHAFTS && defined WORLD_OVERWORLD
#include "/lib/noise.glsl"
#ifndef WU_depthtex0
#define WU_depthtex0
uniform sampler2D depthtex0;
#endif
#ifndef WU_dhDepthTex0
#define WU_dhDepthTex0
uniform sampler2D dhDepthTex0;
#endif
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

#define WD_RAY_STEPS 28

// Whether the open sky shows at screen point uv (1), or land, the far land or a cloud stands there (0).
float wdOpenSky(vec2 uv) {
	if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
		return 0.6; // off the screen: unknown, half open
	}
	float d = textureLod(depthtex0, uv, 0.0).r;
	float dh = textureLod(dhDepthTex0, uv, 0.0).r;
	float cl = textureLod(colortex6, uv, 0.0).r;
	return d >= 1.0 && dh >= 1.0 && cl > 999.0 ? 1.0 : 0.0;
}
#endif

/* RENDERTARGETS: 12 */
layout(location = 0) out vec4 outRays;

void main() {
	outRays = vec4(0.0);
#if WD_SHAFTS && defined WORLD_OVERWORLD
	vec4 clip = gbufferProjection * vec4(sunPosition, 1.0);
	if (clip.w <= 0.0) {
		return; // the sun behind the camera
	}
	vec2 sunUV = clip.xy / clip.w * 0.5 + 0.5;
	ivec2 q = ivec2(gl_FragCoord.xy);
	vec2 uv = (vec2(q) * 2.0 + 1.0) / vec2(viewWidth, viewHeight);
	vec2 aspect = vec2(viewWidth / viewHeight, 1.0);
	float far = length((sunUV - uv) * aspect);
	// beams reach about a screen height from the sun; the sun well off the screen sends fewer
	float reach = exp(-far * 1.6) * (1.0 - smoothstep(0.35, 1.1, length(max(abs(sunUV - 0.5) - 0.5, 0.0) * aspect)));
	if (reach < 0.005) {
		return;
	}
	vec2 step = (sunUV - uv) / float(WD_RAY_STEPS) * min(1.0, 0.75 / max(far, 1e-3));
	float u = lightNoise(q, 0);
	float sum = 0.0, wsum = 0.0, w = 1.0;
	for (int i = 0; i < WD_RAY_STEPS; i++) {
		sum += wdOpenSky(uv + step * (float(i) + u)) * w;
		wsum += w;
		w *= 0.95;
	}
	outRays = vec4(sum / wsum * reach, 0.0, 0.0, 1.0);
#endif
}

#endif
