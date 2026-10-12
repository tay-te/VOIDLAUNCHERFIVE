// VOID Welkin: the ambient occlusion (lighting techniques 2.4, workstream B). program/deferred_ao.glsl works out the
// raw term at half resolution into colortex9 (r the visibility, gb the bent normal in the face's frame, a the view
// depth), each pixel turned by the 4x4-tiled lighting noise (lib/noise.glsl); lightAoFetch resolves it for the light
// pass with lib/denoise.glsl's 4x4 resolve (the window always holds the tile's sixteen turns once each, weighted to
// the pixel's own plane and normal), so the result is smooth and grain-free in every frame, with no history, and no
// edge bleeds onto the land behind it or the next face of a block. Only the light is occluded, never the colour: the
// textures stay pixel-crisp.
//
// lib/light.glsl lays it on the sky's fill and the ground's bounce (fully, with the multi-bounce), on the torch light
// (by half), never on the sun; the sky's fill is looked up along the bent normal (lib/sky/irradiance.glsl).

#ifndef WELKIN_AO
#define WELKIN_AO

// colortex9 (the raw term, at half resolution: size.buffer.colortex9 in shaders.properties), read by Iris from the
// light pass, which always runs
/*
const int colortex9Format = RGBA16F;
const bool colortex9Clear = false;
*/

// how far round a point the occlusion looks (blocks): a block or two, the scale of Minecraft's forms (a step, an
// eave, a wall's foot); wider in a crown, so its heart sinks into its own shade; narrower in a tuft
#define AO_RADIUS 1.75
#define AO_RADIUS_LEAVES 3.0
#define AO_RADIUS_PLANT 1.0
// the widest it looks on screen, full-resolution pixels (near the eye the radius is cut to this)
#define AO_MAX_PX 96.0
// past a few dozen blocks the radius grows with the distance: it never looks narrower on screen than this (full-
// resolution pixels at 900 lines; scaled with the screen), so the middle and far land, the LOD's too, take an
// occlusion at their own scale (hollows and terrace steps a hundred blocks off, the floor of a far wood, a valley)
#define AO_WIDE_PX 40.0
// no occlusion past this view distance (blocks), where the haze has the land; it fades out over the last half
#define AO_FAR 4096.0
// how much thinner than it looks something standing out in front of a point along the view counts
#define AO_THICKNESS 0.5
// slices and depth reads per side of each slice (AO_QUALITY 1 or 2)
#if AO_QUALITY >= 2
#define AO_SLICES 3
#define AO_STEPS 6
#else
#define AO_SLICES 2
#define AO_STEPS 5
#endif

// The face's own frame for world normal n: two tangents and the normal (columns). The same for every pixel of a face
// (block faces are axis-aligned), so the bent normals' tangent parts can be averaged across it.
mat3 aoFrame(vec3 n) {
	vec3 t = normalize(abs(n.y) < 0.9 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
	return mat3(t, cross(n, t), n);
}

// ---- The sun past the shadow map (AO_SUN_FAR): the occlusion's visibility and bent normal read as a cone of open sky
// (ambient aperture lighting, Oat and Sander 2007; the cone of GTAO's specular occlusion, Jimenez et al. 2016): a
// cosine-weighted visibility v is a cone round the bent normal with sin^2 of its half-angle = v. Where the sun stands
// well outside that cone (the floor between a far wood's crowns, the shaded side of a gully, a hollow's far wall at a
// low sun), the sun is taken off, softly, over AO_SUN_SOFT radians past the cone's edge (plus AO_SUN_MARGIN, so open
// ground with a little relief keeps its sun to the horizon). Only where the shadow map does not reach (past it and on
// the LOD's land), where nothing else gives the land's own shade; never inside the map.
#define AO_SUN_MARGIN 0.12
#define AO_SUN_SOFT 0.35

float aoSunCone(float vis, vec3 bentN, vec3 l) {
	float th = asin(sqrt(clamp(vis, 0.0, 1.0)));
	float a = acos(clamp(dot(bentN, l), -1.0, 1.0));
	return 1.0 - smoothstep(th + AO_SUN_MARGIN, th + AO_SUN_MARGIN + AO_SUN_SOFT, a);
}

#if defined AMBIENT_OCCLUSION && !defined WELKIN_AO_PASS
#include "/lib/denoise.glsl"
#ifndef WU_colortex9
#define WU_colortex9
uniform sampler2D colortex9;
#endif
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif

struct AoSample {
	float vis;   // the visibility, 1 for none (where it covers: lib/light.glsl fades it out by the cover)
	vec3 bend;   // the bent normal minus the normal the pass worked with (world), times the cover
	float cover; // how far the occlusion covers here: 1 near, fading out by AO_FAR
};

// The resolved occlusion at full-resolution pixel p (view position v, world normal n, material m).
AoSample lightAoFetch(ivec2 p, vec3 v, vec3 n, int m) {
	AoSample a;
	a.vis = 1.0;
	a.bend = vec3(0.0);
	float z = -v.z;
	a.cover = 1.0 - smoothstep(0.5 * AO_FAR, AO_FAR, z);
	if (a.cover <= 0.0) {
		a.cover = 0.0;
		return a;
	}
	bool plant = matHas(m, MATF_SSS) && !matIsLeaves(m);
	// plants were worked out as facing up (program/deferred_ao.glsl); their own quads' normals mean little, so the
	// resolve matches them on the level plane through the point, without the normal test
	vec3 nA = plant ? vec3(0.0, 1.0, 0.0) : n;
	vec3 vn = normalize(mat3(gbufferModelView) * nA);
	ivec2 q = p / 2;
	vec4 r = resolveSpatial4x4(colortex9, q, 2, v, vn, plant ? vec3(0.0) : n, colortex1);
	vec3 t = r.a > 0.02 ? r.rgb : texelFetch(colortex9, clamp(q, ivec2(0), textureSize(colortex9, 0) - 1), 0).rgb;
	a.vis = clamp(t.x, 0.0, 1.0);
	vec2 tb = t.yz;
	float l2 = dot(tb, tb);
	if (l2 > 1.0) {
		tb /= sqrt(l2);
		l2 = 1.0;
	}
	mat3 fr = aoFrame(nA);
	vec3 bent = fr[0] * tb.x + fr[1] * tb.y + fr[2] * sqrt(1.0 - l2);
	a.bend = (bent - nA) * a.cover;
	return a;
}
#endif

#endif
