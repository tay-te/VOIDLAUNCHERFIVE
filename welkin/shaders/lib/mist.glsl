// VOID Welkin: valley mist at dawn (docs/DESIGN.md 3.4), for composite.
//
// The mist is a flat slab lying in the hollows. VOID Gloam's near land tiles (N0, N1: 16 m cells, 4 km round the
// camera) hold each place's ground, lowness, water distance and mist potential; Gloam's void_mistTime holds the
// hour's and season's part. At a place the mist is potential x mistTime. Where it lies is set by the land's relief,
// not by the mist alone. A place's "pool" weight comes from how low it lies under the land round it (Expanse's
// lowness: 0 level with its surroundings, 1 at 24 blocks or more below them) and from water close by, and fades out
// where the ground stands more than a few blocks over its basin's floor (the lowest ground 64 to 128 m round it) or a
// few dozen over its region's (256 to 512 m round), so a gully high on a mountain, low only against its own sides,
// gets none, and neither does the gently rising land round a pond. The slab's top is the lowest ground nearby + 1 + (3 + 24 x mist) x pool, capped at the basin floor + 2 +
// 26 x mist so it lies level; its density is mist x pool, cut to nothing where mist x pool is under a floor, so the
// morning's thinning shrinks the slab back into the hollows. "Lowest nearby" is a soft minimum over the place's four
// nearest cells, weighted toward the lowest, so it pools without stepping at cell edges; the cap on the basin floor,
// read 64 to 128 m round, keeps the slab level across a wider hollow.
//
// No marching: the slab is sampled at MIST_SAMPLES places along the view ray (the camera's included), its top and
// density run straight between them, and the ray meets each straight piece exactly. The optical depth comes out
// whole, then is cut into four flat opacity steps (the top one still lets the land show through), each flat, with a
// 4x4 Bayer dither only in a narrow band at each step's edge. The dither lies on the world's 16-pixel grid at the
// surface the ray ends on (1/16 block near by, doubling with distance and on grazing faces so a texel never shrinks
// under MIST_DITHER_PIXELS screen pixels); only the sky and vanilla's clouds, which are no ground, are dithered in
// screen space. Its colour is a small flat palette: a light cool white by day and from first light, warming toward a
// low sun in three steps with the horizon's hue, dark blue-grey at night; the two thin steps lean toward the sky's
// colour on that side.
//
// The near tiles reach to the LOD's land as well (their 4 km), and fade out over their last 16 cells. Without Gloam,
// or before the tiles are filled, there is no mist at all.

#ifndef WELKIN_MIST
#define WELKIN_MIST

#include "/lib/common.glsl"
#include "/lib/void.glsl"
#include "/lib/depth.glsl"
#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/fade.glsl"
#include "/lib/water.glsl"

#ifndef WELKIN_GBUFFER_SAMPLERS
#define WELKIN_GBUFFER_SAMPLERS
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif

