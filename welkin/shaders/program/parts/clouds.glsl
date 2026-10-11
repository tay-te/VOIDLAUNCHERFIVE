// VOID Welkin: the voxel clouds (M3). The first composite pass: after the translucents (depthtex0 holds water and
// glass, so a lake below a deck is hidden by the deck and a pane in front of one shows it), before the atmosphere.
//
// Per pixel: the land's distance (the nearer of the chunks and the LOD, lib/depth.glsl), the view ray, then the
// bounded voxel DDA (lib/clouds/march.glsl). A hit is lit by the sky table's light (lib/clouds/shade.glsl) and written
// over the scene; its distance goes to colortex6 for the passes after. Overworld only (world0 wrappers). Debug views 10 (the
// cost map) and 11 (the rings) are drawn here (7 is M1's tiles, 8 and 9 plumbing's); the atmosphere pass's views overwrite 1..9.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
#include "/lib/clouds/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/depth.glsl"
#include "/lib/clouds/shade.glsl"

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 0,6 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outCloud;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
	float km = WC_NO_CLOUD_KM;
	vec3 steps = vec3(0.0); // columns and jumps the ray took, of them gap columns, jumps (the gltest cost map)
#ifdef CLOUDS
	Surface s = surfaceAt(p, texcoord);
	float tScene = s.sky ? 1e30 : length(s.view);
	vec3 rd = normalize(mat3(gbufferModelViewInverse) * viewFromDepth(texcoord, 1.0, gbufferProjectionInverse));
	vec3 ro = vec3(0.0); // deferred's eyePos convention: camera-relative, no view-bob translation
	float pixAngle = 2.0 / (gbufferProjection[1][1] * viewHeight);

	CloudEnv e = welkinCloudEnv();
	CloudHit h = welkinCloudMarch(ro, rd, tScene, pixAngle, e);
	steps = vec3(h.steps, h.gapCols, h.jumps);
	if (h.hit) {
		color.rgb = welkinCloudColor(h, ro, rd, e, pixAngle);
		km = h.t / 1000.0;
	}
	if (h.inside) {
		color.rgb = welkinCloudVeil(color.rgb, rd, e);
	}
#if DEBUG_VIEW == 10
	// the cost map: columns and jumps per pixel, blue few .. red at the cap (WC_STEPS)
	float k = float(h.steps) / float(WC_STEPS);
	color.rgb = h.steps == 0 ? color.rgb * 0.3 : mix(mix(vec3(0.0, 0.2, 1.0), vec3(0.0, 1.0, 0.2), clamp(k * 2.0, 0.0, 1.0)),
		vec3(1.0, 0.0, 0.0), clamp(k * 2.0 - 1.0, 0.0, 1.0));
#elif DEBUG_VIEW == 11
	// the rings: each ring of cells its own colour, the sheets past the DDA white
	if (h.hit) {
		color.rgb = h.sheet ? vec3(1.0) : vec3(fract(float(h.level) * 0.37), fract(float(h.level) * 0.61 + 0.3), fract(float(h.level) * 0.83 + 0.6));
	}
#endif
#endif
	outColor = color;
	outCloud = vec4(km, steps); // colortex6 is R16F: only km is kept
}

#endif
