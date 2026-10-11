// VOID Welkin, underground (M5, P6): the shared part. What the camera is in (UgState), the U tile read either at one
// cell (UgCell) or blended smoothly between the four cells round a point (UgMix), the fog's reach for a given size of
// space, and the palettes. Included by ug_surface.glsl (deferred), ug_atmosphere.glsl (composite2) and ug_debug.glsl;
// a program never includes it itself. The options (UG_*) are in lib/settings.glsl (the option table, module
// "underground").
//
// The look (2026-10-11): everything here is smooth. The tile is read with bilinear weights, so the fog's reach, the
// water's colour, the damp and the glowworm light blend across the 4 m cells instead of switching at their edges;
// ramps are smoothsteps or softBands; nothing is dithered. Palettes are picked with one-hot weights and constant
// matrices (branch-free maths): a const array indexed at run time spills to local memory on NVIDIA (about 3 ms over a
// frame in the haze module's measurement).
//
// Inputs (bridge contract v1, docs/DESIGN.md 2.2 and 2.3, ids from Gloam's CaveKinds):
//   void_underground  x depth factor 0..1 (0 at a cave mouth .. 1 about 48 blocks down), y openness 0..1 (0 a tight
//                     passage .. 1 a great cavern), z realm id, w water kind id
//   void_cave         x glowworms, y bats, z drip, w great-river nearness; each 0..1, already faded by `under`
//   U tile (tile 6)   4 m cells round the U centre, at the centre's height (encoding: the staged module's NOTES.md)
//   header row 0      texel 10 R: tiles holding data; texel 11: the live uniform slots
//   header rows 1..3  the uniform mirror (slot 25 void_underground, 26 void_cave, 30 void_ugCentre), Contract.java
//
// Fallbacks, in order: the uniforms (Gloam and its Iris mixin live) -> the header mirror (mixin missing) -> the light
// level alone (no Gloam, or a Gloam without the underground slots): a plain cave fog, no tile features.

#ifndef WELKIN_UG_COMMON
#define WELKIN_UG_COMMON

#include "/lib/common.glsl"
#include "/lib/void.glsl"
#include "/lib/grid.glsl"

#ifndef VOID_U_UNDERGROUND
#define VOID_U_UNDERGROUND
#ifndef WU_void_underground
#define WU_void_underground
uniform vec4 void_underground;
#endif
#endif
#ifndef VOID_U_CAVE
#define VOID_U_CAVE
#ifndef WU_void_cave
#define WU_void_cave
uniform vec4 void_cave;
#endif
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;
#endif
#ifndef WU_eyeBrightnessSmooth
#define WU_eyeBrightnessSmooth
uniform ivec2 eyeBrightnessSmooth;
#endif
#ifndef WU_isEyeInWater
#define WU_isEyeInWater
uniform int isEyeInWater;
#endif

// ---------------------------------------------------------------------------------------------- the contract

// water kinds (U tile high nibble, void_underground.w)
#define UG_WATER_NONE  0
#define UG_WATER_RIVER 1
#define UG_WATER_SEA   2
#define UG_WATER_LAKE  3
#define UG_WATER_LAVA  4
// realms (void_underground.z): 0 none, 1 wilds, 2 lush, 3 dripstone, 4 crystal, 5 ember, 6 mere, 7 frozen, 8 roots,
// 9 fossil, 10 tidal, 11 amber, 12 brimstone, 13 under a buried sea
#define UG_REALMS 14
// U tile low nibble: column flags
#define UG_F_GLOW    1 // glowworm silk hangs from the roof
#define UG_F_DRIP    2 // water drips from the roof
#define UG_F_GREAT   4 // over or by one of the great rivers
#define UG_F_PRESENT 8 // the cell holds data (Gloam's TileCodec.UG_PRESENT); a cell without it is unknown, not rock

// header slots (Contract.Slot ordinals) and their live-mask bits
#define UG_SLOT_UNDERGROUND 25
#define UG_SLOT_CAVE        26
#define UG_SLOT_UG_CENTRE   30
// U cells are 4 blocks; a cell is used while its Chebyshev distance from the centre is under UG_REACH (the design's
// 128^2 cells; set 128 if Gloam fills the whole 256^2 tile), fading over the last 8.
#define UG_CELL_SHIFT 2
#define UG_REACH 64