// How sharply the soft minimum of the ground round a place leans to the lowest of its four cells (per block).
#define MIST_POOL_SHARPNESS 0.2
// The basin floor: the lowest ground among the four nodes round a place of a coarser lattice (every MIST_BASIN cells,
// 128 m: in full within 64 m, fading out to 128 m). Fading nodes count raised by up to MIST_BASIN_RAISE blocks, so the
// floor runs on without a step as the place moves.
#define MIST_BASIN 8
#define MIST_BASIN_RAISE 40.0
// The region's floor the same way, every MIST_REGION cells (512 m: in full within 256 m, fading out to 512 m). Ground more than
// HIGH_FROM blocks over it pools less mist, none from HIGH_TO: no slab in the high valleys and gullies of a range.
#define MIST_REGION 32
#define MIST_REGION_RAISE 120.0
#define MIST_HIGH_FROM 24.0
#define MIST_HIGH_TO 56.0
// The pool weight from Expanse's lowness (0..1 over 0..24 blocks under the land round a place), read from the place's
// own four 16 m cells: none below FROM, full from FULL. Water closer than WATER_NEAR blocks (fading out by WATER_FAR)
// pools some mist too, on flat land along rivers and lakes, but only on ground within WATER_RISE_FROM..TO blocks of the
// basin floor (the water's own level).
#define MIST_LOW_FROM 0.08
#define MIST_LOW_FULL 0.65
#define MIST_WATER_NEAR 12.0
#define MIST_WATER_FAR 48.0
#define MIST_WATER_POOL 0.6
#define MIST_WATER_RISE_FROM 1.5
#define MIST_WATER_RISE_TO 5.0
// Ground standing higher than RISE_FROM blocks over its basin floor pools less mist, and none from RISE_TO: a gully high
// on a mountain is low against its own sides (Expanse's lowness is local) but far above the valley it drains to.
#define MIST_RISE_FROM 3.0
#define MIST_RISE_TO 12.0
// The slab's top: the lowest ground nearby + 1 + (LIFT + GAIN x mist) x pool, and never more than CAP_LIFT + CAP_GAIN x
// mist over the basin floor, so it lies level in the hollow instead of climbing the slopes round it.
#define MIST_LIFT 3.0
#define MIST_GAIN 24.0
#define MIST_CAP_LIFT 2.0
#define MIST_CAP_GAIN 26.0
// Below FLOOR (the place's potential x the hour's mistTime) there is no mist; full from twice that. As the morning thins
// the mist, the slab shrinks back into the hollows instead of thinning into speckle over the whole land.
#define MIST_FLOOR 0.08
// Opacity per block of mist at full strength (1 - exp(-this x blocks)); 20 blocks of full mist cover 83 %.
#define MIST_EXTINCTION 0.09
// The four steps' opacities. The first is a faint veil in a colour near the sky's, the top one leaves the land under it
// readable (a river still shows through a full canyon).
#define MIST_STEP1 0.18
#define MIST_STEP2 0.38
#define MIST_STEP3 0.56
#define MIST_STEP4 0.72
// How wide the dithered band at each step's edge is, in steps (the rest of each step is flat).
#define MIST_BAND 0.3
// The smallest a dither texel of the mist may be on screen, in pixels.
#define MIST_DITHER_PIXELS 2.0

struct MistTiles {
	bool data;      // the near tiles hold Gloam's data
	bool on;        // and there is mist this hour
	float time;     // void_mistTime (or MIST_FORCE)
	ivec2 camCell;  // the camera's 16 m cell
	ivec2 centre;   // the near tiles' centre cell
};

MistTiles mistTiles() {
	MistTiles t;
	t.camCell = cameraPositionInt.xz >> 4;
	t.centre = t.camCell;
	t.time = 0.0;
	t.data = false;
	int mode = voidMode();
	if (mode != VOID_MODE_NONE && voidTileHasData(VOID_TILE_N0) && voidTileHasData(VOID_TILE_N1)) {
		t.data = true;
		t.centre = voidCentre(VOID_TILE_N0, t.camCell);
		t.time = MIST_FORCE > 0 ? float(MIST_FORCE) / 100.0 : voidMistTime(mode);
	}
	t.on = t.data && t.time > 0.004;
	return t;
}

float mistCameraY() {
	return float(cameraPositionInt.y) + cameraPositionFract.y;
}

// The camera's position in cells, from the corner of cell `base`.
vec2 mistCells(vec2 eyeXZ, ivec2 camCell, ivec2 base) {
	return (vec2(cameraPositionInt.xz & 15) + cameraPositionFract.xz + eyeXZ) / 16.0 + vec2(camCell - base);
}

struct MistPoint {
	float top;     // the slab's top, camera-relative y in blocks
	float density; // mist 0..1, faded toward the tiles' edge
	float pool;    // how readily mist gathers here, 0..1 (for the debug view)
	float rise;    // how high the ground stands over its basin's floor, blocks (for the debug view)
};

