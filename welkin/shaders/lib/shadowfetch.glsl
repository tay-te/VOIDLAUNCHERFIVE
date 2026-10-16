// VOID Welkin: the light pass's shadow read (program/deferred.glsl, deferred4). With the shadow map on (overworld,
// SHADOWS) it reads the resolved reach (colortex11, program/deferred_resolve.glsl) and looks the light's tint up
// (stained glass, or the water the light passed: neither is noisy, so neither goes through the resolve). Without the
// map (LOW, the other dimensions) it works the reach out here as before: the sky-light gate, times the far shadows
// found on screen.

#ifndef WELKIN_SHADOWFETCH
#define WELKIN_SHADOWFETCH

#include "/lib/shadow.glsl"
#if defined FAR_SHADOWS && defined WORLD_OVERWORLD
#include "/lib/farshadow.glsl"
#define WELKIN_FAR_SHADOWS
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif
#endif

#include "/lib/terrain.glsl"

#ifdef WELKIN_SHADOWS_LIVE
#define WELKIN_SHADOW_BUFFERS
#ifndef WU_colortex10
#define WU_colortex10
uniform sampler2D colortex10;
#endif
#ifndef WU_colortex11
#define WU_colortex11
uniform sampler2D colortex11;
#endif
#endif

// The terrain's slope (lib/terrain.glsl) on a top face in the middle and far distance: it takes the sun as the hillside
// it stands on would (Lambert on the land's own normal, steepened TERRAIN_SLOPE % for a stylized hill shading, against
// the face's; at most 3x), fading in from 40 to 160 blocks, where the blocks' own sides stop showing the slope.
// facing: the face's sunFacingMat, changed in place.
float terrainSlopeLast = 1.0; // the factor applied (DEBUG_VIEW 21)
float terrainWLast = 0.0;
void terrainSlopeFacing(vec3 playerPos, vec3 n, inout float facing) {
#ifdef WELKIN_TERRAIN_LIVE
	float w = smoothstep(40.0, 160.0, length(playerPos)) * smoothstep(0.6, 0.95, n.y);
	if (TERRAIN_SLOPE == 0 || w <= 0.0 || facing <= 0.0) {
		return;
	}
	TerrainCell tc = terrainAt(playerPos);
	terrainWLast = tc.w;
	vec3 l = lightDirWorld();
	float ratio = clamp(sunFacing(dot(terrainNormal(tc, float(TERRAIN_SLOPE) * 0.01), l))
		/ max(sunFacing(dot(n, l)), 0.05), 0.0, 3.0);
	terrainSlopeLast = mix(1.0, ratio, w * tc.w);
	facing *= terrainSlopeLast;
#endif
}

// The sun's reach at screen pixel p (the ramp's sunReach, before the face's facing), with the light's tint multiplied
// into sunShare. facing: as in, and on the far land's tops it takes the terrain's slope (terrainSlopeFacing). s the surface there, playerPos camera-relative, n the world normal, sky the sky light level, m
// plumbing's material class, facing sunFacingMat. through: the share of the light that passes the leaves between the
// light and a leaf or a plant (for their glow).
float lightShadowFetch(ivec2 p, Surface s, vec3 playerPos, vec3 n, float sky, int m, inout float facing,
		inout vec3 sunShare, out float through) {
	terrainSlopeFacing(playerPos, n, facing);
#ifdef WELKIN_SHADOW_BUFFERS
	vec2 res = texelFetch(colortex11, p, 0).rg;
	through = res.g;
	float reach = res.r;
	if (s.fromLod) {
		return reach;
	}
	float fade = shadowFade(playerPos);
	if (fade < 1.0 && (facing > 0.0 || matHas(m, MATF_SSS)) && sunAngle < 0.5) {
		float tt = texelFetch(colortex10, p, 0).b;
		vec3 tint = vec3(1.0);
		vec3 l = lightDirWorld();
		if (tt >= 0.0) {
			tint = shadowWaterTint(tt);
			if (!matHas(m, MATF_SSS) && facing > 0.0) {
				// under water the light comes down steeper than it stands in the sky (bent at the surface), so a lake bed
				// under a low sun takes it far more squarely than the grazing angle above says (round 2)
				vec3 lr = vec3(l.xz / 1.33, 0.0).xzy;
				lr.y = sqrt(max(1.0 - dot(lr.xz, lr.xz), 0.0));
				tint *= clamp(sunFacing(dot(n, lr)) / facing, 1.0, 4.0);
			}
			// (review round 1) but a low sun mostly glances off the surface (Fresnel: at 5 degrees above the water
			// about half of it is thrown back, at 2 degrees most): its light on a bed is weaker, so the bank's long
			// shadows across a lake bed at dawn and dusk read as soft lanes, not dark bars over the water
			tint *= mix(0.3, 1.0, smoothstep(0.02, 0.35, l.y));
		} else if (reach > 0.0) {
			tint = shadowGlassTint(shadowSpotDry(playerPos, n, l, matThin(m)));
		}
		sunShare *= mix(tint, vec3(1.0), fade);
	}
	return reach;
#else
	float farVis = 1.0;
#ifdef WELKIN_FAR_SHADOWS
	if (facing > 0.0 && sky > 0.5 && (s.fromLod || farShadowNeed(playerPos) > 0.0)) {
		farVis = farShadowRay(s.view, mat3(gbufferModelView) * n, normalize(shadowLightPosition), 1.0,
			shadowLongFadeStrength(lightDirWorld()));
		farVis = shadowLeafFarRay(m, n, farVis); // workstream C: far crowns
	}
#endif
#ifdef WELKIN_TERRAIN_LIVE
	if (facing > 0.0 || matHas(m, MATF_SSS)) {
		float tAll, tFar;
		terrainShadow(terrainAt(playerPos), playerPos, shadowLongFadeStrength(lightDirWorld()), lightDirWorld().y,
			sunAngle < 0.5 ? 1.0 : 0.0, tAll, tFar);
		farVis = min(farVis, tAll);
	}
#endif
	float reach = sunOpen(sky) * farVis;
	through = (matIsLeaves(m) ? 0.35 : 1.0) * reach;
	if (!s.fromLod) {
		reach = welkinSunReachEx(playerPos, n, sky, m, facing, farVis, sunShare, through);
	}
	return reach;
#endif
}

#endif
