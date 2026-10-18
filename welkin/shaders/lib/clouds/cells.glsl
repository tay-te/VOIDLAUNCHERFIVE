// VOID Welkin: the cloud cells. One function, wcColumn, says what stands in a column of sky: which decks have a cell
// there, how tall a tower rises through them, whether an anvil spreads at the top, and in which tone. The march, the
// cloud shadows, the far rain curtains and water reflections all ask it, so they always agree.
//
// The rules are Gloam's CloudGrid (the GLSL reference it was kept for), with the weather read from the W tiles:
//   - deck cells are 12 x 12 blocks (vanilla's), doubling in width each ring out (wcLevel); decks are 4 blocks thick;
//   - a deck cell is on when its "puffs" value (up to three octaves of hashed value noise on the frame, seeded by
//     void_seed, octaves finer than 2.2 cells left out) clears a threshold set by the cover: (1 - cover) * 0.92 + 0.04
//     for the main deck, so a fair sky has scattered puffs and an overcast all but a few holes;
//   - the low stratus thickens with rain and wet fronts, the high deck spreads ahead of a front;
//   - towers stand on tower cells of 4 x 4 deck cells (48 m near): where showers or a storm are strong enough and a
//     swell field (1.5 km) is high, they rise from the lowest deck in 20-block steps, as tall as the storm is strong,
//     and a storm spreads an anvil under the tower top over its whole footprint;
//   - the tone (fair, showers, storm) picks the palette.
// Decks read W0 (128 m) out to about 10 km, then W1 (1 km), switching cell by cell on a hash across 2 km so the seam
// is a scatter of whole cells, not a line. Towers read W1 everywhere: storms are kilometres wide, and a 1 km bound is
// what lets the march jump the gaps between decks.
//
// W channels (contract 2.3): R front, G cover (after Gloam's humidity and rain-shadow bias), B rain incl. showers,
// A storm.
//
// Written for the GPU's sake: no caches held across the march (texel fetches are cheaper than the registers), integer
// divisions only by constants or as shifts, no dynamically indexed arrays.

#ifndef WELKIN_CLOUD_CELLS
#define WELKIN_CLOUD_CELLS

#include "/lib/clouds/env.glsl"

#define WC_CELL 12           // vanilla's cell, blocks
// (WC_DECK, a deck's first 4 blocks: lib/clouds/env.glsl)
#define WC_TOWER_STEP 20.0   // towers rise in steps this tall
// (WC_TIER, WC_MAIN_TIERS, WC_LOW_TIERS: the decks' tiers, in lib/clouds/env.glsl)
#define WC_ANVIL 0.55        // the storm at which an anvil spreads (CloudGrid)
#define WC_MAX_LEVEL 7       // the coarsest ring: 12 << 7 = 1536 m cells
#define WC_W0_REACH 10000.0  // decks read W0 this far, then W1 across 2 km
#define WC_TOWER_NEAR (6.0 * WC_RING0)   // towers stand whole this far (2.3 km at MEDIUM) ..
#define WC_TOWER_FAR (14.0 * WC_RING0)   // .. and have sunk into their deck by here (5.4 km)

const vec2 WC_EMPTY = vec2(1.0, 0.0);

struct CloudCol {
	vec2 low, main, high;  // the deck cells' y intervals (WC_EMPTY when off)
	vec2 tower;            // the tower's interval
	vec2 anvil;            // the anvil's interval
	int tone;              // 0 fair, 1 showers, 2 storm
	vec3 dens;             // how thick each deck's cell is (low, main, high), 0 a ragged edge cell .. 1 deep inside
	vec2 field;            // the puffs' margin over the threshold (low, main): 0.5 at the outline, 1 deep inside, 0 far
	                       // out in the open; written smooth into the raw map for the shading (wcField)
	ivec2 tiers;           // the tiers over the first 4 blocks (low, main)
};

// The extra height of a deck's top over its first WC_DECK blocks, at most.
float wcDeckSpan(int k) {
	return WC_DECK + (k == 1 ? WC_MAIN_HEIGHT : (k == 0 ? float(WC_LOW_TIERS) * WC_TIER : 0.0));
}


