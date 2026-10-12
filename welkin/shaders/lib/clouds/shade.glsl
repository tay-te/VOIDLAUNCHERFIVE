// VOID Welkin: the clouds' colours. The cells stay pixel-crisp voxels: each face is one colour sideways (the way
// vanilla shades a block's faces), lit by the same light as the land (the look of 2026-10-11: smooth gradients, no
// visible dither, directional sunlight first):
//
//   - the light is the sky table's (lib/sky/table.glsl): its ambient by how much of the dome a face sees (tops all of
//     it, undersides least), plus its direct light (the sun by day, the moon by night, as lightDirWorld()) by a
//     wrapped diffuse, since light scatters through a cloud: the shade side is darker, never black, and a little of
//     the light reaches the underside. So a cloud is exactly as bright as a white block lying in the same light, from
//     the noon sun through overcast, golden hour, a moonlit night and dark nights; lightning lights it with the land;
//   - looking toward the light, sides and undersides brighten smoothly (light scattered forward through the edges);
//   - a side face is darker at its foot than at its brow, a smooth gradient up the deck (4 blocks) or up the tower
//     (base to top), stepped on the cloud's 16-pixel grid so the bands are texels of the voxel, not a blur;
//   - round sunrise and sunset the faces take Gloam's dusk palette (docs/STYLE.md, CloudDeck), dimming as the sun goes
//     down and weaker under an overcast: toward the sun cream, gold and orange tops (farther is warmer), orange sides
//     toward it, rose undersides; away from it lilac and violet tops, dusk-blue undersides. Both halves blend across
//     the sky, with no line between them, and the palette fades in and out smoothly with the sun's height;
//   - a soft shoulder keeps a noon-lit top just under white, so it still reads against its sides;
//   - showers grey a cell, storms more (per cell, so a storm cell is crisp against a fair one); lightning lifts the
//     storm cells while a bolt is out;
//   - far cells sink smoothly into the sky's horizon colour in their direction (toward the sun its glow) with
//     distance: one curve over every ring and the sheets, so no ring edge shows.
//
// Per cloud pixel: about eight sky-table texel fetches and no loops.

#ifndef WELKIN_CLOUD_SHADE
#define WELKIN_CLOUD_SHADE

#include "/lib/clouds/march.glsl"
#include "/lib/fade.glsl"

#define WC_CREAM vec3(1.00, 0.94, 0.82)
#define WC_GOLD vec3(1.00, 0.84, 0.55)
#define WC_ORANGE vec3(1.00, 0.66, 0.40)
#define WC_ROSE vec3(0.94, 0.60, 0.64)
#define WC_LILAC vec3(0.86, 0.84, 0.93)
#define WC_VIOLET vec3(0.64, 0.55, 0.76)
#define WC_DUSK vec3(0.42, 0.48, 0.68)

// How a side face's light runs up it: its foot this much darker, its brow this much lighter.
#define WC_SIDE_FOOT 0.88
#define WC_SIDE_BROW 1.06

float wcLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Where on its own height a side face's point lies, 0 at the foot .. 1 at the brow, on the cloud's 16-pixel grid:
// a deck's 4 blocks, or the tower from its base to the tower top.
float wcSideHeight(float y, CloudEnv e) {
	float yq = (floor(y * 16.0) + 0.5) / 16.0;
	for (int k = 0; k < 3; k++) {
		float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
		if (wcPresent(deck) && yq >= deck && yq <= deck + WC_DECK) {
			return (yq - deck) / WC_DECK;
		}
	}
	float base = wcTowerBase(e);
	return clamp((yq - base) / max(e.layers.w + WC_DECK - base, 1.0), 0.0, 1.0);
}

// A soft shoulder over 0.8, so a noon-lit top stays just under white and still reads against its sides.
vec3 wcShoulder(vec3 c) {
	return mix(c, 0.8 + 0.2 * (1.0 - exp(-(c - 0.8) * 5.0)), step(0.8, c));
}

// A cell's own colour by its tone: fair white, showers grey, storm slate.
vec3 wcAlbedo(int tone) {
	return tone == 0 ? vec3(0.97, 0.97, 0.98) : (tone == 1 ? vec3(0.78, 0.79, 0.83) : vec3(0.54, 0.56, 0.63));
}

// How much the dusk palette colours the clouds: 0 by day and by night, 1 while the sun is near the horizon, smooth.
float wcDuskAmount(float sunY) {
	return smoothstep(-0.18, -0.07, sunY) * (1.0 - smoothstep(0.10, 0.28, sunY));
}

