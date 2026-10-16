// VOID Welkin: the depth prototypes' shared helpers (the depth A/B sheet, branch welkin-depth, 2026-10-16).
//
// Every prototype is off by default (lib/settings.glsl DEPTH_*; WD_* are their 0/1 switches, all on with DEPTH_ALL):
//   SKY     wdSkyLum: the dome's luminous horizon, the glow round the sun, a deeper zenith (gbuffers_skybasic, and the
//           haze's air in composite2, so the far land fades into the same luminous horizon)
//   BLOOM   wdBloomSource: what of a display-space pixel is bright enough to scatter (composite3, final)
//   HAZE    wdHazeDensity, wdPhase: the volumetric haze's height profile and phase (deferred_shafts, composite2)
// Written from scratch for Welkin; no other pack's code.

#ifndef WELKIN_DEPTHFX
#define WELKIN_DEPTHFX

#include "/lib/settings.glsl"
#include "/lib/sky/table.glsl"

float wdLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// A Henyey-Greenstein lobe of asymmetry g at mu = cos(angle to the light), normalized so its mean over the sphere is 1
// (an isotropic medium scatters 1 everywhere).
float wdHgIso(float mu, float g) {
	float a = 1.0 + g * g;
	return (1.0 - g * g) / pow(max(a - 2.0 * g * mu, 1e-4), 1.5);
}

// The same lobe scaled to 1 straight at the light.
float wdHgPeak(float mu, float g) {
	float a = 1.0 + g * g;
	return pow((a - 2.0 * g) / max(a - 2.0 * g * mu, 1e-4), 1.5);
}

// ---- HAZE: the volumetric haze's density, relative to the air at sea level: an exponential falloff with height, so
// the haze lies thick in valleys and the low ground and thins over the ridges; the rays toward the horizon, which stay
// low the longest, gather the most. Blocks of world height y.
#define WD_HAZE_SEA 62.0
#define WD_HAZE_SCALE_H 64.0
// the haze's extinction per block of sea-level air (composite2 scales it by Gloam's air: haziness, mist, rain)
#define WD_HAZE_SIGMA 0.0009
float wdHazeDensity(float y) {
	return exp(-max(y - WD_HAZE_SEA, -20.0) / WD_HAZE_SCALE_H);
}

// ---- BLOOM: the bright share of a display-space colour, in linear light with headroom. The land, the sky and the
// clouds leave the earlier passes as display values squeezed under white (lib/tone.glsl's shoulder): a near-white
// pixel stands for much more light than it shows, so its light is given that headroom back (up to about 6x at white)
// before the soft threshold. `emit` (0..1) adds a glowing texel's own light (a torch's flame, a lamp).
#define WD_BLOOM_THRESHOLD 0.8
#define WD_BLOOM_KNEE 0.4
float wdBloomHeadroom(vec3 lin) {
	float y = max(lin.r, max(lin.g, lin.b));
	float s = smoothstep(0.6, 1.0, y);
	return 1.0 + 7.0 * s * s * s;
}

vec3 wdBloomSource(vec3 display, float emit) {
	vec3 lin = pow(max(display, 0.0), vec3(2.2));
	lin *= wdBloomHeadroom(lin) * (1.0 + 4.0 * emit);
	float y = wdLuma(lin);
	float soft = clamp(y - WD_BLOOM_THRESHOLD + WD_BLOOM_KNEE, 0.0, 2.0 * WD_BLOOM_KNEE);
	soft = soft * soft / (4.0 * WD_BLOOM_KNEE + 1e-5);
	float share = max(soft, y - WD_BLOOM_THRESHOLD) / max(y, 1e-5);
	return lin * share;
}

// ---- SKY: the luminous dome. `col` the dome's display colour in unit direction d, sunW the sun. Needs the sky table
// (lib/sky/table.glsl) included first. In display values.
vec3 wdSkyLum(vec3 col, vec3 d, vec3 sunW) {
#if WD_SKY
	vec3 L = pow(max(col, 0.0), vec3(2.2));
	float day = 1.0 - skyNightStep();
	float sunUp = smoothstep(-0.10, 0.02, sunW.y);
	float hp = max(d.y, 0.0);
	float y = wdLuma(L);
	// a deeper, richer zenith: the top of the dome darkens and gains a little saturation, so the dome runs from a
	// luminous horizon to a deep blue overhead instead of one even blue
	float zen = smoothstep(0.10, 0.85, hp);
	vec3 rich = max(mix(vec3(y), L, 1.22), 0.0) * 0.72;
	L = mix(L, rich, zen * 0.75 * day);
	// a luminous horizon: a paler, brighter band, broad and soft (no edge), at the dome's own hue a little whitened
	float band = exp(-hp / 0.15) * (1.0 - smoothstep(0.0, 0.12, -d.y) * 0.5);
	vec3 pale = mix(L, vec3(wdLuma(L)), 0.28) * 1.42;
	L = mix(L, max(L, pale), band * 0.85 * day);
	// the glow round the sun across the dome: forward scatter in the sun's own colour, a tight bright core and a broad
	// lobe over its half of the sky, strongest while the sun is low (its light crosses the most air)
	vec3 sunC = skyLandDirect();
	sunC /= max(wdLuma(sunC), 1e-4);
	float mu = dot(d, sunW);
	float lobe = 0.45 * wdHgPeak(mu, 0.93) + 0.55 * wdHgPeak(mu, 0.62);
	float low = 1.0 - smoothstep(0.08, 0.6, sunW.y);
	float amt = (0.10 + 0.32 * low) * sunUp * day;
	L += sunC * (lobe * amt * mix(0.75, 1.0, band)) * max(y, 0.25);
	return pow(max(L, 0.0), vec3(1.0 / 2.2));
#else
	return col;
#endif
}

#endif
