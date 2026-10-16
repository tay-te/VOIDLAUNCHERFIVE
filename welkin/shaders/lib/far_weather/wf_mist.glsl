// VOID Welkin far weather: valley mist over the far land, carrying on from the near mist (lib/mist.glsl).
//
// The near mist reads the near tiles (N0/N1, 16 m cells) and fades its density out over their last 16 cells, 112..127
// Chebyshev cells from the near tiles' centre (1792..2032 blocks). The far mist reads the far tiles (F0/F1, 1 km
// cells) with the same rules and fades in over exactly that band, weight 1 - the near mist's, so along any ray the two
// densities add up to one mist that does not step where the tiles change:
//   mist     the cell's mist potential (F1 red) x the hour's mist (void_mistTime, or MIST_FORCE)
//   pool     how readily mist gathers there: the cell's valley-scale lowness (F0 blue) or its water (F0 alpha)
//   slab     from F0's valley floor (the lower quartile of the cell's ground) + 1 + (MIST_LIFT + MIST_GAIN x mist) x pool,
//            capped at the floor + MIST_CAP_LIFT + MIST_CAP_GAIN x mist, as the near slab caps at its basin's floor
//   density  mist x pool, cut to nothing under MIST_FLOOR, as the near mist
// Ground, potential, lowness and water are blended bilinearly between the four nearest far cells, so there is no
// kilometre seam. The ray is sampled where the far mist begins (the band's inner edge), where it is full (the outer
// edge), once between (MIST_SAMPLES >= 3) and at the surface; the slab's top and density run straight between
// samples and the ray meets each piece exactly (mistSegment), plus the surface's own slab, as the near mist. The
// opacity and colour are the near mist's (MIST_EXTINCTION, MIST_OPACITY, mistColor): smooth, no steps, no dither.

#ifndef WELKIN_WF_MIST
#define WELKIN_WF_MIST

#include "/lib/far_weather/wf_config.glsl"

// The near tiles' fade toward their edge at eye-relative xz (1 inside 112 cells, 0 from 127), continuous: the weight
// lib/mist.glsl's mistAt gives its density, from the same cell-centre coordinates.
float wfNearEdge(vec2 eyeXZ, ivec2 nearCentre) {
	ivec2 camCell = cameraPositionInt.xz >> 4;
	vec2 rel = (vec2(cameraPositionInt.xz & 15) + cameraPositionFract.xz + eyeXZ) / 16.0 + vec2(camCell - nearCentre) - 0.5;
	return clamp((127.0 - max(abs(rel.x), abs(rel.y))) / 15.0, 0.0, 1.0);
}

// Where the ray along `dir` leaves the square of half-size `r` near cells round the near tiles' centre (blocks along
// the ray; 0 if the camera is already outside it).
float wfNearSquareExit(vec3 dir, ivec2 nearCentre, float r) {
	ivec2 camCell = cameraPositionInt.xz >> 4;
	vec2 rel0 = (vec2(cameraPositionInt.xz & 15) + cameraPositionFract.xz) / 16.0 + vec2(camCell - nearCentre) - 0.5;
	float t = 1e9;
	if (abs(dir.x) > 1e-6) t = min(t, ((dir.x > 0.0 ? r : -r) - rel0.x) * 16.0 / dir.x);
	if (abs(dir.z) > 1e-6) t = min(t, ((dir.z > 0.0 ? r : -r) - rel0.y) * 16.0 / dir.z);
	return max(t, 0.0);
}

struct WfMistTiles {
	bool  on;
	float time;
	ivec2 nearCentre;
	ivec2 farCentre;
};

WfMistTiles wfMistTiles() {
	WfMistTiles t;
	t.on = false;
	t.time = 0.0;
	t.nearCentre = cameraPositionInt.xz >> 4;
	t.farCentre = cameraPositionInt.xz >> 10;
	int mode = voidMode();
	if (mode != VOID_MODE_NONE && voidTileHasData(VOID_TILE_F0) && voidTileHasData(VOID_TILE_F1)) {
		t.farCentre = voidCentre(VOID_TILE_F0, t.farCentre);
		// the near mist's band: its own centre while the near tiles hold data (else the camera's cell)
		if (voidTileHasData(VOID_TILE_N0)) t.nearCentre = voidCentre(VOID_TILE_N0, t.nearCentre);
		t.time = MIST_FORCE > 0 ? float(MIST_FORCE) / 100.0 : voidMistTime(mode);
		t.on = t.time > 0.004;
	}
	return t;
}

