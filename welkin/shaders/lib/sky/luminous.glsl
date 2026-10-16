// VOID Welkin, atmos (welkin-atmos, 2026-10-16): the luminous sky. Built from the depth prototype SKY (welkin-depth
// c1383920..18ccd89a), for real. The sky table's dome (lib/sky/dome.glsl) is a smooth gradient of the hour's colours;
// this lays the light of a real sky over it, per pixel, in linear light, with no texture and no noise:
//
//   zenith   the top of the dome deepens and gains a little saturation by day: a clear, deep blue overhead
//   band     a broad, soft, luminous band along the horizon (no edge): the dome's own hue a little paler and brighter,
//            cool and clear away from the sun, warming to amber on the sun's side as the sun gets low
//   glow     a wide glow round the sun over its half of the dome (two Henyey-Greenstein lobes, a tight core and a broad
//            one), strongest while the sun is low and its light crosses the most air. The glow is MIXED toward the
//            sun's own hue at a brighter level, never added: an amber light added to a pale blue band sums to white
//            (the prototypes' white sun-side horizon on the steppe), mixed toward it the band turns amber-gold
//   cap      a soft, hue-keeping shoulder: a colour running past white is scaled down as a whole, so the glow near the
//            sun stays amber instead of clipping to white channel by channel
//
// All of it fades under an overcast and in rain (the sky table's overcast), and away at night (the moon's halo is the
// table's). The same light goes onto the haze's air (composite2), so the far land fades into the band the sky shows
// over it, and onto the clouds' far fog (lib/clouds/shade.glsl). Less gold than Complementary: the glow's hue is the
// sun's light a fifth of the way back toward white, and it only reaches the sun's side of the sky.
// Written from scratch for Welkin; no other pack's code.
//
//   SkyLum f = skyLumFrame(sunW);             once per pixel (a handful of table texels)
//   col = skyLuminous(col, d, f, amount);     display colour in, display colour out; amount 0..1
//   col = skyLumCap(col);                     only the shoulder (display)

#ifndef WELKIN_SKY_LUMINOUS
#define WELKIN_SKY_LUMINOUS

#include "/lib/settings.glsl"
#include "/lib/sky/table.glsl"

#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif

struct SkyLum {
	vec3 sunW;
	vec3 hue;    // the sun's glow hue, linear, luma 1
	float day;   // 1 by day, 0 at night
	float low;   // 0 sun high .. 1 sun on the horizon
	float warm;  // the golden afternoon and morning: 0 sun high .. 1 from about 6 degrees down to the horizon
	vec3 gold;   // the glow's hue (linear, luma 1): the sun's own, led toward amber through the golden hours
	float up;    // the sun's glow is there: from just under the horizon up
	float clear; // 1 a clear sky .. about 0.1 under a full overcast
};

float skyLumLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// A Henyey-Greenstein lobe of asymmetry g at mu = cos(angle to the sun), 1 straight at the sun.
float skyLumHg(float mu, float g) {
	float a = 1.0 + g * g;
	return pow((a - 2.0 * g) / max(a - 2.0 * g * mu, 1e-4), 1.5);
}

SkyLum skyLumFrame(vec3 sunW) {
	SkyLum f;
	f.sunW = sunW;
	f.day = 1.0 - skyNightStep();
	float overcast = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
	f.clear = (1.0 - 0.88 * overcast) * (1.0 - 0.5 * clamp(rainStrength, 0.0, 1.0));
	// the sky table's golden hour (Gloam's Twilight maths, 0..1; about 0.67 at 17:00 and 1 at 06:30) leads the warmth:
	// by the sun's height alone 17:00 (the sun some 26 degrees up) read as a plain afternoon
	float golden = skyTable(SKY_X_STATE_TIME, SKY_ROW_LIGHT).r;
	f.low = max(1.0 - smoothstep(0.05, 0.55, sunW.y), 0.75 * golden);
	f.up = smoothstep(-0.14, 0.02, sunW.y);
	f.warm = max(1.0 - smoothstep(0.08, 0.45, sunW.y), golden);
	// the sun's light (linear) while it is up; under the horizon the table's sun-side horizon carries the hue
	vec3 sunL = skyLandDirect();
	vec3 horL = pow(max(skyHorizon(true), vec3(1e-4)), vec3(2.2));
	float ls = skyLumLuma(sunL);
	vec3 h = ls > 1e-3 ? sunL / ls : horL / max(skyLumLuma(horL), 1e-4);
	h = mix(h, horL / max(skyLumLuma(horL), 1e-4), (1.0 - smoothstep(0.0, 0.06, sunW.y)) * 0.5);
	// never a channel past 1.9 of the luma (no neon orange)
	h = min(h, vec3(1.9));
	f.hue = h / max(skyLumLuma(h), 1e-4);
	// the glow's amber: a warm gold (less orange than Complementary's), by how golden the hour is
	const vec3 GOLD = vec3(1.0, 0.66, 0.36) / 0.7448;
	vec3 g = mix(f.hue, GOLD, 0.6 * f.warm);
	f.gold = g / max(skyLumLuma(g), 1e-4);
	return f;
}