// The continuous ring of a distance t: 0 inside WC_RING0 / 2, 1 at WC_RING0, one more at each doubling.
float wcFar(float t) {
	return max(log2(max(t, 1.0) / WC_RING0) + 1.0, 0.0);
}

// 0 for a view away from the sun's side of the sky .. 1 toward it (horizontally).
float wcSunward(vec3 rd, CloudEnv e) {
	return smoothstep(-0.35, 0.35, dot(normalize(rd.xz + vec2(1e-5)), normalize(e.sun.xz + vec2(1e-5))));
}

// Gloam's dusk palette for a face (CloudDeck.band). `sunward` 0..1 (wcSunward), `far` the continuous ring (0 near ..
// 5 and more far), `sun` the sun's direction. A side is its top's colour in less light (warmer turned to the sun, a
// shade darker turned away), never a hue of its own, so the thin sides under a cloud's outline read as its thickness
// and not as a bright lilac sliver.
vec3 wcDusk(vec3 n, float far, float sunward, vec3 sun) {
	vec3 sunTop = mix(mix(WC_CREAM, WC_GOLD, smoothstep(1.0, 2.5, far)), WC_ORANGE, smoothstep(3.0, 4.5, far));
	vec3 awayTop = mix(WC_LILAC, WC_VIOLET, smoothstep(2.0, 3.5, far));
	vec3 top = mix(awayTop, sunTop, sunward);
	if (n.y > 0.5) {
		return top;
	}
	if (n.y < -0.5) {
		return mix(WC_DUSK, WC_ROSE, sunward);
	}
	float toward = dot(n.xz, normalize(sun.xz + vec2(1e-5)));
	vec3 lit = mix(top, WC_ORANGE, 0.3 * sunward);
	return mix(top * 0.74, lit * 0.96, smoothstep(-0.5, 0.6, toward));
}

// The sky's horizon colour in the direction rd: the plain horizon, warming into the horizon toward the sun inside the
// sun's glow (as wide as the sky table says the glow reaches).
vec3 wcHorizon(vec3 rd, CloudEnv e) {
	float spread = max(skyTable(SKY_X_STATE_GLOW, SKY_ROW_LIGHT).a * 2.0, 0.05);
	float toward = 1.0 - smoothstep(0.0, spread, 1.0 - dot(rd, e.sun));
	return mix(skyHorizon(false), skyHorizon(true), toward);
}

// How deep inside the cloud's footprint the point `p` (camera-relative) of a cap face lies: 0 on an open edge of its
// cell (one whose neighbour lacks this deck, tower or anvil) .. 1 a whole cell in from every open edge, smooth. A cell
// with no open edge is all interior. One texel of the finished map (its edge bits); a side face is on an edge: 0.
float wcInterior(CloudHit h, vec3 p, float y, CloudEnv e) {
	if (abs(h.n.y) < 0.5) {
		return 0.0;
	}
	ivec4 tx = wcMapCell(h.cell, h.level, e);
	int s = WC_CELL << h.level;
	float size = float(s);
	vec2 local = clamp(p.xz - wcCellCorner(h.cell, s, e), vec2(0.0), vec2(size));
	CloudCol c = wcMapDecode(tx.x, tx.y, e);
	float inside = y - h.n.y * 0.01;
	int edges = wcIn(c.tower, inside) || wcIn(c.anvil, inside) ? tx.w
		: tx.z >> (wcIn(c.main, inside) ? 4 : (wcIn(c.high, inside) ? 8 : 0));
	// each open edge darkens toward the inside on its own and the shares multiply, so a corner has no crease
	vec4 dist = vec4(local.x, size - local.x, local.y, size - local.y) / size;
	vec4 f = smoothstep(vec4(0.0), vec4(1.0), dist);
	f = mix(vec4(1.0), f, vec4(ivec4(edges, edges >> 1, edges >> 2, edges >> 3) & 1));
	return f.x * f.y * f.z * f.w;
}

