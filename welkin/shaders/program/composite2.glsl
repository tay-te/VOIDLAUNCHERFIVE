// VOID Welkin: the near atmosphere (composite2 in every dimension). The scene so far, with the air laid over it, or a
// debug view (DEBUG_VIEW, lib/settings.glsl). Before it, in the overworld only: composite, the voxel clouds (M3: they
// wrote colortex0 and their distance in colortex6), and composite1, far weather (P5: in the overworld it lays the water
// fog, then the far air past the seam on the land and the clouds, far valley mist and rain curtains; a sky or cloud
// pixel with a curtain in front it finishes with all its air, as this pass would, and marks it with the curtain's
// distance negated in colortex6: this pass then lays no haze, shafts or aerial perspective on it again, only the valley
// mist in front where the pixel is against the sky).
//
// Order per pixel:
//   1. water fog (lib/water.glsl; in the overworld composite1 lays it, P5), so the far water's bed (LOD) and the near water's bed (chunks) read the same under
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
//   then, in every dimension, the underground air (M5, P6, lib/underground/): the surface air above (the haze, the
//      mist, the shafts and the aerial perspective) hands over to it by ug.amount at cave mouths
// Near water on HIGH (WS_WATER_SSR, M6) first takes the terrain's reflection, before any air (lib/ws/ssr.glsl).

#define WELKIN_DEPTH_BEHIND
#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
// (HDR stage A: the bridge)
#include "/lib/hdr.glsl"

// (Look D9) the air under a cloud's shadow (below): the clouds' frame once, in the vertex shader (lib/clouds/envflat.glsl)
#if defined WORLD_OVERWORLD && defined CLOUDS && CLOUD_SHADOWS > 0 && defined DEBUG_PLAIN
#define C2_AIR_SHADOW
#endif

#ifdef VERTEX_SHADER

out vec2 texcoord;
#ifdef C2_AIR_SHADOW
#include "/lib/clouds/envflat.glsl"
#endif

#if defined VALLEY_MIST && defined WORLD_OVERWORLD && defined DEBUG_PLAIN
// the valley mist's camera sample, the same for every pixel: worked out here, once a corner, not once a pixel
#include "/lib/mist.glsl"
flat out vec4 mistCam;    // the camera's slab top, its density, the hour's mist, the mist on
flat out ivec4 mistCamCells; // the camera's cell, the tiles' centre
#endif

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
#if defined VALLEY_MIST && defined WORLD_OVERWORLD && defined DEBUG_PLAIN
	MistTiles t = mistTiles();
	MistPoint a = mistAt(vec2(0.0), t);
	mistCam = vec4(a.top, a.density, t.time, t.on ? 1.0 : 0.0);
	mistCamCells = ivec4(t.camCell, t.centre);
#endif
#ifdef C2_AIR_SHADOW
	wcEnvWrite(welkinCloudEnv());
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
// (look2) the day air's body: the veil over the first few hundred blocks
#define AIR_BODY 0.50
// ---- atmos (welkin-atmos): the luminous sky's air and the volumetric haze (lib/sky/luminous.glsl, lib/haze/volume.glsl)
// (the luminous sky first: the shafts take its frame)
#include "/lib/sky/luminous.glsl"
#if defined SUN_SHAFTS && defined SHADOWS
#define WELKIN_SHAFTS_READ
#include "/lib/shafts.glsl"
#endif
#ifdef HAZE_VOLUME
#define HV_READ
#include "/lib/haze/volume.glsl"
#endif
// (look2) the morning mist
#if MORNING_MIST > 0
#include "/lib/haze/dawn.glsl"
#endif
// ---- end atmos
#if HAZE_DEBUG > 0 && HAZE_DEBUG < 4
#include "/lib/haze/haze_debug.glsl"
#endif
#endif
// ---- M5 underground (P6): the cave air (lib/underground/)
#include "/lib/underground/ug_atmosphere.glsl"
#if UG_DEBUG != 0
#include "/lib/underground/ug_debug.glsl"
#endif

