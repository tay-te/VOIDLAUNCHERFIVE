// VOID Welkin: the light ramp. One function lights everything, in deferred for terrain and the LOD and forward for
// everything else, so near land, far land and entities share the same light.
//
// The light is Minecraft's: sky light, block light and face shading, with the sun (or moon) on top as the strongest,
// directional term. Since the lighting rebuild (2026-10-12) it is summed in LINEAR light and brought to the screen by
// one filmic curve (lib/tone.glsl): the sun from the sky table's land texels (its colour and strength follow its
// height), the sky's fill as a hemisphere (the sky above, the sunlit ground's bounce below), torches, and the result
// toned per pixel. Sky and block light fall off along vanilla's curve, smoothly, eased into LIGHT_BANDS gentle bands
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
#include "/lib/tone.glsl"

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

// The sky's share of the ambient light for a sky-light level: a gentler curve than vanilla's, so a place a few leaf
// layers or a roof edge away from the open sky (sky light 11..13) still gets most of the sky's fill and shade under a
// wood reads green and brown, not black, while a cave a few blocks in still darkens fast. Eased into the same bands.
float rampSky(float level) {
	float l = clamp(level, 0.0, 1.0);
	l = l / (2.0 - 1.0 * l);
	return softBands(l, float(LIGHT_BANDS));
}

// The same ramps as linear light (the display-space ramps taken to linear, so the falloff reads as before). Block
// light falls off a little more steeply than the sky's, so a torch makes a tighter, warmer pool.
float rampSkyLin(float level) {
	return pow(rampSky(level), 2.2);
}

float rampBlockLin(float level) {
	return pow(rampLevel(level), 2.3);
}

// A torch's light, linear: orange at the source, a little paler as it spreads.
vec3 torchColour(float lv) {
	return mix(vec3(1.52, 0.96, 0.48), vec3(1.76, 0.95, 0.35), lv * lv);
}

// Sunlight that filters down through a canopy or bounces in under an overhang: a share of the direct light's colour,
// with no direction, in the shade of places partly open to the sky (sky light about 6..13: under leaves, beneath a
// roof's edge). Out in the open shade (sky light 15) it is 0, so a house's or a cliff's shadow keeps its contrast; in a
// cave it is 0. It only fills the part of the face the sun does not light already.
// With the shadow map on, the sun's own patches through the gaps carry the light under a wood, so the leak is less
// (the floor's shade stays deep between the patches).
float canopyLeak(float skyLevel) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	const float leak = 0.12;
#else
	const float leak = 0.2;
#endif
	return leak * smoothstep(0.25, 0.55, skyLevel) * (1.0 - smoothstep(0.84, 0.97, skyLevel));
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

// The bright sky round a low sun, lighting what faces it, in the sun or not: the share of the fill that comes from the
// sun's side of the sky (the table's fill.a, from the late afternoon to just past sunset), in the colour of the
// horizon toward the sun. Linear, added to the fill (before the sky-light level).
vec3 sunSideGlow(vec3 n, vec4 fill) {
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec2 toSun = normalize(sunW.xz + vec2(1e-5));
	float side = clamp((dot(n.xz, toSun) + 0.25) / 1.25, 0.0, 1.0) * (1.0 - 0.5 * abs(n.y));
	vec3 warm = toLinear(skyHorizon(true));
	warm /= max(lumaLin(warm), 1e-4);
	return warm * (lumaLin(fill.rgb) * fill.a * 0.65 * side);
}

// The same for a material (plumbing's class, M_*): a plant's crossed quads (double-sided, their normal means little)
// take the light on both sides, more where a quad faces it; leaves take it on the faces turned to it, and their
// shaded faces get the light that comes through them instead (welkinGlow). The shadow map decides where the light
// actually reaches.
float sunFacingMat(vec3 n, vec3 l, int m) {
	float ndl = dot(n, l);
	if (matHas(m, MATF_SSS) && !matIsLeaves(m)) {
		return 0.62 + 0.38 * abs(ndl);
	}
	return sunFacing(ndl);
}

