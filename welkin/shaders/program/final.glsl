// VOID Welkin: final. (HDR stage A, 2026-10-19) The ONE output transform (lib/hdr.glsl): the scene-linear light in
// colortex0 is exposed (a per-hour and per-weather curve), brought to the screen by one filmic curve (hue kept, soft
// toe, long shoulder), graded (lib/grade.glsl), then given invisible anti-banding: one 8-bit step of triangular noise,
// fixed per pixel, so the smooth gradients of the sky, the haze and the light leave the 8-bit screen without contour
// lines. At that size the noise cannot be seen as a pattern. No TAA, no blur, no palette steps. Nothing else in the
// pack tone-maps or caps; the look3/look4 roll-offs and the old shoulder are gone (the curve's shoulder is the one).
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
flat out float gradeDusk; // (look3) the evening's last degrees, clear sky: 0..1
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
		gradeDusk = sunAngle < 0.5 ? (1.0 - smoothstep(0.03, 0.30, f.sunW.y)) * f.up * f.clear : 0.0;
	}
#else
	gradeHour = vec4(0.0, 1.0, 1.0, 0.0);
	gradeOpen = 0.0;
	gradeDusk = 0.0;
#endif
	// ---- end look
}

#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;
// ---- look (welkin-look): the colour grade
#include "/lib/grade.glsl"
#include "/lib/hdr.glsl"
#ifndef WU_colortex5
#define WU_colortex5
uniform sampler2D colortex5;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
flat in vec4 gradeHour;
flat in float gradeOpen;
flat in float gradeDusk;
// ---- end look

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif

// ---- atmos (welkin-atmos): the sun beams and the bloom
#if defined SHAFT_BEAMS && defined WORLD_OVERWORLD
#define FINAL_BEAMS
#include "/lib/rays.glsl"
#ifndef WU_colortex13
#define WU_colortex13
uniform sampler2D colortex13;
#endif
#ifndef WU_depthtex0
#define WU_depthtex0
uniform sampler2D depthtex0;
#endif
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhDepthTex0
#define WU_dhDepthTex0
uniform sampler2D dhDepthTex0;
#endif
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

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec3 sc = max(texelFetch(colortex0, p, 0).rgb, 0.0); // scene-linear light (lib/hdr.glsl)
#ifndef DEBUG_PLAIN
	// the inner debug views draw their own colours: shown as they are
	outColor = vec4(clamp(sc, 0.0, 1.0), 1.0);
	return;
#endif
	float expo = hdrExposure();
	// ---- atmos (welkin-atmos): the sun beams, then the bloom, in scene light before the output transform (HDR stage B)
#ifdef FINAL_BEAMS
	{
		// (HDR stage C) the light shafts (lib/rays.glsl, composite5..7's radial blur in colortex13): in-scattered light,
		// by how much air lies in front (a floor near the eye, so the beams under a crown keep the near air they live
		// in), a share over the open sky
		vec3 rays = texture(colortex13, texcoord).rgb;
		float dR = texelFetch(depthtex0, p, 0).r;
		float air;
		if (dR < 1.0) {
			vec4 vp = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, dR * 2.0 - 1.0, 1.0);
			float dist = length(vp.xyz / vp.w);
			air = max(1.0 - exp(-dist / RAY_AIR), RAY_NEAR_FLOOR);
		} else {
#ifdef DISTANT_HORIZONS
			air = texelFetch(dhDepthTex0, p, 0).r < 1.0 ? 1.0 : RAY_SKY;
#else
			air = RAY_SKY;
#endif
		}
		sc += rays * (RAY_GAIN * air / expo);
#if SHAFT_DEBUG == 5 || SHAFT_DEBUG == 6
		outColor = vec4(toDisplay(clamp(rays * 4.0, 0.0, 1.0)), 1.0); // the shafts' blurred light, x4
		return;
#endif
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
		// (the sky lends nothing out: the pixel sun, moon and stars keep their whole light; the land and the clouds lend
		// what they gave the halo, so the halo spreads light rather than making more of it)
		float lend = d >= 1.0 ? 0.0 : 1.0;
#ifdef WORLD_OVERWORLD
		lend = d >= 1.0 && texelFetch(colortex6, p, 0).r <= 999.0 ? 1.0 : lend;