// The last tower cell asked about: a tower cell spans 4 x 4 deck columns, so most asks repeat.
struct TCache {
	ivec3 key;   // tower cell, ring
	float top;   // the tower's top (below its base when there is none)
	bool anvil;
	int tone;
};

TCache wcTCacheNew() {
	TCache t;
	t.key = ivec3(0x7FFFFFFF);
	t.top = -1e9;
	t.anvil = false;
	t.tone = 0;
	return t;
}

// ------------------------------------------------------------------------------------------------- hashing

uint wcHash(ivec2 c, uint salt) {
	uint h = uint(c.x) * 0x8DA6B343u ^ uint(c.y) * 0xD8163841u ^ salt * 0x9E3779B9u;
	h ^= h >> 15u;
	h *= 0x2C1B3C6Du;
	h ^= h >> 12u;
	h *= 0x297A2D39u;
	h ^= h >> 15u;
	return h;
}

float wcU01(uint h) {
	return float(h >> 8u) * (1.0 / 16777216.0);
}

// (clouds2) How high a main-deck cloud stands over its first WC_DECK blocks, 0..1 of its tiers, by how deep inside it a
// point lies: a heap, steep at its flanks and full over most of its body (a cumulus is as tall as it is wide), not a
// gentle terrace that only rises at the very heart (that drew every cloud as a thin slab sheared long in perspective).
float wcMainRise(float dens) {
	return sqrt(clamp(dens, 0.0, 1.0));
}

// A cell's tiers from its thickness `dens` and its hash: none at a ragged rim, `most` deep inside.
int wcTiersOf(float dens, uint h, int most) {
	float f = dens * (float(most) + 0.7) + (wcU01(h * 0x2C1B3C6Du + 11u) - 0.5) * 0.4 - 0.1;
	return clamp(int(floor(f)), 0, most);
}

