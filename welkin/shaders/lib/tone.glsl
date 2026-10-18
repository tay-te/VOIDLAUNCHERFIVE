// VOID Welkin: exposure and tone for the land. No uniforms, so any program may include it.
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
#define LAND_FILL 0.215
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

// ---- the curve
// exposure (a LAND_EXPOSURE of 1 maps a mid grey face lit by 1.0 to mid grey)
#define LAND_EXPOSURE 1.0
// the S round mid grey: 1 is none
// (the balance step: 1.32, the sunlit half a little brighter and the shade a little deeper round the same mid grey)
// (review round 2: 1.12: a luminous frame rather than a harsh one, p95/p5 4.6 to 6.0 against the references' 2.7 to 3.8)
#define LAND_CONTRAST 1.12
// where the shoulder starts (linear, about sRGB 177) and how much of it keeps the hue
#define LAND_KNEE 0.45
// (round 2: 0.92, so a face in the amber low sun keeps its amber: the per-channel roll greyed it toward cream)
#define LAND_HUE_KEEP 0.92
// saturation of the result, 1 as it comes
// (round 2: 0.95, paying back the highlights' kept saturation, so noon's sunlit grass and sand do not turn louder)
#define LAND_SATURATION 0.95
// how much saturation a face gives up as it reaches white (round 3)
#define LAND_HIGHLIGHT_DESAT 0.3

vec3 toLinear(vec3 c) {
	return pow(max(c, 0.0), vec3(2.2));
}

vec3 toDisplay(vec3 c) {
	return pow(max(c, 0.0), vec3(1.0 / 2.2));
}

float lumaLin(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// the soft shoulder for one value: linear to the knee, then a hyperbolic roll that meets 1 at infinity with the
// same slope at the knee. Gentler than an exponential far up, so a pale face in full sun keeps its texture (sand and
// snow at sRGB 220..240, not a flat white) and nothing clips.
float toneShoulder(float x) {
	float over = max(x - LAND_KNEE, 0.0);
	return min(x, LAND_KNEE) + over / (1.0 + over / (1.0 - LAND_KNEE));
}

vec3 toneShoulder(vec3 x) {
	return vec3(toneShoulder(x.r), toneShoulder(x.g), toneShoulder(x.b));
}

// The highlights' desaturation now (lib/light.glsl sets it from the sun's height before it tones; others keep this).
float toneHighlightDesat = LAND_HIGHLIGHT_DESAT;

// Linear light (albedo x irradiance) to the display value the rest of the pack works in.
vec3 welkinTone(vec3 x) {
	x = max(x, 0.0) * LAND_EXPOSURE;
	// the S round mid grey, on luminance (the hue stays): it fades out in the deep dark, so a moonlit night or a dim
	// cave keeps its level and only the daylight's shade deepens
	float y0 = lumaLin(x);
	// (and eases off in the highlights, so a sunlit pale face keeps its texture instead of being pushed into the shoulder)
	float k = (LAND_CONTRAST - 1.0) * smoothstep(0.002, 0.03, y0) * (1.0 - smoothstep(0.3, 1.5, y0));
	x *= pow(max(y0, 1e-6) / 0.18, k);
	// the shoulder: per channel, and on luminance with the hue kept (then pulled back under white)
	vec3 perChannel = toneShoulder(x);
	float y = lumaLin(x);
	vec3 hueKept = x * (toneShoulder(y) / max(y, 1e-6));
	float peak = max(hueKept.r, max(hueKept.g, hueKept.b));
	// a colour past white is brought back under it a quarter by giving up saturation (the luminance kept) and three
	// quarters by giving up brightness (the hue and saturation kept): round 2, so a white trunk or a pale wall in an amber low sun reads
	// amber, not a washed-out cream, and a back-lit crown glows gold-green, not white
	if (peak > 1.0) {
		vec3 desat = mix(vec3(toneShoulder(y)), hueKept, (1.0 - toneShoulder(y)) / max(peak - toneShoulder(y), 1e-4));
		// (round 3: a tenth by saturation, nine tenths by brightness: a pale face in the strong low sun keeps its amber)
		hueKept = mix(desat, hueKept / peak, 0.9);
	}
	x = mix(perChannel, hueKept, LAND_HUE_KEEP);
	// round 3: the highlights give up a little saturation as they near white (up to LAND_HIGHLIGHT_DESAT at it), so
	// sunlit pale stone and sand read a clean near-white, not cream; the mids and the shade keep their colour
	float yl = lumaLin(x);
	x = mix(vec3(yl), x, LAND_SATURATION * (1.0 - toneHighlightDesat * smoothstep(0.45, 0.9, yl)));
	// (review round 2) the brightest faces a little under the shoulder's top, so pale stone in the morning sun keeps its
	// texture and relief instead of a flat near white (valley 08:00's sunlit diorite)
	x *= 1.0 - 0.14 * smoothstep(0.5, 1.0, yl);
	return toDisplay(clamp(x, 0.0, 1.0));
}

// A glowing texel (a torch's flame, a lamp, lava) in linear light to display, keeping its hue: its brightest channel
// rolls into white on the shoulder and the others follow in proportion, so a flame stays yellow-orange instead of
// washing out to white as the per-channel and luminance shoulders would push it.
vec3 welkinToneEmit(vec3 x) {
	x = max(x, 0.0) * LAND_EXPOSURE;
	float peak = max(x.r, max(x.g, x.b));
	x *= toneShoulder(peak) / max(peak, 1e-6);
	return toDisplay(clamp(x, 0.0, 1.0));
}

#endif
