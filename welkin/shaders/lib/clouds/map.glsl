// VOID Welkin: the cloud map. What stands in each column of sky does not depend on where the camera looks, only on
// the weather and the frame, so two prepare passes work every cell out once a frame into a small clipmap, and the
// march, the shadows and the far weather read one texel per column:
//
//   prepare1 (program/cloudmap.glsl) -> colortex7, the raw map: wcColumn for every cell.
//   prepare2 (program/cloudfinish.glsl) -> colortex8, the finished map: each cell with its edges and its block.
//
// The raw map is RGBA8, the finished one RGBA16 (unorm, read back as integers); both WC_MAP x (WC_MAP * 8): one
// WC_MAP x WC_MAP square per ring (ring 0 at the bottom), each centred on
// the camera's cell of that ring. Because every ring doubles both its reach and its cells, the same 160 cells across
// hold every ring (ring k reaches WC_RING0 * 2^k, which is WC_RING0 / 12 cells of 12 * 2^k: 32 at MEDIUM, 43 at
// HIGH), with room for the rims' neighbours, the tower cells, and the sheets out to 80 cells of 1536 m (123 km).
//
// (the clouds rework, 2026-10-16) The raw map's B and A are no longer bits: they hold the low and main decks' smooth
// margin fields (CloudCol.field), which the clouds pass samples with the GPU's bilinear filter (wcField) for the light
// inside each cloud; the raw R carries the main deck's tiers in bits 6..7 and the raw G the low deck's tier in bit 7.
// In the finished map G is the tower's rise (bits 0..6), the low deck's tier (7) and the main deck's tiers (8..9), and
// (clouds2) the tallest main-deck tier over the cell's block (10..11).
//
//   R  bit 0 low deck, 1 main deck, 2 high deck, 3 anvil, 4..5 tone (0 fair, 1 showers, 2 storm); in the finished
//      map also bits 8..11: bits 0..3 ORed over the cell's block (its 4 x 4 tower cell), so with G (one tower per
//      block) the march crosses a block with nothing at the ray's height in one step; and (finished) each deck
//      cell's thickness in four steps (wcMapDensity): the low deck's in bits 6..7, the main's in 12..13, the high's
//      in 14..15 (in the raw map: B, 2 bits a deck)
//   G  the tower's rise above its base, in 4-block units (0: no tower)
//   B  (finished) deck edges: bit 4 k + d where the neighbour in direction d (0 -x, 1 +x, 2 -z, 3 +z) lacks deck k
//      (0 low, 1 main, 2 high) that this cell has
//   A  (finished) tower edges: bit d where that neighbour lacks this cell's anvil or stands lower in tower
// The rims read the edges and need no neighbour.
//
// 205k cells at about one wcColumn each (prepare1), then 20 fetches each (prepare2): together well under a tenth of a
// 1080p screen of work, once a frame, instead of one column evaluation per DDA step per pixel.

#ifndef WELKIN_CLOUD_MAP
#define WELKIN_CLOUD_MAP

#include "/lib/clouds/cells.glsl"

#define WC_MAP 160
#define WC_MAP_HALF 80

#if defined WC_STAGE_FINISH || defined WC_FIELD
#ifndef WU_colortex7
#define WU_colortex7
uniform sampler2D colortex7; // the raw map, read by prepare2 (and its fields by the clouds pass, WC_FIELD)
#endif
#endif
#if !defined WC_STAGE_RAW && !defined WC_STAGE_FINISH
#ifndef WU_colortex8
#define WU_colortex8
uniform sampler2D colortex8; // the finished map, read by everything after
#endif
#endif

// Only the last ring (the sheets out to WC_FAR) needs its whole square: ring k is drawn only out to WC_RING0 * 2^k,
// which is WC_RING0 / 12 of its cells from the centre whatever k is, so the other rings' squares are worked out only
// that far, with a margin for the rims' neighbours and the 4 x 4 tower blocks; the rest of each square is left empty.
// Saves about two thirds of the map passes' work.
#define WC_MAP_INNER (int(WC_RING0) / WC_CELL + 8)

