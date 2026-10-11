// VOID Welkin far weather: debug views (WF_DEBUG).
//  1  two 256 x 256 panels in the top-left corner, one pixel per cell, north up, the camera's cell in the middle (white):
//     W1 in the clouds' frame (grey cover, blue rain, red storm) and F0/F1 (the valley floor in 32-block bands, mist
//     potential cyan, cells without data magenta). They must scroll as the camera flies, and W1 also drifts with the
//     fronts: rain in the panel off the camera's cell must match the curtains on screen in that direction.
//  2  the curtains' optical depth over a darkened scene: red to 1, yellow to 2, white to 3.
//  3  the far mist's optical depth (cyan) over a darkened scene, the near tiles' fade band (where the near mist hands
//     over) a dark ring on the land.
//  4  the far air's opacity on the land as grey (black inside the seam); sky pixels whose curtain hazed itself blue.

#ifndef WELKIN_WF_DEBUG
#define WELKIN_WF_DEBUG

vec3 wfDebug(vec3 scene, ivec2 px, bool sky, bool cloud, vec3 dir, float L, CloudEnv e, WfAir a, WfLayer mist, WfLayer rain) {
#if WF_DEBUG == 1
	ivec2 top = ivec2(px.x, int(viewHeight) - 1 - px.y);
	if (top.y < 256 && top.x < 528 && e.mode != VOID_MODE_NONE) {
		bool second = top.x >= 272;
		ivec2 q = ivec2(second ? top.x - 272 : top.x, top.y);
		if (q.x >= 256) return scene;
		ivec2 off = ivec2(q.x - 128, q.y - 128);     // x east, y south (screen down)
		if (off == ivec2(0)) return vec3(1.0);
		if (!second) {
			ivec2 c0;
			vec2 lo;
			wfW1Camera(e, c0, lo);
			vec4 w = wfW1Cell(c0 + off, e);
			return vec3(w.g * 0.5) + vec3(0.0, 0.2, 1.0) * w.b * 0.6 + vec3(1.0, 0.0, 0.0) * w.a;
		}
		ivec2 cell = (cameraPositionInt.xz >> 10) + off;
		vec4 f0 = voidFetch(VOID_TILE_F0, cell);
		vec4 f1 = voidFetch(VOID_TILE_F1, cell);
		if ((voidFlags(f1.a) & 1) == 0) return vec3(0.8, 0.0, 0.8);
		float g = voidHeight(f0.r, f0.g);
		float band = mod(floor(g / 32.0), 2.0) * 0.15 + clamp((g - 40.0) / 300.0, 0.0, 0.6);
		return vec3(band) + vec3(0.0, 0.7, 0.7) * f1.r * 0.6;
	}
	return scene;
#elif WF_DEBUG == 2
	vec3 c = scene * 0.25;
	float o = rain.od;
	if (o > 0.0) {
		c = o < 1.0 ? vec3(o, 0.0, 0.0) : (o < 2.0 ? vec3(1.0, o - 1.0, 0.0) : vec3(1.0, 1.0, min(o - 2.0, 1.0)));
	}
	return c;
#elif WF_DEBUG == 3
	vec3 c = scene * 0.25;
	if (!sky && !cloud) {
		WfMistTiles mt = wfMistTiles();
		float edge = wfNearEdge(dir.xz * L, mt.nearCentre);
		if (edge > 0.0 && edge < 1.0) c *= 0.4;
	}
	return c + vec3(0.0, 0.8, 0.8) * clamp(mist.od * MIST_EXTINCTION, 0.0, 1.5);
#elif WF_DEBUG == 4
	if (sky || cloud) return rain.alpha > 0.0 && sky ? vec3(0.1, 0.3, 1.0) * rain.alpha : scene * 0.25;
	return vec3(a.on ? wfAirAlpha(a, L) : 0.0);
#else
	return scene;
#endif
}

#endif
