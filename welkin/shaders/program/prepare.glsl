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
const int colortex5Format = RGBA8;
const bool colortex5Clear = false;
*/

/* RENDERTARGETS: 5 */
layout(location = 0) out vec4 outTable;

void main() {
	outTable = skyBuildTexel(ivec2(gl_FragCoord.xy));
}

#endif