#ifdef C2_AIR_SHADOW
#include "/lib/clouds/shadow.glsl"
#include "/lib/clouds/envflat.glsl"
// (Look D9) how much of the country haze's and the aerial perspective's light a land pixel's air loses where the land and
// the air in front of it lie in a cloud's shadow, and how much cooler it turns (the sun's in-scatter goes, the sky's
// stays): the drifting patches of shade stay readable out to the far land instead of drowning in an evenly lit haze
#define AIR_CLOUD_DIM 0.42
#define AIR_CLOUD_TINT vec3(0.86, 0.97, 1.16)
#endif
// (Look D9) the night's ground mist: its amount (as the morning's MORNING_MIST x 0.1) and its moonlit level
#define NIGHT_MIST_AMT 0.42
#define NIGHT_MIST_LEVEL 1.1

in vec2 texcoord;
#if defined WELKIN_VALLEY_MIST && defined DEBUG_PLAIN
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
#ifndef WU_moonPosition
#define WU_moonPosition
uniform vec3 moonPosition;
#endif
#if defined WORLD_OVERWORLD // P5 far weather: was `&& defined CLOUDS`; composite1 writes colortex6 too
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
#endif
#endif
// ---- M6 water (welkin-water): reflections of terrain in near water (WS_WATER_SSR, on HIGH; lib/ws/ssr.glsl)
#if defined WS_WATER_SSR && defined WORLD_OVERWORLD
#include "/lib/sky/dome.glsl"
#include "/lib/ws/config.glsl"
#ifndef WU_colortex4
#define WU_colortex4
uniform sampler2D colortex4;
#endif
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#include "/lib/ws/ssr.glsl"
#define WELKIN_WS_SSR_ON
#endif
// ---- end M6 water

#if !defined DEBUG_PLAIN
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

