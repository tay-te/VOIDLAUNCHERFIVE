// VOID Welkin: the lighting resolve (shared; workstream A owns it, others include it and never edit it, only ask).
//
// A noisy lighting term (lib/noise.glsl) is written to its own raw buffer, its view depth (blocks, -z) in .a, and is
// smoothed here before the light pass reads it. Only lighting terms come through here, never colour: albedo, texture
// and the pixel art stay pixel-crisp.
//
// 1. The spatial resolve, the floor (resolveSpatial4x4): a 4x4 gather weighted by how far each neighbour stands off
//    the centre's own plane and by how alike the normals are, so an edge never bleeds onto the land behind it or onto
//    the next face of a block. The window (-2..1) always holds one 4x4 noise tile's sixteen values once each, so even
//    a single frame, with no history, is smooth and stable: this is the guarantee against grain.
// 2. Temporal accumulation, the polish (optional, TEMPORAL_LIGHT): the last frame's resolved value, reprojected
//    (reproject), clamped to the range of the current frame's resolved neighbourhood (clampHistory; Karis 2014,
//    "High Quality Temporal Supersampling"), blended by historyWeight: about 0.85 on still land, 0.5 on what sways
//    (leaves and plants) and wherever the history had to be clamped, so a moving shadow never smears.
//
// Signatures (sampler parameters: pass the buffer the term lives in):
//   vec3  dnViewPos(vec2 uv, float z)          the view position of screen uv at view depth z (blocks)
//   float dnPlaneWeight(vec3 vc, vec3 vn, vec3 vs, float z)   how well vs lies on the plane through vc with view
//                                                             normal vn (1 on it, falling over a few hundredths of
//                                                             a block near the eye, more far off)
//   float dnNormalWeight(vec3 n, vec3 ns)      how alike two normals are (world or view, both the same space)
//   vec4  resolveSpatial4x4(sampler2D raw, ivec2 q, int scale, vec3 vc, vec3 vn, vec3 n, sampler2D normals)
//         the weighted mean of raw.rgb over the 4x4 window round raw texel q (rgb), and the weight sum (a; 0 when
//         nothing matched: then use the centre's own raw value). scale: screen pixels per raw texel (1 full
//         resolution, 2 half); vc, vn: the centre's view position and view normal; n: its world normal, matched
//         against normals (colortex1's octahedral world normal, read at full resolution); n = vec3(0) skips the
//         normal weight
//   vec4  resolveSpatial4x4Range(...)          the same, also giving the window's min and max of rgb (for the clamp)
//   vec2  reproject(vec3 playerPos)            where a camera-relative point (as gbufferModelViewInverse gives it)
//                                              stood on screen last frame, 0..1 (outside: no history)
//   vec4  clampHistory(vec4 history, vec4 lo, vec4 hi)
//   float historyWeight(int material, bool clamped)  the share of the history kept (plumbing's material class)

#ifndef WELKIN_DENOISE
#define WELKIN_DENOISE

#include "/lib/common.glsl"

#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

vec3 dnViewPos(vec2 uv, float z) {
	vec4 r = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
	vec3 d = r.xyz / r.w;
	return d * (z / max(-d.z, 1e-6));
}

float dnPlaneWeight(vec3 vc, vec3 vn, vec3 vs, float z) {
	float tol = 0.03 + 0.004 * z;
	float d = dot(vn, vs - vc) / tol;
	return exp(-d * d);
}

float dnNormalWeight(vec3 n, vec3 ns) {
	float c = clamp(dot(n, ns), 0.0, 1.0);
	float c2 = c * c;
	return c2 * c2 * c2 * c2;
}

vec4 dnResolve(sampler2D raw, ivec2 q, int scale, vec3 vc, vec3 vn, vec3 n, sampler2D normals, out vec3 lo,
		out vec3 hi) {
	ivec2 top = textureSize(raw, 0) - 1;
	ivec2 fullTop = textureSize(normals, 0) - 1;
	vec2 px = float(scale) / vec2(viewWidth, viewHeight);
	float z = -vc.z;
	bool useN = dot(n, n) > 0.5;
	vec3 sum = vec3(0.0);
	float wsum = 0.0;
	lo = vec3(1e9);
	hi = vec3(-1e9);
	for (int y = -2; y <= 1; y++) {
		for (int x = -2; x <= 1; x++) {
			ivec2 t = clamp(q + ivec2(x, y), ivec2(0), top);
			vec4 s = texelFetch(raw, t, 0);
			vec3 vs = dnViewPos((vec2(t) + 0.5) * px, s.a);
			float w = dnPlaneWeight(vc, vn, vs, z);
			if (useN) {
				ivec2 f = clamp(t * scale + scale / 2, ivec2(0), fullTop);
				w *= dnNormalWeight(n, octDecode(texelFetch(normals, f, 0).xy));
			}
			sum += s.rgb * w;
			wsum += w;
			if (w > 0.25) {
				lo = min(lo, s.rgb);
				hi = max(hi, s.rgb);
			}
		}
	}
	return vec4(sum / max(wsum, 1e-5), wsum);
}

vec4 resolveSpatial4x4(sampler2D raw, ivec2 q, int scale, vec3 vc, vec3 vn, vec3 n, sampler2D normals) {
	vec3 lo, hi;
	return dnResolve(raw, q, scale, vc, vn, n, normals, lo, hi);
}

vec4 resolveSpatial4x4Range(sampler2D raw, ivec2 q, int scale, vec3 vc, vec3 vn, vec3 n, sampler2D normals,
		out vec3 lo, out vec3 hi) {
	return dnResolve(raw, q, scale, vc, vn, n, normals, lo, hi);
}

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

vec2 reproject(vec3 playerPos) {
	vec3 prev = playerPos + (cameraPosition - previousCameraPosition);
	vec4 c = gbufferPreviousProjection * (gbufferPreviousModelView * vec4(prev, 1.0));
	return c.xy / max(c.w, 1e-5) * 0.5 + 0.5;
}

vec4 clampHistory(vec4 history, vec4 lo, vec4 hi) {
	return clamp(history, lo, hi);
}

float historyWeight(int material, bool clamped) {
	bool sways = matIsLeaves(material) || matHas(material, MATF_SSS);
	return (sways || clamped) ? 0.5 : 0.85;
}

#endif
