// VOID Welkin sky (M2): builds one texel of the sky table (layout in lib/sky/table.glsl). Only the prepare pass
// includes this. Each of the 2048 fragments works out the whole mood (a few dozen multiplies) and writes its texel;
// nothing here is per screen pixel.
//
// The mood follows Gloam's no-pack sky (gloam/sky/GloamSky.java) step for step, so the two agree: the day dome,
// the blue hour and golden hour tints, the moonlit navy, overcast and rain greys, the dusk palette round the sun,
// then the haze kind's horizon and the dark of a black night. Every weight is a flat step (3 or 4 levels).

#ifndef WELKIN_SKY_BUILD
#define WELKIN_SKY_BUILD

#include "/lib/common.glsl"
#include "/lib/sky/settings.glsl"
#include "/lib/sky/palette.glsl"
#include "/lib/sky/inputs.glsl"
#include "/lib/sky/table.glsl"
#include "/lib/sky/uniforms.glsl"

struct SkyMood {
	vec3 zen;        // zenith colour
	vec3 horBase;    // horizon colour before the haze kind
	vec3 hor;        // horizon colour with the camera's haze kind
	vec3 below;      // under the horizon
	float night, nightStep, darkStep, clear;
	float cloudStep, rainStep, stormStep, overcast;
	float tw;        // how much the glow round the sun is the dusk palette (0 a day halo .. 1 a sunset)
	float glow;      // sun-glow amount, 0..1.5: how many rings reach how far
	float spread;    // how far round the sun the glow reaches, in 1 - cos(angle)
	float moonGlow;  // the moon halo's amount, 0..1
	float stars;     // star amount, 0..1
	float sunVis;    // the sun's disc through the cloud, 0..1 in thirds
	float moonVis;
	float moonAlpha; // the moon is pale by day, solid at night
	vec3 sunLight;   // premultiplied direct light
	vec3 moonLight;
	vec3 ambient;
	float ambientFactor;
	float sunStrength, moonStrength;
	float sunLow;    // 0 sun high .. 1 on the horizon, in thirds
};

// The haze kind's horizon: the kind's colour at the brightness of the plain horizon, by the kind's weight. At night
// only the hue moves a little; the dark stays.
vec3 skyHazeTint(vec3 h, int kind, float strength, float nightStep) {
	int k = kind > 6 ? 1 : kind;
	float w = SKY_HAZE_WEIGHT[k] * skyStep(strength, 3.0) * (1.0 - 0.6 * nightStep);
	return mix(h, skyTone(SKY_HAZE_KIND[k], h, 1.0), w);
}

