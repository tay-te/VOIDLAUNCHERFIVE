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
// vl the view-space direction to the light.
float farShadow(vec3 v, vec3 vn, vec3 vl) {
	float len = length(v);
	// the ray: about a third of the distance (a far pixel covers more land, and the far hills cast long), 8..192 blocks
	float reach = clamp(len * 0.3, 8.0, 192.0);
	vec3 o = v + vn * (0.04 + 0.004 * len);
	vec2 size = vec2(viewWidth, viewHeight);
	float occ = 0.0;
	for (int i = 0; i < FAR_SHADOW_STEPS; i++) {
		float f = (float(i) + 1.0) / float(FAR_SHADOW_STEPS);
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
		occ = max(occ, smoothstep(margin, 2.5 * margin, behind) * (1.0 - smoothstep(thick, 1.5 * thick, behind)));
		if (occ > 0.995) {
			break;
		}
	}
	return 1.0 - occ;
}

#endif