#define UG_MODE_OFF   0 // not underground (or not the overworld): nothing to do
#define UG_MODE_PLAIN 1 // no underground data: fog from the light level only
#define UG_MODE_EYE   2 // the eye's values from Gloam, no U tile
#define UG_MODE_TILE  3 // the eye's values and the U tile

#define UG_GLOW_COLOR  vec3(0.15, 0.85, 0.70) // glowworm light on the rock
#define UG_GLOW_SPECK  vec3(0.45, 1.00, 0.85) // a glowworm itself
#define UG_EMBER_COLOR vec3(1.00, 0.45, 0.15) // the heat off lava

// ---------------------------------------------------------------------------------------------- palettes

// One-hot weights of an id against four consecutive ids from `first`: branch-free picks from constant matrices.
vec4 ugHot(int id, int first) {
	return vec4(equal(ivec4(id), ivec4(first, first + 1, first + 2, first + 3)));
}

// The water kinds 1..4 (river, sea, lake, lava) as one-hot weights; all 0 for no water.
vec4 ugKindHot(int kind) {
	return ugHot(kind, UG_WATER_RIVER);
}

// How clear each realm's air is: a multiplier on the fog's reach.
float ugRealmClear(int realm) {
	int r = clamp(realm, 0, UG_REALMS - 1);
	return dot(ugHot(r, 0), vec4(1.0, 1.0, 0.9, 0.8)) + dot(ugHot(r, 4), vec4(1.25, 0.75, 0.85, 1.2))
		+ dot(ugHot(r, 8), vec4(0.9, 1.1, 0.85, 1.0)) + dot(ugHot(r, 12), vec4(0.7, 1.3, 1.0, 1.0));
}

// The fog's body colour in each realm: dark, a little fantastical. Display values, like the scene.
const mat4x3 UG_REALM_A = mat4x3(
	vec3(0.060, 0.066, 0.078),  // none: the passages, slate
	vec3(0.066, 0.076, 0.086),  // wilds: cool grey-blue
	vec3(0.050, 0.098, 0.064),  // lush: moss
	vec3(0.100, 0.080, 0.058)); // dripstone: umber
const mat4x3 UG_REALM_B = mat4x3(
	vec3(0.084, 0.062, 0.128),  // crystal: violet
	vec3(0.120, 0.052, 0.040),  // ember: smoky red-brown
	vec3(0.044, 0.092, 0.104),  // mere: teal-blue
	vec3(0.086, 0.112, 0.138)); // frozen: ice
const mat4x3 UG_REALM_C = mat4x3(
	vec3(0.082, 0.080, 0.050),  // roots: earthy olive
	vec3(0.104, 0.096, 0.080),  // fossil: bone
	vec3(0.040, 0.104, 0.106),  // tidal: sea teal
	vec3(0.126, 0.090, 0.040)); // amber: honey
const mat4x3 UG_REALM_D = mat4x3(
	vec3(0.126, 0.080, 0.036),  // brimstone: sulphur ochre
	vec3(0.040, 0.060, 0.132),  // under a buried sea: cobalt
	vec3(0.0), vec3(0.0));

vec3 ugRealmColor(int realm) {
	int r = clamp(realm, 0, UG_REALMS - 1);
	return UG_REALM_A * ugHot(r, 0) + UG_REALM_B * ugHot(r, 4) + UG_REALM_C * ugHot(r, 8) + UG_REALM_D * ugHot(r, 12);
}

// The water kinds' colours, as matrices over the river, sea, lake and lava weights; what is left of the weights is
// "no water". `w` may be one-hot (ugKindHot) or a blend of cells (UgMix.water).
#define UG_NONE_LOW  vec3(0.060, 0.080, 0.090)
#define UG_NONE_DEEP vec3(0.030, 0.066, 0.084)
const mat4x3 UG_WATER_LOW = mat4x3(vec3(0.050, 0.110, 0.096), vec3(0.040, 0.070, 0.150), vec3(0.050, 0.104, 0.120),
	vec3(0.190, 0.070, 0.030));
const mat4x3 UG_WATER_DEEP = mat4x3(vec3(0.030, 0.090, 0.072), vec3(0.026, 0.046, 0.130), vec3(0.030, 0.084, 0.102),
	UG_NONE_DEEP);

