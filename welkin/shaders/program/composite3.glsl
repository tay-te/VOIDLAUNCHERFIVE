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
// (HDR stage A: the bridge)
#include "/lib/hdr.glsl"

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
flat out vec3 beamLight; // the beams' light at full strength (scene light)
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
	// (HDR stage B: linear, luma 1, at the sun-side horizon's scene light)
	vec3 hue = mix(f.hue, f.gold, 0.6);
	hue /= max(dot(hue, vec3(0.2126, 0.7152, 0.0722)), 1e-3);
	vec3 hor = sceneFromAuthored(skyHorizon(true));
	float level = max(dot(hor, vec3(0.2126, 0.7152, 0.0722)), 0.08);
	// strongest at a low sun, a third at noon; gone at night, under an overcast, with the eye in water
	float h = sunW.y;
	float gate = smoothstep(-0.03, 0.04, h) * mix(1.0, 0.35, smoothstep(0.3, 0.85, h)) * f.day * f.clear
		* (isEyeInWater == 0 ? 1.0 : 0.0);
	beamLight = vec3(gate * float(SHAFT_STRENGTH) * 0.01); // (HDR stage C: the hour's strength only; the colour is the sky's)
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
#ifndef WU_colortex3
#define WU_colortex3
uniform sampler2D colortex3; // the water marks (> 0: water in front)
#endif
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
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

#ifdef BLOOM
#ifndef WU_upPosition
#define WU_upPosition
uniform vec3 upPosition;
#endif
// (look4) the golden hour's low sun, 0..1 (as lib/sky/luminous.glsl skyGoldenHeadroom, by the sun's height alone)
float waterGolden() {
#ifdef WORLD_OVERWORLD
	float y = dot(normalize(sunPosition), normalize(upPosition));
	return smoothstep(0.50, 0.05, y) * smoothstep(-0.14, -0.04, y);
#else
	return 0.0;
#endif
}

// How much of pixel p's bright share (display colour c) feeds the bloom: the open sky BLOOM_SKY, a cloud (or a far rain
// curtain over the sky) its body BLOOM_CLOUD and its rim past white wholly (lib/bloom.glsl; the merge of phase 2), the
// rest wholly.
float bloomShareAt(ivec2 p, vec3 c) {
	float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
	d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
	if (d < 1.0) {
		// (look4, 2026-10-18) the water round a low sun a little less: the glitter path's broad field of reflected glow
		// had bloomed into a soft orange ball over the lake; its glints still bloom
		return texelFetch(colortex3, p, 0).a > 0.25 ? mix(1.0, 0.2, waterGolden()) : 1.0;
	}
#ifdef WORLD_OVERWORLD
	if (texelFetch(colortex6, p, 0).r <= 999.0) {
		return bloomCloudShareHdr(c);
	}
#endif
#ifdef WORLD_OVERWORLD
	// (review round 1) the pixel sun's disc and rim wholly: a low sun through the haze still blooms into a halo
	vec3 v = normalize((gbufferProjectionInverse * vec4((vec2(p) + 0.5) / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0)).xyz);
	// (look4) round a low sun the sky's aureole is the glow itself (lib/sky/luminous.glsl): it lends the bloom less, or
	// its broad bright field spread into a soft ball over the sun and the lake; the disc still blooms wholly
	return mix(BLOOM_SKY * mix(1.0, 0.35, waterGolden()), 1.0, bloomSunDisc(v, normalize(sunPosition)));
#else
	return BLOOM_SKY;
#endif
}

// Whether pixel p is a lit surface that does not glow (lib/bloom.glsl bloomHeadroom's `diffuse`): land, the far land,
// creatures; not the sky, a cloud, a glowing texel, or water (its glints, glitter and sky reflections are light, and
// bloom as they did).
float bloomDiffuseAt(ivec2 p, float emit) {
	float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
	d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
	return d < 1.0 && emit <= 0.0 && texelFetch(colortex3, p, 0).a <= 0.25 ? 1.0 : 0.0;
}
#endif

#ifdef C3_BEAMS
#include "/lib/rays.glsl"
#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#include "/lib/noise.glsl"
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
flat in vec3 beamSun;
flat in vec3 beamLight;

