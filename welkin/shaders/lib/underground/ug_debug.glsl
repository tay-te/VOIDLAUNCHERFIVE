// VOID Welkin, underground (M5, P6): the debug overlay (UG_DEBUG 1). Entry: ugDebug(), last thing in composite2.
//
//   bottom-left, from x = 320 (right of DEBUG_VIEW 6's N0 square): the U tile round its centre, 2 pixels a cell,
//   north up. Grey: openness (space / 64); tinted by water kind (river green, sea blue, lake cyan, lava orange);
//   yellow: no data yet; dots: teal glowworms, blue drip, white great river; red: the camera's cell; dark red: outside
//   the reach. Above it, bars: mode (red off, orange plain, yellow eye only, green tile), amount, openness,
//   glowworms, bats, drip, great-river nearness, then a swatch of the realm's fog colour and of the water kind.

#ifndef WELKIN_UG_DEBUG
#define WELKIN_UG_DEBUG

#include "/lib/underground/ug_common.glsl"

#define UG_DEBUG_X 320

vec3 ugDebug(UgState s, ivec2 p, vec3 scene) {
	ivec2 o = p - ivec2(UG_DEBUG_X, 0);
	if (o.x < 0 || o.x >= 256) {
		return scene;
	}
	if (o.y >= 264 && o.y < 280) {
		int bar = o.x / 28;
		float x = float(o.x - bar * 28) / 26.0;
		if (o.x - bar * 28 >= 26) {
			return vec3(0.0);
		}
		if (bar == 0) {
			return mat4x3(vec3(0.9, 0.1, 0.1), vec3(1.0, 0.5, 0.1), vec3(1.0, 0.9, 0.1), vec3(0.1, 0.9, 0.2))
				* ugHot(s.mode, 0);
		}
		if (bar == 7) {
			return s.fog * 5.0;
		}
		if (bar == 8) {
			vec4 w = ugKindHot(s.kind);
			w.w = 0.0;
			return ugWaterDeep(w, vec3(0.030, 0.084, 0.102)) * 4.0;
		}
		float v = bar == 1 ? s.amount : (bar == 2 ? s.open : dot(s.cave, ugHot(bar, 3)));
		return x < v ? vec3(0.9) : vec3(0.15);
	}
	if (o.y >= 256) {
		return scene;
	}
	if (s.mode != UG_MODE_TILE) {
		return vec3(0.08);
	}
	// cell offset from the centre; screen y up is north (-z)
	ivec2 off = ivec2(o.x / 2 - 64, 63 - o.y / 2);
	ivec2 cell = (s.centre.xz >> UG_CELL_SHIFT) + off;
	ivec2 camCell = cameraPositionInt.xz >> UG_CELL_SHIFT;
	if (cell == camCell) {
		return vec3(1.0, 0.0, 0.0);
	}
	if (max(abs(off.x), abs(off.y)) >= UG_REACH) {
		return vec3(0.25, 0.0, 0.0);
	}
	ivec4 b = ivec4(voidFetch(VOID_TILE_U, cell) * 255.0 + 0.5);
	int kind = b.a >> 4;
	int flags = b.a & 15;
	vec3 c = vec3(clamp(float(b.r) * 0.5 / 64.0, 0.0, 1.0));
	vec3 tint = mat4x3(vec3(0.4, 1.0, 0.6), vec3(0.4, 0.5, 1.0), vec3(0.4, 0.9, 1.0), vec3(1.0, 0.5, 0.1))
		* ugKindHot(kind);
	c = kind != 0 ? mix(c, tint, 0.6) : c;
	if ((flags & UG_F_PRESENT) == 0) {
		c = vec3(1.0, 0.95, 0.5); // no data yet
	}
	ivec2 sub = o & 1;
	if ((flags & UG_F_GLOW) != 0 && sub == ivec2(0, 0)) {
		c = UG_GLOW_SPECK;
	}
	if ((flags & UG_F_DRIP) != 0 && sub == ivec2(1, 0)) {
		c = vec3(0.2, 0.4, 1.0);
	}
	if ((flags & UG_F_GREAT) != 0 && sub == ivec2(0, 1)) {
		c = vec3(1.0);
	}
	return c;
}

#endif
