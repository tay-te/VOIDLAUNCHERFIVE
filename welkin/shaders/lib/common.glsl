// VOID Welkin: small shared maths. No uniforms here, so any program can include it.

#ifndef WELKIN_COMMON
#define WELKIN_COMMON

#include "/lib/settings.glsl"

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

// block.properties ids are 10000 + the material above.
int materialFromBlockId(int id) {
	return (id >= 10000 && id < 10100) ? id - 10000 : MAT_NONE;
}

// 4x4 ordered (Bayer) dither threshold in (0, 1) for an integer cell. The same matrix as
// (0, 8, 2, 10 / 12, 4, 14, 6 / 3, 11, 1, 9 / 15, 7, 13, 5), built from the bits of the cell: a runtime-indexed const
// array spills to local memory on NVIDIA (measured at about 3 ms over a frame of haze), the bit form is a few ALU ops.
float bayer4(ivec2 p) {
	int t = p.x ^ p.y;
	int v = ((t & 1) << 3) | ((p.y & 1) << 2) | (t & 2) | ((p.y >> 1) & 1);
	return (float(v) + 0.5) * (1.0 / 16.0);
}

// x in [0, 1] rounded to one of `steps` + 1 flat levels; `d` is the dither threshold (0.5 for no dither).
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
