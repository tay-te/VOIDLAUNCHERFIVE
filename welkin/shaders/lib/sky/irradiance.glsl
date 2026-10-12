// VOID Welkin sky: the sky's light on the land from the dome itself (lighting techniques 2.5, workstream B).
//
// Once a frame, prepare integrates the dome's colour (the same control points and blend lib/sky/dome.glsl draws,
// taken to linear light) over the upper hemisphere into nine RGB spherical-harmonic coefficients (l <= 2), already
// convolved with the cosine lobe (Ramamoorthi and Hanrahan 2001, "An Efficient Representation for Irradiance
// Environment Maps"), and writes them to the sky table's row 13 (SKY_ROW_IRRADIANCE, x 0..8). They are divided, per
// channel, by the irradiance they give an upward face, so the light pass multiplies the table's tuned fill
// (skyLandFill, an upward face in the open) by skyIrradiance(n) and every level the rounds tuned stays where it was:
// only the distribution over orientation now comes from the sky. A face toward the bright low sky round the sun warms
// and brightens, one toward the deep zenith or the rose band opposite takes their colour, a wall facing the horizon
// sees about half of an upward face's light (the share the horizon's brightness gives it), a downward face none (the
// ground's bounce is its own term, lib/light.glsl). Under cloud the dome greys and so does the light; at night it is
// the moonlit dome's.
//
// The sun's disc, the moon and the stars are not in it (the direct light is its own term); nor are the voxel clouds.
//
// Stored as v / (2 SKY_IRR_RANGE) + 0.5 in the RGBA16 table (the normalised coefficients stay within about +-2).

#ifndef WELKIN_SKY_IRRADIANCE
#define WELKIN_SKY_IRRADIANCE

#include "/lib/sky/table.glsl"

#define SKY_ROW_IRRADIANCE 13
#define SKY_IRR_RANGE 4.0

// The real spherical-harmonic basis to l = 2, for a unit direction.
void skyShBasis(vec3 d, out float y[9]) {
	y[0] = 0.282095;
	y[1] = 0.488603 * d.y;
	y[2] = 0.488603 * d.z;
	y[3] = 0.488603 * d.x;
	y[4] = 1.092548 * d.x * d.y;
	y[5] = 1.092548 * d.y * d.z;
	y[6] = 0.315392 * (3.0 * d.z * d.z - 1.0);
	y[7] = 1.092548 * d.x * d.z;
	y[8] = 0.546274 * (d.x * d.x - d.y * d.y);
}

// The sky's light on a face with world normal n, relative to an upward face in the open, per channel (1 on an
// upward face). Linear; never negative.
vec3 skyIrradiance(vec3 n) {
	float y[9];
	skyShBasis(n, y);
	vec3 e = vec3(0.0);
	for (int k = 0; k < 9; k++) {
		e += (skyTable(k, SKY_ROW_IRRADIANCE).rgb - 0.5) * (2.0 * SKY_IRR_RANGE) * y[k];
	}
	return max(e, vec3(0.0));
}

#endif

// ---- the builder (prepare only: lib/sky/build.glsl includes this file after its own definitions) ------------------
#if defined WELKIN_SKY_BUILD && !defined WELKIN_SKY_IRRADIANCE_BUILD
#define WELKIN_SKY_IRRADIANCE_BUILD

// how many directions over the upper hemisphere (a Fibonacci spiral, even in solid angle)
#define SKY_IRR_SAMPLES 128

// the dome's control points as the table's rows 0..4 hold them (ring r, band e at r * 6 + e)
vec3 skyIrrDome[30];

