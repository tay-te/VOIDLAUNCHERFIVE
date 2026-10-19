// VOID Welkin: deferred_aoresolve, run as Iris's deferred1 (tools/wrappers.sh maps it), at half resolution
// (colortex12 is half size: size.buffer.colortex12 in shaders.properties, so Iris draws this pass at that size).
// Resolves the ambient occlusion's raw term (deferred_ao: colortex9, half resolution) with lib/denoise.glsl's 4x4
// resolve, once per half-resolution texel, into colortex12 (x the visibility, yz the bent normal's tangent parts, a the
// view depth); the light pass (lib/ao.glsl lightAoFetch) takes it up to full resolution with a 2x2 depth-aware
// upsample. The balance step (2026-10-13): the same window and weights as the full-resolution resolve it replaces, at
// a quarter of the pixels (the occlusion is smooth by nature; its edges are kept by the upsample's plane test).
// Only the lighting term comes through here; colour and texture never do.

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

#define WELKIN_AORESOLVE_PASS
#include "/lib/ao.glsl"

in vec2 texcoord;

#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif

#ifdef TEMPORAL_AIR
#include "/lib/temporal.glsl"
#ifndef WU_colortex11
#define WU_colortex11
uniform sampler2D colortex11; // last frame's packed history (lib/temporal.glsl)
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
#endif

/* RENDERTARGETS: 12 */
layout(location = 0) out vec4 outAo;

void main() {
	outAo = vec4(1.0, 0.0, 0.0, 6e4);
#ifdef AMBIENT_OCCLUSION
	ivec2 q = ivec2(gl_FragCoord.xy); // the half-resolution texel
	vec4 raw = texelFetch(colortex9, q, 0);
	outAo = vec4(1.0, 0.0, 0.0, raw.a);
	if (aoCover(raw.a) <= 0.0) {
		return;
	}
	// the full-resolution pixel the raw term was worked out for (deferred_ao: the texel's centre)
	ivec2 full = clamp(q * 2 + 1, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
	int m = int(texelFetch(colortex2, full, 0).y * 255.0 + 0.5);
	vec3 n = octDecode(texelFetch(colortex1, full, 0).xy);
	vec3 vc = dnViewPos((vec2(q) + 0.5) * 2.0 / vec2(viewWidth, viewHeight), raw.a);
	outAo = vec4(aoResolveHalf(q, raw, vc, n, m), raw.a);
#ifdef TEMPORAL_AIR
	{
		// (HDR stage C) the visibility blended with last frame's, reprojected, on the same surface, clamped to the raw
		// term's 3x3 range round the texel
		vec2 puv;
		float pz;
		vec3 playerPos = (gbufferModelViewInverse * vec4(vc, 1.0)).xyz;
		if (tReproject(playerPos, false, puv, pz)) {
			vec4 h = texelFetch(colortex11, ivec2(puv * vec2(viewWidth, viewHeight)), 0);
			if (tDepthOk(h.a, pz, false)) {
				ivec2 top = textureSize(colortex9, 0) - 1;
				float lo = 1.0, hi = 0.0;
				for (int y = -1; y <= 1; y++) {
					for (int x = -1; x <= 1; x++) {
						float v = texelFetch(colortex9, clamp(q + ivec2(x, y), ivec2(0), top), 0).x;
						lo = min(lo, v);
						hi = max(hi, v);
					}
				}
				lo = min(lo, outAo.x);
				hi = max(hi, outAo.x);
				outAo.x = mix(outAo.x, clamp(tUnpack(h.g).x, lo, hi), TEMPORAL_AIR_BLEND);
			}
		}
	}
#endif
#endif
}

#endif
