// VOID Welkin: the clouds' light (rebuilt in the clouds rework, 2026-10-16). The cells stay pixel-crisp voxels with hard
// outlines, opaque, no dither; inside that outline each cloud is lit as a volume, in soft gradients that never step at
// a cell's edge:
//
//   - the body (CloudBody): where on its own cloud a hit lies, from its base (0) to its top (1), and how deep inside
//     the cloud it is, from the raw cloud map's smooth margin field (lib/clouds/map.glsl wcField, the GPU's bilinear
//     filter over the cells: 0 at the outline .. 1 deep inside);
//   - the light through it (wcLightPath): the length of cloud a ray toward the sun (or the moon) still crosses, from
//     the hit's own column up to its top and four field samples along the light, each of them a soft height field of
//     the deck's tiers. A top in the open sun crosses none; a side turned from a low sun crosses the cloud's whole
//     body; a lower tier under a taller one toward a low sun lies in its shadow; a base crosses the cloud's thickness;
//   - one bounce and its multiple scattering (wcBodyLight): Beer's law on that path, plus two softer octaves (the light
//     scattered many times keeps a cloud's shaded parts bright and coloured, never black), a gentle back-scatter for
//     the lit side and a strong forward lobe through the thin parts, so the ragged rims and the thin cells glow when the
//     eye looks toward the light (the silver lining);
//   - the sky's light, by how much of the dome a point sees (a top all of it, a base the lower half), less deep inside
//     a thick cloud's lower part, and the land's bounce under it, warm when a low sun lights the land;
//   - round sunrise and sunset the sun the clouds see is stronger and warmer than the land's (they stand high, in less
//     air): tops and edges blaze gold, then orange and rose as it sinks, and once it is under the horizon it still
//     lights the decks from below (rose and peach bellies toward it, dusk lilac away);
//   - at night the moon lights them (silver edges toward it) and they never fall darker than the night sky behind
//     them;
//   - rain and storms: greyer, denser cells (more light lost along the same path), so a rain deck's belly is dark and
//     mottled with its thick and thin parts, heavier under the storm cells;
//   - far off a cloud melts into the horizon's own light in its direction (toward the sun its glow).
// The tops are kept just under white by a soft shoulder.
//
// Per cloud pixel: one finished-map texel, one field sample for the body and four along the light (clouds pass only;
// deferred's puddle reflections, which do not bind the raw map, light the body from its cell alone), a dozen sky-table
// texels, the decks' shadow from above. No loops over the screen.

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

// The share of the scattered sun or moon light (wcBodyLight) a cloud gives back, so a noon-lit top is about as bright
// as a white block in the same light.
#define WC_DIRECT 0.52
// How much light a block of cloud takes out, per tone (fair, showers, storm).
#define WC_SIGMA vec3(0.07, 0.12, 0.20)
// How much brighter the moon lights a cloud than the land (the clouds catch it across their whole body).
#define WC_MOON_GAIN 1.2

float wcLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// A soft shoulder over 0.8, so a noon-lit top stays just under white and still reads against its sides.
vec3 wcShoulder(vec3 c) {
	return mix(c, 0.8 + 0.2 * (1.0 - exp(-(c - 0.8) * 5.0)), step(0.8, c));
}

// A cell's own colour by its tone: fair white, showers grey, storm slate.
vec3 wcAlbedo(int tone) {
	return tone == 0 ? vec3(0.97, 0.97, 0.98) : (tone == 1 ? vec3(0.80, 0.81, 0.85) : vec3(0.56, 0.58, 0.65));
}

// How much the dusk palette colours the clouds: 0 by day and by night, 1 while the sun is near the horizon, smooth.
float wcDuskAmount(float sunY) {
	return smoothstep(-0.18, -0.07, sunY) * (1.0 - smoothstep(0.10, 0.28, sunY));
}

// The continuous ring of a distance t: 0 inside WC_RING0 / 2, 1 at WC_RING0, one more at each doubling.
float wcFar(float t) {
	return max(log2(max(t, 1.0) / WC_RING0) + 1.0, 0.0);
}

// 0 for a view away from the sun's side of the sky .. 1 toward it (horizontally): a wide cosine lobe over the whole sky.
float wcSunward(vec3 rd, CloudEnv e) {
	float c = 0.5 + 0.5 * dot(normalize(rd.xz + vec2(1e-5)), normalize(e.sun.xz + vec2(1e-5)));
	return c * c * (3.0 - 2.0 * c);
}

// How much the sun lights the decks from below: once it is just under the horizon (the decks stand high, so they still
// see it, its rays coming up under them) until it is about six degrees down.
float wcUnderLit(float sunY) {
	return smoothstep(-0.10, -0.03, sunY) * (1.0 - smoothstep(-0.01, 0.04, sunY));
}

// The sky's horizon colour in the direction rd: the plain horizon, warming into the horizon toward the sun inside the
// sun's glow (as wide as the sky table says the glow reaches).
vec3 wcHorizon(vec3 rd, CloudEnv e) {
	float spread = max(skyTable(SKY_X_STATE_GLOW, SKY_ROW_LIGHT).a * 2.0, 0.05);
	float toward = 1.0 - smoothstep(0.0, spread, 1.0 - dot(rd, e.sun));
	return mix(skyHorizon(false), skyHorizon(true), toward);
}

