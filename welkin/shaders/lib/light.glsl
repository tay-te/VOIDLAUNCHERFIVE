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
// ---- Light: ambient (B): the sky's irradiance from the dome (row 13 of the table)
#include "/lib/sky/irradiance.glsl"

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
	// (round 3: 1.9, and the torch a third stronger below: a lamp's glow on a wall reads at dusk and at night as it did
	// before the linear light, the flame still orange and the pool still a pool)
	return pow(rampLevel(level), 1.9);
}

// A torch's light, linear: orange at the source, a little paler as it spreads (round 1: a little stronger and wider at
// levels 4..10, so a lamp's pool reads cosy and a village's lamps glow).
vec3 torchColour(float lv) {
	return mix(vec3(1.66, 1.03, 0.50), vec3(1.86, 0.98, 0.34), lv * lv);
}

// Sunlight that filters down through a canopy or bounces in under an overhang: a share of the direct light's colour,
// with no direction, in the shade of places partly open to the sky (sky light about 6..13: under leaves, beneath a
// roof's edge). Out in the open shade (sky light 15) it is 0, so a house's or a cliff's shadow keeps its contrast; in a
// cave it is 0. It only fills the part of the face the sun does not light already.
// With the shadow map on, the sun's own patches through the gaps carry the light under a wood, so the leak is less
// (the floor's shade stays deep between the patches).
float canopyLeak(float skyLevel) {
#if defined SHADOWS && defined WORLD_OVERWORLD
	const float leak = 0.035;
#else
	const float leak = 0.12;
#endif
	return leak * smoothstep(0.25, 0.55, skyLevel) * (1.0 - smoothstep(0.84, 0.97, skyLevel));
}

// How deep a place lies under a canopy or an overhang by day, 0..1: partly open to the sky (sky light about 9..13) and
// out of the sun. There the sky's fill is cut further (round 1: the forest floor between the sun patches, and a
// crown's inner leaves, sink into deep, readable shade, about sRGB 10..12 at their darkest, instead of an even mid
// green), while the patches the sun reaches keep it. In the open (sky light 15) and in caves (the ramp is dark
// already) it is 0; by night it fades out, so moonlit woods keep their level.
float canopyShade(float skyLevel, float sunReach) {
#ifdef WORLD_OVERWORLD
	float under = smoothstep(0.35, 0.6, skyLevel) * (1.0 - smoothstep(0.82, 0.97, skyLevel));
	return under * (1.0 - clamp(sunReach, 0.0, 1.0)) * smoothstep(-0.02, 0.12, sunHeight());
#else
	return 0.0;
#endif
}

