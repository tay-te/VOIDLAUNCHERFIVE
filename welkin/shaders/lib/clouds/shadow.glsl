// VOID Welkin: cloud shadows, read in deferred (and by the clouds themselves, for the decks' shadows on each other).
// From the lit surface the sun's ray goes up to each deck's middle (two plane intersections, no march) and reads the
// cloud map there (one texel), at the same ring the march would draw that cell at, so the shadows are the visible
// clouds' own cells. The surface point is first snapped to the world's 16-pixel grid, so a shadow's edge steps with
// the block texels instead of cutting them.
//
// A cloud's shadow is light: about a quarter of the sun's direct light under one deck (on open ground, where the
// sky's fill stays, the land is some 15-20 % darker than the field beside it), a little more under two or three. Its
// edge spreads over a few blocks inside the cell's open sides (from the finished map's edge bits, no neighbour read),
// so the shadows lie on the land as soft-edged patches and never tile it in hard squares. They fade in smoothly as
// the sun climbs past about 5 degrees (no pop, and no long streaks at a low sun). Multiply only the direct sun term
// by it (lib/light.glsl's `direct`), never the sky light.
//
// Cost: CLOUD_SHADOWS texel fetches of the cloud map and a little arithmetic, only for pixels the sun reaches (the
// caller checks sky light and the facing first).

#ifndef WELKIN_CLOUD_SHADOW
#define WELKIN_CLOUD_SHADOW

#include "/lib/clouds/map.glsl"

// How far the shadow's edge spreads into a cell from each of its open sides, in blocks.
#define WC_SHADOW_SOFT 5.0
// The sun's share under one, two and three decks.
#define WC_SHADOW_ONE 0.74
#define WC_SHADOW_TWO 0.63
#define WC_SHADOW_THREE 0.56

// How much of deck k's (0 low, 1 main, 2 high) cloud stands over camera-relative point q at altitude `mid`, 0..1: 1
// inside a cloud, easing to a third at an open side of its cell, 0 in a gap.
float wcShadowCover(vec2 q, int k, float mid, CloudEnv e) {
	int lv = wcLevel(length(q));
	ivec2 cell = wcCellOf(q, lv, e);
	ivec4 tx = wcMapCell(cell, lv, e);
	CloudCol c = wcMapDecode(tx.x, tx.y, e);
	if (!wcInside(c, mid)) {
		return 0.0;
	}
	int edges = (wcIn(c.tower, mid) || wcIn(c.anvil, mid) ? tx.w : tx.z >> (4 * k)) & 15;
	if (edges == 0) {
		return 1.0;
	}
	int s = WC_CELL << lv;
	float size = float(s);
	vec2 local = clamp(q - wcCellCorner(cell, s, e), vec2(0.0), vec2(size));
	vec4 dist = vec4(local.x, size - local.x, local.y, size - local.y);
	vec4 f = mix(vec4(0.33), vec4(1.0), smoothstep(vec4(0.0), vec4(WC_SHADOW_SOFT), dist));
	f = mix(vec4(1.0), f, vec4(ivec4(edges, edges >> 1, edges >> 2, edges >> 3) & 1));
	return min(min(f.x, f.y), min(f.z, f.w));
}

// `eyePos`: the surface minus cameraPosition (deferred's eyePos), or any camera-relative point.
float welkinCloudShadow(vec3 eyePos, CloudEnv e) {
#if CLOUD_SHADOWS == 0 || !defined CLOUDS
	return 1.0;
#else
	vec3 l = e.sun;
	float rise = smoothstep(0.08, 0.16, l.y);
	if (rise <= 0.0) {
		return 1.0; // the sun too low: no shadows rather than long streaks
	}
	vec3 snapped = (floor((cameraPositionFract + eyePos) * 16.0) + 0.5) / 16.0 - cameraPositionFract;
	float y = e.camY + snapped.y;
	float under = 0.0;
	for (int i = 0; i < CLOUD_SHADOWS; i++) {
		// the main deck first, then the low stratus, then the high deck
		int k = i == 0 ? 1 : (i == 1 ? 0 : 2);
		float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
		if (!wcPresent(deck)) {
			continue;
		}
		float mid = deck + WC_DECK * 0.5;
		if (mid <= y) {
			continue;
		}
		under += wcShadowCover(snapped.xz + l.xz * ((mid - y) / l.y), k, mid, e);
	}
	float share = 1.0 - (1.0 - WC_SHADOW_ONE) * min(under, 1.0)
		- (WC_SHADOW_ONE - WC_SHADOW_TWO) * clamp(under - 1.0, 0.0, 1.0)
		- (WC_SHADOW_TWO - WC_SHADOW_THREE) * clamp(under - 2.0, 0.0, 1.0);
	return mix(1.0, share, rise);
#endif
}

#endif
