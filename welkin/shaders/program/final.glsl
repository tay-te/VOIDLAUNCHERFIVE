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
flat out float gradeDusk; // (look3) the evening's last degrees, clear sky: 0..1
flat out vec3 rayLight; // (look pass 6) the light shafts' light at full strength (display; lib/rays_light.glsl)
#ifdef WORLD_OVERWORLD
#include "/lib/sky/luminous.glsl"
#include "/lib/rays_light.glsl"
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
		rayLight = rayLightFor(f);
	}
#else
	gradeHour = vec4(0.0, 1.0, 1.0, 0.0);
	gradeOpen = 0.0;
	gradeDusk = 0.0;
	rayLight = vec3(0.0);
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
flat in float gradeDusk;
flat in vec3 rayLight;
// ---- end look

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif

// ---- atmos (welkin-atmos): the sun beams and the bloom
#if defined SHAFT_BEAMS && defined WORLD_OVERWORLD
#define FINAL_BEAMS
#ifndef WU_colortex13
#define WU_colortex13
uniform sampler2D colortex13; // (look pass 6) the light shafts, blurred toward the sun (composite7, lib/rays.glsl)
#endif
#ifndef WU_depthtex0
#define WU_depthtex0
uniform sampler2D depthtex0;
#endif
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
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
// (look pass 6) the shafts' strength over the scene, on the open sky, and how much air (blocks) makes a full beam, with
// the share the nearest air keeps (under a crown the near air is exactly where the beams are)
#define RAY_GAIN 5.0
#define RAY_SKY 0.35
#define RAY_AIR 40.0
#define RAY_AIR_NEAR 0.4
#define RAY_SHADE 0.5
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
	vec3 rayDbg = vec3(0.0); // (look pass 6) SHAFT_DEBUG 5 and 6
	// ---- atmos (welkin-atmos): the sun beams, then the bloom
#ifdef FINAL_BEAMS
	{
		// (look pass 6) the light shafts (lib/rays.glsl), already smooth: one bilinear read
		vec3 rays = textureLod(colortex13, texcoord, 0.0).rgb;
		// as light in the air between the eye and what it sees: the air a ray crosses, with a floor near the eye
		float d0 = texelFetch(depthtex0, p, 0).r;
		float air = 1.0;
		bool sky = false;
		if (d0 < 1.0) {
			vec4 vp = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, d0 * 2.0 - 1.0, 1.0);
			air = mix(RAY_AIR_NEAR, 1.0, 1.0 - exp(-length(vp.xyz / vp.w) / RAY_AIR));
		} else {
			sky = true;
#ifdef DISTANT_HORIZONS
			sky = texelFetch(dhDepthTex0, p, 0).r >= 1.0;
#endif
		}
		vec3 b = rayLight * (sky ? rays.y * RAY_SKY : rays.x * air);
		// the shadowed lanes over the sky and the clouds: the air there lends less of the sun's glow, so they dim a little
		c *= 1.0 - RAY_SHADE * (sky ? rays.z : 0.0);
		// laid on as a screen, so the beams stream bright through the dark lanes and add little over what is already
		// bright
		c += b * RAY_GAIN * (1.0 - 0.6 * clamp(c, 0.0, 1.0));
		rayDbg = SHAFT_DEBUG == 5 ? rays : b * RAY_GAIN;
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
	bool sunArt = false; // (look3) the pixel sun's own art pixels
#ifdef WORLD_OVERWORLD
	// (look3, 2026-10-18) the evening sky's gradient across the whole dome, the clouds in it too: gold round the sun,
	// then the hue turns through a dusky rose to lilac and a soft blue-grey as the view turns away from the sun (the
	// sky and its decks had been one salmon from edge to edge: the sun-side glow, the haze and the warm decks all lean
	// peach). The brightness is kept; only the open sky and the clouds (not the land)
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
	// (look4, 2026-10-18) the pixel sun's own art pixels: they go on to a crisp white whatever lies round them (the
	// glow round the disc no longer eases under it: golden, 2026-10-18 held the sky within 25 degrees of the sun under
	// 0.97 of white, which with the roll-off below flattened the whole of a low sun's glow into one peach field)
	{
		vec3 v = normalize((gbufferProjectionInverse * vec4((vec2(p) + 0.5) / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0)).xyz);
		vec3 sv = normalize(sunPosition);
		if (dot(v, sv) > 0.99) {
			float d = texelFetch(depthtex0, p, 0).r;
#ifdef DISTANT_HORIZONS
			d = min(d, texelFetch(dhDepthTex0, p, 0).r);
#endif
			mat3 toW = mat3(gbufferModelViewInverse);
			vec3 sunW = normalize(toW * sv);
			ivec2 ip;
			if (d >= 1.0 && texelFetch(colortex6, p, 0).r > 999.0 && skyArtPixel(normalize(toW * v), sunW, skyPathAxis(sunW), ip)) {
				int k = skySunPixel(ip);
				sunArt = k >= 1 && k <= 3;
			}
		}
	}
#endif
	// (look3, 2026-10-18) highlights roll off and hold their colour: whatever the light, the glow and the bloom have
	// pushed past white eases under white with its hue kept, so a blazing gold stays gold instead of clipping channel by
	// channel to white paper. By day only, and never on the pixel sun's own art pixels.
	// (look4, 2026-10-18) a long shoulder, not a wall: from 0.7 the light eases toward white over a wide range (1 shows
	// at 0.85, 2 at 0.94, 4 at 0.97), so a low sun's heart still stands over its glow and the glow over the sky round it,
	// where look3's knee at 0.78 had sent anything from 1 to 5 to one 0.93. Through the warm hours the colour past white
	// burns in as gold over the first stops (the glow, the glitter path), and the hottest light, the sun's heart, turns
	// a gold-white, as film's highlights do: gold-white heart, gold, amber, then the rose air
	{
		float m = max(c.r, max(c.g, c.b));
		// (the long shoulder through the warm hours; by day look3's knee, so a noon's white clouds keep their light)
		float K = mix(0.78, 0.70, gradeHour.x);
		float amt = gradeHour.y * gradeOpen * (sunArt ? 0.0 : 1.0);
		if (m > K && amt > 0.0) {
			float o = m - K;
			float t = mix(K + (1.0 - K) * (1.0 - exp(-o / (1.0 - K))), K + (1.0 - K) * o / (o + (1.0 - K)), gradeHour.x);
			vec3 r = c * (t / m);
			float burn = smoothstep(0.9, 2.0, m) * (1.0 - smoothstep(2.2, 6.0, m)) * 0.35 * gradeHour.x;
			r = mix(r, vec3(1.0, 0.70, 0.40) * t, burn);
			float heart = smoothstep(2.0, 6.0, m) * 0.75;
			r = mix(r, vec3(1.0, 0.90, 0.72) * t, heart);
			c = mix(c, r, amt);
		}
	}
	// ---- look (welkin-look): the colour grade (lib/grade.glsl)
	c = welkinGrade(c, gradeHour, gradeOpen);
	// ---- end look
	c = shoulder(c);
	// two uniform values per pixel summed: a triangle from -1 to +1 of one 8-bit step, mean zero
	uint h = pixelHash(p, 7u);
	float tri = (float(h & 0xFFFFu) + float(h >> 16u)) / 65535.0 - 1.0;
	c = clamp(c + tri / 255.0, 0.0, 1.0);
#if SHAFT_DEBUG == 5 || SHAFT_DEBUG == 6
	c = rayDbg; // (look pass 6) the shafts alone, as they are added (display)
#endif
	outColor = vec4(c, 1.0);
}

#endif
