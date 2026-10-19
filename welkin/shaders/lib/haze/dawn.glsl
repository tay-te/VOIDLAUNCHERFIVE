// VOID Welkin, look2 (2026-10-18): the morning mist. A shallow, dense layer that lies on the low ground and the water
// from first light to about 08:30: the river valleys, the lake lands and the shores fill with it at dawn, and the low
// sun's beams (lib/shafts.glsl) stream through it.
//
// For composite2, per pixel, no marching and no noise: the mist's density falls off exponentially over DM_H blocks
// above the ground under the ray's end (the surface's own height, or the camera's ground for the sky) and over DM_LOW
// blocks of that ground's height over sea level, so it pools low and leaves the hills clear. The optical depth along
// the ray comes from the haze's exact exponential-layer integral (lib/haze/haze.glsl hzLayer2): a grazing ray across a
// misty river or lake gathers much of it, a steep look down at the ground very little, as real ground mist.
// Its colour is the dawn air: a pearly blue-grey away from the sun, a warm gold toward it, dimmer where the haze's walk
// found the air in shadow (a valley still in the hill's shade), lit where the sun reaches it.
// Requires lib/haze/haze.glsl and lib/sky/luminous.glsl, and sunAngle.

#ifndef WELKIN_DAWN_MIST
#define WELKIN_DAWN_MIST

#define DM_H 11.0
#define DM_LOW 30.0
// optical depth per block at the ground's foot on low ground, at MORNING_MIST 10
#define DM_DENS 0.10
// the first blocks from the eye stay clear
#define DM_NEAR 12.0
// the most the mist covers (the land reads through)
#define DM_MAX 0.78
// (Look D3) the first light's rose-gold in the mist: its hue (linear, luma 1) and share
#define DM_ROSE vec3(1.32, 0.90, 0.72)
#define DM_ROSE_MIX 0.4

// 1 from first light to about 06:45, (Look D4) under a third of it at 08:00, nothing by about 08:45 (sunAngle runs 0.012
// at 05:30, 0.055 at 06:30, 0.117 at 08:00): the mist burns off as the sun climbs, so the morning light models the land)
float dawnMistHour() {
	float t = sunAngle > 0.5 ? sunAngle - 1.0 : sunAngle;
	return smoothstep(-0.05, -0.015, t) * (1.0 - smoothstep(0.06, 0.15, t));
}

// The cover 0..1 along view direction dir (world, unit) to distance T, from the camera at height camY, over ground at
// height `ground`; amount the hour's mist (0..).
// (weight: how readily the place under the ray's end pools mist, 0..1: composite2 takes it from Gloam's near tiles, the
// valley mist's own pool weight and potential (lib/mist.glsl mistAt), else from the ground's height over the sea's)
float dawnMistLow(float ground) {
	return exp(-clamp(ground - HAZE_SEA_LEVEL, 0.0, 600.0) / DM_LOW);
}

float dawnMistCover(vec3 dir, float T, float camY, float ground, float amount, float weight) {
	float rho = DM_DENS * amount * weight;
	if (rho <= 1e-6 || T <= DM_NEAR) {
		return 0.0;
	}
	vec2 od = hzLayer2(rho, ground + 1.0, DM_H, DM_NEAR, T, T, dir, camY, 0.0);
	return DM_MAX * (1.0 - exp(-od.y));
}

// The mist's colour (scene light): `air` the plain luminous air in this direction, fsun the share of the air in the sun.
vec3 dawnMistColour(vec3 air, vec3 dir, vec3 sunW, float fsun, SkyLum f) {
	float y = dot(air, vec3(0.2126, 0.7152, 0.0722));
	// pearly and luminous: the air's hue, two fifths of its saturation out, clearly brighter than the air (mist scatters
	// the whole sky's light: it glows over the land instead of greying it)
	// (Look D1: more of the air's own hue and a breath of rose: a pink-gold pearl, not a milk white)
	vec3 c = mix(vec3(y), air, 0.8) * vec3(1.03, 0.98, 1.0) * 0.98;
	c = mix(c, sceneFromAuthored(vec3(0.80, 0.84, 0.90)), 0.25) * max(1.0, 0.30 / max(y, 1e-4));
	y = dot(c, vec3(0.2126, 0.7152, 0.0722));
	// toward the sun a warm gold glow
	float mu = clamp(dot(dir, sunW), 0.0, 1.0);
	vec3 gold = f.gold; // (linear, luma 1)
	c = mix(c, gold * y * 1.12, (0.25 + 0.6 * mu * mu) * f.up);
	// (Look D3) the dawn mist holds the first light's rose-gold all through it, from before the sun is up: misty gold and
	// rose, not a pearl grey (DM_ROSE, linear, luma 1)
	c = mix(c, DM_ROSE * y * 1.05, DM_ROSE_MIX * smoothstep(-0.22, -0.02, sunW.y));
	// in shadow a cool, dimmer grey-blue
	c = mix(c * vec3(0.80, 0.86, 0.96) * 0.78, c, clamp(fsun, 0.0, 1.0));
	return c;
}

#endif
