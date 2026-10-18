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
// the sun's reach: the shadow buffers (deferred_shadow, deferred_resolve), or worked out here without the map
#include "/lib/shadowfetch.glsl"

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
// ---- M5 underground (P6): damp walls, realm, glowworm and lava light (lib/underground/ug_surface.glsl)
#include "/lib/underground/ug_surface.glsl"
// ---- M6 water (welkin-water): the wet band at the water's edge, frost, the season's light (lib/ws/surface.glsl)
#if defined WORLD_OVERWORLD && (defined WS_SHORE_WET || defined WS_FROST || defined WS_SEASON_LIGHT)
#include "/lib/sway.glsl"
#include "/lib/ws/config.glsl"
#include "/lib/ws/inputs.glsl"
#include "/lib/ws/season.glsl"
#include "/lib/ws/surface.glsl"
#define WELKIN_WS_DEFERRED
#endif
// ---- end M6 water

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
		if (matIsLeaves(material)) {
			// round 2: leaves' foliage tint a little less saturated, so a sunlit crown reads a natural green, not lime
			albedo = mix(vec3(dot(albedo, vec3(0.2126, 0.7152, 0.0722))), albedo, 0.86);
		}
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
		float through;
		float reach = lightShadowFetch(p, s, playerPos, n, g.w, material, facing, sunShare, through);
#if defined WELKIN_SHADOW_BUFFERS && FOLIAGE_CANOPY > 0
		// workstream C: how far the point stands under a canopy (deferred_shadow found it from the map's leaves above it)
		if (!s.fromLod && shadowFade(playerPos) < 1.0) {
			// (only read where the sky light is cut: in the open it is 0)
			foliageCanopy = g.w < 0.975 ? clamp(-1.0 - texelFetch(colortex10, p, 0).b, 0.0, 1.0) : 0.0;
		}
#endif
#ifdef WELKIN_SHADOW_BUFFERS
		if (matIsLeaves(material) && !s.fromLod && dot(n, lightDir) < 0.5) {
			// workstream C: a leaf's own transmission, per pixel: the leaves before it at its own map texel (only on the
			// faces the glow reaches, turned from the light)
			float fl = shadowFade(playerPos);
			if (fl < 1.0) {
				float tl = shadowThickAt(shadowSpotDry(playerPos, n, lightDir, false), shadowDepthBlocks());
				through = mix(exp(-LEAF_SIGMA * tl) * sunLeakGate(g.w), through, fl);
			}
		}
#endif
		// the light through leaves and plants (lib/light.glsl welkinGlow)
		float glow = welkinGlow(normalize(eyePos), n, lightDir, material, through) * (0.01 * float(LEAF_TRANSLUCENCY));
		if (matThin(material)) {
			// far grass and flowers: their sunlit quads (often pale petals) would read as bright specks over a field, so
			// past about 24 blocks their glow fades and they take the sun a little less squarely
			float farPlant = smoothstep(24.0, 64.0, dist);
			glow *= 1.0 - farPlant;
			facing *= 1.0 - 0.25 * farPlant;
		}
#if FAR_LEAF_FORM > 0
		if (matIsLeaves(material)) {
			// the balance step: past the shadow map (and on the LOD's land) nothing shades a crown's far side, so a leaf
			// there takes the sun more by how its face turns to it: the woods far off keep a lit and a shaded side
			float farLeaf = s.fromLod ? 1.0 : farShadowNeed(playerPos);
			facing = mix(facing, sunFacing(dot(n, lightDir)), farLeaf * float(FAR_LEAF_FORM) * 0.01);
		}
#endif
#ifdef WELKIN_WET
		// wet ground before the light: darkens open ground, picks the puddles
		HzState st = hzState();
		WelkinWet wet = welkinWetSurface(st, albedo, eyePos, n, g.w, hzWetClass(material));
#endif
		// ---- M5 underground (P6): damp, darker rock before the light (not on the LOD's land)
		UgSurface us = UgSurface(vec3(1.0), vec3(0.0), vec3(0.0));
		if (!s.fromLod) {
			us = ugSurface(ugState(), eyePos, n, dist);
		}
		albedo *= us.albedo;
		// ---- end M5
		// ---- M6 water (welkin-water): the shore's wet band (the wetter of it and the rain's), frost, the season's
		// tints of the sun and the shade (the shade's on the fill, below)
