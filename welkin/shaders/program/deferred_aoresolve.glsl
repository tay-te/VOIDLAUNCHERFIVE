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
#endif
}

#endif
