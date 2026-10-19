// VOID Welkin, atmos (welkin-atmos, shafts and bloom step, 2026-10-16): composite3, at half resolution (colortex14 and
// colortex12 are half size, so Iris draws the pass at that size), after the near atmosphere (composite2). Two things:
//
//   colortex14  the bloom's bright source (lib/bloom.glsl): each texel the 2x2 full-resolution pixels under it, the
//               bright share of each (their headroom given back, a glowing texel its own light, a soft threshold),
//               averaged with the soft weights 1 / (1 + 0.15 brightness) so a single blazing pixel cannot flicker into a blotch.
//               Mipmapped for composite4's gather. Every dimension, with BLOOM.
//   colortex12  the sun beams' source (SHAFT_BEAMS, lib/rays.glsl; look pass 6), overworld: r the light the open sky
//               sends toward the eye at each texel (the 2x2 pixels under it), by its brightness and its closeness to
//               the sun on the screen; clouds by their lit, thin parts; land and leaves nothing. composite5 to 7 blur it
//               toward the sun in three chained passes; final lays the beams on. colortex12 (the occlusion's resolve)
//               is free after the light pass.
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
#include "/lib/rays.glsl"
flat out vec3 beamSun;  // the sun's place on the screen (xy, 0..1), and whether it is in front (z)
flat out vec2 beamGate; // x: whether the beams are on at all (the hour, the weather, the eye not in water), y: the
                        // sun-side horizon's luma (display), the yardstick for the sky's brightness
#endif
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
#ifdef C3_BEAMS
	beamSun = raySunScreen(gbufferProjection, sunPosition);
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	SkyLum f = skyLumFrame(sunW);
	float h = sunW.y;
	beamGate.x = smoothstep(-0.03, 0.04, h) * f.day * f.clear * (isEyeInWater == 0 ? 1.0 : 0.0) * beamSun.z;
	beamGate.y = max(skyLumLuma(skyHorizon(true)), 0.05);
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
		return bloomCloudShare(c);
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
flat in vec3 beamSun;
flat in vec2 beamGate;
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
			vec3 b = bloomSource(c, emit, bloomDiffuseAt(p, emit));
			// (the open sky and the clouds' bodies only in part: lib/bloom.glsl BLOOM_SKY, BLOOM_CLOUD)
			b *= bloomShareAt(p, c);
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

	// r the source, g the same with nothing in the way, a the gate
	vec4 beams = vec4(0.0);
#ifdef C3_BEAMS
	if (beamGate.x > 0.0) {
		vec2 uv = (vec2(q) * 2.0 + 1.0) / vec2(viewWidth, viewHeight);
		float aspect = viewWidth / viewHeight;
		float near = rayNear(length((beamSun.xy - uv) * vec2(aspect, 1.0)));
		ivec2 hi = ivec2(viewWidth, viewHeight) - 1;
		for (int k = 0; k < 4; k++) {
			ivec2 p = min(q * 2 + ivec2(k & 1, k >> 1), hi);
			float lum = dot(max(texelFetch(colortex0, p, 0).rgb, 0.0), vec3(0.2126, 0.7152, 0.0722)) / beamGate.y;
			float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
			d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
			if (d < 1.0) {
				continue; // land, leaves, the far land: nothing
			}
			float o;
			if (texelFetch(colortex6, p, 0).r <= 999.0) {
				// a cloud: its lit, thin parts (brighter than the horizon's sky) shine through; its dark body not
				o = RAY_CLOUD * smoothstep(0.7, 1.5, lum) * min(lum, 1.8);
			} else {
				// the open sky by its own brightness (the sun's disc no more than the bright sky round it)
				o = clamp(lum, 0.3, 1.8);
			}
			beams.r += o;
		}
		beams.r *= 0.25 * near;
		beams.g = near; // the blur carries it along, for the openness
		beams.a = beamGate.x; // constant over the frame: the blurs keep it
	}
#endif
	outBeams = beams;
}

#endif
