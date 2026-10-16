// VOID Welkin: the clouds' colours. The cells stay pixel-crisp voxels: each face is one colour sideways (the way
// vanilla shades a block's faces), lit by the same light as the land (the look of 2026-10-11: smooth gradients, no
// visible dither, directional sunlight first):
//
//   - the light is the sky table's (lib/sky/table.glsl): its ambient by how much of the dome a face sees (tops all of
//     it, undersides least), plus its direct light (the sun by day, the moon by night, as lightDirWorld()) by a
//     wrapped diffuse, since light scatters through a cloud: the shade side is darker, never black, and a little of
//     the light reaches the underside. So a cloud is exactly as bright as a white block lying in the same light, from
//     the noon sun through overcast, golden hour, a moonlit night and dark nights; lightning lights it with the land;
//   - shaded like a block of cloud, top bright, sides mid, base dark: a top darkens toward the cloud's open edges and
//     a side toward its foot, so a cloud reads as a body and not a flat slab; the base is darkest in a big cloud's
//     middle and under a thick cover; looking toward the light, sides and undersides brighten (forward scatter);
//   - a side is never brighter than the cloud's body seen with it (its base from below, its top from above) unless a
//     strong direct light falls on it, and a side only a few pixels tall takes the body's colour: no bright slivers
//     along the outlines;
//   - tops and sides lie in the shadow of the decks above them by day (lib/clouds/shadow.glsl);
//   - round sunrise and sunset the faces take Gloam's dusk palette (docs/STYLE.md, CloudDeck) at the sky's own
//     brightness (a top as bright as the horizon behind it, a base as the dome over it), dimmer while the sun is under
//     the horizon and weaker under an overcast: toward the sun gold and orange tops (farther is warmer), rose
//     undersides; away from it lilac and violet tops, dusk-blue undersides. Both halves blend across the sky, with no
//     line between them, and the palette fades in and out smoothly with the sun's height;
//   - a soft shoulder keeps a noon-lit top just under white, so it still reads against its sides;
//   - showers grey a cell, storms more (per cell, so a storm cell is crisp against a fair one); lightning lifts the
//     storm cells while a bolt is out;
//   - far cells sink smoothly into the sky's horizon colour in their direction (toward the sun its glow) with
//     distance: one curve over every ring and the sheets, so no ring edge shows.
//
// Per cloud pixel: about a dozen sky-table texel fetches, one or two cloud-map texels for the shadow from above,
// and no loops; a side face works out its body's colour as well.

#ifndef WELKIN_CLOUD_SHADE
#define WELKIN_CLOUD_SHADE

#include "/lib/clouds/march.glsl"
#include "/lib/clouds/shadow.glsl"
#include "/lib/sky/dome.glsl"
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
// Round 1: a wide cosine lobe over the whole sky (it was a step across 40 degrees, which split a dusk sky into a rose
// half and a grey-blue half along a seam through the deck).
float wcSunward(vec3 rd, CloudEnv e) {
	float c = 0.5 + 0.5 * dot(normalize(rd.xz + vec2(1e-5)), normalize(e.sun.xz + vec2(1e-5)));
	return c * c * (3.0 - 2.0 * c);
}

// How much a low sun lights the decks from below: from just under the horizon (the decks stand high, so they see it a
// little longer than the land) until it stands about ten degrees up.
float wcUnderLit(float sunY) {
	return smoothstep(-0.07, 0.0, sunY) * (1.0 - smoothstep(0.1, 0.22, sunY));
}