// the walk: steps, how far toward the sun it reaches (screen heights), how fast a step's weight fades from the texel,
// how wide the glow round the sun the beams carry is (screen heights)
#define BEAM_STEPS 18 // (review round 1: 18, was 24, the decay per step matched; final gathers the noise away)
#define BEAM_REACH 1.0 // (look: 1.0, was 0.8)
#define BEAM_DECAY 0.940
#define BEAM_GLOW 0.28 // (look2: 0.28, wider) (look: 0.20, was 0.18)
// how strong the beams are (of the sun-side horizon's light), and on the sky itself (the dome already glows there)
#define BEAM_GAIN 3.4 // (look2: 3.4) (look: 2.0, was 0.9; BEAM_FLAT takes back most of it where nothing breaks the glow)
#define BEAM_SKY 0.2 // (look: 0.2, was 0.12)
// (look) how much of an unbroken glow is taken off: the beams are what the occluders break up, not a veil round the sun
#define BEAM_FLAT 0.5 // (look2: 0.5, was 0.65: more of the warm glow kept round a low sun)

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
		float expo = hdrExposure();
		for (int k = 0; k < 4; k++) {
			ivec2 p = min(q * 2 + ivec2(k & 1, k >> 1), hi);
			vec3 c = texelFetch(colortex0, p, 0).rgb * expo; // exposed scene light (HDR stage B)
			vec4 fl = texelFetch(colortex2, p, 0);
			float emit = fl.x > 0.5 ? unpackEmission(fl.z) : 0.0;
			vec3 b = bloomSourceHdr(c, emit, bloomDiffuseAt(p, emit));
			// (the open sky and the clouds' bodies only in part: lib/bloom.glsl BLOOM_SKY, BLOOM_CLOUD)
			b *= bloomShareAt(p, c);
			// (Look D5) a source texel's bright share eases under BLOOM_SRC_CAP: the pixel sun's disc (600 to 1500) spread
			// into a glow that stayed past the curve's shoulder out to a contour, a cream disc with an edge over the leaves
			// round it. Eased, the halo falls off gradually from the sun (the disc keeps its own light: the sky lends none)
			// (a glowing texel, a lamp or a flame, keeps its whole glow)
			b *= mix(BLOOM_SRC_CAP / (BLOOM_SRC_CAP + bloomLuma(b)), 1.0, smoothstep(0.0, 0.1, emit));
			// (a soft brightness weight: the full 1 / (1 + brightness) also swallowed a lamp's few bright pixels, so the
			// lamps barely glowed; this one still keeps a lone glint from flickering into a blotch)
			float w = 1.0 / (1.0 + 0.01 * bloomLuma(b));
			sum += b * w;
			wsum += w;
		}
		sum /= wsum;
	}
#endif
	outBloom = vec4(sum, 1.0);

	vec3 beams = vec3(0.0);
#ifdef C3_BEAMS
	// (HDR stage C) the light shafts' source (lib/rays.glsl): the open sky's scene light near the sun, through the
	// clouds' transmittance, for composite5..7's radial blur
	float gate = dot(beamLight, vec3(0.2126, 0.7152, 0.0722));
	if (beamSun.z > 0.5 && gate > 1e-4) {
		vec2 uv = (vec2(q) * 2.0 + 1.0) / vec2(viewWidth, viewHeight);
		float aspect = viewWidth / viewHeight;
		float near = rayNear(length((beamSun.xy - uv) * vec2(aspect, 1.0))) * rayOnScreen(beamSun.xy, aspect);
		if (near > 1e-4) {
			float expoR = hdrExposure();
			ivec2 hi = ivec2(viewWidth, viewHeight) - 1;
			for (int k = 0; k < 4; k++) {
				ivec2 p = min(q * 2 + ivec2(k & 1, k >> 1), hi);
				float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
				d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
				if (d >= 1.0) {
					float T = texelFetch(colortex6, p, 0).g; // what the clouds in front let through
					vec3 L = texelFetch(colortex0, p, 0).rgb * expoR;
					float y = dot(L, vec3(0.2126, 0.7152, 0.0722));
					L *= RAY_SRC_CAP / (RAY_SRC_CAP + y); // eased under the cap: the sky's light, not the disc's
					// (Look D1) squared: a cloud's thin edge gives little, so the gaps stand out as beams
					T = clamp(T, 0.0, 1.0);
					beams += L * T * T;
				}
			}
			beams *= 0.25 * near * gate;
		}
	}
#endif
	outBeams = vec4(beams, 1.0);
}

#endif
