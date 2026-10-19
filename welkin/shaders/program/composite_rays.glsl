// VOID Welkin, look pass 6 (2026-10-19): one of the light shafts' three radial blurs (lib/rays.glsl), at half size.
// RAY_PASS 1 (composite5) reads composite3's source in colortex12 and writes colortex13, 2 (composite6) reads 13 and
// writes 12, 3 (composite7) reads 12 and writes 13 in the sun's colour at the hour's strength (display), which final
// lays on (x the beams over the land, y over the sky, z the shadowed lanes' shade; final gives them the sun's colour).
// Each pass averages RAY_TAPS points toward the sun's place on the screen, RAY_TAPS times farther apart than
// the pass before. Overworld only. Written from scratch for Welkin; no other pack's code.

#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
#include "/lib/rays.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
flat out vec3 raySun;   // the sun's place on the screen (xy, 0..1), and whether it is in front (z)
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
#ifndef WU_colortex13
#define WU_colortex13
uniform sampler2D colortex13;
#endif
#define RAY_SRC colortex13
/* RENDERTARGETS: 12 */
#else
#ifndef WU_colortex12
#define WU_colortex12
uniform sampler2D colortex12;
#endif
#define RAY_SRC colortex12
/* RENDERTARGETS: 13 */
#endif
layout(location = 0) out vec4 outRays;

#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

void main() {
	vec4 r = vec4(0.0);
	if (raySun.z > 0.5) {
		float on = rayOnScreen(raySun.xy, viewWidth / viewHeight);
		if (on > 0.0) {
#if SHAFT_DEBUG == 5
			float frac = 0.0; // the source itself
#elif RAY_PASS == 1
			float frac = RAY_REACH / float(RAY_TAPS * RAY_TAPS * RAY_TAPS);
#elif RAY_PASS == 2
			float frac = RAY_REACH / float(RAY_TAPS * RAY_TAPS);
#else
			float frac = RAY_REACH / float(RAY_TAPS);
#endif
			r = rayBlur(RAY_SRC, texcoord, raySun.xy, frac);
#if RAY_PASS == 3
			// how open the line toward the sun is (0..1, the sky's brightness aside: mostly the part near the sun); the
			// beams' brightness then falls off slowly with the distance from the sun, so they reach well across the frame
			float open = clamp(r.r / max(r.g, 1e-5), 0.0, 1.0);
			float far = length((raySun.xy - texcoord) * vec2(viewWidth / viewHeight, 1.0));
			float fall = rayFall(far) * on * r.a;
			// x: over the land, a wholly open line gives less than a broken one: an unbroken view toward the sun is the
			// haze's glow (composite2 already draws it), and the beams are the light the occluders break up
			// y: over the sky, sharpened, so the lanes between the clouds' beams go dark
			// z: the air in the shadowed lanes glows less: how much of the sun's forward glow they lose
			r = vec4(open * (1.0 - RAY_FLAT * open * open), open * open, 1.0 - open, 1.0) * fall;
#endif
		}
	}
	outRays = r;
}
#endif