// Gloam's dusk palette for a face (CloudDeck.band). `sunward` 0..1 (wcSunward), `far` the continuous ring (0 near ..
// 5 and more far), `sun` the sun's direction. A side is its top's colour in less light (warmer turned to the sun, a
// shade darker turned away), never a hue of its own, so the thin sides under a cloud's outline read as its thickness
// and not as a bright lilac sliver.
vec3 wcDusk(vec3 n, float far, float sunward, vec3 sun) {
	vec3 sunTop = mix(mix(WC_CREAM, WC_GOLD, 0.4 + 0.6 * smoothstep(1.0, 2.5, far)), WC_ORANGE, smoothstep(3.0, 4.5, far));
	// before sunrise and after sunset the tops lie in the earth's shadow, lit by the pink sky over the sun: rose
	sunTop = mix(sunTop, WC_ROSE, 1.0 - smoothstep(-0.08, 0.02, sun.y));
	vec3 awayTop = mix(WC_LILAC, WC_VIOLET, smoothstep(2.0, 3.5, far));
	vec3 top = mix(awayTop, sunTop, sunward);
	if (n.y > 0.5) {
		return top;
	}
	if (n.y < -0.5) {
		// (round 1: while the sun stands just over the horizon it shines under the decks, so their bases glow peach
		// toward it and rose away from it, as at a real sunset, instead of a dusk blue)
		// (round 3: the under-light's colour fades away from the sun: peach over the sun's side, rose overhead, the
		// dusk lavender-blue over the far side, so a dawn deck is graded across the sky, not one salmon slab)
		return mix(mix(WC_DUSK, WC_ROSE, sunward), mix(WC_ROSE, WC_ORANGE, 0.6 * sunward),
			wcUnderLit(sun.y) * mix(0.25, 1.0, sunward));
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

// Which deck an altitude lies in (0 low, 1 main, 2 high), or -1 (a tower or an anvil).
int wcDeckAt(float y, CloudEnv e) {
	if (wcPresent(e.layers.x) && y >= e.layers.x - 0.01 && y <= e.layers.x + WC_DECK + 0.01) return 0;
	if (y >= e.layers.y - 0.01 && y <= e.layers.y + WC_DECK + 0.01) return 1;
	if (wcPresent(e.layers.z) && y >= e.layers.z - 0.01 && y <= e.layers.z + WC_DECK + 0.01) return 2;
	return -1;
}

// Whether the point `p` (camera-relative) of a cap face lies inside the cloud's footprint (1) or on the rim along an
// open edge of its cell (0: one whose neighbour lacks this deck, tower or anvil). A cell with no open edge is all
// interior. `tx`: the hit cell's texel of the finished map (its edge bits); a side face is
// on an edge: 0. A base takes its deck's edges only (a tower's edges say where its neighbours stand lower, which on
// the flat base under a storm outlined every tower cell as a tile on the ceiling): under a tower it is all interior.
float wcInterior(CloudHit h, ivec4 tx, vec3 p, float y, CloudEnv e) {
	if (abs(h.n.y) < 0.5) {
		return 0.0;
	}
	int s = WC_CELL << h.level;
	float size = float(s);
	vec2 local = clamp(p.xz - wcCellCorner(h.cell, s, e), vec2(0.0), vec2(size));
	CloudCol c = wcMapDecode(tx.x, tx.y, e);
	float inside = y - h.n.y * 0.01;
	int k = wcDeckAt(inside, e);
	bool deckFace = k >= 0 && (k == 0 ? wcIn(c.low, inside) : (k == 1 ? wcIn(c.main, inside) : wcIn(c.high, inside)));
	if (h.n.y < -0.5 && !deckFace) {
		return 1.0;
	}
	int edges = !deckFace && (wcIn(c.tower, inside) || wcIn(c.anvil, inside)) ? tx.w : tx.z >> (4 * max(k, 0));
	// a crisp rim along each open edge, a block and a half wide near (doubling with the rings, so it stays a line a
	// few pixels thick), the voxel's bevel: 0 on the rim, 1 inside it (a gradient across the whole cell read as a
	// blurred, see-through edge)
	float rim = 1.5 * float(1 << h.level);
	vec4 dist = vec4(local.x, size - local.x, local.y, size - local.y);
	vec4 f = smoothstep(vec4(rim * 0.85), vec4(rim), dist);
	f = mix(vec4(1.0), f, vec4(ivec4(edges, edges >> 1, edges >> 2, edges >> 3) & 1));
	return f.x * f.y * f.z * f.w;
}

// ---- Air and tone (D, 2026-10-13): a cloud base's light runs from its open edges into its middle.
// How near the point `p` (camera-relative) of a base lies to an open edge of its cloud, 1 at the edge .. 0 from a
// whole cell in (12 blocks at the finest ring, wider with each ring), on the block grid (the cloud's own
// voxel steps, never a blur): light reaching under a cloud through its thin outer parts lifts the base near its
// outline, so a base reads as a body graded into a darker middle and its staircase outline sits softly against the
// sky instead of a flat grey slab cut out of it. 0 for a cell with no open edge and for anything but a deck's base.
float wcUnderEdge(CloudHit h, ivec4 tx, vec3 p, float y, CloudEnv e) {
	if (h.n.y > -0.5) {
		return 0.0;
	}
	float inside = y + 0.01;
	int k = wcDeckAt(inside, e);
	if (k < 0) {
		return 0.0; // the flat base under a tower or an anvil: all middle
	}
	int edges = (tx.z >> (4 * k)) & 15;
	if (edges == 0) {
		return 0.0;
	}
	int s = WC_CELL << h.level;
	float size = float(s);
	float block = float(1 << h.level);
	vec2 local = clamp(p.xz - wcCellCorner(h.cell, s, e), vec2(0.0), vec2(size));
	local = (floor(local / block) + 0.5) * block; // the block grid of this ring
	vec4 dist = vec4(local.x, size - local.x, local.y, size - local.y);
	vec4 open = vec4(ivec4(edges, edges >> 1, edges >> 2, edges >> 3) & 1);
	float d = min(min(mix(1e4, dist.x, open.x), mix(1e4, dist.y, open.y)), min(mix(1e4, dist.z, open.z), mix(1e4, dist.w, open.w)));
	float f = 1.0 - clamp(d / size, 0.0, 1.0);
	return f * (2.0 - f) * 0.5 + 0.5 * f * f;
}

// How thick the hit's cloud is there, 0..1 in four steps (the cell's own, from the finished map; a tower or an anvil
// is thick).
float wcDensity(CloudHit h, ivec4 tx, float y, CloudEnv e) {
	int k = wcDeckAt(y - h.n.y * 0.01, e);
	vec3 d = wcMapDensity(tx.x);
	return k == 0 ? d.x : (k == 1 ? d.y : (k == 2 ? d.z : 1.0));
}

// How much of the light on a cloud is the sun's (or moon's) direct beam rather than the sky's fill, 0..1: about a half
// under a clear sky, near nothing under an overcast.
float wcDirectShare() {
	float d = wcLuma(skyDirectLight()), a = wcLuma(skyAmbientLight());
	return d / max(d + a, 1e-4);
}

// The sky's own colour in direction rd: the horizon low down, the zenith overhead (a view down takes its mirror).
vec3 wcDome(vec3 rd, CloudEnv e) {
	return mix(wcHorizon(rd, e), skyZenith(), smoothstep(0.02, 0.7, abs(rd.y)));
}

// How thick the cover is, 0..1 (the sky table's overcast, rain): a thick deck's base is darker.
float wcThick() {
	return clamp(max(skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a, rainStrength), 0.0, 1.0);
}

// A face with normal n, `up` its side height (0.5 for tops and undersides), `interior` how deep in the cloud's
// footprint a cap's point lies (wcInterior), `dens` how thick the cloud's cell is (wcDensity), in light from direction
// l, seen along rd, `sun` the share of the direct light reaching it past the decks above (the clouds' own shadow),
// `thick` wcThick(). Shaded like a block of cloud: top bright, sides mid, base dark; a thick cell's top a little
// brighter and its base darker than a thin one's, cell by cell, so a deck reads as voxels with a body.
//   - tops see the whole dome and take the light by how high it stands; a top darkens toward the open edges of the
//     cloud (the light wraps round a rounded top, and its edge sees less of the dome), so each cloud reads as a body
//     and a broad deck is not one flat sheet;
//   - sides take the light by how squarely they face it, wrapped (light scatters through a cloud), and darken toward
//     their foot;
//   - undersides see little of the dome and almost none of the light, one flat shade a cell: a thick cell's base is
//     darker, and a thick cover's darker still.
vec3 wcLit(vec3 n, float up, float interior, float dens, int tone, vec3 rd, vec3 l, float sun, float thick) {
	bool top = n.y > 0.5, under = n.y < -0.5;
	float ndl = dot(n, l);
	float skyView, diffuse, ao;
	// (round 1: a cloud scatters the sun through its whole body, so its sides and base are much brighter than a block's
	// shade: a light blue-grey base and soft white sides under a white top, never a grey slab darker than the land)
	if (top) {
		skyView = 1.0;
		diffuse = clamp((ndl + 0.25) / 1.25, 0.0, 1.0);
		ao = 1.0; // (the rim and the thickness shade the top after the shoulder: wcFaceColour)
	} else if (under) {
		skyView = 0.9;
		// (one flat shade a cell: a lighter rim along a base's outline read as a bright line round every cloud)
		diffuse = 0.3;
		ao = 0.95 * mix(1.03, 0.86, dens) * (1.0 - 0.2 * thick);
	} else {
		skyView = 0.95;
		diffuse = clamp((ndl + 0.7) / 1.7, 0.0, 1.0);
		ao = mix(0.84, 1.0, up);
	}
	// forward scatter: looking toward the light, the sides and undersides glow at the edges
	float forward = pow(clamp(dot(rd, l), 0.0, 1.0), 8.0) * (top ? 0.10 : (under ? 0.15 : 0.30));
#if WD_SKY
	// depth prototype SKY: sunlit tops and edges over shaded bellies. A top takes the sun squarely (a brighter, whiter
	// cap), a side by how it faces the sun (its sunlit edge bright, its far side in the cloud's own shade), a base only
	// the sky's light through the body (deeper, cooler), and toward the sun the thin edges glow (silver lining)
	if (top) {
		diffuse = clamp((ndl + 0.12) / 1.12, 0.0, 1.0) * 1.22;
	} else if (under) {
		diffuse = 0.16;
		ao *= 0.90;
		skyView = 0.86;
	} else {
		diffuse = clamp((ndl + 0.25) / 1.25, 0.0, 1.0) * 1.18;
		ao = mix(0.70, 1.0, up);
	}
	forward = pow(clamp(dot(rd, l), 0.0, 1.0), 5.0) * (top ? 0.22 : (under ? 0.30 : 0.65));
#endif
	vec3 light = skyAmbientLight() * skyView + skyDirectLight() * (diffuse + forward) * sun;
	if (under) {
		// (air and tone (D): a base is lit through the cloud by the light's side of the sky: brighter seen toward the
		// sun or the moon, a cooler, deeper shade away from it, one broad gradient across the sky; and the sunlit
		// ground below throws a little warm light up under it)
		float toward = 0.5 + 0.5 * dot(rd, l);
		light *= mix(0.86, 1.14, toward * toward);
		light += skyDirectLight() * 0.06 * max(l.y, 0.0) * vec3(1.0, 0.95, 0.86);
	}
	return wcAlbedo(tone) * light * ao;
}

// The colour of a cloud face with normal n at distance t along rd: `up` its side height, `interior` as wcInterior,
// `dens` as wcDensity, `sun` its share of the direct light (wcLit).
vec3 wcFaceColour(vec3 n, float up, float interior, float dens, int tone, vec3 rd, float t, float sun, CloudEnv e) {
	float thick = wcThick();
	vec3 c = wcLit(n, up, interior, dens, tone, rd, lightDirWorld(), sun, thick);
	// round sunrise and sunset Gloam's palette, weaker under an overcast. Its brightness is the sky's: a top about
	// as bright as the horizon behind it (a little brighter toward the sun), a base as the dome over it, a side
	// between, and all dimmer while the sun is under the horizon (the decks lie in the earth's shadow then and only
	// the sky lights them): before sunrise and after sunset the decks are dusky and rosy, never a pale sheet brighter
	// than the sky
	float dusk = wcDuskAmount(e.sun.y) * (1.0 - 0.6 * skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a);
	if (dusk > 0.0) {
		float sw = wcSunward(rd, e);
		vec3 p = wcDusk(n, wcFar(t), sw, e.sun) * (tone == 2 ? 0.7 : (tone == 1 ? 0.85 : 1.0));
		float face, ref;
		if (n.y > 0.5) {
			face = 1.0;
			ref = wcLuma(wcHorizon(rd, e)) * mix(0.75, 1.05, sw);
		} else if (n.y < -0.5) {
			face = 0.91 * mix(1.02, 0.84, dens) * (1.0 - 0.2 * thick);
			ref = wcLuma(wcDome(rd, e)) * mix(0.80, 1.05, sw);
		} else {
			face = mix(WC_SIDE_FOOT, WC_SIDE_BROW, up) * 0.92;
			ref = wcLuma(wcHorizon(rd, e)) * mix(0.62, 0.95, sw);
		}
		// lit from below by the low sun: a base or a side as bright as the horizon toward the sun allows (less away
		// from it), so the whole deck glows and the sky has no dark half
		if (n.y < 0.5) {
			float under = wcUnderLit(e.sun.y) * (1.0 - skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a);
			// (round 3: brightest over the sun's side, falling off faster away from it: a lit rim toward the sun, a
			// darker deck behind)
			ref = mix(ref, max(ref, wcLuma(skyHorizon(true)) * mix(0.32, 0.92, sw * sw)), under);
		}
		float earth = mix(0.45, 1.0, smoothstep(-0.10, 0.03, e.sun.y));
		vec3 d = p * (face * ref * earth / max(wcLuma(p), 1e-3));
		d *= max(1.0, wcLuma(c) / max(wcLuma(d), 1e-3)); // never darker than the plain light
		c = mix(c, d, dusk);
	}
	// (air and tone (D): at night a cloud is lit by the moon and the night sky round it, never a black cut-out in the
	// stars: its body takes the dome's own colour behind it at about its brightness, a base a little under it, a top a
	// little over, brighter toward the moon; so a night deck reads as a soft grey-blue body that only hides the stars)
	float night = skyNightStep() * (1.0 - wcDuskAmount(e.sun.y));
	if (night > 0.0) {
		SkyFrame sf = skyFrame();
		vec3 behind = skyDomeOnly(rd, sf);
		float moonward = 0.5 + 0.5 * dot(rd, sf.moonW);
		float k = (n.y > 0.5 ? 1.12 : (n.y < -0.5 ? 0.86 : 0.97)) * mix(0.92, 1.14, moonward * moonward * e.moon);
		vec3 tint = mix(behind, vec3(wcLuma(behind)), 0.35) * k;
		tint *= tone == 2 ? 0.8 : (tone == 1 ? 0.9 : 1.0);
		c = mix(c, max(c, tint + 0.25 * c), night);
	}
	// a closed overcast's base is the sky: the overcast dome's own colour in that direction (the horizon's low down, the
	// zenith's overhead, darker in a thick middle), not a dark slab over a brighter haze, nor a pale sheet brighter than
	// the grey sky round it
	if (n.y < -0.5) {
		float closed = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
		vec3 dome = wcDome(rd, e) * 0.95 * mix(1.01, 0.95, dens);
		c = mix(c, dome, closed);
	}
	if (e.flash && tone == 2) {
		c = mix(c, vec3(0.86, 0.86, 1.0), 0.5);
	}
	// a top's crisp rim and its thickness shade it after the shoulder, which would squeeze a noon-lit top's shades
	// into one flat white
	return wcShoulder(c) * (n.y > 0.5 ? mix(0.84, 1.0, interior) * mix(0.86, 1.02, dens) : 1.0);
}

// (air and tone (D)) How much a base takes the plain day's look below: not in the dusk palette, the night's or a closed
// overcast's (which already match the sky).
float wcBaseDay(CloudEnv e) {
	return (1.0 - max(wcDuskAmount(e.sun.y), skyNightStep())) * (1.0 - skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a);
}

// A base by day: a neutral grey body (less of the sky's blue), a little deeper.
vec3 wcBaseNeutral(vec3 c, float day) {
#if WD_SKY
	// depth prototype SKY: a base keeps the sky's blue it is lit by (a cool, luminous shade, not a grey slab)
	return mix(c, mix(vec3(wcLuma(c)), c, 0.9) * 0.9, day);
#endif
	return mix(c, mix(vec3(wcLuma(c)), c, 0.55) * 0.86, day);
}

// The colour of a cloud face at altitude `y`, `interior` as wcInterior (the hit's own face, no shadow from above).
vec3 welkinCloudFace(CloudHit h, vec3 rd, float y, float interior, CloudEnv e) {
	float up = abs(h.n.y) > 0.5 ? 0.5 : wcSideHeight(y, e);
	vec3 c = wcFaceColour(h.n, up, interior, 0.6, h.tone, rd, h.t, 1.0, e);
	return h.n.y < -0.5 ? wcBaseNeutral(c, wcBaseDay(e)) : c;
}

// What a far cloud sinks into: the sky behind it (a deck over the eye fades into the sky at its height, not into
// the paler horizon, which made far cells brighter than the sky round them, as if seen through), the horizon for a
// deck seen from above.
vec3 wcFogTarget(vec3 rd, CloudEnv e) {
	vec3 hor = wcHorizon(rd, e);
	if (rd.y <= 0.0) {
		return wdSkyLum(hor, normalize(vec3(rd.x, 0.0, rd.z) + vec3(1e-5, 0.0, 0.0)), e.sun);
	}
	// the dome's plain band at this height (two texels, the dome's own band mapping), with the sun's glow of the
	// horizon fading out upward
	float x = clamp(sqrt(rd.y) * 4.6 - 0.5, 0.0, 4.0) / 4.0;
	vec3 band = skyRowAt(1.0 + 4.0 * x, SKY_ROW_DOME) + (hor - skyHorizon(false)) * (1.0 - x);
	// (air and tone (D): by day a far cloud pales into the bright air between it and the eye, half the horizon's colour,
	// rather than taking the deeper blue of the sky behind it: sunk into that blue a far deck read as a see-through grey
	// slab; paled, it reads as a far white cloud in the haze)
	return wdSkyLum(mix(band, hor, 0.5 * wcBaseDay(e)), rd, e.sun); // depth prototype SKY: the same luminous air
}

// How far a hit sinks into the horizon colour: a little from a few hundred blocks out, then smoothly more with each
// doubling of the distance, one curve over every ring and the sheets, so far cells melt into the horizon as the far
// land does (composite2 lays the land's haze over them besides); more in rain.
float wcFog(CloudHit h) {
	// (air and tone (D): a little less, 0.12 a ring and at most 0.62, was 0.16 and 0.86: the atmosphere pass hazes a
	// cloud as the land at its distance besides, and with both a far deck sank to the sky's own colour and read as a
	// see-through grey slab)
	float f = clamp((wcFar(h.t) - 0.8) * 0.12, 0.0, 0.62);
	f = min(f + 0.08 * smoothstep(0.6 * WC_REACH, WC_REACH, h.t), 0.9);
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
	return max(1.0 - smoothstep(2.5, 9.0, tall), 1.0 - smoothstep(0.04, 0.22, across));
}

// The share of the direct light that reaches a cloud's top or side at camera-relative point p past the decks above
// it: the clouds' shadows on each other (lib/clouds/shadow.glsl), by day only.
float wcSelfShadow(vec3 n, vec3 p, CloudEnv e) {
#if CLOUD_SHADOWS == 0
	return 1.0;
#else
	if (n.y < -0.5 || e.sun.y < 0.08) {
		return 1.0;
	}
	return welkinCloudShadow(p, e);
#endif
}

// The finished colour of a cloud hit, from the ray's origin `ro` (camera-relative) along `rd`. `pixAngle`: radians
// per screen pixel. Far off and at a grazing angle the decks become a soft band (wcBandOf), not here.
vec3 welkinCloudColor(CloudHit h, vec3 ro, vec3 rd, CloudEnv e, float pixAngle) {
	float y = e.camY + ro.y + rd.y * h.t - h.n.y * 0.01;
	vec3 p = ro + rd * h.t;
	bool side = abs(h.n.y) < 0.5;
	float up = side ? wcSideHeight(y, e) : 0.5;
	float sun = wcSelfShadow(h.n, p - h.n * 0.05, e);
	ivec4 tx = wcMapCell(h.cell, h.level, e);
	float dens = wcDensity(h, tx, y, e);
	vec3 c = wcFaceColour(h.n, up, wcInterior(h, tx, p, y, e), dens, h.tone, rd, h.t, sun, e);
	// (air and tone (D): a base is a neutral grey body, not a sheet of the sky's own blue, a little deeper in its
	// middle; toward its open edges it whitens block by block, as the sun shines through a cloud's thin outer parts
	// and its sunlit sides show, so a cloud seen from below reads as a solid body with a lit outline)
	if (h.n.y < -0.5) {
		float day = wcBaseDay(e);
		c = wcBaseNeutral(c, day);
		float ue = wcUnderEdge(h, tx, p, y, e);
		if (ue > 0.0) {
			vec3 topLit = wcLit(vec3(0.0, 1.0, 0.0), 0.5, 1.0, dens, h.tone, rd, lightDirWorld(), 1.0, wcThick());
			vec3 rimC = mix(wcShoulder(topLit) * 0.96, c * 1.28, 1.0 - day);
			c = mix(c, max(c, rimC), (WD_SKY == 1 ? 0.95 : 0.7) * ue);
		}
	}
	if (side) {
		// the cloud's body seen with this side: its base from below, its top from above
		vec3 capN = vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0);
		vec3 cap = wcFaceColour(capN, 0.5, 0.0, dens, h.tone, rd, h.t, sun, e);
		// a side is never brighter than that body in a diffuse light (an overcast, the dusk, the night): only in a
		// strong direct sun may a sunlit side seen from below stand out over its darker base, and then by a quarter; and
		// it leans to the body's hue, so a dusk side is no olive strip along a rose base
		float allow = rd.y > 0.0 ? mix(1.0, WD_SKY == 1 ? 1.7 : 1.25, smoothstep(0.25, 0.5, wcDirectShare()) * smoothstep(0.05, 0.2, e.sun.y)) : 1.0;
		c *= min(1.0, allow * wcLuma(cap) / max(wcLuma(c), 1e-4));
		c = mix(c, cap * (wcLuma(c) / max(wcLuma(cap), 1e-4)), 0.6);
		c = mix(c, cap, wcSideThin(h, rd, pixAngle));
	}
	return mix(c, wcFogTarget(rd, e), wcFog(h));
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
	// (a narrow hand-over: past it the cells are drawn whole and crisp; across a wide one every cell was blended with
	// the sky behind it and read as a see-through slab)
	float weight = 1.0 - smoothstep(1.2, 3.0, rows);
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
	vec3 cloud = mix(welkinCloudFace(f, rd, y, 0.5, e), wcFogTarget(rd, e), wcFog(f));
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
	return mix(scene, wcShoulder(c), 1.0 - exp(-min(path, 1e4) / 7.0));
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

// The fog bank's density across a deck's height (its slab a..b), summed from below up to altitude y: 1 inside the
// slab, easing off linearly over WC_SLAB_MARGIN blocks above and below it, so the bank has no hard top or bottom.
#define WC_SLAB_MARGIN 20.0
float wcSlabCum(float y, float a, float b) {
	float m = WC_SLAB_MARGIN;
	float lo = clamp(y - (a - m), 0.0, m);
	float hi = clamp(y - b, 0.0, m);
	return lo * lo / (2.0 * m) + clamp(y - a, 0.0, b - a) + hi - hi * hi / (2.0 * m);
}

// A deck at eye height as fog: how much of the view the ray's path through deck k's bank veils, between the eye and
// `seen` (the land, the next cloud face, or 1e30 for the sky), by how much of the deck the weather over the camera
// fills: a long level path through a full deck is solid, a steep one through the bank's height is clear, so above
// and below the eye the sky and the land show, and toward the horizon the deck closes softly. The bank's soft top and
// bottom (wcSlabCum) keep the horizon from splitting the view into two flat fields when the eye stands just over or
// under the slab. Scaled by wcNearDeck (the cells beyond take over from it as the eye leaves the slab).
float wcSlabFog(int k, vec3 rd, float seen, float share, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	float near = wcNearDeck(k, e);
	if (near <= 0.0) {
		return 0.0;
	}
	float a = deck, b = deck + WC_DECK, m = WC_SLAB_MARGIN;
	float s = min(seen, 4000.0);
	float path;
	if (abs(rd.y) < 1e-4) {
		float y = e.camY;
		path = s * clamp((y - (a - m)) / m, 0.0, 1.0) * clamp((b + m - y) / m, 0.0, 1.0);
	} else {
		path = abs(wcSlabCum(e.camY + rd.y * s, a, b) - wcSlabCum(e.camY, a, b)) / abs(rd.y);
	}
	return near * (1.0 - exp(-path * share / 45.0));
}


#endif
