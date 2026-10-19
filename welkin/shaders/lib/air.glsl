#ifndef WELKIN_AIR
#define WELKIN_AIR

// VOID Welkin, workstream D (air and tone, 2026-10-13): the light in the air, for the atmosphere pass (composite2).
//
//   vec3 air = airMie(skyHazeAir(kind, dir, sunW), dir, sunW, kind);   the haze's colour with its forward scatter
//   c = airPerspective(c, air, mie, dist, dir, sunW);                  a land pixel's aerial perspective
//
// Forward scatter (Mie): the haze is mostly fine droplets and dust, which scatter the sun's light forward. Seen toward
// the sun the air glows warm and bright, most when the sun is low and its light crosses the most air; away from it the
// air keeps its plain colour. The lobe is two Henyey-Greenstein lobes (a narrow one round the sun and a broad one over
// its half of the sky), normalized to 1 straight at the sun, in the sun-side horizon's own colour, added to the colour
// the haze converges to. The haze lays it over the land and the low sky by its own opacity, so the far land toward the
// sun takes a warm veil, the horizon there becomes a bright band and the sky above, where the haze is thin, keeps its
// colour: one gradient over land and sky, no bloom, no seam.
//
// Aerial perspective: past the near land, the land's colour leans toward the air's hue at its own brightness (a
// sky-tinted desaturation, not a veil) and gives up a little of its contrast toward the air, more toward a low sun.
// It grows with distance over a few kilometres, so the middle distance stands in front of the far land in layers.
// Smooth, per pixel, no noise.

#include "/lib/sky/table.glsl"
#include "/lib/hdr.glsl"

// (Look D1) the aerial perspective's and the forward scatter's density and onset in linear light (lib/hdr.glsl airCover)
#define AIR_LIN_DENS 1.0
#define AIR_LIN_MIE_DENS 1.0
#define AIR_LIN_ONSET 0.3
// (Look D6) the air over the land (composite2): its hue away from the sun and toward it (linear), how much of the air's
// own hue it takes, and its level against the sky's band
#define AIR_LAYER_AWAY vec3(0.72, 0.94, 1.45)
#define AIR_LAYER_SUN vec3(1.18, 0.98, 0.72)
#define AIR_LAYER_HUE 0.9
#define AIR_LAYER_LEVEL 0.8

// One Henyey-Greenstein lobe of asymmetry g at mu = cos(angle to the sun), 1 at mu = 1.
float airHg(float mu, float g) {
	float a = 1.0 + g * g;
	return pow((a - 2.0 * g) / max(a - 2.0 * g * mu, 1e-4), 1.5);
}

// How much the sun's height feeds the forward scatter: a trace at noon, most from about 15 degrees down to the
// horizon, gone soon after sunset.
float airLowSun(vec3 sunW) {
	float h = sunW.y;
	return smoothstep(-0.07, 0.01, h) * mix(0.12, 1.0, 1.0 - smoothstep(0.08, 0.45, h));
}

// (Look D6) The air over the land as layered, coloured air: blue-grey away from the sun, warm toward it, a step under the
// sky band's own light, so far ridges stand in it one behind another instead of dissolving into a milky strip along the
// horizon. By day and through the golden hours, less under an overcast. composite1's far air and composite2's near air
// take the same, so they still meet without a ring at the seam.
vec3 airLayered(vec3 air, vec3 dir, vec3 sunW) {
	float overcast = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
	float k = smoothstep(-0.04, 0.08, sunW.y) * (1.0 - 0.88 * overcast) * (1.0 - skyNightStep());
	if (k <= 0.0) {
		return air;
	}
	vec2 dz = dir.xz, sz = sunW.xz;
	float az = dot(dz, dz) > 1e-8 && dot(sz, sz) > 1e-8 ? dot(normalize(dz), normalize(sz)) : 0.0;
	float side = 0.5 + 0.5 * az;
	vec3 hue = mix(AIR_LAYER_AWAY, AIR_LAYER_SUN, side * side * side);
	hue /= dot(hue, vec3(0.2126, 0.7152, 0.0722));
	float y = dot(air, vec3(0.2126, 0.7152, 0.0722));
	return mix(air, hue * y, AIR_LAYER_HUE * k) * mix(1.0, AIR_LAYER_LEVEL, k);
}

vec3 airMie(vec3 air, vec3 dir, vec3 sunW, int kind) {
#if AIR_MIE > 0
	float mu = dot(dir, sunW);
	float lobe = 0.55 * airHg(mu, 0.8) + 0.45 * airHg(mu, 0.45);
	float amount = float(AIR_MIE) * 0.01 * 0.5 * airLowSun(sunW) * (1.0 - 0.7 * skyNightStep());
	vec3 sunSide = sceneFromAuthored(skyHazeHorizon(kind, true)); // (HDR stage B: scene light)
	// (the sun-side horizon's hue at the air's own brightness or above: a warm light, never a darkening)
	// (HDR stage B: in scene light the sun-side horizon stands several times the air's own light; the forward scatter is
	// in the sun-side hue at the air's own level, so the in-scatter grows with the air rather than with the bright
	// horizon, and backlit decks and ridges toward a low sun keep their dark against it)
	vec3 glow = sunSide * (dot(air, vec3(0.2126, 0.7152, 0.0722)) / max(dot(sunSide, vec3(0.2126, 0.7152, 0.0722)), 1e-3));
	return air + glow * lobe * amount;
#else
	return air;
#endif
}

// `mie`: what airMie added to the air in this direction. The haze lays it on only by its own opacity, which is small
// over the near and middle land (the clear near air); the droplets that scatter the low sun forward fill the air from
// a hundred blocks or so out, so the middle land toward the sun takes the warm light over about a kilometre.
vec3 airPerspective(vec3 c, vec3 air, vec3 mie, float dist, vec3 dir, vec3 sunW) {
	c += mie * airCover(max(dist - 96.0, 0.0) / 1400.0, AIR_LIN_MIE_DENS, AIR_LIN_ONSET); // (Look D1: optical depth)
#if AERIAL_PERSPECTIVE > 0
	float w = float(AERIAL_PERSPECTIVE) * 0.01 * airCover(max(dist - 120.0, 0.0) / 1800.0, AIR_LIN_DENS, AIR_LIN_ONSET);
	w *= 1.0 - 0.6 * skyNightStep();
	if (w <= 0.0) {
		return c;
	}
	float toward = max(dot(dir, sunW), 0.0);
	float lowSide = airLowSun(sunW) * toward * toward;
	vec3 Y = vec3(0.2126, 0.7152, 0.0722);
	float lc = dot(c, Y), la = max(dot(air, Y), 1e-3);
	// the air's hue at the land's own brightness
	vec3 tinted = air * (lc / la);
	c = mix(c, tinted, 0.5 * w);
	// a little of the contrast toward the air, more toward a low sun (its warm veil)
	// (0.08, 0.3 toward a low sun: more lifted the vistas' far darks and read flatter, not deeper)
	return mix(c, air, w * mix(0.08, 0.3, lowSide));
#else
	return c;
#endif
}

#endif