bool wcMapUsed(ivec2 local, int lv) {
	ivec2 d = abs(local - WC_MAP_HALF);
	return lv >= WC_MAX_LEVEL || max(d.x, d.y) <= WC_MAP_INNER;
}

// The camera's cell in ring lv: the square's centre.
ivec2 wcMapAnchor(int lv, CloudEnv e) {
	return e.frame12 >> lv;
}

// Where cell `cell` of ring `lv` sits in the map, or (-1, -1) outside it.
ivec2 wcMapTexel(ivec2 cell, int lv, CloudEnv e) {
	ivec2 local = cell - wcMapAnchor(lv, e) + WC_MAP_HALF;
	if (any(lessThan(local, ivec2(0))) || any(greaterThanEqual(local, ivec2(WC_MAP)))) {
		return ivec2(-1);
	}
	return ivec2(local.x, local.y + lv * WC_MAP);
}

// The column as the raw map stores it.
vec4 wcMapEncode(CloudCol c, CloudEnv e) {
	int bits = (c.low.x <= c.low.y ? 1 : 0) | (c.main.x <= c.main.y ? 2 : 0) | (c.high.x <= c.high.y ? 4 : 0)
		| (c.anvil.x <= c.anvil.y ? 8 : 0) | (c.tone << 4) | (c.tiers.y << 6);
	int rise = c.tower.x <= c.tower.y ? int(clamp(floor((c.tower.y - c.tower.x) / 4.0 + 0.5), 1.0, 127.0)) : 0;
	return vec4(float(bits) / 255.0, float(rise | (c.tiers.x << 7)) / 255.0, c.field.x, c.field.y);
}

// The finished map's thickness bits (R bits 6..7 low, 12..13 main, 14..15 high) from the raw map's fields (B low, A
// main; the high deck's sheet is taken as of middling thickness).
int wcMapDensityBits(vec2 field) {
	ivec2 q = min(ivec2(sqrt(clamp(field * 2.0 - 1.0, 0.0, 1.0)) * 4.0), ivec2(3));
	return (q.x << 6) | (q.y << 12) | (2 << 14);
}

// Each deck cell's thickness (low, main, high), 0..1 in four steps, from the finished map's R.
vec3 wcMapDensity(int r) {
	return vec3((r >> 6) & 3, (r >> 12) & 3, (r >> 14) & 3) / 3.0;
}

// The finished map's G: (rise, low tier, main tiers).
ivec3 wcMapRise(int g) {
	return ivec3(g & 127, (g >> 7) & 1, (g >> 8) & 3);
}

CloudCol wcMapDecode(int bits, int g, CloudEnv e) {
	CloudCol c;
	ivec3 r = wcMapRise(g);
	int rise = r.x;
	c.tiers = r.yz;
	c.field = vec2(0.0);
	c.low = (bits & 1) != 0 ? vec2(e.layers.x, e.layers.x + WC_DECK + float(r.y) * WC_TIER) : WC_EMPTY;
	c.main = (bits & 2) != 0 ? vec2(e.layers.y, e.layers.y + WC_DECK + wcMainTierHeight(r.z)) : WC_EMPTY;
	c.high = (bits & 4) != 0 ? vec2(e.layers.z, e.layers.z + WC_DECK) : WC_EMPTY;
	c.anvil = (bits & 8) != 0 ? vec2(e.layers.w - WC_TOWER_STEP, e.layers.w + WC_DECK) : WC_EMPTY;
	c.tone = (bits >> 4) & 3;
	c.dens = vec3(1.0); // (wcMapDensity reads it from the finished map's R where it matters)
	float base = wcTowerBase(e);
	c.tower = rise > 0 ? vec2(base, base + 4.0 * float(rise)) : WC_EMPTY;
	return c;
}

