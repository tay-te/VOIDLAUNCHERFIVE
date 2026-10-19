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
// (HDR stage B) the sun's light scattered forward through a thin cloud over the disc, scene light
#define WC_THROUGH_SUN 6.0
// the silver lining's light past the body, scene light per unit of its authored strength (at most 0.55)
#define WC_RIM_SCENE 6.0
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

// skies (welkin-skies): the painterly ramp's levels (wcPaint)
#define WS_LIT_LO 0.55
#define WS_LIT_HI 1.1
#define WS_BELLY 0.85
#define WS_BELLY_WARM 0.8
#define WS_LIT 1.5
#define WS_LIT_WARM 1.25
#define WS_PAINT 1.0
#define WS_BELLY_RIM 0.42
#define WS_BELLY_THIN 0.12
#define WS_UNDER 0.9
#define WS_RIM_W 0.6
#define WS_RELIEF 0.35
#define WS_SIDE_NOON 0.85
#define WS_SIDE_FOOT 0.3
#define WS_FOOT 0.0
#define WS_BROW 0.8
#define WS_NOON_LIT vec3(1.0, 0.96, 0.88)
#define WS_NOON_SHADE vec3(0.88, 0.88, 1.06)
// (Look D3) the paint's levels in scene light, against the sky behind the cloud (wcPaint)
#define WD_LIT 1.15
#define WD_LIT_WARM 0.85
#define WD_LOW_SUN 0.3
#define WD_SHADE_KNEE 0.5
#define WD_SHADE_SOFT 0.8
#define WD_UNDER_SKY 0.45
#define WD_BELLY 0.52
#define WD_BELLY_WARM 0.5
#define WD_BELLY_SHOWER 0.42
#define WD_BELLY_STORM 0.30
#define WD_BOUNCE 0.18
#define WD_BOUNCE_HUE vec3(1.12, 0.96, 0.78)
#define WD_TURN 0.25
#define WD_TURN_HUE vec3(1.10, 0.95, 0.88)
#define WD_BACKGLOW 1.2
// (Look D5) a deck toward a low sun: how much darker its heart, its rim's light (x the sky behind), how much of its far
// fog it sheds
#define WD_BACKLIT_HEAVY 0.45
// (Look D5) how much of the lit share a face turned from a low sun loses, seen toward it (its outline keeps it)
#define WD_BACKLIT_SHADE 0.8
#define WD_BACKLIT_RIM 1.6
#define WD_BACKLIT_FOG 0.75
// the silver lining's scene light at noon .. through the warm hours (times WC_RIM_SCENE)
#define WD_RIM_NOON 0.5
#define WD_RIM_WARM 1.5
// (Look D3) the moonlit paint (night): against the night sky behind the cloud
#define WN_SILVER vec3(0.84, 0.90, 1.0)
#define WN_SHADE vec3(0.80, 0.83, 0.94)
#define WN_LIT 1.9
#define WN_BELLY 0.7
#define WN_EDGE 1.4
#define WN_PAINT 0.9
#define WS_SHADE_SKY 0.35
#define WS_DUSK_SHADE vec3(0.84, 0.80, 0.98) // (Look D3: less violet, was (0.78, 0.74, 1.0): in scene light it read lilac)
#define WS_CLOSED_KEEP 0.75
#define WS_STORM_HEART 0.32
#define WS_STORM_CELL 0.6
// (Look D6) a closed deck's belly: its thick heart deeper still, its thin seams and rim a pale silver over the sky's level,
// and how much of the belly the paint takes
#define WS_STORM_DEEP 0.55
#define WS_STORM_BELLY 0.72
#define WS_STORM_SEAM 1.05
#define WS_STORM_PAINT 0.85

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

