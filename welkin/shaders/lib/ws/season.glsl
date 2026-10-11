// VOID Welkin water: the seasonal touches. Blocks already arrive tinted by Gloam (grass, leaves, bare crowns), so the
// pack only adds a lower, paler winter sun with cooler shade and darker water, autumn warmth in the light, and frost on
// cold mornings with a few glints on frost and snow. All smooth: a season changes over days, and every factor follows
// Gloam's values continuously. Without Gloam's uniforms there are no seasons (all factors 0).
//
// Needs lib/ws/inputs.glsl first.

#ifndef WELKIN_WS_SEASON
#define WELKIN_WS_SEASON

struct WsSeason {
	float winter; // 0..1
	float autumn; // 0..1
	float cold;   // 0..1, frost-forming cold here (the season's, sharpened by the local temperature)
	float snowY;  // the snowline (world y); very high when unknown
};

WsSeason wsSeasonNow(WsIn i) {
	WsSeason s;
	s.winter = 0.0;
	s.autumn = 0.0;
	s.cold = 0.0;
	s.snowY = 1.0e5;
	if (i.live) {
		s.winter = smoothstep(0.0, 1.0, clamp(-i.warmth, 0.0, 1.0));
		s.autumn = smoothstep(0.0, 1.0, clamp(i.season.y, 0.0, 1.0));
		// hot places keep no frost even in a cold season; cold places get it earlier
		float local = clamp(1.25 - 1.5 * i.temp, 0.0, 1.0);
		s.cold = clamp(i.season.z * local + max(-i.temp - 0.4, 0.0), 0.0, 1.0);
		s.snowY = i.season.w;
	}
	return s;
}

// The direct light's tint: winter's low sun paler, more golden and weaker; autumn's warmer.
vec3 wsSeasonSun(WsSeason s) {
	vec3 t = vec3(1.0);
#ifdef WS_SEASON_LIGHT
	t *= mix(vec3(1.0), vec3(1.0, 0.90, 0.78) * 0.84, s.winter);
	t *= mix(vec3(1.0), vec3(1.05, 0.96, 0.84), s.autumn * 0.6);
#endif
	return t;
}

// The shade's (ambient) tint: winter a little cooler, autumn a little warmer.
vec3 wsSeasonAmbient(WsSeason s) {
	vec3 t = vec3(1.0);
#ifdef WS_SEASON_LIGHT
	t *= mix(vec3(1.0), vec3(0.94, 0.97, 1.05), s.winter);
	t *= mix(vec3(1.0), vec3(1.03, 0.99, 0.93), s.autumn * 0.6);
#endif
	return t;
}

// The water body's tint: winter water darker and colder.
vec3 wsSeasonWater(WsSeason s) {
#ifdef WS_SEASON_LIGHT
	return mix(vec3(1.0), vec3(0.84, 0.90, 0.98), s.winter);
#else
	return vec3(1.0);
#endif
}

// How much frost lies here now, 0..1: it forms through the night, holds at dawn and melts by mid-morning (later where
// the sun does not reach); very cold days keep half of it all day. Only where the sky is open. angle: Iris's sunAngle
// (0 sunrise, 0.25 noon, 0.5 sunset); sun: how much of the direct light reaches the point (0..1).
float wsFrostAmount(WsSeason s, float skyLight, float sun, float angle) {
#ifdef WS_FROST
	if (s.cold <= 0.0) {
		return 0.0;
	}
	float melt = mix(0.07, 0.0, sun); // shade keeps it about an hour and a half longer
	float night = angle > 0.5 ? 1.0 : 1.0 - smoothstep(0.03 + melt, 0.10 + melt, angle);
	night = max(night, 0.5 * smoothstep(0.7, 0.9, s.cold));
	float open = smoothstep(0.7, 0.9, skyLight);
	return clamp(s.cold * night * open, 0.0, 1.0);
#else
	return 0.0;
#endif
}

// Frost on a surface: it settles texel by texel on the block's own 16-pixel grid as `amount` grows (like a frost
// texture coming in, never a dither), in two pale tones; where a texel is smaller than a screen pixel it is the
// smooth average instead. texel: the surface's world texel; footprint: blocks per screen pixel.
vec3 wsFrost(vec3 albedo, ivec3 texel, vec3 n, float amount, float footprint) {
#ifdef WS_FROST
	if (amount <= 0.0) {
		return albedo;
	}
	float a = n.y > 0.5 ? amount : amount * 0.4; // tops frost more than sides
	vec3 rime = vec3(0.84, 0.89, 0.95) * max(dot(albedo, vec3(0.2126, 0.7152, 0.0722)) * 0.35 + 0.72, 0.0);
	float h = hash01(texel);
	float tone = hash01(texel + ivec3(0, 4099, 0));
	vec3 crisp = h < a * 0.85 ? mix(albedo, rime * mix(0.9, 1.04, tone), 0.55 + 0.4 * smoothstep(0.0, 1.0, a)) : albedo;
	vec3 smoothed = mix(albedo, rime, a * 0.6);
	return mix(crisp, smoothed, smoothstep(0.5 / 16.0, 2.0 / 16.0, footprint));
#else
	return albedo;
#endif
}

// A few glints on frost and snow in the direct light: each texel has its own tiny facet, and the few whose facet
// mirrors the light into the eye shine. Fixed in the world (no flicker); they move as the view moves, as real snow
// glitters. Returns the light to add. V: camera to point (unit), L: toward the light, light: the direct light that
// reaches the point. snow: 1 on snow blocks. footprint: blocks per screen pixel (none once a texel is under a pixel).
vec3 wsSparkle(ivec3 texel, vec3 n, vec3 V, vec3 L, vec3 light, float frost, float snow, float worldY, WsSeason s,
		float footprint) {
#ifdef WS_FROST
	float snowy = snow > 0.5 ? max(s.cold, 0.4) : ((worldY > s.snowY && n.y > 0.5) ? s.cold : 0.0);
	float base = max(frost, snowy);
	float near = 1.0 - smoothstep(1.0 / 16.0, 2.0 / 16.0, footprint);
	if (base <= 0.0 || near <= 0.0 || n.y < 0.3) {
		return vec3(0.0);
	}
	vec3 h = vec3(hash01(texel + ivec3(17, 0, 0)), hash01(texel + ivec3(0, 0, 31)), hash01(texel + ivec3(0, 53, 0)));
	if (h.z > base * 0.05) {
		return vec3(0.0);
	}
	vec3 f = normalize(n + vec3(h.x - 0.5, 0.0, h.y - 0.5) * 0.9);
	float c = dot(reflect(-L, f), -V);
	return light * (1.6 * smoothstep(0.985, 0.995, c) * near);
#else
	return vec3(0.0);
#endif
}

#endif
