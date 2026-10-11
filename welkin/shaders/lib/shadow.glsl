// VOID Welkin: the near sun shadows. One shadow map over the near chunks only (shadowDistance blocks round the
// camera; the LOD never draws into it: dhShadow.enabled=false), drawn by program/shadow.glsl and read here by deferred
// and the forward programs that light themselves (entities, block entities, glass and water).
//
// The look: clean edges, slightly softened by a fixed filter (hardware 2x2 compare, then SHADOW_SOFTNESS taps on a
// fixed grid; no noise, no rotation, no dither), fading smoothly into the plain sky-light gate over the last 20 % of
// shadowDistance, where the light ramp's own estimate (lib/light.glsl sunOpen) takes over for the far chunks and the
// LOD. Light that passes stained glass takes its colour; under water it takes a blue-green tint and fades with the
// depth it travels (smooth; water_shadows' caustics come with P7).
//
// The two option consts (shadowMapResolution, shadowDistance) and sunPathRotation are in lib/settings.glsl; the rest
// of Iris's shadow directives are here. Iris reads them from any program that includes this file.

#ifndef WELKIN_SHADOW
#define WELKIN_SHADOW

#include "/lib/settings.glsl"

const float shadowDistanceRenderMul = 1.0; // draw only the chunks inside shadowDistance into the map
const float shadowIntervalSize = 2.0;
const bool shadowHardwareFiltering1 = true; // shadowtex1 (opaque only) is a compare sampler with the 2x2 blend
const bool shadowtex0Nearest = true;        // shadowtex0 (translucents too) is read as plain depth, for the tint
const bool shadowcolor0Nearest = true;
/*
const int shadowcolor0Format = RGBA8;
const vec4 shadowcolor0ClearColor = vec4(1.0, 1.0, 1.0, 1.0);
*/

// Fisheye distortion: texels about 6.7x finer at the player than at the map's edge.
const float SHADOW_DISTORT = 0.85;
// The map's depth range is squeezed by this, so casters up to about 200 blocks toward the sun still land in it
// (Iris's shadow projection reaches about 100).
const float SHADOW_Z_SCALE = 0.5;

float shadowDistortFactor(vec2 clipXY) {
	return length(clipXY) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT);
}

// Shadow clip space (orthographic, so w is 1) to the distorted map space the shadow program writes.
vec3 shadowDistort(vec3 clip) {
	return vec3(clip.xy / shadowDistortFactor(clip.xy), clip.z * SHADOW_Z_SCALE);
}

#if defined SHADOWS && defined WORLD_OVERWORLD && !defined WELKIN_SHADOW_PASS
#define WELKIN_SHADOWS_LIVE

#ifndef WU_shadowtex1
#define WU_shadowtex1
uniform sampler2DShadow shadowtex1; // opaque only, compare
#endif
#ifndef WU_shadowtex0
#define WU_shadowtex0
uniform sampler2D shadowtex0;       // opaque and translucent depth
#endif
#ifndef WU_shadowcolor0
#define WU_shadowcolor0
uniform sampler2D shadowcolor0;     // what translucents let through (program/shadow.glsl)
#endif
#ifndef WU_shadowModelView
#define WU_shadowModelView
uniform mat4 shadowModelView;
#endif
#ifndef WU_shadowProjection
#define WU_shadowProjection
uniform mat4 shadowProjection;
#endif

// How far into the fade at the map's edge a point is: 0 inside 80 % of shadowDistance, 1 at it.
float shadowFade(vec3 playerPos) {
	return smoothstep(0.8 * shadowDistance, shadowDistance, length(playerPos));
}

// The compare taps: the hardware 2x2 blend alone, or a fixed grid of them (2x2 or 3x3) whose spacing grows with the
// local texel up to one texel, so the edge reads as a short, smooth ramp and never as stairs or noise.
float shadowTaps(vec3 uvz, float spacing) {
	float o = spacing / float(shadowMapResolution);
#if SHADOW_SOFTNESS >= 2
	float v = 0.0;
	for (int y = -1; y <= 1; y++) {
		for (int x = -1; x <= 1; x++) {
			v += texture(shadowtex1, uvz + vec3(vec2(x, y) * o, 0.0));
		}
	}
	return v * (1.0 / 9.0);
#elif SHADOW_SOFTNESS == 1
	float h = 0.5 * o;
	return 0.25 * (texture(shadowtex1, uvz + vec3(-h, -h, 0.0)) + texture(shadowtex1, uvz + vec3(h, -h, 0.0))
		+ texture(shadowtex1, uvz + vec3(-h, h, 0.0)) + texture(shadowtex1, uvz + vec3(h, h, 0.0)));
#else
	return texture(shadowtex1, uvz);
#endif
}