// Which deck's slab (its base to its tallest tier) an altitude lies in (0 low, 1 main, 2 high), or -1.
int wcDeckAt(float y, CloudEnv e) {
	if (wcPresent(e.layers.x) && y >= e.layers.x - 0.01 && y <= e.layers.x + wcDeckSpan(0) + 0.01) return 0;
	if (y >= e.layers.y - 0.01 && y <= e.layers.y + wcDeckSpan(1) + 0.01) return 1;
	if (wcPresent(e.layers.z) && y >= e.layers.z - 0.01 && y <= e.layers.z + wcDeckSpan(2) + 0.01) return 2;
	return -1;
}

// The sky's own colour in direction rd: the horizon low down, the zenith overhead (a view down takes its mirror).
vec3 wcDome(vec3 rd, CloudEnv e) {
	return mix(wcHorizon(rd, e), skyZenith(), smoothstep(0.02, 0.7, abs(rd.y)));
}

// How closed the cover is, 0..1 (the sky table's overcast).
float wcClosed() {
	return skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
}

// How plainly daylit the sky is: not in the dusk palette, the night's or a closed overcast's.
float wcDay(CloudEnv e) {
	return (1.0 - max(wcDuskAmount(e.sun.y), skyNightStep())) * (1.0 - wcClosed());
}

// ------------------------------------------------------------------------------------------------- the body

struct CloudBody {
	vec3 p;        // the hit, camera-relative
	float y;       // its altitude, a hair inside the cloud
	vec2 iv;       // its cloud's interval in this column (base, top)
	float hIn;     // where on that height it lies: 0 the base .. 1 the top
	float depth;   // how deep inside its cloud: 0 at the outline .. 1 deep inside (smooth)
	float top;     // the cloud's top over the point, smooth (from the field; the cell's own top where none)
	int k;         // its deck (0 low, 1 main, 2 high), -1 a tower or an anvil
	int lv;        // the ring
	bool field;    // the field may be read along the light
	float bill;    // (review round 2) the billows over the body: -0.5 a hollow .. 0.5 a swell, smooth (wcBillow)
	float relief;  // (review round 2) how the billows turn to the sun: 1 a swell's sunward flank .. -1 its far side
};

