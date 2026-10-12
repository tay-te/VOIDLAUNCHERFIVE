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
// creatures cast into the map only within half its reach (past that their shadow is a few texels; entity drawing is
// the pass's most expensive geometry per block)
const float entityShadowDistanceMul = 0.5;
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

// Blocks along the light per unit of the map's depth (its 0..1 depth range, after the squeeze).
float shadowDepthBlocks() {
	return 2.0 / (abs(shadowProjection[2][2]) * SHADOW_Z_SCALE);
}

// ---- The penumbra (contact hardening). A shadow is crisp where its caster stands close to it (the foot of a post,
// the base of a wall) and widens with the distance the light travels from the caster to the ground, as the sun's
// disc makes it: a fixed share of that distance (SHADOW_SPREAD blocks per block, a little wider than the real sun's
// 0.0093, for a storybook softness), never under one map texel. The width is found from the casters round the point
// (a 3x3 search of the map's depths), then the edge is filtered by a 4x4 grid of hardware-compared taps spread over
// it. Points with no caster round them, or deep inside a shadow, stop after the search: only the edges pay for the
// filter. Fixed offsets, no rotation, no noise: the edge is a smooth ramp, stable from frame to frame.
#if SHADOW_SOFTNESS >= 2
const float SHADOW_SPREAD = 0.034;
#else
const float SHADOW_SPREAD = 0.02;
#endif
// the widest penumbra filtered, in map texels (a wider one is drawn at this; near the eye a texel is about 1/40 block)
const float SHADOW_MAX_TEXELS = 6.0;

// The 4x4 grid of compare taps over a penumbra of radius r texels (each tap is the hardware's 2x2 blend, so the
// ramp between taps stays smooth). grad: the receiver's plane, as map depth per unit of map uv: each tap compares
// against the plane's own depth where it lands, so a face never shadows itself however low the light grazes it.
float shadowPcf(vec3 uvz, float r, vec2 grad) {
	float s = max(r, 0.5) / (1.5 * float(shadowMapResolution));
	float v = 0.0;
	for (int y = 0; y < 4; y++) {
		for (int x = 0; x < 4; x++) {
			vec2 o = (vec2(x, y) - 1.5) * s;
			v += texture(shadowtex1, vec3(uvz.xy + o, uvz.z + dot(grad, o)));
		}
	}
	return v * (1.0 / 16.0);
}

// A camera-relative point to the map's distorted (uv, depth), 0..1.
vec3 shadowMapPos(vec3 playerPos) {
	return shadowDistort((shadowProjection * (shadowModelView * vec4(playerPos, 1.0))).xyz) * 0.5 + 0.5;
}

