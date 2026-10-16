// VOID Welkin: final. A gentle shoulder for anything lit past white, then invisible anti-banding: one 8-bit step of
// triangular noise, fixed per pixel, so the smooth gradients of the sky, the haze and the light leave the 8-bit
// screen without contour lines. At that size the noise cannot be seen as a pattern. No TAA, no blur, no palette steps.
// atmos (welkin-atmos, shafts and bloom step): before the shoulder, the sun beams (composite3, colortex12: the 4x4
// half-resolution texels round the pixel, each noise offset once) are added, then the bloom (composite4's halo in
// colortex9, lib/bloom.glsl) is laid on in linear light with headroom.

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

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif

// ---- atmos (welkin-atmos): the sun beams and the bloom
#if defined SHAFT_BEAMS && defined WORLD_OVERWORLD
#define FINAL_BEAMS
#ifndef WU_colortex12
#define WU_colortex12
uniform sampler2D colortex12;
#endif
#endif
#ifdef BLOOM
#include "/lib/bloom.glsl"
#ifndef WU_colortex9
#define WU_colortex9
uniform sampler2D colortex9;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
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
#ifdef WORLD_OVERWORLD
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6; // the clouds' distance (> 999: none)
#endif
#endif
#endif
// ---- end atmos

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 shoulder(vec3 c) {
	// linear to 0.8, then a soft roll into 1.0
	vec3 over = max(c - 0.8, 0.0);
	return min(c, 0.8) + 0.2 * (1.0 - exp(-over * 5.0));
}

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec3 c = max(texelFetch(colortex0, p, 0).rgb, 0.0);
	// ---- atmos (welkin-atmos): the sun beams, then the bloom
#ifdef FINAL_BEAMS
	{
		// four bilinear taps at the corners between texels: the 4x4 half-resolution texels round the pixel, evenly
		vec2 hp = 1.0 / vec2(textureSize(colortex12, 0));
		// (the texel boundary between q - 2 | q - 1 and q | q + 1 in each axis)
		vec2 cu = vec2(p >> 1) * hp;
		vec3 b = texture(colortex12, cu + vec2(-hp.x, -hp.y)).rgb + texture(colortex12, cu + vec2(hp.x, -hp.y)).rgb
			+ texture(colortex12, cu + vec2(-hp.x, hp.y)).rgb + texture(colortex12, cu + vec2(hp.x, hp.y)).rgb;
		c += b * 0.25;
	}
#endif
#ifdef BLOOM
	{
		vec4 fl = texelFetch(colortex2, p, 0);
		float emit = fl.x > 0.5 ? unpackEmission(fl.z) : 0.0;
		float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
		d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
		// (the sky lends nothing out: the pixel sun, moon and stars keep their whole brightness; the land half)
		float lend = d >= 1.0 ? 0.0 : 0.5;
#ifdef WORLD_OVERWORLD
		// (a cloud lends out as the land does, so a sunlit deck spreads its light rather than only gaining the halo over
		// it; the merge of phase 2)
		lend = d >= 1.0 && texelFetch(colortex6, p, 0).r <= 999.0 ? 0.5 : lend;
#endif
		vec4 glow = texture(colortex9, texcoord); // rgb the halo, a the frame's mean bright share (composite4)
		// (review round 1: a lit surface that does not glow keeps its pale texels out of the halo, lib/bloom.glsl)
		c = bloomApply(c, glow.rgb, emit, lend, glow.a, d < 1.0 && emit <= 0.0 ? 1.0 : 0.0);
	}
#endif
	// ---- end atmos
	c = shoulder(c);
	// two uniform values per pixel summed: a triangle from -1 to +1 of one 8-bit step, mean zero
	uint h = pixelHash(p, 7u);
	float tri = (float(h & 0xFFFFu) + float(h >> 16u)) / 65535.0 - 1.0;
	c = clamp(c + tri / 255.0, 0.0, 1.0);
	outColor = vec4(c, 1.0);
}

#endif
