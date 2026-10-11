// VOID Welkin sky (M2): the sky's palette and the clock. No uniforms, so any program may include it.
//
// The named colours are Gloam's (gloam/sky/Palette.java) so the pack and Gloam's no-pack sky agree. The clock maths
// mirrors gloam/sky/Twilight.java and Night.java exactly (same tick thresholds, same smoothstep), so the pack's
// fallbacks give the very numbers Gloam would send through void_sky and void_light.

#ifndef WELKIN_SKY_PALETTE
#define WELKIN_SKY_PALETTE

#define SKY_PI 3.14159265

// the band round the low sun, gold while it is up to dusk blue as the afterglow goes (Twilight.STEPS)
const vec3 SKY_GOLD      = vec3(1.00, 0.78, 0.36);
const vec3 SKY_ORANGE    = vec3(1.00, 0.55, 0.22);
const vec3 SKY_ROSE      = vec3(0.92, 0.42, 0.44);
const vec3 SKY_VIOLET    = vec3(0.54, 0.38, 0.60);
const vec3 SKY_DUSK_BLUE = vec3(0.28, 0.34, 0.64);
const vec3 SKY_SUNSET[5] = vec3[5](SKY_GOLD, SKY_ORANGE, SKY_ROSE, SKY_VIOLET, SKY_DUSK_BLUE);

// the night
const vec3 SKY_MOONLIGHT         = vec3(0.60, 0.70, 1.00);
const vec3 SKY_CLOUDED_MOONLIGHT = vec3(0.52, 0.60, 0.86);
const vec3 SKY_MOONLIT           = vec3(0.020, 0.035, 0.080); // a clear sky under a full moon: navy, not black
const vec3 SKY_MOONLIT_HORIZON   = vec3(0.045, 0.062, 0.115);
const vec3 SKY_STARLIT           = vec3(0.006, 0.009, 0.020); // a clear moonless night

// tints, scaled to the brightness of what they colour (skyTone)
const vec3 SKY_OVERCAST     = vec3(0.66, 0.71, 0.80);
const vec3 SKY_OVERCAST_FOG = vec3(0.74, 0.78, 0.84);
const vec3 SKY_STORM_CLOUD  = vec3(0.52, 0.56, 0.66);
const vec3 SKY_MIST         = vec3(0.86, 0.90, 0.94);
const vec3 SKY_DUST         = vec3(0.90, 0.78, 0.60);
const vec3 SKY_GOLDEN_HAZE  = vec3(1.00, 0.84, 0.58);

// the day dome: Minecraft's clear blue overhead, a pale blue at the horizon
const vec3 SKY_DAY_ZENITH  = vec3(0.40, 0.58, 0.96);
const vec3 SKY_DAY_HORIZON = vec3(0.72, 0.83, 0.98);
// the pale halo round a high sun
const vec3 SKY_DAY_HALO    = vec3(1.00, 0.97, 0.88);
// a lightning flash: a cold violet white
const vec3 SKY_FLASH       = vec3(0.78, 0.80, 0.98);

// The horizon colour of each haze kind (design table 2.4) and how strongly it tints a clear horizon. 0 is "none":
// the plain sky. Kinds 7..15 are reserved and read as temperate.
const vec3 SKY_HAZE_KIND[7] = vec3[7](
	vec3(0.72, 0.83, 0.98), // 0 none
	vec3(0.80, 0.85, 0.92), // 1 temperate: neutral
	vec3(0.76, 0.80, 0.86), // 2 humid_lowland: pale blue-grey
	vec3(0.86, 0.90, 0.94), // 3 misty_lakes: cool white
	vec3(0.72, 0.79, 0.73), // 4 forest_damp: green-grey
	vec3(0.90, 0.78, 0.60), // 5 dusty: warm ochre
	vec3(0.64, 0.77, 0.98)  // 6 clear_alpine: thin and blue
);
const float SKY_HAZE_WEIGHT[7] = float[7](0.0, 0.15, 0.35, 0.40, 0.35, 0.45, 0.25);