SkyMood skyMood(SkyInputs s, vec3 sunW, vec3 moonW) {
	SkyMood m;
	m.night = skyNight(s.u);
	m.nightStep = skyStep(m.night, 4.0);
	m.darkStep = skyStep(skyDark(s.u), 4.0);
	m.clear = skyClear(s.cover);
	// how grey the fronts' cloud makes the sky past what rain already greys (GloamSky.cloud)
	m.cloudStep = skyStep(skySmooth(0.3, 0.95, s.cloudCover) * (1.0 - s.rain), 3.0);
	m.rainStep = skyStep(s.rain, 3.0);
	m.stormStep = skyStep(s.storm * s.rain, 3.0);
	m.overcast = skyStep(skySmooth(0.45, 0.95, s.cover), 3.0);

	// ---- the dome ----
	float day = 1.0 - 0.97 * m.nightStep;
	vec3 z = SKY_DAY_ZENITH * day;
	vec3 h = SKY_DAY_HORIZON * day;
	// the blue hour: a deep clear blue overhead, the land cool
	z = mix(z, skyTone(SKY_DUSK_BLUE, z, 1.0), 0.55 * s.blue);
	h = mix(h, skyTone(SKY_DUSK_BLUE, h, 1.0), 0.30 * s.blue);
	// the golden hour: paler gold toward the low sun
	z = mix(z, skyTone(SKY_GOLDEN_HAZE, z, 1.05), 0.20 * s.golden);
	h = mix(h, skyTone(SKY_GOLDEN_HAZE, h, 1.0), 0.40 * s.golden);
	// the night: a moonlit navy under a clear moon, near black without one
	float moonlit = skyStep(m.night * s.moon * m.clear, 3.0);
	z = max(z, SKY_MOONLIT * moonlit);
	h = max(h, SKY_MOONLIT_HORIZON * moonlit);
	z = max(z, SKY_STARLIT * m.nightStep * m.clear);
	// the fronts' cloud: a clean cool blue-grey, not a muddy one
	z = mix(z, skyTone(SKY_OVERCAST, z, 0.8), 0.8 * m.cloudStep);
	h = mix(h, skyTone(SKY_OVERCAST_FOG, h, 0.9), 0.7 * m.cloudStep);
	// rain greys and darkens it, a thunderstorm more
	z = mix(z, skyTone(SKY_STORM_CLOUD, z, 0.8), 0.7 * m.rainStep);
	h = mix(h, skyTone(SKY_OVERCAST_FOG, h, 0.85), 0.6 * m.rainStep);
	z *= 1.0 - 0.45 * m.stormStep;
	h *= 1.0 - 0.35 * m.stormStep;
	// a black night darkens the horizon with the gameplay light (GloamSky.fog)
	h *= mix(1.0, 0.25 + 0.75 * clamp(s.light.z, 0.0, 1.0), m.darkStep);
	m.zen = z;
	m.horBase = h;
	m.hor = skyHazeTint(h, s.hazeKind, s.hazeStrength, m.nightStep);
	m.below = m.hor * 0.82;

	// ---- the glow round the sun, and the moon's halo ----
	m.tw = skyStep(skyTwilight(s.u), 3.0);
	float closed = skyStep(skySmooth(0.55, 0.95, s.cloudCover) * (1.0 - s.rain), 3.0);
	float sunset = (1.0 - 0.75 * closed) * min(1.25, s.vivid) * (1.0 - 0.8 * m.rainStep);
	float dayHalo = 0.45 * (1.0 - 0.8 * m.overcast) * (1.0 - m.nightStep);
	m.glow = mix(dayHalo, sunset, m.tw);
	m.spread = mix(0.14, 0.95, m.tw);
	m.moonGlow = skyStep(s.moon * m.clear * m.nightStep, 3.0);
	// stars: Gloam's count (fewer under a bright moon, none under cloud) from the blue hour on
	m.stars = clamp(s.light.y, 0.0, 1.0) * skyStep(skyStarTime(s.u), 3.0) * (1.0 - s.blind);

	// ---- sun and moon discs ----
	m.sunVis = skyStep(1.0 - skySmooth(0.55, 1.0, s.cover), 3.0) * (1.0 - s.blind);
	m.moonVis = m.sunVis;
	m.moonAlpha = mix(0.5, 1.0, m.nightStep);
	m.sunLow = skyStep(1.0 - skySmooth(0.0, 0.35, sunW.y), 3.0);

	// ---- the light ----
	// sun: Minecraft's warm white, gold then orange as it sinks; through cloud only a little gets through
	vec3 sunCol = mix(vec3(1.00, 0.95, 0.86), vec3(1.00, 0.70, 0.42), m.sunLow);
	m.sunStrength = skyStep(skySmooth(-0.02, 0.22, sunW.y), 4.0) * (1.0 - 0.85 * m.overcast) * (1.0 - 0.5 * m.stormStep);
	m.sunLight = sunCol * 0.55 * m.sunStrength;
	// moon: silver-blue under a clear sky, slate blue under cloud; as strong as Gloam's dark nights allow
	vec3 moonCol = m.clear > 0.5 ? SKY_MOONLIGHT : SKY_CLOUDED_MOONLIGHT;
	m.moonStrength = m.nightStep * clamp(s.light.z, 0.0, 1.0) * skyStep(skySmooth(-0.02, 0.2, moonW.y), 2.0) * (1.0 - 0.7 * m.overcast);
	m.moonLight = moonCol * 0.11 * m.moonStrength;
	// the sky's ambient: vanilla's day/night light curve, times Gloam's gameplay scale (void_light.z), a little
	// less under a heavy overcast by day (SKY_LIGHT_FACTOR's layer) and in rain
	m.ambientFactor = mix(1.0, NIGHT_SKYLIGHT, m.nightStep) * clamp(s.light.z, 0.0, 1.0)
		* (1.0 - 0.15 * m.cloudStep * (1.0 - m.night)) * (1.0 - 0.28 * m.rainStep);
	vec3 dayAmb = mix(vec3(0.66, 0.72, 0.82), vec3(0.70, 0.64, 0.62), 0.6 * s.golden);
	dayAmb = mix(dayAmb, vec3(0.70, 0.72, 0.76), 0.7 * max(m.cloudStep, m.rainStep));
	vec3 nightAmb = m.clear > 0.5 ? SKY_MOONLIGHT : SKY_CLOUDED_MOONLIGHT;
	m.ambient = mix(dayAmb, nightAmb, m.nightStep) * m.ambientFactor * (1.0 - s.blind);
	return m;
}