#ifdef WELKIN_WS_DEFERRED
#ifdef WELKIN_WET
		float wsRainWet = wet.wet;
#else
		float wsRainWet = 0.0;
#endif
		WsDeferred wsd = wsDeferredSurface(albedo, eyePos, n, g.w, material, unpackEmission(flags.z), wsRainWet,
			reach * facing, blocksPerPixelAt(dist, gbufferProjection[1][1], viewHeight));
		sunShare *= wsd.sunTint;
#endif
		// ---- end M6 water
		vec2 lm = g.zw;
		lm.x = welkinHandBlockLight(lm.x, dist); // a torch in hand lights the ground on the same ramp
		// the LOD's leaves: the crown's average self-shade the near shadow map gives near crowns (lib/light.glsl)
		vec3 fill = vec3(1.0);
		if (matIsLeaves(material)) {
			// workstream C: a leaf block keeps more of Minecraft's face shading on its fill, so a crown reads block by block
			fill *= mix(1.0, faceShade(n), LEAF_FACE_SHADE);
		}
		if (s.fromLod) {
			reach *= farCanopySun(material);
			fill *= farCanopyFill(material);
		}
#ifdef WELKIN_WS_DEFERRED
		fill *= wsd.ambientTint; // M6 water: the season's tint of the shade
#endif
		// ---- Light: ambient (B): the ambient occlusion round near points and its bent normal (program/deferred_ao.glsl,
		// lib/ao.glsl); lib/light.glsl lays it on the fill and the torch light
		// (the LOD's land too: the middle and far land take it at their own, wider scale)
		float contact = 1.0;
#ifdef AMBIENT_OCCLUSION
		AoSample aoS = lightAoFetch(p, s.view, n, material);
		contact = aoS.vis;
		lightAoCover = aoS.cover;
		lightAoBend = aoS.bend;
#if AO_SUN_FAR > 0 && defined WORLD_OVERWORLD
		// past the shadow map and on the LOD's land, the sun outside the occlusion's open cone is taken off (lib/ao.glsl),
		// over the same fade as the far shadows (lib/shadow.glsl farShadowNeed: the map's own reach, half under the moon)
#ifdef WELKIN_SHADOWS_LIVE
		float aoFar = s.fromLod ? 1.0 : farShadowNeed(playerPos);
#else
		float aoFar = s.fromLod ? 1.0 : smoothstep(0.8 * shadowDistance, shadowDistance, length(playerPos));
#endif
		// (a plant's quads were taken as facing up by the occlusion: its cone stands round the up direction)
		vec3 nCone = matThin(material) ? vec3(0.0, 1.0, 0.0) : n;
		// the far rays and the terrain's shadow see the same hills and crowns the cone does: the darker of the two, not
		// their product, so a far penumbra is not darkened twice
		reach = min(reach, mix(1.0, aoSunCone(pow(contact, AO_STRENGTH), normalize(nCone + lightAoBend), lightDir),
			aoFar * aoS.cover * float(AO_SUN_FAR) * 0.01 * (matIsLeaves(material) ? 0.35 : 1.0)));
		// (workstream C: on a far crown a third of it: its occlusion sees the crowns round it, which stand beside it, not
		// between it and the sun; the crown's own form comes from its faces, lib/foliage.glsl leafFacing)