// The lowest ground round a place (`cells` from the corner of cell `base`) among the four nodes of a lattice every
// `step` cells round it, anchored to world cells (step a power of two). A node counts in full while the place is
// within half a lattice step of it on each axis, and is raised by up to `raise` blocks as it falls to a whole step
// away (where it leaves the footprint), so the floor runs on without a step as the place moves. Returns 1e9 when no
// node holds data.
float mistFloor(vec2 cells, ivec2 base, MistTiles t, int step, float raise) {
	ivec2 bb = t.camCell & ~(step - 1);
	vec2 q = (cells + vec2(base - bb) - 0.5) / float(step);
	ivec2 n = ivec2(floor(q));
	vec2 f = q - vec2(n);
	float lowest = 1e9;
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i & 1, i >> 1);
		ivec2 c = bb + (n + o) * step;
		vec2 d = mix(f, 1.0 - f, vec2(o));
		vec2 w2 = clamp((1.0 - d) * 2.0, 0.0, 1.0);
		float w = w2.x * w2.y;
		if (w <= 0.0 || !voidCellValid(c, t.centre)) {
			continue;
		}
		vec4 n0 = voidFetch(VOID_TILE_N0, c);
		float g = voidHeight(n0.r, n0.g);
		// a cell not filled yet reads as the bottom of the height range
		if (g > -1000.0) {
			lowest = min(lowest, g + raise * (1.0 - w));
		}
	}
	return lowest;
}