float ugWaterSum(vec4 w) {
	return dot(w, vec4(1.0));
}

// What the water lends the air just over it (the low stratum of the fog).
vec3 ugWaterLow(vec4 w) {
	return UG_WATER_LOW * w + UG_NONE_LOW * (1.0 - ugWaterSum(w));
}

// The colour deep water absorbs toward (lava's body is not water: the plain colour). `lake` replaces the lake's own
// colour (it takes some of the realm's).
vec3 ugWaterDeep(vec4 w, vec3 lake) {
	mat4x3 m = UG_WATER_DEEP;
	m[2] = lake;
	return m * w + UG_NONE_DEEP * (1.0 - ugWaterSum(w));
}

// Blocks of water to about two thirds absorbed.
float ugWaterClear(vec4 w) {
	return dot(w, vec4(5.0, 9.0, 7.0, 6.0)) + 6.0 * (1.0 - ugWaterSum(w));
}

// ---------------------------------------------------------------------------------------------- the state

// One U cell, eye-relative. Heights are eye-relative y (the eye at 0). Rock cells (space 0) carry the column flags and
// water kind but no floor or roof of their own: those fall back to the eye's. Used by the debug view.
struct UgCell {
	float valid;  // 0 outside the tile or no data, 1 inside, fading at the edge
	float space;  // blocks of open space at the tile's height (0 in rock)
	float floorY; // the floor, or the water's surface over it
	float ceilY;  // the roof
	int kind;     // UG_WATER_*
	int flags;    // UG_F_*
};

// The U tile at a point, blended between the four cells round it (bilinear weights on the 4 m grid).
struct UgMix {
	float valid;   // how much of the point the tile covers: the bilinear weights of cells with data, times the edge fade
	float open;    // the share of that which is open space (not rock), 0..1
	float density; // the open cells' fog density (1 / blocks), their weighted mean; 0 where none is open
	float floorY;  // the open cells' floor (or water surface), weighted, eye-relative; the frame's where none is open
	float ceilY;   // the same for the roof
	vec4 water;    // the share of the valid cells over river, sea, lake and lava water, 0..1 each
	vec3 flags;    // the share with glowworms, drip, great river: of the open cells where there are any (a wall takes
	               // the flags of the space it faces), else of all the valid cells
};

struct UgState {
	int mode;     // UG_MODE_*
	float amount; // how much the underground look applies, 0..1: eased by Gloam (or by the light level), never stepped
	float open;   // openness round the eye, 0..1
	int realm;
	int kind;     // the nearest cavern water's kind
	vec4 cave;    // glowworms, bats, drip, great-river nearness
	ivec3 centre; // the U tile's centre, blocks (x, height of the slab, z)
	float light;  // brightness of fog and mist: follows the block light at the eye, in soft bands
	float clear;  // the realm's clearness (ugRealmClear), once per pixel
	vec3 fog;     // the realm's fog colour (ugRealmColor), once per pixel
	UgMix eye;    // the tile round the eye (tile mode)
};

UgMix ugNoMix() {
	return UgMix(0.0, 0.0, 0.0, -8.0, 8.0, vec4(0.0), vec3(0.0));
}

// ---------------------------------------------------------------------------------------------- reach

// The fog's reach (blocks to about two thirds) for a space `size` blocks across: about 20 in a passage, 100 in a
// cavern, 300 over a buried sea; `clear` is the realm's.
float ugReach(float size, float clear) {
	return clamp(10.0 + size * 4.5, 14.0, 320.0) * clear;
}

// The same from the eye's 0..1 openness (Gloam's smoothstep(1.5, 14, mean space) of the ring round the eye).
float ugReachOpen(float open, float clear) {
	return ugReach(mix(2.0, 40.0, open), clear);
}

// ---------------------------------------------------------------------------------------------- header reads

int ugBytes(ivec2 texel) {
	ivec4 b = ivec4(texelFetch(void_data, texel, 0) * 255.0 + 0.5);
	return b.x | (b.y << 8) | (b.z << 16) | (b.w << 24);
}