// The receiver plane's depth gradient in map uv round p (a point on a face with world normal n): two small steps
// along the face, mapped, solved for d(depth)/d(uv). Held to the slope of a face the light meets at about 2.5
// degrees (past that the face takes almost no light anyway), so an edge-on face cannot push its taps out of range.
vec2 shadowPlaneGrad(vec3 p, vec3 uvz, vec3 n, float texelBlocks, float depthBlocks) {
	vec3 t1 = normalize(cross(n, abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
	vec3 t2 = cross(n, t1);
	vec3 d1 = shadowMapPos(p + t1 * 0.25) - uvz;
	vec3 d2 = shadowMapPos(p + t2 * 0.25) - uvz;
	float det = d1.x * d2.y - d1.y * d2.x;
	if (abs(det) < 1e-14) {
		return vec2(0.0);
	}
	vec2 g = vec2(d1.z * d2.y - d2.z * d1.y, d1.x * d2.z - d2.x * d1.z) / det;
	// the steepest allowed: 23 blocks of depth per block across the map (cot 2.5 degrees)
	float gMax = 23.0 * texelBlocks * float(shadowMapResolution) / depthBlocks;
	float gl = length(g);
	return gl > gMax ? g * (gMax / gl) : g;
}

// The receiver's sky light, when the caller knows it (welkinSunReachEx sets it): water dims the sky light a level per
// block, so only a point under less than the open sky's full level can lie under water (the search for the surface
// above it, below, runs only there).
float shadowSkyHint = 0.0;

// A point's shadow: vis what of the light gets through (0..1), tint the colour it takes through stained glass or
// water, depth the blocks of matter the light passed before it (the casters' mean distance above it along the light;
// 0 in the open), for the glow through leaves.
struct ShadowSample {
	float vis;
	vec3 tint;
	float depth;
	float cover; // the share of the search that found a caster (0 in the open)
	bool underWater; // the light reached the point through water's surface (it is bent on the way down)
};

// What of the sun's (or the moon's) direct light reaches a point: playerPos camera-relative (feet-relative as
// gbufferModelViewInverse gives it), n the face's world normal, lightDir the world light direction.
// thin: a plant's quad (double-sided, its normal means little): the lookup steps toward the light instead of off the
// face. water: the point is on water's surface, which other water in front of it never tints (a slope's or a fall's
// upper steps would otherwise dim its lower ones in patches).
// minWidth: the narrowest penumbra (half width, blocks): leaves take a wide one, so the light through a crown falls on
// its own leaves as soft dapples instead of the long streaks a grazing sun draws from each leaf pixel.
ShadowSample shadowSampleW(vec3 playerPos, vec3 n, vec3 lightDir, bool thin, bool water, float minWidth) {
	ShadowSample r;
	r.vis = 1.0;
	r.tint = vec3(1.0);
	r.depth = 0.0;
	r.cover = 0.0;
	r.underWater = false;
	float depthBlocks = shadowDepthBlocks();
	float waterTravel = 0.0;
	vec3 q = playerPos;
	vec3 uvz;
	float texelBlocks;
	vec3 offset;
	for (int pass = 0; pass < 2; pass++) {
		vec3 sp = (shadowProjection * (shadowModelView * vec4(q, 1.0))).xyz;
		float f = shadowDistortFactor(sp.xy);
		// one map texel in blocks here: the distortion's slope (1 - k) / f^2 at this radius
		texelBlocks = (2.0 * shadowDistance / float(shadowMapResolution)) * f * f / (1.0 - SHADOW_DISTORT);
		// normal offset sized from the local texel: off the face for solids, toward the light for plants; plus a step
		// toward the light that grows as the light grazes the face (a texel's depth spread is the texel times the
		// tangent of the light's angle to the normal), so a low sun on level ground lights it instead of the ground
		// shadowing itself
		float ndl = max(dot(n, lightDir), 0.0);
		float grazing = min(sqrt(max(1.0 - ndl * ndl, 0.0)) / max(ndl, 0.05), 12.0);
		offset = thin ? lightDir * (2.0 * texelBlocks + 0.03)
			: n * (1.6 * texelBlocks + 0.02) + lightDir * (texelBlocks * (0.4 + 0.9 * grazing));
		uvz = shadowMapPos(q + offset);
		if (any(lessThanEqual(uvz.xy, vec2(0.0))) || any(greaterThanEqual(uvz.xy, vec2(1.0))) || uvz.z >= 1.0) {
			return r;
		}
		if (pass == 1 || water) {
			break;
		}
		// round 2: under water. The light is bent down where it enters the surface (at most 49 degrees off the vertical),
		// so it reaches the bed from the surface just above, not along the straight line toward a low sun, which runs
		// under water into the bed's own rise toward the shore and blacked out a whole lake bed at dawn. The point
		// is moved up the bent ray to just under the surface, and the shadow is looked up there (the casters above the
		// water still shade it, bed and surface alike); the water it passed tints it.
		float ly = max(lightDir.y, 0.02);
		vec3 lr = vec3(lightDir.xz / 1.33, 0.0).xzy;
		lr.y = sqrt(max(1.0 - dot(lr.xz, lr.xz), 0.0));
		float below = -1.0; // the depth under the surface, blocks (-1: not under water)
		float d0 = texture(shadowtex0, uvz.xy).r;
		if (d0 < uvz.z - 0.0002 && texture(shadowcolor0, uvz.xy).a < 0.5) {
			// the straight line toward the light leaves through a water surface first
			below = (uvz.z - d0) * depthBlocks * ly;
		} else if (shadowSkyHint < 0.95 && texture(shadowtex1, uvz) < 0.5) {
			// in shadow: the straight line may run into the bed's rise or the bank before it reaches the surface, so
			// look for the surface up the bent ray: a probe that finds water as the first thing toward the light, at
			// most a block and a bit over it, is just under the surface
			const float rises[5] = float[5](1.0, 2.0, 3.5, 6.0, 10.0);
			for (int i = 0; i < 5; i++) {
				vec3 m = shadowMapPos(playerPos + lr * (rises[i] / lr.y));
				float dw = texture(shadowtex0, m.xy).r;
				float over = (m.z - dw) * depthBlocks * ly;
				if (over >= 0.0 && over < 1.3 && texture(shadowcolor0, m.xy).a < 0.5) {
					below = rises[i] + over;
					break;
				}
			}
		}
		if (below < 0.0) {
			break;
		}
		below = min(below, 32.0);
		waterTravel = below / lr.y;
		q = playerPos + lr * (max(below - 0.2, 0.0) / lr.y);
		r.underWater = true;
	}
	float px = 1.0 / float(shadowMapResolution);
	// round 2: the receiver's own plane (not for a plant's quad, whose normal means little): a grazing light on level
	// ground no longer finds the ground a few texels toward the light "above" it (the self-shadow that dimmed a
	// meadow and striped a lake under a low sun)
	vec2 grad = thin ? vec2(0.0) : shadowPlaneGrad(q + offset, uvz, n, texelBlocks, depthBlocks);
	// the casters round the point: a 3x3 search over the widest penumbra a caster up to about 24 blocks above could
	// throw here (at least the hardware tap's own 2x2), in the depths of everything (shadowtex0)
	float searchTexels = clamp((SHADOW_SPREAD * 24.0 + 0.02) / texelBlocks, 1.0, SHADOW_MAX_TEXELS);
	float bias = (0.04 + 0.5 * texelBlocks) / depthBlocks;
	float sum = 0.0;
	float count = 0.0;
	for (int y = -1; y <= 1; y++) {
		for (int x = -1; x <= 1; x++) {
			vec2 o = vec2(x, y) * (searchTexels * px);
			float d = texture(shadowtex0, uvz.xy + o).r;
			if (d < uvz.z + dot(grad, o) - bias) {
				sum += d;
				count += 1.0;
			}
		}
	}
	float centre = texture(shadowtex1, uvz);
	if (count > 0.0) {
		float above = (uvz.z - sum / count) * depthBlocks; // blocks from the casters down to the point
		r.depth = above;
		r.cover = count / 9.0;
#if SHADOW_SOFTNESS == 0
		r.vis = centre;
#else
		if (count > 8.5 && centre < 0.001) {
			r.vis = 0.0; // deep inside a shadow
		} else {
			float width = max(SHADOW_SPREAD * above + 0.015, minWidth); // the penumbra's half width, blocks
			r.vis = shadowPcf(uvz, min(width / texelBlocks, SHADOW_MAX_TEXELS), grad);
		}
#endif
	} else {
		r.vis = centre; // nothing round it: the hardware tap alone (1 in the open)
	}

	// the water the light passed on the way down to a point under the surface: a pale blue-green, fading with the
	// path through it (a third left past about ten blocks)
	if (r.underWater) {
		r.tint = vec3(0.70, 0.86, 0.88) * mix(0.35, 1.0, exp(-waterTravel * 0.12));
	} else if (r.vis > 0.0) {
		// a translucent block between the light and the point: in shadowtex0, not in shadowtex1 (stained glass; water
		// over water's own surface never tints it, so a slope's or a fall's upper steps do not dim its lower ones)
		float d0 = texture(shadowtex0, uvz.xy).r;
		if (d0 < uvz.z - 0.0002) {
			vec4 c = texture(shadowcolor0, uvz.xy);
			r.tint = c.a < 0.5 ? vec3(1.0) : c.rgb * 2.0; // stored at half
		}
	}
	return r;
}

ShadowSample shadowSample(vec3 playerPos, vec3 n, vec3 lightDir, bool thin, bool water) {
	return shadowSampleW(playerPos, n, lightDir, thin, water, 0.0);
}

// The old form, for callers that want only the visibility and the tint.
float shadowVisibility(vec3 playerPos, vec3 n, vec3 lightDir, bool thin, bool water, out vec3 tint) {
	ShadowSample s = shadowSample(playerPos, n, lightDir, thin, water);
	tint = s.tint;
	return s.vis;
}
#endif

#ifndef WELKIN_SHADOW_PASS
#include "/lib/light.glsl"

// How far the sun reaches a surface (the ramp's sunReach), with what it lets through multiplied into sunShare: the
// shadow map where it covers, fading over its last 20 % into the sky-light gate the far chunks, the LOD and LOW use
// (for leaves, times the crown's average self-shade, farCanopySun, as the LOD's leaves take it: the tones meet),
// times farVis there: the far shadows found on screen (lib/farshadow.glsl; 1 without them). playerPos camera-relative,
// n the world normal, sky the sky light level, m plumbing's material class, facing the face's sunFacingMat (no lookup
// where it is 0: no direct light there anyway). through: the share of the light that passes the leaves between the
// light and a leaf or a plant, with the sky-light gate in it (for their glow).
float welkinSunReachEx(vec3 playerPos, vec3 n, float sky, int m, float facing, float farVis, inout vec3 sunShare,
		out float through) {
	float reach = sunOpen(sky) * farVis;
	// past the map: a crown's leaves pass about a third of the light on average (the LOD's leaves take the same)
	through = (matIsLeaves(m) ? 0.35 : 1.0) * reach;
#ifdef WELKIN_SHADOWS_LIVE
	reach *= farCanopySun(m);
	float fade = shadowFade(playerPos);
	// leaves and plants look the map up on their shaded faces too, for the light through them
	if (fade < 1.0 && (facing > 0.0 || matHas(m, MATF_SSS))) {
		shadowSkyHint = sky;
		ShadowSample sh = shadowSampleW(playerPos, n, lightDirWorld(), matThin(m), matIsWater(m),
			matIsLeaves(m) ? 0.22 : 0.0);
		float gate = sunLeakGate(sky);
		reach = mix(sh.vis * gate, reach, fade);
		vec3 tint = sh.tint;
		if (sh.underWater && !matHas(m, MATF_SSS) && facing > 0.0) {
			// under water the light comes down steeper than it stands in the sky (bent at the surface), so a lake bed
			// under a low sun takes it far more squarely than the grazing angle above says (round 2)
			vec3 l = lightDirWorld();
			vec3 lr = vec3(l.xz / 1.33, 0.0).xzy;
			lr.y = sqrt(max(1.0 - dot(lr.xz, lr.xz), 0.0));
			tint *= clamp(sunFacing(dot(n, lr)) / facing, 1.0, 4.0);
		}
		sunShare *= mix(tint, vec3(1.0), fade);
		// what passes the casters round the point: the gaps whole, about a block of leaves 0.45, three a tenth
		float pass = (1.0 - sh.cover) + sh.cover * exp(-0.8 * sh.depth);
		through = mix(pass * gate, through, fade);
	}
#endif
	return reach;
}

float welkinSunReach(vec3 playerPos, vec3 n, float sky, int m, float facing, inout vec3 sunShare) {
	float through;
	return welkinSunReachEx(playerPos, n, sky, m, facing, 1.0, sunShare, through);
}

// Whether a point needs the far shadows: past the map's 80 % (all of it with the map off), not on the LOD's land
// (which always does).
float farShadowNeed(vec3 playerPos) {
#ifdef WELKIN_SHADOWS_LIVE
	return shadowFade(playerPos);
#else
	return 1.0;
#endif
}
#endif

#endif
