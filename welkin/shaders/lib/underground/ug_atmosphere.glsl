// VOID Welkin, underground (M5, P6): the air under the land, for composite2. Entries: ugComposite() (reads the
// buffers itself) and ugAtmosphere() (the maths).
//
//   water kinds   translucent cavern water is tinted by its kind (river green-teal, buried sea cobalt, a realm's lake
//                 blue-green in the realm's colour), absorbing smoothly with its thickness, with small glints on the
//                 surface that fade in and out (the sea's glow even in the dark); looking out from under the water,
//                 the same colours close in
//   fog           reach from openness: the tile round the eye and UG_FOG_SAMPLES points along the view ray each give a
//                 density (1 / reach(size of the space)), blended between cells; their mean times the distance is the
//                 optical depth. Tight in passages (~20 blocks), long in caverns (~100) and over buried seas (~300).
//                 A smooth exponential fog, no steps and no dither.
//   fog colour    three strata from floor to roof (the floor and roof where the eye stands), blended smoothly into each
//                 other: the low one takes the colour of the water under the ray, the middle the realm's, the roof's
//                 is darker, or teal under glowworms
//   water mist    an exact ray-slab intersection over the water the ray sees: over great rivers a mist 4..6 blocks
//                 deep whose top rises and falls slowly in a smooth swell, with soft bands drifting up through it; a
//                 low fog on buried seas, a faint one on lakes, an ember haze over lava
//
// Fetches per pixel (four per tile read, the bilinear blend): the front surface, UG_FOG_SAMPLES along the ray, the
// mist's middle (only where a slab is crossed), plus the shared state's header texels and the eye's cells (the same
// for every pixel). No marching.
//
// Use in composite2, after the surface air (the haze and the valley mist hand over by 1 - ug.amount):
//
//   UgState ug = ugState();
//   color.rgb = ugComposite(ug, p, texcoord, color.rgb);
//
// ugComposite needs WELKIN_DEPTH_BEHIND defined before the program first includes lib/depth.glsl (composite2 does).

#ifndef WELKIN_UG_ATMOSPHERE
#define WELKIN_UG_ATMOSPHERE

#include "/lib/underground/ug_common.glsl"
#include "/lib/depth.glsl"

#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#ifndef WU_colortex3
#define WU_colortex3
uniform sampler2D colortex3;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif

#define UG_SKY_DIST 256.0 // how far a sky pixel is taken to be, underground
#define UG_MIST_RISE 0.8  // blocks a second the river mist's swell climbs

// t along a ray from the eye (y = 0) with vertical direction dy, inside the slab lo..hi, clamped to 0..dist.
vec2 ugSlab(float lo, float hi, float dy, float dist) {
	if (abs(dy) < 1e-5) {
		return lo <= 0.0 && hi >= 0.0 ? vec2(0.0, dist) : vec2(0.0);
	}
	float a = lo / dy;
	float b = hi / dy;
	return clamp(vec2(min(a, b), max(a, b)), 0.0, dist);
}

// Smooth value noise over the block grid (the corners hashed, smoothstep between): the river mist's swell.
float ugBlockNoise(vec3 eyePos) {
	vec2 local = cameraPositionFract.xz + eyePos.xz;
	vec2 lf = floor(local);
	ivec2 b = cameraPositionInt.xz + ivec2(lf);
	vec2 f = local - lf;
	f = f * f * (3.0 - 2.0 * f);
	float a = hash01(ivec3(b.x, 11, b.y));
	float c = hash01(ivec3(b.x + 1, 11, b.y));
	float d = hash01(ivec3(b.x, 11, b.y + 1));
	float e = hash01(ivec3(b.x + 1, 11, b.y + 1));
	return mix(mix(a, c, f.x), mix(d, e, f.x), f.y);
}

