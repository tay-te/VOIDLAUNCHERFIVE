// VOID Welkin: composite3, the depth prototype BLOOM's source (the depth A/B sheet, 2026-10-16). Half resolution
// (colortex14 is half size, so Iris draws the pass at that size): each texel takes the 2x2 full-resolution pixels under
// it, keeps the bright share of each (lib/depthfx.glsl wdBloomSource: the display value given its headroom back, a
// glowing texel its own light, then a soft threshold), and averages them weighted by 1 / (1 + brightness), so a
// single blazing pixel cannot flicker into a blotch. composite4 gathers the halo from this buffer's mipmaps; final
// lays it on. Off (DEPTH_BLOOM and DEPTH_ALL off) the program is disabled in shaders.properties.

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

#include "/lib/depthfx.glsl"

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 14 */
layout(location = 0) out vec4 outBloom;

void main() {
	ivec2 q = ivec2(gl_FragCoord.xy);
	ivec2 hi = ivec2(viewWidth, viewHeight) - 1;
	vec3 sum = vec3(0.0);
	float wsum = 0.0;
#if WD_BLOOM
	for (int k = 0; k < 4; k++) {
		ivec2 p = min(q * 2 + ivec2(k & 1, k >> 1), hi);
		vec3 c = texelFetch(colortex0, p, 0).rgb;
		vec4 f = texelFetch(colortex2, p, 0);
		float emit = f.x > 0.5 ? unpackEmission(f.z) : 0.0;
		vec3 b = wdBloomSource(c, emit);
		float w = 1.0 / (1.0 + wdLuma(b));
		sum += b * w;
		wsum += w;
	}
	sum /= wsum;
#endif
	outBloom = vec4(sum, 1.0);
}

#endif
