// VOID Welkin: deferred. Lights the opaque world after the chunks and the LOD have drawn, before translucents.
//
// The surface is the chunks' (depthtex0) where they drew and the LOD's (dhDepthTex0) elsewhere, each through its own
// projection (lib/depth.glsl). Pixels marked in colortex2.x get the stepped light ramp (lib/light.glsl) with its band edges
// dithered on the world's 16-pixel grid; the rest lit themselves in their gbuffers program. Wet ground (M2,
// lib/haze/wet.glsl) darkens open ground before the light and lays its puddles on after it. Then, only while the haze
// is off, the far edge fades into the horizon colour (lib/fade.glsl); with the haze on, composite's haze draws the edge
// in the air's colour. The sky is left as gbuffers_skybasic drew it.

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

#include "/lib/depth.glsl"
#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/handlight.glsl"
#include "/lib/fade.glsl"
#include "/lib/void.glsl"

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

// ---- M2 haze: wet ground (deferred declares gbufferProjection and viewHeight itself)
#if defined WET_ENABLED && defined WORLD_OVERWORLD
#define HZ_HAVE_VIEW_UNIFORMS
#include "/lib/haze/wet.glsl"
#define WELKIN_WET
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
	Surface s = surfaceAt(p, texcoord);
	if (s.sky) {
		outColor = color;
		return;
	}
	vec3 eyePos = mat3(gbufferModelViewInverse) * s.view;
	float dist = length(s.view);
	vec4 flags = texelFetch(colortex2, p, 0);
	vec4 g = texelFetch(colortex1, p, 0);
	vec3 n = octDecode(g.xy);

	float d = 0.5;
#ifdef DITHER
	int level = gridLevel(blocksPerPixelAt(dist, gbufferProjection[1][1], viewHeight), 1.0);
	d = worldDither(eyePos, n, level);
#endif
#if DEBUG_VIEW != 4
	if (flags.x > 0.5) {
		vec3 albedo = color.rgb;
#ifdef WELKIN_WET
		// wet ground before the light: darkens open ground, picks the puddles
		HzState st = hzState();
		int material = int(flags.y * 255.0 + 0.5); // plumbing's material class (M_*)
		WelkinWet wet = welkinWetSurface(st, albedo, eyePos, n, g.w, hzWetClass(material));
#endif
		vec2 lm = g.zw;
		lm.x = welkinHandBlockLight(lm.x, dist); // a torch in hand lights the ground in the same steps
		color.rgb = welkinLight(albedo, n, lm, flags.z, d);
#ifdef WELKIN_WET
		// puddles mirror the sky (the sky table's horizon), glint toward the light, flick with drops
		color.rgb = welkinWetLight(st, color.rgb, wet, lightDirWorld(), skyDirectLight(), skyHorizon(false));
#if HAZE_DEBUG == 4
		color.rgb = welkinWetDebug(wet);
#endif
#endif
	}
#endif
#if defined FAR_FADE && !(defined HAZE_ENABLED && defined WORLD_OVERWORLD) && DEBUG_VIEW == 0
	color.rgb = mix(color.rgb, horizonColor(), farFade(length(eyePos.xz), d));
#endif
	outColor = color;
}

#endif
