// VOID Welkin: final. A gentle shoulder for anything lit past white, then invisible anti-banding: one 8-bit step of
// triangular noise, fixed per pixel, so the smooth gradients of the sky, the haze and the light leave the 8-bit
// screen without contour lines. At that size the noise cannot be seen as a pattern. No other post effects: no bloom,
// no TAA, no blur, no palette steps.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 shoulder(vec3 c) {
	// linear to 0.8, then a soft roll into 1.0
	vec3 over = max(c - 0.8, 0.0);
	return min(c, 0.8) + 0.2 * (1.0 - exp(-over * 5.0));
}

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec3 c = shoulder(max(texelFetch(colortex0, p, 0).rgb, 0.0));
	// two uniform values per pixel summed: a triangle from -1 to +1 of one 8-bit step, mean zero
	uint h = pixelHash(p, 7u);
	float tri = (float(h & 0xFFFFu) + float(h >> 16u)) / 65535.0 - 1.0;
	c = clamp(c + tri / 255.0, 0.0, 1.0);
	outColor = vec4(c, 1.0);
}

#endif
