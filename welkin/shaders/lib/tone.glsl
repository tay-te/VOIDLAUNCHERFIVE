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

// ---- the light model (linear irradiance on a face square to the light; mid grey albedo is 0.18)
// the noon sun
#define LAND_SUN 2.4
// the open sky's fill on an upward face at noon
#define LAND_FILL 0.245
// the ground's bounce: the share of the sunlit ground's light that reaches a downward face
#define LAND_BOUNCE 0.36
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
#define LAND_CONTRAST 1.22
// where the shoulder starts (linear) and how much of it keeps the hue
#define LAND_KNEE 0.52
#define LAND_HUE_KEEP 0.8
// saturation of the result, 1 as it comes
#define LAND_SATURATION 0.97

vec3 toLinear(vec3 c) {
	return pow(max(c, 0.0), vec3(2.2));
}

vec3 toDisplay(vec3 c) {
	return pow(max(c, 0.0), vec3(1.0 / 2.2));
}

float lumaLin(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// the soft shoulder for one value: linear to the knee, then an exponential roll that meets 1 at infinity with the
// same slope at the knee
float toneShoulder(float x) {
	float over = max(x - LAND_KNEE, 0.0);
	return min(x, LAND_KNEE) + (1.0 - LAND_KNEE) * (1.0 - exp(-over / (1.0 - LAND_KNEE)));
}

vec3 toneShoulder(vec3 x) {
	return vec3(toneShoulder(x.r), toneShoulder(x.g), toneShoulder(x.b));
}

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
	hueKept = peak > 1.0 ? mix(vec3(toneShoulder(y)), hueKept, (1.0 - toneShoulder(y)) / max(peak - toneShoulder(y), 1e-4)) : hueKept;
	x = mix(perChannel, hueKept, LAND_HUE_KEEP);
	x = mix(vec3(lumaLin(x)), x, LAND_SATURATION);
	return toDisplay(clamp(x, 0.0, 1.0));
}

#endif