#endif
		vec4 glow = texture(colortex9, texcoord); // rgb the halo, a the frame's mean bright share (composite4), exposed
		vec3 add = bloomAddHdr(sc * expo, glow.rgb, emit, lend, glow.a,
			d < 1.0 && emit <= 0.0 && texelFetch(colortex3, p, 0).a <= 0.25 ? 1.0 : 0.0);
		// (what a pixel lends out never takes more than half its light: a flame keeps its own brightness under its glow)
		sc = max(sc + add / expo, 0.5 * sc);
	}
#endif
	// ---- end atmos

	// ======== the output transform: exposure, the one filmic curve, the grade, the dither ========
	vec3 lin = hdrOutput(sc, expo); // linear display, 0..1
	vec3 c = toDisplay(lin);
#ifdef WORLD_OVERWORLD
	// (look3, 2026-10-18; part of the grade since HDR stage A) the evening sky's gradient across the whole dome, the
	// clouds in it too: gold round the sun, then the hue turns through a dusky rose to lilac and a soft blue-grey as the
	// view turns away from the sun. The brightness is kept; only the open sky and the clouds (not the land)
	if (gradeDusk > 0.0) {
		float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
		d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
		if (d >= 1.0) {
			vec3 v = normalize((gbufferProjectionInverse * vec4((vec2(p) + 0.5) / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0)).xyz);
			float mu = dot(v, normalize(sunPosition));
			// (it fades out down to the horizon, where the sky meets the far haze over the land: no seam there)
			float elev = (mat3(gbufferModelViewInverse) * v).y;
			float w = smoothstep(0.92, 0.40, mu) * gradeDusk * smoothstep(0.0, 0.18, elev);
			float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
			// display-value hues at luma 1: a dusky rose-lilac across, a blue-grey far from the sun
			vec3 lilac = vec3(1.02, 0.93, 1.10) / 0.9631;
			vec3 dusk = vec3(0.84, 0.92, 1.08) / 0.9158;
			vec3 hue = mix(lilac, dusk, smoothstep(0.55, -0.1, mu));
			// (a cloud less: its sunset-lit flanks keep their peach and gold)
			bool cl = texelFetch(colortex6, p, 0).r <= 999.0;
			c = mix(c, hue * y, (cl ? 0.36 : 0.62) * w);
			// and a little dimmer away from the sun, so the glow round it reads as light in the air, not a sheet at white
			c *= 1.0 - (cl ? 0.10 : 0.20) * w;
		}
	}
#endif
	// ---- look (welkin-look): the colour grade (lib/grade.glsl), after the curve
	c = welkinGrade(c, gradeHour, gradeOpen);
	// ---- end look
#if DEBUG_VIEW == 23
	// (HDR stage A) the scene's light before the output transform, in stops from mid grey (exposure in)
	c = hdrFalseColour(sc * expo, p, ivec2(viewWidth, viewHeight));
#elif DEBUG_VIEW == 24
	// (HDR stage A) what the output transform (curve and grade) pushes to white or black
	c = hdrClipView(toLinear(c));
#endif
	// the anti-banding: two uniform values per pixel summed, a triangle from -1 to +1 of one 8-bit step, mean zero
	uint h = pixelHash(p, 7u);
	float tri = (float(h & 0xFFFFu) + float(h >> 16u)) / 65535.0 - 1.0;
#if DEBUG_VIEW == 23 || DEBUG_VIEW == 24 || DEBUG_VIEW == 25
	tri = 0.0;
#endif
	c = clamp(c + tri / 255.0, 0.0, 1.0);
#if DEBUG_VIEW == 25
	// (HDR stage C) the frame-time probe (program/prepare.glsl): the top-left 8x8 pixels hold the mean frame time in
	// tenths of a millisecond, r the high byte, g the low byte; b the frames counted / 16
	if (p.x < 8 && p.y >= int(viewHeight) - 8) {
		vec4 ftp = texelFetch(colortex5, ivec2(0, 30), 0);
		float tenths = floor(ftp.r * 1000.0 + 0.5); // (seconds / 0.1) x 1000 = tenths of a ms
		c = vec3(floor(tenths / 256.0), mod(tenths, 256.0), min(ftp.g * 4096.0 / 16.0, 255.0)) / 255.0;
	}
#endif
	outColor = vec4(c, 1.0);
}

#endif