// (review round 2) Smooth value noise with its lattice every `w` blocks at camera-relative point q, on the clouds'
// drifting frame (so it rides with the cells), continuous within a block (the cells' own wcValue steps by whole blocks).
float wcValueAt(vec2 q, int w, uint salt, CloudEnv e) {
	vec2 r = e.frameFract + q;
	vec2 fl = floor(r);
	ivec2 m = e.frameInt + ivec2(fl);
	ivec2 l = wcFloorDiv(m, w);
	vec2 f = (vec2(m - l * w) + (r - fl)) / float(w);
	f = f * f * (3.0 - 2.0 * f);
	float a = wcU01(wcHash(l, salt));
	float b = wcU01(wcHash(l + ivec2(1, 0), salt));
	float c = wcU01(wcHash(l + ivec2(0, 1), salt));
	float d = wcU01(wcHash(l + ivec2(1, 1), salt));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// (review round 2) The billows of a deck's body at camera-relative point q: two octaves of smooth value noise (about
// 96 and 40 blocks, the scale of a cumulus's own lobes), -0.5..0.5. They shade a cloud inside its crisp outline (a
// belly's thick and thin lobes, a top's domes and hollows), so a big cloud's heart is never one flat grey sheet; the
// finer octave fades out far off, and the whole far off (a far cloud's lobes would be a smudged texture).
float wcBillow(vec2 q, CloudEnv e) {
	float d = length(q);
	float v = wcValueAt(q, 96, e.seed + 301u, e);
	float fine = mix(0.5, wcValueAt(q + vec2(17.0, -9.0), 40, e.seed + 307u, e), 1.0 - smoothstep(1.5 * WC_RING0, 4.0 * WC_RING0, d));
	return clamp((0.65 * v + 0.35 * fine - 0.5) * 1.6, -0.5, 0.5) * (1.0 - 0.6 * smoothstep(2.0 * WC_RING0, 8.0 * WC_RING0, d));
}

// The body of a hit. `useField`: read the smooth field (the clouds pass), else the cell's own thickness.
CloudBody wcBodyOf(CloudHit h, vec3 ro, vec3 rd, CloudEnv e, bool useField) {
	CloudBody b;
	b.p = ro + rd * h.t;
	b.y = e.camY + b.p.y - h.n.y * 0.01;
	b.lv = h.level;
	ivec4 tx = wcMapCell(h.cell, h.level, e);
	CloudCol c = wcMapDecode(tx.x, tx.y, e);
	b.k = wcDeckAt(b.y, e);
	vec2 iv = b.k == 0 ? c.low : (b.k == 1 ? c.main : (b.k == 2 ? c.high : WC_EMPTY));
	if (b.k < 0 || !wcIn(iv, b.y)) {
		b.k = -1;
		iv = wcIn(c.anvil, b.y) ? c.anvil : c.tower;
		iv = wcIn(iv, b.y) ? iv : vec2(b.y - 2.0, b.y + 2.0);
	}
	b.iv = iv;
	b.hIn = clamp((b.y - iv.x) / max(iv.y - iv.x, 1.0), 0.0, 1.0);
	vec3 dn = wcMapDensity(tx.x);
	b.depth = b.k == 0 ? dn.x : (b.k == 1 ? dn.y : (b.k == 2 ? dn.z : 1.0));
	b.field = false;
	b.top = iv.y;
	b.bill = 0.0;
	b.relief = 0.0;
#ifdef WC_FIELD
	if (useField && (b.k == 0 || b.k == 1)) {
		vec2 f = wcFieldSmooth(b.p.xz - h.n.xz * 0.05, e);
		b.depth = clamp((b.k == 0 ? f.x : f.y) * 2.0 - 1.0, 0.0, 1.0);
		b.field = true;
		// a base's light runs through the cloud's smooth thickness over it, not the cell's own steps of height (which
		// tiled a belly in squares)
		if (h.n.y < -0.5) {
			b.top = iv.x + WC_DECK + (b.k == 1 ? wcMainHeightAt(wcMainRise(b.depth)) : b.depth * float(WC_LOW_TIERS) * WC_TIER);
		}
		// (review round 2) the billows: a belly's light runs through thicker and thinner lobes, a top has its domes
		// (clouds2: a belly is flat, as a real cumulus's base is: no billows under it. Its lobes, stretched long in
		// perspective, read as grey smudges sheared across every deck seen from below)
		b.bill = h.n.y < -0.5 ? 0.0 : wcBillow(b.p.xz, e);
		// (a top's billows as relief toward a sun from the side: a swell's flank turned to the sun lit, its far flank in
		// its own shade, so the lobes read as rounded volumes, not as blotches)
		if (h.n.y > 0.5 && length(e.sun.xz) > 0.05) {
			vec2 sd = normalize(e.sun.xz);
			b.relief = clamp((b.bill - wcBillow(b.p.xz + sd * 10.0, e)) * 4.0, -1.0, 1.0);
		}
	}
#endif
	if (b.k == 2 || b.k < 0) {
		// the high sheet and the towers have no field: a slow smooth mottle over the frame instead, so a base is
		// never one flat colour
		vec2 m = vec2(e.frameInt) + e.frameFract + b.p.xz;
		ivec2 mi = ivec2(floor(m));
		float v = 0.65 * wcValue(mi, 96, e.seed + 211u) + 0.35 * wcValue(mi, 32, e.seed + 223u);
		b.depth = clamp(b.k < 0 ? 0.55 + 0.6 * (v - 0.5) : 0.1 + 1.3 * (v - 0.3), 0.0, 1.0);
		// the high sheet is thin where its mottle is (the light through it, and its belly's shade, follow)
		b.top = b.k == 2 && h.n.y < -0.5 ? b.iv.x + WC_DECK * (0.3 + 1.4 * b.depth) : b.top;
	}
	return b;
}

// Henyey-Greenstein, scaled so its average over the sphere is 1.
float wcHG(float c, float g) {
	float g2 = g * g;
	return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * c, 1e-4), 1.5);
}

// How many blocks of cloud a ray from the body toward the light l still crosses: its own column up to the top, then
// four samples of the deck's field along the light, each read as a soft height field (a cell's top from how deep
// inside its cloud it lies), so a taller tier toward a low sun shades the lower ones and a cloud's own body shades its
// far side.
float wcLightPath(CloudBody b, vec3 l, CloudEnv e) {
	float own = max(b.top - b.y, 0.0) / max(l.y, 0.035);
	// (a belly's light also crosses the cloud's breadth on its way down to it: deeper inside a broad cloud, more of it;
	// so a wide, thin deck's belly darkens toward its heart instead of hanging overhead as one white sheet)
	// (review round 1: 9, was 14: the belly's heart a softer shade, not grey blotches inside a white cloud)
	// (review round 2: 6, and a little of the billows: a heavy grey heart and dark lobes read as dirty smudges in a
	// lit cloud; the billows' form comes from their relief toward the sun instead)
	own += b.hIn < 0.02 ? 6.0 * b.depth * (1.0 + 0.3 * b.bill) : 0.0;
#ifdef WC_FIELD
	float hl = length(l.xz);
	if (b.field && hl > 0.03) {
		vec2 dir = l.xz / hl;
		float slope = l.y / hl;
		float sec = sqrt(1.0 + slope * slope);
		// (clouds2: the cells' width at the point, continuous with the distance: the ring's own doubled where one ring
		// met the next, and the light through a deck stepped there in a long straight seam)
		float cs = float(WC_CELL) * clamp(1.5 * length(b.p.xz) / WC_RING0, 1.0, float(1 << WC_MAX_LEVEL));
		float extra = float(WC_LOW_TIERS) * WC_TIER;
		float path = min(own, 0.15 * cs * sec);
		float prev = 0.15 * cs;
		for (int i = 0; i < WC_LIGHT_SAMPLES; i++) {
			// (two samples at LOW: farther apart)
			float a = cs * (WC_LIGHT_SAMPLES < 4 ? (i == 0 ? 0.6 : 3.0) : (i == 0 ? 0.4 : (i == 1 ? 1.1 : (i == 2 ? 2.6 : 5.5))));
			vec2 f = wcField(b.p.xz + dir * a, b.lv, e);
			float m = b.k == 0 ? f.x : f.y;
			float mr = clamp(m * 2.0 - 1.0, 0.0, 1.0);
			float top = b.iv.x + WC_DECK + (b.k == 1 ? wcMainHeightAt(wcMainRise(mr)) : mr * extra);
			float yr = b.y + a * slope;
			// (clouds2: the sample's ray leaves the heap over a few tiers' height, not two blocks: the light through a belly had
			// stepped where the samples crossed the heaps' tops, in hard-edged dark contours under a low sun)
			path += smoothstep(0.36, 0.72, m) * smoothstep(-12.0, 12.0, top - yr) * (a - prev) * sec;
			prev = a;
		}
		return path;
	}
#endif
	return min(own, 64.0);
}

