// VOID Welkin: final. A gentle shoulder for anything lit past white, then the palette steps: every channel rounded
// to one of COLOR_STEPS levels with a 4x4 Bayer dither between neighbouring levels, the way a limited palette would
// draw it. No other post effects: no bloom, no TAA, no blur.

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

uniform sampler2D colortex0;

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
#if COLOR_STEPS > 0
	float d = 0.5;
#ifdef DITHER
	d = bayer4(p);
#endif
	c = steps3(c, float(COLOR_STEPS), d);
#endif
	outColor = vec4(c, 1.0);
}

#endif
