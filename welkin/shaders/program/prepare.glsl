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

// (HDR stage C) the frame-time probe: texel (0, 30) keeps a slow running mean of Iris's frameTime (seconds / 0.1) over
// the frames the studio does not film (its shots fix the clock at about 100 s); final shows it with DEBUG_VIEW 25
#ifndef WU_frameTime
#define WU_frameTime
uniform float frameTime;
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;
#endif

void main() {
	outTable = skyBuildTexel(ivec2(gl_FragCoord.xy));
	if (ivec2(gl_FragCoord.xy) == ivec2(0, 30)) {
		vec4 prev = texelFetch(colortex5, ivec2(0, 30), 0);
		bool filming = abs(frameTimeCounter - 100.0) < 2.0;
		float ft = clamp(frameTime / 0.1, 0.0, 1.0);
		float n = prev.g * 4096.0;
		outTable = filming || frameTime <= 0.0 ? prev
			: vec4(n < 1.0 ? ft : mix(prev.r, ft, max(0.02, 1.0 / (n + 1.0))), min(n + 1.0, 4000.0) / 4096.0, 0.0, 1.0);
	}
}

#endif