// The slab at an eye-relative horizontal point.
MistPoint mistAt(vec2 eyeXZ, MistTiles t) {
	MistPoint m;
	m.top = -1e4;
	m.density = 0.0;
	m.pool = 0.0;
	m.rise = 0.0;
	// the lattices are anchored to world cells, so they do not shift as the camera crosses a cell
	ivec2 base = t.camCell;
	vec2 cells = mistCells(eyeXZ, t.camCell, base);

	// The place itself, bilinear over its four nearest 16 m cells: its ground, mist potential, lowness and water.
	vec2 p0 = cells - 0.5;
	ivec2 c0 = ivec2(floor(p0));
	vec2 f0 = p0 - vec2(c0);
	float wp = 0.0, sp = 0.0, sLow = 0.0, sWater = 0.0, sh = 0.0;
	float g[4];
	float b[4];
	float lowest = 1e9;
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i & 1, i >> 1);
		ivec2 c = base + c0 + o;
		vec4 n0 = voidFetch(VOID_TILE_N0, c);
		vec4 n1 = voidFetch(VOID_TILE_N1, c);
		int fl = voidFlags(n1.a);
		b[i] = 0.0;
		g[i] = 0.0;
		if (voidCellValid(c, t.centre) && (fl & 1) != 0) {
			b[i] = (o.x == 1 ? f0.x : 1.0 - f0.x) * (o.y == 1 ? f0.y : 1.0 - f0.y);
			g[i] = voidHeight(n0.r, n0.g);
			lowest = min(lowest, g[i]);
			wp += b[i];
			sh += b[i] * g[i];
			sp += b[i] * n1.r;
			// how readily mist gathers here: low under the land round it, or by water (N0 alpha: 4 m steps)
			sLow += b[i] * smoothstep(MIST_LOW_FROM, MIST_LOW_FULL, n0.b);
			sWater += b[i] * ((fl & 8) != 0 ? 1.0 : 1.0 - smoothstep(MIST_WATER_NEAR, MIST_WATER_FAR, n0.a * 1020.0));
		}
	}
	if (wp < 1e-6) {
		return m;
	}
	float here = sh / wp;
	float fill = clamp(wp * 2.0, 0.0, 1.0);
	float mist = clamp(sp / wp, 0.0, 1.0) * t.time;
	float low = sLow / wp;
	float wet = sWater / wp;
	// level land with nothing lower round it gets a 1-block floor layer of no density: nothing to see. Most land is
	// such land, so it stops here, before the lattices are read (the rise below only ever lowers the pool).
	m.top = here + 1.0 - mistCameraY();
	if (max(low, MIST_WATER_POOL * wet) * mist < MIST_FLOOR) {
		return m;
	}

	// The lowest ground nearby: a soft minimum over the same four cells, the bilinear weight times a lean toward the
	// lowest (continuous, since a cell leaves the footprint with its weight at 0). The basin's floor below holds the
	// slab level over a wider hollow.
	float wg = 0.0, sg = 0.0;
	for (int i = 0; i < 4; i++) {
		float w = b[i] * exp(-MIST_POOL_SHARPNESS * (g[i] - lowest));
		wg += w;
		sg += w * g[i];
	}
	float ground = min(wg > 1e-6 ? sg / wg : here, here);
	// the basin's floor (64 to 128 m round) and the region's (256 to 512 m round), never above the nearby ground
	float basin = min(mistFloor(cells, base, t, MIST_BASIN, MIST_BASIN_RAISE), ground);
	// how high the place stands over its basin's floor
	float rise = here - basin;
	m.rise = rise;
	float water = wet * (1.0 - smoothstep(MIST_WATER_RISE_FROM, MIST_WATER_RISE_TO, rise));
	float pool = clamp(max(low, MIST_WATER_POOL * water) * (1.0 - smoothstep(MIST_RISE_FROM, MIST_RISE_TO, rise)), 0.0, 1.0);
	if (pool * mist < MIST_FLOOR) {
		return m;
	}
	// and over the region's
	float high = here - min(mistFloor(cells, base, t, MIST_REGION, MIST_REGION_RAISE), basin);
	m.rise = max(rise, high - MIST_HIGH_FROM);
	pool *= 1.0 - smoothstep(MIST_HIGH_FROM, MIST_HIGH_TO, high);
	float top = ground + 1.0 + (MIST_LIFT + MIST_GAIN * mist) * pool;
	top = min(top, basin + MIST_CAP_LIFT + MIST_CAP_GAIN * mist);
	// at the floor the slab has no density and sits 1 block over the place, as where the reading stopped early above
	float floorCut = smoothstep(MIST_FLOOR, 2.0 * MIST_FLOOR, mist * pool);
	m.top = mix(here + 1.0, top, floorCut) - mistCameraY();
	m.pool = pool;
	// fade over the tiles' last 16 cells, and where only some cells hold data yet (a fill in progress)
	vec2 rel = cells + vec2(base - t.centre) - 0.5;
	float edge = clamp((127.0 - max(abs(rel.x), abs(rel.y))) / 15.0, 0.0, 1.0);
	m.density = mist * pool * floorCut * edge * fill;
	return m;
}

struct MistRay {
	float depth; // density x blocks inside the slab
	float enter; // the first distance along the ray inside it
	float exit;  // the last
};

// The ray from ta to tb, the slab's top and density running straight from `a` to `b`; `dy` the ray's rise per block.
void mistSegment(inout MistRay r, float ta, float tb, MistPoint a, MistPoint b, float dy) {
	if (tb <= ta) {
		return;
	}
	// how far below the top the ray is at each end: straight in between, so where it crosses is exact
	float ga = a.top - dy * ta;
	float gb = b.top - dy * tb;
	if (ga <= 0.0 && gb <= 0.0) {
		return;
	}
	float u0 = ta, u1 = tb;
	if (ga < 0.0 || gb < 0.0) {
		float tc = ta + (tb - ta) * ga / (ga - gb);
		if (ga < 0.0) {
			u0 = tc;
		} else {
			u1 = tc;
		}
	}
	float k = 1.0 / (tb - ta);
	float m0 = mix(a.density, b.density, (u0 - ta) * k);
	float m1 = mix(a.density, b.density, (u1 - ta) * k);
	r.depth += (u1 - u0) * 0.5 * (m0 + m1);
	r.enter = min(r.enter, u0);
	r.exit = max(r.exit, u1);
}

