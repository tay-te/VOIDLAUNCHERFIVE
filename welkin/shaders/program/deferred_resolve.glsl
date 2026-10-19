// VOID Welkin: deferred_resolve, run as Iris's deferred3 (tools/wrappers.sh maps it). Smooths the raw, noise-sampled
// lighting terms before the light pass (deferred4) reads them (lib/denoise.glsl):
//   the shadow: colortex10 (deferred_shadow: r the sun's reach, g what passes the leaves, a the view depth) into
//   colortex11 (r the reach, g what passes, a the view depth, kept for the next frame's reprojection).
// Spatial, always: a 4x4 gather weighted by each neighbour's distance off the centre's plane and by its normal, which
// holds every value of the 4x4 noise tile once: a single frame is smooth and stable. Temporal, with TEMPORAL_LIGHT:
// the last frame's result, reprojected, clamped to the current window's range and blended by historyWeight (less on
// what sways and where the clamp acted), reset where the depth does not match (disocclusion).
// Only the lighting terms come through here; colour and texture never do.
// The occlusion is resolved in its own pass at half resolution (program/deferred_aoresolve.glsl, deferred1; the balance
// step, 2026-10-13).

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/denoise.glsl"

in vec2 texcoord;

#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex10
#define WU_colortex10
uniform sampler2D colortex10;
#endif
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif
#ifdef TEMPORAL_LIGHT
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif
#ifndef WU_colortex11
#define WU_colortex11
uniform sampler2D colortex11; // last frame's (lib/temporal.glsl: packed history at the end of each frame)
#endif
#include "/lib/temporal.glsl"
#ifdef TEMPORAL_LIGHT
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
#endif

#if DEBUG_VIEW == 17
#define RESOLVE_DEBUG
#endif

/* RENDERTARGETS: 11 */
layout(location = 0) out vec4 outRes;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 raw = texelFetch(colortex10, p, 0);
	// (HDR stage C) the air's packed history passes through here untouched for composite2
	float airHist = texelFetch(colortex11, p, 0).b;
	if (raw.a > 5e3) {
		outRes = vec4(raw.rg, airHist, 1e4);
		return;
	}
#if SHADOW_FILTER != 0 && !defined RESOLVE_DEBUG
	// The balance step (2026-10-13): where the whole 4x4 window holds one value (open sun and deep shadow, the lookup's
	// noise-free branches: most of the land), the weighted resolve, and the history's clamp with it, would hand that
	// value back unchanged, so it is skipped: sixteen plain reads instead of the weights, normals and positions.
	{
		ivec2 top = textureSize(colortex10, 0) - 1;
		vec2 mn = raw.rg;
		vec2 mx = raw.rg;
		for (int y = -2; y <= 1; y++) {
			for (int x = -2; x <= 1; x++) {
				vec2 v = texelFetch(colortex10, clamp(p + ivec2(x, y), ivec2(0), top), 0).rg;
				mn = min(mn, v);
				mx = max(mx, v);
			}
		}
		if (all(lessThan(mx - mn, vec2(1e-4)))) {
			outRes = vec4(raw.rg, airHist, raw.a);
			return;
		}
	}
#endif
	vec2 size = vec2(viewWidth, viewHeight);
	vec3 vc = dnViewPos((vec2(p) + 0.5) / size, raw.a);
	vec3 n = octDecode(texelFetch(colortex1, p, 0).xy);
#if SHADOW_FILTER == 0
	// the fixed grid is noise-free: nothing to resolve
	outRes = vec4(raw.rg, airHist, raw.a);
	return;
#endif
	vec3 vn = mat3(gbufferModelView) * n;
	vec3 lo, hi;
	vec4 r = resolveSpatial4x4Range(colortex10, p, 1, vc, vn, n, colortex1, lo, hi);
	if (r.a < 2.5) {
		// the plane test left this pixel nearly alone (a leaf's mip edge, a blade seen edge-on): its own noisy value would
		// show as a speck, so it takes its neighbours at a looser depth test (the grain check's forest specks)
		vec3 lo2, hi2;
		vec4 r2 = resolveSpatial4x4Loose(colortex10, p, 1, vc, vn, n, colortex1, 12.0, lo2, hi2);
		if (r2.a > r.a) {
			r = r2;
			lo = lo2;
			hi = hi2;
		}
	}
	vec2 res = r.a > 1e-3 ? r.rg : raw.rg;
	float dbg = 0.0;
#ifdef TEMPORAL_LIGHT
	if (r.a > 1e-3) {
		vec3 playerPos = (gbufferModelViewInverse * vec4(vc, 1.0)).xyz;
		vec2 puv = reproject(playerPos);
		if (all(greaterThan(puv, vec2(0.0))) && all(lessThan(puv, vec2(1.0)))) {
			vec4 h = texelFetch(colortex11, ivec2(puv * size), 0);
			h.rg = tUnpack(h.r); // (HDR stage C: packed at the end of the frame by composite2)
			// the depth the point stood at last frame, against the history's own: a mismatch is a disocclusion
			vec3 prev = playerPos + (cameraPosition - previousCameraPosition);
			float pz = -(gbufferPreviousModelView * vec4(prev, 1.0)).z;
			if (abs(h.a - pz) < 0.05 + 0.02 * pz) {
				vec2 hc = clamp(h.rg, lo.rg, hi.rg);
				bool clamped = any(greaterThan(abs(hc - h.rg), vec2(0.02)));
				int material = int(texelFetch(colortex2, p, 0).y * 255.0 + 0.5);
				float w = historyWeight(material, clamped);
				res = mix(res, hc, w);
				dbg = clamped ? -w : w;
			}
		}
	}
#endif
#ifdef RESOLVE_DEBUG
	// DEBUG_VIEW 17: the history weight (b; negative where the clamp acted), carried to the light pass in b
	outRes = vec4(res, dbg, raw.a);
#else
	outRes = vec4(res, airHist, raw.a);
#endif
}

#endif