// What of the sun's (or the moon's) direct light reaches a point, 0..1, before the fade: playerPos camera-relative
// (feet-relative as gbufferModelViewInverse gives it), n the face's world normal, lightDir the world light direction.
// thin: a plant's quad (double-sided, its normal means little): the lookup steps toward the light instead of off the
// face. tint: the colour of that light after stained glass or water on the way (1 without).
float shadowVisibility(vec3 playerPos, vec3 n, vec3 lightDir, bool thin, out vec3 tint) {
	tint = vec3(1.0);
	vec3 sp = (shadowProjection * (shadowModelView * vec4(playerPos, 1.0))).xyz;
	float f = shadowDistortFactor(sp.xy);
	// one map texel in blocks here: the distortion's slope (1 - k) / f^2 at this radius
	float texelBlocks = (2.0 * shadowDistance / float(shadowMapResolution)) * f * f / (1.0 - SHADOW_DISTORT);
	// normal offset sized from the local texel (no slope bias needed): off the face for solids, toward the light for
	// plants; plus a little toward the light against acne on faces turned almost edge-on
	vec3 offset = thin ? lightDir * (2.0 * texelBlocks + 0.03) : n * (1.6 * texelBlocks + 0.02) + lightDir * (0.4 * texelBlocks);
	vec3 uvz = shadowDistort((shadowProjection * (shadowModelView * vec4(playerPos + offset, 1.0))).xyz) * 0.5 + 0.5;
	if (any(lessThanEqual(uvz.xy, vec2(0.0))) || any(greaterThanEqual(uvz.xy, vec2(1.0))) || uvz.z >= 1.0) {
		return 1.0;
	}
	// the soft spacing: a share of the local texel, at most one texel
	float vis = shadowTaps(uvz, clamp(0.08 / texelBlocks, 0.5, 1.0));

	// a translucent block between the light and the point: in shadowtex0, not in shadowtex1
	if (vis > 0.0) {
		float d0 = texture(shadowtex0, uvz.xy).r;
		if (d0 < uvz.z - 0.0002) {
			vec4 c = texture(shadowcolor0, uvz.xy);
			tint = c.rgb * 2.0; // stored at half
			if (c.a < 0.5) {
				// water: the light fades with the depth it travels under the surface (blocks, from the two depths)
				float travel = (uvz.z - d0) * 2.0 /(abs(shadowProjection[2][2]) * SHADOW_Z_SCALE);
				tint *= mix(0.35, 1.0, exp(-travel * 0.12));
			}
		}
	}
	return vis;
}
#endif

#ifndef WELKIN_SHADOW_PASS
#include "/lib/light.glsl"

// How far the sun reaches a surface (the ramp's sunReach), with what it lets through multiplied into sunShare: the
// shadow map where it covers, fading over its last 20 % into the sky-light gate the far chunks, the LOD and LOW use
// (for leaves, times the crown's average self-shade, farCanopySun, as the LOD's leaves take it: the tones meet).
// playerPos camera-relative, n the world normal, sky the sky light level, m plumbing's material class, facing the
// face's sunFacingMat (no lookup where it is 0: no direct light there anyway).
float welkinSunReach(vec3 playerPos, vec3 n, float sky, int m, float facing, inout vec3 sunShare) {
	float reach = sunOpen(sky);
#ifdef WELKIN_SHADOWS_LIVE
	reach *= farCanopySun(m);
	float fade = shadowFade(playerPos);
	if (fade < 1.0 && facing > 0.0) {
		vec3 tint;
		float vis = shadowVisibility(playerPos, n, lightDirWorld(), matThin(m), tint);
		reach = mix(vis * sunLeakGate(sky), reach, fade);
		sunShare *= mix(tint, vec3(1.0), fade);
	}
#endif
	return reach;
}
#endif

#endif