// A vec4 slot of the mirror: 16 bits a component, high byte in row 1, low in row 2, from lo..hi.
vec4 ugMirror4(int slot, vec4 lo, vec4 hi) {
	vec4 a = floor(texelFetch(void_data, ivec2(slot, 1), 0) * 255.0 + 0.5);
	vec4 b = floor(texelFetch(void_data, ivec2(slot, 2), 0) * 255.0 + 0.5);
	return lo + (a * 256.0 + b) / 65535.0 * (hi - lo);
}

ivec3 ugMirrorIvec3(int slot) {
	return ivec3(ugBytes(ivec2(slot, 1)), ugBytes(ivec2(slot, 2)), ugBytes(ivec2(slot, 3)));
}

// ---------------------------------------------------------------------------------------------- cells

// The bytes of U cell `cell` and how far it may be used: 0 without data or past the reach, fading over the last 8
// cells. Branch-free: a cell past the reach is still fetched (the tile wraps), and weighs 0.
float ugCellWeight(UgState s, ivec2 cell, out ivec4 b) {
	ivec2 d = abs(cell - (s.centre.xz >> UG_CELL_SHIFT));
	b = ivec4(voidFetch(VOID_TILE_U, cell) * 255.0 + 0.5);
	float w = clamp(float(UG_REACH - max(d.x, d.y)) / 8.0, 0.0, 1.0);
	return (b.a & UG_F_PRESENT) != 0 ? w : 0.0;
}

// The one cell a point is in (the debug view's reading).
UgCell ugCell(UgState s, vec3 eyePos) {
	UgCell c = UgCell(0.0, 0.0, s.eye.floorY, s.eye.ceilY, UG_WATER_NONE, 0);
	if (s.mode != UG_MODE_TILE) {
		return c;
	}
	ivec2 cell = (cameraPositionInt.xz + ivec2(floor(cameraPositionFract.xz + eyePos.xz))) >> UG_CELL_SHIFT;
	ivec4 b;
	c.valid = ugCellWeight(s, cell, b);
	if (c.valid <= 0.0) {
		return c;
	}
	c.space = float(b.r) * 0.5;
	if (b.r > 0) {
		float base = float(s.centre.y - cameraPositionInt.y) - cameraPositionFract.y;
		c.floorY = base + float(b.g - 128);
		c.ceilY = base + float(b.b - 128);
	}
	c.kind = b.a >> 4;
	c.flags = b.a & 15;
	return c;
}

// The tile at `eyePos` (eye-relative), blended between the four cells whose centres surround it. Four fetches.
UgMix ugMix(UgState s, vec3 eyePos) {
	UgMix m = ugNoMix();
	m.floorY = s.eye.floorY;
	m.ceilY = s.eye.ceilY;
	if (s.mode != UG_MODE_TILE) {
		return m;
	}
	// the point in cells, measured from the centre of the cell holding its block, with the camera's integer part
	// kept apart (exact 30 million blocks out)
	vec2 local = cameraPositionFract.xz + eyePos.xz;
	vec2 lf = floor(local);
	ivec2 block = cameraPositionInt.xz + ivec2(lf);
	vec2 pos = (vec2(block & 3) + (local - lf)) * 0.25 - 0.5;
	vec2 pf = floor(pos);
	ivec2 c0 = (block >> UG_CELL_SHIFT) + ivec2(pf);
	vec2 f = pos - pf;
	float base = float(s.centre.y - cameraPositionInt.y) - cameraPositionFract.y;
	float sumW = 0.0;
	float sumO = 0.0;
	float sumD = 0.0;
	float sumF = 0.0;
	float sumC = 0.0;
	vec4 water = vec4(0.0);
	vec3 flags = vec3(0.0);
	vec3 flagsO = vec3(0.0);
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i & 1, i >> 1);
		vec2 bw2 = mix(1.0 - f, f, vec2(o));
		ivec4 b;
		float w = bw2.x * bw2.y * ugCellWeight(s, c0 + o, b);
		int kind = b.a >> 4;
		water += w * ugKindHot(kind);
		vec3 fl3 = vec3(notEqual(ivec3(b.a) & ivec3(UG_F_GLOW, UG_F_DRIP, UG_F_GREAT), ivec3(0)));
		flags += w * fl3;
		sumW += w;
		// open cells: their floor, roof and fog density (the vault speaks for the hall: Expanse's space is the
		// distance to the nearest rock, which at the slab's height is often the floor)
		float wo = b.r > 0 ? w : 0.0;
		flagsO += wo * fl3;
		float fl = base + float(b.g - 128);
		float ce = base + float(b.b - 128);
		float size = max(float(b.r) * 0.5, 0.4 * (ce - fl));
		float d = (kind == UG_WATER_LAVA ? 1.3 : 1.0) / ugReach(size, s.clear); // lava: smoke
		sumO += wo;
		sumD += wo * d;
		sumF += wo * fl;
		sumC += wo * ce;
	}
	float inv = 1.0 / max(sumW, 1e-4);
	m.valid = sumW;
	m.open = sumO * inv;
	m.water = water * inv;
	float invO = 1.0 / max(sumO, 1e-4);
	m.density = sumD * invO;
	// floor, roof and flags: the open cells', handing over to the frame's (or all cells') where hardly any is open
	float k = smoothstep(0.0, 0.25, sumO);
	m.flags = mix(flags * inv, flagsO * invO, k);
	m.floorY = mix(s.eye.floorY, sumF * invO, k);
	m.ceilY = mix(s.eye.ceilY, sumC * invO, k);
	return m;
}

