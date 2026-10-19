// VOID Welkin (D7): the clouds' march result, handed from the march pass (program/deferred_cloudmarch.glsl, run as
// deferred6) to the clouds pass (program/parts/clouds.glsl, composite) through colortex10, RGBA16F, free once the light
// pass (deferred4) has read the shadow's raw terms. Every channel holds what a half float keeps exactly or nearly:
//
//   r, g  the hit's distance / 1024 as two halves, r rounded to a half (exact) and g the rest: 22 bits, so the hit lies
//         where the march put it to about a ten-millionth of its distance
//   b     whole numbers under 2048: 1 hit, 2 inside, 4 sheet, 8 x the face (0..5: -x, +x, -y, +y, -z, +z), 64 x the
//         tone, 256 x the ring
//   a     inside: how far the ray stays in the eye's own cloud (tOut) / 1024; else the hit cell's low five bits of x
//         and z (x + 32 z), from which the clouds pass recovers the cell exactly (DEBUG_VIEW 10: the columns taken)
//
// Splitting the pass: the march is a long loop of dependent map reads; compiled together with the cloud light (one
// very large shader) it ran at the occupancy of the whole, its reads' latency in the open. Alone it is lean.

#ifndef WELKIN_CLOUD_HITBUF
#define WELKIN_CLOUD_HITBUF

#include "/lib/clouds/march.glsl"

// x (>= 0) rounded to a half float's 11 significant bits (0 under the half's normal range)
float wcHalfRound(float x) {
	if (x < 6.2e-5) {
		return 0.0;
	}
	float ulp = exp2(floor(log2(x)) - 10.0);
	return floor(x / ulp + 0.5) * ulp;
}

vec4 wcHitEncode(CloudHit h) {
	float m = h.hit ? h.t / 1024.0 : 0.0;
	float hi = wcHalfRound(m);
	int face = 0;
	if (abs(h.n.x) > 0.5) {
		face = h.n.x > 0.0 ? 1 : 0;
	} else if (abs(h.n.y) > 0.5) {
		face = h.n.y > 0.0 ? 3 : 2;
	} else {
		face = h.n.z > 0.0 ? 5 : 4;
	}
	int bits = (h.hit ? 1 : 0) | (h.inside ? 2 : 0) | (h.sheet ? 4 : 0) | (face << 3) | (h.tone << 6) | (h.level << 8);
	float a = h.inside ? h.tOut / 1024.0 : float((h.cell.x & 31) + 32 * (h.cell.y & 31));
#if DEBUG_VIEW == 10
	a = float(h.steps);
#endif
	return vec4(hi, m - hi, float(bits), a);
}

// The hit back from its texel, for the ray rd (camera-relative from the eye). The steps and jump counts are not kept.
CloudHit wcHitDecode(vec4 v, vec3 rd, CloudEnv e) {
	CloudHit h = wcNoHit();
	int bits = int(v.b + 0.5);
	h.hit = (bits & 1) != 0;
	h.inside = (bits & 2) != 0;
	h.sheet = (bits & 4) != 0;
	int face = (bits >> 3) & 7;
	h.tone = (bits >> 6) & 3;
	h.level = (bits >> 8) & 7;
	h.n = face < 2 ? vec3(face == 1 ? 1.0 : -1.0, 0.0, 0.0)
		: (face < 4 ? vec3(0.0, face == 3 ? 1.0 : -1.0, 0.0) : vec3(0.0, 0.0, face == 5 ? 1.0 : -1.0));
	h.tOut = h.inside ? v.a * 1024.0 : 0.0;
	if (h.hit) {
		h.t = (v.r + v.g) * 1024.0;
		// the cell: the hit point's own (a hair inside a side face), set right by the stored low bits
		ivec2 c = wcCellOf((rd * h.t).xz - h.n.xz * 0.01, h.level, e);
		if (!h.inside) {
			int lo = int(v.a + 0.5);
			ivec2 want = ivec2(lo & 31, lo >> 5);
			c += ((want - (c & 31) + 16) & 31) - 16;
		}
		h.cell = c;
	}
#if DEBUG_VIEW == 10
	h.steps = int(v.a + 0.5);
#endif
	return h;
}

#endif