// Value noise with its lattice every `w` blocks (a constant at every call, so the division is cheap), at whole frame
// block `m`.
float wcValue(ivec2 m, int w, uint salt) {
	ivec2 l = wcFloorDiv(m, w);
	vec2 f = vec2(m - l * w) / float(w);
	f = f * f * (3.0 - 2.0 * f);
	float a = wcU01(wcHash(l, salt));
	float b = wcU01(wcHash(l + ivec2(1, 0), salt));
	float c = wcU01(wcHash(l + ivec2(0, 1), salt));
	float d = wcU01(wcHash(l + ivec2(1, 1), salt));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// CloudGrid.puffs: octaves w0 > w1 > w2 (each 0.62 of the last, w2 0 for none), an octave finer than 2.2 cells of
// size `s` left out (the first always kept), stretched to fill 0..1. Each deck calls it with its own constant
// wavelengths (wcPuffsMain ...), so the lattice divisions stay divisions by constants once inlined.
float wcPuffs(ivec2 m, int s, int w0, int w1, int w2, uint salt) {
	float sum = wcValue(m, w0, salt);
	float total = 1.0;
	if (float(w1) >= 2.2 * float(s)) {
		sum += 0.62 * wcValue(m, w1, salt + 0x632BE5ABu);
		total += 0.62;
		if (w2 > 0 && float(w2) >= 2.2 * float(s)) {
			sum += 0.3844 * wcValue(m, w2, salt + 0xC657CB56u);
			total += 0.3844;
		}
	}
	return clamp((sum / total - 0.5) * 2.6 + 0.5, 0.0, 1.0);
}

float wcSmooth(float a, float b, float x) {
	float t = clamp((x - a) / (b - a), 0.0, 1.0);
	return t * t * (3.0 - 2.0 * t);
}

// 4x4 Bayer threshold in (0, 1), the same matrix as lib/common.glsl's bayer4, by bits instead of a table.
float wcBayer(ivec2 p) {
	int x = p.x & 3, y = p.y & 3;
	int lo = ((x ^ y) & 1) << 1 | (y & 1);
	int hi = (((x >> 1) ^ (y >> 1)) & 1) << 1 | ((y >> 1) & 1);
	return (float(lo * 4 + hi) + 0.5) / 16.0;
}

// ------------------------------------------------------------------------------------------------- rings and cells

// The ring a point `d` blocks out (horizontally) lies in: 0 inside WC_RING0, then one more each doubling.
int wcLevel(float d) {
	return d < WC_RING0 ? 0 : min(int(log2(d / WC_RING0)) + 1, WC_MAX_LEVEL);
}

// The frame cell of ring `lv` (12 << lv blocks) under camera-relative point `q`, by shifts from the camera's cell.
ivec2 wcCellOf(vec2 q, int lv, CloudEnv e) {
	ivec2 b = e.frame12 >> lv;
	vec2 r = vec2(((e.frame12 - (b << lv)) * WC_CELL) + e.frameRem) + e.frameFract + q;
	return b + ivec2(floor(r / float(WC_CELL << lv)));
}

// A frame cell's corner, camera-relative, for cells `s` blocks across.
vec2 wcCellCorner(ivec2 cell, int s, CloudEnv e) {
	return vec2(cell * s - e.frameInt) - e.frameFract;
}

// The Bayer threshold on the clouds' own 16-pixel grid (lib/grid.glsl's worldTexel, laid on the drifting frame
// instead of the world, so the pattern rides with the cloud) at camera-relative point `p` of a face with normal `n`,
// at grid `level`. The point is pulled half a texel inside the face.
float wcDither(vec3 p, vec3 n, int level, CloudEnv e) {
	float texel = exp2(float(level)) / 16.0;
	vec3 local = vec3(e.frameFract.x, cameraPositionFract.y, e.frameFract.y) + p - n * (0.5 * texel);
	ivec3 base = ivec3(e.frameInt.x, cameraPositionInt.y, e.frameInt.y);
	ivec3 c;
	if (level <= 4) {
		int sc = 16 >> level;
		c = ivec3(floor(local * float(sc))) + base * sc;
	} else {
		int k = min(level - 4, 20);
		ivec3 mask = ivec3((1 << k) - 1);
		c = ivec3(floor((local + vec3(base & mask)) / float(1 << k))) + (base >> k);
	}
	return wcBayer(faceAxes(c, n));
}

// ------------------------------------------------------------------------------------------------- weather

// Tile `tile` (cells 2^shift blocks) at frame block `m`, bilinear from four fetches; `valid` the fade weight toward
// the tile's edge.
vec4 wcTile(int tile, int shift, ivec2 m, ivec2 centre, out float valid) {
	int size = 1 << shift;
	ivec2 u = m - size / 2;
	ivec2 i = u >> shift;
	vec2 f = vec2(u - (i << shift)) / float(size);
	valid = min(wcCellWeight(i, centre), wcCellWeight(i + 1, centre));
	vec4 a = voidFetch(tile, i), b = voidFetch(tile, i + ivec2(1, 0));
	vec4 c = voidFetch(tile, i + ivec2(0, 1)), d = voidFetch(tile, i + ivec2(1, 1));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Without Gloam: one front field over 3 km and 1 km, as Gloam's Air would shape it, overcast in vanilla rain.
vec4 wcFallbackWeather(ivec2 m, CloudEnv e) {
	float f = (wcValue(m, 3072, e.seed + 101u) + 0.5 * wcValue(m, 1024, e.seed + 103u)) / 1.5;
	f = clamp((f - 0.5) * 1.8 + 0.5, 0.0, 1.0);
	float rain = e.rainFb * (0.12 + 0.88 * wcSmooth(0.1, 0.3, f));
	float cover = mix(wcSmooth(0.30, 0.75, f), 0.95, e.rainFb);
	float storm = e.stormFb * wcSmooth(0.3, 0.6, f);
	return vec4(f, cover, rain, storm);
}

// The tower part of a weather sample: max(storm footprint, showers * 0.55), and the storm footprint alone.
vec2 wcTowerOf(vec4 w) {
	float wide = max(w.a, wcSmooth(0.79, 0.93, w.r));
	return vec2(max(wide, wcSmooth(0.72, 0.94, w.r) * 0.55), wide);
}

// ------------------------------------------------------------------------------------------------- towers

float wcTowerBase(CloudEnv e) {
	return e.layers.x > WC_ABSENT * 0.5 ? e.layers.x : e.layers.y;
}

// The highest a tower rises for tower strength `tower` (CloudGrid: as tall as the storm is strong, in 20-block steps
// from the lowest deck up to the tower top).
float wcTowerTop(float tower, CloudEnv e) {
	float base = wcTowerBase(e);
	float span = e.layers.w + WC_DECK - base;
	float rise = ceil(clamp((tower - 0.18) / 0.6, 0.0, 1.0) * span / WC_TOWER_STEP) * WC_TOWER_STEP;
	return base + min(max(rise, WC_DECK), span);
}

// The tower and anvil on tower cell `tc` of ring `lv` (4 x 4 deck cells), into the cache.
void wcTower(ivec2 tc, int lv, CloudEnv e, inout TCache t) {
	ivec3 key = ivec3(tc, lv);
	if (t.key == key) {
		return;
	}
	t.key = key;
	t.top = -1e9;
	t.anvil = false;
	t.tone = 0;
	int ts = (WC_CELL * 4) << lv;
	ivec2 m = tc * ts + ts / 2;
	vec4 wf;
	if (e.w1) {
		float v1;
		wf = wcTile(VOID_TILE_W1, 10, m + e.w1Off, e.w1Centre, v1);
		if (v1 < 0.5) {
			return;
		}
	} else {
		wf = wcFallbackWeather(m, e);
	}
	vec2 tw = wcTowerOf(wf);
	if (tw.x <= 0.18) {
		return; // nothing rises here (an anvil needs the storm past 0.55, which this bounds too)
	}
	// towers stand only where it rains (or a storm is up): a high front alone, under a clear sky, raised them as lone
	// pillars and slabs on the horizon
	if (wf.b < 0.12 && wf.a < 0.35) {
		return;
	}
	t.tone = tw.y > 0.45 ? 2 : 1;
	float swell = clamp((wcPuffs(m, ts, 1536, 512, 0, e.seed + 41u) - 0.5) * 0.85 + 0.5, 0.0, 1.0);
	float strength = tw.x * (0.35 + 0.65 * swell);
	// far off a tower sinks back into its deck: a tower cell doubles with the rings, so a storm 6 km out stood up as
	// one hard-edged box 1.5 km wide on the horizon. Lowering a tower keeps the march's corner bound (an upper bound).
	vec2 rel = vec2(m - e.frameInt) - e.frameFract;
	strength *= 1.0 - smoothstep(WC_TOWER_NEAR, WC_TOWER_FAR, length(rel));
	if (strength > 0.18 && (wf.g > 0.5 || tw.y > 0.4)) {
		t.top = wcTowerTop(strength, e);
	}
	t.anvil = tw.y > WC_ANVIL && strength > 0.1;
}

// ------------------------------------------------------------------------------------------------- the column

bool wcOverlaps(vec2 iv, float lo, float hi) {
	return iv.x <= hi && iv.y >= lo;
}

bool wcPresent(float deck) {
	return deck > WC_ABSENT * 0.5;
}

// What stands in deck cell `cell` of ring `lv`. Only what the altitudes lo..hi overlap is worked out (the rest is
// left WC_EMPTY), so a ray crossing one deck pays for one deck. Pass -1e9, 1e9 for the whole column.
CloudCol wcColumn(ivec2 cell, int lv, float lo, float hi, CloudEnv e, inout TCache tq) {
	CloudCol c;
	c.low = WC_EMPTY;
	c.main = WC_EMPTY;
	c.high = WC_EMPTY;
	c.tower = WC_EMPTY;
	c.anvil = WC_EMPTY;
	c.tone = 0;
	c.dens = vec3(0.0);
	c.field = vec2(0.0);
	c.tiers = ivec2(0);

	float base = wcTowerBase(e);
	if (wcOverlaps(vec2(base, e.layers.w + WC_DECK), lo, hi)) {
		// the tower (and anvil) of the tower cell this column is part of
		wcTower(cell >> 2, lv, e, tq);
		c.tower = vec2(base, tq.top);
		c.anvil = tq.anvil ? vec2(e.layers.w - WC_TOWER_STEP, e.layers.w + WC_DECK) : WC_EMPTY;
		c.tone = tq.top > base || tq.anvil ? tq.tone : 0;
	}

	vec2 mainIv = vec2(e.layers.y, e.layers.y + WC_DECK);
	vec2 lowIv = vec2(e.layers.x, e.layers.x + WC_DECK);
	vec2 highIv = vec2(e.layers.z, e.layers.z + WC_DECK);
	bool doMain = wcOverlaps(vec2(mainIv.x, mainIv.x + wcDeckSpan(1)), lo, hi);
	bool doLow = wcPresent(e.layers.x) && wcOverlaps(vec2(lowIv.x, lowIv.x + wcDeckSpan(0)), lo, hi);
	bool doHigh = wcPresent(e.layers.z) && wcOverlaps(highIv, lo, hi);
	if (!(doMain || doLow || doHigh)) {
		return c;
	}

	int s = WC_CELL << lv;
	ivec2 m = cell * s + s / 2;                       // the cell's centre, frame blocks
	vec2 rel = vec2(m - e.frameInt) - e.frameFract;   // from the camera
	uint ch = wcHash(cell, e.seed ^ (uint(lv) * 0x85EBCA6Bu));

	// the decks' weather: W0 near, W1 past it, cell by cell across the seam
	vec4 w;
	if (e.w1) {
		float v;
		float toW1 = clamp((length(rel) - WC_W0_REACH) / 2000.0, 0.0, 1.0);
		bool near = e.w0 && wcU01(ch * 0x9E3779B9u + 7u) >= toW1;
		w = near ? wcTile(VOID_TILE_W0, 7, m, e.w0Centre, v) : wcTile(VOID_TILE_W1, 10, m + e.w1Off, e.w1Centre, v);
		if (wcU01(ch) >= v) {
			return c; // past the tile's reach: the sky is clear, cell by cell
		}
	} else {
		w = wcFallbackWeather(m, e);
	}
	float wide = max(w.a, wcSmooth(0.79, 0.93, w.r));
	float showers = wcSmooth(0.72, 0.94, w.r);
	float rain = w.b;
	c.tone = max(c.tone, wide > 0.45 ? 2 : (rain > 0.25 || showers > 0.5 ? 1 : 0));

	// the main deck: puffs against the cover, and always under a storm
	if (doMain) {
		// (review round 1: 480 and 160, were 384 and 128: fewer, larger clouds)
		float n = wcPuffs(m, s, 480, 160, 48, e.seed + 31u);
		if (lv <= 1) {
			n += (wcU01(ch) - 0.5) * 0.08; // a ragged cell here and there at the edges
		}
		float cover = max(w.g, e.fair);
		float thr = (1.0 - cover) * 0.92 + 0.04;
		// the margin field: how far the puffs clear the threshold, over a wider span the fuller the cover, so an
		// overcast's deck still has thick and thin parts (its belly mottled, its top lumpy) instead of one even sheet
		// (over the headroom the puffs have above the threshold, at most: a thin cover's clouds are the puffs' saturated
		// cores, which would all read as ragged rims)
		// (review round 2: over 0.55 of that headroom, so a fair cumulus is deep in its heart (taller tiers, a thick belly
		// that shades toward its middle) and thin only at its rims: at 0.38 front the fair clouds were all rim, flat
		// slabs four to eight blocks thick with one even belly)
		float span = max(min(0.22 + 0.5 * cover, 1.0 - thr) * 0.55, 0.04);
		float margin = (n - thr) / span;
		// deep inside, the puffs saturate: the cloud's heart is lumpy instead (a finer swell 6 cells across), so a
		// broad deck has domes and hollows all over its top and a mottled belly, not one flat field
		float lump = wcValue(m, 72, e.seed + 57u);
		// (review round 1: the lumps take less of the field, 0.45 not 0.65: they drew grey blotches inside a belly)
		// (clouds2: 0.2: the heaps give the tops their form now, and the lumps still drew dark blotches in a belly)
		c.field.y = margin > 0.0 || wide > 0.5
			? 0.5 + 0.5 * clamp(max(margin, wide > 0.5 ? 0.3 : 0.0), 0.0, 1.0) * (0.8 + 0.2 * lump)
			: clamp(0.5 + 0.5 * margin, 0.0, 0.5);
		if (n > thr || wide > 0.5) {
			// thin at a cloud's ragged edge, thick inside it (the shading's body)
			c.dens.y = c.field.y * 2.0 - 1.0;
			// a storm's sheet stands a tier taller
			c.tiers.y = min(wcTiersOf(wcMainRise(c.dens.y), ch, WC_MAIN_TIERS) + (wide > 0.5 ? 1 : 0), WC_MAIN_TIERS);
			c.main = vec2(mainIv.x, mainIv.y + wcMainTierHeight(c.tiers.y));
		}
	}
	// the low stratus: wide grey sheets under wet fronts
	if (doLow) {
		float wet = max(rain, wcSmooth(0.45, 0.82, w.r));
		float stratus = min(0.95, (0.1 + 0.9 * wet) * 0.6);
		float n = wcPuffs(m, s, 768, 256, 96, e.seed + 7u);
		float margin = (n - 1.0 + stratus * 0.9) / max(min(0.22 + 0.4 * wet, stratus * 0.9), 0.04);
		float lump = wcValue(m, 96, e.seed + 61u);
		c.field.x = stratus <= 0.05 ? 0.0 : (margin > 0.0 ? 0.5 + 0.5 * min(margin, 1.0) * (0.3 + 0.7 * lump)
			: clamp(0.5 + 0.5 * margin, 0.0, 0.5));
		if (stratus > 0.05 && n > 1.0 - stratus * 0.9) {
			c.dens.x = c.field.x * 2.0 - 1.0;
			c.tiers.x = wcTiersOf(c.dens.x, ch ^ 0x5bd1e995u, WC_LOW_TIERS);
			c.low = vec2(lowIv.x, lowIv.y + float(c.tiers.x) * WC_TIER);
		}
	}
	// the high deck: thin wide sheets, more ahead of a front
	if (doHigh) {
		// (review round 2: none under a fair sky, 0.06 was: the high deck's saturated puffs still laid big, thin,
		// grey slabs over a clear sky, sheared long in perspective; it spreads ahead of a front as before)
		float sheets = 0.66 * wcSmooth(0.4, 0.8, w.r);
		float n = wcPuffs(m, s, 1536, 512, 192, e.seed + 13u);
		if (sheets > 0.02 && n > 1.0 - sheets) {
			c.high = highIv;
			c.dens.z = clamp((n - 1.0 + sheets) / 0.22, 0.0, 1.0);
		}
	}
	return c;
}

bool wcIn(vec2 iv, float y) {
	return y >= iv.x && y <= iv.y;
}

// Whether altitude y is inside a cloud of this column.
bool wcInside(CloudCol c, float y) {
	return wcIn(c.low, y) || wcIn(c.main, y) || wcIn(c.high, y) || wcIn(c.tower, y) || wcIn(c.anvil, y);
}

// The first t in t0..t1 at which the ray (altitude y0 + dy * t) is inside interval `iv`, or 1e30.
float wcEnter(vec2 iv, float y0, float dy, float t0, float t1) {
	if (iv.x > iv.y) {
		return 1e30;
	}
	float a = t0, b = t1;
	if (abs(dy) < 1e-6) {
		if (y0 < iv.x || y0 > iv.y) {
			return 1e30;
		}
	} else {
		float ta = (iv.x - y0) / dy, tb = (iv.y - y0) / dy;
		a = max(t0, min(ta, tb));
		b = min(t1, max(ta, tb));
	}
	return a <= b ? a : 1e30;
}

// Where the ray first meets a cloud while it crosses the column (between t0 and t1), or 1e30.
float wcColumnEnter(CloudCol c, float y0, float dy, float t0, float t1) {
	float t = wcEnter(c.main, y0, dy, t0, t1);
	t = min(t, wcEnter(c.low, y0, dy, t0, t1));
	t = min(t, wcEnter(c.high, y0, dy, t0, t1));
	t = min(t, wcEnter(c.tower, y0, dy, t0, t1));
	t = min(t, wcEnter(c.anvil, y0, dy, t0, t1));
	return t;
}

#endif
