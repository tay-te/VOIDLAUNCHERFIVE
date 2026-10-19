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
			r = textureLod(RAY_SRC, texcoord, 0.0).rgb; // the source itself
#elif RAY_PASS == 1
			r = rayBlurLog(RAY_SRC, texcoord, raySun.xy, RAY_LOG_STEP1);
#elif RAY_PASS == 2
			r = rayBlurLog(RAY_SRC, texcoord, raySun.xy, RAY_LOG_STEP2);
#else
			r = rayBlurLog(RAY_SRC, texcoord, raySun.xy, RAY_LOG_STEP3);
#endif
#if RAY_PASS == 3
			float far = length((raySun.xy - texcoord) * vec2(viewWidth / viewHeight, 1.0));
			r *= rayFall(far) * on;
#endif
		}
	}
	outRays = vec4(r, 1.0);
}
#endif
