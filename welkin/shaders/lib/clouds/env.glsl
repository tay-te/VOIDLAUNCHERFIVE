// VOID Welkin: what the clouds know about the sky this frame, in one struct built once per pixel (CloudEnv).
//
// Three sources, by voidMode() (lib/void.glsl):
//   FULL    Gloam's uniforms and the W0/W1 weather tiles.
//   HEADER  the tiles, with the uniforms read back from the header's 16-bit mirror (Gloam's Contract.mirror): the same
//           clouds, the drift a little coarser (W0's shift to half a block).
//   NONE    no Gloam: one front field hashed from a fixed seed, drifting east as vanilla's clouds do, overcast in
//           vanilla rain, storm towers in thunder. The decks come from the world's height exactly as Gloam would place
//           them (CloudGrid.Layers), so a tall Expanse world has its three decks either way.
//
// The clouds' frame: the weather drifts, so cells are counted in the fronts' frame, frame = world + shift. Gloam's
// W1 shift (void_weatherShift.zw) is used for everything; it equals W0's (xy) mod 32768, which is all W0's
// wrap-around addressing needs. The camera's frame position is kept as whole blocks plus a fraction so cells hold
// their precision anywhere in the world.

#ifndef WELKIN_CLOUD_ENV
#define WELKIN_CLOUD_ENV

#include "/lib/common.glsl"
#include "/lib/void.glsl"
#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/clouds/settings.glsl"
#include "/lib/clouds/uniforms.glsl"

#define WC_ABSENT -100000.0  // a deck the world does not have
#define WC_DECK 4.0          // a deck's first tier: its thickness at a cloud's rim (vanilla's)
// (the clouds rework, 2026-10-16: a deck cell is no longer one 4-block slab. Its base stays flat at the deck's height,
// as a real cumulus's does, and its top rises in tiers with how deep inside its cloud the cell lies (the puffs'
// margin over the cover's threshold, plus a hashed nudge so the tops are lumpy, not terraced rings): a fair-weather
// cumulus is a voxel dome, thin at its ragged rim, three or four tiers over its heart. The low stratus takes one
// tier at most; the high deck stays one thin sheet.)
#define WC_TIER 6.0          // each tier over the first 4 blocks (review round 2: 6, was 4: a fair cumulus up to 22 blocks tall, a volume with lit sides rather than a slab)
#define WC_MAIN_TIERS 3      // the main deck: up to 4 + 3 x WC_MAIN_TIER blocks
// (clouds2: the main deck's tiers WC_MAIN_TIER tall, as tall as a cell is wide: the deck stands some 240 blocks over the
// land on a tall world, where a cumulus 22 blocks tall under a sky hundreds of blocks wide read as a flat slab; at 12 a
// fair cumulus is up to 40 blocks tall, voxel cubes stacked into a dome, with lit sides toward the horizon)
#define WC_MAIN_TIER 12.0   // (unused since the heaps below; kept for the docs' history)
// (clouds2, the heaps: the main deck's three tiers are no longer equal steps but rise 15, 44 and 80 blocks over the
// first WC_DECK (wcMainTierHeight): a fair cumulus is a heap up to 84 blocks tall over a cloud 200 to 400 wide, its
// flanks standing as lit walls toward the horizon, instead of a slab eight times as wide as it is tall)
#define WC_MAIN_HEIGHT 80.0
// The height of main-deck tier t (0..3) over the first WC_DECK blocks; and the same for a continuous rise 0..1.
float wcMainTierHeight(int t) {
	return t <= 0 ? 0.0 : (t == 1 ? 15.0 : (t == 2 ? 44.0 : WC_MAIN_HEIGHT));
}
float wcMainHeightAt(float rise) {
	float r = clamp(rise, 0.0, 1.0);
	return WC_MAIN_HEIGHT * r * sqrt(r);
}
#define WC_LOW_TIERS 1       // the low stratus: up to 10

// Gloam's Contract.Slot ordinals: the texel column of each uniform in the header mirror (rows 1..3).
#define WC_SLOT_SEED 2
#define WC_SLOT_FRONT 8
#define WC_SLOT_SHOWERS 11
#define WC_SLOT_MAX_STORM 13
#define WC_SLOT_LIGHT 22
#define WC_SLOT_CLOUD_LAYERS 24
#define WC_SLOT_WEATHER_SHIFT 29