// How far the ray along `dir` (eye-relative) stays over the near tiles, in blocks.
float mistTileReach(vec3 dir, MistTiles t) {
	vec2 origin = mistCells(vec2(0.0), t.camCell, t.centre);
	vec2 lo = (vec2(-127.0) - origin) * 16.0;
	vec2 hi = (vec2(128.0) - origin) * 16.0;
	float reach = 1e7;
	if (abs(dir.x) > 1e-5) {
		reach = min(reach, (dir.x > 0.0 ? hi.x : lo.x) / dir.x);
	}
	if (abs(dir.z) > 1e-5) {
		reach = min(reach, (dir.z > 0.0 ? hi.y : lo.y) / dir.z);
	}
	return max(reach, 0.0);
}

// The mist's colour looking along `dir`, for opacity step `level` (1..4), in flat steps (`d` the dither threshold).
// Mist is lit water: from first light (before the land's own day light is up) it is a light cool white, so the dawn mist
// lifts the scene rather than greying it. Toward a low sun it warms in three steps, its hue from the horizon (the sky's
// lowest band, fogColor, until M2's sky table) mixed with a fixed warm white and kept at full brightness, so a dark fog
// colour cannot darken it. Dark blue-grey at night. The two thin steps lean toward the sky's own colour on that side
// (warm toward the sun, cool blue away from it) at the mist's brightness, so a thin veil reads as air, not as frost.
vec3 mistColor(vec3 dir, int level, float d) {
	float sh = sunHeight();
	float light = clamp(sh * 5.0 + 0.6, 0.0, 1.0);
	float low = 1.0 - clamp(sh * 3.0, 0.0, 1.0);
	vec3 dayMist = mix(vec3(0.88, 0.91, 0.95), vec3(0.80, 0.84, 0.92), low);
	vec3 nightMist = vec3(0.12, 0.14, 0.20);
	vec3 c = mix(nightMist, dayMist, light);
	// toward a low sun the mist warms, in three flat steps
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	float toward = clamp((dot(dir, sunW) - 0.25) / 0.75, 0.0, 1.0);
	float side = steps(toward * toward, 3.0, d);
	vec3 hue = fogColor / max(max(fogColor.r, max(fogColor.g, fogColor.b)), 0.05);
	vec3 warm = mix(vec3(1.00, 0.85, 0.68), hue * 0.98, 0.35);
	c = mix(c, warm, side * low * light * 0.65);
	if (level <= 2) {
		float bright = max(c.r, max(c.g, c.b));
		vec3 sky = mix(vec3(0.62, 0.72, 0.88), mix(hue, warm, 0.5), side * low) * bright;
		c = mix(c, sky, level == 1 ? 0.6 : 0.3);
	}
	return c * mix(1.0, 0.8, rainStrength);
}

// The opacity step (0..4) for a cover in [0, 1]: four flat steps, dithered (`d` the threshold) only in a band
// MIST_BAND steps wide round each step's edge, so a wide stretch of thin mist is one flat faint step, not a checker.
int mistLevel(float cover, float d) {
	float x = cover * 4.0;
	float edge = floor(x + 0.5);
	float u = clamp((x - edge) / MIST_BAND + 0.5, 0.0, 1.0);
	return int(clamp(edge - 1.0 + (u > d ? 1.0 : 0.0), 0.0, 4.0));
}

float mistOpacity(int level) {
	const float o[5] = float[5](0.0, MIST_STEP1, MIST_STEP2, MIST_STEP3, MIST_STEP4);
	return o[level];
}

// Whether vanilla's clouds drew this pixel (gbuffers_clouds marks colortex2 with MAT_CLOUD): their depth is no ground.
bool mistIsCloud(vec4 flags) {
	return flags.x < 0.5 && int(flags.y * 255.0 + 0.5) == MAT_CLOUD;
}

