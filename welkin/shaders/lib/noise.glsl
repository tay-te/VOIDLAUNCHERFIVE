// VOID Welkin: the lighting noise (shared; workstream A owns it, others include it and never edit it, only ask).
//
// Noise is allowed only inside a lighting term (sun visibility, light through leaves, occlusion, in-scatter) that a
// depth-aware resolve then smooths until no grain is left (lib/denoise.glsl): textures and colour never see it. The
// rule that keeps a single frame grain-free: the noise is tiled 4x4 (lightNoise2), so the 4x4 resolve window
// (resolveSpatial4x4) always holds the tile's sixteen values exactly once, and its average is the same smooth
// many-sample result everywhere, every frame. With temporal accumulation on, the tile's values move along a
// low-discrepancy sequence from frame to frame (the same sixteen-per-tile property holds in every frame).
//
// Methods (published): interleaved gradient noise (Jimenez 2014, "Next Generation Post Processing in Call of Duty:
// Advanced Warfare"); interleaved sampling (Keller and Heidrich 2001); the R2 sequence (Roberts 2018, "The
// unreasonable effectiveness of quasirandom sequences"); the Vogel (golden-angle) disk (Vogel 1979).
//
// Signatures:
//   float ign(vec2 p)                     interleaved gradient noise at pixel p, 0..1
//   float ign(vec2 p, int frame)          the same, offset per frame (5.588238 px per frame, 8 frames)
//   int   tile4x4(ivec2 p)                the pixel's index in its 4x4 tile, 0..15, in a dispersed (Bayer) order
//   vec2  r2(float n)                     the n-th point of the R2 sequence, 0..1 squared
//   vec2  lightNoise2(ivec2 p, int frame) two noise values for pixel p: 16 R2 points spread over the 4x4 tile; frame
//                                         0 is the static pattern, frame k > 0 continues the sequence (temporal)
//   float lightNoise(ivec2 p, int frame)  its first value
//   int   lightFrame()                    the frame index the lighting noise uses now: 0 unless TEMPORAL_LIGHT is on
//   vec2  vogelDisk(int i, int n, float phi, float u)   sample i of n on the unit disk, turned by phi (radians),
//                                         its radius offset by u (0..1; 0.5 is the textbook Vogel disk)

#ifndef WELKIN_NOISE
#define WELKIN_NOISE

#include "/lib/settings.glsl"

float ign(vec2 p) {
	return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float ign(vec2 p, int frame) {
	return ign(p + 5.588238 * float(frame & 7));
}

int tile4x4(ivec2 p) {
	const int b[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
	return b[(p.x & 3) + 4 * (p.y & 3)];
}

vec2 r2(float n) {
	return fract(vec2(0.5) + n * vec2(0.7548776662, 0.5698402910));
}

vec2 lightNoise2(ivec2 p, int frame) {
	return r2(float(tile4x4(p) + 16 * frame));
}

float lightNoise(ivec2 p, int frame) {
	return lightNoise2(p, frame).x;
}

#ifdef TEMPORAL_LIGHT
#ifndef WU_frameCounter
#define WU_frameCounter
uniform int frameCounter;
#endif
#endif

int lightFrame() {
#ifdef TEMPORAL_LIGHT
	return frameCounter & 63;
#else
	return 0;
#endif
}

vec2 vogelDisk(int i, int n, float phi, float u) {
	float r = sqrt((float(i) + u) / float(n));
	float a = float(i) * 2.39996323 + phi;
	return r * vec2(cos(a), sin(a));
}

#endif
