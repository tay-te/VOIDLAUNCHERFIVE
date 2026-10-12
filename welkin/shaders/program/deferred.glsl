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
#include "/lib/ao.glsl"
#if defined FAR_SHADOWS && defined WORLD_OVERWORLD
#include "/lib/farshadow.glsl"
#define WELKIN_FAR_SHADOWS
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif
#endif

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
#include "/lib/watersurf.glsl"
#define WELKIN_WET
#if defined CLOUDS && defined WET_PUDDLES
// the clouds overhead in the puddles: the decks as sheets along the reflected ray (lib/clouds/march.glsl)
#include "/lib/clouds/shade.glsl"
#define WELKIN_WET_CLOUDS
#endif
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
		// past the map, and on the LOD's land, the far shadows found on screen (lib/farshadow.glsl)
		vec3 playerPos = (gbufferModelViewInverse * vec4(s.view, 1.0)).xyz;
		float farVis = 1.0;
#ifdef WELKIN_FAR_SHADOWS
		if (facing > 0.0 && g.w > 0.5 && (s.fromLod || farShadowNeed(playerPos) > 0.0)) {
			farVis = farShadow(s.view, mat3(gbufferModelView) * n, normalize(shadowLightPosition));
		}
#endif
		float reach = sunOpen(g.w) * farVis;
		float through = (matIsLeaves(material) ? 0.35 : 1.0) * reach;
		if (!s.fromLod) {
			reach = welkinSunReachEx(playerPos, n, g.w, material, facing, farVis, sunShare, through);
		}
		// the light through leaves and plants (lib/light.glsl welkinGlow)
		float glow = welkinGlow(normalize(eyePos), n, lightDir, material, through);
		if (matThin(material)) {
			// far grass and flowers: their sunlit quads (often pale petals) would read as bright specks over a field, so
			// past about 24 blocks their glow fades and they take the sun a little less squarely
			float farPlant = smoothstep(24.0, 64.0, dist);
			glow *= 1.0 - farPlant;
			facing *= 1.0 - 0.25 * farPlant;
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
		// the contact occlusion round near points (program/deferred_ao.glsl, lib/ao.glsl)
		float contact = 1.0;
#ifdef CONTACT_AO
		if (!s.fromLod) {
			contact = welkinContactAo(p, -s.view.z);
		}
#endif
		color.rgb = welkinLightGlow(albedo, n, lm, unpackEmission(flags.z), sunShare, fill, reach, facing, flags.w, glow,
			contact);
#ifdef WELKIN_WET
		// puddles mirror the sky (the sky table's horizon), glint toward the light where it reaches, flick with drops
		vec3 skyR = skyHorizon(false);
		if (wet.wet > 0.0 || wet.puddle > 0) {
			// what the wet surface mirrors: the sky's gradient along the reflected view (a puddle is level), and in a
			// puddle the clouds overhead
			vec3 r = reflect(normalize(eyePos), wet.puddle >= 2 ? vec3(0.0, 1.0, 0.0) : n);
			skyR = waterSkyColour(r) * smoothstep(0.45, 0.95, g.w);
#ifdef WELKIN_WET_CLOUDS
			if (wet.puddle == 2 && r.y > 0.02) {
				CloudEnv ce = welkinCloudEnv();
				CloudHit ch = welkinCloudSheets(eyePos, r, 0.0, WC_FAR, ce, wcNoHit());
				if (ch.hit) {
					float pixAngle = 2.0 / (gbufferProjection[1][1] * viewHeight);
					skyR = welkinCloudColor(ch, eyePos, r, ce, pixAngle);
				}
			}
#endif
		}
		color.rgb = welkinWetLight(st, color.rgb, wet, n, lightDir, skyDirectLight() * reach * sunShare, skyR);
#if HAZE_DEBUG == 4
		color.rgb = welkinWetDebug(wet);
#endif
#endif
#if DEBUG_VIEW == 12
		// the sun's reach x facing, with what glass, water and the clouds let through; the LOD's land tinted blue
		color.rgb = vec3(reach * facing) * sunShare * (s.fromLod ? vec3(0.55, 0.7, 1.0) : vec3(1.0));
#elif DEBUG_VIEW == 13
		// the contact shade (white: none) over the far shadows (blue: in shadow past the map)
		color.rgb = vec3(contact) * mix(vec3(0.3, 0.45, 1.0), vec3(1.0), farVis);
#elif DEBUG_VIEW == 14
		// the light through leaves and plants (green), what passes the casters (red)
		color.rgb = vec3(through, min(glow * 2.0, 1.0), 0.0);
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
