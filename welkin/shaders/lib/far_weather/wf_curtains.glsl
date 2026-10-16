// VOID Welkin far weather: rain curtains under far rainy weather (DESIGN 3.4 "Rain and wet ground": far rain becomes
// curtains in composite1).
//
// Each rainy far weather cell (W1, 1 km, in the clouds' drifting frame, so a curtain travels with its front and the
// clouds over it) hangs one column of rain: a box from under the far ground up into the base of the cloud deck that
// stands over its middle in the cloud map (lib/clouds/map.glsl wcMapColumn: the low stratus where the map has it there,
// else the main deck, else the lowest deck), its width set by how hard it rains (heavy cells fill the cell, so a
// front reads as one long wall; light ones stand apart as single shafts), its place in the cell hashed per cell.
// Without Gloam's W1 the clouds' own fallback field (wcFallbackWeather: wet in vanilla rain) places them, so the
// curtains always hang under the clouds that are drawn.
//
// The ray walks the W1 grid with a 2D DDA between the floor and the highest curtain top only, front to back, and
// meets each rainy cell's box exactly (slab test): the optical depth is the true path through the rain, thin at a
// column's edges, thick through its middle and through a whole front. One W1 fetch per cell walked, two more (cloud
// map, far ground) per rainy cell; the walk is long only on the band of rays near the horizon, and capped at
// WF_CURTAIN_STEPS. The walk stops at the land and at a nearer cloud (colortex6), so a cloud in front hides the rain.
//
// The look (2026-10-11): smooth. The opacity is a plain exponential of the path, no steps; the nearest column carries
// soft streaks (smooth value noise across its face, drifting slowly down); the colour is rain lit by the sky table's
// ambient light and, where a low sun gets under the deck, its direct light, brighter looking toward the light, darker
// under a storm; lightning lights it through the sky table's flash.

#ifndef WELKIN_WF_CURTAINS
#define WELKIN_WF_CURTAINS

#include "/lib/far_weather/wf_config.glsl"

struct WfLayer {
	vec3  color;
	float alpha;
	float t;      // where along the ray it starts (blocks)
	float od;     // optical depth (debug views)
};

#if WF_DEBUG == 5
// what the walk met, for WF_DEBUG 5: 0 no stretch to walk, 1 walked dry cells, 2 a rainy cell, 3 its column hidden or
// missed, 4 a column hit with no depth, 5 a curtain
int wfDbgState = 0;
#endif

WfLayer wfNoLayer() {
	return WfLayer(vec3(0.0), 0.0, 1e9, 0.0);
}

