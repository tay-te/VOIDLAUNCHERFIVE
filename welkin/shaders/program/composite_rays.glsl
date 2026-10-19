// VOID Welkin, HDR stage C: one of the light shafts' three radial blurs (lib/rays.glsl), at half size. RAY_PASS 1
// (composite5) reads composite3's source in colortex12 and writes colortex13, 2 (composite6) reads 13 and writes 12,
// 3 (composite7) reads 12 and writes 13 with the fall-off from the sun; final adds it. Overworld only.

#include "/lib/common.glsl"
#include "/lib/rays.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
flat out vec3 raySun;
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
	raySun = raySunScreen(gbufferProjection, sunPosition);
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
flat in vec3 raySun;

#if RAY_PASS == 2
uniform sampler2D colortex13;
#define RAY_SRC colortex13
/* RENDERTARGETS: 12 */
#else
uniform sampler2D colortex12;
#define RAY_SRC colortex12
/* RENDERTARGETS: 13 */
#endif
layout(location = 0) out vec4 outRays;

uniform float viewWidth;
uniform float viewHeight;

void main() {
	vec3 r = vec3(0.0);
	if (raySun.z > 0.5) {
		float on = rayOnScreen(raySun.xy, viewWidth / viewHeight);
		if (on > 0.0) {
#if SHAFT_DEBUG == 6
			float frac = 0.0; // the source itself
#elif RAY_PASS == 1
			// (each pass covers RAY_OVERLAP times the next pass's step: a texel nearer the sun has a shorter line, and
			// without the overlap the next pass's taps would land on gaps between its copies, as steps)
			float frac = RAY_REACH * RAY_OVERLAP * RAY_OVERLAP / float(RAY_TAPS * RAY_TAPS * RAY_TAPS);
#elif RAY_PASS == 2
			float frac = RAY_REACH * RAY_OVERLAP / float(RAY_TAPS * RAY_TAPS);
#else
			float frac = RAY_REACH / float(RAY_TAPS);
#endif
			r = rayBlur(RAY_SRC, texcoord, raySun.xy, frac);
#if RAY_PASS == 3
			float far = length((raySun.xy - texcoord) * vec2(viewWidth / viewHeight, 1.0));
			r *= rayFall(far) * on;
#endif
		}
	}
	outRays = vec4(r, 1.0);
}
#endif
