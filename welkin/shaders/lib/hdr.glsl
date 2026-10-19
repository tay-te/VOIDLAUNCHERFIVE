// VOID Welkin: the HDR foundation (stage A, 2026-10-19). The scene's unit, the exposure, the ONE output transform's
// curve and its inverse, and the bridge that lets the passes not yet moved to linear light keep working.
//
// ---- the scene's unit
// colortex0 holds scene-linear radiance from the light pass (deferred) and the sky (gbuffers_skybasic) until final.
// 1.0 is the light a white (albedo 1) face sends back when lit square-on by an irradiance of 1, the light model's
// unit (lib/tone.glsl: a noon sun is LAND_SUN 2.7, the open sky's fill on an upward face LAND_FILL 0.215). So a mid
// grey (0.18) face under that unit light is 0.18, and the output transform at exposure 1 shows it at mid grey on
// screen (display 0.46). The sky's colours, the sun's disc, the moon, the stars and every glow are on the same scale:
// a noon sky is about 0.3 to 0.8, the pixel sun's disc some HDR_SUN_RADIANCE (far above anything lit by it).
//
// ---- the output transform (final, in this order)
//   1. exposure  x * hdrExposure(): a per-hour and per-weather curve (below), no auto-exposure yet
//   2. the curve hdrCurve(): one filmic curve on luminance, hue kept:
//        toe/S  welkinTone's S round mid grey (contrast HDR_CONTRAST), fading out in the deep dark (the soft toe:
//               moonlit land and dim caves keep their level) and in the highlights
//        shoulder linear to HDR_KNEE, then a long power roll toward white, 1 - A (1 + o / (A m))^-m: slope 1 at the
//               knee, white only at infinity (a sunlit pale face about 0.8, the sky round the sun 0.9, the sun's disc
//               0.95 and up), so the sun can stand above its sky and nothing clips
//        colour the chroma (the colour minus its luminance) is kept, a little out overall and in the highlights
//               (welkinTone's LAND_SATURATION and LAND_HIGHLIGHT_DESAT); the brightest light (the last few percent
//               under white: the sun's heart) burns toward white; and (stage C) a path to white: the chroma is
//               compressed smoothly as the brightest channel nears the top (hdrGamut), the luminance kept, so a
//               bright saturated colour desaturates toward white instead of flattening at 1
//   3. the grade (lib/grade.glsl welkinGrade, display values), then 4. the anti-banding dither.
// Nothing else in the pack tone-maps or caps the scene.
//
// ---- authored colours (stage B)
// The passes that still author their colours in display values (the clouds' light, the haze's and the mist's colours,
// the curtains, the cave air, the sky table's palette, vanilla's sky stages and textures) bring them into scene light
// once, at their source, with sceneFromAuthored; everything after (every composite, transmittance, in-scatter, glint,
// bloom) is scene light. The stage A bridge is gone.

#ifndef WELKIN_HDR
#define WELKIN_HDR

#include "/lib/settings.glsl"
#include "/lib/tone.glsl"
#ifdef WORLD_OVERWORLD
#include "/lib/sky/table.glsl"
#endif

#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#ifndef WU_sunAngle
#define WU_sunAngle
uniform float sunAngle;
#endif

// ---- the exposure curve (a multiplier on scene light; 1 shows the land as the light model was tuned)
// by day (the land's light model was tuned at exposure 1: the reference)
#define HDR_EXPOSURE_DAY 0.92
// through the golden hours (the sky table's golden hour, 0..1)
#define HDR_EXPOSURE_GOLDEN 1.0
// at night (the moonlit land's lift lives in the light model, LAND_NIGHT; this is on top)
#define HDR_EXPOSURE_NIGHT 0.52
// (Look D4) round sunset and in the blue hour, while the night step is in but the sun is only a little under (the old
// night exposure: the dusk's glow and the blue hour keep their level)
#define HDR_EXPOSURE_TWILIGHT 1.1
// under a full overcast or in rain
#define HDR_EXPOSURE_OVERCAST 0.9
// the Nether and the End
#define HDR_EXPOSURE_OTHER 1.0