// ---------------------------------------------------------------------------------------------- the state

UgState ugState() {
	UgState s;
	s.mode = UG_MODE_OFF;
	s.amount = 0.0;
	s.open = 0.0;
	s.realm = 0;
	s.kind = UG_WATER_NONE;
	s.cave = vec4(0.0);
	s.centre = cameraPositionInt;
	s.eye = ugNoMix();
	s.clear = 1.0;
	s.fog = ugRealmColor(0);
	float blockEye = float(eyeBrightnessSmooth.x) / 240.0;
	s.light = mix(0.7, 1.15, softBands(blockEye, 3.0)) * UG_FOG_BRIGHTNESS;
#if defined WORLD_NETHER || defined WORLD_END
	return s;
#else
	int vm = voidMode();
	bool live = false;
	if (vm != VOID_MODE_NONE) {
		int liveMask = ugBytes(ivec2(VOID_TEXEL_LIVE_MASK, 0));
		int need = (1 << UG_SLOT_UNDERGROUND) | (1 << UG_SLOT_CAVE) | (1 << UG_SLOT_UG_CENTRE);
		if ((liveMask & need) == need) {
			live = true;
			vec4 u;
			if (vm == VOID_MODE_FULL) {
				u = void_underground;
				s.cave = void_cave;
			} else {
				u = ugMirror4(UG_SLOT_UNDERGROUND, vec4(0.0), vec4(1.0, 1.0, 255.0, 255.0));
				s.cave = ugMirror4(UG_SLOT_CAVE, vec4(0.0), vec4(1.0));
			}
			s.amount = clamp(u.x, 0.0, 1.0);
			s.open = clamp(u.y, 0.0, 1.0);
			s.realm = clamp(int(u.z + 0.5), 0, UG_REALMS - 1);
			s.kind = clamp(int(u.w + 0.5), 0, 4);
			s.clear = ugRealmClear(s.realm);
			s.fog = ugRealmColor(s.realm);
			if (s.amount > 0.0) {
				s.mode = UG_MODE_EYE;
				// the centre the tile was written with comes in the same upload as the tile: the mirror, not the uniform
				if (voidTileHasData(VOID_TILE_U)) {
					s.centre = ugMirrorIvec3(UG_SLOT_UG_CENTRE);
					s.mode = UG_MODE_TILE;
					// the tile round the eye; where it is all rock (a wall's edge, or the slab off the eye's height)
					// the floor and roof stay 8 blocks under and over the eye
					s.eye = ugMix(s, vec3(0.0));
				}
			}
		}
	}
	if (!live) {
		// no underground data: under rock by the light level, with a plain cave fog
		float skyEye = float(eyeBrightnessSmooth.y) / 240.0;
		float low = 1.0 - smoothstep(46.0, 54.0, float(cameraPositionInt.y) + cameraPositionFract.y);
		s.amount = (1.0 - smoothstep(0.05, 0.3, skyEye)) * low;
		s.open = 0.15; // a passage-like reach, about 45 blocks
		s.mode = s.amount > 0.0 ? UG_MODE_PLAIN : UG_MODE_OFF;
	}
	return s;
#endif
}

#endif