struct CloudEnv {
	int mode;          // voidMode()
	bool w0, w1;       // which weather tiles hold data (both false: the hashed fallback field)
	vec4 layers;       // low, main, high, tower top (world y); a missing deck is WC_ABSENT
	float yLo, yHi;    // every cloud lies between these
	float camY;        // the eye's world y
	ivec2 frameInt;    // the camera in the clouds' frame, whole blocks
	vec2 frameFract;   // and the rest (0..2)
	ivec2 w1Off;       // added to frame blocks to address W1 (0 unless W1's frame differs by whole 32768s)
	ivec2 frame12;     // floor(frameInt / 12): the camera's 12-block cell, so every ring's cell is a shift away
	ivec2 frameRem;    // frameInt - 12 * frame12 (0..11)
	ivec2 w0Centre;    // the tiles' centres, low 8 bits are all that matter (wrapped distances)
	ivec2 w1Centre;
	uint seed;
	bool towers;       // a tower may stand within reach: the march searches the gaps between decks
	float fair;        // the fair-weather cover floor
	float rainFb;      // the fallback field's weather (NONE)
	float stormFb;
	vec3 sun;          // world-space unit vector to the sun
	vec3 lightDir;     // the sun by day, the moon by night
	float moon;        // the moon's light 0..1 (void_light.x), 1 without Gloam
	float darkNight;   // the gameplay sky light scale (void_light.z), 1 without Gloam
	bool flash;        // a lightning bolt is out
};

int wcFloorDiv(int a, int b) {
	return a >= 0 ? a / b : -((b - 1 - a) / b);
}

ivec2 wcFloorDiv(ivec2 a, int b) {
	return ivec2(wcFloorDiv(a.x, b), wcFloorDiv(a.y, b));
}

// Gloam's CloudGrid.Layers.of: the deck stack for a world of this height (one deck on a vanilla-height world, a low
// stratus and the main deck on a taller one, and a thin high deck as well on a tall one).
vec4 wcLayersOf(float cloudY, float maxY) {
	float v = floor(cloudY + 0.5);
	if (maxY - v < 160.0) {
		return vec4(WC_ABSENT, v, WC_ABSENT, max(v + 100.0, maxY - 8.0));
	}
	float low = v + 16.0;
	if (maxY - v < 300.0) {
		return vec4(low, maxY - 40.0, WC_ABSENT, maxY - 8.0);
	}
	float high = maxY - 20.0;
	return vec4(low, low + floor((high - low) * 0.45 + 0.5), high, high);
}

// One float component of a uniform from the header mirror: high byte in row 1, low byte in row 2, over lo..hi.
float wcMirrorFloat(int slot, int comp, float lo, float hi) {
	vec4 a = texelFetch(void_data, ivec2(slot, 1), 0);
	vec4 b = texelFetch(void_data, ivec2(slot, 2), 0);
	float q = floor(a[comp] * 255.0 + 0.5) * 256.0 + floor(b[comp] * 255.0 + 0.5);
	return lo + q / 65535.0 * (hi - lo);
}

// An int from the header mirror (four little-endian bytes in row 1).
int wcMirrorInt(int slot) {
	ivec4 v = ivec4(texelFetch(void_data, ivec2(slot, 1), 0) * 255.0 + 0.5);
	return v.x | (v.y << 8) | (v.z << 16) | (v.w << 24);
}

// A tile's centre from header row 0 (texel 2 + tile: x in RG, z in BA, low 16 bits each).
ivec2 wcHeaderCentre(int tile) {
	ivec4 v = ivec4(texelFetch(void_data, ivec2(2 + tile, 0), 0) * 255.0 + 0.5);
	return ivec2(v.x | (v.y << 8), v.z | (v.w << 8));
}

// How far toward a tile's edge a cell is, as the fade weight (1 inside 112 cells, 0 at 128), measured wrapped so
// the tile's own frame and ours may differ by whole tiles.
float wcCellWeight(ivec2 cell, ivec2 centre) {
	ivec2 d = abs(((cell - centre + 128) & 255) - 128);
	return clamp((127.0 - float(max(d.x, d.y))) / 15.0, 0.0, 1.0);
}

