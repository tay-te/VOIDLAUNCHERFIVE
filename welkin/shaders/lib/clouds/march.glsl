// VOID Welkin: the bounded voxel DDA through the cloud decks.
//
// The march is two-dimensional. A ray walks the columns of cells it crosses in x and z; in each column the cloud is a
// few solid y intervals (decks, a tower, an anvil), read as one texel of the cloud map (lib/clouds/map.glsl), which
// the ray meets or misses exactly (wcEnter), so there is never a vertical step, and the box faces come out exact, as
// voxels. What keeps it bounded:
//
//   1. Only the slab yLo..yHi is walked (the lowest deck's base to the tower top); the rest of the ray is solved by
//      two plane intersections. Land nearer than the clouds ends it (most of the screen never enters the loop).
//   2. Cells double in width each ring out (12 m inside WC_RING0, 24 m to twice that ...). A 4-block deck seen from h
//      blocks below is crossed over 4 d / h blocks at distance d, where cells are about 12 d / WC_RING0 wide, so a
//      deck costs about WC_RING0 / (3 h) + 2 columns at any distance: 5 columns for a deck 40 blocks up at MEDIUM.
//      And a block of 4 x 4 cells with nothing at the ray's height (the block map, one texel) is crossed in one step.
//   3. Between decks only towers can stand. They stand on tower cells 4 deck cells wide, and the ray only walks
//      those inside W1 cells whose corners say a tower can reach its height (the tower top is monotonic in the front
//      and the storm, and a bilinear blend never exceeds its corners); elsewhere it jumps straight to the next deck
//      or out of the 1 km W1 cell. With no storm or shower in reach (void_maxStorm, void_showers, void_front) it
//      jumps each gap at once. At most WC_GAP_STEPS jumps a ray.
//   4. A hard cap of WC_STEPS columns and jumps. Whatever is left (a ray grazing a deck from inside it, or past
//      WC_REACH) meets the decks as flat sheets: one plane hit and one map texel each, out to WC_FAR.
// So a pixel reads at most WC_STEPS + 4 map texels (the cap, a tower face's own texel, three sheets), whatever the distance
// or the weather.

#ifndef WELKIN_CLOUD_MARCH
#define WELKIN_CLOUD_MARCH

#include "/lib/clouds/map.glsl"

struct CloudHit {
	bool hit;
	bool inside;   // the eye is inside a cloud (that cell is skipped and the screen veiled)
	float tOut;    // inside: how far the ray runs before it leaves the eye's own cloud
	bool sheet;    // met as a flat sheet, past the DDA
	float t;       // distance along the ray
	vec3 n;        // the face's world normal
	int level;     // the ring
	int tone;
	ivec2 cell;    // the deck cell the face belongs to
	int steps;     // columns and jumps taken (the cost map)
	int gapCols;   // of them, tower cells walked between decks
	int jumps;     // and jumps
};

CloudHit wcNoHit() {
	CloudHit h;
	h.hit = false;
	h.inside = false;
	h.tOut = 0.0;
	h.sheet = false;
	h.t = 1e30;
	h.n = vec3(0.0, 1.0, 0.0);
	h.level = 0;
	h.tone = 0;
	h.cell = ivec2(0);
	h.steps = 0;
	h.gapCols = 0;
	h.jumps = 0;
	return h;
}

// Whether altitudes lo..hi touch any deck's slab.
bool wcDeckBand(float lo, float hi, CloudEnv e) {
	return wcOverlaps(vec2(e.layers.y, e.layers.y + WC_DECK), lo, hi)
		|| (wcPresent(e.layers.x) && wcOverlaps(vec2(e.layers.x, e.layers.x + WC_DECK), lo, hi))
		|| (wcPresent(e.layers.z) && wcOverlaps(vec2(e.layers.z, e.layers.z + WC_DECK), lo, hi));
}

// The t at which the ray, at altitude y now, next enters a deck's slab (1e30 if it never does).
float wcNextDeck(float y0, float dy, float invDy, float y, CloudEnv e) {
	if (abs(dy) < 1e-6) {
		return 1e30;
	}
	float best = 1e30;
	for (int k = 0; k < 3; k++) {
		float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
		float plane = dy > 0.0 ? deck : deck + WC_DECK;
		if (wcPresent(deck) && ((dy > 0.0 && plane >= y) || (dy < 0.0 && plane <= y))) {
			best = min(best, (plane - y0) * invDy);
		}
	}
	return best;
}