// The light a cloud gives back of one body's light (`col`, from direction l): Beer's law along the path through the
// cloud with a gentle back-scatter off its lit side and a forward lobe through its thin parts, plus two softer octaves
// of the light scattered many times; a voxel side turned from the light a little darker, as a block's.
vec3 wcBodyLight(CloudBody b, vec3 n, float sigma, vec3 rd, vec3 l, vec3 col, CloudEnv e, out vec3 glint) {
	// (clouds2: the optical depth eased off past about 2.5: a tall cloud's heart toward a low sun crossed a hundred blocks
	// of cloud and fell to a mid grey core; the light scattered all through a cumulus keeps its shaded parts luminous)
	float tau = sigma * wcLightPath(b, l, e);
	tau = 2.5 * (1.0 - exp(-tau / 2.5)) + 0.08 * tau;
	float c = dot(rd, l);
	float thin = 1.0 - b.depth;
	// (seen from the lit side a thin part gives back less than a deep one: the powder look of a cumulus's sunlit flank)
	float powder = mix(1.0, 0.74 + 0.26 * b.depth, clamp(0.4 - 1.4 * c, 0.0, 1.0));
	// (the forward lobe is light come through the cloud: a top lit from above shows little of it)
	// (review round 2: a wider, gentler lobe, capped lower: a cloud edge seen edge-on beside the sun drew a hard white
	// streak like a lens line; the silver lining stays, softer)
	float fwd = min(wcHG(c, 0.6), 7.0) * thin * thin * (n.y > 0.5 ? 0.06 : 0.16);
	float p1 = mix(1.0, wcHG(c, -0.3), 0.5) * powder + fwd;
	// (a base takes less of the many-times-scattered light at dusk: the low sun's colour belongs on the tops and the
	// edges, the bellies keep the dusk sky's)
	// (and through the golden hour every face takes a little less of it: the warm light stays on what the sun reaches,
	// the shade between keeps the dusk's violet, never a brown mix of the two)
	float dk = wcDuskAmount(e.sun.y);
	// (review round 2: toward the sun a belly keeps the low sun's many-times-scattered light, so a sunset deck's
	// underside glows peach and gold from its thin sunward edges into a darker heart (the light's path through the
	// cloud gives the gradient); away from the sun it keeps the dusk sky's colours as before)
	float sw = wcSunward(rd, e);
	float sw2 = sw * sw;
	float under = (n.y < -0.5 ? 1.0 - 0.7 * dk * (1.0 - 0.8 * sw2) : 1.0) * (1.0 - 0.35 * dk * (1.0 - 0.6 * sw2));
	// (review round 2: the many-times-scattered light leans forward too, more gently than the single scattering: a
	// cloud's shaded body seen toward the light is brighter than seen from behind it)
	float msPhase = mix(1.0, min(wcHG(c, 0.35), 2.6), 0.45);
	// (review round 2: a third, softest octave: the light scattered all through a cloud keeps even a thick heart bright,
	// as the reference packs' clouds are)
	float ms = exp(-tau) * (p1 - fwd) + (0.6 * exp(-0.3 * tau) * mix(1.0, p1, 0.3) + 0.45 * exp(-0.1 * tau)
		+ 0.35 * exp(-0.03 * tau)) * under * msPhase;
	// (a top's dome takes the light most squarely at its heart, its rims at a grazing angle: domes read from above)
	// (review round 1: a side bright at its brow and darker toward its foot, so a cloud's sides read as a rounded,
	// lit volume from below, not a flat band)
	// (review round 2: a side's brow by its height in the deck, not in its own column, so the sides of neighbouring
	// columns of different heights share one gradient instead of stacking in bands; the billows on tops and sides)
	float hDeck = b.k >= 0 ? clamp((b.y - b.iv.x) / wcDeckSpan(b.k), 0.0, 1.0) : b.hIn;
	float face = n.y > 0.5 ? mix(0.72, 1.0, smoothstep(0.0, 0.7, b.depth)) * (1.0 + 0.2 * b.bill)
		: (n.y < -0.5 ? 1.0 : (0.64 + 0.4 * dot(n.xz, l.xz) / max(length(l.xz), 1e-3)) * mix(0.72, 1.08, hDeck * (2.0 - hDeck)) * (1.0 + 0.12 * b.bill));
	// (clouds2: the heaps' flanks, now walls up to 84 blocks tall, turn and shade more than a slab's thin sides did: a
	// flank across the sun's path a soft shade under its sunlit brow, darker toward its foot; at 0.8 to 1.1 every wall
	// of a morning deck came out one flat white past the shoulder)
	// the forward lobe's single scattering apart: the silver lining, laid on past the shoulder (wcCloudLight), so a
	// thin rim toward the sun may blaze past white and the bloom takes it
	glint = col * (exp(-tau) * fwd * face * WC_DIRECT);
	return col * (ms * face * WC_DIRECT);
}