// Smooth value noise on a lattice of whole units at `p`, hashed with the clouds' hash.
float wfNoise2(vec2 p, uint salt) {
	vec2 i = floor(p);
	vec2 f = p - i;
	f = f * f * (3.0 - 2.0 * f);
	ivec2 c = ivec2(i);
	float a = wcU01(wcHash(c, salt)), b = wcU01(wcHash(c + ivec2(1, 0), salt));
	float d = wcU01(wcHash(c + ivec2(0, 1), salt)), e = wcU01(wcHash(c + ivec2(1, 1), salt));
	return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

// The camera in W1's frame: its cell and the place in that cell (0..1, a hair over at most).
void wfW1Camera(CloudEnv e, out ivec2 c0, out vec2 lo) {
	ivec2 m = e.frameInt + e.w1Off;
	c0 = m >> 10;
	lo = (vec2(m & 1023) + e.frameFract) / 1024.0;
}

// W1 at frame cell `wc` (R front, G cover, B rain, A storm), faded toward the tile's edge; the fallback field's
// weather at the cell's middle without Gloam's tile.
vec4 wfW1Cell(ivec2 wc, CloudEnv e) {
	if (e.w1) {
		return voidFetch(VOID_TILE_W1, wc) * vec4(1.0, 1.0, vec2(wcCellWeight(wc, e.w1Centre)));
	}
	return wcFallbackWeather((wc << 10) + 512 - e.w1Off, e);
}

// The highest a curtain reaches: into the base of the main deck (the low stratus lies under it).
float wfCurtainCeiling(CloudEnv e) {
	return e.layers.y + WF_CURTAIN_INTO_DECK;
}

// The base of the deck over camera-relative point q (blocks): the cloud map's column there, read at the ring the
// clouds draw that distance with.
float wfDeckBase(vec2 q, CloudEnv e) {
	float base = wcTowerBase(e);
#ifdef CLOUDS
	int lv = wcLevel(length(q));
	ivec4 m = wcMapCell(wcCellOf(q, lv, e), lv, e);
	bool low = (m.x & 1) != 0 && e.layers.x > WC_ABSENT * 0.5;
	bool main = (m.x & 2) != 0;
	base = low ? e.layers.x : (main ? e.layers.y : base);
#endif
	return base;
}

// The far ground under camera-relative point q (F0's valley floor), or sea level where the far tiles know nothing.
// fOk: F0 holds data (voidMode() != NONE and its tile bit); fCentre: F0's centre cell.
float wfFarGround(vec2 q, bool fOk, ivec2 fCentre) {
	if (!fOk) return HAZE_SEA_LEVEL;
	ivec2 cell = (cameraPositionInt.xz + ivec2(floor(cameraPositionFract.xz + q))) >> 10;
	vec4 f0 = voidFetch(VOID_TILE_F0, cell);
	float h = voidHeight(f0.r, f0.g);
	return (voidCellValid(cell, fCentre) && h > -1000.0) ? h : HAZE_SEA_LEVEL;
}

// The curtains along unit `dir` (eye-relative), up to tEnd (the land, or 1e9 for the sky) and tCloud (a nearer cloud,
// blocks). `stormOut`: the storm share of what was seen (debug and colour).
WfLayer wfCurtains(vec3 dir, float tEnd, float tCloud, CloudEnv e, out float stormOut) {
	WfLayer layer = wfNoLayer();
	stormOut = 0.0;
	float hk = length(dir.xz);
	// (without Gloam's W1 the fallback field rains only in vanilla rain: nothing to walk for otherwise)
	if (hk < 1e-3 || (!e.w1 && e.rainFb <= 0.0)) {
		return layer;
	}
	float camY = e.camY;
	float dy = dir.y;
	float ceiling = wfCurtainCeiling(e);
	// (the defects pass, 2026-10-16: the reach was held to DH's render distance, which VOID LOD reports small, so every
	// curtain past about 4-8 km was cut, and the storms the fronts hang 7-20 km out never showed. A curtain stands
	// under the clouds, which are drawn far past the land, and the walk stops at the land anyway: only a world drawn
	// without far land keeps a shorter reach, so curtains do not stand on the void.)
	float reach = WF_CURTAIN_REACH;
#ifndef DISTANT_HORIZONS
	reach = min(reach, 16384.0);
#endif

	// the stretch of ray between the floor and the ceiling, past the curtains' start, before the land and the clouds
	float tA = WF_CURTAIN_START / hk;
	float tB = min(min(tEnd, reach / hk), tCloud);
	if (abs(dy) > 1e-6) {
		float t1 = (ceiling - camY) / dy, t2 = (WF_WORLD_FLOOR - camY) / dy;
		tA = max(tA, min(t1, t2));
		tB = min(tB, max(t1, t2));
	} else if (camY > ceiling || camY < WF_WORLD_FLOOR) {
		return layer;
	}
	if (tB <= tA) {
		return layer;
	}
#if WF_DEBUG == 5
	wfDbgState = 1;
#endif

	// the 2D DDA over W1's cells, relative to the camera's own weather cell c0
	ivec2 c0;
	vec2 lo;
	wfW1Camera(e, c0, lo);
	vec2 d = dir.xz / 1024.0;
	vec2 dSafe = vec2(abs(d.x) < 1e-12 ? (d.x >= 0.0 ? 1e-12 : -1e-12) : d.x,
	                  abs(d.y) < 1e-12 ? (d.y >= 0.0 ? 1e-12 : -1e-12) : d.y);
	vec2 inv = 1.0 / dSafe;
	ivec2 stepDir = ivec2(dSafe.x > 0.0 ? 1 : -1, dSafe.y > 0.0 ? 1 : -1);
	ivec2 cell = ivec2(floor(lo + d * tA));
	vec2 tMax = (vec2(cell + max(stepDir, ivec2(0))) - lo) * inv;
	vec2 tDelta = abs(inv);

	bool fOk = e.mode != VOID_MODE_NONE && voidTileHasData(VOID_TILE_F0);
	ivec2 fCentre = fOk ? voidCentre(VOID_TILE_F0, cameraPositionInt.xz >> 10) : ivec2(0);
	float fall = mod(frameTimeCounter, 3600.0) * 5.0;   // the streaks drift down, 5 blocks a second
	float od = 0.0, stormSum = 0.0;
	bool first = true;
	float tFirst = 1e9;
	float tEnter = tA;

	for (int i = 0; i < WF_CURTAIN_STEPS; i++) {
		float tExit = min(min(tMax.x, tMax.y), tB);
		ivec2 wc = c0 + cell;
		vec4 w = wfW1Cell(wc, e);
		float rain = w.b;
		if (rain > WF_MIN_RAIN && tExit > tEnter) {
#if WF_DEBUG == 5
			wfDbgState = max(wfDbgState, 2);
#endif
			float storm = w.a;
			float h1 = wcU01(wcHash(wc, e.seed + 0x5F1u)), h2 = wcU01(wcHash(wc, e.seed + 0x5F2u));
			float h3 = wcU01(wcHash(wc, e.seed + 0x5F3u)), h4 = wcU01(wcHash(wc, e.seed + 0x5F4u));
			// the column: wider the harder it rains, set somewhere of its own in the cell
			vec2 size = clamp(vec2(0.30 + 0.45 * rain + 0.20 * storm) + 0.25 * (vec2(h1, h2) - 0.5), 0.2, 0.98);
			vec2 bmin = vec2(cell) + (1.0 - size) * vec2(h3, h4);
			vec2 bmax = bmin + size;
			vec2 ta = (bmin - lo) * inv, tb = (bmax - lo) * inv;
			vec2 tn = min(ta, tb), tf = max(ta, tb);
			// its top in the deck over its middle, its foot under the far ground
			vec2 centre = ((bmin + bmax) * 0.5 - lo) * 1024.0;
			float top = wfDeckBase(centre, e) + WF_CURTAIN_INTO_DECK;
			float bottom = min(wfFarGround(centre, fOk, fCentre), top - 40.0) - WF_CURTAIN_FOOT;
			float ty1 = -1e9, ty2 = 1e9;
			if (abs(dy) > 1e-6) {
				float a = (bottom - camY) / dy, b = (top - camY) / dy;
				ty1 = min(a, b);
				ty2 = max(a, b);
			} else if (camY < bottom || camY > top) {
				ty2 = -1e9;
			}
			float sideIn = max(tn.x, tn.y);
			float tIn = max(sideIn, max(ty1, tEnter));
			float tOut = min(min(tf.x, tf.y), min(ty2, tExit));
			// (seen from above through a gap in the deck, a column's top shows: the walk has already stopped at any cloud
			// in front, so a top the ray reaches is not under a cloud. It was hidden here, which kept every curtain off
			// the views from the peaks.)
			bool hidden = false;
#if WF_DEBUG == 5
			wfDbgState = max(wfDbgState, tOut > tIn && !hidden ? 4 : 3);
#endif
			if (tOut > tIn && !hidden) {
				float tm = 0.5 * (tIn + tOut);
				float vr = clamp((camY + dy * tm - bottom) / max(top - bottom, 1.0), 0.0, 1.0);
				// densest under the cloud, thinning toward the ground, easing in over the foot
				float profile = mix(0.6, 1.0, vr) * smoothstep(0.0, 0.08, vr);
				float hm = tm * hk;
				float fade = smoothstep(WF_CURTAIN_START, WF_CURTAIN_START + WF_CURTAIN_FADE_IN, hm)
					* (1.0 - smoothstep(0.8 * reach, reach, hm));
				float odi = (tOut - tIn) * WF_CURTAIN_DENSITY * rain * mix(1.0, 1.6, storm) * profile * fade;
				if (first && odi > 0.0) {
					// soft streaks on the nearest column, across its face in the weather's own frame (they travel with
					// it), drifting slowly down
					first = false;
					tFirst = tIn;
					// (the frame wrapped to W1's own 256 cells, so the floats stay exact anywhere in the world)
					vec2 hw = (vec2(c0 & 255) + lo + d * tIn) * 1024.0;
					float u = abs(tn.x - sideIn) < 1e-3 ? hw.y : hw.x;
					float yh = camY + dy * tIn;
					float streak = 0.65 * wfNoise2(vec2(u / 40.0, (yh + fall) / 160.0), e.seed + 0x5F5u)
						+ 0.35 * wfNoise2(vec2(u / 14.0, (yh + fall) / 70.0), e.seed + 0x5F6u);
					odi *= 0.55 + 0.9 * streak;
				}
				od += odi;
				stormSum += storm * odi;
			}
		}
		if (tExit >= tB || od > 3.0) {
			break;
		}
		if (tMax.x < tMax.y) {
			tEnter = tMax.x;
			tMax.x += tDelta.x;
			cell.x += stepDir.x;
		} else {
			tEnter = tMax.y;
			tMax.y += tDelta.y;
			cell.y += stepDir.y;
		}
	}

	layer.od = od;
	layer.t = tFirst;
#if WF_DEBUG == 5
	if (od > 0.0) wfDbgState = 5;
#endif
	if (od <= 0.0) {
		return layer;
	}
	float storm = stormSum / od;
	stormOut = storm;
	layer.alpha = WF_CURTAIN_ALPHA * (1.0 - exp(-od * mix(1.3, 1.9, storm)));

	// (the defects pass, 2026-10-16) the rain takes the colour of the deck's underside it falls from (the sky table's
	// cloud base, slate under a storm), a little darker inside the shaft, so a curtain reads as the cloud's own grey
	// reaching down to the land and a storm as a dark wall against the bright horizon. It was lit with the land's
	// linear light, which came out the haze's own pale grey and vanished into it. Toward a low sun the rain glows (the
	// light gets under the deck and scatters forward through the drops); lightning lights it from inside.
	vec3 l = lightDirWorld();
	float lowSun = 1.0 - smoothstep(0.10, 0.45, l.y);
	float forward = pow(clamp(dot(dir, l), 0.0, 1.0), 5.0) * mix(0.25, 0.7, lowSun) * (1.0 - 0.6 * storm);
	vec3 base = mix(skyCloud(SKY_CLOUD_BASE), skyCloud(SKY_CLOUD_STORM), smoothstep(0.1, 0.7, storm));
	vec3 c = base * mix(0.86, 0.62, storm);
	c += skyHorizon(true) * forward * (1.0 - skyNightStep());
	c += skyFlashLight() * (0.25 + 0.75 * storm) * 0.6;
	layer.color = c;
	return layer;
}

#endif
