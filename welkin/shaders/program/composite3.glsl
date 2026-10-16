// VOID Welkin, atmos (welkin-atmos, shafts and bloom step, 2026-10-16): composite3, at half resolution (colortex14 and
// colortex12 are half size, so Iris draws the pass at that size), after the near atmosphere (composite2). Two things:
//
//   colortex14  the bloom's bright source (lib/bloom.glsl): each texel the 2x2 full-resolution pixels under it, the
//               bright share of each (their headroom given back, a glowing texel its own light, a soft threshold),
//               averaged with the soft weights 1 / (1 + 0.15 brightness) so a single blazing pixel cannot flicker into a blotch.
//               Mipmapped for composite4's gather. Every dimension, with BLOOM.
//   colortex12  the sun beams (SHAFT_BEAMS, lib/shafts.glsl), overworld: while the sun stands in or near the view,
//               each texel walks toward the sun's place on the screen and gathers how much of the way the open sky
//               shows (no land, no LOD, no cloud: colortex6 holds the clouds' distance), each step weighted by the
//               glow round the sun there and fading with its distance from the texel, so the light through gaps in a
//               crown, between clouds or over a ridge streams toward the eye as beams. The start is offset per texel by
//               the 4x4 lighting noise and final gathers the 4x4 texels round each pixel, so no grain shows. rgb the
//               beams' light to add (display), already in the sun's colour and at the hour's strength. colortex12 (the
//               occlusion's resolve) is free after the light pass.
// Written from scratch for Welkin; no other pack's code.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#if defined SHAFT_BEAMS && defined WORLD_OVERWORLD
#define C3_BEAMS
#endif

#ifdef VERTEX_SHADER
out vec2 texcoord;
#ifdef C3_BEAMS
#include "/lib/sky/luminous.glsl"
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#ifndef WU_isEyeInWater
#define WU_isEyeInWater
uniform int isEyeInWater;
#endif
flat out vec3 beamSun;   // the sun's place on the screen (xy, 0..1), and whether it is in front (z)
flat out vec3 beamLight; // the beams' light at full strength (display)
#endif
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
#ifdef C3_BEAMS
	vec4 clip = gbufferProjection * vec4(sunPosition, 1.0);
	beamSun = vec3(clip.w > 0.0 ? clip.xy / clip.w * 0.5 + 0.5 : vec2(0.5), clip.w > 0.0 ? 1.0 : 0.0);
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	SkyLum f = skyLumFrame(sunW);
	// the sun's own light led toward the warm gold through the golden hours (display, luma 1), as bright as the
	// sun-side horizon
	vec3 hue = pow(mix(f.hue, f.gold, 0.6) * 0.4, vec3(1.0 / 2.2));
	hue /= max(dot(hue, vec3(0.2126, 0.7152, 0.0722)), 1e-3);
	vec3 hor = skyHorizon(true);
	float level = max(dot(hor, vec3(0.2126, 0.7152, 0.0722)), 0.35);
	// strongest at a low sun, a third at noon; gone at night, under an overcast, with the eye in water
	float h = sunW.y;
	float gate = smoothstep(-0.03, 0.04, h) * mix(1.0, 0.35, smoothstep(0.3, 0.85, h)) * f.day * f.clear
		* (isEyeInWater == 0 ? 1.0 : 0.0);
	beamLight = hue * level * gate * float(SHAFT_STRENGTH) * 0.01;
#endif
}
#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;

#ifdef BLOOM
#include "/lib/bloom.glsl"
#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

#ifndef WU_depthtex0
#define WU_depthtex0
uniform sampler2D depthtex0;
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhDepthTex0
#define WU_dhDepthTex0
uniform sampler2D dhDepthTex0;
#endif
#endif
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
#endif

// Whether nothing is drawn at full-resolution pixel p: no land, no LOD, and (in the overworld) no cloud.
bool openSkyAt(ivec2 p) {
	bool open = texelFetch(depthtex0, p, 0).r >= 1.0;
#ifdef DISTANT_HORIZONS
	open = open && texelFetch(dhDepthTex0, p, 0).r >= 1.0;
#endif
#ifdef WORLD_OVERWORLD
	open = open && texelFetch(colortex6, p, 0).r > 999.0;
#endif
	return open;
}

#ifdef C3_BEAMS
#include "/lib/noise.glsl"
flat in vec3 beamSun;
flat in vec3 beamLight;

