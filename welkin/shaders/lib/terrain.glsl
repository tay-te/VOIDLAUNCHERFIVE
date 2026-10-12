// VOID Welkin: the terrain's own light (rebuild workstream A, 2026-10-12). Past the near shadow map the land was lit
// evenly: the LOD's land is columns of blocks whose every top faces straight up, so a hillside turned from the sun took
// the same sun as one turned to it, and nothing off the screen cast a shadow. Here the land's shape comes from Gloam's
// near land tile (N0: the ground's height every 16 blocks, 4 km round the camera, lib/void.glsl), and two things are
// worked out from it:
//
// 1. The terrain's shadow (a heightfield shadow). Once a frame, for every cell of the tile, the prepare pass marches
//    toward the light over the heights (16-block steps near, wider further, out to 2 km or the land's relief) and keeps
//    the shadow line: the height above which the cell's column sees the light, ys = max over the march of
//    (ground(t) - t * tan(elevation)), with the distance t of the caster that set it (the penumbra widens with it). A
//    point is lit by how far it stands above that line, over a soft edge (no noise: the edge is a smoothstep, the line
//    bilinear between cells). So a ridge shades the valley behind it at a low sun, a hill's far side falls into its own
//    shade, and this from land off the screen as well. The pass keeps a second line made only of casters past the near
//    shadow map's reach, for the points inside it: the map sees the near casters, this the far ones.
// 2. The terrain's slope (macro normals). The same heights, Sobel-smoothed, give the land's own normal; a top face in the
//    middle and far distance takes the sun as the hillside it stands on would, so slopes turned from the sun darken
//    and slopes turned to a low sun catch it.
//
// The buffer: colortex15, 256 x 512 RGBA32F, rewritten every frame (prepare3, program/prepare_terrain.glsl), indexed as
// the tile is (each cell at its world cell mod 256): rows 0..255 the shadow lines (ys over all casters, its penumbra
// half width, ys over the far casters, its half width), rows 256..511 the land (Sobel gradient dy/dx and dy/dz, the
// ground, 1 where the cell holds land). Without Gloam (or its tile) every cell reads lit and level: the pack runs as
// before. Overworld only, with TERRAIN_SHADOWS.

#ifndef WELKIN_TERRAIN
#define WELKIN_TERRAIN

#include "/lib/settings.glsl"

/*
const int colortex15Format = RGBA32F;
const bool colortex15Clear = false;
*/

#if defined TERRAIN_SHADOWS && defined WORLD_OVERWORLD
#define WELKIN_TERRAIN_LIVE

#ifndef WU_cameraPosition
#define WU_cameraPosition
uniform vec3 cameraPosition;
#endif

const float TERRAIN_CELL = 16.0;     // blocks per cell (N0)
const float TERRAIN_SPREAD = 0.03;   // the penumbra's half width per block of distance to the caster
const float TERRAIN_BIAS = 1.0;      // blocks: a point this far under the line still counts as on it
// a stylization: the land's relief counts this much for its own shade (a ridge 60 blocks over the valley throws the
// shadow of one 90 blocks over it), so the far land's form reads at the golden hour as a storybook map's hill shading
const float TERRAIN_SHADOW_RELIEF = 1.5;
const float TERRAIN_FLOOR = 32.0;    // a line never lower than this under the cell's ground (lit anyway; keeps the
                                     // bilinear blend between cells on the scale of the land)

// ---- The prepare pass (program/prepare_terrain.glsl)
#ifdef WELKIN_TERRAIN_BUILD
#include "/lib/void.glsl"

// The ground of a cell (h16), or -1e4 where the tile holds nothing.
float terrainGround(ivec2 cell) {
	vec4 g = voidFetch(VOID_TILE_N0, cell);
	float h = voidHeight(g.r, g.g);
	return h < -1000.0 ? -1e4 : h;
}

