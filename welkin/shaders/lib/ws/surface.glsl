// VOID Welkin water: what the water module adds to deferred's lighting of the opaque world, around the light step's
// welkinSunReach and welkinLightCore (program/deferred.glsl calls these two):
//
//   WsDeferred d = wsDeferredSurface(albedo, eyePos, n, sky, material, emission, rainWet, sunSeen, footprint);
//       before the light: darkens `albedo` for the wet band at the water's edge and lays frost on it; returns the
//       season's tints for the direct light (sunTint, into sunShare) and the shade (ambientTint)
//   color += wsDeferredSparkle(d, eyePos, n, material, light);   after the light: glints on frost and snow
//
// The wet band: land within about 6 blocks of water (VOID Gloam's near tiles, N0: the water distance blended between
// the four nearest 16 m cells) and less than 1.4 blocks above its level (2.4 once the ground is wet; the lowest
// ground of the four cells stands for the water level, as Gloam writes the sea's surface there) darkens, smoothly, as
// wet ground does. It stacks with the rain's wet ground (lib/haze/wet.glsl) as the wetter of the two, never as both
// (INTEGRATION.md conflict C4). Without Gloam's tiles there is no band.
//
// Needs lib/light.glsl, lib/void.glsl, lib/sway.glsl and lib/ws/{config,inputs,season}.glsl first.

#ifndef WELKIN_WS_SURFACE
#define WELKIN_WS_SURFACE

struct WsDeferred {
	vec3 sunTint;     // the season's tint of the direct light
	vec3 ambientTint; // the season's tint of the shade
	float shore;      // 0..1 the wet band
	float frost;      // 0..1
	float footprint;  // blocks per screen pixel
	WsSeason sea;
};

// How much darker a material gets when wet (0 for what does not soak: plants, snow, glass, lava, lights).
float wsWetDark(int m, float emission) {
	if (emission > 0.0 || matHas(m, MATF_SSS) || matIsSnow(m) || matIsGlassLike(m) || matIsWater(m) || m == M_LAVA
			|| m == M_PORTAL || m == M_FIRE) {
		return 0.0;
	}
	return 0.16 + 0.07 * float(matPorous(m));
}

// The wet band at the water's edge, 0..1 (smooth). playerPos camera-relative.
float wsShoreWet(vec3 playerPos, float groundWet, int mode) {
#ifdef WS_SHORE_WET
	if (mode == VOID_MODE_NONE || length(playerPos) > 192.0 || !voidTileHasData(VOID_TILE_N0)) {
		return 0.0;
	}
	// cell coordinates without large floats: the camera's 16 m cell in ints, the rest local
	vec2 local = vec2(cameraPositionInt.xz & 15) + cameraPositionFract.xz + playerPos.xz;
	vec2 cf = local / 16.0 - 0.5;
	vec2 c0 = floor(cf);
	vec2 f = cf - c0;
	ivec2 baseCell = cameraPositionInt.xz >> 4;
	ivec2 cell = baseCell + ivec2(c0);
	ivec2 centre = voidCentre(VOID_TILE_N0, baseCell);
	float trust = voidCellWeight(cell, centre);
	if (trust <= 0.0) {
		return 0.0;
	}
	vec4 a = voidFetch(VOID_TILE_N0, cell);
	vec4 b = voidFetch(VOID_TILE_N0, cell + ivec2(1, 0));
	vec4 c = voidFetch(VOID_TILE_N0, cell + ivec2(0, 1));
	vec4 d = voidFetch(VOID_TILE_N0, cell + ivec2(1, 1));
	// A: the water distance in 4-block steps
	float waterDist = mix(mix(a.a, b.a, f.x), mix(c.a, d.a, f.x), f.y) * 255.0 * 4.0;
	float waterY = min(min(voidHeight(a.r, a.g), voidHeight(b.r, b.g)), min(voidHeight(c.r, c.g), voidHeight(d.r, d.g)));
	float y = float(cameraPositionInt.y) + cameraPositionFract.y + playerPos.y;
	float h = y - waterY;
	float reach = 1.4 + 1.0 * groundWet;
	float near = 1.0 - smoothstep(2.0, 6.5, waterDist);
	float low = (1.0 - smoothstep(0.3 * reach, reach, h)) * smoothstep(-0.8, -0.3, h);
	return near * low * trust;
#else
	return 0.0;
#endif
}

// Before the light. albedo: darkened and frosted in place. sky: the light map's sky light; rainWet: how wet the
// rain's wet ground already made this surface (0 without it); sunSeen: the sun's reach x facing here (for frost
// melting later in the shade); footprint: blocks per screen pixel.
WsDeferred wsDeferredSurface(inout vec3 albedo, vec3 playerPos, vec3 n, float sky, int m, float emission,
		float rainWet, float sunSeen, float footprint) {
	WsIn inp = wsInputs();
	WsDeferred d;
	d.sea = wsSeasonNow(inp);
	d.sunTint = wsSeasonSun(d.sea);
	d.ambientTint = wsSeasonAmbient(d.sea);
	d.footprint = footprint;
	d.shore = 0.0;
	d.frost = 0.0;

	float dark = wsWetDark(m, emission);
	if (dark > 0.0 && inp.mode != VOID_MODE_NONE) {
		d.shore = wsShoreWet(playerPos, inp.wet, inp.mode);
		// only what the band adds beyond the rain's wetness: the wetter of the two, never both
		float extra = max(d.shore - rainWet, 0.0);
		if (extra > 0.0) {
			float l = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
			albedo = max(mix(vec3(l), albedo, 1.0 + 0.2 * extra), 0.0) * (1.0 - dark * extra);
		}
	}

	if (emission <= 0.0 && !matIsWater(m)) {
		d.frost = wsFrostAmount(d.sea, sky, clamp(sunSeen, 0.0, 1.0), sunAngle);
		if (d.frost > 0.0) {
			ivec3 texel = cameraPositionInt * 16 + ivec3(floor((cameraPositionFract + playerPos - n * (0.5 / 16.0)) * 16.0));
			albedo = wsFrost(albedo, texel, n, d.frost, footprint);
		}
	}
	return d;
}

// After the light: glints on frost and snow. light: the direct light that reaches the point (colour, after the
// shadow map and the clouds).
vec3 wsDeferredSparkle(WsDeferred d, vec3 playerPos, vec3 n, int m, vec3 light) {
#ifdef WS_FROST
	if (d.frost <= 0.0 && !matIsSnow(m) && d.sea.cold <= 0.0) {
		return vec3(0.0);
	}
	ivec3 texel = cameraPositionInt * 16 + ivec3(floor((cameraPositionFract + playerPos - n * (0.5 / 16.0)) * 16.0));
	float worldY = float(cameraPositionInt.y) + cameraPositionFract.y + playerPos.y;
	return wsSparkle(texel, n, normalize(playerPos), lightDirWorld(), light, d.frost, matIsSnow(m) ? 1.0 : 0.0,
		worldY, d.sea, d.footprint);
#else
	return vec3(0.0);
#endif
}

// WS_DEBUG 3 and 4: the wet band and frost, or the season's factors.
vec3 wsDeferredDebug(WsDeferred d, vec3 c) {
#if WS_DEBUG_VIEW == 3
	return vec3(d.shore, d.shore * 0.5, d.frost);
#elif WS_DEBUG_VIEW == 4
	return vec3(d.sea.autumn, d.sea.cold, d.sea.winter);
#else
	return c;
#endif
}

#endif
