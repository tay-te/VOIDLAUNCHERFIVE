// VOID Welkin: the light ramp. One function lights everything, in deferred for terrain and the LOD and forward for
// everything else, so near land, far land and entities share the same light.
//
// The light is Minecraft's: sky light, block light and face shading, with the sun (or moon) on top as the strongest,
// directional term. Sky and block light fall off along vanilla's curve, smoothly, eased into LIGHT_BANDS gentle bands
// (lib/common.glsl softBands, SOFT_BANDS of the way): no flat steps, no dither. The sun lights a face by how squarely
// it faces it (Lambert, with a little wrap so a grazing face is not cut off), where the shadow map (lib/shadow.glsl)
// says it reaches, or past the map's reach where the sky does.
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

// The wrap of the direct light: slight while the light stands high, wider as it sinks, so at the golden hour the tops
// of things still catch a warm share of a sun that only grazes them (a stylized lift, smooth with the sun's height).
float sunWrap() {
	return mix(0.08, 0.25, 1.0 - smoothstep(0.06, 0.4, lightDirWorld().y));
}

// How much of the sun (or moon) a face gets from its direction: Lambert with that wrap, so a face turned just past
// edge-on to a low sun fades out over a few degrees instead of switching off.
float sunFacing(float ndl) {
	float w = sunWrap();
	return clamp((ndl + w) / (1.0 + w), 0.0, 1.0);
}

// The glow of the sky round a low sun, lighting what faces it, in the light or not: 0 by day and night, up from the
// late afternoon to just past sunset. Returns the light to add to the ambient (before the sky-light level).
vec3 sunSideGlow(vec3 n) {
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	float hour = (1.0 - smoothstep(0.06, 0.4, sunW.y)) * smoothstep(-0.12, 0.0, sunW.y);
	vec2 toSun = normalize(sunW.xz + vec2(1e-5));
	float side = clamp((dot(n.xz, toSun) + 0.25) / 1.25, 0.0, 1.0) * (1.0 - 0.5 * abs(n.y));
	return skyHorizon(true) * (0.14 * hour * side);
}

// The same for a material (plumbing's class, M_*): leaves let a little light through to their shaded faces, and a
// plant's crossed quads (double-sided, their normal means little) take the light on both sides, more where a quad
// faces it. The shadow map decides where the light actually reaches.
float sunFacingMat(vec3 n, vec3 l, int m) {
	float ndl = dot(n, l);
	if (matHas(m, MATF_SSS)) {
		if (matIsLeaves(m)) {
			return mix(sunFacing(ndl), 1.0, 0.3);
		}
		return 0.62 + 0.38 * abs(ndl);
	}
	return sunFacing(ndl);
}

// Whether a material is a plant's thin quad (for the shadow lookup, which then steps toward the light).
bool matThin(int m) {
	return matHas(m, MATF_SSS) && !matIsLeaves(m);
}

// Whether the sky reaches a place enough for the sun to: from sky light 0.55 (a few blocks under a roof edge or deep
// in a wood) to fully open at 0.93. Stands in for the shadow map past its reach (the far chunks, the LOD, LOW) and
// in the programs that do not read it; smooth, no step.
float sunOpen(float skyLevel) {
	return smoothstep(0.55, 0.93, skyLevel);
}

// Where the shadow map covers a place, it decides; only a gate this loose stays, so a sealed cave the map misses
// (a chunk outside it, a culled section) cannot leak sunlight.
float sunLeakGate(float skyLevel) {
	return smoothstep(0.0, 0.3, skyLevel);
}

// Face shading as Minecraft does it, continuous for entity normals: top 1.0, north/south 0.8, east/west 0.6,
// bottom 0.6.
float faceShade(vec3 n) {
	return clamp(0.8 + 0.2 * n.y - 0.2 * abs(n.x), 0.5, 1.0);
}

// The balance of the overworld's light: the sun (or moon) a little stronger than the sky table's raw value and the
// sky's ambient a little weaker, so a face in the sun stands clearly out of the shade without the land getting
// brighter overall (a top face in the noon sun is lit about as before; a shaded one about 0.45 of it).
const float LIGHT_SUN_GAIN = 1.3;
const float LIGHT_SKY_GAIN = 0.88;

// The lit colour of `albedo` with world normal `n`, light map levels `lm` (x block, y sky, both 0..1) and emission
// 0..1. sunShare: what of the direct light reaches the face, as a colour (cloud shadow, shadow-map visibility and the
// tint through glass or water, the season's sun tint); ambientTint: the season's shade tint. sunReach: the sky-light
// gate (sunOpen, or the shadow map's loose sunLeakGate where it covers). facing: how squarely the face meets the
// light (sunFacing or sunFacingMat).
vec3 welkinLightCore(vec3 albedo, vec3 n, vec2 lm, float emission, vec3 sunShare, vec3 ambientTint, float sunReach,
		float facing) {
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
	vec3 ambient = (skyAmbientLight() * LIGHT_SKY_GAIN * face + sunSideGlow(n)) * sky * ambientTint;
	// the sun (or the moon) on faces turned to it, where it reaches (sun or moon as shadowLightPosition, its strength
	// through the cloud cover from the sky table)
	vec3 direct = skyDirectLight() * LIGHT_SUN_GAIN * facing * sunReach * sunShare;
#endif
	vec3 light = ambient + direct + blockCol * block * BLOCKLIGHT_STRENGTH;
	light = max(light, vec3(0.045)); // never quite black: cave walls still read
	light = mix(light, max(light, vec3(0.55)), nightVision);
	return albedo * light + albedo * emission;
}

// The light without a shadow map: the sky-light gate stands in for it. The `dither` argument is ignored.
vec3 welkinLightEx(vec3 albedo, vec3 n, vec2 lm, float emission, float dither, vec3 sunShare, vec3 ambientTint) {
	return welkinLightCore(albedo, n, lm, emission, sunShare, ambientTint, sunOpen(lm.y),
		sunFacing(dot(n, lightDirWorld())));
}

vec3 welkinLight(vec3 albedo, vec3 n, vec2 lm, float emission, float dither) {
	return welkinLightEx(albedo, n, lm, emission, dither, vec3(1.0), vec3(1.0));
}

#endif
