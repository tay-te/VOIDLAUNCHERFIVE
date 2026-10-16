// VOID Welkin water: the ripples. An exact sum of 18 fixed waves, evaluated at the pixel's own point (no texel snap,
// no slope steps), animated with the clock; the wind re-weights them and scales them, rain adds rings. The same field
// lights the water (its normal) and the ground under it (the caustics, in the shadow pass).
//
// Precision and seams: every wave vector is a whole number of cycles per 1024 blocks, and the integer part of the
// position is wrapped to that period with integer maths (the camera's integer position times 16, in ints), so the
// phase is exact anywhere in the world and the pattern tiles with no seam. Every frequency is a whole number of cycles
// per hour, so frameTimeCounter's wrap at 3600 s is seamless too.
//
// The wind never turns or speeds up a wave (that would make them jump or race): it re-weights the 16 directional waves
// by how well each lines up with void_wind's direction and scales their height by its speed and gusts. With distance,
// the waves shorter than a few screen pixels fade out smoothly (no shimmer), and what they would have tilted the
// surface by is handed on as roughness (WsWaves.var), which widens the sun's image in the water instead: the
// glitter path gets longer and softer with distance, as it does on real water.

#ifndef WELKIN_WS_RIPPLES
#define WELKIN_WS_RIPPLES

const int WS_P = 16384;            // the period in sixteenths of a block (1024 blocks)
const int WS_PMASK = WS_P - 1;

// 8 swells (6.4 blocks) every 45 degrees, 8 shorter waves (3.3 blocks) between them, 2 chop waves (1.6 blocks).
const ivec2 WS_K[18] = ivec2[18](
	ivec2( 160,    0), ivec2( 113,  113), ivec2(   0,  160), ivec2(-113,  113),
	ivec2(-160,    0), ivec2(-113, -113), ivec2(   0, -160), ivec2( 113, -113),
	ivec2( 286,  119), ivec2( 119,  286), ivec2(-119,  286), ivec2(-286,  119),
	ivec2(-286, -119), ivec2(-119, -286), ivec2( 119, -286), ivec2( 286, -119),
	ivec2( 600,  220), ivec2(-230,  610)
);
// Cycles per hour (900 = 0.25 Hz), a little different per wave so crests never march in step.
const float WS_F[18] = float[18](
	900.0, 914.0, 886.0, 928.0, 872.0, 942.0, 858.0, 956.0,
	1224.0, 1240.0, 1206.0, 1258.0, 1190.0, 1274.0, 1172.0, 1290.0,
	1764.0, 1790.0
);

struct WsWaves {
	vec2 slope;    // dh/dx, dh/dz (per block)
	float height;  // blocks
	float var;     // slope variance of the waves too short to draw here (roughness for the glints)
	float energy;  // how rough the water is, 0.12..1.3 (times the shelter)
};

// How rough open water is in this wind and rain (before the shelter).
float wsEnergy(vec4 wind, float rain) {
	return clamp(0.12 + wind.z / 9.0 + 0.4 * rain, 0.12, 1.3);
}

// The wave field at camera-relative point `rel` (x, z), drawn for `footprint` blocks per screen pixel. shelter 0..1:
// 1 open water, 0 under trees and roofs (calm, not still). t: frameTimeCounter.
WsWaves wsWaveField(vec2 rel, float footprint, vec4 wind, float rain, float shelter, float t) {
	WsWaves w;
	w.slope = vec2(0.0);
	w.height = 0.0;
	w.var = 0.0;
	w.energy = wsEnergy(wind, rain) * mix(0.3, 1.0, shelter);

	// the position in sixteenths of a block: integer part exact (wrapped), the rest as a fraction
	vec2 local = (cameraPositionFract.xz + rel) * 16.0;
	vec2 fl = floor(local);
	ivec2 tw = (cameraPositionInt.xz * 16 + ivec2(fl)) & WS_PMASK;
	vec2 tf = local - fl;
	const float TAU = 6.2831853;
	const float PER_BLOCK = TAU / 1024.0; // d(phase)/d(block) per cycle per 1024 blocks

	for (int i = 0; i < 18; i++) {
		ivec2 k = WS_K[i];
		vec2 kf = vec2(k);
		float klen = length(kf);
		float weight;
		float amp;
		if (i < 16) {
			float a = max(dot(kf / klen, wind.xy), 0.0);
			weight = 0.12 + 0.88 * a * a; // mostly down the wind, a little from every side
			amp = i < 8 ? 0.055 : 0.026;
		} else {
			weight = 0.35 + 0.65 * wind.w; // chop comes with gusts, from no side in particular
			amp = 0.011;
		}
		float a = amp * weight * w.energy;
		// fade a wave out between 8 and 3 screen pixels per wavelength; its slope then counts as roughness
		float pixels = (1024.0 / klen) / max(footprint, 1e-4);
		float keep = smoothstep(3.0, 8.0, pixels);
		float slopeAmp = a * PER_BLOCK * klen;
		w.var += 0.5 * slopeAmp * slopeAmp * (1.0 - keep);
		a *= keep;
		if (a <= 0.0) {
			continue;
		}
		int ph = (k.x * tw.x + k.y * tw.y) & WS_PMASK;
		float th = TAU * ((float(ph) + dot(kf, tf)) / float(WS_P) - fract(WS_F[i] / 3600.0 * t));
		w.height += a * sin(th);
		w.slope += (a * cos(th) * PER_BLOCK) * kf;
	}
	return w;
}

