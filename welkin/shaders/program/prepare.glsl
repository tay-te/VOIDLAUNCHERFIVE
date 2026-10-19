// VOID Welkin: prepare. Builds the 64x32 sky table in colortex5 once a frame, before anything is drawn: the dome's
// colour steps, the light and ambient colours the light ramp uses, horizon colours per haze kind, cloud face colours
// and the sun, moon and star palettes (layout in lib/sky/table.glsl). Inputs: the sun and moon, the clock and moon
// phase, rain and thunder, and Gloam's void_sky, void_light, void_cloudCover, void_storm, void_haze and
// void_hazeKind where it sends them (lib/sky/inputs.glsl falls back per value).
//
// The viewport is the buffer's 64x32 (size.buffer.colortex5 in shaders.properties), so this is 2048 fragments.

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

// x (> 0, in a half float's normal range) rounded to a half float's 11 significant bits
float probeHalf(float x) {
	float ulp = exp2(floor(log2(x)) - 10.0);
	return floor(x / ulp + 0.5) * ulp;
}

void main() {
	outTable = skyBuildTexel(ivec2(gl_FragCoord.xy));
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