CloudEnv welkinCloudEnv() {
	CloudEnv e;
	e.mode = voidMode();
	e.camY = float(cameraPositionInt.y) + cameraPositionFract.y;
	e.layers = wcLayersOf(cloudHeight, float(bedrockLevel + heightLimit - 1));
	e.fair = float(CLOUD_FAIR) / 100.0;
	e.rainFb = rainStrength;
	e.stormFb = thunderStrength;
	e.towers = thunderStrength > 0.0 || rainStrength > 0.3;
	e.moon = 1.0;
	e.darkNight = 1.0;
	e.w0 = false;
	e.w1 = false;
	e.w0Centre = ivec2(0);
	e.w1Centre = ivec2(0);
	e.seed = 0x5EEDC10Du;
	e.w1Off = ivec2(0);

	// No Gloam: vanilla's drift, 0.03 blocks a tick toward +x, kept in whole blocks plus hundredths so it stays exact
	ivec2 shiftInt = ivec2(0);
	vec2 shiftFract = vec2(0.0);
	int ticks = (worldDay % 1024) * 24000 + worldTime;
	shiftInt.x = -(((ticks * 3) / 100) % 32768);
	shiftFract.x = -float((ticks * 3) % 100) / 100.0;

	if (e.mode != VOID_MODE_NONE) {
		vec4 layers;
		vec2 shift;
		float maxStorm, showers, front;
		if (e.mode == VOID_MODE_FULL) {
			layers = void_cloudLayers;
			shift = void_weatherShift.zw;
			e.seed = uint(void_seed);
			maxStorm = void_maxStorm;
			showers = void_showers;
			front = void_front;
			e.moon = void_light.x;
			e.darkNight = void_light.z;
		} else {
			layers = vec4(wcMirrorFloat(WC_SLOT_CLOUD_LAYERS, 0, -1024.0, 1024.0), wcMirrorFloat(WC_SLOT_CLOUD_LAYERS, 1, -1024.0, 1024.0),
				wcMirrorFloat(WC_SLOT_CLOUD_LAYERS, 2, -1024.0, 1024.0), wcMirrorFloat(WC_SLOT_CLOUD_LAYERS, 3, -1024.0, 1024.0));
			layers = floor(layers + 0.5);
			// W0's shift to half a block (the mirror's 16 bits over 32768); W1's would only be good to 4 blocks
			shift = vec2(wcMirrorFloat(WC_SLOT_WEATHER_SHIFT, 0, 0.0, 32768.0), wcMirrorFloat(WC_SLOT_WEATHER_SHIFT, 1, 0.0, 32768.0));
			// W1 is addressed in its own frame, which is W0's plus whole 32768s; the mirror gives that to 4 blocks
			vec2 shift1 = vec2(wcMirrorFloat(WC_SLOT_WEATHER_SHIFT, 2, 0.0, 262144.0), wcMirrorFloat(WC_SLOT_WEATHER_SHIFT, 3, 0.0, 262144.0));
			e.w1Off = ivec2(floor((shift1 - shift) / 32768.0 + 0.5)) * 32768;
			e.seed = uint(wcMirrorInt(WC_SLOT_SEED));
			maxStorm = wcMirrorFloat(WC_SLOT_MAX_STORM, 0, 0.0, 1.0);
			showers = wcMirrorFloat(WC_SLOT_SHOWERS, 0, 0.0, 1.0);
			front = wcMirrorFloat(WC_SLOT_FRONT, 0, 0.0, 1.0);
			e.moon = wcMirrorFloat(WC_SLOT_LIGHT, 0, 0.0, 1.0);
			e.darkNight = wcMirrorFloat(WC_SLOT_LIGHT, 2, 0.0, 1.0);
		}
		int tileMask = int(texelFetch(void_data, ivec2(10, 0), 0).r * 255.0 + 0.5);
		e.w0 = (tileMask & (1 << VOID_TILE_W0)) != 0;
		e.w1 = (tileMask & (1 << VOID_TILE_W1)) != 0;
		if (e.w1) {
			// Gloam's layers win once it sends a main deck; -1 marks a missing deck
			if (layers.y > -1000.0 && layers.w > layers.y) {
				e.layers = vec4(abs(layers.x + 1.0) < 0.5 ? WC_ABSENT : layers.x, layers.y,
					abs(layers.z + 1.0) < 0.5 ? WC_ABSENT : layers.z, layers.w);
			}
			vec2 sf = floor(shift);
			shiftInt = ivec2(sf);
			shiftFract = shift - sf;
			e.w0Centre = wcHeaderCentre(VOID_TILE_W0);
			e.w1Centre = wcHeaderCentre(VOID_TILE_W1);
			e.towers = maxStorm > 0.0 || showers > 0.0 || front > 0.55;
		} else {
			e.w0 = false; // W0 alone (no far tile) is not worth a second path: the fallback field
		}
	}

	e.frameInt = cameraPositionInt.xz + shiftInt;
	e.frameFract = cameraPositionFract.xz + shiftFract;
	if (e.frameFract.x < 0.0) {
		e.frameFract.x += 1.0;
		e.frameInt.x -= 1;
	}

	e.frame12 = wcFloorDiv(e.frameInt, 12);
	e.frameRem = e.frameInt - e.frame12 * 12;

	e.yLo = e.layers.x > WC_ABSENT * 0.5 ? e.layers.x : e.layers.y;
	e.yHi = max(max(e.layers.w, e.layers.z) + 4.0, e.layers.y + WC_DECK + WC_MAIN_HEIGHT);

	e.sun = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	e.lightDir = e.sun.y > -0.06 ? e.sun : -e.sun;
	e.flash = lightningBoltPosition.w > 0.5;
	return e;
}

#endif
