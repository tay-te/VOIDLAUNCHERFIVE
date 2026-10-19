// VOID Welkin: the land's light model (its strengths) and the display conversions. No uniforms, so any program may
// include it. (HDR stage A, 2026-10-19: the land is no longer toned here. The light pass writes scene-linear light and
// final's one output transform, lib/hdr.glsl, brings everything to the screen; the history below is the old curve's.)
//
// The land (terrain, the LOD, entities, water's body, everything lib/light.glsl lights) is lit in LINEAR light: the
// albedo taken to linear, the sun, the sky's fill, the ground's bounce and the torches summed as light adds, then
// brought to the screen here by one filmic curve, per pixel, with no neighbourhood (no bloom, no adaptation, no TAA):
//
//   exposure  -> a gentle S round mid grey (a deeper toe, livelier mids)  -> a soft shoulder into white
//             -> back to display values
//
// The curve keeps shade where it was, lifts the sunlit half to bright, and rolls a pale face in full sun into near
// white without clipping. The S is on luminance and fades out in the deep dark (nights keep their level); the
// shoulder runs mostly on luminance (keeping the hue: sunlit grass stays green instead of sliding to yellow as its
// green channel saturates first, sunlit sand stays sand-coloured) and a little per channel (so the brightest faces
// still whiten a little, as film does). The sky, the clouds and the haze
// stay in display values as they were; final's own shoulder only touches what is lit past white.
//
// The light model's strengths (what lib/sky/build.glsl writes into the sky table) live here too, so the table and the
// curve are tuned together.

#ifndef WELKIN_TONE
#define WELKIN_TONE

#include "/lib/settings.glsl"

// ---- the light model (linear irradiance on a face square to the light; mid grey albedo is 0.18)
// the noon sun (round 1 of the lighting review: a tenth stronger, so the sun clearly leads)
// (round 3: 2.7, paying for the fill's cut: the noon field's median stays, its sun-to-shade grows past 5:1)
#define LAND_SUN 2.7
// how much stronger the sun stands through the morning and the afternoon (about 15 to 45 degrees up) than its curve
// (the balance step: 0.14, was 0.1: 16:30's field back toward the 120..140 band as its shade deepened)
// (review round 2: 0.3: 16:30 had sunk to a dim grey-green, land luma 72 against the reference packs' 106 to 118)
#define LAND_SUN_SHOULDER_HOURS 0.3
// the open sky's fill on an upward face at noon (round 1: about a quarter less, so the shade is deeper and the sun's
// side carries the form; lib/sky/build.glsl skyLandFillLevel takes it down faster as the sun sinks)
// (round 3: 0.126: open ground's shade about a fifth deeper and a clearer blue)
// (ambient (B): 0.16: the darkness now comes from the occlusion (lib/ao.glsl), so open shade, which sees the whole sky,
// takes back a brighter, sky-blue fill; lib/light.glsl looks it up along each face's bent normal)
// (the balance step, 2026-10-13: 0.14, with the AO and the contrast together: noon's open shade p5 back inside 25..35
// and 16:30's sun to shade past 7:1, the occlusion keeping the corners)
// (review round 2: 0.19 and 0.14 below: the darkest twentieth of a sunlit field was half the reference packs'; the
// sky's fill a little higher, the open shade's colour as it was)
#define LAND_FILL 0.17
// (ambient (B): while the sun is low the fill keeps round 3's level, so the golden hour's long shadows stay deep and cool
// against the amber, and the fill rises to LAND_FILL as the sun climbs past about 17 degrees)
// (golden, 2026-10-16: 0.105, and down to it from a higher sun (about 38 degrees, was 17): the golden hours' shade deeper
// and cooler against the warm light, toward the 6:1 the user asked for at 16:30 to 18:30)
#define LAND_FILL_LOW 0.105
// The open sky's fill on an upward face at noon for the sun's height h (lib/sky/build.glsl skyLand).
float landFillAt(float h) {
	return mix(LAND_FILL_LOW, LAND_FILL, smoothstep(0.12, 0.62, h));
}

// the ground's bounce: the share of the sunlit ground's light that reaches a downward face (round 1: less, so a wall's
// shade side reads the sky's cool blue, warmed a little by the ground, not an olive grey)
// (review round 2: 0.32: the sunlit ground's warm bounce lifts the shade a little, warmly)
#define LAND_BOUNCE 0.32
// the light a sunlit surface throws back onto its own shaded faces and its neighbours' (a cheap short-range bounce),
// as a share of the sun on level ground: pale stone in shade reads a light grey, grass and dirt stay deep
// (round 2: 0.3 on walls, a fifth of it on tops: a shaded wall of pale stone among sunlit stone reads a light cool
// grey, as Hysteria's and Complementary's do, not a dark notch; dark grass and dirt bounce little, so their shade stays
// deep. lib/light.glsl takes the albedo into it twice over, so only pale surfaces take much.)
// (ambient (B): 0.05: the occlusion's multi-bounce now keeps pale corners light, so the self-bounce, which lifted pale
// stone's shade toward its lit side until the steps had no form, keeps only a trace: shaded calcite reads a cool
// blue-grey well below its sunlit tops, never a black notch)
// (the balance step: 0.15, in a near-neutral colour (LAND_SELF_BOUNCE_HUE): the gorge's shaded pale walls had sunk to
// a muddy grey, the valley's median a fifth to a third under round 3's; they read a cool light grey again below their
// sunlit tops, the steps' form kept)
#define LAND_SELF_BOUNCE 0.15
// how much of the sun's colour the self-bounce keeps (0 a neutral grey, 1 the sun's own colour)
#define LAND_SELF_BOUNCE_HUE 0.25
// the night (fill and moon) against its old display-space look: the linear sum of fill and moon is darker than the
// old display sum, so it is lifted to read as before
#define LAND_NIGHT 1.6
// how strongly the warm band along the horizon toward a low sun shows in the sky
#define LOW_SUN_BAND 0.85
// what a glowing pixel (emission 1) adds, in units of its own albedo
#define LAND_EMIT 1.6
// the darkest a lit face gets (a sealed cave wall still reads)
#define LAND_FLOOR 0.0016

// ---- the curve: moved to final's one output transform (lib/hdr.glsl, HDR stage A, 2026-10-19). welkinTone's S round
// mid grey, its soft shoulder and its saturation live on there (HDR_CONTRAST, HDR_KNEE, HDR_SATURATION, ...).

vec3 toLinear(vec3 c) {
	return pow(max(c, 0.0), vec3(2.2));
}

vec3 toDisplay(vec3 c) {
	return pow(max(c, 0.0), vec3(1.0 / 2.2));
}

float lumaLin(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

#endif