// The sun as the clouds see it, premultiplied: the land's sun, held on longer round the horizon (the decks stand
// high, in less air) and warmer, turning orange and rose as it sinks and under the horizon; dimmed by a closed cover.
vec3 wcCloudSun(CloudEnv e) {
	vec4 s = skyTable(SKY_X_SUN, SKY_ROW_LIGHT);
	vec3 hue = s.a > 0.02 ? s.rgb / s.a : vec3(0.55, 0.30, 0.20);
	// gold through the golden hour, a deep orange over the last degrees, rose once the sun is under the horizon
	// (review round 1: only the hue moves, the brightness is kept: these targets are dim colours, about 0.4 of the
	// sun's luma, and mixing toward them halved the low sun on the clouds, so a dusk deck's lit faces came out brown)
	float y0 = max(wcLuma(hue), 1e-4);
	hue /= y0;
	float low = 1.0 - smoothstep(0.03, 0.22, e.sun.y);
	hue = mix(hue, vec3(0.58, 0.39, 0.25) / 0.4203, 0.45 * low);
	hue = mix(hue, vec3(0.58, 0.36, 0.26) / 0.3995, 0.5 * (1.0 - smoothstep(-0.01, 0.09, e.sun.y)));
	hue = mix(hue, vec3(0.60, 0.36, 0.40) / 0.4140, smoothstep(0.0, -0.08, e.sun.y));
	hue *= y0 / max(wcLuma(hue), 1e-4);
	float strength = max(s.a, smoothstep(-0.10, 0.06, e.sun.y) * (1.0 - 0.85 * wcClosed()));
	return hue * strength * (1.0 + 0.2 * wcDuskAmount(e.sun.y) * (1.0 - wcClosed()));
}

