// VOID Welkin: prepare. Builds the 64x32 sky table in colortex5 once a frame, before anything is drawn: the dome's
// colour steps, the light and ambient colours the light ramp uses, horizon colours per haze kind, cloud face colours
// and the sun, moon and star palettes (layout in lib/sky/table.glsl). Inputs: the sun and moon, the clock and moon
// phase, rain and thunder, and Gloam's void_sky, void_light, void_cloudCover, void_storm, void_haze and
// void_hazeKind where it sends them (lib/sky/inputs.glsl falls back per value).
//
// The viewport is the buffer's 192x128 (size.buffer.colortex5 in shaders.properties): the table is its corner 64x32;
// (D11) columns 64..191 hold the gust map (lib/sway.glsl welkinGustMap), 16384 texels of the gust field.

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

void main() {
	gl_Position = ftransform();
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/sky/build.glsl"

/*
const int colortex5Format = RGBA16F;
const bool colortex5Clear = false;
*/

/* RENDERTARGETS: 5 */
layout(location = 0) out vec4 outTable;

// (HDR stage C) the frame-time probe: texel (0, 30) keeps the mean of Iris's frameTime (seconds / 0.1) over the frames
// the studio does not film (its shots fix the clock at about 100 s); final shows it with DEBUG_VIEW 25.
// (D7) A plain mean since the last filmed frame (a filmed frame marks a = 0.5 and the next idle frame starts over), up
// to 2048 frames, then a running mean over about that many. RGBA16F holds 11 bits, which gave the old running mean a
// dead zone of about 0.15 ms (an update under half a step was lost): the mean is now kept as r + b, r the value rounded
// to a half float (exact, so the target's own rounding never moves it) and b the rest.
#ifndef WU_frameTime
#define WU_frameTime
uniform float frameTime;
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;
#endif
// (D11) the gust map's inputs
#include "/lib/void.glsl"
#ifndef WU_cameraPositionInt
#define WU_cameraPositionInt
uniform ivec3 cameraPositionInt;
#endif
#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#ifndef WU_void_wind
#define WU_void_wind
uniform vec4 void_wind;
#endif
#ifndef WU_void_storm
#define WU_void_storm
uniform float void_storm;
#endif
#include "/lib/sway.glsl"

// x (> 0, in a half float's normal range) rounded to a half float's 11 significant bits
float probeHalf(float x) {
	float ulp = exp2(floor(log2(x)) - 10.0);
	return floor(x / ulp + 0.5) * ulp;
}

void main() {
	ivec2 t = ivec2(gl_FragCoord.xy);
	if (t.x >= WG_MAP_X) {
		// (D11) the gust field at this texel's world cell (the same inputs the terrain and the shadow pass use)
		vec2 gs = vec2(0.0);
		if (WIND_GUSTS > 0.0) {
			bool live = void_bridge == 1 && BRIDGE_MODE == 0;
			vec4 wind = live ? void_wind : welkinWindFallback(rainStrength);
			vec2 P = vec2(welkinGustCellOf(t - ivec2(WG_MAP_X, 0), cameraPositionInt.xz)) * WG_MAP_CELL;
			gs = welkinGust(P, frameTimeCounter, wind, max(live ? void_storm : 0.0, 0.6 * rainStrength), false);
		}
		outTable = vec4(gs, 0.0, 1.0);
		return;
	}
	if (t.y >= 32) {
		outTable = vec4(0.0);
		return;
	}
	outTable = skyBuildTexel(t);
	if (ivec2(gl_FragCoord.xy) == ivec2(0, 30)) {
		vec4 prev = texelFetch(colortex5, ivec2(0, 30), 0);
		bool filming = abs(frameTimeCounter - 100.0) < 2.0;
		if (filming || frameTime <= 0.0) {
			outTable = vec4(prev.rgb, 0.5);
		} else {
			float ft = clamp(frameTime / 0.1, 1e-4, 1.0);
			float n = prev.a > 0.75 ? clamp(prev.g, 0.0, 2048.0) : 0.0;
			float m = n < 1.0 ? ft : prev.r + prev.b;
			m += (ft - m) / (n + 1.0);
			float hi = probeHalf(m);
			outTable = vec4(hi, min(n + 1.0, 2048.0), m - hi, 1.0);
		}
	}
}

#endif
