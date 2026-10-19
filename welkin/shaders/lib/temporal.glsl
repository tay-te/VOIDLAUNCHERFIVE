// VOID Welkin, HDR stage C (temporal, 2026-10-19): temporal accumulation for the half-resolution lighting terms in the
// air (the haze volume's walk and the shafts' term, colortex13) and the ambient occlusion (colortex12). Textures and
// the image itself never go through it (no TAA). Written from scratch for Welkin; no other pack's code.
//
// With TEMPORAL_AIR the noise of those walks moves along its R2 sequence frame by frame (airFrame, lib/noise.glsl), and
// each resolve blends its result with the last frame's, reprojected with the previous frame's matrices and camera:
//   - rejected off the screen, where the depth there last frame does not match the point's (disocclusion: a relative
//     depth test, both sky or both land), and on a camera jump (a teleport or a cut: more than TEMPORAL_JUMP blocks);
//   - clamped to the current neighbourhood's range (no smear of a beam or a shade that moved);
//   - TEMPORAL_AIR_BLEND of history where valid (TEMPORAL_AIR_STRESS: 0.995, the ghosting test).
// The history lives in colortex11 (RGBA32F, never cleared; lib/buffers.glsl), packed as half pairs at the end of each
// frame by composite2: r (reach, passes) and g (occlusion, 0) as packed pairs (tPack), b the air's three terms
// (tPackAir; deferred3 carries b through its pass), a the view depth (-z).

#ifndef WELKIN_TEMPORAL
#define WELKIN_TEMPORAL

#include "/lib/settings.glsl"

#if TEMPORAL_AIR_STRESS == 1
#define TEMPORAL_AIR_BLEND 0.995
#else
#define TEMPORAL_AIR_BLEND 0.88
#endif
#define TEMPORAL_JUMP 6.0

#ifndef WU_cameraPosition
#define WU_cameraPosition
uniform vec3 cameraPosition;
#endif
#ifndef WU_previousCameraPosition
#define WU_previousCameraPosition
uniform vec3 previousCameraPosition;
#endif
#ifndef WU_gbufferPreviousModelView
#define WU_gbufferPreviousModelView
uniform mat4 gbufferPreviousModelView;
#endif
#ifndef WU_gbufferPreviousProjection
#define WU_gbufferPreviousProjection
uniform mat4 gbufferPreviousProjection;
#endif

// Two values >= 0 in one 32-bit float word (GLSL 330 has no half packing): each as v / (1 + v), the low 16 bits a
// 16-bit fixed point, the high ones offset to 128..32128 so the word is always a normal float (never a denormal a GPU
// could flush, never an infinity or NaN).
float tPack(vec2 v) {
	vec2 e = max(v, 0.0) / (1.0 + max(v, 0.0));
	uint lo = uint(e.x * 65535.0 + 0.5);
	uint hi = 128u + uint(e.y * 32000.0 + 0.5);
	return uintBitsToFloat(lo | (hi << 16u));
}

vec2 tUnpack(float f) {
	uint b = floatBitsToUint(f);
	vec2 e = vec2(float(b & 0xFFFFu) / 65535.0, float(max(b >> 16u, 128u) - 128u) / 32000.0);
	e = min(e, vec2(0.99998));
	return e / (1.0 - e);
}

// The air's three terms (the haze volume's walk: r the shafts' term, g the sunlit haze, b all the haze along the ray) in
// one word, so deferred3 can carry them through its own pass in colortex11 b: b as b / (16 + b) in 12 bits, r as
// r / (1 + r) in 11 bits, the sunlit share g / b in the exponent's 8 bits (kept within 1..254: always a normal float).
float tPackAir(vec3 h) {
	h = max(h, 0.0);
	uint a = uint(h.b / (16.0 + h.b) * 4095.0 + 0.5);
	uint r = uint(h.r / (1.0 + h.r) * 2047.0 + 0.5);
	uint c = 1u + uint(clamp(h.g / max(h.b, 1e-6), 0.0, 1.0) * 253.0 + 0.5);
	return uintBitsToFloat(a | (r << 12u) | (c << 23u));
}

vec3 tUnpackAir(float f) {
	uint w = floatBitsToUint(f);
	float eb = min(float(w & 0xFFFu) / 4095.0, 0.9998);
	float er = min(float((w >> 12u) & 0x7FFu) / 2047.0, 0.9998);
	float share = float(max((w >> 23u) & 0xFFu, 1u) - 1u) / 253.0;
	float b = 16.0 * eb / (1.0 - eb);
	return vec3(er / (1.0 - er), share * b, b);
}

// Where camera-relative world point `playerPos` stood on last frame's screen (uv) and its view depth then (z). False off
// the screen or after a camera jump. `sky`: a direction at the far plane (only the turn counts).
bool tReproject(vec3 playerPos, bool sky, out vec2 puv, out float pz) {
	vec3 jump = cameraPosition - previousCameraPosition;
	if (dot(jump, jump) > TEMPORAL_JUMP * TEMPORAL_JUMP) {
		puv = vec2(-1.0);
		pz = 0.0;
		return false;
	}
	vec3 prev = sky ? playerPos : playerPos + jump;
	vec4 vp = gbufferPreviousModelView * vec4(prev, 1.0);
	vec4 c = gbufferPreviousProjection * vp;
	puv = c.xy / max(c.w, 1e-5) * 0.5 + 0.5;
	pz = -vp.z;
	return c.w > 0.0 && all(greaterThan(puv, vec2(0.0))) && all(lessThan(puv, vec2(1.0)));
}

// Whether history depth hz (colortex11 a) matches the point's depth then (pz): both sky (> 5e3), or within a relative
// tolerance.
bool tDepthOk(float hz, float pz, bool sky) {
	if (sky || hz > 5e3) {
		return sky && hz > 5e3;
	}
	return abs(hz - pz) < 0.05 + 0.03 * pz;
}

#endif
