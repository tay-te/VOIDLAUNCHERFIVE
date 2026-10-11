// VOID Welkin: the far edge. Land fades into the horizon colour in four flat steps toward the end of what is drawn
// (the LOD's reach with VOID LOD, the render distance without), so the world never ends in a hard cut against the
// sky. M2's haze by country replaces the colour; the steps stay.

#ifndef WELKIN_FADE
#define WELKIN_FADE

#include "/lib/common.glsl"

#ifndef WU_fogColor
#define WU_fogColor
uniform vec3 fogColor;
#endif
#ifndef WU_far
#define WU_far
uniform float far;
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhRenderDistance
#define WU_dhRenderDistance
uniform int dhRenderDistance;
#endif
#endif

// 0 near to 1 at the edge, in four steps; `d` is the dither threshold.
float farFade(float horizontal, float d) {
#ifdef DISTANT_HORIZONS
	float edge = max(float(dhRenderDistance), far);
	float t = clamp((horizontal - edge * 0.5) / (edge * 0.5), 0.0, 1.0);
#else
	float t = clamp((horizontal - far * 0.8) / (far * 0.2), 0.0, 1.0);
#endif
	return steps(t * t, 4.0, d);
}

vec3 horizonColor() {
	return fogColor;
}

#endif
