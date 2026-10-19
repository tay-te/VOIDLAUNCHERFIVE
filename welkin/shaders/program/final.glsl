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
// ---- look (welkin-look): the hour for the colour grade (lib/grade.glsl), once per frame
flat out vec4 gradeHour; // warm, day, clear, morning
flat out float gradeOpen; // the eye's openness to the sky
#ifdef WORLD_OVERWORLD
#include "/lib/sky/luminous.glsl"
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
#endif
#ifndef WU_eyeBrightnessSmooth
#define WU_eyeBrightnessSmooth
uniform ivec2 eyeBrightnessSmooth;
#endif
// ---- end look

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
	// ---- look (welkin-look)
#ifdef WORLD_OVERWORLD
	{
		SkyLum f = skyLumFrame(normalize(mat3(gbufferModelViewInverse) * sunPosition));
		gradeHour = vec4(f.warm * f.up, f.day, f.clear, sunAngle < 0.25 || sunAngle > 0.75 ? 1.0 : 0.0);
		gradeOpen = smoothstep(0.15, 0.7, float(eyeBrightnessSmooth.y) / 240.0);
	}
#else
	gradeHour = vec4(0.0, 1.0, 1.0, 0.0);
	gradeOpen = 0.0;
#endif
	// ---- end look
}

#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;
// ---- look (welkin-look): the colour grade
#include "/lib/grade.glsl"
flat in vec4 gradeHour;
flat in float gradeOpen;
// ---- end look

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
#ifndef WU_colortex3
#define WU_colortex3
uniform sampler2D colortex3; // the water marks (> 0: water in front)
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
// (golden, 2026-10-18) the sky round the pixel sun kept under its disc (below, after the bloom)
#ifdef WORLD_OVERWORLD
#include "/lib/sky/celestial.glsl"
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
uniform sampler2D colortex6; // the clouds' distance (> 999: none)
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
#endif

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
		// (look, welkin-look: laid on as a screen, so the beams stream bright through the dark lanes between trunks and
		// over shaded land, and add little over what is already bright: open water and sky toward the sun take no veil;
		// was c += b * 0.25)
		c += b * 0.35 * (1.0 - clamp(c, 0.0, 1.0)); // (look2: 0.35, was 0.25)
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
		// (look2: 0.3, was 0.5: with the stronger scatter a small light, a lamp or a lit window, gave out more than its halo
		// brought back and dimmed)
		float lend = d >= 1.0 ? 0.0 : 0.3;
#ifdef WORLD_OVERWORLD
		// (a cloud lends out as the land does, so a sunlit deck spreads its light rather than only gaining the halo over
		// it; the merge of phase 2)
		lend = d >= 1.0 && texelFetch(colortex6, p, 0).r <= 999.0 ? 0.3 : lend;
#endif
		vec4 glow = texture(colortex9, texcoord); // rgb the halo, a the frame's mean bright share (composite4)
		// (review round 1: a lit surface that does not glow keeps its pale texels out of the halo, lib/bloom.glsl)
		c = bloomApply(c, glow.rgb, emit, lend, glow.a,
			d < 1.0 && emit <= 0.0 && texelFetch(colortex3, p, 0).a <= 0.25 ? 1.0 : 0.0);
	}
#endif
	// ---- end atmos
#ifdef WORLD_OVERWORLD
	// (golden, 2026-10-18) the pixel sun a crisp solid disc in its glow: round the sun (within about 25 degrees) the
	// open sky, its glow and the halo the bloom lays on it ease under 0.97 of white, hue kept (a soft shoulder from
	// 0.7), while the disc's own art pixels go on to white. The glow and the halo had blazed the sky round a sun some 10 to
	// 25 degrees up to the disc's own white, and the disc dissolved into a soft white blob
	{
		vec3 v = normalize((gbufferProjectionInverse * vec4((vec2(p) + 0.5) / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0)).xyz);
		vec3 sv = normalize(sunPosition);
		float near = smoothstep(0.88, 0.97, dot(v, sv));
		float m = max(c.r, max(c.g, c.b));
		if (near > 0.0 && m > 0.7) {
			float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
			d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
			// only the open sky (not the land, not a cloud)
			near *= d >= 1.0 && texelFetch(colortex6, p, 0).r > 999.0 ? 1.0 : 0.0;
			// the disc's own art pixels (lib/sky/celestial.glsl, as the dome draws them) go on to white
			mat3 toW = mat3(gbufferModelViewInverse);
			vec3 sunW = normalize(toW * sv);
			ivec2 ip;
			if (near > 0.0 && skyArtPixel(normalize(toW * v), sunW, skyPathAxis(sunW), ip)) {
				int k = skySunPixel(ip);
				near *= k >= 1 && k <= 3 ? 0.0 : 1.0;
			}
			float t = 0.7 + 0.27 * (1.0 - exp(-(m - 0.7) / 0.27));
			c = mix(c, c * (t / m), near);
		}
	}
#endif
	// ---- look (welkin-look): the colour grade (lib/grade.glsl)
	c = welkinGrade(c, gradeHour, gradeOpen);
	// ---- end look
	c = shoulder(c);
	// two uniform values per pixel summed: a triangle from -1 to +1 of one 8-bit step, mean zero
	uint h = pixelHash(p, 7u);
	float tri = (float(h & 0xFFFFu) + float(h >> 16u)) / 65535.0 - 1.0;
	c = clamp(c + tri / 255.0, 0.0, 1.0);
	outColor = vec4(c, 1.0);
}

#endif