// The first t in t0..t1 at which the ray (altitude y0 + dy * t) is inside interval `iv`, or 1e30. Branch-free:
// invDy is 1 / dy with dy clamped off zero, so a level ray gives huge same-signed ta and tb unless it lies inside.
float wcEnterInv(vec2 iv, float y0, float invDy, float t0, float t1) {
	float ta = (iv.x - y0) * invDy, tb = (iv.y - y0) * invDy;
	float a = max(t0, min(ta, tb)), b = min(t1, max(ta, tb));
	return iv.x <= iv.y && a <= b ? a : 1e30;
}

float wcColumnEnterInv(CloudCol c, float y0, float invDy, float t0, float t1) {
	return min(min(min(wcEnterInv(c.main, y0, invDy, t0, t1), wcEnterInv(c.low, y0, invDy, t0, t1)),
		min(wcEnterInv(c.high, y0, invDy, t0, t1), wcEnterInv(c.tower, y0, invDy, t0, t1))),
		wcEnterInv(c.anvil, y0, invDy, t0, t1));
}

// The rim test: whether the hit pixel falls in a dithered opening along the cloud's outline. The band is one block
// wide, doubling with the cells (never under two grid texels); in its outer half one texel in two is open, in its
// inner half one in four, on a 4x4 Bayer laid on the cloud's own 16-pixel grid (it drifts with the cloud, so it never
// crawls). Only edges against open air count: the finished map's edge bits (`cellTexel`, the hit cell's texel) say
// which neighbours lack this cell's decks, or its anvil or tower height, so no neighbour is read.
bool wcRimOpen(CloudHit h, ivec4 cellTexel, vec3 p, float y, vec3 rd, float blocksPerPixel, CloudEnv e) {
#ifndef CLOUD_RIMS
	return false;
#else
	if (h.level > WC_RIM_LEVELS) {
		return false; // far out the band is a pixel or two
	}
	int s = WC_CELL << h.level;
	float size = float(s);
	vec2 local = p.xz - wcCellCorner(h.cell, s, e);
	bool capFace = abs(h.n.y) > 0.5;
	vec2 dist = vec2(capFace || abs(h.n.z) > 0.5 ? min(local.x, size - local.x) : 1e9,
		capFace || abs(h.n.x) > 0.5 ? min(local.y, size - local.y) : 1e9);
	if (min(dist.x, dist.y) >= max(float(1 << h.level), size * 0.25)) {
		return false; // past the widest band there can be
	}
	// which kind of cloud was hit: the tower (or anvil) where it stands at this height, else a deck
	CloudCol c = wcMapDecode(cellTexel.x, cellTexel.y, e);
	float inside = y - h.n.y * 0.01;
	int edges = wcIn(c.tower, inside) || wcIn(c.anvil, inside) ? cellTexel.w
		: cellTexel.z >> (wcIn(c.main, inside) ? 4 : (wcIn(c.high, inside) ? 8 : 0));
	int dx = local.x < size * 0.5 ? 0 : 1, dz = local.y < size * 0.5 ? 2 : 3;
	float edge = min(((edges >> dx) & 1) != 0 ? dist.x : 1e9, ((edges >> dz) & 1) != 0 ? dist.y : 1e9);
	if (edge >= 1e8) {
		return false;
	}
	int g = gridLevel(blocksPerPixel / max(abs(dot(h.n, rd)), 0.2), 1.0);
	float band = min(max(float(1 << h.level), exp2(float(g)) / 8.0), size * 0.25);
	if (edge >= band) {
		return false;
	}
	return wcDither(p, h.n, g, e) < (edge < band * 0.5 ? 0.5 : 0.25);
#endif
}

// The decks as flat sheets along a ray from t0 to t1, nearest first: one plane hit and one map texel each. What the
// march leaves past its reach or its cap, and cheap clouds for water reflections.
CloudHit welkinCloudSheets(vec3 ro, vec3 rd, float t0, float t1, CloudEnv e, CloudHit h) {
	if (abs(rd.y) < 1e-5) {
		return h;
	}
	float y0 = e.camY + ro.y;
	for (int i = 0; i < 3; i++) {
		int k = rd.y > 0.0 ? i : 2 - i; // upward low, main, high; downward high, main, low
		float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
		float t = ((rd.y > 0.0 ? deck : deck + WC_DECK) - y0) / rd.y;
		if (!wcPresent(deck) || t < t0 || t >= t1) {
			continue;
		}
		vec2 q = ro.xz + rd.xz * t;
		int lv = wcLevel(length(q));
		ivec2 cell = wcCellOf(q, lv, e);
		CloudCol c = wcMapColumn(cell, lv, e);
		float mid = deck + WC_DECK * 0.5;
		if (wcInside(c, mid)) {
			h.hit = true;
			h.sheet = true;
			h.t = t;
			h.n = vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0);
			h.level = lv;
			h.tone = c.tone;
			h.cell = cell;
			return h;
		}
	}
	return h;
}

