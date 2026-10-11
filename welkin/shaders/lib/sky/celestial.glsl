// VOID Welkin sky (M2): the sky's pixel grids and the pixel-art bodies on them.
//
// Two grids, both pixel-true:
//   - the sky grid: an equal-angle cube map of the direction, SKY_TEXELS cells per 90 degrees (about 0.35 degrees a
//     texel at 256). The dome's band edges and dither snap to it in world directions, so turning the camera never
//     moves them; the stars sit on the same grid turned with the celestial sphere, so they wheel with the sun.
//   - the art grid of the sun and the moon: SKY_ART_PIXEL radians a pixel (vanilla's sun texel by default), laid on
//     a plane facing the body and aligned with its path, like vanilla's quads.
// No texture is sampled; each body is a few comparisons per pixel, and only near it.

#ifndef WELKIN_SKY_CELESTIAL
#define WELKIN_SKY_CELESTIAL

#include "/lib/common.glsl"
#include "/lib/sky/settings.glsl"
#include "/lib/sky/palette.glsl"
#include "/lib/sky/table.glsl"
#include "/lib/sky/uniforms.glsl"

// ------------------------------------------------------------------------------------------------------ the sky grid

// The cell of direction d (need not be unit) on an equal-angle cube map with n cells per face side:
// x the face (0 +x, 1 -x, 2 +y, 3 -y, 4 +z, 5 -z), yz the cell.
ivec3 skyCell(vec3 d, int n) {
	vec3 a = abs(d);
	int face;
	vec2 uv;
	if (a.x >= a.y && a.x >= a.z) {
		face = d.x > 0.0 ? 0 : 1;
		uv = d.zy / a.x;
	} else if (a.y >= a.z) {
		face = d.y > 0.0 ? 2 : 3;
		uv = d.xz / a.y;
	} else {
		face = d.z > 0.0 ? 4 : 5;
		uv = d.xy / a.z;
	}
	vec2 e = atan(uv) * (4.0 / SKY_PI); // -1..1, equal angles
	ivec2 c = clamp(ivec2(floor((e * 0.5 + 0.5) * float(n))), ivec2(0), ivec2(n - 1));
	return ivec3(face, c);
}

// The unit direction through the centre of a cell.
vec3 skyCellDir(ivec3 c, int n) {
	vec2 uv = tan(((vec2(c.yz) + 0.5) / float(n) * 2.0 - 1.0) * (SKY_PI * 0.25));
	float s = (c.x & 1) == 0 ? 1.0 : -1.0;
	vec3 d;
	if (c.x < 2) {
		d = vec3(s, uv.y, uv.x);
	} else if (c.x < 4) {
		d = vec3(uv.x, s, uv.y);
	} else {
		d = vec3(uv.x, uv.y, s);
	}
	return normalize(d);
}

// The axis the sun and moon turn about, in world space. Iris turns the sky by rotY(-90) rotZ(sunPathRotation)
// rotX(angle), so every celestial point lies in the plane at right angles to (0, sin r, cos r). Made exactly
// perpendicular to the sun so the frames below stay orthonormal.
vec3 skyPathAxis(vec3 sunW) {
	float r = radians(sunPathRotation);
	vec3 a = vec3(0.0, sin(r), cos(r));
	return normalize(a - sunW * dot(a, sunW));
}

// ----------------------------------------------------------------------------------------------- sun and moon

