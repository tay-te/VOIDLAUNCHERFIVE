// VOID Welkin: the near atmosphere (composite2 in every dimension). The scene so far, with the air laid over it, or a
// debug view (DEBUG_VIEW, lib/settings.glsl). Before it, in the overworld only: composite, the voxel clouds (M3: they
// wrote colortex0 and their distance in colortex6), and composite1, far weather (M4, still to come).
//
// Order per pixel:
//   1. water fog (lib/water.glsl), so the far water's bed (LOD) and the near water's bed (chunks) read the same under
//      the same depth of water; while the eye is in water it owns the view and nothing below runs
//   2. overworld, eye in open air: the haze by country (M2, lib/haze/) from the camera out, cut where valley mist
//      begins: the haze behind the mist, then the valley mist (M1, lib/mist.glsl), then the haze in front. The haze
//      fades into the sky table's horizon for its kind, and draws the world's far edge (deferred's FAR_FADE is off
//      while it is on). Cloud pixels (colortex6 < 999, M3) get no haze and no water fog: the clouds fogged themselves
//      by ring; the mist only lies in front of a cloud against the sky.
//      Workstream D (air and tone, 2026-10-13): the haze's colour takes the sun's forward scatter (lib/air.glsl
//      airMie); the sun shafts (lib/shafts.glsl, resolved from colortex13) go on before the haze in front; the land
//      then takes its aerial perspective (airPerspective)
//   3. debug views
// Still to come here: the underground air (M5) and the near-water reflections (M6, HIGH).

#define WELKIN_DEPTH_BEHIND
#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

#if defined VALLEY_MIST && defined WORLD_OVERWORLD && DEBUG_VIEW == 0
// the valley mist's camera sample, the same for every pixel: worked out here, once a corner, not once a pixel
#include "/lib/mist.glsl"
flat out vec4 mistCam;    // the camera's slab top, its density, the hour's mist, the mist on
flat out ivec4 mistCamCells; // the camera's cell, the tiles' centre
#endif

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
#if defined VALLEY_MIST && defined WORLD_OVERWORLD && DEBUG_VIEW == 0
	MistTiles t = mistTiles();
	MistPoint a = mistAt(vec2(0.0), t);
	mistCam = vec4(a.top, a.density, t.time, t.on ? 1.0 : 0.0);
	mistCamCells = ivec4(t.camCell, t.centre);
#endif
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/water.glsl"
#include "/lib/void.glsl"
#include "/lib/fade.glsl"
#if defined VALLEY_MIST && defined WORLD_OVERWORLD
#define WELKIN_VALLEY_MIST
#include "/lib/mist.glsl"
#endif
#if defined WORLD_OVERWORLD
#include "/lib/sky/table.glsl"
#include "/lib/haze/haze.glsl"
#include "/lib/air.glsl"
#if (defined SUN_SHAFTS || WD_SHAFTS || WD_HAZE) && defined SHADOWS
#define WELKIN_SHAFTS_READ
#include "/lib/shafts.glsl"
#endif
#include "/lib/depthfx.glsl"
#if WD_SHAFTS
#ifndef WU_colortex12
#define WU_colortex12
uniform sampler2D colortex12;
#endif
#endif
#if HAZE_DEBUG > 0 && HAZE_DEBUG < 4
#include "/lib/haze/haze_debug.glsl"
#endif
#endif

in vec2 texcoord;
#if defined WELKIN_VALLEY_MIST && DEBUG_VIEW == 0
flat in vec4 mistCam;
flat in ivec4 mistCamCells;
#endif

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#if defined WORLD_OVERWORLD && defined CLOUDS
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
#endif
#endif

#if DEBUG_VIEW != 0
#include "/lib/debug.glsl"
#endif
#if SKY_DEBUG == 1
#include "/lib/sky/debug.glsl"
#endif

#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
#ifdef DEBUG_JOIN

#ifndef WELKIN_GBUFFER_SAMPLERS
#define WELKIN_GBUFFER_SAMPLERS
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif

// Whether the chunks' land is at `q` (1), the LOD's (0), or neither (-1: the sky, or vanilla's clouds, marked in
// colortex2 by gbuffers_clouds).
int joinSide(ivec2 q) {
	q = clamp(q, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
	Surface s = surfaceAt(q, (vec2(q) + 0.5) / vec2(viewWidth, viewHeight));
	vec4 flags = texelFetch(colortex2, q, 0);
	bool cloud = flags.x < 0.5 && int(flags.y * 255.0 + 0.5) == MAT_CLOUD;
	return s.sky || cloud ? -1 : (s.chunk ? 1 : 0);
}

// A magenta line, two pixels wide, where the chunks' land meets the LOD's.
vec3 joinLine(ivec2 p, vec3 c) {
	int here = joinSide(p);
	if (here < 0) {
		return c;
	}
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i == 0 ? 2 : (i == 1 ? -2 : 0), i == 2 ? 2 : (i == 3 ? -2 : 0));
		int there = joinSide(p + o);
		if (there >= 0 && there != here) {
			return vec3(1.0, 0.0, 1.0);
		}
	}
	return c;
}
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

// A pinhole in the far land or water: a lone pixel well under the horizon that shows what lies behind a surface
// through a crack between two of the LOD's tiles, with that surface on both sides of it. Either the sky shone through
// (a white speck on the far water), or the bed under the far water did (the water is drawn by dh_water, a separate
// mesh: its tiles' seams and T-joins let the lit bed through as specks and faint straight lines across a lake). It is
// drawn as its neighbour: the pixel to read instead, or p itself.
bool pinholeWater(ivec2 q) {
	return texelFetch(colortex3, q, 0).a > 0.0;
}

ivec2 pinholeFill(ivec2 p, vec2 uv) {
	Surface s = surfaceAt(p, uv);
	vec3 d = mat3(gbufferModelViewInverse) * (s.sky ? viewFromDepth(uv, 1.0, gbufferProjectionInverse) : s.view);
	if (d.y > -0.006 * length(d)) {
		return p;
	}
	vec2 px = 1.0 / vec2(viewWidth, viewHeight);
	if (s.sky) {
		bool l = !surfaceAt(p + ivec2(-1, 0), uv - vec2(px.x, 0.0)).sky;
		bool r = !surfaceAt(p + ivec2(1, 0), uv + vec2(px.x, 0.0)).sky;
		if (l && r) {
			return p + ivec2(-1, 0);
		}
		bool dn = !surfaceAt(p + ivec2(0, -1), uv - vec2(0.0, px.y)).sky;
		bool up = !surfaceAt(p + ivec2(0, 1), uv + vec2(0.0, px.y)).sky;
		return dn && up ? p + ivec2(0, 1) : p;
	}
	// the far water's seams: a pixel with no water on it, water on both sides, and itself farther than the water there
	if (!s.fromLod || pinholeWater(p)) {
		return p;
	}
	float dist = length(s.view);
	for (int k = 0; k < 2; k++) {
		ivec2 o = k == 0 ? ivec2(1, 0) : ivec2(0, 1);
		vec2 ou = vec2(o) * px;
		if (pinholeWater(p - o) && pinholeWater(p + o)) {
			Surface a = surfaceAt(p - o, uv - ou);
			Surface b = surfaceAt(p + o, uv + ou);
			if (!a.sky && !b.sky && dist > max(length(a.view), length(b.view)) + 0.5) {
				return p - o;
			}
		}
	}
	return p;
}

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec2 uv = texcoord;
#if DEBUG_VIEW == 0
	{
		ivec2 q = pinholeFill(p, uv);
		if (q != p) {
			uv += vec2(q - p) / vec2(viewWidth, viewHeight);
			p = q;
		}
	}
#endif
	vec4 color = texelFetch(colortex0, p, 0);
#if DEBUG_VIEW == 0
	// M3: a cloud drawn here (composite), nearer than whatever lies behind it; it fogged itself by ring
	bool cloud = false;
	float cloudKm = 1000.0;
#if defined WORLD_OVERWORLD && defined CLOUDS
	cloudKm = texelFetch(colortex6, p, 0).r;
	cloud = isEyeInWater == 0 && cloudKm < 999.0;
#endif
	// 1. water fog (not over a cloud in front of a lake, seen from above the deck)
	if (!cloud) {
		color.rgb = waterFog(p, uv, color.rgb);
	}