// One band of the plain dome (no glow).
vec3 skyBand(SkyMood m, int e) {
	return e == 0 ? m.below : mix(m.hor, m.zen, SKY_BAND_MIX[e]);
}

// The glow colour for band e: a pale halo by day, the hour's dusk step at dawn and dusk, cooler steps higher up.
vec3 skyGlowColour(SkyMood m, SkyInputs s, int e) {
	vec3 band = skyBand(m, e);
	vec3 halo = skyTone(SKY_DAY_HALO, band, 1.18);
	// while the sun is up the whole glow is gold; once it is down the bands above run cooler (stripes)
	int shift = s.sunsetIndex == 0 ? 0 : SKY_GLOW_SHIFT[e];
	vec3 dusk = SKY_SUNSET[clamp(s.sunsetIndex + shift, 0, 4)];
	// under the fronts' cloud and in the dark the dusk colours dim with the sky they sit in
	dusk *= mix(1.0, max(skyLuma(band) * 2.2, 0.35), max(m.cloudStep, m.darkStep));
	return mix(halo, dusk, m.tw);
}

// Flash and blindness, over everything that is sky.
vec3 skyPost(vec3 c, SkyInputs s) {
	c = mix(c, SKY_FLASH, 0.5 * s.flash);
	return c * (1.0 - s.blind);
}

vec3 skyDome(SkyMood m, SkyInputs s, int ring, int e) {
	vec3 c = mix(skyBand(m, e), skyGlowColour(m, s, e), SKY_RING_MIX[ring]);
	return skyPost(c, s);
}

vec3 skyMoonDome(SkyMood m, SkyInputs s, int ring, int e) {
	vec3 band = skyBand(m, e);
	vec3 halo = SKY_MOONLIGHT * 0.16 * (0.4 + 0.6 * s.moon);
	return skyPost(mix(band, max(band, halo), SKY_MOON_RING_MIX[ring]), s);
}

vec3 skyCloudFace(SkyMood m, SkyInputs s, int face) {
	// a white cloud, slate under a heavy front (GloamSky.clouds), lit by whichever light is up
	vec3 c = vec3(0.98, 0.98, 1.0);
	c = mix(c, skyTone(SKY_STORM_CLOUD, c, mix(1.0, 0.7, m.cloudStep)), 0.75 * max(m.cloudStep, m.rainStep));
	c *= 1.0 - 0.35 * m.stormStep;
	float bright = mix(1.0, 0.16, m.nightStep) * mix(1.0, 0.3 + 0.7 * clamp(s.light.z, 0.0, 1.0), m.darkStep);
	vec3 nightTone = skyTone(SKY_CLOUDED_MOONLIGHT, c, 1.0);
	c = mix(c, nightTone, m.nightStep) * bright;
	vec3 duskSide = SKY_SUNSET[clamp(s.sunsetIndex, 0, 4)] * max(skyLuma(c), 0.2) * 1.4;
	vec3 r;
	if (face == SKY_CLOUD_TOP) {
		r = c;
	} else if (face == SKY_CLOUD_SUNSIDE) {
		r = mix(c * 0.92, duskSide, 0.45 * m.tw * (1.0 - m.overcast));
	} else if (face == SKY_CLOUD_SHADE) {
		r = mix(c * 0.78, skyTone(m.zen, c * 0.78, 1.0), 0.25);
	} else if (face == SKY_CLOUD_BASE) {
		r = c * 0.66;
	} else if (face == SKY_CLOUD_STORM) {
		r = skyTone(SKY_STORM_CLOUD, c, 0.55);
	} else {
		r = mix(c, duskSide, 0.6 * m.tw);
	}
	return skyPost(clamp(r, 0.0, 1.0), s);
}

vec3 skySprite(SkyMood m, SkyInputs s, int x) {
	float lo = m.sunLow;
	// the sun: a white-gold core, a gold middle, an orange rim; all warmer as it sinks
	if (x == 0) return mix(vec3(1.00, 0.99, 0.92), vec3(1.00, 0.90, 0.62), lo);
	if (x == 1) return mix(vec3(1.00, 0.94, 0.72), SKY_GOLD, lo);
	if (x == 2) return mix(vec3(1.00, 0.84, 0.50), SKY_ORANGE, lo);
	if (x == 3) return mix(vec3(1.00, 0.95, 0.80), mix(SKY_GOLD, SKY_ORANGE, 0.5), lo);
	// the moon: pale grey-white, darker maria, a slate terminator, the dark side all but black
	if (x == 8) return vec3(0.88, 0.90, 0.94);
	if (x == 9) return vec3(0.68, 0.71, 0.78);
	if (x == 10) return vec3(0.52, 0.55, 0.64);
	if (x == 11) return max(m.zen * 0.6, vec3(0.030, 0.038, 0.060));
	// stars: three steps of white
	if (x == 16) return vec3(0.42, 0.45, 0.55);
	if (x == 17) return vec3(0.70, 0.72, 0.80);
	if (x == 18) return vec3(0.98, 0.98, 1.00);
	return vec3(0.0);
}

