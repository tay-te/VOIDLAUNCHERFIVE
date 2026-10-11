// VOID Welkin: deferred. Lights the opaque world after the chunks and the LOD have drawn, before translucents.
//
// The surface is the chunks' (depthtex0) where they drew and the LOD's (dhDepthTex0) elsewhere, each through its own
// projection (lib/depth.glsl). Pixels marked in colortex2.x get the light ramp (lib/light.glsl: smooth, in soft
// bands), the sun reaching them through the near shadow map (lib/shadow.glsl) and cut by the clouds' shadows (M3);
// the rest lit
// themselves in their gbuffers program. Wet ground (M2, lib/haze/wet.glsl) darkens open ground before the light and
// lays its puddles on after it. Then, only while the haze is off, the far edge fades into the horizon colour
// (lib/fade.glsl); with the haze on, composite2's haze draws the edge in the air's colour. The sky is left as
// gbuffers_skybasic drew it.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
// M3: Iris reads colortex6/7/8's formats from a program that always runs
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
#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/handlight.glsl"
#include "/lib/fade.glsl"
#include "/lib/shadow.glsl"
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

// ---- M3 clouds: cloud shadows
#if defined WORLD_OVERWORLD && defined CLOUDS && CLOUD_SHADOWS > 0
#include "/lib/clouds/shadow.glsl"
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

#if DEBUG_VIEW != 4
	if (flags.x > 0.5) {
		vec3 albedo = color.rgb;
		int material = int(flags.y * 255.0 + 0.5); // plumbing's material class (M_*)
		vec3 lightDir = lightDirWorld();
		float facing = sunFacingMat(n, lightDir, material);
		vec3 sunShare = vec3(1.0);
#if defined WORLD_OVERWORLD && defined CLOUDS && CLOUD_SHADOWS > 0
		// the decks' shadow, only where the light could reach
		if (g.w > 0.0 && facing > 0.0) {
			sunShare *= welkinCloudShadow(eyePos, welkinCloudEnv());
		}
#endif
		// the sun's reach: the near shadow map on the chunks inside its range, the sky-light gate beyond it and on the
		// LOD's land (which never casts or takes map shadows), fading from one to the other over the map's last 20 %
		float reach = sunOpen(g.w);
		if (!s.fromLod) {
			vec3 playerPos = (gbufferModelViewInverse * vec4(s.view, 1.0)).xyz;
			reach = welkinSunReach(playerPos, n, g.w, material, facing, sunShare);
		}
#ifdef WELKIN_WET
		// wet ground before the light: darkens open ground, picks the puddles
		HzState st = hzState();
		WelkinWet wet = welkinWetSurface(st, albedo, eyePos, n, g.w, hzWetClass(material));
#endif
		vec2 lm = g.zw;
		lm.x = welkinHandBlockLight(lm.x, dist); // a torch in hand lights the ground on the same ramp
		// the LOD's leaves: the crown's average self-shade the near shadow map gives near crowns (lib/light.glsl)
		vec3 fill = vec3(1.0);
		if (s.fromLod) {
			reach *= farCanopySun(material);
			fill = vec3(farCanopyFill(material));
		}
		color.rgb = welkinLightFull(albedo, n, lm, unpackEmission(flags.z), sunShare, fill, reach, facing, flags.w);
#ifdef WELKIN_WET
		// puddles mirror the sky (the sky table's horizon), glint toward the light where it reaches, flick with drops
		color.rgb = welkinWetLight(st, color.rgb, wet, lightDir, skyDirectLight() * reach * sunShare, skyHorizon(false));
#if HAZE_DEBUG == 4
		color.rgb = welkinWetDebug(wet);
#endif
#endif
#if DEBUG_VIEW == 12
		// the sun's reach x facing, with what glass, water and the clouds let through; the LOD's land tinted blue
		color.rgb = vec3(reach * facing) * sunShare * (s.fromLod ? vec3(0.55, 0.7, 1.0) : vec3(1.0));
#endif
	}
#endif
	float d = 0.5; // the far fade's old dither threshold: no dither any more
#if defined FAR_FADE && !(defined HAZE_ENABLED && defined WORLD_OVERWORLD) && DEBUG_VIEW == 0
	color.rgb = mix(color.rgb, horizonColor(), farFade(length(eyePos.xz), d));
#endif
	outColor = color;
}

#endif
