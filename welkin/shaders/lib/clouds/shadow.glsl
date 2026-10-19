// VOID Welkin: cloud shadows, read in deferred (and by the clouds themselves, for the decks' shadows on each other).
// From the lit surface the sun's ray goes up to each deck's middle (two plane intersections, no march) and reads the
// cloud map there (one texel), at the same ring the march would draw that cell at, so the shadows are the visible
// clouds' own cells. The surface point is first snapped to the world's 16-pixel grid, so a shadow's edge steps with
// the block texels instead of cutting them.
//
// A cloud's shadow takes about two fifths of the sun's direct light under one deck (on open ground, where the sky's
// fill stays, the land is about a third darker than the field beside it), a little more under two or three. Its
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

#ifndef WU_colortex7
#define WU_colortex7
uniform sampler2D colortex7; // the raw cloud map: its smooth fields (review round 2: the far shadows)
#endif

// (review round 2) The raw map's smooth margin field of deck k (0 low, 1 main) at camera-relative q in ring lv, through
// the bilinear filter (as lib/clouds/map.glsl wcField): 0.5 at a cloud's outline, 1 deep inside.
float wcShadowField(vec2 q, int k, int lv, CloudEnv e) {
	int s = WC_CELL << lv;
	ivec2 cell = wcCellOf(q, lv, e);
	vec2 f = (q - wcCellCorner(cell, s, e)) / float(s);
	vec2 local = clamp(vec2(cell - wcMapAnchor(lv, e) + WC_MAP_HALF) + f, vec2(0.5), vec2(float(WC_MAP) - 0.5));
	vec4 t = texture(colortex7, (local + vec2(0.0, float(lv * WC_MAP))) / vec2(float(WC_MAP), float(WC_MAP * 8)));
	return k == 0 ? t.b : t.a;
}

// How far the shadow's edge spreads into a cell from each of its open sides, in blocks.
#define WC_SHADOW_SOFT 5.0
// skies: the field over which a deck's shadow eases in, from the cloud's thin rim (0.5 at the outline) inward
#define WS_SHADOW_LO 0.38
#define WS_SHADOW_HI 0.68
// The sun's share under one, two and three decks.
// (round 2: a little lighter, so one cloud passing over a view dims it, not flattens it)
// (the balance step, 2026-10-13: deeper again, 0.62 / 0.52 / 0.45, so the clouds' shadows lie on the middle and far land as
// clear patches of shade that give a vista its depth; on open ground under one deck the land is about a third darker)
// (air and tone (D), 2026-10-13: 0.42 / 0.34 / 0.28: a cumulus takes most of the sun, so each cloud's shadow reads on
// the vistas as a clear patch of shade between sunlit fields, the strongest cue a wide land has for its depth; the
// sky's fill is untouched, so the patches stay sky-blue and readable, and a clear sky with a few clouds has a few
// patches, never a veil)
// (review round 2: 0.52 / 0.44 / 0.38: one fair cloud over a view dimmed the whole of it to a grey-green dusk at 16:30,
// against the reference packs' lighter cloud shade; the patches still read on the vistas)
#define WC_SHADOW_ONE 0.52
#define WC_SHADOW_TWO 0.44
#define WC_SHADOW_THREE 0.38
// (Look D4) while the sun stands high (past about 30 degrees) a cloud takes more of it: at midday the drifting patches of
// shade are the land's strongest depth cue, and over a sun-led field one deck's shadow reads clearly. The golden hours
// keep the lighter shares above, so a long low-sun shadow never blots out a lit ridge
#define WC_SHADOW_ONE_HIGH 0.34
#define WC_SHADOW_TWO_HIGH 0.28
#define WC_SHADOW_THREE_HIGH 0.24

// How much of deck k's (0 low, 1 main, 2 high) cloud stands over camera-relative point q at altitude `mid`, 0..1: 1
// inside a cloud, easing to a third at an open side of its cell, 0 in a gap.
float wcShadowCover(vec2 q, int k, float mid, CloudEnv e) {
	int lv = wcLevel(length(q));
	// (review round 2) past the first rings a deck's shadow follows its smooth field, not its cells: the far cells are
	// 48 to 1536 blocks wide and their shadows lay on the far land and sea as stepped, jagged dark bands (far0, storm0)
	// skies (welkin-skies, 2026-10-18): near too. The cells' own shadows, squares with a few blocks of soft edge, lay
	// on the land as stepped tiles; the smooth field's shadow is a soft patch with a broad penumbra, drifting with
	// its cloud (one bilinear fetch, cheaper than the cell and its edges)
	float smoothK = k <= 1 ? 1.0 : 0.0;
	float soft = 0.0;
	if (smoothK > 0.0) {
		soft = smoothstep(WS_SHADOW_LO, WS_SHADOW_HI, wcShadowField(q, k, lv, e));
		if (smoothK >= 1.0) {
			return soft;
		}
	}
	ivec2 cell = wcCellOf(q, lv, e);
	ivec4 tx = wcMapCell(cell, lv, e);
	CloudCol c = wcMapDecode(tx.x, tx.y, e);
	if (!wcInside(c, mid)) {
		return mix(0.0, soft, smoothK);
	}
	int edges = (wcIn(c.tower, mid) || wcIn(c.anvil, mid) ? tx.w : tx.z >> (4 * k)) & 15;
	if (edges == 0) {
		return mix(1.0, soft, smoothK);
	}
	int s = WC_CELL << lv;
	float size = float(s);
	vec2 local = clamp(q - wcCellCorner(cell, s, e), vec2(0.0), vec2(size));
	vec4 dist = vec4(local.x, size - local.x, local.y, size - local.y);
	// (D: the soft edge grows with the ring's cells, a third of a cell at most, so a far cloud's shadow on the far land
	// is a soft-edged patch like a near one's, not a hard square)
	vec4 f = mix(vec4(0.33), vec4(1.0), smoothstep(vec4(0.0), vec4(max(WC_SHADOW_SOFT, 0.3 * size)), dist));
	f = mix(vec4(1.0), f, vec4(ivec4(edges, edges >> 1, edges >> 2, edges >> 3) & 1));
	return mix(min(min(f.x, f.y), min(f.z, f.w)), soft, smoothK);
}

// `eyePos`: the surface minus cameraPosition (deferred's eyePos), or any camera-relative point.
float welkinCloudShadow(vec3 eyePos, CloudEnv e) {
#if CLOUD_SHADOWS == 0 || !defined CLOUDS
	return 1.0;
#else
	vec3 l = e.sun;
	// (D: from about 3 degrees, was 5: the morning and evening vistas keep their cloud patches, long but soft)
	float rise = smoothstep(0.05, 0.13, l.y);
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
	float hi = smoothstep(0.3, 0.55, l.y);
	float s1 = mix(WC_SHADOW_ONE, WC_SHADOW_ONE_HIGH, hi);
	float s2 = mix(WC_SHADOW_TWO, WC_SHADOW_TWO_HIGH, hi);
	float s3 = mix(WC_SHADOW_THREE, WC_SHADOW_THREE_HIGH, hi);
	float share = 1.0 - (1.0 - s1) * min(under, 1.0)
		- (s1 - s2) * clamp(under - 1.0, 0.0, 1.0)
		- (s2 - s3) * clamp(under - 2.0, 0.0, 1.0);
	return mix(1.0, share, rise);
#endif
}

#endif