vec3 valleyMist(ivec2 p, vec2 uv, vec3 scene) {
	if (isEyeInWater != 0) {
		return scene;
	}
	MistTiles t = mistTiles();
	if (!t.on) {
		return scene;
	}
	Surface s = surfaceAt(p, uv);
	vec3 eye = mat3(gbufferModelViewInverse) * s.view;
	float dist = length(eye);
	vec3 dir = eye / max(dist, 1e-4);

	// Only where the sky reaches: land the sky light does not reach (caves, under overhangs) gets none. Water and the
	// sky are open by definition (a deep lake's bed has little sky light under the water).
	bool water = texelFetch(colortex3, p, 0).a > 0.0;
	vec4 flags = texelFetch(colortex2, p, 0);
	vec4 g = texelFetch(colortex1, p, 0);
	// Vanilla's clouds stand far over any slab: the ray to one is treated as a ray to the sky, ending at the cloud.
	bool cloud = !s.sky && mistIsCloud(flags);
	// Under a canopy (sky light a step or more under full) the forest floor takes less mist, so the woods do not
	// fill with frost-white speckle between the trunks.
	float open = 1.0;
	if (!s.sky && !water && flags.x > 0.5) {
		open = clamp((g.w - 0.7) / 0.25, 0.0, 1.0);
		if (open <= 0.0) {
			return scene;
		}
	}

	float end = min(s.sky ? 1e7 : dist, mistTileReach(dir, t));
	if (end <= 0.0) {
		return scene;
	}
	MistRay r;
	r.depth = 0.0;
	r.enter = 1e9;
	r.exit = 0.0;
	MistPoint a = mistAt(vec2(0.0), t);
	// A ray to the sky or a cloud that climbs from over the camera's own slab meets no mist (slabs lie in hollows,
	// under the camera that looks out over them): half the screen saves its samples.
	if ((s.sky || cloud) && dir.y > 0.0 && a.top < 0.0) {
		return scene;
	}
#if MIST_SAMPLES >= 4
	const float f1 = 0.3, f2 = 0.7;
#elif MIST_SAMPLES == 3
	const float f1 = 0.5;
#endif
#if MIST_SAMPLES >= 3
	float t1 = end * f1;
	MistPoint b1 = mistAt(dir.xz * t1, t);
	mistSegment(r, 0.0, t1, a, b1, dir.y);
	a = b1;
#else
	float t1 = 0.0;
#endif
#if MIST_SAMPLES >= 4
	float t2 = end * f2;
	MistPoint b2 = mistAt(dir.xz * t2, t);
	mistSegment(r, t1, t2, a, b2, dir.y);
	a = b2;
	t1 = t2;
#endif
	MistPoint e = mistAt(dir.xz * end, t);
	mistSegment(r, t1, end, a, e, dir.y);
	// The slab right over the surface the ray ends on, met exactly as a level slab with that place's own top and
	// density. The straight pieces between samples cut across a narrow hollow (a canyon between two samples on its rims
	// has its top dragged down to theirs), so the ray's path through this last slab counts at least this much.
	float below = e.top - dir.y * end;
	if (below > 0.0 && e.density > 0.0) {
		float inside = dir.y < -1e-4 ? below / -dir.y : end;
		r.depth = max(r.depth, min(inside, end - t1) * e.density);
	}
	if (r.depth <= 0.0) {
		return scene;
	}
	float cover = 1.0 - exp(-r.depth * MIST_EXTINCTION * MIST_THICKNESS * open);

	// The dither lies on the world's 16-pixel grid at the surface the ray ends on (the land, the water's surface or
	// the LOD's land), so the steps' edges read as texture on the blocks: 1/16 block near by, doubling with distance.
	// A grazing face counts its texels as smaller on screen, so the grid coarsens there before it can break into a
	// screen-pixel checker. The sky has no world position: there the dither is the screen's.
	float d = 0.5;
#ifdef DITHER
	if (s.sky || cloud) {
		d = bayer4(p);
	} else {
		vec3 n = vec3(0.0, 1.0, 0.0);
		if (!water && flags.x > 0.5) {
			n = octDecode(g.xy);
		}
		float bpp = blocksPerPixelAt(max(dist, 0.05), gbufferProjection[1][1], viewHeight) / max(abs(dot(n, dir)), 0.25);
		d = worldDither(eye, n, gridLevel(bpp, MIST_DITHER_PIXELS));
	}
#endif
	int level = mistLevel(cover, d);
	if (level == 0) {
		return scene;
	}
	return mix(scene, mistColor(dir, level, d), mistOpacity(level));
}