// A face with normal n, `up` its side height (0.5 for tops and undersides), `interior` how deep in the cloud's
// footprint a cap's point lies (wcInterior), in light from direction l, seen along rd.
//   - tops see the whole dome and take the light by how high it stands; a cloud's top is a touch brighter toward its
//     rim and greyer toward its thick middle, so a broad top is not one flat colour;
//   - sides take the light by how squarely they face it, wrapped (light scatters through a cloud), and darken toward
//     their foot;
//   - undersides see little of the dome and almost none of the light: a little leaks in near an open edge, and the
//     middle of a big cloud's base is its darkest part.
vec3 wcLit(vec3 n, float up, float interior, int tone, vec3 rd, vec3 l) {
	bool top = n.y > 0.5, under = n.y < -0.5;
	float ndl = dot(n, l);
	float skyView, diffuse, ao;
	if (top) {
		skyView = 1.0;
		diffuse = clamp((ndl + 0.25) / 1.25, 0.0, 1.0);
		ao = mix(1.02, 0.93, interior);
	} else if (under) {
		// (kept gentle: the interior is worked out cell by cell, so in a patchy deck a strong one outlined every cell
		// as a tile on the ceiling)
		skyView = 0.80;
		diffuse = 0.15 + 0.07 * (1.0 - interior);
		ao = mix(0.97, 0.91, interior);
	} else {
		skyView = 0.86;
		diffuse = clamp((ndl + 0.45) / 1.45, 0.0, 1.0) * 0.95;
		ao = mix(0.78, 1.04, up);
	}
	// forward scatter: looking toward the light, the sides and undersides glow at the edges
	float forward = pow(clamp(dot(rd, l), 0.0, 1.0), 8.0) * (top ? 0.10 : (under ? 0.30 * (1.0 - interior) : 0.40));
	vec3 light = skyAmbientLight() * skyView + skyDirectLight() * (diffuse + forward);
	return wcAlbedo(tone) * light * ao;
}

// The colour of a cloud face at altitude `y`, `interior` as wcInterior.
vec3 welkinCloudFace(CloudHit h, vec3 rd, float y, float interior, CloudEnv e) {
	float up = abs(h.n.y) > 0.5 ? 0.5 : wcSideHeight(y, e);
	vec3 c = wcLit(h.n, up, interior, h.tone, rd, lightDirWorld());
	// round sunrise and sunset Gloam's palette at its own brightness, dimming as the sun goes down (never darker than
	// the plain light), weaker under an overcast
	float dusk = wcDuskAmount(e.sun.y) * (1.0 - 0.6 * skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a);
	if (dusk > 0.0) {
		vec3 p = wcDusk(h.n, wcFar(h.t), wcSunward(rd, e), e.sun) * (h.tone == 2 ? 0.7 : (h.tone == 1 ? 0.85 : 1.0));
		float face = h.n.y > 0.5 ? mix(1.02, 0.93, interior)
			: (h.n.y < -0.5 ? mix(0.88, 0.82, interior) : 0.94 * mix(WC_SIDE_FOOT, WC_SIDE_BROW, up));
		vec3 d = p * face * mix(0.40, 0.90, smoothstep(-0.16, 0.08, e.sun.y));
		d *= max(1.0, wcLuma(c) / max(wcLuma(d), 1e-3));
		c = mix(c, d, dusk);
	}
	// a closed overcast's base is the sky: the overcast dome's own colour in that direction (the horizon's low down, the
	// zenith's overhead, a touch darker in a thick middle), not a dark slab over a brighter haze, nor a pale sheet
	// brighter than the grey sky round it
	if (h.n.y < -0.5) {
		float closed = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
		vec3 dome = mix(wcHorizon(rd, e), skyZenith(), smoothstep(0.02, 0.7, rd.y)) * mix(0.97, 0.92, interior);
		c = mix(c, dome, closed);
	}
	if (e.flash && h.tone == 2) {
		c = mix(c, vec3(0.86, 0.86, 1.0), 0.5);
	}
	return wcShoulder(c);
}

// How far a hit sinks into the horizon colour: none near, then smoothly more with each doubling of the distance
// (the old per-ring steps 0.12, 0.27, 0.42 ... laid on one curve), a little more toward the sheets, more in rain.
float wcFog(CloudHit h) {
	float f = clamp((wcFar(h.t) - 1.2) * 0.13, 0.0, 0.8);
	f = min(f + 0.1 * smoothstep(0.6 * WC_REACH, WC_REACH, h.t), 0.85);
	return mix(f, min(1.0, f * 1.4 + 0.08), smoothstep(0.2, 0.8, rainStrength));
}

