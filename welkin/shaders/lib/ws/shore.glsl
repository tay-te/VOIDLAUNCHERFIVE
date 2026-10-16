// VOID Welkin water: foam along the shore (the defects pass, 2026-10-16), for composite1, over the lit scene.
//
// The water's own pass sees one fragment and the depth behind it: it finds the shore only where the water thins over
// a sloping bed. At a bank (a grass edge over water a block or more deep, the commonest shore in Minecraft) the bed
// does not rise, and no foam showed anywhere. So the shore is looked for on screen: round each near water pixel, three
// rings of eight probes 0.2, 0.5 and 0.9 blocks out (in world size, so the band keeps its width with distance)
// look for land standing at the water's own level (a bank, a beach, a rock: from a little under the surface to two
// blocks over it); the nearest one found, in blocks across the water, sets the band. The band breathes in and out
// with a slow swell, its foam in the water's own 1/16-block pixels (crisp, drifting slowly), lit at the water's own
// brightness so it is white by day, gold at dusk and dim at night.
//
// Only the chunks' level water (colortex3's mark 1), within WS_SHORE_REACH blocks, with the eye out of the water.

#ifndef WELKIN_WS_SHORE
#define WELKIN_WS_SHORE

#include "/lib/grid.glsl"

#define WS_SHORE_REACH 96.0
// how far across the water the foam reaches from the bank at the swell's low and high (blocks), and its strength
#define WS_SHORE_NEAR 0.35
#define WS_SHORE_FAR 0.95
#define WS_SHORE_STRENGTH 0.75

#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;
#endif

uint wsShoreHash(uint x) {
	x ^= x >> 16;
	x *= 0x7feb352du;
	x ^= x >> 15;
	x *= 0x846ca68bu;
	x ^= x >> 16;
	return x;
}

float wsShoreValue(vec2 p, int salt) {
	vec2 i = floor(p);
	vec2 f = p - i;
	f = f * f * (3.0 - 2.0 * f);
	ivec2 c = ivec2(i);
	uint s = uint(salt) * 0x27d4eb2du;
	float a = float(wsShoreHash(uint(c.x) * 0x9E3779B1u + uint(c.y) * 0x85EBCA77u + s) >> 8) / 16777216.0;
	float b = float(wsShoreHash(uint(c.x + 1) * 0x9E3779B1u + uint(c.y) * 0x85EBCA77u + s) >> 8) / 16777216.0;
	float d = float(wsShoreHash(uint(c.x) * 0x9E3779B1u + uint(c.y + 1) * 0x85EBCA77u + s) >> 8) / 16777216.0;
	float e = float(wsShoreHash(uint(c.x + 1) * 0x9E3779B1u + uint(c.y + 1) * 0x85EBCA77u + s) >> 8) / 16777216.0;
	return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

vec3 wsShoreFoam(ivec2 p, vec2 uv, vec3 color) {
	if (isEyeInWater != 0) {
		return color;
	}
	vec4 mark = texelFetch(colortex3, p, 0);
	if (mark.a < 0.75) {
		return color;
	}
	Surface s = surfaceAt(p, uv);
	if (s.sky || s.fromLod) {
		return color;
	}
	vec3 w = mat3(gbufferModelViewInverse) * s.view;
	float dist = length(w);
	if (dist > WS_SHORE_REACH) {
		return color;
	}
	vec2 res = vec2(viewWidth, viewHeight);
	float bpp = blocksPerPixelAt(dist, gbufferProjection[1][1], viewHeight);
	float swell = 0.5 + 0.5 * sin(frameTimeCounter * 0.9 + wsShoreValue((w.xz + vec2(cameraPositionInt.xz & 1023) + cameraPositionFract.xz) * 0.11, 9) * 6.2832);
	float reach = mix(WS_SHORE_NEAR, WS_SHORE_FAR, swell);
	// three rings of eight probes, 0.2, 0.5 and 0.9 blocks out: the nearest ring that meets the bank says how far off
	// its edge is (between that ring and the one inside it)
	float best = 1e9;
	const float radii[3] = float[3](0.2, 0.5, 0.9);
	for (int ring = 0; ring < 3; ring++) {
		float rpx = clamp(radii[ring] / bpp, 1.0, 48.0);
		for (int i = 0; i < 8; i++) {
			float ang = (float(i) + 0.5 * float(ring & 1)) * 0.785398;
			ivec2 q = p + ivec2(round(vec2(cos(ang), sin(ang)) * rpx));
			if (any(lessThan(q, ivec2(0))) || any(greaterThanEqual(q, ivec2(res)))) {
				continue;
			}
			if (texelFetch(colortex3, q, 0).a > 0.0) {
				continue;
			}
			Surface sq = surfaceAt(q, (vec2(q) + 0.5) / res);
			if (sq.sky) {
				continue;
			}
			vec3 e = mat3(gbufferModelViewInverse) * sq.view;
			float dy = e.y - w.y;
			if (dy < -0.7 || dy > 2.0) {
				continue;
			}
			best = min(best, min(length(e.xz - w.xz), radii[ring]) - (ring == 0 ? 0.15 : 0.5 * (radii[ring] - radii[ring - 1])));
		}
		if (best < 1e8) {
			break;
		}
	}
	if (best > reach) {
		return color;
	}
	float band = 1.0 - smoothstep(0.0, reach, max(best, 0.0));
	vec2 wp = vec2(cameraPositionInt.xz & 1023) + cameraPositionFract.xz + w.xz;
	vec2 fp = (floor(wp * 16.0) + 0.5) / 16.0;
	float pix = smoothstep(0.38, 0.68, 0.65 * wsShoreValue(fp * 1.7 + frameTimeCounter * 0.06, 11)
		+ 0.35 * wsShoreValue(fp * 4.3 - frameTimeCounter * 0.05, 13));
	pix = mix(pix, 0.55, smoothstep(0.04, 0.12, bpp * 1.0));
	float foam = band * mix(0.3, 1.0, pix) * WS_SHORE_STRENGTH;
	// lit at the water's brightness: white by day, warm at dusk, dim at night
	float lit = max(mark.r, max(mark.g, mark.b));
	vec3 tint = mark.rgb / max(lit, 1e-3);
	vec3 foamCol = mix(vec3(1.0), tint, 0.2) * clamp(lit * 1.7 + 0.08, 0.0, 1.05);
	return mix(color, foamCol, foam);
}

#endif