// The mean fog density along the view ray, and the tile at the ray's middle sample (for the strata and the mist).
float ugFogDensity(UgState s, vec3 dir, float dist, out UgMix mid) {
	mid = s.eye;
	float eyeD = 1.0 / ugReachOpen(s.open, s.clear);
	if (s.mode != UG_MODE_TILE) {
		return eyeD;
	}
	// the tile round the eye: its vault tells a hall from a passage, which the openness (taken at eye height, where
	// the floor is always near) cannot
	eyeD = mix(eyeD, s.eye.density, s.eye.valid * s.eye.open);
	float sum = eyeD;
	float weight = 1.0;
	float reach = min(dist, float(UG_REACH * 4 - 16));
	for (int i = 0; i < UG_FOG_SAMPLES; i++) {
		float t = (float(i) + 0.5) / float(UG_FOG_SAMPLES) * reach;
		UgMix c = ugMix(s, dir * t);
		float w = c.valid * c.open;
		sum += c.density * w;
		weight += w;
		if (i == UG_FOG_SAMPLES / 2) {
			mid = c;
		}
	}
	return sum / weight;
}

// The water kinds at a tile point, as weights, falling back to the eye's kind where the tile says nothing.
vec4 ugWaterAt(UgState s, UgMix m) {
	return mix(ugKindHot(s.kind), m.water, clamp(m.valid * 2.0, 0.0, 1.0));
}

// The fog colour at a point `v` of the way from floor (0) to roof (1), in three strata blended into each other: the low
// one takes the water's colour, the roof's goes darker, or teal under glowworms (`glow` 0..1).
vec3 ugFogColor(UgState s, vec4 water, float v, float glow) {
	vec3 mid = s.fog;
	vec3 low = mix(mid * 1.15, ugWaterLow(water), 0.45 * ugWaterSum(water));
	vec3 roof = mid * 0.72;
#ifdef UG_GLOWWORMS
	roof = mix(roof, UG_GLOW_COLOR * 0.085, smoothstep(0.0, 1.0, glow) * 0.7);
#endif
	vec3 c = mix(low, mid, smoothstep(0.18, 0.42, v));
	c = mix(c, roof, smoothstep(0.62, 0.86, v));
	return c * s.light;
}

// Cavern water: tinted by kind, absorbing with its thickness, with glints.
vec3 ugWater(UgState s, vec3 color, vec3 eyeFront, float thick, UgMix hit, float dist) {
	vec4 w = ugWaterAt(s, hit);
	w.w = 0.0; // lava is not water
	vec3 lake = mix(vec3(0.030, 0.084, 0.102), s.fog * 1.6, 0.35);
	vec3 deep = ugWaterDeep(w, lake);
	// the water takes its colour at the brightness it is lit to: dark in the dark, clearer by a lantern
	float lit = clamp(dot(color, vec3(0.3, 0.5, 0.2)) * 5.0, 0.3, 1.0);
	float a = 1.0 - exp(-thick / ugWaterClear(w));
	color = mix(color, deep * lit * s.light, a * 0.8);
	// glints on the surface, 2x2 texels, each swelling and fading in turn
	if (dist < 40.0) {
		float none = 1.0 - ugWaterSum(w);
		float rate = dot(w, vec4(0.02, 0.035, 0.012, 0.0)) + 0.008 * none;
		vec3 tint = mat4x3(vec3(0.12, 0.23, 0.19), vec3(0.10, 0.16, 0.34), vec3(0.14, 0.24, 0.28), vec3(0.0)) * w
			+ vec3(0.12, 0.18, 0.21) * none;
		ivec3 q = worldTexel(eyeFront - vec3(0.0, 1.0 / 32.0, 0.0), 1);
		float h = hash01(ivec3(q.x, 7, q.z));
		float t = frameTimeCounter * 1.5 + h * 7.0;
		int phase = int(floor(t));
		if (hash01(ivec3(q.x, phase, q.z)) < rate) {
			// the buried seas glow on their own; the rest only catch light
			float swell = sin(3.14159265 * fract(t));
			color += tint * swell * mix(s.light * 0.6, 1.0, w.y) * (1.0 - smoothstep(30.0, 40.0, dist));
		}
	}
	return color;
}