// The dome's colour in unit direction d (h = d.y >= 0), as lib/sky/dome.glsl skyDomePos and skyDomeColour blend it
// from the table, then the warm band toward a low sun and the rose band opposite (skyLowSunBandOver). The moon's halo
// is left out (it is faint and small). Keep in step with dome.glsl.
vec3 skyIrrDomeAt(vec3 d, vec3 sunW, float amount, float spread, vec4 lowBand) {
	float h = max(d.y, 0.0);
	float x = clamp(sqrt(h) * 4.6 - 0.5, 0.0, 4.0) / 4.0;
	float band = 1.0 + 4.0 * mix(x, softBands(x, 4.0), smoothstep(0.25, 0.6, x));
	float shape = clamp(1.0 - (1.0 - dot(d, sunW)) / max(spread, 0.01), 0.0, 1.0);
	shape *= mix(1.0, 0.5, clamp(h * 2.5, 0.0, 1.0));
	float rx = clamp((shape * amount * 4.0 - 0.5) / 3.5, 0.0, 1.0);
	rx = rx * rx * (3.0 - 2.0 * rx);
	float ring = 4.0 * mix(rx, softBands(rx, 4.0), smoothstep(0.08, 0.3, h));
	float bi = min(floor(band), 4.0);
	int b0 = int(bi);
	float bf = clamp(band - bi, 0.0, 1.0);
	float ri = min(floor(ring), 3.0);
	int r0 = int(ri);
	float rf = clamp(ring - ri, 0.0, 1.0);
	vec3 lo = mix(skyIrrDome[r0 * 6 + b0], skyIrrDome[r0 * 6 + b0 + 1], bf);
	vec3 hi = mix(skyIrrDome[(r0 + 1) * 6 + b0], skyIrrDome[(r0 + 1) * 6 + b0 + 1], bf);
	vec3 col = mix(lo, hi, rf);
	if (lowBand.a > 0.0) {
		vec2 sz = sunW.xz;
		float az = 0.0;
		if (dot(d.xz, d.xz) > 1e-8 && dot(sz, sz) > 1e-8) {
			az = dot(normalize(d.xz), normalize(sz));
		}
		float wAz = 0.5 + 0.5 * az;
		wAz *= wAz;
		float w = clamp(lowBand.a * wAz * exp(-h / 0.10), 0.0, 1.0);
		col = mix(col, max(col, lowBand.rgb), w);
		float wAnti = 0.5 - 0.5 * az;
		wAnti *= wAnti;
		vec3 rose = vec3(0.86, 0.64, 0.70) * max(dot(lowBand.rgb, vec3(0.2126, 0.7152, 0.0722)) * 0.62, 0.0);
		float wa = clamp(lowBand.a * wAnti * exp(-h / 0.22) * 0.55, 0.0, 1.0);
		col = mix(col, max(col, rose), wa);
	}
	return col;
}

// Coefficient k (0..8) of the sky's irradiance, normalised to an upward face, encoded for the table.
vec4 skyIrradianceTexel(SkyMood m, SkyInputs s, vec3 sunW, vec3 moonW, int k) {
	for (int r = 0; r < 5; r++) {
		for (int e = 0; e < 6; e++) {
			skyIrrDome[r * 6 + e] = skyDome(m, s, r, e);
		}
	}
	vec4 lowBand = skyLand(m, s, sunW, moonW).band;
	vec3 c[9];
	for (int j = 0; j < 9; j++) {
		c[j] = vec3(0.0);
	}
	float y[9];
	for (int i = 0; i < SKY_IRR_SAMPLES; i++) {
		float h = (float(i) + 0.5) / float(SKY_IRR_SAMPLES);
		float a = float(i) * 2.3999632;
		float r = sqrt(max(1.0 - h * h, 0.0));
		vec3 d = vec3(r * cos(a), h, r * sin(a));
		vec3 L = pow(max(skyIrrDomeAt(d, sunW, m.glow, m.spread, lowBand), 0.0), vec3(2.2));
		skyShBasis(d, y);
		for (int j = 0; j < 9; j++) {
			c[j] += L * y[j];
		}
	}
	// each sample stands for 2 pi / N of the hemisphere; then the cosine lobe's band factors (pi, 2 pi / 3, pi / 4)
	float w = 6.2831853 / float(SKY_IRR_SAMPLES);
	vec3 e[9];
	for (int j = 0; j < 9; j++) {
		float band = j == 0 ? 3.1415927 : (j < 4 ? 2.0943951 : 0.7853982);
		e[j] = c[j] * (w * band);
	}
	// what an upward face gets
	skyShBasis(vec3(0.0, 1.0, 0.0), y);
	vec3 up = vec3(0.0);
	for (int j = 0; j < 9; j++) {
		up += e[j] * y[j];
	}
	vec3 v = e[k] / max(up, vec3(1e-7));
	// a sky too dark to have a shape (a black night, blindness): a plain even sky, so the land keeps the tuned fill
	if (max(up.r, max(up.g, up.b)) < 1e-6) {
		v = k == 0 ? vec3(1.772454) : (k == 1 ? vec3(1.023327) : vec3(0.0));
	}
	return vec4(clamp(v / (2.0 * SKY_IRR_RANGE) + 0.5, 0.0, 1.0), 1.0);
}

#endif