// The light that comes through leaves and plants toward the eye, as a share of the direct light (0 for everything
// else): a leaf's face turned from the sun glows where the sun stands behind it, most when the eye looks toward the
// sun (a forward lobe; a little from every side, as a leaf scatters), and a grass blade or a flower lit from behind
// glows the same way. through: what the leaves between the sun and the face let pass, with the sky-light gate
// (lib/shadow.glsl welkinSunReachEx). v the direction from the eye to the point, n the face's world normal, l the
// light's.
float welkinGlow(vec3 v, vec3 n, vec3 l, int m, float through) {
	if (!matHas(m, MATF_SSS)) {
		return 0.0;
	}
	float c = clamp(dot(v, l), 0.0, 1.0);
	float lobe = 0.25 + 1.5 * c * c * c;
	if (matIsLeaves(m)) {
		// the faces turned away from the light (half on an edge-on face)
		return 1.2 * lobe * through * clamp(0.5 - dot(n, l), 0.0, 1.0);
	}
	return 0.6 * lobe * through;
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

// How much of Minecraft's corner shading (ambient occlusion, separateAo: colortex2.w) darkens the sky's fill and the
// torch light, as a power of it in linear light (2.2 would be vanilla's own depth; a little less, so corners darken
// without going grimy). It never darkens the sun: a corner in the sun is in the sun.
const float LIGHT_AO_POW = 2.3;

// How much of a torch's light still shows, 0.65..1: in the open by day the sun drowns most of it anyway (the light
// adds), so only a little is taken off; a lamp's pool still reads in the shade at noon. Under a roof, in a cave or at
// night it is whole.
float blockLightDaylight(float skyLevel) {
#ifdef WORLD_OVERWORLD
	float sun = smoothstep(0.12, 0.45, sunHeight());
	return 1.0 - 0.35 * sun * smoothstep(0.6, 1.0, skyLevel);
#else
	return 1.0;
#endif
}

// The lit colour of `albedo` with world normal `n`, light map levels `lm` (x block, y sky, both 0..1) and emission
// 0..1, as a display value. sunShare: what of the direct light reaches the face, as a colour (cloud shadow, shadow-map
// visibility and the tint through glass or water, the season's sun tint); ambientTint: the season's shade tint.
// sunReach: the sky-light gate (sunOpen, or the shadow map's loose sunLeakGate where it covers). facing: how squarely
// the face meets the light (sunFacing or sunFacingMat). ao: Minecraft's corner shading, 1 for none. The shadow map, the
// clouds and the facing only ever scale the direct term; the sky's fill and the torch light are the same in the sun
// and the shade.
// glow: the light through leaves and plants (welkinGlow), as a share of the direct light: it takes the leaf's own
// colour twice over (light through a leaf is a deeper, warmer green than light off it). contact: the contact
// occlusion (lib/ao.glsl, 1 for none): it darkens the fill and the torch light, and the sun a little, so plants and
// corners sit in their own small shade even in the sun.
vec3 welkinLightGlow(vec3 albedo, vec3 n, vec2 lm, float emission, vec3 sunShare, vec3 ambientTint, float sunReach,
		float facing, float ao, float glow, float contact) {
	vec3 a = toLinear(albedo);
	float aoFill = pow(clamp(ao, 0.0, 1.0), LIGHT_AO_POW) * contact;
	float lv = rampLevel(lm.x);
	vec3 block = torchColour(lv) * (rampBlockLin(lm.x) * blockLightDaylight(lm.y) * pow(BLOCKLIGHT_STRENGTH, 2.2));
#if defined WORLD_NETHER
	vec3 ambient = toLinear(vec3(0.52, 0.40, 0.34) * faceShade(n));
	vec3 direct = vec3(0.0);
#elif defined WORLD_END
	vec3 ambient = toLinear(vec3(0.44, 0.40, 0.52) * faceShade(n));
	vec3 direct = vec3(0.0);
#else
	// the sky's fill as a hemisphere: the sky above, the ground's bounce below, Minecraft's face shading softened on
	// top (the sun now does the modelling), the bright sky round a low sun on what faces it
	vec4 fill = skyLandFill();
	vec3 hemi = fill.rgb * (0.5 + 0.5 * n.y) + skyLandBounce() * (0.5 - 0.5 * n.y);
	hemi = hemi * mix(1.0, faceShade(n), 0.45) + sunSideGlow(n, fill);
	vec3 ambient = hemi * rampSkyLin(lm.y) * ambientTint;
	// the sun (or the moon) on faces turned to it, where it reaches; under a canopy a little of it filters through
	float lit = facing * sunReach;
	vec3 direct = skyLandDirect() * (lit + canopyLeak(lm.y) * (1.0 - lit)) * sunShare;
	if (glow > 0.0) {
		// through the leaf: its own hue at its own brightness, warmed (the light through chlorophyll is yellow-green)
		vec3 hue = mix(vec3(1.0), min(a / max(lumaLin(a), 0.004), vec3(3.0)), 0.75) * vec3(1.2, 1.05, 0.55);
		direct += skyLandDirect() * sunShare * hue * glow;
	}
#endif
	vec3 light = (ambient + block) * aoFill + direct * mix(1.0, contact, 0.3);
	light = max(light, vec3(LAND_FLOOR)); // never quite black: cave walls still read
	light = mix(light, max(light, vec3(0.27)), nightVision);
	return welkinTone(a * light + a * (emission * LAND_EMIT));
}

vec3 welkinLightFull(vec3 albedo, vec3 n, vec2 lm, float emission, vec3 sunShare, vec3 ambientTint, float sunReach,
		float facing, float ao) {
	return welkinLightGlow(albedo, n, lm, emission, sunShare, ambientTint, sunReach, facing, ao, 0.0, 1.0);
}

vec3 welkinLightCore(vec3 albedo, vec3 n, vec2 lm, float emission, vec3 sunShare, vec3 ambientTint, float sunReach,
		float facing) {
	return welkinLightFull(albedo, n, lm, emission, sunShare, ambientTint, sunReach, facing, 1.0);
}

// What the far land's leaves (the LOD's, and the near chunks' past the shadow map) take of the sun and the sky's fill
// while the near shadow map is on: a crown's own shade, averaged. Near crowns shade their own lower leaves and the
// leaves under them, the far flat tops cannot, so without this the far forest is a brighter, greener band than the
// near forest in front of it. 1 for everything else, and with the shadow map off (then neither has self-shade).
float farCanopySun(int m) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	return matIsLeaves(m) ? 0.56 : 1.0;
#else
	return 1.0;
#endif
}

float farCanopyFill(int m) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	return matIsLeaves(m) ? 0.76 : 1.0;
#else
	return 1.0;
#endif
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