// Mist over the water the ray sees: an exact intersection with a slab on the water's surface.
vec3 ugMist(UgState s, vec3 color, vec3 dir, float dist, UgMix hit, UgMix mid, float density, vec3 fogColor) {
	// the water that sets the slab: the first of the front surface's, the middle sample's and the eye's points that
	// is open and over water; only the great rivers steam
	UgMix w = hit;
	vec4 k = hit.water * vec4(hit.flags.z, 1.0, 1.0, 1.0);
	if (hit.valid * hit.open * ugWaterSum(k) <= 0.0) {
		w = mid;
		k = mid.water * vec4(mid.flags.z, 1.0, 1.0, 1.0);
	}
	if (w.valid * w.open * ugWaterSum(k) <= 0.0) {
		w = s.eye;
		k = s.eye.water * vec4(s.eye.flags.z, 1.0, 1.0, 1.0);
	}
	float sumK = ugWaterSum(k);
	if (w.valid * w.open * sumK <= 0.0) {
		return color;
	}
	vec4 kn = k / sumK; // the kinds' shares of this mist
	// depth, the dense layer's share, the densities (1 / blocks), the colour: river, sea, lake, lava
	float h = dot(kn, vec4(2.0 + 4.0 * max(s.cave.w, 0.5), 3.0, 2.0, 5.0));
	float innerShare = dot(kn, vec4(0.4, 0.4, 0.5, 0.3));
	float innerD = dot(kn, vec4(0.16, 0.10, 0.06, 0.14));
	float outerD = dot(kn, vec4(0.035, 0.03, 0.02, 0.05));
	vec3 lakeMist = mix(vec3(0.15, 0.19, 0.21), s.fog * 2.0, 0.3);
	vec3 mistColor = mat4x3(vec3(0.17, 0.20, 0.20) * s.light, vec3(0.12, 0.15, 0.24) * s.light, lakeMist * s.light,
		vec3(0.55, 0.22, 0.08)) * kn; // lava's haze is lit by the lava itself
#ifdef UG_GLOWWORMS
	mistColor = mix(mistColor, UG_GLOW_COLOR * 0.3, smoothstep(0.0, 1.0, s.cave.x) * 0.25);
#endif
	float lo = w.floorY;
	vec2 t = ugSlab(lo, lo + h, dir.y, dist);
	if (t.y <= t.x) {
		return color;
	}
	// the mist stands only over its own water: how much of the segment's middle is water of these kinds
	vec3 m = dir * (0.5 * (t.x + t.y));
	UgMix mc = ugMix(s, m);
	vec4 mk = mc.water * vec4(mc.flags.z, 1.0, 1.0, 1.0);
	float here = clamp(dot(mk, vec4(greaterThan(kn, vec4(0.0)))), 0.0, 1.0) * mc.valid;
	if (here <= 0.0) {
		return color;
	}
	// over the great rivers the top swells slowly up and down, smoothly from block to block
	float rise = 0.5 + 0.5 * sin(6.2831853 * (frameTimeCounter * UG_MIST_RISE / h + ugBlockNoise(m)));
	float top = h * mix(1.0, 0.55 + 0.45 * rise, kn.x);
	vec2 outer = ugSlab(lo, lo + top, dir.y, dist);
	vec2 inner = ugSlab(lo, lo + h * innerShare, dir.y, dist);
	// soft bands drifting up through the thin layer, two blocks apart
	float yWorld = float(cameraPositionInt.y) + cameraPositionFract.y + m.y;
	float band = 0.775 + 0.225 * cos(3.14159265 * (yWorld - frameTimeCounter * 0.5));
	float tau = max(outer.y - outer.x, 0.0) * outerD * band + max(inner.y - inner.x, 0.0) * innerD;
	float a = (1.0 - exp(-tau)) * 0.9 * here;
	// the fog between the eye and the mist
	float f = 1.0 - exp(-0.5 * (outer.x + outer.y) * density * UG_FOG_DENSITY);
	return mix(color, mix(mistColor, fogColor, f), a);
}

// Underground from inside cavern water: the water's own colour closes in.
vec3 ugUnderwater(UgState s, vec3 color, float dist) {
	vec4 w = ugKindHot(s.kind);
	w.w = 0.0;
	vec3 lake = mix(vec3(0.030, 0.084, 0.102), s.fog * 1.6, 0.35);
	float q = 1.0 - exp(-dist / (ugWaterClear(w) * 2.0));
	return mix(color, ugWaterDeep(w, lake) * s.light * 1.3, q);
}

