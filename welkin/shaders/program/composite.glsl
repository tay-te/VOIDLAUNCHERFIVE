// VOID Welkin: composite. The scene so far, with the atmosphere, or a debug view (DEBUG_VIEW, lib/settings.glsl).
//
// In order: water fog, so the far water's bed (LOD) and the near water's bed (chunks) read the same under the same
// depth of water (lib/water.glsl); then valley mist from VOID Gloam's near tiles (M1, lib/mist.glsl), over the chunks
// and the LOD's land alike. Still to come here: haze by country (M2), underground fog (M5), each an exact ray-slab
// intersection with a few tile samples, stepped and dithered on the world grid. The voxel clouds (M3) and the far
// weather (composite1, M4) follow it.

#define WELKIN_DEPTH_BEHIND
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

#include "/lib/water.glsl"
#ifdef VALLEY_MIST
#ifdef WORLD_OVERWORLD
#define WELKIN_VALLEY_MIST
#include "/lib/mist.glsl"
#endif
#endif

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif

#if DEBUG_VIEW != 0
#include "/lib/debug.glsl"
#endif
#if SKY_DEBUG == 1
#include "/lib/sky/debug.glsl"
#endif

#ifdef DEBUG_JOIN
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif

#ifndef WELKIN_GBUFFER_SAMPLERS
#define WELKIN_GBUFFER_SAMPLERS
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif

// Whether the chunks' land is at `q` (1), the LOD's (0), or neither (-1: the sky, or vanilla's clouds, marked in
// colortex2 by gbuffers_clouds).
int joinSide(ivec2 q) {
	q = clamp(q, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
	Surface s = surfaceAt(q, (vec2(q) + 0.5) / vec2(viewWidth, viewHeight));
	vec4 flags = texelFetch(colortex2, q, 0);
	bool cloud = flags.x < 0.5 && int(flags.y * 255.0 + 0.5) == MAT_CLOUD;
	return s.sky || cloud ? -1 : (s.chunk ? 1 : 0);
}

// A magenta line, two pixels wide, where the chunks' land meets the LOD's.
vec3 joinLine(ivec2 p, vec3 c) {
	int here = joinSide(p);
	if (here < 0) {
		return c;
	}
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i == 0 ? 2 : (i == 1 ? -2 : 0), i == 2 ? 2 : (i == 3 ? -2 : 0));
		int there = joinSide(p + o);
		if (there >= 0 && there != here) {
			return vec3(1.0, 0.0, 1.0);
		}
	}
	return c;
}
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
#if DEBUG_VIEW == 0
	color.rgb = waterFog(p, texcoord, color.rgb);
#ifdef WELKIN_VALLEY_MIST
	color.rgb = valleyMist(p, texcoord, color.rgb);
#endif
#else
	color.rgb = debugView(p, texcoord, color.rgb);
#endif
#ifdef DEBUG_JOIN
	color.rgb = joinLine(p, color.rgb);
#endif
#if SKY_DEBUG == 1
	color.rgb = skyDebugOverlay(p, color.rgb); // the sky table, 8x, bottom left
#endif
	outColor = color;
}

#endif