// Rain rings: in each block a drop may land every 0.75 s, at a random place and moment; its ring spreads to a quarter
// block and fades. Returns the slope it adds (0 where none), already faded out where a block is under 20 pixels.
vec2 wsRainRings(vec2 rel, float rain, float footprint, float t) {
	float show = rain * (1.0 - smoothstep(0.02, 0.05, footprint));
	if (show <= 0.02) {
		return vec2(0.0);
	}
	vec2 local = cameraPositionFract.xz + rel;
	vec2 fl = floor(local);
	ivec2 cell = cameraPositionInt.xz + ivec2(fl);
	vec2 f = local - fl;
	float u = t / 0.75 + hash01(ivec3(cell, 7919)); // 4800 drops an hour: the clock's wrap lands on a whole drop
	int drop = int(floor(u));
	float tau = u - float(drop);
	ivec3 key = ivec3(cell.x, drop, cell.y);
	if (hash01(key) > rain * 0.6) {
		return vec2(0.0);
	}
	vec2 centre = 0.3 + 0.4 * vec2(hash01(key + ivec3(1, 0, 0)), hash01(key + ivec3(0, 0, 1)));
	vec2 d = f - centre;
	float r = length(d);
	float radius = 0.04 + 0.24 * tau;
	float x = (r - radius) / 0.035;
	float g = -2.0 * x * exp(-x * x); // the slope across a smooth ring crest
	float a = 0.22 * (1.0 - tau) * (1.0 - tau) * show;
	return r > 1e-4 ? (d / r) * (a * g) : vec2(0.0);
}

// The water's normal for a top face, with roughness, and how rough it is. Flat when ripples are off.
vec3 wsWaterNormal(vec2 rel, float footprint, vec4 wind, float rain, float shelter, float t, out float var,
		out float energy) {
	var = 0.0;
	energy = 0.0;
#if WS_RIPPLE_MODE > 0
	WsWaves w = wsWaveField(rel, footprint, wind, rain, shelter, t);
	var = w.var;
	energy = w.energy;
	vec2 s = w.slope;
#if WS_RIPPLE_MODE > 1
	s += wsRainRings(rel, rain * shelter, footprint, t);
#endif
	return normalize(vec3(-s.x, 1.0, -s.y));
#else
	return vec3(0.0, 1.0, 0.0);
#endif
}

// Caustics: how much the waves gather the light at a point of the ground under them, as a gain about 1 (0.7..1.5):
// bright lines where the surface crosses its rest height, from the same field, smooth. footprint: blocks per texel of
// whatever is drawing them (the shadow map), so the lines fade out before they would alias.
float wsCaustic(vec2 rel, float footprint, vec4 wind, float rain, float t) {
	WsWaves w = wsWaveField(rel, max(footprint, 0.02), wind, rain, 1.0, t);
	float s = 0.03 * max(w.energy, 0.15);
	float c = 1.0 - smoothstep(0.0, s, abs(w.height));
	float contrast = 1.0 - smoothstep(0.12, 0.5, footprint);
	return mix(1.0, 0.72 + 0.8 * c * c, contrast);
}

#endif