// eyeFront: the nearest surface, translucents included (depthtex0, or the LOD's), eye-relative (world minus
// cameraPosition); eyeBack: the surface behind translucents (depthtex1); sky: nothing drawn (eyeFront then any point
// along the view ray); water: translucent water at this pixel; emission: the front surface's emission 0..1
// (unpackEmission(colortex2.z); 0 on water and sky), so lava and lights carry through the fog.
vec3 ugAtmosphere(UgState s, vec3 color, vec3 eyeFront, vec3 eyeBack, bool sky, bool water, float emission) {
	if (s.mode == UG_MODE_OFF || isEyeInWater >= 2) {
		return color; // above ground, or in lava or powder snow: the pack's own handling
	}
	float dist = sky ? UG_SKY_DIST : length(eyeFront);
	vec3 dir = eyeFront / max(length(eyeFront), 1e-4);
	if (sky) {
		eyeFront = dir * dist;
	}
	vec3 c = color;
	if (isEyeInWater == 1) {
#ifdef UG_WATER_KINDS
		c = ugUnderwater(s, c, dist);
#endif
		return mix(color, c, s.amount);
	}
	UgMix hit = sky ? ugNoMix() : ugMix(s, eyeFront);

#ifdef UG_WATER_KINDS
	if (water && !sky) {
		float thick = min(distance(eyeFront, eyeBack), 48.0);
		c = ugWater(s, c, eyeFront, thick, hit, dist);
	}
#endif

	UgMix mid;
	float density = ugFogDensity(s, dir, dist, mid);
	// the strata lie at fixed heights, framed by the floor and roof where the eye stands (the middle sample's where the
	// eye's own tile is rock), so they run level across the whole view whatever the rock does
	float fw = s.mode == UG_MODE_TILE ? smoothstep(0.0, 0.5, s.eye.valid * s.eye.open) : 1.0;
	float floorY = mix(mid.floorY, s.eye.floorY, fw);
	float ceilY = mix(mid.ceilY, s.eye.ceilY, fw);
	float v = sky ? 1.0 : (eyeFront.y - floorY) / max(ceilY - floorY, 1.0);
	UgMix over = sky ? mid : hit;
	vec4 kinds = ugWaterAt(s, over);
	float glow = max(s.cave.x, over.flags.x * over.valid);
	vec3 fogColor = ugFogColor(s, kinds, v, glow);
#ifdef UG_FOG
	// what glows (lava, torches, glowstone) carries through the fog: a third of the density
	float f = 1.0 - exp(-dist * density * UG_FOG_DENSITY * (1.0 - 0.7 * emission));
	c = mix(c, fogColor, f);
#endif

#ifdef UG_WATER_MIST
	if (s.mode == UG_MODE_TILE) {
		c = ugMist(s, c, dir, dist, hit, mid, density, fogColor);
	}
#endif
	return mix(color, c, s.amount);
}

#ifdef WELKIN_DEPTH_BEHIND
// The whole underground air at screen texel `p` (uv its centre): reads the depths, the water marks (colortex3, written
// by the water programs) and the emission (colortex2.z), then ugAtmosphere. Needs surfaceBehind (WELKIN_DEPTH_BEHIND).
vec3 ugComposite(UgState s, ivec2 p, vec2 uv, vec3 color) {
	if (s.mode == UG_MODE_OFF) {
		return color;
	}
	Surface f = surfaceAt(p, uv);
	vec3 eyeFront = mat3(gbufferModelViewInverse) * f.view;
	vec3 eyeBack = eyeFront;
	bool water = false;
	float emission = 0.0;
	if (!f.sky) {
		water = !f.fromLod && texelFetch(colortex3, p, 0).a > 0.0;
		if (water) {
			Surface b = surfaceBehind(p, uv);
			eyeBack = b.sky ? eyeFront + normalize(eyeFront) * 48.0 : mat3(gbufferModelViewInverse) * b.view;
		} else {
			emission = unpackEmission(texelFetch(colortex2, p, 0).z); // (packed with the light class since the light review)
		}
	}
	return ugAtmosphere(s, color, eyeFront, eyeBack, f.sky, water, emission);
}
#endif

#endif
