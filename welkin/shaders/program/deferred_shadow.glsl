// VOID Welkin: deferred_shadow, run as Iris's deferred1 (tools/wrappers.sh maps it). The sun's reach for every lit
// pixel, written raw into colortex10 for deferred_resolve (deferred2) to smooth and the light (deferred3) to read:
//   r  the sun's reach (lib/shadow.glsl welkinSunReachRaw: the near map through the PCSS lookup, fading over its last
//      20 % into the sky-light gate; the far shadows on screen past it and on the LOD's land; the contact shadows)
//   g  what passes the leaves between the light and the point (the glow through leaves and plants)
//   b  the blocks of water the light passed (0 or more: under water), else -1 - the blocks from the point up to the
//      first thing the light meets at its own map texel (the leaf thickness; -1 in the open)
//   a  the view depth, blocks (1e4 where nothing is lit in deferred: the sky, self-lit things)
// The PCSS taps, the far ray's steps and the contact ray's start are moved by the 4x4-tiled lighting noise
// (lib/noise.glsl); r and g are only ever read through the resolve, so the noise never reaches the screen.
// Overworld only, with SHADOWS (shaders.properties); without the map the light pass works the reach out itself.

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
#include "/lib/light.glsl"
#include "/lib/shadow.glsl"
#include "/lib/noise.glsl"
#if defined FAR_SHADOWS && defined WORLD_OVERWORLD
#include "/lib/farshadow.glsl"
#define WELKIN_FAR_SHADOWS
#endif
#if defined CONTACT_SHADOWS && defined WORLD_OVERWORLD && SHADOW_FILTER > 0
#ifndef WELKIN_FAR_SHADOWS
#include "/lib/farshadow.glsl"
#endif
#define WELKIN_CONTACT_SHADOWS
#endif

in vec2 texcoord;

#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif

void shadowTerms(Surface s, vec3 playerPos, vec3 n, float sky, int material, float facing, vec3 lightDir, vec3 vl,
	vec3 vn, vec2 nz, out float reach, out float through, out float travelThick);

/* RENDERTARGETS: 10 */
layout(location = 0) out vec4 outRaw;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	Surface s = surfaceAt(p, texcoord);
	vec4 flags = texelFetch(colortex2, p, 0);
	if (s.sky || flags.x < 0.5) {
		outRaw = vec4(1.0, 1.0, -1.0, 1e4);
		return;
	}
	vec4 g = texelFetch(colortex1, p, 0);
	vec3 n = octDecode(g.xy);
	int material = int(flags.y * 255.0 + 0.5);
	vec3 lightDir = lightDirWorld();
	float facing = sunFacingMat(n, lightDir, material);
	vec3 playerPos = (gbufferModelViewInverse * vec4(s.view, 1.0)).xyz;
	vec2 nz = lightNoise2(p, lightFrame());
#if SHADOW_FILTER == 0
	nz = vec2(0.0);
#endif
	vec3 vl = normalize(shadowLightPosition);
	vec3 vn = mat3(gbufferModelView) * n;

	float reach, through, travelThick;
#if SHADOW_REFERENCE > 0
	// the noise-free reference for the grain check: the average of all sixteen noise values of the tile, per pixel
	reach = 0.0;
	through = 0.0;
	for (int k = 0; k < 16; k++) {
		float rk, tk;
		shadowTerms(s, playerPos, n, g.w, material, facing, lightDir, vl, vn, r2(float(k)), rk, tk, travelThick);
		reach += rk;
		through += tk;
	}
	reach *= 1.0 / 16.0;
	through *= 1.0 / 16.0;
#else
	shadowTerms(s, playerPos, n, g.w, material, facing, lightDir, vl, vn, nz, reach, through, travelThick);
#endif
	outRaw = vec4(reach, through, travelThick, -s.view.z);
}

// The terms for one noise value (deferred_shadow's body).
void shadowTerms(Surface s, vec3 playerPos, vec3 n, float sky, int material, float facing, vec3 lightDir, vec3 vl,
		vec3 vn, vec2 nz, out float reach, out float through, out float travelThick) {
	// past the map, and on the LOD's land, the far shadows found on screen
	float farVis = 1.0;
#ifdef WELKIN_FAR_SHADOWS
	if (facing > 0.0 && sky > 0.5 && (s.fromLod || farShadowNeed(playerPos) > 0.0)) {
#if SHADOW_FILTER == 0
		farVis = farShadow(s.view, vn, vl);
#else
		farVis = farShadowRay(s.view, vn, vl, 1.0 - nz.y, shadowLongFadeStrength(lightDir));
#endif
	}
#endif
	reach = sunOpen(sky) * farVis;
	through = (matIsLeaves(material) ? 0.35 : 1.0) * reach;
	travelThick = -1.0;
	if (!s.fromLod) {
		reach = welkinSunReachRaw(playerPos, n, sky, material, facing, farVis, nz, through, travelThick);
	}
#ifdef WELKIN_CONTACT_SHADOWS
	// what the map is too coarse to see, near the eye and at mid range (not on leaves: the map and the light through
	// them carry a crown)
	if (!s.fromLod && facing > 0.0 && reach > 0.002 && !matIsLeaves(material) && travelThick < 0.0
			&& length(playerPos) < 48.0) {
		// (plants' own blades: two thirds, so a tuft darkens inside without going spotty)
		reach *= mix(1.0, contactShadow(s.view, vn, vl, nz.y), matThin(material) ? 0.65 : 1.0);
	}
#endif
}

#endif