// The dome's bands: 0 below the horizon, 1 the horizon .. 5 the zenith; how far each sits from the horizon colour
// toward the zenith colour.
const float SKY_BAND_MIX[6] = float[6](0.0, 0.0, 0.30, 0.55, 0.78, 1.0);
// Glow rings round the sun, 0 none .. 4 nearest: how much of the glow colour each takes.
const float SKY_RING_MIX[5] = float[5](0.0, 0.22, 0.42, 0.62, 0.80);
// Halo rings round the moon, 0 none .. 3 nearest.
const float SKY_MOON_RING_MIX[4] = float[4](0.0, 0.10, 0.20, 0.32);
// Which sunset step each band's glow takes, past the hour's own: higher bands run cooler, so a sunset reads as
// stripes of orange, rose and violet.
const int SKY_GLOW_SHIFT[6] = int[6](0, 0, 0, 1, 2, 2);

// vanilla's DimensionType.MOON_BRIGHTNESS_PER_PHASE (phase 0 full .. 4 new)
const float SKY_MOON_PHASE_LIGHT[8] = float[8](1.0, 0.75, 0.5, 0.25, 0.0, 0.25, 0.5, 0.75);

// Gloam's Noise.smoothstep: works for reversed edges too (GLSL's smoothstep does not promise that).
float skySmooth(float e0, float e1, float x) {
	float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
	return t * t * (3.0 - 2.0 * t);
}

// Gloam's Palette.step: x in n flat steps, the nearest one.
float skyStep(float x, float n) {
	return floor(clamp(x, 0.0, 1.0) * n + 0.5) / n;
}

float skyLuma(vec3 c) {
	return dot(c, vec3(0.3, 0.59, 0.11));
}

// Palette.tone: the palette colour `tint` at the brightness of `like` (times b).
vec3 skyTone(vec3 tint, vec3 like, float b) {
	return clamp(tint * (skyLuma(like) * b / max(1e-4, skyLuma(tint))), 0.0, 1.0);
}

// ---------------------------------------------------------------------------------------------- the clock (Twilight)

// The time of day folded onto the evening: dusk times stay, a dawn time becomes the dusk time with the sun as high.
float skyDuskTime(float clock) {
	float t = mod(clock, 24000.0);
	if (t >= 9000.0 && t < 18000.0) {
		return t;
	}
	return t >= 18000.0 ? 36000.0 - t : 12000.0 - t;
}

// 0 day .. 1 night, on vanilla's own light ramps
float skyNight(float u) {
	return skySmooth(11270.0, 13140.0, u);
}

// how far the day's last light has gone: later than skyNight, after the blue hour
float skyDark(float u) {
	return skySmooth(12700.0, 13700.0, u);
}

float skyGolden(float u) {
	return skySmooth(10000.0, 11800.0, u) * (1.0 - skySmooth(12500.0, 13050.0, u));
}

float skyBlue(float u) {
	return skySmooth(12550.0, 13150.0, u) * (1.0 - skySmooth(13500.0, 14300.0, u));
}

// The sunset palette step for the hour: 0 gold (sun up) .. 4 dusk blue (Twilight.FROM).
int skySunsetIndex(float u) {
	return u >= 14000.0 ? 4 : (u >= 13500.0 ? 3 : (u >= 12700.0 ? 2 : (u >= 12000.0 ? 1 : 0)));
}

// How much of the sky glows round the sun for dawn and dusk: from late afternoon to the end of the afterglow.
float skyTwilight(float u) {
	return skySmooth(10500.0, 12000.0, u) * (1.0 - skySmooth(13800.0, 14400.0, u));
}

// When stars show: from the blue hour on.
float skyStarTime(float u) {
	return skySmooth(12400.0, 13600.0, u);
}

// ------------------------------------------------------------------------------------------------- the night (Night)

// how much open sky there is between the clouds, 1..0
float skyClear(float cloud) {
	return 1.0 - skySmooth(0.25, 1.0, cloud);
}

// Night.light: the night's light as a share of a clear full moon's, 0.03..1
float skyNightLight(float moon, float cloud) {
	float clear = skyClear(cloud);
	float moonlight = moon * (0.12 + 0.88 * clear);
	return min(1.0, 0.03 + 0.16 * clear + (1.0 - 0.03 - 0.16) * moonlight);
}

// Night.stars: how bright the stars may shine, 0..1.5
float skyNightStars(float moon, float cloud) {
	float clear = skyClear(cloud);
	return clear * clear * (1.5 - 0.5 * moon);
}

#endif
