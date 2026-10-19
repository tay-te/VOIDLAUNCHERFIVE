// VOID Welkin (D7): the clouds pass's CloudEnv, worked out once in its vertex shader and handed to every pixel as flat
// values. Everything in CloudEnv is the same over the screen (the weather's uniforms and tiles' headers, the decks, the
// camera's frame, the sun), and built per pixel it cost about 0.15 ms at the steppe vista, besides holding some 35
// registers through the march. The fragment shader reads it back (wcEnvRead) bit for bit; the compiler may reload
// the flat values where it needs them instead of keeping them live.

#ifndef WELKIN_CLOUD_ENVFLAT
#define WELKIN_CLOUD_ENVFLAT

#include "/lib/clouds/env.glsl"

#ifdef VERTEX_SHADER
#define WC_FLAT flat out
#else
#define WC_FLAT flat in
#endif

WC_FLAT ivec4 wcEnvI0;     // mode, flags (w0 1, w1 2, towers 4, flash 8), seed, 0
WC_FLAT ivec4 wcEnvI1;     // frameInt, w1Off
WC_FLAT ivec4 wcEnvI2;     // frame12, frameRem
WC_FLAT ivec4 wcEnvI3;     // w0Centre, w1Centre
WC_FLAT vec4 wcEnvLayers;
WC_FLAT vec4 wcEnvF0;      // yLo, yHi, camY, fair
WC_FLAT vec4 wcEnvF1;      // frameFract, rainFb, stormFb
WC_FLAT vec4 wcEnvF2;      // sun, moon
WC_FLAT vec4 wcEnvF3;      // lightDir, darkNight

#ifdef VERTEX_SHADER
void wcEnvWrite(CloudEnv e) {
	wcEnvI0 = ivec4(e.mode, (e.w0 ? 1 : 0) | (e.w1 ? 2 : 0) | (e.towers ? 4 : 0) | (e.flash ? 8 : 0), int(e.seed), 0);
	wcEnvI1 = ivec4(e.frameInt, e.w1Off);
	wcEnvI2 = ivec4(e.frame12, e.frameRem);
	wcEnvI3 = ivec4(e.w0Centre, e.w1Centre);
	wcEnvLayers = e.layers;
	wcEnvF0 = vec4(e.yLo, e.yHi, e.camY, e.fair);
	wcEnvF1 = vec4(e.frameFract, e.rainFb, e.stormFb);
	wcEnvF2 = vec4(e.sun, e.moon);
	wcEnvF3 = vec4(e.lightDir, e.darkNight);
}
#else
CloudEnv wcEnvRead() {
	CloudEnv e;
	e.mode = wcEnvI0.x;
	e.w0 = (wcEnvI0.y & 1) != 0;
	e.w1 = (wcEnvI0.y & 2) != 0;
	e.towers = (wcEnvI0.y & 4) != 0;
	e.flash = (wcEnvI0.y & 8) != 0;
	e.seed = uint(wcEnvI0.z);
	e.frameInt = wcEnvI1.xy;
	e.w1Off = wcEnvI1.zw;
	e.frame12 = wcEnvI2.xy;
	e.frameRem = wcEnvI2.zw;
	e.w0Centre = wcEnvI3.xy;
	e.w1Centre = wcEnvI3.zw;
	e.layers = wcEnvLayers;
	e.yLo = wcEnvF0.x;
	e.yHi = wcEnvF0.y;
	e.camY = wcEnvF0.z;
	e.fair = wcEnvF0.w;
	e.frameFract = wcEnvF1.xy;
	e.rainFb = wcEnvF1.z;
	e.stormFb = wcEnvF1.w;
	e.sun = wcEnvF2.xyz;
	e.moon = wcEnvF2.w;
	e.lightDir = wcEnvF3.xyz;
	e.darkNight = wcEnvF3.w;
	return e;
}
#endif

#endif