// The cloud's colour at the body with face normal n, tone `tone`, seen along rd; `shadow` the share of the sun past
// the decks above.
vec3 wcCloudLight(CloudBody b, vec3 n, int tone, vec3 rd, float shadow, CloudEnv e) {
	float sigma = tone == 0 ? WC_SIGMA.x : (tone == 1 ? WC_SIGMA.y : WC_SIGMA.z);
	float closed = wcClosed();
	float dusk = wcDuskAmount(e.sun.y);
	float night = skyNightStep();
	float lower = 1.0 - b.hIn;
	float tall = clamp((b.top - b.iv.x) / 16.0, 0.25, 1.0);

	// the sky's light: a top sees all the dome, a base the lower half of it; deep inside a thick cloud's lower part
	// less of it gets in
	// (a top's hollows, its thin rims and the lower tiers between taller ones see a little less of it than its domes)
	// (review round 2: a base 0.72, was 0.62: a fair cloud's belly a light, soft grey, not a mid grey)
	float view = n.y > 0.5 ? mix(0.5, 0.92, smoothstep(0.0, 0.8, b.depth)) : (n.y < -0.5 ? 0.72 : mix(0.68, 0.92, b.hIn));
	// (review round 1: 0.3, was 0.42, the same: fewer dark blotches in a belly)
	float occl = (1.0 - 0.3 * b.depth * lower * mix(0.6, 1.0, tall)) * (1.0 - 0.08 * b.bill * lower);
	// (at night less of it: the land's night fill is lifted for play, and a cloud as bright read as a patch of day sky)
	vec3 amb = skyAmbientLight() * (view * occl * mix(1.0, 0.55, night));
	// round sunset the shaded parts take the dusk sky's own hue, warm toward the sun and lilac away from it
	// (a base sees the dusk's lower sky and the dimming land: lilac and dusk blue, rose only toward the sun)
	if (dusk > 0.0) {
		float sw = wcSunward(rd, e);
		// (a belly takes the dusk's rose and violet, rosier toward the sun: a sunset's clouds glow, never a muddy taupe)
		vec3 hue = n.y > 0.5 ? mix(mix(WC_LILAC, WC_VIOLET, 0.4), mix(WC_ROSE, WC_GOLD, 0.3), sw)
			: mix(mix(WC_VIOLET, WC_DUSK, 0.25), mix(WC_ROSE, WC_VIOLET, 0.25), sw * sw);
		// (review round 1: the hue at the ambient light's own brightness; these palette colours sit at about 0.6 luma
		// and taken as they were they darkened every dusk face to brown)
		hue /= max(wcLuma(hue), 1e-4);
		amb = mix(amb, hue * ((n.y > 0.5 ? 0.92 : mix(0.9, 1.05, sw * sw)) * wcLuma(amb)), 0.85 * dusk * (1.0 - closed));
	}
	// the land's bounce under a cloud: its sunlit ground's light thrown up, warmer and stronger at a low sun
	vec3 sunC = wcCloudSun(e);
	vec3 bounce = sunC * (max(e.sun.y, 0.0) * 0.28 * vec3(0.92, 1.0, 0.86) + 0.07 * dusk * vec3(1.0, 0.8, 0.75))
		* lower * (n.y > 0.5 ? 0.0 : 1.0);

	vec3 light = amb + bounce;
	vec3 glint = vec3(0.0), g;
	if (e.sun.y > -0.12) {
		// (review round 2: the billows' relief, strongest with the sun low and to the side)
		float lat = 1.0 - smoothstep(0.35, 0.95, e.sun.y);
		light += wcBodyLight(b, n, sigma, rd, e.sun, sunC, e, g) * shadow * (1.0 + 0.5 * clamp(b.relief, -0.3, 1.0) * lat);
		glint += g * shadow;
	}
	if (night > 0.0) {
		vec3 moonW = normalize(mat3(gbufferModelViewInverse) * moonPosition);
		if (moonW.y > -0.05) {
			light += wcBodyLight(b, n, sigma, rd, moonW, skyMoonLight() * WC_MOON_GAIN, e, g);
			glint += g;
		}
	}
	// the sun just under the horizon still lights the decks from below: a rose and peach glow under them toward it,
	// fading across the sky, more in a thin part and less under a closed cover
	float under = wcUnderLit(e.sun.y) * (1.0 - closed) * lower;
	if (under > 0.0) {
		float sw = wcSunward(rd, e);
		vec3 glow = mix(WC_ROSE, WC_ORANGE, 0.6 * sw) * wcLuma(skyHorizon(true));
		light += glow * under * mix(0.2, 1.0, sw * sw) * mix(1.0, 0.55, b.depth) * (n.y < 0.5 ? 1.0 : 0.0);
	}
	vec3 c = wcAlbedo(tone) * light;
	// (clouds2) by day a cloud's shaded parts sit under the bright sky behind them, as the reference packs' do: the belly
	// and the lower sides turned from the sun a soft blue-grey, deepest under the thick heart, lightest at the thin rim,
	// so each cloud reads as a lit volume over its own shade (they had come out as bright as the sky, a white smear)
	float dayK = wcDay(e);
	if (dayK > 0.0) {
		float away = n.y < -0.5 ? 1.0 : (n.y > 0.5 ? 0.0 : lower * (0.55 - 0.45 * dot(n.xz, e.sun.xz) / max(length(e.sun.xz), 1e-3)));
		float shade = dayK * away * mix(0.3, 1.0, b.depth);
		vec3 cool = skyZenith() / max(wcLuma(skyZenith()), 1e-4);
		c = mix(c, c * mix(vec3(1.0), clamp(cool, 0.6, 1.6), 0.25) * 0.84, shade);
	}

	// (review round 1) round sunrise and sunset a cloud is never darker than the sky behind it: the light scattered many
	// times inside a deck comes from the whole bright low sky, which the luminous sky made brighter than the bellies'
	// ambient term, so the dusk and dawn decks stood as brown and charcoal slabs over a glowing sky. The floor is the
	// sky behind the cloud in rose and peach (peach toward the sun, rose-lilac away from it), a little over it, most on
	// the thin parts and the tops, so the forms still read; the lit tops and the silver lining go on over it.
	// (and through the golden hours, more gently: a morning or late-afternoon cloud's belly takes the warm low sky's
	// light rather than a grey shade under it)
	// (only once the hour is well golden: the floor costs a look at the sky, which a mid-afternoon need not pay)
	float goldenK = smoothstep(0.3, 0.9, skyTable(SKY_X_STATE_TIME, SKY_ROW_LIGHT).r) * (1.0 - dusk);
	float glowK = max(dusk, 0.75 * goldenK) * (1.0 - closed) * (1.0 - 0.7 * smoothstep(0.2, 0.8, rainStrength));
	if (glowK > 0.0) {
		float sw = wcSunward(rd, e);
		// (the dome's horizon and zenith under the luminous sky: the sky behind, near enough for a floor and a third of
		// skyDomeOnly's table reads)
		vec3 behind = skyLuminous(wcDome(rd, e), rd, skyLumFrame(e.sun), 1.0);
		// (rose away from the sun, peach toward it; their own colour, the clouds glow in the low sun's light)
		vec3 hueF = mix(vec3(0.97, 0.72, 0.70), vec3(1.0, 0.70, 0.50), sw);
		// (in the golden hours with the sun still high, a warm cream: the light of a bright low sky, not a sunset's)
		hueF = mix(hueF, mix(vec3(0.96, 0.90, 0.88), vec3(1.0, 0.88, 0.74), sw), goldenK);
		hueF /= wcLuma(hueF);
		// (thin rims and ragged edges brightest, the thick heart of a belly a soft shade under them: the forms read)
		// (review round 2: brighter than the sky behind by more, the bellies most: a sunset's clouds glow pink and peach
		// over a lilac sky, away from the sun too (the low sun lights them from the side), as both reference packs' do;
		// and their billows' relief shows)
		// (clouds2: lower under the heart of a belly, a little over the sky there, and highest on the thin rims, the
		// sides' brows and the tops: every face had been lifted to half again the sky's light, and in the tone curve's
		// shoulder a whole sunset deck came out one flat cream; now the bellies keep their form under lit rims)
		float lift = (n.y > 0.5 ? 1.6 : (n.y < -0.5 ? 1.22 + 0.25 * dusk : mix(1.1, 1.5, b.hIn))) * (n.y > 0.5 ? mix(1.0, 1.15, sw * sw) : mix(1.0, 0.8, sw * sw * sw * sw))
			* mix(1.3, mix(0.84, 0.95, dusk), b.depth * mix(0.6, 1.0, lower)) * mix(1.0, 0.82, goldenK) * (1.0 - 0.15 * b.bill) * (1.0 + 0.35 * clamp(b.relief, -0.3, 1.0))
			* (tone == 2 ? 0.8 : (tone == 1 ? 0.9 : 1.0));
		// (clouds2: the flanks and bellies seen toward the low sun a little under the glow behind them, as a backlit cloud
		// is, so its silver rims show: lifted over that glow, a sunset deck around the sun came out one washed cream)
		// (the shortfall is added in the floor's own warm light, not taken channel by channel: a per-channel max of a
		// cool grey belly and a cream floor came out a mauve-brown mottle)
		float fy = wcLuma(behind) * lift;
		c += hueF * (max(fy - wcLuma(c), 0.0) * glowK);
		// (clouds2: the shaded faces take the floor's rose and peach at their own brightness: a belly turned from the low
		// sun had come out a muddy brown, the low sun's orange at a fraction of its light)
		float shadeF = n.y < -0.5 ? 1.0 : (n.y > 0.5 ? 0.0 : 0.5 * lower);
		vec3 shadeHue = mix(vec3(0.88, 0.76, 0.88), vec3(0.97, 0.74, 0.72), sw);
		c = mix(c, shadeHue * (wcLuma(c) / wcLuma(shadeHue)), 0.7 * shadeF * dusk * (1.0 - closed) * (1.0 - 0.5 * sw));
	}

	// at night a cloud is never a black cut-out in the stars: its body takes at least the night dome's own colour
	// behind it, a top a little over it, a base a little under
	float nightK = night * (1.0 - dusk);
	if (nightK > 0.0) {
		vec3 behind = skyDomeOnly(rd, skyFrame());
		float k = n.y > 0.5 ? 1.12 : (n.y < -0.5 ? 0.88 : 0.98);
		vec3 tint = mix(behind, vec3(wcLuma(behind)), 0.35) * k * (tone == 2 ? 0.8 : (tone == 1 ? 0.9 : 1.0));
		c = mix(c, max(c, tint + 0.3 * c), nightK);
	}
	// under a closed overcast the base is kept near the overcast dome's own brightness in that direction, a heavy
	// grey that still lies darker under the thick parts, so a rain deck reads as one sky with a body
	// (a side only toward its foot: a side's brow is lit as the top over it, or the overcast's sides stood as pale
	// streaks along every tier seen from above)
	float baseLike = n.y < -0.5 ? 1.0 : (n.y < 0.5 ? lower * lower : 0.0);
	if (closed > 0.0 && baseLike > 0.0) {
		vec3 dome = wcDome(rd, e) * mix(1.04, 0.5, b.depth * lower) * (tone == 2 ? 0.78 : (tone == 1 ? 0.92 : 1.0));
		c = mix(c, mix(c, dome, 0.6), closed * baseLike);
	}
	if (e.flash && tone == 2) {
		c = mix(c, vec3(0.86, 0.86, 1.0), 0.5);
	}
	// the silver lining past the shoulder, less of it under a closed cover (it has no sun to show)
	// (review round 2: at most 0.55 past the shoulder: the rim glows and blooms without a hard white line)
	return wcShoulder(c) + min(wcAlbedo(tone) * glint * (1.0 - 0.7 * closed), vec3(0.55));
}