/* RENDERTARGETS: 0,11 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outHist; // (HDR stage C) the packed history for the next frame (lib/temporal.glsl)
#include "/lib/temporal.glsl"
#ifndef WU_colortex11
#define WU_colortex11
uniform sampler2D colortex11;
#endif
#ifndef WU_colortex12
#define WU_colortex12
uniform sampler2D colortex12;
#endif

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
	// the water's seams: a pixel with no water on it, water on both sides, and itself farther than the water there.
	// (the defects pass, 2026-10-16: the chunks' water too, whose quads leave the same cracks along a fall's steps and
	// a shore's edge, dotted dark or bright with the rock or the bed behind)
	if (pinholeWater(p)) {
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
#if defined DEBUG_PLAIN
	{
		ivec2 q = pinholeFill(p, uv);
		if (q != p) {
			uv += vec2(q - p) / vec2(viewWidth, viewHeight);
			p = q;
		}
	}
#endif
	vec4 color = texelFetch(colortex0, p, 0);
	vec3 hzHist = vec3(0.0); // (HDR stage C) the air's resolved terms, kept for the next frame
	float hzHistDepth = 1e4;
	// (HDR stage B) the air in scene light: every layer is colour * T + in-scatter (the haze, the valley and dawn mist,
	// the haze volume, the aerial perspective), the shafts' in-scattered light added, the cave air the same
#if defined DEBUG_PLAIN
	// M3: a cloud drawn here (composite), nearer than whatever lies behind it; it fogged itself by ring
	bool cloud = false;
	float cloudKm = 1000.0;
	bool finished = false;
#if defined WORLD_OVERWORLD // P5 far weather: was `&& defined CLOUDS` (composite1 writes colortex6 too)
	cloudKm = texelFetch(colortex6, p, 0).r;
	// a rain curtain composite1 finished over the sky or a cloud, with all its air: marked by its distance negated
	finished = isEyeInWater == 0 && cloudKm < 0.0;
	cloud = isEyeInWater == 0 && cloudKm < 999.0;
#endif
#ifdef WELKIN_WS_SSR_ON
	// M6 water: the terrain near water reflects, before any air (not on a cloud drawn in front of the water); composite1
	// changed nothing within its reach (no far air, curtains or far mist inside 384 blocks, no water fog from above)
	if (!cloud && isEyeInWater == 0) {
		color.rgb = wsWaterSSR(color.rgb, p);
	}
#endif
	// 1. water fog (not over a cloud in front of a lake, seen from above the deck). P5 far weather: in the overworld
	// composite1 lays it, before the far air and layers (which must lie over a lake's water as over the land).
#if !defined WORLD_OVERWORLD
	if (!cloud) {
		color.rgb = waterFog(p, uv, color.rgb);
	}
#endif
	UgState ug = ugState(); // M5 underground (P6): amount 0 above ground
#if defined WORLD_OVERWORLD
	if (isEyeInWater == 0 && ug.amount < 1.0) {
		Surface s = surfaceAt(p, uv);
		vec3 eyePos = mat3(gbufferModelViewInverse) * s.view;
		// 2. haze behind the mist, the valley mist, haze in front. The split is 0 until M1 exposes the slab's entry
		// distance (MistRay.enter): then near mist banks stay crisp and far ones are hazed exactly.
		HzState st = hzState();
		vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
		vec3 air = sceneFromAuthored(skyHazeAir(st.kind, normalize(eyePos), sunW)); // (scene light)
		// ---- atmos (welkin-atmos): the haze's air is the luminous sky's horizon too, so the far land fades into the
		// same bright band the sky shows over it (below the horizon: the horizon's own light in that direction)
		SkyLum lum = skyLumFrame(sunW);
		{
			vec3 dA = normalize(eyePos);
			dA = dA.y < 0.0 ? normalize(vec3(dA.x, 0.0, dA.z) + vec3(1e-5, 0.0, 0.0)) : dA;
			air = skyLuminousLin(air, dA, lum, 0.8);
			// (Look D9) the night's moonlit air: the far land fades into the same cool, moon-side glow the sky shows
			air += skyNightGlow(dA, sunW, normalize(mat3(gbufferModelViewInverse) * moonPosition));
		}
		// (Look D6) over the land the air is layered, coloured air rather than the sky's band (lib/air.glsl airLayered)
		if (!s.sky) {
			air = airLayered(air, normalize(eyePos), sunW);
		}
		// ---- air and tone (D): the haze's forward scatter toward the sun (lib/air.glsl)
		vec3 airPlain = air;
		air = airMie(air, normalize(eyePos), sunW, st.kind);
		vec3 airPlainHv = airPlain; // (the haze volume and the dawn mist take their own cloud shadows)
#ifdef C2_AIR_SHADOW
		// (Look D9) the air over the land in a cloud's shadow: read where the in-scatter that reaches the eye mostly comes
		// from, over the far part of the ray (the land's own point twice), as a share of the sun (lib/clouds/shadow.glsl)
		if (!s.sky && !cloud && !finished && sunW.y > 0.05) {
			CloudEnv ce = wcEnvRead();
			float sh = 0.25 * (welkinCloudShadow(eyePos * 0.55, ce) + welkinCloudShadow(eyePos * 0.8, ce))
				+ 0.5 * welkinCloudShadow(eyePos, ce);
			float closed = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
			float u = (1.0 - clamp(sh, 0.0, 1.0)) * (1.0 - closed) * smoothstep(0.05, 0.25, sunW.y);
			vec3 tintA = AIR_CLOUD_TINT / dot(AIR_CLOUD_TINT, vec3(0.2126, 0.7152, 0.0722));
			vec3 dimA = (1.0 - AIR_CLOUD_DIM * u) * mix(vec3(1.0), tintA, u);
			air *= dimA;
			airPlain *= dimA;
		}
#endif
		// (HDR stage B: the forward scatter is in-scattered light, uncapped: toward a low sun it may pass white; look4's
		// golden-hour exposure and cap are gone)
		vec3 n = s.sky ? vec3(0.0, 1.0, 0.0) : hzScreenNormal(eyePos);
		WelkinHaze h;
		if (finished) {
			// P5: composite1 laid this pixel's whole air (the curtain's near and far pieces, the sky or cloud behind)
			h.air = air;
			h.rainLook = 0.0;
			h.dp = p;
			h.back = vec4(air, 0.0);
			h.front = vec4(air, 0.0);
		} else if (cloud) {
			// a cloud takes the same air as land at its distance (the clear air thins with height, so a deck overhead
			// keeps its colour and the far decks low over the horizon sink into the haze the land beyond them meets),
			// but not the world's far edge: the clouds stand past the land
			vec3 rdW = normalize(mat3(gbufferModelViewInverse) * viewFromDepth(uv, 1.0, gbufferProjectionInverse));
			h = welkinHazeEx(st, rdW * (cloudKm * 1000.0), vec3(0.0, 1.0, 0.0), false, 0.0, air, gl_FragCoord.xy, false);
		} else {
			h = welkinHaze(st, eyePos, n, s.sky, 0.0, air, gl_FragCoord.xy);
		}
		// (review round 2) from mid-afternoon into the golden hours the far haze takes a little warmth whatever its
		// kind's own cool tint (a misty land's white at 16:48 read cool beside both reference packs' warm cream):
		// cream in the afternoon, rose-peach on a clear morning, least once the hour is a full sunset (its glow has it)
		{
			// (golden, 2026-10-18: 0.75 and a deeper afternoon cream, was 0.5 and (1.06, 1.0, 0.88): the mesa vista's far air
			// at 16:48 still read a cool milky white beside the reference packs' warm hazy afternoon)
			float wA = 0.75 * smoothstep(0.1, 0.5, lum.warm) * (1.0 - 0.5 * smoothstep(0.8, 1.0, lum.warm)) * lum.clear * lum.up;
			// (look3, 2026-10-18) most toward the sun, a third of it away from it: the air away from an afternoon sun is a
			// cool blue-grey in layers (the mesa's far valleys had gone one khaki murk), the sun's side glows warm
			wA *= mix(0.3, 1.0, smoothstep(0.35, 0.95, clamp(dot(normalize(eyePos), sunW) * 0.5 + 0.5, 0.0, 1.0)));
			vec3 tw = sunAngle < 0.25 || sunAngle > 0.75 ? vec3(1.05, 0.97, 0.95) : vec3(1.09, 1.0, 0.80);
			tw /= dot(tw, vec3(0.2126, 0.7152, 0.0722));
			h.back.rgb = mix(h.back.rgb, h.back.rgb * tw, wA);
			h.front.rgb = mix(h.front.rgb, h.front.rgb * tw, wA);
		}
		// (look, welkin-look 2026-10-18) the air has body: by day the haze sits a little under the sky's horizon and
		// leans a soft blue-grey away from the sun, so the far ridges and woods stand in layers against the sky instead
		// of dissolving into one milky white; toward the sun it keeps its warm glow
		{
			float toSun = clamp(dot(normalize(eyePos), sunW) * 0.5 + 0.5, 0.0, 1.0);
			// (look2, 2026-10-18: bolder, a blue-grey a fifth under the horizon away from the sun and a warm glow toward
			// it; it eases off as the haze closes to the world's far edge, which must meet the sky's horizon. The vistas'
			// middle distance had been one milky white sheet: now a layered blue-grey air)
			// (Look D3: a deeper, earthier body, was (0.80, 0.85, 0.92) and (1.0, 0.94, 0.84): the mesa's far air still read
			// a pale milk; now a slate blue-grey away from the sun, an amber toward it, each a step under the horizon)
			vec3 body = mix(vec3(0.72, 0.78, 0.88), vec3(1.0, 0.91, 0.76), smoothstep(0.35, 1.0, toSun * toSun));
			float wB = lum.day * lum.up * (s.sky ? 0.0 : 1.0);
			float wBb = wB * (1.0 - smoothstep(0.8, 1.0, h.back.a));
			float wBf = wB * (1.0 - smoothstep(0.8, 1.0, h.front.a));
			h.back.rgb = mix(h.back.rgb, h.back.rgb * body, wBb);
			h.front.rgb = mix(h.front.rgb, h.front.rgb * body, wBf);
			// (look2) the country haze's murk (the mesa vista's milky sheet: HAZE_STRENGTH 0 takes it, the valley mist,
			// the far mist and the volume do not) a third thinner by day over the middle distance, so the land reads
			// through it in layers; the world's far edge still closes into the sky
			h.back.a *= 1.0 - 0.33 * wBb;
			h.front.a *= 1.0 - 0.33 * wBf;
		}
		// M5 underground (P6): the surface air hands over to the cave air at cave mouths
		h.back.a *= 1.0 - ug.amount;
		h.front.a *= 1.0 - ug.amount;
		// (HDR stage B: no special case over the sun's disc or the golden hour's sky: the disc's light is far above the
		// air's and passes through it by the haze's own transmittance)
		float sunDisc = 0.0;
		// (HDR stage B) a ray up into the sky, or to a cloud deck over the horizon, leaves the dense low air within a
		// short way: the haze over the open sky and the decks thins with the ray's elevation, at every hour (look4 did
		// this for the golden hours only, as an exposure)
		if ((s.sky || cloud) && !finished) {
			float thin = 0.85 * smoothstep(0.02, 0.15, normalize(eyePos).y) * lum.clear;
			h.back.a *= 1.0 - thin;
			h.front.a *= 1.0 - thin;
		}
		color.rgb = welkinHazeBack(color.rgb, h);
		vec3 ugBeforeMist = color.rgb;
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
			ma.ground = -1e4;
			color.rgb = valleyMistWith(p, uv, color.rgb, mt, ma);
		}
#endif
		// ---- atmos (welkin-atmos): the volumetric haze (lib/haze/volume.glsl), its light and shadow in the air
		vec3 hvRaw = vec3(0.0);
		hzHistDepth = s.sky ? 1e4 : -s.view.z;
#ifdef HV_READ
		if (!cloud && !finished) {
			float hvDist = pinholeWater(p) ? -1.0 : resolveDist(p, uv); // look3: the walk's own (opaque) distance; water: the tile's mean
#ifdef TEMPORAL_AIR
			// (HDR stage C) the last frame's, reprojected, where it lay on the same surface; clamped to the tile's range
			vec2 puv;
			float pz;
			bool settled = tReproject(eyePos, s.sky, puv, pz);
			vec4 hh = settled ? texelFetch(colortex11, ivec2(puv * vec2(viewWidth, viewHeight)), 0) : vec4(0.0);
			settled = settled && tDepthOk(hh.a, pz, s.sky);
			vec3 hvLo, hvHi;
			hvRaw = hvResolveRange(p, hvDist, settled, hvLo, hvHi);
			if (settled) {
				vec3 hist = tUnpackAir(hh.b);
				hvRaw = mix(hvRaw, clamp(hist, hvLo, hvHi), TEMPORAL_AIR_BLEND);
			}
#else
			hvRaw = hvResolve(p, hvDist);
#endif
			hzHist = hvRaw;
			float hvMist = 0.0;
#if defined WELKIN_VALLEY_MIST && defined DEBUG_PLAIN
			hvMist = clamp(mistCam.y * mistCam.z * 2.0, 0.0, 1.0);
#endif
			color.rgb = mix(hvComposite(color.rgb, hvRaw, st, normalize(eyePos), sunW, airPlainHv, s.sky, hvMist, lum),
				color.rgb, max(ug.amount, sunDisc));
		}
#endif
		// ---- end atmos
		// ---- look2: the morning mist (lib/haze/dawn.glsl), before the shafts, so the beams stream through it
		float dawnAmt = 0.0;
#if MORNING_MIST > 0
		dawnAmt = float(MORNING_MIST) * 0.1 * dawnMistHour() * (1.0 - ug.amount);
		// (Look D9) a clear night's ground mist: the same low layer lies in the hollows, the valleys and the low distance from
		// the deep night on, cool and catching the moonlight faintly (brighter on the moon's side), thinner than the dawn's
		float nightMistW = smoothstep(-0.10, -0.25, sunW.y) * lum.clear;
		dawnAmt = max(dawnAmt, NIGHT_MIST_AMT * nightMistW * (1.0 - ug.amount));
		if (dawnAmt > 0.0 && !cloud && !finished) {
			float camY = float(cameraPositionInt.y) + cameraPositionFract.y;
			float T = s.sky ? 700.0 : length(eyePos);
			float ground = s.sky ? min(camY - 2.0, HAZE_SEA_LEVEL + 8.0) : camY + eyePos.y;
			float weight = 0.5 * dawnMistLow(ground);
#if defined WELKIN_VALLEY_MIST && defined DEBUG_PLAIN
			if (mistCam.w > 0.5) {
				// Gloam's near tiles: the valleys, the hollows and the water (the valley mist's pool and potential, with
				// its soft billows), read at the ray's end (a sky ray: a few hundred blocks out)
				MistTiles mt;
				mt.data = true;
				mt.on = true;
				mt.time = 1.0;
				mt.camCell = mistCamCells.xy;
				mt.centre = mistCamCells.zw;
				MistPoint mp = mistAt(normalize(eyePos).xz * min(T, 600.0), mt);
				weight = max(weight, clamp(mp.density * 1.4, 0.0, 1.6));
				// the mist lies on the land's ground, not on whatever the ray ends on (a fall, a cliff's face, a crown)
				ground = mp.ground > -1e3 ? min(ground, mp.ground) : ground;
			}
#endif
			float cover = dawnMistCover(normalize(eyePos), T, camY, ground, dawnAmt, weight);
			// on the sky the bank sits low on the horizon
			cover *= s.sky ? 0.55 * (1.0 - smoothstep(-0.02, 0.035, normalize(eyePos).y)) : 1.0;
			if (cover > 0.0) {
				float fsunM = 1.0;
#ifdef HV_READ
				fsunM = clamp(hvRaw.g / max(hvRaw.b, 1e-4), 0.0, 1.0);
#endif
				vec3 dmc = dawnMistColour(airPlainHv, normalize(eyePos), sunW, fsunM, lum);
				if (nightMistW > 0.0) {
					vec3 moonW = normalize(mat3(gbufferModelViewInverse) * moonPosition);
					float toMoon = clamp(dot(normalize(eyePos), moonW) * 0.5 + 0.5, 0.0, 1.0);
					vec3 nmc = sceneFromAuthored(NIGHT_MIST_LEVEL * (skyAmbientLight() + 0.9 * skyDirectLight()) * vec3(0.92, 0.97, 1.06))
						* mix(0.8, 1.5, toMoon * toMoon * smoothstep(-0.05, 0.15, moonW.y))
						+ 0.5 * skyNightGlow(normalize(vec3(eyePos.x, 0.0, eyePos.z) + vec3(1e-5, 0.0, 0.0)), sunW, moonW);
					dmc = mix(dmc, nmc, nightMistW);
				}
				color.rgb = mix(color.rgb, dmc, airCoverA(cover, AIR_DAWN_DENS, AIR_DAWN_ONSET));
#ifdef DM_DEBUG
				color.rgb = vec3(cover, dawnAmt, fract(ground / 16.0));
#endif
			}
		}
#endif
		// ---- air and tone (D): the sun shafts (lib/shafts.glsl), in the near air, before the haze in front of it
#ifdef WELKIN_SHAFTS_READ
		if (shaftGate(sunW) > 0.0 && sunAngle < 0.5) {
#ifdef HV_READ
			float lit = hvRaw.r; // atmos: the haze's walk gives the shafts' term (resolved above)
#else
			float lit = shaftResolve(p, pinholeWater(p) ? -1.0 : resolveDist(p, uv));
#endif
			// atmos (shafts and bloom): thicker in hazy air and where the morning's valley mist lies, thinner in rain
			float shThick = mix(0.85, 1.5, clamp(st.haze.x, 0.0, 1.0)) * (1.0 - 0.5 * st.rain);
#if defined WELKIN_VALLEY_MIST && defined DEBUG_PLAIN
			shThick *= 1.0 + 0.8 * clamp(mistCam.y * mistCam.z * 2.0, 0.0, 1.0);
#endif
			shThick *= 1.0 + 0.9 * clamp(dawnAmt, 0.0, 1.5); // look2: thicker in the morning mist
			color.rgb += shaftLight(normalize(eyePos), sunW, st.kind, lit, s.sky, lum, shThick, s.sky ? 1e4 : length(eyePos)) * (cloud ? 0.0 : 1.0);
#if SHAFT_DEBUG == 1
			color.rgb = vec3(lit * 20.0); // (look4: x20) the resolved sunlit share of the near air
#elif SHAFT_DEBUG == 2
			color.rgb = vec3(texelFetch(colortex13, p >> 1, 0).r); // the raw term
#endif
		}
#endif
		color.rgb = mix(color.rgb, ugBeforeMist, ug.amount); // M5: the valley mist (and the shafts) hand over too
		color.rgb = welkinHazeFront(color.rgb, h);
		// ---- air and tone (D): the land's aerial perspective (lib/air.glsl)
		if (!s.sky && !cloud) {
			// (M5: it hands over to the cave air at cave mouths, as the haze does)
			color.rgb = mix(airPerspective(color.rgb, h.air, (air - airPlain) * (1.0 - h.back.a), length(eyePos),
				normalize(eyePos), sunW), color.rgb, ug.amount);
			// (look2, 2026-10-18) the air has body by day: a gentle blue-grey veil that grows over the first few hundred
			// blocks, most with the sun high (midday calm, every wood a step paler than the one in front), less through
			// the golden hours (their own warm air carries them), none at night or under a cave mouth
			float dB = length(eyePos);
			float wN = AIR_BODY * lum.day * lum.up * mix(1.0, 0.45, lum.warm) * (1.0 - ug.amount)
				* (1.0 - exp(-max(dB - 16.0, 0.0) / 220.0));
			float aY = dot(h.air, vec3(0.2126, 0.7152, 0.0722));
			// (a soft warm grey: the air's own light, most of its colour out, a breath of warmth)
			vec3 veil = mix(vec3(aY), h.air, 0.35) * vec3(1.03, 1.0, 0.95) * 0.80;
			color.rgb = mix(color.rgb, veil, airCoverA(wN, AIR_BODY_DENS, AIR_BODY_ONSET));
		}
#if HAZE_DEBUG > 0 && HAZE_DEBUG < 4
		welkinHazeDebug(st, h, eyePos, s.sky, color.rgb);
#endif
	}
#endif
	// ---- M5 underground (P6): the cave air under the land, the eye in water included; then its debug overlay
	// (HDR stage B) the cave air's colours are authored in display values: its transmittance and its own light are
	// read off it (it lays its layers over what is behind as mixes), and laid on in scene light
	if (ug.mode != UG_MODE_OFF) {
		vec3 u0 = ugComposite(ug, p, uv, vec3(0.0));
		vec3 u1 = ugComposite(ug, p, uv, vec3(1.0));
		vec3 T = clamp(u1 - u0, 0.0, 1.0);
		vec3 X = u0 / max(1.0 - T, 1e-3);
		color.rgb = color.rgb * T + sceneFromAuthored(X) * (1.0 - T);
	}
#if UG_DEBUG != 0
	color.rgb = ugDebug(ug, p, color.rgb);
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
	{
		// (HDR stage C) the history for the next frame: this frame's shadow terms (deferred3), the occlusion (colortex12,
		// the half-resolution resolve), the air's terms, the view depth
		vec2 shR = texelFetch(colortex11, ivec2(gl_FragCoord.xy), 0).rg;
		float aoV = texelFetch(colortex12, ivec2(gl_FragCoord.xy) >> 1, 0).x;
		outHist = vec4(tPack(shR), tPack(vec2(aoV, 0.0)), tPackAir(hzHist),
			hzHistDepth);
	}
	outColor = color;
}

#endif