// How thin a side face is on screen, 0 (a broad face) .. 1 (a strip a pixel or two across): the deck's 4 blocks seen
// from afar or nearly edge-on. Far and low in the sky the sides of the slabs shrink to hairlines between the cap faces;
// lit differently from them they read as bright streaks and outlines, so a thin side takes its cap's colour instead.
float wcSideThin(CloudHit h, vec3 rd, float pixAngle) {
	if (abs(h.n.y) > 0.5) {
		return 0.0;
	}
	float tall = WC_DECK * sqrt(max(1.0 - rd.y * rd.y, 0.0)) / max(h.t * pixAngle, 1e-6); // pixels up the face
	float across = abs(dot(h.n, rd));                                                     // how squarely it is seen
	return max(1.0 - smoothstep(2.0, 8.0, tall), 1.0 - smoothstep(0.04, 0.22, across));
}

// The finished colour of a cloud hit, from the ray's origin `ro` (camera-relative) along `rd`. `pixAngle`: radians
// per screen pixel. Far off and at a grazing angle the decks become a soft band (wcBandOf), not here.
vec3 welkinCloudColor(CloudHit h, vec3 ro, vec3 rd, CloudEnv e, float pixAngle) {
	float y = e.camY + ro.y + rd.y * h.t - h.n.y * 0.01;
	vec3 p = ro + rd * h.t;
	vec3 c = welkinCloudFace(h, rd, y, wcInterior(h, p, y, e), e);
	float thin = wcSideThin(h, rd, pixAngle);
	if (thin > 0.0) {
		// the cap the strip runs along: the underside looking up, the top looking down
		CloudHit cap = h;
		cap.n = vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0);
		c = mix(c, welkinCloudFace(cap, rd, y, 0.0, e), thin);
	}
	return mix(c, wcHorizon(rd, e), wcFog(h));
}

// ------------------------------------------------------------------------------------------- the far band
// Far off and low over the horizon a deck's cells shrink to a pixel or two from front to back: drawn cell by cell they
// alias into thin horizontal streaks. There the deck becomes a band instead: the cloud's colour and the sky between
// its cells, mixed by how much of the deck the weather fills (the W tiles' cover, the cells' own rule on average),
// the same for a pixel that hit a cell and one that looked through a gap, so the far decks melt into one soft band
// over the horizon. One plane and one bilinear weather read per deck, only for rays near the horizon.

// How much of deck k (0 low, 1 main, 2 high) the weather `w` fills, as the cells' own rule would on average.
float wcDeckShare(int k, vec4 w, CloudEnv e) {
	float wide = max(w.a, wcSmooth(0.79, 0.93, w.r));
	if (k == 1) {
		return wide > 0.5 ? 1.0 : clamp(0.04 + 0.92 * max(w.g, e.fair), 0.0, 1.0);
	}
	if (k == 0) {
		float wet = max(w.b, wcSmooth(0.45, 0.82, w.r));
		float stratus = min(0.95, (0.1 + 0.9 * wet) * 0.6);
		return stratus > 0.05 ? clamp(stratus * 0.9, 0.0, 1.0) : 0.0;
	}
	return 0.06 + 0.6 * wcSmooth(0.35, 0.8, w.r);
}

// The weather over camera-relative point q (frame blocks): W0 when `near` and it holds data, else W1, else the
// fallback field; past a tile's reach the sky is clear.
vec4 wcWeatherAt(vec2 q, bool near, CloudEnv e) {
	ivec2 m = e.frameInt + ivec2(floor(q + e.frameFract));
	float v;
	if (near && e.w0) {
		return wcTile(VOID_TILE_W0, 7, m, e.w0Centre, v) * v;
	}
	if (e.w1) {
		return wcTile(VOID_TILE_W1, 10, m + e.w1Off, e.w1Centre, v) * v;
	}
	return wcFallbackWeather(m, e);
}

// Deck k along the ray: where the ray enters its slab (x), its middle (y), the band's weight (z: 0 cells .. 1 band)
// and how much of it is cloud (w). z is 0 for a deck the ray never meets or meets with cells several pixels deep.
vec4 wcBandOf(int k, vec3 ro, vec3 rd, float pixAngle, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	float y0 = e.camY + ro.y;
	if (!wcPresent(deck) || abs(rd.y) < 1e-5 || (y0 >= deck && y0 <= deck + WC_DECK)) {
		return vec4(1e30, 1e30, 0.0, 0.0);
	}
	float tIn = ((rd.y > 0.0 ? deck : deck + WC_DECK) - y0) / rd.y;
	float tMid = (deck + 0.5 * WC_DECK - y0) / rd.y;
	if (tIn <= 0.0) {
		return vec4(1e30, 1e30, 0.0, 0.0);
	}
	vec2 q = ro.xz + rd.xz * tMid;
	// the cells' width there, continuous (the ring's own steps would draw the band's edge as arcs where it doubles)
	float cell = float(WC_CELL) * clamp(2.0 * length(q) / WC_RING0, 1.0, float(1 << WC_MAX_LEVEL));
	// how many pixels a cell spans from front to back
	float rows = cell * abs(rd.y) / max(tMid * pixAngle, 1e-6);
	float weight = 1.0 - smoothstep(1.5, 5.0, rows);
	if (weight <= 0.0) {
		return vec4(tIn, tMid, 0.0, 0.0);
	}
	return vec4(tIn, tMid, weight, wcDeckShare(k, wcWeatherAt(q, false, e), e));
}