// The ground between cell centres (cell c's centre at c + 0.5), bilinear.
float terrainGroundAt(vec2 q) {
	vec2 pq = q - 0.5;
	ivec2 c = ivec2(floor(pq));
	vec2 f = pq - vec2(c);
	float a = terrainGround(c);
	float b = terrainGround(c + ivec2(1, 0));
	float d = terrainGround(c + ivec2(0, 1));
	float e = terrainGround(c + ivec2(1, 1));
	return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

// One texel of the buffer. lightDir: world, toward the light; nearReach: how far the near shadow map's casters reach
// round the camera (blocks; 0 with no map).
vec4 terrainBuild(ivec2 p, vec3 lightDir, float nearReach) {
	bool lineRow = p.y < 256;
	vec4 none = lineRow ? vec4(-1e4, 1.0, -1e4, 1.0) : vec4(0.0, 0.0, 0.0, 0.0);
	if (voidMode() == VOID_MODE_NONE || !voidTileHasData(VOID_TILE_N0)) {
		return none;
	}
	ivec2 camCell = ivec2(floor(cameraPosition.xz / TERRAIN_CELL));
	ivec2 centre = voidCentre(VOID_TILE_N0, camCell);
	ivec2 t = ivec2(p.x, p.y & 255);
	ivec2 cell = centre + ((t - centre + 128) & 255) - 128;
	float g0 = terrainGround(cell);
	if (g0 < -1000.0) {
		return none;
	}
	if (!lineRow) {
		// the land's slope: a Sobel gradient over the 3x3 cells round it (a cell without land counts as this one)
		float h[9];
		for (int j = 0; j < 3; j++) {
			for (int i = 0; i < 3; i++) {
				float v = terrainGround(cell + ivec2(i - 1, j - 1));
				h[i + 3 * j] = v < -1000.0 ? g0 : v;
			}
		}
		float gx = (h[2] + 2.0 * h[5] + h[8]) - (h[0] + 2.0 * h[3] + h[6]);
		float gz = (h[6] + 2.0 * h[7] + h[8]) - (h[0] + 2.0 * h[1] + h[2]);
		return vec4(gx, gz, 0.0, 0.0) / (8.0 * TERRAIN_CELL) + vec4(0.0, 0.0, g0, 1.0);
	}
	// the shadow lines
	float hl = length(lightDir.xz);
	float floorY = g0 - TERRAIN_FLOOR;
	if (lightDir.y <= 0.0 || hl < 1e-3) {
		return vec4(floorY, 1.0, floorY, 1.0); // the light straight overhead (or under the land: the sky table's dark)
	}
	float tanE = max(lightDir.y, 0.01) / hl;
	vec2 dir = lightDir.xz / hl;
	vec2 q0 = vec2(cell) + 0.5;
	vec2 cam = cameraPosition.xz;
	vec2 at = q0 * TERRAIN_CELL;
	float ysAll = floorY, tAll = 16.0;
	float ysOut = floorY, tOut = 16.0;
	// out to 2 km, or until the ray stands 400 blocks over the cell (no land that tall round it)
	float tMax = min(2048.0, 400.0 / tanE);
	float tt = 0.0;
	for (int i = 0; i < 72; i++) {
		// 16-block steps, growing by a third of a cell a step (out to 2 km in about 60 steps)
		tt += TERRAIN_CELL * (1.0 + float(i) / 24.0);
		if (tt > tMax) {
			break;
		}
		vec2 q = q0 + dir * (tt / TERRAIN_CELL);
		ivec2 dc = ivec2(floor(q)) - centre;
		if (max(abs(dc.x), abs(dc.y)) > 126) {
			break; // off the tile
		}
		float v = g0 + (terrainGroundAt(q) - g0) * TERRAIN_SHADOW_RELIEF - tt * tanE;
		if (v > ysAll) {
			ysAll = v;
			tAll = tt;
		}
		if (v > ysOut && length(at + dir * tt - cam) > nearReach) {
			ysOut = v;
			tOut = tt;
		}
	}
	return vec4(ysAll, 0.75 + TERRAIN_SPREAD * tAll, ysOut, 0.75 + TERRAIN_SPREAD * tOut);
}
#endif

// ---- The lookups (deferred_shadow, the light)
#ifndef WELKIN_TERRAIN_BUILD
#ifndef WU_colortex15
#define WU_colortex15
uniform sampler2D colortex15;
#endif

struct TerrainCell {
	vec4 line;   // ys over all casters, its half width, ys over the far casters, its half width
	vec2 grad;   // the ground's gradient (dy/dx, dy/dz)
	float w;     // how far to trust it: 0 without data, fading out toward the tile's edge (1.6..1.9 km)
};

// The land at a camera-relative point: bilinear between the four nearest cells.
TerrainCell terrainAt(vec3 playerPos) {
	TerrainCell r;
	r.line = vec4(-1e4, 1.0, -1e4, 1.0);
	r.grad = vec2(0.0);
	r.w = 0.0;
	vec2 wxz = cameraPosition.xz + playerPos.xz;
	vec2 pq = wxz / TERRAIN_CELL - 0.5;
	ivec2 c = ivec2(floor(pq));
	vec2 f = pq - vec2(c);
	// the tile is centred on the camera's cell; past about 100 cells its cells may still be filling
	ivec2 camCell = ivec2(floor(cameraPosition.xz / TERRAIN_CELL));
	ivec2 dc = abs(c - camCell);
	float edge = 1.0 - smoothstep(100.0, 118.0, float(max(dc.x, dc.y)));
	if (edge <= 0.0) {
		return r;
	}
	vec4 wts = vec4((1.0 - f.x) * (1.0 - f.y), f.x * (1.0 - f.y), (1.0 - f.x) * f.y, f.x * f.y);
	vec4 line = vec4(0.0);
	vec4 land = vec4(0.0);
	float ws = 0.0;
	for (int k = 0; k < 4; k++) {
		ivec2 t = (c + ivec2(k & 1, k >> 1)) & 255;
		vec4 l = texelFetch(colortex15, ivec2(t.x, t.y + 256), 0);
		float w = wts[k] * l.w;
		line += texelFetch(colortex15, t, 0) * w;
		land += l * w;
		ws += w;
	}
	if (ws < 1e-3) {
		return r;
	}
	r.line = line / ws;
	r.grad = land.xy / ws;
	r.w = edge * clamp(ws * 4.0, 0.0, 1.0);
	return r;
}

// What of the light the terrain lets reach a point at world height y under a shadow line ys with half width h.
float terrainLineVis(float y, float ys, float h) {
	return smoothstep(-1.0, 1.0, (y - ys + TERRAIN_BIAS) / h);
}

// The terrain's shadow at a camera-relative point: all (every caster; past the near map) and far (only the casters past
// the near map's reach; for points inside it). longFade: lib/shadow.glsl shadowLongFadeStrength now.
void terrainShadow(TerrainCell tc, vec3 playerPos, float longFade, out float all, out float far) {
	float y = cameraPosition.y + playerPos.y;
	all = terrainLineVis(y, tc.line.x, tc.line.y);
	far = terrainLineVis(y, tc.line.z, tc.line.w);
	// the low sun's long shadows thin with their length, as the map's (the half width says how far the caster stands)
	float ta = (tc.line.y - 0.75) / TERRAIN_SPREAD;
	float tf = (tc.line.w - 0.75) / TERRAIN_SPREAD;
	all = mix(all, 1.0, longFade * smoothstep(56.0, 144.0, ta));
	far = mix(far, 1.0, longFade * smoothstep(56.0, 144.0, tf));
	all = mix(1.0, all, tc.w);
	far = mix(1.0, far, tc.w);
}

// The land's own normal there (world), its slope steepened `relief` times (a stylized hill shading: the far land's
// gentle 16-block-scale slopes read as form), up where nothing is known.
vec3 terrainNormal(TerrainCell tc, float relief) {
	vec2 g = tc.grad * (tc.w * relief);
	return normalize(vec3(-g.x, 1.0, -g.y));
}
#endif

#endif
#endif
