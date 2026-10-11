// VOID Welkin: the world's 16-pixel grid. Effects that cover the world (dither, far texture, later mist and haze)
// snap to the texels a 16x16 block texture would have, coarsening by doubling with distance, so they read as
// texture, not as screen noise. All maths is camera-relative with the integer camera position kept apart, so the grid
// holds its precision 30 million blocks out.

#ifndef WELKIN_GRID
#define WELKIN_GRID

#include "/lib/common.glsl"

#ifndef WU_cameraPositionInt
#define WU_cameraPositionInt
uniform ivec3 cameraPositionInt;
#endif
#ifndef WU_cameraPositionFract
#define WU_cameraPositionFract
uniform vec3 cameraPositionFract;
#endif

// The world texel containing `eyePos` (a world position minus cameraPosition), at `level` (0: 16 texels a block,
// 1: 8, ... 4: one a block, 5: one per 2 blocks, ...). Exact integer maths for the camera's part.
ivec3 worldTexel(vec3 eyePos, int level) {
	vec3 local = cameraPositionFract + eyePos;
	if (level <= 4) {
		int s = 16 >> level;
		return ivec3(floor(local * float(s))) + cameraPositionInt * s;
	}
	int k = min(level - 4, 20);
	ivec3 mask = ivec3((1 << k) - 1);
	return ivec3(floor((local + vec3(cameraPositionInt & mask)) / float(1 << k))) + (cameraPositionInt >> k);
}

// The coarsest grid level whose texel still spans at least `minPixels` screen pixels, given how many blocks one pixel
// covers there.
int gridLevel(float blocksPerPixel, float minPixels) {
	float texelsPerPixel = blocksPerPixel * 16.0 * minPixels;
	return clamp(int(ceil(log2(max(texelsPerPixel, 1.0)))), 0, 24);
}

// How many blocks one screen pixel covers at view distance `dist` (projection's [1][1] and the view height in pixels).
float blocksPerPixelAt(float dist, float proj11, float viewH) {
	return dist * 2.0 / (proj11 * viewH);
}

// The Bayer threshold of the world texel at `eyePos` on the face with normal `n`, at grid `level`. The point is pulled
// half a texel inside the face, so texels on a face edge do not flicker between neighbours.
float worldDither(vec3 eyePos, vec3 n, int level) {
	float texel = exp2(float(level)) / 16.0;
	ivec3 c = worldTexel(eyePos - n * (0.5 * texel), level);
	return bayer4(faceAxes(c, n));
}

#endif
