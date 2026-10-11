// VOID Welkin: the VOID bridge (contract version 1, docs/DESIGN.md section 2). VOID Gloam hands the pack world state
// through uniforms (void_*) and one RGBA8 data texture (gloam:pack/data, bound as void_data). The pack must run with
// neither: every read goes through voidMode(), and nothing is fetched from void_data before the magic is checked,
// because without Gloam Iris binds the 16x16 missing texture there.
//
// Naming: void_* belongs to Gloam. The pack's own derived uniforms (smooth() in shaders.properties) are welkin_*.

#ifndef WELKIN_VOID
#define WELKIN_VOID

#define VOID_CONTRACT 1

#define VOID_MODE_NONE    0 // no Gloam data: the pack's built-in defaults
#define VOID_MODE_HEADER  1 // data texture present, uniforms missing (the Iris mixin did not apply): header mirror
#define VOID_MODE_FULL    2 // data texture and uniforms

#define VOID_TILE_N0 0 // near land A, 16 m cells
#define VOID_TILE_N1 1 // near land B
#define VOID_TILE_F0 2 // far land A, 1 km cells
#define VOID_TILE_F1 3 // far land B
#define VOID_TILE_W0 4 // near weather, 128 m cells, drifting frame
#define VOID_TILE_W1 5 // far weather, 1 km cells
#define VOID_TILE_U  6 // underground, 4 m cells

#define VOID_TEX_W 1024
#define VOID_TEX_H 516

uniform sampler2D void_data; // read only with texelFetch: Iris binds it MIPPED_NEAREST_REPEAT whatever Gloam asks
uniform int void_bridge;     // 1 when Gloam's uniform mixin is live; undeclared by Iris means 0
uniform int void_version;    // the contract version Gloam speaks
uniform float void_mistTime; // the hour's and season's part of the mist, 0..1 (times a cell's mist potential)

// Header row 0 (Gloam's Contract): texels 2..9 the tile centres, 10 the mask of tiles holding data, 11 the mask of
// live uniform slots. Rows 1..3: the uniform mirror, one texel column per slot in design table 2.2's order.
#define VOID_TEXEL_CENTRES   2
#define VOID_TEXEL_TILE_MASK 10
#define VOID_TEXEL_LIVE_MASK 11
#define VOID_SLOT_MIST_TIME  16 // void_mistTime's column in the mirror (Contract.Slot.MIST_TIME's ordinal)

// Whether void_data is Gloam's: the right size and texel (0, 0) holding the bytes V O I D. The size test comes first,
// so the missing texture is never read past its 16x16.
bool voidMagic() {
	ivec2 size = textureSize(void_data, 0);
	if (size.x < VOID_TEX_W || size.y < VOID_TEX_H) {
		return false;
	}
	ivec4 m = ivec4(texelFetch(void_data, ivec2(0, 0), 0) * 255.0 + 0.5);
	return m == ivec4(86, 79, 73, 68);
}

// The contract version in the header (texel (1, 0), red), for when the uniforms are missing.
int voidHeaderVersion() {
	return int(texelFetch(void_data, ivec2(1, 0), 0).r * 255.0 + 0.5);
}

// Which channel the pack may read this frame. A newer major contract than VOID_CONTRACT counts as no Gloam.
int voidMode() {
	if (!voidMagic()) {
		return VOID_MODE_NONE;
	}
	if (void_bridge == 1) {
		return void_version >= 1 && void_version <= VOID_CONTRACT ? VOID_MODE_FULL : VOID_MODE_NONE;
	}
	int v = voidHeaderVersion();
	return v >= 1 && v <= VOID_CONTRACT ? VOID_MODE_HEADER : VOID_MODE_NONE;
}

// Where a tile's cell lives in the texture: tiles in a 4x2 grid of 256^2 under four header rows, each cell at its
// world cell mod 256 (wrap-around addressing).
ivec2 voidTexel(int tile, ivec2 cell) {
	return ivec2((tile & 3) * 256, 4 + (tile >> 2) * 256) + (cell & 255);
}

// One cell of a tile. Only call once voidMode() is not VOID_MODE_NONE.
vec4 voidFetch(int tile, ivec2 cell) {
	return texelFetch(void_data, voidTexel(tile, cell), 0);
}

