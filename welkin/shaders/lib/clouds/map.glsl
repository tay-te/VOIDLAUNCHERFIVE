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
//   R  bit 0 low deck, 1 main deck, 2 high deck, 3 anvil, 4..5 tone (0 fair, 1 showers, 2 storm); in the finished
//      map also bits 8..11: bits 0..3 ORed over the cell's block (its 4 x 4 tower cell), so with G (one tower per
//      block) the march crosses a block with nothing at the ray's height in one step
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

#if defined WC_STAGE_FINISH
#ifndef WU_colortex7
#define WU_colortex7
uniform sampler2D colortex7; // the raw map, read by prepare2 only
#endif
#elif !defined WC_STAGE_RAW
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
		| (c.anvil.x <= c.anvil.y ? 8 : 0) | (c.tone << 4);
	float rise = c.tower.x <= c.tower.y ? clamp(floor((c.tower.y - c.tower.x) / 4.0 + 0.5), 1.0, 255.0) : 0.0;
	return vec4(float(bits) / 255.0, rise / 255.0, 0.0, 0.0);
}

CloudCol wcMapDecode(int bits, int rise, CloudEnv e) {
	CloudCol c;
	c.low = (bits & 1) != 0 ? vec2(e.layers.x, e.layers.x + WC_DECK) : WC_EMPTY;
	c.main = (bits & 2) != 0 ? vec2(e.layers.y, e.layers.y + WC_DECK) : WC_EMPTY;
	c.high = (bits & 4) != 0 ? vec2(e.layers.z, e.layers.z + WC_DECK) : WC_EMPTY;
	c.anvil = (bits & 8) != 0 ? vec2(e.layers.w - WC_TOWER_STEP, e.layers.w + WC_DECK) : WC_EMPTY;
	c.tone = (bits >> 4) & 3;
	float base = wcTowerBase(e);
	c.tower = rise > 0 ? vec2(base, base + 4.0 * float(rise)) : WC_EMPTY;
	return c;
}

#if defined WC_STAGE_FINISH
// A raw map cell (prepare2) as (bits, rise): zero outside the map.
ivec2 wcRawCell(ivec2 cell, int lv, CloudEnv e) {
	ivec2 p = wcMapTexel(cell, lv, e);
	return p.x < 0 ? ivec2(0) : ivec2(texelFetch(colortex7, p, 0).rg * 255.0 + 0.5);
}
#elif !defined WC_STAGE_RAW
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

#endif