// The far slab at an eye-relative horizontal point (top camera-relative, as MistPoint).
MistPoint wfFarMistAt(vec2 eyeXZ, WfMistTiles t) {
	MistPoint m;
	m.top = -1e4;
	m.density = 0.0;
	m.pool = 0.0;
	m.rise = 0.0;
	float camY = mistCameraY();
	ivec2 camF = cameraPositionInt.xz >> 10;
	vec2 cells = (vec2(cameraPositionInt.xz & 1023) + cameraPositionFract.xz + eyeXZ) / 1024.0 - 0.5;
	ivec2 c0 = ivec2(floor(cells));
	vec2 f = cells - vec2(c0);
	float wp = 0.0, sg = 0.0, sp = 0.0, sl = 0.0, sw = 0.0, se = 0.0;
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i & 1, i >> 1);
		ivec2 c = camF + c0 + o;
		vec4 f0 = voidFetch(VOID_TILE_F0, c);
		vec4 f1 = voidFetch(VOID_TILE_F1, c);
		if (!voidCellValid(c, t.farCentre) || (voidFlags(f1.a) & 1) == 0) {
			continue;
		}
		float b = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
		wp += b;
		sg += b * voidHeight(f0.r, f0.g);
		sp += b * f1.r;
		sl += b * f0.b;
		sw += b * ((voidFlags(f1.a) & 8) != 0 ? 1.0 : f0.a);
		se += b * voidCellWeight(c, t.farCentre);
	}
	if (wp < 1e-6) {
		return m;
	}
	float ground = sg / wp;
	float mist = clamp(sp / wp, 0.0, 1.0) * t.time;
	float pool = clamp(max(smoothstep(MIST_LOW_FROM, MIST_LOW_FULL, sl / wp), MIST_WATER_POOL * sw / wp), 0.0, 1.0);
	float top = ground + 1.0 + (MIST_LIFT + MIST_GAIN * mist) * pool;
	top = min(top, ground + MIST_CAP_LIFT + MIST_CAP_GAIN * mist);
	float floorCut = smoothstep(MIST_FLOOR, 2.0 * MIST_FLOOR, mist * pool);
	m.top = mix(ground + 1.0, top, floorCut) - camY;
	m.pool = pool;
	m.density = mist * pool * floorCut * (se / wp) * clamp(wp * 2.0, 0.0, 1.0)
		* (1.0 - wfNearEdge(eyeXZ, t.nearCentre));
	return m;
}

// The far mist on a land pixel at distance tS along unit `dir`: x the optical depth (density x blocks, as MistRay),
// y where the ray enters it. Zero depth where there is none.
vec2 wfFarMistRay(vec3 dir, float tS, WfMistTiles t) {
	float tA = wfNearSquareExit(dir, t.nearCentre, 112.0);
	if (tS <= tA) {
		return vec2(0.0, 1e9);
	}
	float tB = min(wfNearSquareExit(dir, t.nearCentre, 127.0), tS);
	MistRay r;
	r.depth = 0.0;
	r.enter = 1e9;
	r.exit = 0.0;
	MistPoint a = wfFarMistAt(dir.xz * tA, t);
	MistPoint b = wfFarMistAt(dir.xz * tB, t);
	mistSegment(r, tA, tB, a, b, dir.y);
	float t1 = tB;
	a = b;
#if MIST_SAMPLES >= 3
	if (tS > tB * 1.5) {
		float tM = sqrt(tB * tS);
		MistPoint mid = wfFarMistAt(dir.xz * tM, t);
		mistSegment(r, t1, tM, a, mid, dir.y);
		a = mid;
		t1 = tM;
	}
#endif
	MistPoint e = wfFarMistAt(dir.xz * tS, t);
	mistSegment(r, t1, tS, a, e, dir.y);
	// the surface's own slab, met as a level slab (the straight pieces cut across a valley narrower than a sample step)
	float below = e.top - dir.y * tS;
	if (below > 0.0 && e.density > 0.0) {
		float inside = dir.y < -1e-4 ? below / -dir.y : tS;
		r.depth = max(r.depth, min(inside, tS - t1) * e.density);
		r.enter = min(r.enter, tS - min(inside, tS - t1));
	}
	return vec2(r.depth, r.enter);
}

#endif
