// VOID Welkin: the light ramp. One function lights everything, in deferred for terrain and the LOD and forward for
// everything else, so near land, far land and entities share the same light.
//
// The light is Minecraft's: sky light, block light and face shading, with the sun (or moon) on top as the strongest,
// directional term. Sky and block light fall off along vanilla's curve, smoothly, eased into LIGHT_BANDS gentle bands
// (lib/common.glsl softBands, SOFT_BANDS of the way): no flat steps, no dither. The sun lights a face by how squarely
// it faces it (Lambert, with a little wrap so a grazing face is not cut off), only where the sky reaches.
//
// The `dither` argument is kept for the callers' signature (and the staged modules'); the ramp ignores it.

#ifndef WELKIN_LIGHT
#define WELKIN_LIGHT

#include "/lib/common.glsl"
// the sky table (prepare, colortex5): ambient and direct light, dark nights and lightning already in
#include "/lib/sky/table.glsl"

#ifndef WU_shadowLightPosition
#define WU_shadowLightPosition
uniform vec3 shadowLightPosition; // the sun by day, the moon by night, in view space
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#ifndef WU_sunAngle
#define WU_sunAngle
uniform float sunAngle;           // 0 sunrise, 0.25 noon, 0.5 sunset
#endif
#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#ifndef WU_nightVision
#define WU_nightVision
uniform float nightVision;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif

// How high the sun stands: -1..1.
float sunHeight() {
	return sin(sunAngle * 6.2831853);
}

// The day share of the light, 0 at night to 1 by day: a time fade, not a screen gradient.
float dayAmount() {
	return clamp(sunHeight() * 5.0 + 0.35, 0.0, 1.0);
}

vec3 lightDirWorld() {
	return normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
}

// Vanilla's light curve for a level in [0, 1] (darker mids than linear), eased into the ramp's soft bands.
float rampLevel(float level) {
	float l = clamp(level, 0.0, 1.0);
	l = l / (3.2 - 2.2 * l);
	return softBands(l, float(LIGHT_BANDS));
}

// The old name, for callers not yet moved over; the dither threshold is ignored.
float rampStep(float level, float dither) {
	return rampLevel(level);
}

// How much of the sun (or moon) a face gets from its direction: Lambert with a slight wrap, so a face turned just
// past edge-on to a low sun fades out over a few degrees instead of switching off.
float sunFacing(float ndl) {
	return clamp((ndl + 0.08) / 1.08, 0.0, 1.0);
}

// Whether the sky reaches a place enough for the sun to: from sky light 0.55 (a few blocks under a roof edge or deep
// in a wood) to fully open at 0.93. Stands in for the shadow map (M6) under roofs and canopies; smooth, no step.
float sunOpen(float skyLevel) {
	return smoothstep(0.55, 0.93, skyLevel);
}

// Face shading as Minecraft does it, continuous for entity normals: top 1.0, north/south 0.8, east/west 0.6,
// bottom 0.6.
float faceShade(vec3 n) {
	return clamp(0.8 + 0.2 * n.y - 0.2 * abs(n.x), 0.5, 1.0);
}

// The lit colour of `albedo` with world normal `n`, light map levels `lm` (x block, y sky, both 0..1), emission
// 0..1 and the (ignored) dither threshold. sunShare: what of the direct light reaches the face, as a colour (cloud shadow,
// shadow-map visibility and the tint through glass or water, the season's sun tint); ambientTint: the season's shade
// tint. welkinLight() passes 1 for both.
vec3 welkinLightEx(vec3 albedo, vec3 n, vec2 lm, float emission, float dither, vec3 sunShare, vec3 ambientTint) {
	float face = faceShade(n);
	float block = rampLevel(lm.x);
	vec3 blockCol = vec3(1.00, 0.80, 0.56) * 1.15;
#if defined WORLD_NETHER
	vec3 ambient = vec3(0.52, 0.40, 0.34) * face;
	vec3 direct = vec3(0.0);
#elif defined WORLD_END
	vec3 ambient = vec3(0.44, 0.40, 0.52) * face;
	vec3 direct = vec3(0.0);
#else
	float sky = rampLevel(lm.y);
	// the day/night curve x Gloam's gameplay sky light (dark nights) x rain and overcast, plus lightning
	vec3 ambient = skyAmbientLight() * sky * face * ambientTint;

	// the sun (or the moon) on faces turned to it, only where the sky reaches (sun or moon as shadowLightPosition,
	// its strength through the cloud cover from the sky table)
	vec3 lightCol = skyDirectLight();
	float lit = sunFacing(dot(n, lightDirWorld()));
	vec3 direct = lightCol * lit * sunOpen(lm.y) * sunShare;
#endif
	vec3 light = ambient + direct + blockCol * block * BLOCKLIGHT_STRENGTH;
	light = max(light, vec3(0.045)); // never quite black: cave walls still read
	light = mix(light, max(light, vec3(0.55)), nightVision);
	return albedo * light + albedo * emission;
}

vec3 welkinLight(vec3 albedo, vec3 n, vec2 lm, float emission, float dither) {
	return welkinLightEx(albedo, n, lm, emission, dither, vec3(1.0), vec3(1.0));
}

#endif