// ------------------------------------------------------------------------------------------------- distance

// What a far cloud sinks into: the horizon's light in its direction (toward the sun its glow); a deck high over the eye
// a little of the sky's band at its height.
vec3 wcFogTarget(vec3 rd, CloudEnv e) {
	vec3 hor = wcHorizon(rd, e);
	if (rd.y <= 0.0) {
		// atmos (welkin-atmos): far clouds sink into the same luminous air as the land (lib/sky/luminous.glsl)
		return skyLuminous(hor, normalize(vec3(rd.x, 0.0, rd.z) + vec3(1e-5, 0.0, 0.0)), skyLumFrame(e.sun), 0.6);
	}
	float x = clamp(sqrt(rd.y) * 4.6 - 0.5, 0.0, 4.0) / 4.0;
	vec3 band = skyRowAt(1.0 + 4.0 * x, SKY_ROW_DOME) + (hor - skyHorizon(false)) * (1.0 - x);
	// (the clouds rework: a far deck high over the eye takes mostly the horizon's light, 0.7 of it; the merge of phase 2:
	// that air is then the luminous sky's, as for the land, so far clouds and far land fade into one band)
	return skyLuminous(mix(band, hor, 0.7), rd, skyLumFrame(e.sun), 0.6); // atmos (welkin-atmos) + clouds (welkin-clouds)
}