// The hue-keeping shoulder in linear light: past `knee` the brightest channel eases toward 1 and the colour is scaled
// with it, so a hue never slides toward white by clipping.
vec3 skyLumCapLin(vec3 L) {
	const float knee = 0.78;
	float m = max(L.r, max(L.g, L.b));
	if (m <= knee) {
		return L;
	}
	float t = knee + (1.0 - knee) * (1.0 - exp(-(m - knee) / (1.0 - knee)));
	return L * (t / m);
}

vec3 skyLumCap(vec3 col) {
	return pow(skyLumCapLin(pow(max(col, 0.0), vec3(2.2))), vec3(1.0 / 2.2));
}

// `col` the dome's display colour toward unit direction d. `amount` scales everything (1 the dome, less for the air).
vec3 skyLuminous(vec3 col, vec3 d, SkyLum f, float amount) {
#ifdef SKY_LUMINOUS
	float k = amount * float(SKY_GLOW) * 0.01 * f.clear * f.day;
	if (k <= 0.0) {
		return col;
	}
	vec3 L = pow(max(col, 0.0), vec3(2.2));
	float y = skyLumLuma(L);
	float hp = max(d.y, 0.0);
	float mu = dot(d, f.sunW);
	vec2 dz = d.xz, sz = f.sunW.xz;
	float az = dot(dz, dz) > 1e-8 && dot(sz, sz) > 1e-8 ? dot(normalize(dz), normalize(sz)) : 0.0;
	float side = 0.5 + 0.5 * az;

	// the zenith: deeper and a little richer overhead (less while the sun is low: a golden-hour zenith stays light)
	float zen = smoothstep(0.15, 0.95, hp);
	vec3 rich = max(mix(vec3(y), L, 1.15), 0.0) * 0.84;
	L = mix(L, rich, zen * 0.6 * min(k, 1.0) * (1.0 - 0.5 * f.low));

	// the luminous band: broad and soft, its own hue paler and brighter; cool away from the sun, on the sun's side
	// warming toward the sun's hue as it sinks (its brightness kept: only the hue moves), and a touch of that warmth
	// all round the horizon while the sun is low
	float band = exp(-hp / 0.30) * (1.0 - 0.45 * smoothstep(0.0, 0.12, -d.y));
	vec3 pale = mix(L, vec3(y), 0.42) * 1.55;
	vec3 coolPale = pale * vec3(0.98, 1.0, 1.03);
	float warm = f.warm * f.up * mix(0.25, 1.0, side * side);
	vec3 bandC = mix(coolPale, f.gold * skyLumLuma(pale), clamp(0.80 * warm, 0.0, 1.0));
	L = mix(L, bandC, clamp(band * 0.85 * k, 0.0, 1.0));
	y = skyLumLuma(L);

	// the glow round the sun: mixed toward the sun's hue at a brighter level (amber at a low sun, never white)
	float lobe = 0.35 * skyLumHg(mu, 0.93) + 0.65 * skyLumHg(mu, 0.55);
	float glow = (0.22 + 0.55 * f.low) * f.up;
	vec3 target = f.gold * max(y, 0.10) * (1.2 + 1.2 * lobe);
	L = mix(L, target, clamp(lobe * glow * k, 0.0, 0.92));
	// the dome lit: the whole sky a little brighter by day, the sun's half more while the sun is low
	L *= 1.0 + k * (0.10 + 0.14 * f.low * f.up * side);

	return pow(skyLumCapLin(L), vec3(1.0 / 2.2));
#else
	return col;
#endif
}

#endif