// (Look D3) The sky's own colour in direction rd in scene light, the sky behind a cloud for its paint. The clouds pass
// works out the horizon (away, toward the sun) and zenith in scene light once in its vertex shader (WC_SKY_FLAT: the
// curve's inverse per pixel cost a tenth of a millisecond); elsewhere they are converted here.
#ifdef WC_SKY_FLAT
flat in vec3 wcSkyHorAwayS;
flat in vec3 wcSkyHorSunS;
flat in vec3 wcSkyZenS;
#endif
vec3 wcDomeScene(vec3 rd, CloudEnv e) {
#ifdef WC_SKY_FLAT
	float spread = max(skyTable(SKY_X_STATE_GLOW, SKY_ROW_LIGHT).a * 2.0, 0.05);
	float toward = 1.0 - smoothstep(0.0, spread, 1.0 - dot(rd, e.sun));
	return mix(mix(wcSkyHorAwayS, wcSkyHorSunS, toward), wcSkyZenS, smoothstep(0.02, 0.7, abs(rd.y)));
#else
	return sceneFromAuthored(wcDome(rd, e));
#endif
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
	float rim;     // skies: how near the cloud's own voxel outline: 1 at an open side of the cell .. 0 a few blocks in
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
	// skies (welkin-skies): the rim, from the finished map's edge bits (no neighbour read): the distance to the nearest
	// open side of the hit's cell, over about two thirds of a cell, so a belly lightens toward the cloud's crisp outline
	// in a soft gradient that follows it (the smooth field's contours drew grey ovals across a belly instead)
	b.rim = 0.0;
	{
		int edges = (b.k >= 0 ? (tx.z >> (4 * b.k)) : tx.w) & 15;
		if (edges != 0) {
			int s = WC_CELL << h.level;
			vec2 local = clamp(b.p.xz - wcCellCorner(h.cell, s, e), vec2(0.0), vec2(float(s)));
			vec4 dist = vec4(local.x, float(s) - local.x, local.y, float(s) - local.y);
			dist = mix(vec4(1e4), dist, vec4(ivec4(edges, edges >> 1, edges >> 2, edges >> 3) & 1));
			b.rim = 1.0 - smoothstep(0.0, WS_RIM_W * float(s), min(min(dist.x, dist.y), min(dist.z, dist.w)));
		}
	}
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

// (Look D5) Where a cloud's outline is as seen toward a low sun: a side face is the cell's outline in the world (its rim
// and thin share are full there), but a side turned to the eye is the cell's shaded side against the light. The outline
// on screen is a face seen nearly edge-on, or a belly's own outline (and thinnest part)
float wcBackEdge(CloudBody b, vec3 n, vec3 rd) {
	float graze = n.y < -0.5 ? 0.0 : smoothstep(0.55, 0.93, 1.0 - abs(dot(n, rd)));
	float belly = n.y < -0.5 ? max(b.rim * b.rim, pow(1.0 - b.depth, 6.0)) : 0.0;
	return max(graze * max(b.rim, 0.5), belly);
}

// ------------------------------------------------------------------------------------------------- skies: the paint
// skies (welkin-skies, 2026-10-18): the painterly ramp. The light above (wcBodyLight and the sky's) gives every point of
// a cloud its form: how much of the sun reaches it, against a sunlit top. Here that form is laid in with two colours,
// as a painter lays in a cloud: a heavy belly colour (a warm-neutral slate by day, deeper in rain and storm, dusky at
// the golden hour) and a lit colour in the sun's own light (cream at noon, gold and ember as it sinks), blended by a
// soft S over the form, so the sunlit tops and flanks glow, the thin rims stay light and the thick bellies hang dark
// and heavy, with soft gradients between. Night keeps the moonlit light below.
vec3 wcPaint(vec3 c, CloudBody b, vec3 n, int tone, vec3 rd, vec3 sunC, float shadow, vec3 skyS, CloudEnv e,
	out float weight) {
	// (the night's step is well up by sunset: the dusk keeps the paint)
	float k = 1.0 - skyNightStep() * (1.0 - wcDuskAmount(e.sun.y));
	weight = 0.0;
	if (k <= 0.0) {
		return vec3(0.0);
	}
	vec3 amb = skyAmbientLight();
	float yTop = max(wcLuma(wcAlbedo(0) * (amb * 0.9 + sunC * 1.25)), 1e-4);
	float L = wcLuma(c) / yTop;
	float closed = wcClosed();
	float dusk = wcDuskAmount(e.sun.y);
	float golden = smoothstep(0.1, 0.7, skyTable(SKY_X_STATE_TIME, SKY_ROW_LIGHT).r) * (1.0 - dusk);
	float warm = max(dusk, golden) * (1.0 - closed);
	float sw = wcSunward(rd, e);
	// skies2 (2026-10-18): the form is laid in by the shape, as a painter blocks in a cumulus, not by the light's ratio
	// alone (which left a dusk deck's sunward flanks in shade and a noon deck's walls one flat white):
	//   - a top lit, a little less in its hollows and at its thin rims, its domes turned to a low sun brightest;
	//   - a flank by how squarely it faces the sun (strongly at a low sun, little at noon, when the sky lights all
	//     sides) and by its height in the deck: lit at its brow, sinking into the belly's shade toward its foot, one
	//     soft gradient down the whole wall;
	//   - a belly heavy, light only toward its crisp outline and where it is thin.
	// The decks above (shadow) take the sun off a top or a flank.
	float lowSun = clamp(length(e.sun.xz) / max(length(e.sun), 1e-3), 0.0, 1.0);
	float hDeck = b.k >= 0 ? clamp((b.y - b.iv.x) / wcDeckSpan(b.k), 0.0, 1.0) : b.hIn;
	float sunK = mix(0.25, 1.0, shadow) * (1.0 - 0.6 * closed);
	float lit;
	if (n.y > 0.5) {
		lit = mix(0.72, 1.0, smoothstep(0.0, 0.6, b.depth)) * (1.0 + WS_RELIEF * clamp(b.relief, -1.0, 1.0) * lowSun) * sunK;
	} else if (n.y < -0.5) {
		float r = b.rim * b.rim * (3.0 - 2.0 * b.rim);
		lit = WS_BELLY_RIM * r + WS_BELLY_THIN * (1.0 - b.depth);
	} else {
		float facing = dot(n.xz, e.sun.xz) / max(length(e.sun.xz), 1e-3);
		float sideSun = mix(WS_SIDE_NOON, smoothstep(-0.55, 0.75, facing), lowSun);
		float brow = smoothstep(WS_FOOT, WS_BROW, hDeck);
		lit = sideSun * mix(WS_SIDE_FOOT, 1.0, brow) * sunK;
	}
	// (the light's own form still counts where it is brighter: thin parts lit through, the silver of a backlit edge)
	// (Look D3: toward a low sun only where the cloud is thin: the light through a thick body seen against the sun is a
	// dark backlit shape, its thin edges glow)
	lit = max(lit, 0.6 * smoothstep(WS_LIT_LO, WS_LIT_HI, L) * mix(1.0, 1.0 - b.depth, warm * sw * sw));
	// round sunset the low sun lights the decks from below: the bellies toward it glow, their thin parts most
	// (Look D3: mostly at the outline, 0.15 in a body's heart, was 0.35: a sunset deck's heart hangs dusky)
	float under = dusk * (1.0 - closed) * mix(0.35, 1.0, sw * sw) * mix(0.15, 1.0, b.rim) * (n.y < 0.5 ? 1.0 : 0.4);
	lit = clamp(max(lit, WS_UNDER * under), 0.0, 1.2);
	vec3 sunHue = sunC / max(wcLuma(sunC), 1e-4);
	// (gold through the golden hour, ember toward the sun at sunset, a deep rose-gold away from it)
	// (Look D3: deeper, a light that colours rather than a pale cream: an amber gold, a rose-gold away from a setting sun,
	// ember toward it)
	vec3 goldHue = mix(mix(vec3(1.0, 0.68, 0.34), sunHue, 0.15), mix(vec3(1.0, 0.55, 0.42), vec3(1.0, 0.45, 0.20), sw), dusk);
	vec3 litHue = mix(mix(WS_NOON_LIT, sunHue, 0.3), goldHue, warm);
	// (under a closed cover no sun reaches the tops: their light is the overcast's cool grey)
	litHue = mix(litHue, vec3(0.94, 0.97, 1.04), closed);
	// (Look D3) the hues are written as the eye sees them; in scene light they are their linear forms (an authored
	// gold (1, 0.68, 0.34) is a linear (1, 0.42, 0.10)): written as they were, every warm light came out pale
	litHue = toLinear(litHue);
	litHue /= max(wcLuma(litHue), 1e-4);
	// the shade: a cool blue-grey under the warm lit tops by day (the painter's warm light, cool shade), a dusky violet
	// through the warm hours, warmer toward a sunset sun (a backlit belly holds the low light's ember)
	vec3 skyHue = skyZenith() / max(wcLuma(skyZenith()), 1e-4);
	vec3 shadeHue = mix(WS_NOON_SHADE, skyHue, WS_SHADE_SKY);
	shadeHue = mix(shadeHue, mix(WS_DUSK_SHADE, vec3(0.92, 0.70, 0.72), 0.6 * sw * sw * dusk), warm);
	shadeHue = pow(max(shadeHue, vec3(1e-4)), vec3(1.6));
	shadeHue /= max(wcLuma(shadeHue), 1e-4);
	// (Look D3) painted natively in scene light, against the sky behind the cloud in scene light (`skyS`): no display
	// round trip. A belly hangs WD_BELLY of its sky (heavier deep inside, lighter toward its thin outline), a lit top or
	// flank WD_LIT of it in the sun's colour (a calm cream at noon, gold through the warm hours), with a soft S between
	float ySky = wcLuma(skyS);
	float s = smoothstep(0.0, 1.0, min(lit, 1.0)) + max(lit - 1.0, 0.0);
	// (Look D5) seen toward a low sun, a face turned from it is the deck's shaded side against the light: a small cell is
	// thin all through, and its thin-body light (bellies, the light's own form) lit it a soft peach all over. There only
	// its outline keeps the light; the body hangs in its shade colour (the outline then glows, wcCloudLight's rim)
	{
		float backP = smoothstep(0.50, 0.05, e.sun.y) * smoothstep(-0.14, -0.04, e.sun.y) * (1.0 - closed);
		float away = 1.0 - smoothstep(-0.15, 0.45, dot(n, e.sun));
		float edge = wcBackEdge(b, n, rd);
		s *= 1.0 - WD_BACKLIT_SHADE * backP * sw * sw * away * (1.0 - edge);
	}
	float heavy = (tone == 2 ? WD_BELLY_STORM : (tone == 1 ? WD_BELLY_SHOWER : mix(WD_BELLY, WD_BELLY_WARM, warm)))
		* mix(1.15, 0.8, sqrt(b.depth));
	vec3 shade = shadeHue * heavy;
	// the land's warm light thrown up under a belly (and a flank's foot) through the warm hours
	float low = n.y < -0.5 ? 1.0 : (n.y < 0.5 ? 1.0 - hDeck : 0.0);
	shade += toLinear(WD_BOUNCE_HUE) * (WD_BOUNCE * warm * low * mix(0.5, 1.0, sw) * (1.0 - closed));
	// the lit colour is the sun's own light on the cloud, not a share of its sky: a low sun's tops are dimmer than noon's
	// and darker than the bright sky round the sun (a backlit deck), brighter than the darker sky away from it
	// (the clouds' sun is held at full strength round the horizon for its hue; its light through the long low air is
	// dimmer: WD_LOW_SUN of it on the horizon)
	float sunL = wcLuma(sunC) * (1.0 - 0.6 * closed) * mix(WD_LOW_SUN, 1.0, smoothstep(0.0, 0.4, e.sun.y));
	// (a cloud's shade does not follow a bright sky up: toward a low sun the sky behind blazes, but the deck's body seen
	// against it is the light come through its depth, a dark backlit shape under its glowing edges. Past WD_SHADE_KNEE
	// the sky's level counts softly)
	float yShade = ySky / (1.0 + max(ySky - WD_SHADE_KNEE, 0.0) / WD_SHADE_SOFT);
	vec3 paint = mix(shade * yShade, litHue * (mix(WD_LIT, WD_LIT_WARM, warm) * sunL), s);
	// round sunset the bellies and flanks are lit from under by the low sun and its glowing sky: an ember glow toward it,
	// rose away from it, a share of the sky's own light there, most at the thin outline
	float underG = dusk * (1.0 - closed) * mix(0.25, 1.0, sw * sw) * mix(0.3, 1.0, max(b.rim, (1.0 - b.depth) * (1.0 - b.depth)))
		* (n.y < 0.5 ? 1.0 : 0.25);
	vec3 emberHue = mix(vec3(1.0, 0.56, 0.50), vec3(1.0, 0.50, 0.24), sw);
	emberHue = toLinear(emberHue);
	float edgeU = max(b.rim, (1.0 - b.depth) * (1.0 - b.depth));
	edgeU = mix(edgeU, wcBackEdge(b, n, rd), sw * sw); // (Look D5) at the outline
	paint += emberHue / wcLuma(emberHue) * (WD_UNDER_SKY * underG * mix(yShade, ySky, edgeU) * (1.0 - 0.5 * s));
	// the turn between the light and the shade: a painter's warmer, rosier half-tone through the warm hours
	paint *= mix(vec3(1.0), WD_TURN_HUE, 4.0 * s * (1.0 - s) * warm * WD_TURN);
	// a deck toward a low sun glows at its thin parts and along its outline: the light come through its edges
	float thinE = (1.0 - b.depth) * (1.0 - b.depth);
	// (Look D5) the glow keeps to the outline and the truly thin parts: a small cell is thin all through, and lit through
	// its whole body it read a soft peach. Toward a low sun (sw) it narrows to the outline on screen (wcBackEdge)
	thinE = mix(thinE, wcBackEdge(b, n, rd), sw * sw);
	paint += goldHue / max(wcLuma(goldHue), 1e-4) * (WD_BACKGLOW * ySky * warm * sw * sw * sw * max(thinE, 0.7 * b.rim * (1.0 - sw * sw)) * (1.0 - closed));
	weight = WS_PAINT * k * (1.0 - WS_CLOSED_KEEP * closed);
	return paint;
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
	// skies (welkin-skies): the form painted (wcPaint), in place of the day's blue-grey shade; (Look D3) in scene light,
	// laid over the light's own colour once that is in scene light (below)
	vec3 skyS = wcDomeScene(rd, e);
	float paintW;
	vec3 paintS = wcPaint(c, b, n, tone, rd, sunC, shadow, skyS, e, paintW);

	// (skies (welkin-skies): the dusk and golden-hour floor that lifted every face over the glowing sky behind it, in rose
	// and peach, is gone: it drew a sunset deck as one pastel candy-pink sheet. wcPaint sets the warm hours now: gold
	// and ember lit faces over dusky, heavy bellies, darker than the sky toward the sun)

	// at night a cloud is never a black cut-out in the stars: its body takes at least the night dome's own colour
	// behind it, a top a little over it, a base a little under
	float nightK = night * (1.0 - dusk);
	if (nightK > 0.0) {
		vec3 behind = skyDomeColour(skyDomePos(normalize(rd), skyFrame())); // (authored, display values)
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
		// (skies2: heavier and more threatening: the thick parts sink further, a storm cell's belly darkest)
		float heart = mix(1.0, WS_STORM_HEART, b.depth * lower) * (tone == 2 ? WS_STORM_CELL : (tone == 1 ? 0.88 : 1.0));
		// (Look D6) a storm's belly has structure: its thin parts and its rim let the light above through (a pale silver
		// over the sky's level), its thick heart sinks to a dark slate, and the paint takes more of it, so a rain deck is
		// layered dark masses with lit seams, never one flat grey ceiling
		float thinB = (1.0 - smoothstep(0.0, 0.15, b.depth)) * lower * (b.k == 2 || b.k < 0 ? 0.3 : 1.0);
		float heartS = WS_STORM_BELLY * heart * heart * mix(1.0, WS_STORM_DEEP, smoothstep(0.35, 1.0, b.depth) * lower);
		heartS = mix(heartS, WS_STORM_SEAM, max(thinB, 0.6 * b.rim * b.rim * lower));
		c = mix(c, mix(c, wcDome(rd, e) * heart, 0.6), closed * baseLike);
		paintS = mix(paintS, skyS * heartS, closed * baseLike);
		paintW = mix(paintW, WS_STORM_PAINT, closed * baseLike);
	}
	// (Look D3) the light's colour into scene light, the paint over it
	vec3 cS = mix(sceneFromAuthored(wcShoulder(c)), paintS, paintW);
	// (Look D3) moonlit: at night the clouds are painted in the moon's light against the night sky behind them, as the
	// day's are in the sun's: tops and flanks turned to the moon a cool silver over the sky, bellies a shade under it, and
	// the thin parts and outlines toward the moon glow (the moon's silver lining), never one flat grey slab
	float nightP = night * (1.0 - dusk) * (1.0 - 0.7 * closed);
	if (nightP > 0.0) {
		vec3 moonW = normalize(mat3(gbufferModelViewInverse) * moonPosition);
		float mUp = smoothstep(-0.02, 0.15, moonW.y);
		float yN = wcLuma(skyS);
		float hD = b.k >= 0 ? clamp((b.y - b.iv.x) / wcDeckSpan(b.k), 0.0, 1.0) : b.hIn;
		float faceM = n.y > 0.5 ? mix(0.55, 1.0, clamp(moonW.y, 0.0, 1.0)) * mix(0.75, 1.0, b.depth)
			: (n.y < -0.5 ? 0.0 : smoothstep(-0.45, 0.8, dot(n.xz, moonW.xz) / max(length(moonW.xz), 1e-3)) * mix(0.3, 1.0, hD));
		float cm = 0.5 + 0.5 * dot(normalize(rd.xz + vec2(1e-5)), normalize(moonW.xz + vec2(1e-5)));
		cm = cm * cm * (3.0 - 2.0 * cm);
		float thinM = (1.0 - b.depth) * (1.0 - b.depth);
		vec3 silver = toLinear(WN_SILVER);
		silver /= wcLuma(silver);
		vec3 shadeN = toLinear(WN_SHADE);
		shadeN /= wcLuma(shadeN);
		float sM = smoothstep(0.0, 1.0, faceM) * mUp;
		vec3 nightC = yN * (mix(shadeN * (WN_BELLY * mix(1.15, 0.8, sqrt(b.depth))), silver * WN_LIT, sM)
			+ silver * (WN_EDGE * mUp * cm * cm * cm * max(thinM, 0.7 * b.rim)));
		cS = mix(cS, nightC, nightP * WN_PAINT);
	}
	if (e.flash && tone == 2) {
		cS = mix(cS, sceneFromAuthored(vec3(0.86, 0.86, 1.0)), 0.5);
	}
	// the silver lining past the shoulder, less of it under a closed cover (it has no sun to show)
	// (review round 2: at most 0.55 past the shoulder: the rim glows and blooms without a hard white line)
	// (skies: on a belly only along its rim, or its thin parts glowed all over round the thick heart's grey oval)
	glint *= n.y < -0.5 ? b.rim : 1.0;
	// (HDR stage B) the cloud's authored colour, its silver lining laid on past the shoulder, into scene light: a
	// rim authored past white lands well above the sky (lib/hdr.glsl sceneFromAuthored), so a sunlit edge can blaze.
	// look4's golden-hour exposure and rim boost are gone: the output transform's shoulder holds the peak
	// a deck toward a low sun is seen from its shaded side, against the light: its body darker most toward the sun
	// (look4's backlit body, kept as the cloud's own light), so its thin edges and the sun through it blaze over it
	float backlit = smoothstep(0.50, 0.05, e.sun.y) * smoothstep(-0.14, -0.04, e.sun.y) * (1.0 - closed);
	vec3 backRim = vec3(0.0);
	if (backlit > 0.0) {
		float swk = wcSunward(rd, e);
		float dk = 0.08 + 0.32 * swk * swk + 0.18 * smoothstep(0.75, 0.995, dot(rd, e.sun));
		// (Look D5) the deck between the eye and a low sun is a dark, heavy shape: its thick heart most, its thin parts and
		// outline less (they are lit through, below), so the body reads against the blazing sky rather than a soft peach
		dk += WD_BACKLIT_HEAVY * swk * swk * (1.0 - 0.6 * wcBackEdge(b, n, rd));
		cS *= 1.0 - backlit * min(dk, 0.92);
		// (Look D5) and its thin parts and outline blaze: the low sun's light come through the edge, a gold-white rim at the
		// sky's own level and over it, strongest straight toward the sun
		float edgeR = wcBackEdge(b, n, rd);
		vec3 rimHue = toLinear(vec3(1.0, 0.86, 0.62));
		backRim = rimHue / wcLuma(rimHue) * (WD_BACKLIT_RIM * wcLuma(skyS) * backlit * swk * swk * swk * edgeR);
		// the silver lining likewise keeps to the outline there: over a small cell's whole thin body it read cream
		glint *= mix(1.0, edgeR, backlit * swk * swk);
	}
	float warmRim = max(dusk, smoothstep(0.3, 0.9, skyTable(SKY_X_STATE_TIME, SKY_ROW_LIGHT).r)) * (1.0 - closed);
	// (the body's authored colour into scene light; the silver lining is light on top of it, WC_RIM_SCENE of scene
	// light at its strongest: above the sky, under the sun)
	vec3 outC = cS + backRim + min(wcAlbedo(tone) * glint * (1.0 - 0.7 * closed), vec3(0.55))
		* (WC_RIM_SCENE * mix(WD_RIM_NOON, WD_RIM_WARM, warmRim));
	// a thin cloud across the sun glows with it: the sun's light scattered forward through it, gold-white over the disc
	// and close round it, less through a thick part (the low sun behind a strip of cloud is a blaze in the strip, not a
	// dark bar across it). Real light, scene units, at every hour the sun is up (strongest as it gets low)
	float up = smoothstep(-0.14, -0.04, e.sun.y) * (1.0 - closed);
	if (up > 0.0) {
		float ang = acos(clamp(dot(rd, e.sun), -1.0, 1.0));
		float rS = float(SUN_SIZE) * float(SKY_ART_PIXEL) * 1e-4;
		float through = exp(-max(ang - rS, 0.0) / 0.045) + 0.15 * exp(-ang / 0.15);
		vec3 sunHue = mix(vec3(1.0, 0.86, 0.62) / 0.8810, vec3(1.0, 0.62, 0.32) / 0.6626, smoothstep(0.0, 0.25, ang));
		float low = mix(0.5, 1.0, smoothstep(0.50, 0.05, e.sun.y));
		outC += sunHue * (WC_THROUGH_SUN * up * low * through * mix(1.1, 0.12, b.depth) * (n.y < -0.5 ? 1.0 : 0.6));
	}
	return outC;
}

// ------------------------------------------------------------------------------------------------- distance

// What a far cloud sinks into: the horizon's light in its direction (toward the sun its glow); a deck high over the eye
// a little of the sky's band at its height.
vec3 wcFogTarget(vec3 rd, CloudEnv e) {
	vec3 hor = wcHorizon(rd, e);
	if (rd.y <= 0.0) {
		// atmos (welkin-atmos): far clouds sink into the same luminous air as the land (lib/sky/luminous.glsl)
		return skyLuminousLin(sceneFromAuthored(hor), normalize(vec3(rd.x, 0.0, rd.z) + vec3(1e-5, 0.0, 0.0)), skyLumFrame(e.sun),
			0.6);
	}
	float x = clamp(sqrt(rd.y) * 4.6 - 0.5, 0.0, 4.0) / 4.0;
	vec3 band = skyRowAt(1.0 + 4.0 * x, SKY_ROW_DOME) + (hor - skyHorizon(false)) * (1.0 - x);
	// (the clouds rework: a far deck high over the eye takes mostly the horizon's light, 0.7 of it; the merge of phase 2:
	// that air is then the luminous sky's, as for the land, so far clouds and far land fade into one band)
	return skyLuminousLin(sceneFromAuthored(mix(band, hor, 0.7)), rd, skyLumFrame(e.sun), 0.6); // (scene light)
}

// (Look D5) How much of its far fog a deck keeps: toward a low sun a deck stands dark against the glow, not sunk into it
// (the glow's peach laid over the decks there is what made them read soft)
float wcFogBacklit(vec3 rd, CloudEnv e) {
	float backlit = smoothstep(0.50, 0.05, e.sun.y) * smoothstep(-0.14, -0.04, e.sun.y) * (1.0 - wcClosed());
	float swk = wcSunward(rd, e);
	// (a strip of deck right on the horizon is far off through the low air: it keeps its fog, the shed comes in from a few
	// degrees up)
	return 1.0 - WD_BACKLIT_FOG * backlit * swk * swk * smoothstep(0.01, 0.09, rd.y);
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
	// (Look D6) a deck's top in a gap of the storm decks over it takes the sun a little softer than the land does: lit
	// silver masses in the dark, not snow-white fields
	float sh = welkinCloudShadow(p, e);
	return sh > 1.0 ? 1.0 + (sh - 1.0) * 0.8 : sh;
#endif
}

// (clouds2) The body of a far band's face at altitude `y`: no field, no shadow from above.
CloudBody wcBandBody(CloudHit h, vec3 rd, float y, CloudEnv e) {
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
	b.rim = 0.0;
	return b;
}

// The colour of a cloud face at altitude `y` from its cell alone (the far bands): no field, no shadow from above.
vec3 welkinCloudFace(CloudHit h, vec3 rd, float y, float interior, CloudEnv e) {
	return wcCloudLight(wcBandBody(h, rd, y, e), h.n, h.tone, rd, 1.0, e);
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
#elif CLOUD_DEBUG == 4
	return vec3(b.rim, b.depth, 1.0 - abs(dot(n, rd))) * 0.5; // D5DBG
#endif
	// (Look D6) the far fog's target (the luminous sky, the curve's inverse) only where there is fog: a near cloud has none
	float fogA = airCoverA(wcFog(h), AIR_CLOUDFOG_DENS, AIR_CLOUDFOG_ONSET) * wcFogBacklit(rd, e);
	return fogA > 0.0 ? mix(c, wcFogTarget(rd, e), fogA) : c;
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
vec4 wcBandOf(int k, vec3 ro, vec3 rd, float pixAngle, float tScene, CloudEnv e) {
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
	// (clouds2: 1.2..3 was: the last rows of cells stood as sawtooth bands; the high deck, a thin sheet whose cells are
	// kilometres wide out there, gives way to its band sooner, or its far edge stood as a staircase over the horizon)
	float weight = k == 2 ? 1.0 - smoothstep(4.0, 12.0, rows) : 1.0 - smoothstep(2.0, 5.0, rows);
	// (clouds2: nor behind the land, before the weather is read: the bands were a quarter of the clouds pass)
	if (weight <= 0.0 || tIn >= tScene) {
		return vec4(tIn, tMid, 0.0, 0.0);
	}
	return vec4(tIn, tMid, weight, wcDeckShare(k, wcWeatherAt(q, false, e), e));
}

// (clouds2) The far band of a deck met at tMid along rd, as a hit for welkinCloudShade: its face toward the eye, its
// ring, and the tone its cells would take from the weather there.
CloudHit wcBandHit(vec3 rd, float tMid, CloudEnv e) {
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
	return f;
}

// (clouds2) The finished colour of a cloud hit (band false), or of a far band met as wcBandHit (band true), from the ray's
// origin `ro` along `rd`. The clouds pass calls it once a pixel, so the whole cloud light is compiled there once: a
// second copy for the bands had cost a tenth of a millisecond in registers alone.
vec3 welkinCloudShade(CloudHit h, vec3 ro, vec3 rd, CloudEnv e, float pixAngle, bool band) {
	CloudBody b;
	vec3 n = h.n;
	float sun = 1.0;
	if (band) {
		b = wcBandBody(h, rd, e.camY + ro.y + rd.y * h.t, e);
	} else {
		b = wcBodyOf(h, ro, rd, e, true);
		if (abs(n.y) < 0.5) {
			float thin = wcSideThin(h, rd, b.iv.y - b.iv.x, pixAngle);
			b.hIn = mix(b.hIn, rd.y > 0.0 ? 0.0 : 1.0, thin);
			n = thin > 0.5 ? vec3(0.0, rd.y > 0.0 ? -1.0 : 1.0, 0.0) : n;
		}
		sun = wcSelfShadow(n, b.p - h.n * 0.05, e);
	}
	vec3 c = wcCloudLight(b, n, h.tone, rd, sun, e);
#if CLOUD_DEBUG == 1
	vec3 dk = b.k == 0 ? vec3(1.0, 0.2, 0.2) : (b.k == 1 ? vec3(0.2, 1.0, 0.2) : (b.k == 2 ? vec3(0.3, 0.4, 1.0) : vec3(1.0)));
	return dk * (h.n.y > 0.5 ? 0.6 : (h.n.y < -0.5 ? 0.3 : 1.0)) * ((h.level & 1) == 0 ? 1.0 : 0.75) * (band ? 0.5 : 1.0);
#elif CLOUD_DEBUG == 2
	return vec3((b.top - b.iv.x) / 24.0, b.depth, b.field ? 1.0 : 0.0);
#elif CLOUD_DEBUG == 3
	return vec3(wcLightPath(b, e.sun, e) / 64.0);
#elif CLOUD_DEBUG == 4
	return vec3(b.rim, b.depth, 1.0 - abs(dot(n, rd))) * 0.5; // D5DBG
#endif
	// (Look D6) the far fog's target (the luminous sky, the curve's inverse) only where there is fog: a near cloud has none
	float fogA = airCoverA(wcFog(h), AIR_CLOUDFOG_DENS, AIR_CLOUDFOG_ONSET) * wcFogBacklit(rd, e);
	return fogA > 0.0 ? mix(c, wcFogTarget(rd, e), fogA) : c;
}

// The band's colour for deck k met at tMid, `share` of it cloud, over the sky's horizon colour.
vec3 wcBandColour(vec3 rd, float tMid, float share, CloudEnv e) {
	CloudHit f = wcBandHit(rd, tMid, e);
	float y = e.camY + rd.y * tMid;
	vec3 hor = sceneFromAuthored(wcHorizon(rd, e));
	vec3 cloud = mix(welkinCloudFace(f, rd, y, 0.5, e), wcFogTarget(rd, e), airCoverA(wcFog(f), AIR_CLOUDFOG_DENS, AIR_CLOUDFOG_ONSET) * wcFogBacklit(rd, e));
	return mix(hor, cloud, share);
}

// Inside a cloud: a dense, smooth fog of the cloud's own light over the path through it, so what is seen out of a
// cell fades into it instead of showing the cell's faces as flat halves. `path`: how far the ray stays in the cloud
// (CloudHit.tOut: continuous over the screen, so the cell's outline never shows; the slab's fog takes over past it).
// (Look D6) the veil's colour in scene light, once a pixel (the slab fog's decks share it). Outside the dusk it is the
// same over the screen: the clouds pass works it out in its vertex shader (wcVeilS), as the sky behind the clouds, so
// the curve's inverse is not run per pixel (it was, up to three times, wherever the eye stood near a deck: about 0.3 ms
// at the steppe vista)
#ifdef WC_SKY_FLAT
flat in vec3 wcVeilS;
#endif
vec3 wcVeilColour(vec3 rd, CloudEnv e) {
	float dusk = wcDuskAmount(e.sun.y);
#ifdef WC_SKY_FLAT
	if (dusk <= 0.0) {
		return wcVeilS;
	}
#endif
	vec3 c = wcAlbedo(0) * (skyAmbientLight() * 0.82 + skyDirectLight() * 0.42);
	if (dusk > 0.0) {
		vec3 p = mix(WC_LILAC, WC_CREAM, wcSunward(rd, e));
		c = mix(c, p * max(wcLuma(c), wcLuma(skyHorizon(false))) / wcLuma(p), dusk);
	}
	return sceneFromAuthored(wcShoulder(c));
}

vec3 welkinCloudVeil(vec3 scene, vec3 rd, float path, CloudEnv e) {
	return mix(scene, wcVeilColour(rd, e), 1.0 - exp(-min(path, 1e4) / 7.0)); // (scene light in and out)
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


// (D7) Where the march may stop for deck k: where the ray enters its slab if the deck is all band there and not a fog
// bank (wcBandOf's z, times 1 - wcNearDeck, at least 0.999), else 1e30. The same arithmetic as wcBandOf, without the
// weather: the clouds pass works out its cut before the march and the bands themselves after it, so the bands are not
// held through the march's loop.
float wcBandCut(int k, vec3 ro, vec3 rd, float pixAngle, CloudEnv e) {
	float deck = k == 0 ? e.layers.x : (k == 1 ? e.layers.y : e.layers.z);
	float y0 = e.camY + ro.y;
	if (!wcPresent(deck) || abs(rd.y) < 1e-5 || (y0 >= deck && y0 <= deck + wcDeckSpan(k))) {
		return 1e30;
	}
	float tIn = ((rd.y > 0.0 ? deck : deck + wcDeckSpan(k)) - y0) / rd.y;
	float tMid = (deck + 0.5 * WC_DECK - y0) / rd.y;
	if (tIn <= 0.0) {
		return 1e30;
	}
	vec2 q = ro.xz + rd.xz * tMid;
	float cell = float(WC_CELL) * clamp(2.0 * length(q) / WC_RING0, 1.0, float(1 << WC_MAX_LEVEL));
	float rows = cell * abs(rd.y) / max(tMid * pixAngle, 1e-6);
	float weight = k == 2 ? 1.0 - smoothstep(4.0, 12.0, rows) : 1.0 - smoothstep(2.0, 5.0, rows);
	return weight * (1.0 - wcNearDeck(k, e)) >= 0.999 ? tIn : 1e30;
}

#endif