// ---- the curve
// the S round mid grey (welkinTone's LAND_CONTRAST)
#define HDR_CONTRAST 1.12
// (Look D4) by day, once the sun stands past about 20 degrees: a stronger S, so the sunlit tops stand clear of the shade
// and the land has form (the golden hours, dawn and night keep HDR_CONTRAST)
#define HDR_CONTRAST_DAY 1.22
// where the shoulder starts (linear display) and how long it is (smaller m: longer)
#define HDR_KNEE 0.45
#define HDR_SHOULDER_M 0.4
// the saturation of the result and the highlights' (welkinTone's LAND_SATURATION, LAND_HIGHLIGHT_DESAT)
#define HDR_SATURATION 0.95
#define HDR_HIGHLIGHT_DESAT 0.3
// how much of its colour the brightest light (the last few percent under white: the sun's heart) gives up
#define HDR_BURN 0.8
// the path to white: the most of the headroom over its luminance a colour's brightest channel may take, and how hard
// the compression turns (higher: later)
#define HDR_GAMUT_MAX 0.92
#define HDR_GAMUT_P 4.0
// the inverse's clamp: the curve's output never taken past this (about 28000 x mid grey's light at exposure 1)
#define HDR_T_MAX 0.995

float hdrLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// The exposure now. The same in every program in a frame (uniforms and the sky table only).
float hdrExposure() {
#ifdef WORLD_OVERWORLD
	vec4 st = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT); // g night, a overcast
	float golden = skyTable(SKY_X_STATE_TIME, SKY_ROW_LIGHT).r;
	float night = clamp(st.g, 0.0, 1.0);
	// (Look D4) the night step is already most of the way in round sunset and in the blue hour: those keep the twilight's
	// exposure, and the deep night's lower one comes in only once the sun is well under (about 5 to 15 degrees down)
	float deep = 1.0 - smoothstep(-0.26, -0.09, sin(sunAngle * 6.2831853));
	float e = mix(mix(HDR_EXPOSURE_DAY, HDR_EXPOSURE_GOLDEN, clamp(golden, 0.0, 1.0)),
		mix(HDR_EXPOSURE_TWILIGHT, HDR_EXPOSURE_NIGHT, deep), night);
	return e * mix(1.0, HDR_EXPOSURE_OVERCAST, max(clamp(st.a, 0.0, 1.0), clamp(rainStrength, 0.0, 1.0)));
#else
	return HDR_EXPOSURE_OTHER;
#endif
}

// the S's strength at luminance y: none in the deep dark (the toe), none in the highlights
float hdrSK(float y) {
	float k = mix(HDR_CONTRAST, HDR_CONTRAST_DAY, smoothstep(0.3, 0.55, sin(sunAngle * 6.2831853)));
	return (k - 1.0) * smoothstep(0.002, 0.03, y) * (1.0 - smoothstep(0.3, 1.5, y));
}

float hdrS(float y) {
	return y * pow(max(y, 1e-9) / 0.18, hdrSK(y));
}

// The S's inverse: ln(s / 0.18) = u (1 + k), solved for u by a fixed-point walk (it converges fast: k changes slowly)
float hdrSInv(float s) {
	if (s <= 1e-9) {
		return 0.0;
	}
	float v = log(s / 0.18);
	float u = v;
	for (int i = 0; i < 6; i++) {
		u = v / (1.0 + hdrSK(0.18 * exp(u)));
	}
	return 0.18 * exp(u);
}

float hdrShoulder(float s) {
	const float A = 1.0 - HDR_KNEE;
	if (s <= HDR_KNEE) {
		return s;
	}
	return 1.0 - A * pow(1.0 + (s - HDR_KNEE) / (A * HDR_SHOULDER_M), -HDR_SHOULDER_M);
}

float hdrShoulderInv(float t) {
	const float A = 1.0 - HDR_KNEE;
	if (t <= HDR_KNEE) {
		return t;
	}
	return HDR_KNEE + A * HDR_SHOULDER_M * (pow(A / max(1.0 - t, 1e-6), 1.0 / HDR_SHOULDER_M) - 1.0);
}

// the saturation at output luminance t: a little out overall; the highlights' pull toward white only while the sun
// stands high, as the land had it (a face in the low sun keeps its amber); and the brightest light, near white, burns
// toward it (the sun's heart a gold-white over its gold aureole)
float hdrSat(float t) {
	float sunH = sin(sunAngle * 6.2831853);
	return HDR_SATURATION * (1.0 - HDR_HIGHLIGHT_DESAT * smoothstep(0.15, 0.4, sunH) * smoothstep(0.45, 0.9, t))
		* (1.0 - HDR_BURN * smoothstep(0.92, 0.995, t));
}