// the walk: steps, how far toward the sun it reaches (screen heights), how fast a step's weight fades from the texel,
// how wide the glow round the sun the beams carry is (screen heights)
#define BEAM_STEPS 24
#define BEAM_REACH 0.8
#define BEAM_DECAY 0.955
#define BEAM_GLOW 0.18
// how strong the beams are (of the sun-side horizon's light), and on the sky itself (the dome already glows there)
#define BEAM_GAIN 0.9
#define BEAM_SKY 0.12

// Whether the open sky shows at screen point uv (1), or land, the far land or a cloud stands there (0).
float beamOpen(vec2 uv) {
	if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
		return 0.5; // off the screen: unknown, half open
	}
	float d = textureLod(depthtex0, uv, 0.0).r;
#ifdef DISTANT_HORIZONS
	d = min(d, textureLod(dhDepthTex0, uv, 0.0).r);
#endif
	float cl = textureLod(colortex6, uv, 0.0).r;
	return d >= 1.0 && cl > 999.0 ? 1.0 : 0.0;
}
#endif

/* RENDERTARGETS: 14,12 */
layout(location = 0) out vec4 outBloom;
layout(location = 1) out vec4 outBeams;

void main() {
	ivec2 q = ivec2(gl_FragCoord.xy);
	vec3 sum = vec3(0.0);
#ifdef BLOOM
	{
		ivec2 hi = ivec2(viewWidth, viewHeight) - 1;
		float wsum = 0.0;
		for (int k = 0; k < 4; k++) {
			ivec2 p = min(q * 2 + ivec2(k & 1, k >> 1), hi);
			vec3 c = texelFetch(colortex0, p, 0).rgb;
			vec4 fl = texelFetch(colortex2, p, 0);
			float emit = fl.x > 0.5 ? unpackEmission(fl.z) : 0.0;
			vec3 b = bloomSource(c, emit);
			// (the open sky only in part: lib/bloom.glsl BLOOM_SKY)
			b *= openSkyAt(p) ? BLOOM_SKY : 1.0;
			// (a soft brightness weight: the full 1 / (1 + brightness) also swallowed a lamp's few bright pixels, so the
			// lamps barely glowed; this one still keeps a lone glint from flickering into a blotch)
			float w = 1.0 / (1.0 + 0.15 * bloomLuma(b));
			sum += b * w;
			wsum += w;
		}
		sum /= wsum;
	}
#endif
	outBloom = vec4(sum, 1.0);

	vec3 beams = vec3(0.0);
#ifdef C3_BEAMS
	if (beamSun.z > 0.5 && dot(beamLight, beamLight) > 1e-6) {
		vec2 sunUV = beamSun.xy;
		vec2 uv = (vec2(q) * 2.0 + 1.0) / vec2(viewWidth, viewHeight);
		vec2 aspect = vec2(viewWidth / viewHeight, 1.0);
		float far = length((sunUV - uv) * aspect);
		// the sun well off the screen sends fewer beams; none from more than about a screen height past the edge
		float onScreen = 1.0 - smoothstep(0.25, 1.0, length(max(abs(sunUV - 0.5) - 0.5, 0.0) * aspect));
		if (onScreen > 0.0 && far < BEAM_REACH + 3.0 * BEAM_GLOW) {
			vec2 stp = (sunUV - uv) / float(BEAM_STEPS) * min(1.0, BEAM_REACH / max(far, 1e-3));
			float stpLen = length(stp * aspect);
			float u = lightNoise(q, 0);
			float acc = 0.0, wsum = 0.0, w = 1.0;
			for (int i = 0; i < BEAM_STEPS; i++) {
				float t = float(i) + u;
				vec2 s = uv + stp * t;
				// the glow round the sun the step stands in: the beams carry the bright core's light
				float g = exp(-max(far - stpLen * t, 0.0) / BEAM_GLOW);
				acc += beamOpen(s) * g * w;
				wsum += w;
				w *= BEAM_DECAY;
			}
			float rays = acc / wsum * onScreen;
			bool skyHere = beamOpen(uv) > 0.75;
			beams = beamLight * (BEAM_GAIN * rays * (skyHere ? BEAM_SKY : 1.0));
		}
	}
#endif
	outBeams = vec4(beams, 1.0);
}

#endif
