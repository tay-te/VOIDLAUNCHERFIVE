// VOID Welkin: far shadows. Past the shadow map's reach (and on the LOD's land, which never draws into it) the sun's
// shadows are found on the screen: from the lit point, a short ray toward the light is stepped through the depth
// buffers (the chunks' where they drew, the LOD's elsewhere), and the point is in shadow where something on screen
// stands in front of the ray. So the houses and trees of a village 100-200 blocks off throw their long evening
// shadows across the fields, a ridge darkens the slope behind it, and the far hills shade their own valleys.
//
// The look: the ray's steps are fixed (no jitter, no noise), packed near the point and spreading out along the ray, so
// a caster's shadow is sharp at its foot and its far end softens a little. Each step's occlusion fades in over a depth
// margin (no hard on/off per pixel) and the ray reaches further from farther points, as a far pixel covers more land.
// What is off the screen casts nothing here; what is behind something nearer is not taken for a caster (a thickness
// test: the ray must pass within a few blocks behind the surface it meets).
//
// Cost: FAR_SHADOW_STEPS depth reads per lit pixel past the map, whatever the distance (the ray's length is in blocks,
// its steps a fixed count), so it grows with the screen, not with the view distance.

#ifndef WELKIN_FARSHADOW
#define WELKIN_FARSHADOW

#include "/lib/depth.glsl"

#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

const int FAR_SHADOW_STEPS = 14;

// A depth read back to view distance (-z) through its own projection's inverse.
float fsLinear(float d, mat4 inv) {
	float z = d * 2.0 - 1.0;
	return -(inv[2][2] * z + inv[3][2]) / (inv[2][3] * z + inv[3][3]);
}

// How far from the eye (view -z) the nearest opaque surface at texel q is: the chunks' where they drew, else the LOD's,
// else far away.
float fsSceneDepth(ivec2 q) {
	float d0 = texelFetch(depthtex0, q, 0).r;
	if (d0 < 1.0) {
		return fsLinear(d0, gbufferProjectionInverse);
	}
#ifdef DISTANT_HORIZONS
	float dh = texelFetch(dhDepthTex0, q, 0).r;
	if (dh < 1.0) {
		return fsLinear(dh, dhProjectionInverse);
	}
#endif
	return 1e6;
}

// What of the light reaches a point past the map, 0..1: v its view-space position, vn the face's view-space normal,
// vl the view-space direction to the light. jitter (0..1] moves every step along the ray by that share of a step:
// 1 gives the fixed steps (the light pass without the shadow buffers); deferred_shadow passes the lighting noise,
// and the resolve turns the steps' hard on/off into a graded edge. longFade: how much the low sun's long shadows thin
// with their length (lib/shadow.glsl shadowLongFade's strength now; 0 for none).
float farShadowRay(vec3 v, vec3 vn, vec3 vl, float jitter, float longFade) {
	float len = length(v);
	// the ray: about a third of the distance (a far pixel covers more land, and the far hills cast long), 8..192 blocks
	float reach = clamp(len * 0.3, 8.0, 192.0);
	vec3 o = v + vn * (0.04 + 0.004 * len);
	vec2 size = vec2(viewWidth, viewHeight);
	float occ = 0.0;
	for (int i = 0; i < FAR_SHADOW_STEPS; i++) {
		float f = (float(i) + jitter) / float(FAR_SHADOW_STEPS);
		float t = reach * f * f;
		vec3 p = o + vl * t;
		if (p.z > -0.3) {
			break; // the ray came round in front of the eye
		}
		vec4 c = gbufferProjection * vec4(p, 1.0);
		vec2 uv = c.xy / c.w * 0.5 + 0.5;
		if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
			break; // off the screen: nothing known casts here
		}
		float rz = -p.z;
		float behind = rz - fsSceneDepth(ivec2(uv * size));
		// a margin for the depth buffers' own steps on grazing land, growing with the distance
		float margin = 0.12 + 0.005 * rz;
		// how far behind a surface the ray may pass and still be under it: a block or two near the point, more
		// along the ray (the steps spread out) and far off
		float thick = 2.0 + 0.6 * t + 0.02 * rz;
		float o1 = smoothstep(margin, 2.5 * margin, behind) * (1.0 - smoothstep(thick, 1.5 * thick, behind));
		occ = max(occ, o1 * (1.0 - longFade * smoothstep(56.0, 144.0, t)));
		if (occ > 0.995) {
			break;
		}
	}
	return 1.0 - occ;
}

float farShadow(vec3 v, vec3 vn, vec3 vl) {
	return farShadowRay(v, vn, vl, 1.0, 0.0);
}

// ---- Contact shadows (rebuild workstream A): a short ray toward the light through the chunks' depth, for what the
// shadow map is too coarse to see: a grass blade shading its own tuft, the lip of a stair, a small gap at mid range.
// About 0.25 blocks near the eye, growing to 1 block by 48, fading out from 32 to 48 (further out it found grass and
// block edges as dark specks on a field: the map's 1.5-texel floor covers that range); CONTACT_STEPS steps whose
// start the lighting noise moves
// (jitter 0..1), resolved with the map's visibility. The same thickness test as the far ray, tighter: only something
// that stands just in front of the ray counts. v, vn, vl as farShadowRay. 1: nothing in the way.
const int CONTACT_STEPS = 8;

float contactShadow(vec3 v, vec3 vn, vec3 vl, float jitter) {
	float len = length(v);
	float keep = 1.0 - smoothstep(32.0, 48.0, len);
	if (keep <= 0.0) {
		return 1.0;
	}
	float reach = mix(0.25, 1.0, clamp(len / 48.0, 0.0, 1.0));
	vec3 o = v + vn * (0.02 + 0.003 * len);
	vec2 size = vec2(viewWidth, viewHeight);
	float occ = 0.0;
	for (int i = 0; i < CONTACT_STEPS; i++) {
		float t = reach * (float(i) + jitter) / float(CONTACT_STEPS);
		vec3 p = o + vl * t;
		if (p.z > -0.1) {
			break;
		}
		vec4 c = gbufferProjection * vec4(p, 1.0);
		vec2 uv = c.xy / c.w * 0.5 + 0.5;
		if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) {
			break;
		}
		float rz = -p.z;
		float d0 = texelFetch(depthtex0, ivec2(uv * size), 0).r;
		if (d0 >= 1.0) {
			continue;
		}
		float behind = rz - fsLinear(d0, gbufferProjectionInverse);
		float margin = 0.015 + 0.004 * rz;
		float thick = 0.2 + 0.5 * t + 0.01 * rz;
		occ = max(occ, smoothstep(margin, 2.5 * margin, behind) * (1.0 - smoothstep(thick, 1.5 * thick, behind)));
	}
	return 1.0 - occ * keep;
}

#endif
