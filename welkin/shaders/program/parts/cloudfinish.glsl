// VOID Welkin: the finished cloud map (M3), prepare pass 2, right after the raw map (prepare1). Copies each cell and
// adds what the march would otherwise have to fetch around it (lib/clouds/map.glsl):
//   its edges: which neighbours lack each of its decks, or its anvil, or stand lower in tower;
//   its block: the deck and anvil bits ORed over the 4 x 4 cells of its tower cell.
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
	CloudEnv e = welkinCloudEnv();
	ivec2 cell = wcMapAnchor(lv, e) + ivec2(p.x, p.y - lv * WC_MAP) - WC_MAP_HALF;
	ivec2 own = ivec2(texelFetch(colortex7, p, 0).rg * 255.0 + 0.5);
	int bits = own.x, rise = own.y;

	// the edges, toward -x, +x, -z, +z
	int deckEdges = 0, towerEdges = 0;
	for (int d = 0; d < 4; d++) {
		ivec2 off = d == 0 ? ivec2(-1, 0) : (d == 1 ? ivec2(1, 0) : (d == 2 ? ivec2(0, -1) : ivec2(0, 1)));
		ivec2 n = wcRawCell(cell + off, lv, e);
		int missing = bits & 7 & ~n.x;
		deckEdges |= ((missing & 1) << d) | (((missing >> 1) & 1) << (4 + d)) | (((missing >> 2) & 1) << (8 + d));
		if (((bits & 8) != 0 && (n.x & 8) == 0) || rise > n.y) {
			towerEdges |= 1 << d;
		}
	}

	// the block: the 4 x 4 cells of this cell's tower cell
	int block = 0;
	ivec2 b0 = (cell >> 2) << 2;
	for (int j = 0; j < 4; j++) {
		for (int i = 0; i < 4; i++) {
			block |= wcRawCell(b0 + ivec2(i, j), lv, e).x & 15;
		}
	}
	outMap = vec4(float(bits | (block << 8)), float(rise), float(deckEdges), float(towerEdges)) / 65535.0;
}

#endif