// The voxel clouds along a ray: from `ro` (camera-relative, world axes) toward unit `rd`, stopping at the land
// (`tScene`, 1e30 for sky). `pixAngle`: radians per screen pixel, for the rims' grid.
CloudHit welkinCloudMarch(vec3 ro, vec3 rd, float tScene, float pixAngle, CloudEnv e) {
	CloudHit h = wcNoHit();

	// 1. the slab
	float y0 = e.camY + ro.y;
	float invDy = 1.0 / (abs(rd.y) < 1e-6 ? 1e-6 : rd.y);
	float tA, tB;
	if (abs(rd.y) < 1e-6) {
		if (y0 < e.yLo || y0 > e.yHi) {
			return h;
		}
		tA = 0.0;
		tB = 1e30;
	} else {
		float ta = (e.yLo - y0) * invDy, tb = (e.yHi - y0) * invDy;
		tA = max(min(ta, tb), 0.0);
		tB = max(ta, tb);
	}
	float tEnd = min(min(tB, tScene), WC_FAR);
	if (tA >= tEnd) {
		return h;
	}
	float tReach = min(tEnd, WC_REACH);

	vec2 dir = rd.xz;
	ivec2 stepI = ivec2(dir.x >= 0.0 ? 1 : -1, dir.y >= 0.0 ? 1 : -1);
	vec2 inv = vec2(stepI) / max(abs(dir), vec2(1e-7));
	float base = wcTowerBase(e);
	vec2 anvilBand = vec2(e.layers.w - WC_TOWER_STEP, e.layers.w + WC_DECK);

	float t = tA;
	int lv = 0;
	ivec2 cell = ivec2(0);
	bool fresh = true;     // the cell must be found from the point (after a jump, a tower cell or a ring change)
	int axis = -1;         // how the ray came into this column: 0 through an x side, 1 a z side, -1 through a plane
	int gaps = 0;
	bool first = true;
	bool insideRun = false;   // the ray is still inside the cloud the eye is in
	float searchUntil = -1.0; // inside a W1 cell already found to hold a tower that may reach the ray, until here,
	float searchTop = -1e9;   // up to this height,
	bool searchAnvil = false; // and an anvil
	ivec3 lastBlock = ivec3(0x7FFFFFFF); // the block (tower cell) last found to hold cloud at the ray's height

	for (int i = 0; i < WC_STEPS; i++) {
		if (t >= tReach) {
			break;
		}
		h.steps++;
		vec3 p = ro + rd * t;
		vec2 nudge = p.xz + dir * (1e-3 + t * 1e-5); // a hair past the boundary the ray stands on
		if (fresh) {
			lv = wcLevel(length(p.xz));
			cell = wcCellOf(nudge, lv, e);
			fresh = false;
		}
		int s = WC_CELL << lv;
		vec2 corner = wcCellCorner(cell, s, e);
		vec2 tf = max((corner - ro.xz) * inv, (corner + float(s) - ro.xz) * inv);
		float tc = min(tf.x, tf.y);
		int exitAxis = tf.x < tf.y ? 0 : 1;
		float t1 = min(tc, tReach);
		float ya = y0 + rd.y * t, yb = y0 + rd.y * t1;
		bool towerStep = false;

		bool inGap = !wcDeckBand(min(ya, yb), max(ya, yb), e);

		// 2b. a block (4 x 4 cells) with nothing at the ray's height is crossed in one step: the column's texel
		// carries its block's deck bits (A) and the block's one tower (G)
		ivec4 raw = ivec4(0);
		if (!inGap) {
			raw = wcMapCell(cell, lv, e);
			ivec3 bkey = ivec3(cell >> 2, lv);
			if (bkey != lastBlock) {
				int ts = s * 4;
				vec2 bcorner = wcCellCorner(cell >> 2, ts, e);
				vec2 tfB = max((bcorner - ro.xz) * inv, (bcorner + float(ts) - ro.xz) * inv);
				float tB2 = min(tfB.x, tfB.y);
				float yB = y0 + rd.y * min(tB2, tReach);
				float blo = min(ya, yB), bhi = max(ya, yB);
				int bits = raw.x >> 8;
				float rise = float(raw.y) * 4.0;
				bool held = ((bits & 1) != 0 && wcOverlaps(vec2(e.layers.x, e.layers.x + WC_DECK), blo, bhi))
					|| ((bits & 2) != 0 && wcOverlaps(vec2(e.layers.y, e.layers.y + WC_DECK), blo, bhi))
					|| ((bits & 4) != 0 && wcOverlaps(vec2(e.layers.z, e.layers.z + WC_DECK), blo, bhi))
					|| ((bits & 8) != 0 && wcOverlaps(anvilBand, blo, bhi))
					|| (rise > 0.0 && wcOverlaps(vec2(base, base + rise), blo, bhi));
				if (!held) {
					h.jumps++;
					insideRun = false;
					first = false;
					t = max(tB2, t + 1e-3);
					fresh = true;
					axis = tfB.x < tfB.y ? 0 : 1;
					continue;
				}
				lastBlock = bkey;
			}
		}

		// 3. between decks only towers stand: walk tower cells only where the W1 cell says one may reach the ray
		if (inGap) {
			float tNext = wcNextDeck(y0, rd.y, invDy, ya, e);
			float tJump = tNext;
			int jumpAxis = -1;
			bool search = false;
			if (t < searchUntil) {
				// still in that W1 cell: walk on only while the ray is low enough for its towers
				float ye = y0 + rd.y * min(min(searchUntil, tNext), tReach);
				float slo = min(ya, ye), shi = max(ya, ye);
				search = wcOverlaps(vec2(base, searchTop), slo, shi) || (searchAnvil && wcOverlaps(anvilBand, slo, shi));
				if (!search) {
					tJump = min(tJump, searchUntil);
				}
			} else if (e.towers && gaps < WC_GAP_STEPS) {
				search = true;
				if (e.w1) {
					// the W1 cell between texel centres the point is in
					ivec2 f1 = e.frameInt + e.w1Off - 512;
					ivec2 i1 = f1 >> 10;
					i1 += ivec2(floor((vec2(f1 - (i1 << 10)) + e.frameFract + nudge) / 1024.0));
					vec4 hiW = max(max(voidFetch(VOID_TILE_W1, i1), voidFetch(VOID_TILE_W1, i1 + ivec2(1, 0))),
						max(voidFetch(VOID_TILE_W1, i1 + ivec2(0, 1)), voidFetch(VOID_TILE_W1, i1 + ivec2(1, 1))));
					vec2 c1 = vec2((i1 << 10) + 512 - e.frameInt - e.w1Off) - e.frameFract;
					vec2 tf1 = max((c1 - ro.xz) * inv, (c1 + 1024.0 - ro.xz) * inv);
					float tCell = min(tf1.x, tf1.y);
					vec2 bound = wcTowerOf(hiW);
					float ye = y0 + rd.y * min(min(tCell, tNext), tReach);
					float slo = min(ya, ye), shi = max(ya, ye);
					searchTop = bound.x > 0.18 ? wcTowerTop(bound.x, e) : -1e9;
					searchAnvil = bound.x > 0.18 && bound.y > WC_ANVIL;
					search = wcOverlaps(vec2(base, searchTop), slo, shi) || (searchAnvil && wcOverlaps(anvilBand, slo, shi));
					if (search) {
						searchUntil = tCell;
					} else if (tCell < tJump) {
						tJump = tCell;
						jumpAxis = tf1.x < tf1.y ? 0 : 1;
					}
				}
			}
			if (!search) {
				h.jumps++;
				insideRun = false;
				gaps++;
				first = false;
				t = max(tJump, t + 1e-3);
				fresh = true;
				axis = jumpAxis;
				continue;
			}
			// one tower cell (4 x 4 deck columns)
			h.gapCols++;
			int ts = s * 4;
			vec2 tcorner = wcCellCorner(cell >> 2, ts, e);
			vec2 tfT = max((tcorner - ro.xz) * inv, (tcorner + float(ts) - ro.xz) * inv);
			tc = min(tfT.x, tfT.y);
			exitAxis = tfT.x < tfT.y ? 0 : 1;
			t1 = min(min(tc, tNext), tReach);
			if (t1 < tc) {
				tc = t1; // left through the next deck's plane
				exitAxis = -1;
			}
			towerStep = true;
		}

		// most columns are empty: one fetch and a compare
		if (towerStep) {
			raw = wcMapCell(cell, lv, e);
		}
		CloudCol c = wcMapDecode(raw.x, raw.y, e);
		float th = (raw.x & 15) + raw.y > 0 ? wcColumnEnterInv(c, y0, invDy, t, t1) : 1e30;
		if (th < 1e29) {
			bool atEntry = th <= t * (1.0 + 1e-5) + 1e-4;
			if ((first && t == 0.0 && th == 0.0) || (insideRun && atEntry)) {
				// the eye's own cloud (a deck cell, a tower or an anvil): see out of it, through as many of its cells as
				// the ray crosses before it comes out into the open, so its inside is one smooth fog (welkinCloudVeil)
				// and never the faces of its own neighbouring cells
				h.inside = true;
				float yc = y0 + rd.y * min(t + 0.01, t1);
				vec2 iv = wcIn(c.low, yc) ? c.low : (wcIn(c.main, yc) ? c.main : (wcIn(c.high, yc) ? c.high
					: (wcIn(c.tower, yc) ? c.tower : (wcIn(c.anvil, yc) ? c.anvil : vec2(yc)))));
				float tY = abs(rd.y) < 1e-6 ? 1e30 : ((rd.y > 0.0 ? iv.y : iv.x) - y0) * invDy;
				h.tOut = min(t1, max(tY, t));
				insideRun = tY >= t1; // out through the column's side: perhaps into more of the same cloud
				float th2 = insideRun ? 1e30 : wcColumnEnterInv(c, y0, invDy, h.tOut + 1e-3, t1);
				if (th2 < 1e29) {
					// out of its top or bottom, then into another of the column's clouds: that one's cap
					h.hit = true;
					h.t = th2;
					h.level = lv;
					h.tone = c.tone;
					h.n = vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0);
					h.cell = towerStep ? wcCellOf((ro + rd * th2).xz, lv, e) : cell;
					return h;
				}
			} else {
				insideRun = false;
				h.hit = true;
				h.t = th;
				h.level = lv;
				h.tone = c.tone;
				// a side face where the ray came in through a side of this column (or tower cell) and met the cloud right
				// there. After a ring change the point may lie inside the new, wider cell, not on its side: then the
				// cloud is met inside the slab, and drawn as its cap (a side there was a wall standing on the ring's edge)
				bool side = axis >= 0 && atEntry;
				if (side) {
					int sz = towerStep ? (WC_CELL * 4) << lv : WC_CELL << lv;
					vec2 corner = wcCellCorner(towerStep ? cell >> 2 : cell, sz, e);
					vec2 pe = ro.xz + rd.xz * t;
					float bound = axis == 0 ? (stepI.x > 0 ? corner.x : corner.x + float(sz)) : (stepI.y > 0 ? corner.y : corner.y + float(sz));
					side = abs((axis == 0 ? pe.x : pe.y) - bound) < 0.05 + t * 2e-5;
				}
				h.n = side ? (axis == 0 ? vec3(-float(stepI.x), 0.0, 0.0) : vec3(0.0, 0.0, -float(stepI.y)))
					: vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0);
				vec3 ph = ro + rd * th;
				// the deck column the face belongs to (a tower face: the one just inside it)
				h.cell = towerStep ? wcCellOf(ph.xz - h.n.xz * 0.01, lv, e) : cell;
				ivec4 hitTexel = towerStep ? wcMapCell(h.cell, lv, e) : raw;
				if (!wcRimOpen(h, hitTexel, ph, y0 + rd.y * th, rd, th * pixAngle, e)) {
					return h;
				}
				h.hit = false; // through the rim's opening: on behind it
				towerStep = true; // so the next column is found afresh
			}
		} else {
			insideRun = false;
		}
		first = false;
		if (towerStep) {
			t = max(tc, t + 1e-3);
			fresh = true;
			axis = exitAxis;
			continue;
		}
		// on to the next column: one step along the exit axis while the ring holds
		t = tc;
		axis = exitAxis;
		if (wcLevel(length((ro + rd * t).xz)) == lv) {
			if (exitAxis == 0) {
				cell.x += stepI.x;
			} else {
				cell.y += stepI.y;
			}
		} else {
			fresh = true;
		}
	}

	// 4. what the DDA left: the decks as sheets
	if (t < tEnd) {
		h = welkinCloudSheets(ro, rd, max(t, tA), tEnd, e, h);
	}
	return h;
}

#endif