// DEBUG_VIEW 7: the near tiles projected onto the land. Red: mist potential; green: the pool weight the mist uses
// (lowness and water, cut by the rise over the basin floor); blue: that rise (full at MIST_RISE_TO blocks). Cells not filled yet: a dark checker; outside the tiles: dim red. Magenta: the tiles' centre
// cell; yellow: the camera's cell; orange and red: the 112- and 127-cell rings where the mist fades out. White
// hatching where the land lies under the mist's top this hour and the mist there is not nil (with MIST_FORCE if
// set). Cell edges are lined near by. The bridge status square sits top left, as in view 6.
vec3 mistDebugTiles(ivec2 p, vec2 uv, vec3 scene) {
	int mode = voidMode();
	if (p.x < 48 && int(viewHeight) - 1 - p.y < 48) {
		return mode == VOID_MODE_FULL ? vec3(0.1, 0.9, 0.2) : (mode == VOID_MODE_HEADER ? vec3(1.0, 0.85, 0.1) : vec3(0.95, 0.1, 0.1));
	}
	vec3 grey = vec3(dot(scene, vec3(0.30, 0.59, 0.11)));
	MistTiles t = mistTiles();
	Surface s = surfaceAt(p, uv);
	if (!t.data || s.sky || mistIsCloud(texelFetch(colortex2, p, 0))) {
		return grey * 0.4;
	}
	vec3 eye = mat3(gbufferModelViewInverse) * s.view;
	vec2 local = mistCells(eye.xz, t.camCell, t.camCell);
	ivec2 cell = t.camCell + ivec2(floor(local));
	vec2 f = fract(local);
	ivec2 dc = abs(cell - t.centre);
	int cheb = max(dc.x, dc.y);
	vec3 c;
	if (cheb > 127) {
		c = mix(grey * 0.3, vec3(0.5, 0.0, 0.0), 0.5);
	} else {
		vec4 n0 = voidFetch(VOID_TILE_N0, cell);
		vec4 n1 = voidFetch(VOID_TILE_N1, cell);
		int fl = voidFlags(n1.a);
		if ((fl & 1) == 0) {
			c = ((cell.x + cell.y) & 1) == 0 ? vec3(0.12) : vec3(0.03);
		} else {
			MistPoint m = mistAt(eye.xz, t);
			c = vec3(n1.r, m.pool, clamp(m.rise / float(MIST_RISE_TO), 0.0, 1.0));
			if (eye.y < m.top && m.density > 0.02 && ((p.x + p.y) & 7) < 2) {
				c = mix(c, vec3(1.0), 0.7);
			}
		}
		if (cheb == 112) {
			c = vec3(1.0, 0.55, 0.1);
		} else if (cheb == 127) {
			c = vec3(0.9, 0.1, 0.1);
		}
		if (cell == t.camCell) {
			c = vec3(1.0, 0.9, 0.1);
		}
		if (cell == t.centre) {
			c = vec3(1.0, 0.0, 1.0);
		}
		// cell edges, while a cell spans a good few pixels
		if (blocksPerPixelAt(length(eye), gbufferProjection[1][1], viewHeight) < 1.0) {
			vec2 e = min(f, 1.0 - f);
			if (min(e.x, e.y) < 0.03) {
				c *= 0.45;
			}
		}
	}
	return mix(c, grey, 0.15);
}

#endif