// The art pixel of direction d on a body at unit direction b: false if d is far from it. The art grid's rows stay
// level with the world (so the pixels sit square on screen while the camera is level, whatever sunPathRotation
// tilts the path to); only a body near the zenith falls back to the path axis.
bool skyArtPixel(vec3 d, vec3 b, vec3 axis, out ivec2 ip) {
	float k = dot(d, b);
	ip = ivec2(0);
	if (k <= 0.5) {
		return false;
	}
	vec3 up = abs(b.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : axis;
	vec3 r = normalize(cross(up, b));
	vec3 v = cross(b, r);
	vec2 q = vec2(dot(d, r), dot(d, v)) / (k * float(SKY_ART_PIXEL) * 1e-4);
	ip = ivec2(floor(q));
	return true;
}

// The sun's pixel: 0 none, 1 core, 2 middle, 3 rim, 4 halo. A pixel circle SUN_SIZE pixels in radius.
int skySunPixel(ivec2 ip) {
	vec2 c = vec2(ip) + 0.5;
	float d2 = dot(c, c);
	float r = float(SUN_SIZE);
	if (d2 < (r - 2.6) * (r - 2.6)) {
		return 1;
	}
	if (d2 < (r - 1.3) * (r - 1.3)) {
		return 2;
	}
	if (d2 < r * r) {
		return 3;
	}
#ifdef SUN_HALO
	// one pixel ring, every other pixel: a halo you can count, not a bloom
	if (d2 < (r + 1.6) * (r + 1.6) && ((ip.x + ip.y) & 1) == 0) {
		return 4;
	}
#endif
	return 0;
}

// The moon's maria as a 10x10 bit picture (bit x of row y, row 0 at the top), scaled to MOON_SIZE.
const int SKY_MARIA[10] = int[10](0x000, 0x018, 0x09C, 0x188, 0x0C0, 0x006, 0x10E, 0x1C4, 0x080, 0x000);

// The moon's pixel for phase 0 (full) .. 4 (new) .. 7: 0 none, 1 lit, 2 maria, 3 terminator, 4 dark side.
// Phases 1..3 wane (lit on the -x side of the path frame), 5..7 wax (+x).
int skyMoonPixel(ivec2 ip, int phase) {
	float r = float(MOON_SIZE);
	vec2 c = vec2(ip) + 0.5;
	if (dot(c, c) >= r * r) {
		return 0;
	}
	vec2 n = c / r;
	float w = sqrt(max(0.0, 1.0 - n.y * n.y));
	float side = phase < 4 ? -1.0 : 1.0;
	float lim = -cos(float(phase) * SKY_PI * 0.25) * w;
	float sx = side * n.x;
	if (sx < lim) {
		return 4;
	}
	if (phase != 0 && sx < lim + 1.0 / r) {
		return 3;
	}
	ivec2 mp = clamp(ivec2(floor((n * 0.5 + 0.5) * 10.0)), ivec2(0), ivec2(9));
	if (((SKY_MARIA[9 - mp.y] >> mp.x) & 1) != 0) {
		return 2;
	}
	return 1;
}

// ------------------------------------------------------------------------------------------------------- the stars

// A sky-grid cell's star level: 0 none, 1 dim, 2 mid, 3 bright. Each candidate cell has a fixed rank; a star shows
// while its rank is under `amount`, so the brightest come out first and a clear moonless night shows them all.
int skyStarLevel(ivec3 cell, float amount, out uint seed) {
	seed = hashU(uvec3(uint(cell.y), uint(cell.z), uint(cell.x) * 977u + 13u));
	float h = float(seed & 0xFFFFu) / 65535.0;
	float most = float(STAR_DENSITY) * 0.001;
	if (h >= most) {
		return 0;
	}
	float rank = h / most;
	if (rank >= amount) {
		return 0;
	}
	int level = rank < 0.06 ? 3 : (rank < 0.30 ? 2 : 1);
#ifdef STAR_TWINKLE
	// now and then a star drops a level for a moment: twinkling in steps
	float phase = float((seed >> 16u) & 0xFFu) / 255.0;
	uint tick = uint(floor(frameTimeCounter * 1.7 + phase * 16.0));
	uint t = hashU(uvec3(uint(cell.y), uint(cell.z), tick + uint(cell.x) * 7919u));
	if ((t & 0xFFu) < 56u) {
		level -= 1;
	}
#endif
	return level;
}

// The stars at celestial direction c (the view direction in the sun's turning frame), `amount` 0..1, `elev` the
// view's elevation (stars sink a level near the horizon). Bright stars get a plus of dim arms.
vec3 skyStars(vec3 c, float amount, float elev) {
	int n = SKY_TEXELS;
	ivec3 cell = skyCell(c, n);
	uint seed;
	int level = skyStarLevel(cell, amount, seed);
	vec3 tint = vec3(1.0);
	uint kind = (seed >> 24u) % 6u;
	if (kind == 0u) {
		tint = vec3(1.0, 0.88, 0.72);
	} else if (kind == 1u) {
		tint = vec3(0.80, 0.88, 1.0);
	}
	if (level < 1) {
		// an arm of a bright neighbour
		tint = vec3(1.0);
		const ivec2 around[4] = ivec2[4](ivec2(1, 0), ivec2(-1, 0), ivec2(0, 1), ivec2(0, -1));
		for (int i = 0; i < 4; i++) {
			ivec3 nb = ivec3(cell.x, cell.yz + around[i]);
			if (nb.y < 0 || nb.z < 0 || nb.y >= n || nb.z >= n) {
				continue;
			}
			uint s2;
			if (skyStarLevel(nb, amount, s2) == 3) {
				level = 1;
				break;
			}
		}
	}
	if (elev < 0.06) {
		level -= 1;
	}
	if (level < 1) {
		return vec3(0.0);
	}
	return skyTable(15 + level, SKY_ROW_SPRITE).rgb * tint;
}

#endif
