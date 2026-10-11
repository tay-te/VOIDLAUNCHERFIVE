// VOID Welkin: small shared maths. No uniforms here, so any program can include it.

#ifndef WELKIN_COMMON
#define WELKIN_COMMON

#include "/lib/settings.glsl"
#include "/lib/materials.glsl"

// G-buffer material ids (colortex2.y * 255), from block.properties and the Distant Horizons materials.
#define MAT_NONE     0
#define MAT_FOLIAGE  1
#define MAT_LEAVES   2
#define MAT_WATER    3
#define MAT_EMISSIVE 4
#define MAT_GLASS    5
#define MAT_SNOW     6
#define MAT_SAND     7
#define MAT_STONE    8
#define MAT_LAVA     9
#define MAT_CLOUD    10 // vanilla's clouds (gbuffers_clouds): no ground, never under the mist

// block.properties ids are 10000 + M + 100 * L (lib/materials.glsl): M 0..9 are the materials above, finer
// classes start at 10, L is the light colour. This returns the fine class; welkinCoarseMat() maps it to MAT_*.
int materialFromBlockId(int id) {
	return welkinMat(id);
}

// ---- The look (2026-10-11): smooth gradients with gentle banding, no visible dither anywhere.
// Light, sky, haze and mist are continuous. Where a ramp should still read as a few bands, softBands() eases it into
// them without any edge; the only noise in the pack is final's anti-banding, one 8-bit step, invisible.

// The share of the terrace softBands() applies, from SOFT_BANDS (0..100 %).
float softBandsAmount() {
	return float(SOFT_BANDS) * 0.01;
}

// x in [0, 1] through `n` soft bands: within each band the ramp eases off near the band's level and on again toward
// the next (a smoothstep across the band), so the gradient stays continuous and monotonic, with no edge and nothing to
// dither, while the bands still read. k 0 is the plain gradient, 1 the full smoothstep terrace (its slope 0 at each
// level, 1.5 between them).
float softBands(float x, float n, float k) {
	float y = clamp(x, 0.0, 1.0) * n;
	float i = min(floor(y), n - 1.0);
	float f = y - i;
	return (i + mix(f, f * f * (3.0 - 2.0 * f), k)) / n;
}

// The same with the pack's SOFT_BANDS share.
float softBands(float x, float n) {
	return softBands(x, n, softBandsAmount());
}

// A 32-bit integer hash of a pixel (and a salt), for final's anti-banding noise.
uint pixelHash(ivec2 p, uint salt) {
	uint h = uint(p.x) * 0x8da6b343u ^ uint(p.y) * 0xd8163841u ^ salt * 0xcb1ab31fu;
	h = (h ^ (h >> 15u)) * 0x2c1b3c6du;
	h = (h ^ (h >> 12u)) * 0x297a2d39u;
	return h ^ (h >> 15u);
}

// ---- Legacy (the old flat-step look). Nothing in the pack uses these for its look any more; they stay so the staged
// wave-2 modules (far weather, underground, water and shadows) still compile until they are adapted (see the note at
// the top of the scratchpad's INTEGRATION.md). Debug views may still use bayer4.

// 4x4 ordered (Bayer) dither threshold in (0, 1) for an integer cell. The same matrix as
// (0, 8, 2, 10 / 12, 4, 14, 6 / 3, 11, 1, 9 / 15, 7, 13, 5), built from the bits of the cell: a runtime-indexed const
// array spills to local memory on NVIDIA (measured at about 3 ms over a frame of haze), the bit form is a few ALU ops.
float bayer4(ivec2 p) {
	int t = p.x ^ p.y;
	int v = ((t & 1) << 3) | ((p.y & 1) << 2) | (t & 2) | ((p.y >> 1) & 1);
	return (float(v) + 0.5) * (1.0 / 16.0);
}

// x in [0, 1] rounded to one of `steps` + 1 flat levels; `d` is the dither threshold (0.5 for no dither). Legacy.
float steps(float x, float n, float d) {
	return clamp(floor(x * n + d) / n, 0.0, 1.0);
}

vec3 steps3(vec3 x, float n, float d) {
	return clamp(floor(x * n + d) / n, 0.0, 1.0);
}

// Integer hash (PCG-style), for per-texel variation on the world grid.
uint hashU(uvec3 v) {
	v = v * 1664525u + 1013904223u;
	v.x += v.y * v.z;
	v.y += v.z * v.x;
	v.z += v.x * v.y;
	v ^= v >> 16u;
	v.x += v.y * v.z;
	v.y += v.z * v.x;
	v.z += v.x * v.y;
	return v.x ^ v.y ^ v.z;
}

float hash01(ivec3 c) {
	return float(hashU(uvec3(c)) & 0xFFFFu) / 65535.0;
}

// Octahedral normal encoding into [0, 1]^2.
vec2 octEncode(vec3 n) {
	n /= abs(n.x) + abs(n.y) + abs(n.z) + 1e-6;
	vec2 e = n.xy;
	if (n.z < 0.0) {
		e = (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
	}
	return e * 0.5 + 0.5;
}

vec3 octDecode(vec2 f) {
	f = f * 2.0 - 1.0;
	vec3 n = vec3(f, 1.0 - abs(f.x) - abs(f.y));
	float t = max(-n.z, 0.0);
	n.x += n.x >= 0.0 ? -t : t;
	n.y += n.y >= 0.0 ? -t : t;
	return normalize(n);
}

// The light map coordinate as Iris hands it ((level + 0.5) / 16) to the level in [0, 1] (level / 15).
vec2 lightmapLevel(vec2 lmcoord) {
	return clamp((lmcoord - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
}

// The two axes of the face a world normal points out of, for anything laid out on a block face.
ivec2 faceAxes(ivec3 cell, vec3 n) {
	vec3 a = abs(n);
	if (a.y >= a.x && a.y >= a.z) {
		return cell.xz;
	}
	return a.x >= a.z ? cell.zy : cell.xy;
}

#endif