#if defined WC_STAGE_FINISH
// A raw map cell (prepare2) as (bits and main tiers, rise and low tier): zero outside the map.
ivec2 wcRawCell(ivec2 cell, int lv, CloudEnv e) {
	ivec2 p = wcMapTexel(cell, lv, e);
	return p.x < 0 ? ivec2(0) : ivec2(texelFetch(colortex7, p, 0).rg * 255.0 + 0.5);
}
#endif
#if !defined WC_STAGE_RAW && !defined WC_STAGE_FINISH
// A finished map cell as (bits | block bits << 8, rise, deck edges, tower edges): zero (nothing) outside the map.
ivec4 wcMapCell(ivec2 cell, int lv, CloudEnv e) {
	ivec2 p = wcMapTexel(cell, lv, e);
	return p.x < 0 ? ivec4(0) : ivec4(texelFetch(colortex8, p, 0) * 65535.0 + 0.5);
}

// What stands in deck cell `cell` of ring `lv`: one texel of the finished map.
CloudCol wcMapColumn(ivec2 cell, int lv, CloudEnv e) {
	ivec4 m = wcMapCell(cell, lv, e);
	return wcMapDecode(m.x, m.y, e);
}
#endif

#ifdef WC_FIELD
// The low and main decks' margin fields (x low, y main) at camera-relative point q, read at ring lv through the GPU's
// bilinear filter: 0.5 on a cloud's outline, rising smoothly to 1 deep inside it and falling to 0 out in the open, so
// the light inside a voxel cloud runs in soft gradients with no cell steps (the outline itself stays the cells').
vec2 wcFieldAt(vec2 q, int lv, CloudEnv e) {
	int s = WC_CELL << lv;
	ivec2 cell = wcCellOf(q, lv, e);
	vec2 f = (q - wcCellCorner(cell, s, e)) / float(s);
	vec2 local = clamp(vec2(cell - wcMapAnchor(lv, e) + WC_MAP_HALF) + f, vec2(0.5), vec2(float(WC_MAP) - 0.5));
	return texture(colortex7, (local + vec2(0.0, float(lv * WC_MAP))) / vec2(float(WC_MAP), float(WC_MAP * 8))).ba;
}

// (clouds2) The fields at q, eased from ring to ring: each ring's map is worked out on its own cells (twice as wide as
// the ring's before, with its finer octaves left out), so the light inside a deck jumped where one ring met the next,
// a long straight seam through an overcast belly. Over the outer third of each ring the next ring's field is blended
// in, so at the ring's edge it is the next ring's already. `lv` is unused: the ring is the point's own.
vec2 wcField(vec2 q, int lv, CloudEnv e) {
	float d = length(q);
	int l0 = wcLevel(d);
	vec2 f = wcFieldAt(q, l0, e);
	if (l0 < WC_MAX_LEVEL) {
		float edge = WC_RING0 * exp2(float(l0));
		float w = smoothstep(edge * (l0 == 0 ? 0.6 : 0.8), edge, d);
		if (w > 0.0) {
			f = mix(f, wcFieldAt(q, l0 + 1, e), w);
		}
	}
	return f;
}

// (clouds2) The fields at q, smoother: four bilinear reads half a cell apart, averaged (a quadratic B-spline over the
// cells), so a cloud's heart shades in round soft forms instead of the bilinear filter's diamonds. For the body's own
// depth; the samples along the light take the plain field.
vec2 wcFieldSmooth(vec2 q, CloudEnv e) {
	float h = 0.5 * float(WC_CELL) * clamp(1.5 * length(q) / WC_RING0, 1.0, float(1 << WC_MAX_LEVEL));
	return 0.25 * (wcField(q + vec2(h, h), 0, e) + wcField(q + vec2(-h, h), 0, e)
		+ wcField(q + vec2(h, -h), 0, e) + wcField(q + vec2(-h, -h), 0, e));
}
#endif

#endif