// How far a hit sinks into the horizon colour: a little from a few hundred blocks out, then smoothly more with each
// doubling of the distance, one curve over every ring and the sheets (composite2 lays the land's haze over them
// besides); more in rain.
float wcFog(CloudHit h) {
	// (clouds2: a third of what it was: composite2 lays the land's haze over the clouds at their distance besides, and
	// the two together washed every deck past a kilometre into the sky's own white, the clouds reading as faint smears)
	float f = clamp((wcFar(h.t) - 1.5) * 0.045, 0.0, 0.3);
	f = min(f + 0.08 * smoothstep(0.6 * WC_REACH, WC_REACH, h.t), 0.9);
	return mix(f, min(1.0, f * 1.4 + 0.08), smoothstep(0.2, 0.8, rainStrength));
}

// How thin a side face is on screen, 0 (a broad face) .. 1 (a strip a pixel or two across): a deck's side seen from
// afar or nearly edge-on takes its cap's light, so the outlines show no bright or dark streaks.
float wcSideThin(CloudHit h, vec3 rd, float tall, float pixAngle) {
	if (abs(h.n.y) > 0.5) {
		return 0.0;
	}
	float px = tall * sqrt(max(1.0 - rd.y * rd.y, 0.0)) / max(h.t * pixAngle, 1e-6); // pixels up the face
	float across = abs(dot(h.n, rd));                                                // how squarely it is seen
	return max(1.0 - smoothstep(2.5, 9.0, px), 1.0 - smoothstep(0.04, 0.22, across));
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

// The colour of a cloud face at altitude `y` from its cell alone (the far bands): no field, no shadow from above.
vec3 welkinCloudFace(CloudHit h, vec3 rd, float y, float interior, CloudEnv e) {
	CloudBody b;
	b.p = rd * h.t;
	b.y = y;
	b.k = wcDeckAt(y, e);
	float deck = b.k == 0 ? e.layers.x : (b.k == 2 ? e.layers.z : e.layers.y);
	b.iv = vec2(deck, deck + WC_DECK + 0.5 * (wcDeckSpan(max(b.k, 0)) - WC_DECK));
	b.hIn = h.n.y > 0.0 ? 1.0 : 0.0;
	b.depth = 0.75; // about a broad deck's average, so the band meets the cells it takes over from
	b.lv = h.level;
	b.field = false;
	b.bill = 0.0;
	b.relief = 0.0;
	return wcCloudLight(b, h.n, h.tone, rd, 1.0, e);
}

// The finished colour of a cloud hit, from the ray's origin `ro` (camera-relative) along `rd`. `pixAngle`: radians
// per screen pixel. Far off and at a grazing angle the decks become a soft band (wcBandOf), not here.
vec3 welkinCloudColor(CloudHit h, vec3 ro, vec3 rd, CloudEnv e, float pixAngle) {
	CloudBody b = wcBodyOf(h, ro, rd, e, true);
	vec3 n = h.n;
	if (abs(n.y) < 0.5) {
		// a side only a few pixels tall, or seen edge-on, takes its cap's light: its base from below, its top from above
		float thin = wcSideThin(h, rd, b.iv.y - b.iv.x, pixAngle);
		b.hIn = mix(b.hIn, rd.y > 0.0 ? 0.0 : 1.0, thin);
		n = thin > 0.5 ? vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0) : n;
	}
	float sun = wcSelfShadow(n, b.p - h.n * 0.05, e);
	vec3 c = wcCloudLight(b, n, h.tone, rd, sun, e);
#if CLOUD_DEBUG == 1
	vec3 dk = b.k == 0 ? vec3(1.0, 0.2, 0.2) : (b.k == 1 ? vec3(0.2, 1.0, 0.2) : (b.k == 2 ? vec3(0.3, 0.4, 1.0) : vec3(1.0)));
	return dk * (h.n.y > 0.5 ? 0.6 : (h.n.y < -0.5 ? 0.3 : 1.0)) * ((h.level & 1) == 0 ? 1.0 : 0.75);
#elif CLOUD_DEBUG == 2
	return vec3((b.top - b.iv.x) / 24.0, b.depth, b.field ? 1.0 : 0.0);
#elif CLOUD_DEBUG == 3
	return vec3(wcLightPath(b, e.sun, e) / 64.0);
#endif
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
	return 0.66 * wcSmooth(0.4, 0.8, w.r); // (review round 2: as wcColumn's high deck)
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
	if (!wcPresent(deck) || abs(rd.y) < 1e-5 || (y0 >= deck && y0 <= deck + wcDeckSpan(k))) {
		return vec4(1e30, 1e30, 0.0, 0.0);
	}
	float tIn = ((rd.y > 0.0 ? deck : deck + wcDeckSpan(k)) - y0) / rd.y;
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
	f.level = wcLevel(tMid * length(rd.xz));
	// (the clouds rework: the band takes the tone its cells would, from the weather there, as wcColumn sets it; a fair
	// band beyond grey rain cells drew a pale sawtooth line along the deck where the cells gave way to it)
	vec4 w = wcWeatherAt(rd.xz * tMid, false, e);
	float wide = max(w.a, wcSmooth(0.79, 0.93, w.r));
	f.tone = wide > 0.45 ? 2 : (w.b > 0.25 || wcSmooth(0.72, 0.94, w.r) > 0.5 ? 1 : 0);
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
	float off = max(max(deck - e.camY, e.camY - deck - wcDeckSpan(k)), 0.0);
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
	float a = deck, b = deck + wcDeckSpan(k), m = WC_SLAB_MARGIN;
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