// A cell is valid while its Chebyshev distance from the tile's centre is under 128.
bool voidCellValid(ivec2 cell, ivec2 centre) {
	ivec2 d = abs(cell - centre);
	return max(d.x, d.y) < 128;
}

// The fade weight of a cell toward the tile's edge: 1 inside 112 cells, 0 at 128 (near -> far -> uniforms).
float voidCellWeight(ivec2 cell, ivec2 centre) {
	ivec2 d = abs(cell - centre);
	return clamp((127.0 - float(max(d.x, d.y))) / 15.0, 0.0, 1.0);
}

// A height split across two bytes ("h16"): y = (hi * 256 + lo) / 32 - 1024, so -1024..1024 to 1/32 block.
float voidHeight(float hi, float lo) {
	return (floor(hi * 255.0 + 0.5) * 256.0 + floor(lo * 255.0 + 0.5)) / 32.0 - 1024.0;
}

// The flags nibble of an N1/F1 cell's alpha: bit 0 data present, 1 from Expanse, 2 snow ground, 3 water surface.
int voidFlags(float a) {
	return int(a * 255.0 + 0.5) & 15;
}

// The haze kind in the high nibble of an N1/F1 cell's alpha (table 2.4).
int voidHazeKind(float a) {
	return (int(a * 255.0 + 0.5) >> 4) & 15;
}

// A texel's four bytes.
ivec4 voidBytes(ivec2 texel) {
	return ivec4(texelFetch(void_data, texel, 0) * 255.0 + 0.5);
}

// Whether tile `tile` holds data (header texel 10, red: one bit per tile).
bool voidTileHasData(int tile) {
	return ((voidBytes(ivec2(VOID_TEXEL_TILE_MASK, 0)).r >> tile) & 1) != 0;
}

// The full cell a 16-bit cell (as the header holds a centre) stands for, taken as the one nearest `near`.
int voidUnwrap16(int low16, int near) {
	return near + (((low16 - near + 32768) & 65535) - 32768);
}

// Tile `tile`'s centre cell from the header (RG x, BA z, each the cell's low 16 bits), unwrapped against `near` (the
// camera's cell at the tile's cell size). The header's centre always matches the tile data next to it, so it is read
// in preference to void_nearCentre.
ivec2 voidCentre(int tile, ivec2 near) {
	ivec4 b = voidBytes(ivec2(VOID_TEXEL_CENTRES + tile, 0));
	return ivec2(voidUnwrap16(b.r | (b.g << 8), near.x), voidUnwrap16(b.b | (b.a << 8), near.y));
}

// Whether uniform slot `slot` is live (header texel 11: a 32-bit little-endian mask).
bool voidSlotLive(int slot) {
	ivec4 b = voidBytes(ivec2(VOID_TEXEL_LIVE_MASK, 0));
	return ((b[slot >> 3] >> (slot & 7)) & 1) != 0;
}

// A float uniform's component from the header mirror: 16 bits over lo..hi, the high byte in row 1, the low in row 2.
float voidMirrorFloat(int slot, int component, float lo, float hi) {
	ivec4 hi4 = voidBytes(ivec2(slot, 1));
	ivec4 lo4 = voidBytes(ivec2(slot, 2));
	int h = hi4[component];
	int l = lo4[component];
	return lo + float(h * 256 + l) / 65535.0 * (hi - lo);
}

// void_mistTime in either mode (0 with no Gloam, or when the slot is not live).
float voidMistTime(int mode) {
	if (mode == VOID_MODE_FULL) {
		return clamp(void_mistTime, 0.0, 1.0);
	}
	if (mode == VOID_MODE_HEADER && voidSlotLive(VOID_SLOT_MIST_TIME)) {
		return voidMirrorFloat(VOID_SLOT_MIST_TIME, 0, 0.0, 1.0);
	}
	return 0.0;
}

// Four fetches and a bilinear blend at a fractional cell position (the texture itself is never filtered).
vec4 voidBilinear(int tile, vec2 cellPos) {
	vec2 p = cellPos - 0.5;
	ivec2 c = ivec2(floor(p));
	vec2 f = p - vec2(c);
	vec4 a = voidFetch(tile, c);
	vec4 b = voidFetch(tile, c + ivec2(1, 0));
	vec4 d = voidFetch(tile, c + ivec2(0, 1));
	vec4 e = voidFetch(tile, c + ivec2(1, 1));
	return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

#endif