#if defined WORLD_OVERWORLD
	if (isEyeInWater == 0) {
		Surface s = surfaceAt(p, uv);
		vec3 eyePos = mat3(gbufferModelViewInverse) * s.view;
		// 2. haze behind the mist, the valley mist, haze in front. The split is 0 until M1 exposes the slab's entry
		// distance (MistRay.enter): then near mist banks stay crisp and far ones are hazed exactly.
		HzState st = hzState();
		vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
		vec3 air = skyHazeAir(st.kind, normalize(eyePos), sunW);
		// ---- air and tone (D): the haze's forward scatter toward the sun (lib/air.glsl)
		// depth prototype SKY: the haze's air is the dome's luminous horizon too, so the far land fades into the same
		// bright band the sky shows over it (below the horizon: the horizon's own colour in that direction)
		{
			vec3 dA = normalize(eyePos);
			if (dA.y < 0.0) {
				dA = normalize(vec3(dA.x, 0.0, dA.z) + vec3(1e-5, 0.0, 0.0));
			}
			// (half of it: the whole of the brighter band as the haze's colour paled every far ridge toward white)
			air = mix(air, wdSkyLum(air, dA, sunW), 0.5);
		}
		vec3 airPlain = air;
		air = airMie(air, normalize(eyePos), sunW, st.kind);
		vec3 n = s.sky ? vec3(0.0, 1.0, 0.0) : hzScreenNormal(eyePos);
		WelkinHaze h;
		if (cloud) {
			// a cloud takes the same air as land at its distance (the clear air thins with height, so a deck overhead
			// keeps its colour and the far decks low over the horizon sink into the haze the land beyond them meets),
			// but not the world's far edge: the clouds stand past the land
			vec3 rdW = normalize(mat3(gbufferModelViewInverse) * viewFromDepth(uv, 1.0, gbufferProjectionInverse));
			h = welkinHazeEx(st, rdW * (cloudKm * 1000.0), vec3(0.0, 1.0, 0.0), false, 0.0, air, gl_FragCoord.xy, false);
		} else {
			h = welkinHaze(st, eyePos, n, s.sky, 0.0, air, gl_FragCoord.xy);
		}
		color.rgb = welkinHazeBack(color.rgb, h);
#ifdef WELKIN_VALLEY_MIST
		// the mist lies far below the decks: in front of a cloud only when the cloud is against the sky
		if (!cloud || s.sky) {
			MistTiles mt;
			mt.data = mistCam.w > 0.5;
			mt.on = mt.data;
			mt.time = mistCam.z;
			mt.camCell = mistCamCells.xy;
			mt.centre = mistCamCells.zw;
			MistPoint ma;
			ma.top = mistCam.x;
			ma.density = mistCam.y;
			ma.pool = 0.0;
			ma.rise = 0.0;
			color.rgb = valleyMistWith(p, uv, color.rgb, mt, ma);
		}
#endif
#if WD_HAZE && defined WELKIN_SHAFTS_READ
		// depth prototype HAZE: the volumetric haze (deferred_shafts wdVolume): the air along the ray, lit by the sun
		// where the shadow map, the terrain and the clouds let it through, by the sky elsewhere. Its colour is the
		// luminous air itself (the horizon the far land fades into): darker and cooler where the air lies in shadow,
		// warmer and brighter toward a low sun (forward scatter). Its thickness follows the height profile (thick low
		// down, in the valleys and toward the horizon) and Gloam's air: the haziness of the land, the valley mist,
		// the rain.
		if (sunW.y > -0.08 && !cloud) {
			vec3 vol = wdVolResolve(p, s.sky ? 1e4 : length(eyePos));
			// (gently with the land's haziness: the country haze already thickens the air of a hazy land, and the two
			// together closed a jungle's distance into a white veil)
			float dens = mix(0.7, 1.1, clamp(st.haze.x, 0.0, 1.0)) * (1.0 + 1.6 * st.rain);
#ifdef WELKIN_VALLEY_MIST
			// (where Gloam's valley mist already lies the haze yields to it: both together closed a misty valley's
			// distance into a white veil)
			dens *= 1.0 - 0.5 * clamp(mistCam.y * mistCam.z * 2.0, 0.0, 1.0);
#endif
			dens *= (WD_ALL == 1 ? 0.4 : 1.0) * smoothstep(-0.08, 0.04, sunW.y);
			float a = 1.0 - exp(-WD_HAZE_SIGMA * dens * vol.b);
			if (a > 0.0) {
				vec3 dV = normalize(eyePos);
				float fsun = clamp(vol.g / max(vol.b, 1e-4), 0.0, 1.0);
				float mu = dot(dV, sunW);
				float lowS = 1.0 - smoothstep(0.1, 0.55, sunW.y);
				// the air's own colour: plain away from the sun, a cooler blue where it lies in shadow
				vec3 base = airPlain;
				vec3 cool = mix(base, base * vec3(0.80, 0.90, 1.08), 0.6) * 0.62;
				vec3 sunSide = skyHazeHorizon(st.kind, true);
				vec3 sunHue = sunSide / max(wdLuma(sunSide), 1e-3);
				float fwd = (0.35 + 0.65 * lowS) * (0.6 * wdHgPeak(mu, 0.82) + 0.4 * wdHgPeak(mu, 0.45));
				vec3 lit = base * 1.08 + sunHue * wdLuma(base) * fwd * 0.9;
				vec3 hz = mix(cool, lit, fsun);
				color.rgb = mix(color.rgb, hz, a * (s.sky ? 0.35 : 1.0));
			}
		}
#endif
		// ---- air and tone (D): the sun shafts (lib/shafts.glsl), in the near air, before the haze in front of it
#ifdef WELKIN_SHAFTS_READ
		if ((WD_SHAFTS == 1 || (shaftGate(sunW) > 0.0 && sunAngle < 0.5)) && sunW.y > -0.04
#if !defined SUN_SHAFTS && !WD_SHAFTS
				&& false
#endif
				) {
			float lit = shaftResolve(p, s.sky ? 1e4 : length(eyePos));
			color.rgb += shaftLight(normalize(eyePos), sunW, st.kind, lit, s.sky) * (cloud ? 0.0 : 1.0);
#if SHAFT_DEBUG == 1
			color.rgb = vec3(lit); // the resolved sunlit share of the near air
#elif SHAFT_DEBUG == 2
			color.rgb = vec3(texelFetch(colortex13, p >> 1, 0).r); // the raw term
#endif
		}
#endif
#if WD_SHAFTS
		// depth prototype SHAFTS, the screen-space beams (composite1, colortex12): the 4x4 half-resolution texels round
		// the pixel averaged (each noise offset once), as light in the sun-side horizon's hue, strongest at a low sun
		if (sunW.y > -0.04) {
			ivec2 qc = p >> 1;
			ivec2 hi = textureSize(colortex12, 0) - 1;
			float rays = 0.0;
			for (int y = -2; y < 2; y++) {
				for (int x = -2; x < 2; x++) {
					rays += texelFetch(colortex12, clamp(qc + ivec2(x, y), ivec2(0), hi), 0).r;
				}
			}
			rays /= 16.0;
			vec3 sunSideR = skyHazeHorizon(st.kind, true);
			vec3 sunLR = skyLandDirect();
			vec3 rayHue = mix(sunLR / max(wdLuma(sunLR), 1e-3), sunSideR / max(wdLuma(sunSideR), 1e-3), 0.3);
			float h0 = sunW.y;
			float gateR = smoothstep(-0.03, 0.05, h0) * mix(1.0, 0.3, smoothstep(0.3, 0.9, h0)) * (1.0 - skyNightStep());
			float xr = rays * gateR * (WD_ALL == 1 ? 0.7 : 1.0) * (s.sky ? 0.55 : 1.0);
			color.rgb += rayHue * (0.42 * (1.0 - exp(-xr / 0.42)));
		}
#endif
		color.rgb = welkinHazeFront(color.rgb, h);
		// ---- air and tone (D): the land's aerial perspective (lib/air.glsl)
		if (!s.sky && !cloud) {
			color.rgb = airPerspective(color.rgb, h.air, (air - airPlain) * (1.0 - h.back.a), length(eyePos), normalize(eyePos), sunW);
		}
#if HAZE_DEBUG > 0 && HAZE_DEBUG < 4
		welkinHazeDebug(st, h, eyePos, s.sky, color.rgb);
#endif
	}
#endif
#else
	color.rgb = debugView(p, uv, color.rgb);
#endif
#ifdef DEBUG_JOIN
	color.rgb = joinLine(p, color.rgb);
#endif
#if SKY_DEBUG == 1
	color.rgb = skyDebugOverlay(p, color.rgb); // the sky table, 8x, bottom left
#endif
	outColor = color;
}

#endif
