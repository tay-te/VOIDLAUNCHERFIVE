// VOID Welkin: the finished cloud map (M3), prepare pass 2, right after the raw map (prepare1). Copies each cell and
// adds what the march would otherwise have to fetch around it (lib/clouds/map.glsl):
//   its edges: which neighbours lack each of its decks, or its anvil, or stand lower in tower;
//   its block: the deck and anvil bits ORed over the 4 x 4 cells of its tower cell;
//   its decks' thickness, from the raw map's B.
// 21 fetches of colortex7 a cell, into colortex8 (RGBA16, 160 x 1280, size.buffer.colortex8). Overworld only.

#include "/lib/common.glsl"
#include "/lib/clouds/buffers.glsl"

#ifdef VERTEX_SHADER

void main() {
	gl_Position = ftransform();
}

#endif

#ifdef FRAGMENT_SHADER

#define WC_STAGE_FINISH
#include "/lib/clouds/map.glsl"

/* RENDERTARGETS: 8 */
layout(location = 0) out vec4 outMap;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	int lv = min(p.y / WC_MAP, WC_MAX_LEVEL);
	if (!wcMapUsed(ivec2(p.x, p.y - lv * WC_MAP), lv)) {
		outMap = vec4(0.0);
		return;
	}
	CloudEnv e = welkinCloudEnv();
	ivec2 cell = wcMapAnchor(lv, e) + ivec2(p.x, p.y - lv * WC_MAP) - WC_MAP_HALF;
	vec4 rawOwn = texelFetch(colortex7, p, 0);
	ivec2 own = ivec2(rawOwn.rg * 255.0 + 0.5);
	// (the clouds rework: the raw R's bits 6..7 are the main deck's tiers, the raw G's bit 7 the low deck's tier)
	int bits = own.x & 63, rise = own.y & 127;
	int tiers = (own.y >> 7) | ((own.x >> 6) << 1);

	// the edges, toward -x, +x, -z, +z
	int deckEdges = 0, towerEdges = 0;
	for (int d = 0; d < 4; d++) {
		ivec2 off = d == 0 ? ivec2(-1, 0) : (d == 1 ? ivec2(1, 0) : (d == 2 ? ivec2(0, -1) : ivec2(0, 1)));
		ivec2 n = wcRawCell(cell + off, lv, e);
		int missing = bits & 7 & ~n.x;
		deckEdges |= ((missing & 1) << d) | (((missing >> 1) & 1) << (4 + d)) | (((missing >> 2) & 1) << (8 + d));
		if (((bits & 8) != 0 && (n.x & 8) == 0) || rise > (n.y & 127)) {
			towerEdges |= 1 << d;
		}
	}

	// the block: the 4 x 4 cells of this cell's tower cell
	int block = 0, blockTier = 0;
	ivec2 b0 = (cell >> 2) << 2;
	for (int j = 0; j < 4; j++) {
		for (int i = 0; i < 4; i++) {
			int r = wcRawCell(b0 + ivec2(i, j), lv, e).x;
			block |= r & 15;
			blockTier = max(blockTier, r >> 6); // (clouds2) the block's tallest main-deck tier, for the march's block skip
		}
	}
	outMap = vec4(float(bits | (block << 8) | wcMapDensityBits(rawOwn.ba)), float(rise | (tiers << 7) | (blockTier << 10)), float(deckEdges),
		float(towerEdges)) / 65535.0;
}

#endif