// The texel at t: what prepare writes.
vec4 skyBuildTexel(ivec2 t) {
	if (t.y == SKY_ROW_CHECK) {
		return t.x == 0 ? vec4(87.0, 75.0, 1.0, 255.0) / 255.0 : vec4(0.0);
	}
#if defined WORLD_NETHER || defined WORLD_END
	// no sky here: a flat table of the dimension's fog colour, so any reader still gets something sane
	if (t.y <= SKY_ROW_HAZE_SUN || (t.y >= 10 && t.y <= 12)) {
		return vec4(fogColor, 1.0);
	}
	return vec4(0.0);
#else
	SkyInputs s = skyInputs();
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec3 moonW = normalize(mat3(gbufferModelViewInverse) * moonPosition);
	SkyMood m = skyMood(s, sunW, moonW);
	int e = min(t.x, 5);

	if (t.y <= 4) {
		return vec4(skyDome(m, s, t.y, e), 1.0);
	}
	if (t.y >= 10 && t.y <= 12) {
		return vec4(skyMoonDome(m, s, t.y - SKY_ROW_MOON_DOME, e), 1.0);
	}
	if (t.y == SKY_ROW_LIGHT) {
		bool day = sunAngle < 0.5; // Iris's shadowLightPosition switches here
		if (t.x == SKY_X_SUN) return vec4(m.sunLight, m.sunStrength);
		if (t.x == SKY_X_MOON) return vec4(m.moonLight, m.moonStrength);
		if (t.x == SKY_X_AMBIENT) return vec4(min(m.ambient + SKY_FLASH * 0.45 * s.flash, 1.0), m.ambientFactor);
		if (t.x == SKY_X_SHADOW) return day ? vec4(m.sunLight, m.sunStrength) : vec4(m.moonLight, m.moonStrength);
		if (t.x == SKY_X_HOR_AWAY) return vec4(skyDome(m, s, 0, 1), 1.0);
		if (t.x == SKY_X_HOR_SUN) return vec4(skyDome(m, s, int(clamp(floor(m.glow * 2.0 + 0.5), 0.0, 4.0)), 1), 1.0);
		if (t.x == SKY_X_ZENITH) return vec4(skyDome(m, s, 0, 5), 1.0);
		if (t.x == SKY_X_FLASH) return vec4(SKY_FLASH * 0.45 * s.flash, s.flash);
		if (t.x == SKY_X_STATE_LIGHT) return vec4(clamp(s.light.z, 0.0, 1.0), m.nightStep, m.darkStep, m.overcast);
		if (t.x == SKY_X_STATE_GLOW) return vec4(m.glow / 1.5, m.moonGlow, m.stars, m.spread / 2.0);
		if (t.x == SKY_X_STATE_SHOW) return vec4(m.sunVis, m.moonVis, m.moonAlpha, s.flash);
		if (t.x == SKY_X_STATE_TIME) return vec4(s.golden, s.blue, float(s.sunsetIndex) / 4.0, s.moon);
		return vec4(0.0);
	}
	if (t.y == SKY_ROW_HAZE_AWAY || t.y == SKY_ROW_HAZE_SUN) {
		if (t.x > 15) {
			return vec4(0.0);
		}
		vec3 h = skyHazeTint(m.horBase, t.x, s.hazeStrength, m.nightStep);
		if (t.y == SKY_ROW_HAZE_SUN) {
			int ring = int(clamp(floor(m.glow * 2.0 + 0.5), 0.0, 4.0));
			h = mix(h, skyGlowColour(m, s, 1), SKY_RING_MIX[ring]);
		}
		return vec4(skyPost(h, s), 1.0);
	}
	if (t.y == SKY_ROW_CLOUD) {
		return t.x <= SKY_CLOUD_RIM ? vec4(skyCloudFace(m, s, t.x), 1.0) : vec4(0.0);
	}
	if (t.y == SKY_ROW_SPRITE) {
		return vec4(skySprite(m, s, t.x), 1.0);
	}
	return vec4(0.0);
#endif
}

#endif