// How much the hour is the low sun's, 0..1: 1 from the sun's last few degrees to its setting, 0 once it stands about
// 17 degrees up and at night.
float lowSunShare() {
	float h = sunHeight();
	return (1.0 - smoothstep(0.08, 0.3, h)) * smoothstep(-0.1, 0.0, h);
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

// The same for a material (plumbing's class, M_*): a plant's crossed quads (double-sided, their normal means little)
// take the light on both sides, more where a quad faces it. A leaf block is a cube of small leaves turned every way,
// so every face takes a good share of the sun where the light reaches it (the shadow map, softened, decides that:
// inner faces of a crown sit in their neighbours' shade), the faces turned to it most; the shaded faces also get
// the light that comes through the leaves (welkinGlow).
float sunFacingMat(vec3 n, vec3 l, int m) {
	float ndl = dot(n, l);
	if (matHas(m, MATF_SSS)) {
		if (matIsLeaves(m)) {
			// (round 3: half the light on any face, not 0.36: a crown's sides read sunlit green where the map lets the
			// sun reach them, as a cube of leaves turned every way does)
			return 0.5 + 0.5 * sunFacing(ndl);
		}
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
	// (round 3: what passes is a share, never more than all of it; and the moon's light through leaves is a faint
	// silver rim, not a glow)
	through = clamp(through, 0.0, 1.0) * (sunAngle < 0.5 ? 1.0 : 0.35);
	// round 1: a rim, not a glow: mostly when the eye looks toward the light (a narrow forward lobe), at most half the
	// direct light, so back-lit crowns read as warm-rimmed silhouettes instead of neon-lit ones
	float c = clamp(dot(v, l), 0.0, 1.0);
	float c2 = c * c;
	float lobe = 0.06 + 0.94 * c2 * c2;
	if (matIsLeaves(m)) {
		// at a low sun the light through a crown spreads wider (the long path through the leaves scatters it), so a
		// crown seen from the side glows gold at dawn and at the golden hour, as the sun's colour makes it
		float low = 1.0 - smoothstep(0.06, 0.3, l.y);
		lobe = mix(lobe, 0.4 + 0.6 * c2, low);
		// the faces turned away from the light (half on an edge-on face)
		return 0.7 * lobe * through * clamp(0.5 - dot(n, l), 0.0, 1.0);
	}
	return 0.32 * lobe * through;
}

// How far the sky's fill takes a face as facing up, 0..1 (round 3: deferred sets it for plants' crossed quads, whose
// normal means little: a grass blade or a flower sees the open sky above it, so in the shade it reads as light as the
// ground's top, not as dark as a wall; the land's shade then shows its tops and tufts against the dark sides).
float lightFillUp = 0.0;

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

// ---- Light: ambient (B) -----------------------------------------------------------------------------------------
// How much of Minecraft's corner shading (ambient occlusion, separateAo: colortex2.w) darkens the sky's fill and the
// torch light where the pack's own occlusion does not reach (past AO_FAR, on the LOD, in forward programs, with
// AMBIENT_OCCLUSION off), as a power of it in linear light, never below LIGHT_AO_FLOOR of the fill (round 2's values:
// that light is as it was). It never darkens the sun: a corner in the sun is in the sun.
const float LIGHT_AO_POW = 1.8;
const float LIGHT_AO_FLOOR = 0.2;
// Where the pack's occlusion covers (program/deferred_ao.glsl, lib/ao.glsl: ground-truth ambient occlusion, graded by
// the real geometry within a block or two), Minecraft's corner shading stays only as a gentle partner: this share of
// it, linear.
const float AO_VANILLA = 0.35;
// The darkest the fill gets in the deepest corner the occlusion finds (after the multi-bounce, which keeps pale stone
// lighter than dark dirt), so a shaded pocket reads deep, never black.
const float AO_FLOOR = 0.12;
// How much of Minecraft's face shading (top 1, sides 0.8 and 0.6, bottom 0.5) stays on the sky's fill, by day and at
// night: the sky's own irradiance (lib/sky/irradiance.glsl) gives each orientation its light, and this keeps the
// game's look on top (a block's sides a step below its top), as round 3 had it, so the shade side of things stays
// darker than open ground in shade and the land keeps its form
const float AMB_FACE_SHADE = 0.45;
const float AMB_FACE_SHADE_NIGHT = 0.45;
// How much of the sky's hue by orientation the fill takes (its brightness by orientation is taken whole): at noon a
// wall facing the pale horizon sees a whiter sky than a top under the deep zenith, at dusk one facing the afterglow a
// warmer one; half of that keeps the shade on walls a clear, cool sky-blue while dusk's faces still warm and cool.
const float AMB_SKY_HUE = 0.5;

// Set by deferred for each pixel before it lights it (forward programs leave them): how far the occlusion covers the
// pixel (0 none .. 1 full; it fades out by AO_FAR), and the bend it gives the normal the sky's light is looked up along
// (the bent normal, the direction of least occlusion, minus the normal the occlusion pass worked with), world space.
float lightAoCover = 0.0;
vec3 lightAoBend = vec3(0.0);

// The occlusion of the fill with its multi-bounce (Jimenez, Wu, Pesce and Jarabo 2016, "Practical Real-Time
// Strategies for Accurate Indirect Occlusion": a cubic fit in the visibility per albedo): light that bounces inside a
// pale corner comes back, so pale stone loses less of its fill to a corner than dark dirt, per channel. a: linear
// albedo.
vec3 aoMultiBounce(float v, vec3 a) {
	vec3 ka = 2.0404 * a - 0.3324;
	vec3 kb = -4.7951 * a + 0.6417;
	vec3 kc = 2.7552 * a + 0.6903;
	return max(vec3(v), ((v * ka + kb) * v + kc) * v);
}

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
// colour twice over (light through a leaf is a deeper, warmer green than light off it). contact: the ambient
// occlusion's visibility (lib/ao.glsl lightAoFetch, 1 for none; not faded), with lightAoCover and lightAoBend: it darkens the
// sky's fill and the ground's bounce fully and the torch light by half, never the sun (the shadows carry that).
vec3 welkinLightGlow(vec3 albedo, vec3 n, vec2 lm, float emission, vec3 sunShare, vec3 ambientTint, float sunReach,
		float facing, float ao, float glow, float contact) {
	vec3 a = toLinear(albedo);
	// ---- Light: ambient (B): the occlusion of the fill and the torch light
	// Minecraft's corner shading: round 2's depth where the pack's occlusion does not reach, a gentle partner where it
	// does; the pack's occlusion with its multi-bounce on top
	// (the two blend as wholes over the occlusion's fade, so the corners keep their depth through it)
	float aoV = clamp(ao, 0.0, 1.0);
	float aoFar = mix(LIGHT_AO_FLOOR, 1.0, pow(aoV, LIGHT_AO_POW));
	float aoNear = mix(1.0, aoV, AO_VANILLA);
	vec3 aoPack = aoMultiBounce(pow(clamp(contact, 0.0, 1.0), AO_STRENGTH), a);
	vec3 aoFill = mix(vec3(aoFar), max(aoNear * aoPack, vec3(AO_FLOOR)), lightAoCover);
	vec3 aoBlock = mix(vec3(aoFar), aoNear * mix(vec3(1.0), aoPack, 0.5), lightAoCover);
	float lv = rampLevel(lm.x);
	vec3 block = torchColour(lv) * (1.35 * rampBlockLin(lm.x) * blockLightDaylight(lm.y) * pow(BLOCKLIGHT_STRENGTH, 2.2));
#if defined WORLD_NETHER
	vec3 ambient = toLinear(vec3(0.52, 0.40, 0.34) * faceShade(n));
	vec3 direct = vec3(0.0);
#elif defined WORLD_END
	vec3 ambient = toLinear(vec3(0.44, 0.40, 0.52) * faceShade(n));
	vec3 direct = vec3(0.0);
#else
	// ---- Light: ambient (B): the sky's fill from the dome (lib/sky/irradiance.glsl), along the bent normal where the
	// occlusion gives one: each orientation takes the light of the sky it faces (warm toward the bright low sky round
	// the sun, the zenith's blue overhead, the rose band opposite a low sun), an upward face in the open exactly the
	// table's tuned fill; the ground's bounce below; a touch of Minecraft's face shading
	vec4 fill = skyLandFill();
	vec3 nf = lightFillUp > 0.0 ? normalize(mix(n, vec3(0.0, 1.0, 0.0), lightFillUp)) : n;
	vec3 ns = normalize(nf + lightAoBend);
	vec3 skyE = skyIrradiance(ns);
	skyE = mix(vec3(lumaLin(skyE)), skyE, AMB_SKY_HUE);
	vec3 hemi = fill.rgb * skyE + skyLandBounce() * (0.5 - 0.5 * nf.y);
	// (a plant's thin blades take the sky on both their faces and pass some through: up to 1.45x a top's fill, so in the
	// shade a meadow's tufts and stalks read light against the ground, as dry grass does at dusk)
	hemi = hemi * mix(1.0, faceShade(nf), mix(AMB_FACE_SHADE, AMB_FACE_SHADE_NIGHT, skyNightStep())) * (1.0 + 0.64 * lightFillUp);
	// (round 3: the fill under a canopy cut by 0.8, not 0.68: the forest floor between the sun patches sinks deeper)
	vec3 ambient = hemi * rampSkyLin(lm.y) * ambientTint * (1.0 - 0.8 * canopyShade(lm.y, sunReach));
	// the short-range bounce (round 1): a shaded face among sunlit surfaces like itself takes back a share of the sun
	// in its own colour, more on walls than on tops, so its light follows its albedo: the shade side of pale stone or
	// sand reads a soft light grey (its colour half desaturated, so the bounce does not saturate the shade) instead of a near-black square beside a sunlit face, while grass and dirt (dark,
	// so they bounce little) keep their deep shade. It fades with the sun's height (level ground catches less) and
	// under cover.
	// round 2: mostly on walls (a level top sees the open sky, little of its sunlit neighbours: a fifth of it, so the
	// shade on open ground stays deep), and in a near-neutral colour (the albedo a quarter, the sun's colour half), so
	// shaded pale stone reads cool light grey and not a warm, muddy brown-grey: the sky's blue carries its hue.
	vec3 dl = skyLandDirect();
	// (only pale surfaces take it: a pale cliff stands among pale neighbours, a dirt bank among dark ones, so its shade
	// stays a deep earth brown and open ground keeps its sun-to-shade contrast)
	ambient += mix(vec3(lumaLin(a)), a, 0.25) * mix(vec3(lumaLin(dl)), dl, 0.5) * (LAND_SELF_BOUNCE
		* smoothstep(0.1, 0.45, lumaLin(a))
		* max(lightDirWorld().y, 0.0) * (1.0 - 0.8 * max(n.y, 0.0))
		* rampSkyLin(lm.y) * (1.0 - canopyShade(lm.y, sunReach)));
	// the sun (or the moon) on faces turned to it, where it reaches; under a canopy a little of it filters through
	float lit = facing * sunReach;
	vec3 direct = skyLandDirect() * (lit + canopyLeak(lm.y) * (1.0 - lit)) * sunShare;
	// through the leaf (round 1): the light keeps the sun's colour (amber at a low sun) and takes only half the leaf's
	// saturation, so a back-lit crown rims warm instead of glowing lime
	vec3 through = vec3(0.0);
	if (glow > 0.0) {
		vec3 aT = mix(vec3(lumaLin(a)), a, 0.65);
		through = aT * skyLandDirect() * sunShare * glow;
	}
#endif
	vec3 light = ambient * aoFill + block * aoBlock + direct;
	light = max(light, vec3(LAND_FLOOR)); // never quite black: cave walls still read
	light = mix(light, max(light, vec3(0.27)), nightVision);
#if defined WORLD_NETHER || defined WORLD_END
	vec3 lin = a * light;
#else
	vec3 lin = a * light + through;
#endif
	// the highlights' pull toward white only while the sun stands high (a face in the low sun keeps its amber)
	toneHighlightDesat = LAND_HIGHLIGHT_DESAT * smoothstep(0.15, 0.4, sunHeight());
	vec3 c = welkinTone(lin);
	if (emission > 0.0) {
		// a glowing texel keeps its hue into white (lib/tone.glsl welkinToneEmit): a flame stays yellow
		c = mix(c, max(c, welkinToneEmit(lin + a * (emission * LAND_EMIT))), clamp(emission / 0.85, 0.0, 1.0));
	}
	return c;
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
