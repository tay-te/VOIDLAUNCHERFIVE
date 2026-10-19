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
//        colour the chroma (the colour minus its luminance) is kept, then compressed smoothly as the luminance nears
//               white, so a colour past white whitens instead of clipping channel by channel, and the brightest
//               light (the sun's heart) burns to white; a little saturation out overall and in the highlights
//               (welkinTone's LAND_SATURATION and LAND_HIGHLIGHT_DESAT)
//   3. the grade (lib/grade.glsl welkinGrade, display values), then 4. the anti-banding dither.
// Nothing else in the pack tone-maps or caps the scene.
//
// ---- the bridge (stage A only: stage B deletes every use)
// The passes not yet in linear light (clouds, far weather, haze, mist, the shafts' in-scatter, water, the bloom and the
// beams) still work in the old display values. They read colortex0 through hdrBridgeToDisplay (exposure and curve: the
// same display value the output transform will give that light) and give their result back through hdrBridgeToScene
// (the inverse of the curve and the exposure, clamped short of the curve's asymptote at HDR_T_MAX). A pixel such a pass
// leaves alone comes back as it went in (to float precision); what it changes looks on screen as it did before. Light
// such a pass ADDS (the bloom's halo, the beams) comes in through hdrBridgeLight (the exposure undone). docs/DESIGN.md
// "The HDR foundation, stage A" lists every use; `grep -rn hdrBridge` finds them.

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
#define HDR_EXPOSURE_DAY 1.0
// through the golden hours (the sky table's golden hour, 0..1)
#define HDR_EXPOSURE_GOLDEN 1.0
// at night (the moonlit land's lift lives in the light model, LAND_NIGHT; this is on top)
#define HDR_EXPOSURE_NIGHT 1.0
// under a full overcast or in rain
#define HDR_EXPOSURE_OVERCAST 1.0
// the Nether and the End
#define HDR_EXPOSURE_OTHER 1.0

// ---- the curve
// the S round mid grey (welkinTone's LAND_CONTRAST)
#define HDR_CONTRAST 1.12
// where the shoulder starts (linear display) and how long it is (smaller m: longer)
#define HDR_KNEE 0.45
#define HDR_SHOULDER_M 0.4
// the saturation of the result and the highlights' (welkinTone's LAND_SATURATION, LAND_HIGHLIGHT_DESAT)
#define HDR_SATURATION 0.95
#define HDR_HIGHLIGHT_DESAT 0.3
// the chroma compression's hardness near white (higher: later and harder)
#define HDR_GAMUT_P 6.0
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
	float e = mix(mix(HDR_EXPOSURE_DAY, HDR_EXPOSURE_GOLDEN, clamp(golden, 0.0, 1.0)), HDR_EXPOSURE_NIGHT, night);
	return e * mix(1.0, HDR_EXPOSURE_OVERCAST, max(clamp(st.a, 0.0, 1.0), clamp(rainStrength, 0.0, 1.0)));
#else
	return HDR_EXPOSURE_OTHER;
#endif
}

// the S's strength at luminance y: none in the deep dark (the toe), none in the highlights
float hdrSK(float y) {
	return (HDR_CONTRAST - 1.0) * smoothstep(0.002, 0.03, y) * (1.0 - smoothstep(0.3, 1.5, y));
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

// the saturation at output luminance t (the highlights' pull toward white only while the sun stands high, as the land
// had it: a face in the low sun keeps its amber)
float hdrSat(float t) {
	float sunH = sin(sunAngle * 6.2831853);
	return HDR_SATURATION * (1.0 - HDR_HIGHLIGHT_DESAT * smoothstep(0.15, 0.4, sunH) * smoothstep(0.45, 0.9, t));
}

// The curve: exposed scene light to linear display (0..1).
vec3 hdrCurve(vec3 x) {
	x = max(x, 0.0);
	float y = hdrLuma(x);
	if (y <= 1e-9) {
		return vec3(0.0);
	}
	float t = hdrShoulder(hdrS(y));
	vec3 c = x * (t / y) - t;
	float r = max(c.r, max(c.g, c.b)) / max(1.0 - t, 1e-6);
	if (r > 1e-4) {
		c *= pow(1.0 + pow(r, HDR_GAMUT_P), -1.0 / HDR_GAMUT_P);
	}
	return max(t + c * hdrSat(t), 0.0);
}

// The curve's inverse: linear display back to exposed scene light (clamped at HDR_T_MAX).
vec3 hdrCurveInv(vec3 d) {
	d = max(d, 0.0);
	float t0 = hdrLuma(d);
	if (t0 <= 1e-9) {
		return vec3(0.0);
	}
	float t = min(t0, HDR_T_MAX);
	vec3 c = (d - t0) / hdrSat(t);
	float rc = min(max(c.r, max(c.g, c.b)) / max(1.0 - t, 1e-6), 0.995);
	if (rc > 1e-4) {
		c *= pow(1.0 - pow(rc, HDR_GAMUT_P), -1.0 / HDR_GAMUT_P);
	}
	float y = hdrSInv(hdrShoulderInv(t));
	return max((t + c) * (y / t), 0.0);
}

// ---- the output transform's first two steps (final): scene light to linear display
vec3 hdrOutput(vec3 scene, float exposure) {
	return hdrCurve(scene * exposure);
}

// ---- the bridge (stage A; see the head of this file and docs/DESIGN.md)
// scene light -> the display value the old passes work in
vec3 hdrBridgeToDisplay(vec3 scene) {
	return toDisplay(hdrCurve(scene * hdrExposure()));
}

// an old pass's display value -> scene light
vec3 hdrBridgeToScene(vec3 display) {
	return hdrCurveInv(toLinear(display)) / hdrExposure();
}

// light an old pass adds (linear, in display white's units) -> scene light
vec3 hdrBridgeLight(vec3 light) {
	return light / hdrExposure();
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