// The path to white (stage C): the chroma (the colour minus its luminance t) measured against the headroom left above
// t, r = (brightest channel - t) / (1 - t), is compressed smoothly toward HDR_GAMUT_MAX (r / (1 + (r / M)^p)^(1/p)):
// a mildly coloured light keeps its colour, a saturated one gives up saturation as its brightest channel nears the
// top, and as t itself nears white the colour runs out of room and whitens. The luminance is kept, so a bright orange
// sky keeps its gradation (no channel flattens at 1) and the sun's heart burns white. Exactly invertible.
float hdrGamut(float r) {
	return r / pow(1.0 + pow(r / HDR_GAMUT_MAX, HDR_GAMUT_P), 1.0 / HDR_GAMUT_P);
}

float hdrGamutInv(float r) {
	r = min(r, HDR_GAMUT_MAX * 0.9995);
	return r / pow(1.0 - pow(r / HDR_GAMUT_MAX, HDR_GAMUT_P), 1.0 / HDR_GAMUT_P);
}

// The curve: exposed scene light to linear display (0..1).
vec3 hdrCurve(vec3 x) {
	x = max(x, 0.0);
	float y = hdrLuma(x);
	if (y <= 1e-9) {
		return vec3(0.0);
	}
	float t = hdrShoulder(hdrS(y));
	// the luminance through the curve, the chroma kept (then a little out), then compressed toward white
	vec3 c = (x * (t / y) - t) * hdrSat(t);
	float r = max(c.r, max(c.g, c.b)) / max(1.0 - t, 1e-6);
	if (r > 1e-5) {
		c *= hdrGamut(r) / r;
	}
	return max(t + c, 0.0);
}

// The curve's inverse: linear display back to exposed scene light (clamped at HDR_T_MAX).
vec3 hdrCurveInv(vec3 d) {
	d = max(d, 0.0);
	float t0 = hdrLuma(d);
	if (t0 <= 1e-9) {
		return vec3(0.0);
	}
	float t = min(t0, HDR_T_MAX);
	vec3 c = d - t0;
	float r = max(c.r, max(c.g, c.b)) / max(1.0 - t, 1e-6);
	if (r > 1e-5) {
		c *= hdrGamutInv(r) / r;
	}
	c /= hdrSat(t);
	float y = hdrSInv(hdrShoulderInv(t));
	return max((t + c) * (y / t), 0.0);
}

// A colour AUTHORED in display values (the sky table's palette, a cloud's or the haze's colour as its module works it
// out, an authored constant) as scene light: what the old pipeline showed it as (its final shoulder: linear to 0.8,
// then a soft roll, so an authored white was 0.93 on screen) through the curve's inverse at exposure 1. A value
// authored past white (a cloud's silver lining, a glint) lands well above the sky (1.2 -> about 60). Every composite,
// transmittance and in-scatter after it is in scene light; stage D re-authors the colours in linear light.
vec3 sceneFromAuthored(vec3 display) {
	vec3 d = max(display, 0.0);
	vec3 over = max(d - 0.8, 0.0);
	d = min(d, 0.8) + 0.2 * (1.0 - exp(-over * 5.0));
	return hdrCurveInv(toLinear(d));
}

// ---- (Look D1) the air in linear light: optical depth, not a mix
// Laid on in scene light (colour * T + in-scatter), a layer's cover is 1 - exp(-tau) of its optical depth. Each layer
// keeps its own density (`dens`, against what it was tuned to as a display-value mix) and its own onset: the first
// `onset` of optical depth counts by tau / (tau + onset), so the thin near air stays clear (the near land keeps its
// darks, no milk) while the far air takes its whole depth (each ridge a step paler than the one in front). This
// replaces stage B's stopgap, a power 1.7 of the old opacity over every layer alike.
float airTau(float a) {
	return -log(max(1.0 - clamp(a, 0.0, 1.0), 1e-5));
}

float airCover(float tau, float dens, float onset) {
	float t = max(tau * dens, 0.0);
	return 1.0 - exp(-t * t / (t + onset));
}

// the same for an opacity worked out as 1 - exp(-tau) (or near enough)
float airCoverA(float a, float dens, float onset) {
	return airCover(airTau(a), dens, onset);
}