#endif
#endif
		// plants' crossed quads take the sky's fill as from above, and 1.45x (lib/light.glsl lightFillUp)
		// (and leaves, as a cube of small leaves turned every way, a little: a crown's sides are not walls)
		// (workstream C: leaves 0.15, not 0.35: a crown's heart is not lit from the open sky above it)
		lightFillUp = matThin(material) ? 0.7 : (matIsLeaves(material) ? 0.15 : 0.0);
		// (forest2: a crown's heart keeps round 3's deep cut of the sky's fill; the floor under it less, lib/foliage.glsl)
		canopyFillCut = matIsLeaves(material) ? CANOPY_FILL_CUT : CANOPY_FILL_CUT_FLOOR;
		// (workstream C: a plant's corner shading carries its root shade, gbuffers_common: laid on whole, with the
		// occlusion)
		float aoMc = flags.w;
		if (matThin(material) && lightAoCover > 0.0) {
			contact *= aoMc;
			aoMc = 1.0;
		}
		// ---- M5 underground (P6): the realm's, the glowworms' and the lava's light and the rock's own glow, in linear
		// light inside the light pass (under the fill's occlusion, before the tone), not added to the toned colour
		lightUgLin = toLinear(us.light);
		lightEmitLin = toLinear(us.glow);
#ifdef WELKIN_WS_DEFERRED
		// M6 water: glints on frost and snow where the direct light reaches, as emitted light in linear light (the
		// land's direct light: the tone rolls a glint into white, as it does a sunlit pale face)
		lightEmitLin += wsDeferredSparkle(wsd, eyePos, n, material, skyLandDirect() * reach * facing * sunShare);
#endif
		color.rgb = welkinLightGlow(albedo, n, lm, unpackEmission(flags.z), sunShare, fill, reach, facing, aoMc, glow,
			contact);
#ifdef WELKIN_WS_DEFERRED
		color.rgb = wsDeferredDebug(wsd, color.rgb); // M6 water: the WS_DEBUG views
#endif
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
		// the ambient occlusion (white: none) over the far shadows (blue: in shadow past the map)
		color.rgb = vec3(contact) * mix(vec3(0.3, 0.45, 1.0), vec3(1.0),
			(s.fromLod || farShadowNeed(playerPos) > 0.0) ? reach : 1.0);
#elif DEBUG_VIEW == 14
		// the light through leaves and plants (green), what passes the casters (red), the canopy over the point (blue,
		// workstream C)
		color.rgb = vec3(through, min(glow * 2.0, 1.0), max(foliageCanopy, 0.0));
#elif DEBUG_VIEW == 21
		// the terrain's light (workstream A, lib/terrain.glsl): red the sun's reach, green the slope's factor / 2,
		// blue where the terrain's data is
		color.rgb = vec3(reach, terrainSlopeLast * 0.5, terrainWLast);
#elif DEBUG_VIEW >= 15 && DEBUG_VIEW <= 17 && defined WELKIN_SHADOW_BUFFERS
		// the shadow buffers (workstream A): 15 the raw reach (noisy), 16 the resolved reach, 17 the history weight
		// (green kept, red where the clamp acted; black: no history)
		vec4 sraw = texelFetch(colortex10, p, 0);
		vec4 sres = texelFetch(colortex11, p, 0);
		color.rgb = DEBUG_VIEW == 15 ? vec3(sraw.r) : (DEBUG_VIEW == 16 ? vec3(sres.r)
			: vec3(max(-sres.b, 0.0), max(sres.b, 0.0), 0.0));
#elif DEBUG_VIEW == 18
		// Light: ambient (B): the occlusion of the fill as it is laid on (with the multi-bounce on this albedo)
		color.rgb = mix(vec3(mix(LIGHT_AO_FLOOR, 1.0, pow(flags.w, LIGHT_AO_POW))), max(aoMultiBounce(pow(contact, AO_STRENGTH),
			toLinear(albedo)) * mix(1.0, flags.w, AO_VANILLA), vec3(AO_FLOOR)), lightAoCover);
#elif DEBUG_VIEW == 19
		// the bent normal the sky's light is looked up along (world, 0.5 + 0.5 xyz)
		color.rgb = normalize(n + lightAoBend) * 0.5 + 0.5;
#elif DEBUG_VIEW == 22
		// Light: ambient (B): the sun's reach x facing after the far land's own shade (AO_SUN_FAR), the occlusion beneath
		color.rgb = vec3(reach * facing) * 0.75 + 0.25 * vec3(contact);
#elif DEBUG_VIEW == 20
		// Light: ambient (B): the sky's irradiance along the (bent) normal, relative to an upward face: 0.4 is 1
		color.rgb = skyIrradiance(normalize(n + lightAoBend)) * 0.4;
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