// The band's colour for deck k met at tMid, `share` of it cloud, over the sky's horizon colour.
vec3 wcBandColour(vec3 rd, float tMid, float share, CloudEnv e) {
	CloudHit f = wcNoHit();
	f.hit = true;
	f.t = tMid;
	f.n = vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0);
	float y = e.camY + rd.y * tMid;
	vec3 hor = wcHorizon(rd, e);
	vec3 cloud = mix(welkinCloudFace(f, rd, y, 0.5, e), hor, wcFog(f));
	return mix(hor, cloud, share);
}

// Inside a cloud: a dense, smooth fog of the cloud's own light over the path through it, so what is seen out of a
// cell fades into it instead of showing the cell's faces as flat halves. `path`: how far the ray stays in the cloud
// (CloudHit.tOut: continuous over the screen, so the cell's outline never shows; the slab's fog takes over past it).
vec3 welkinCloudVeil(vec3 scene, vec3 rd, float path, CloudEnv e) {
	vec3 c = wcAlbedo(0) * (skyAmbientLight() * 0.82 + skyDirectLight() * 0.42);
	float dusk = wcDuskAmount(e.sun.y);
	if (dusk > 0.0) {
		vec3 p = mix(WC_LILAC, WC_CREAM, wcSunward(rd, e));
		c = mix(c, p * max(wcLuma(c), wcLuma(skyHorizon(false))) / wcLuma(p), dusk);
	}
	return mix(scene, wcShoulder(c), 1.0 - exp(-min(path, 1e4) / 5.0));
}

// How near the eye is to deck k's slab, 1 inside or at its faces .. 0 from 30 blocks off: a deck at eye height is
// seen edge-on as a hard plane across the land, so there its near cells give way to a fog bank (wcSlabFog).
float wcNearDeck(int k, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	if (!wcPresent(deck)) {
		return 0.0;
	}
	float off = max(max(deck - e.camY, e.camY - deck - WC_DECK), 0.0);
	return 1.0 - smoothstep(4.0, 30.0, off);
}

// A deck at eye height as fog: how much of the view the ray's path through deck k's slab veils, between the eye and
// `seen` (the land, the next cloud face, or 1e30 for the sky), by how much of the deck the weather over the camera
// fills: a long level path through a full deck is solid, a steep one through the slab's thin height is clear, so
// above and below the eye the sky and the land show, and toward the horizon the deck closes softly. Scaled by
// wcNearDeck (the cells beyond take over from it as the eye leaves the slab).
float wcSlabFog(int k, vec3 rd, float seen, float share, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	float near = wcNearDeck(k, e);
	if (near <= 0.0) {
		return 0.0;
	}
	float t0, t1;
	if (abs(rd.y) < 1e-5) {
		bool inSlab = e.camY >= deck && e.camY <= deck + WC_DECK;
		t0 = 0.0;
		t1 = inSlab ? 1e30 : 0.0;
	} else {
		float ta = (deck - e.camY) / rd.y, tb = (deck + WC_DECK - e.camY) / rd.y;
		t0 = max(min(ta, tb), 0.0);
		t1 = max(ta, tb);
	}
	float path = clamp(min(t1, seen) - t0, 0.0, 4000.0);
	return near * (1.0 - exp(-path * share / 45.0));
}

// Which deck an altitude lies in (0 low, 1 main, 2 high), or -1 (a tower or an anvil).
int wcDeckAt(float y, CloudEnv e) {
	if (wcPresent(e.layers.x) && y >= e.layers.x - 0.01 && y <= e.layers.x + WC_DECK + 0.01) return 0;
	if (y >= e.layers.y - 0.01 && y <= e.layers.y + WC_DECK + 0.01) return 1;
	if (wcPresent(e.layers.z) && y >= e.layers.z - 0.01 && y <= e.layers.z + WC_DECK + 0.01) return 2;
	return -1;
}

#endif