// the layers' density and onset (the country haze; the far air of far weather; the valley mist; the morning mist; the
// clouds' far fog; the day's blue-grey body). The haze volume's and the aerial perspective's live with them
// (lib/haze/volume.glsl HV_LIN_*, lib/air.glsl AIR_LIN_*).
#define AIR_HAZE_DENS 1.0
#define AIR_HAZE_ONSET 0.3
#define AIR_FAR_DENS 0.8
#define AIR_FAR_ONSET 0.3
#define AIR_MIST_DENS 1.0
#define AIR_MIST_ONSET 0.25
#define AIR_DAWN_DENS 0.9
#define AIR_DAWN_ONSET 0.3
#define AIR_CLOUDFOG_DENS 1.0
#define AIR_CLOUDFOG_ONSET 0.3
#define AIR_BODY_DENS 1.0
#define AIR_BODY_ONSET 0.3

// ---- (Look D1) the emitters' gentler path to white: a glowing texel's exposed light eases toward HDR_EMIT_PEAK (its
// luminance; slope 1 in the dim), and keeps a little more of its colour, so a flame stays orange and gold (its yellow
// core pale gold) rather than running to white; the bloom's halo carries its glow. em: how much it glows (0..1).
#define HDR_EMIT_PEAK 0.85
#define HDR_EMIT_SAT 1.15
vec3 hdrEmitterLight(vec3 x, float em) {
	float y = hdrLuma(x);
	if (y <= 1e-6) {
		return x;
	}
	float yl = HDR_EMIT_PEAK * (1.0 - exp(-y / HDR_EMIT_PEAK));
	vec3 e = x * (yl / y);
	e = max(yl + (e - yl) * HDR_EMIT_SAT, 0.0);
	return mix(x, e, em);
}

// ---- the output transform's first two steps (final): scene light to linear display
vec3 hdrOutput(vec3 scene, float exposure) {
	return hdrCurve(scene * exposure);
}

// ---- the instruments (final, DEBUG_VIEW 23 and 24)
// False colour by stops from mid grey (the exposed scene's luminance against 0.18), one colour a stop.
vec3 hdrFalseColour(vec3 exposedScene, ivec2 p, ivec2 size) {
	float y = hdrLuma(exposedScene);
	float ev = log2(max(y, 1e-9) / 0.18);
	// the legend: a strip along the bottom, -8 .. +8 stops
	if (p.y < 18) {
		ev = (float(p.x) / float(size.x)) * 17.0 - 8.5;
		if (p.y < 2 || abs(fract(ev + 0.5) - 0.5) * float(size.x) / 17.0 < 1.0) {
			return vec3(0.0);
		}
	}
	int b = int(floor(ev + 0.5));
	vec3 c;
	if (b <= -8) c = vec3(0.0);
	else if (b == -7) c = vec3(0.10, 0.0, 0.16);
	else if (b == -6) c = vec3(0.25, 0.0, 0.40);
	else if (b == -5) c = vec3(0.20, 0.05, 0.70);
	else if (b == -4) c = vec3(0.05, 0.20, 0.90);
	else if (b == -3) c = vec3(0.0, 0.50, 0.90);
	else if (b == -2) c = vec3(0.0, 0.65, 0.60);
	else if (b == -1) c = vec3(0.10, 0.55, 0.25);
	else if (b == 0) c = vec3(0.50);
	else if (b == 1) c = vec3(0.45, 0.80, 0.15);
	else if (b == 2) c = vec3(0.85, 0.90, 0.10);
	else if (b == 3) c = vec3(1.0, 0.65, 0.0);
	else if (b == 4) c = vec3(1.0, 0.30, 0.0);
	else if (b == 5) c = vec3(0.90, 0.0, 0.10);
	else if (b == 6) c = vec3(0.90, 0.0, 0.70);
	else if (b == 7) c = vec3(1.0, 0.60, 1.0);
	else c = vec3(1.0);
	// mid grey marked: the band's own grey, with a fine hatching where the light is within a sixth of a stop of it
	if (b == 0 && abs(ev) < 0.17 && ((p.x + p.y) & 3) == 0) {
		c = vec3(1.0);
	}
	return c;
}

// What the curve pushes to white (red: a channel past 0.98 on screen; magenta: the luminance past 0.98) and into black
// (blue: under 0.02 on screen), over the frame in grey.
vec3 hdrClipView(vec3 linDisplay) {
	vec3 d = toDisplay(linDisplay);
	float g = hdrLuma(d) * 0.6;
	vec3 c = vec3(g);
	if (max(d.r, max(d.g, d.b)) >= 0.98) c = vec3(1.0, 0.1, 0.1);
	if (hdrLuma(d) >= 0.98) c = vec3(1.0, 0.0, 1.0);
	if (max(d.r, max(d.g, d.b)) <= 0.02) c = vec3(0.0, 0.2, 1.0);
	return c;
}

#endif
