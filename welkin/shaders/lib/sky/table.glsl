// VOID Welkin sky (M2): the sky table's layout and its readers. The prepare pass writes the table into colortex5
// (64x32 RGBA8, size.buffer in shaders.properties) once a frame; anything after it reads it with texelFetch: the sky
// dome (gbuffers_skybasic), the light ramp (lib/light.glsl), the far fade and haze, water reflections, clouds.
//
// Its colours are control points: readers blend between neighbouring texels where they need a gradient (the dome),
// and every weight behind them changes smoothly with the hour and the weather. They are in the same display space as
// the pack's albedo and Minecraft's own sky and fog colours. Texel (x, y), y counted from the bottom row:
//
//   y 0..4    the dome, sun-glow ring g = y (0 none .. 4 nearest the sun); x = band 0 below the horizon,
//             1 horizon .. 5 zenith (x 6, 7 repeat the zenith)
//   y 5       light and state:
//               x 0  sun direct light, premultiplied by its strength (a: strength)
//               x 1  moon direct light, premultiplied (a: strength)
//               x 2  sky ambient light: colour times the day/night factor times Gloam's gameplay scale (a: factor)
//               x 3  the shadow light (sun by day, moon by night, as Iris's shadowLightPosition), premultiplied
//               x 4  horizon colour away from the sun   x 5  horizon toward the sun   x 6  zenith colour
//               x 7  lightning flash light (rgb premultiplied, a: flash step)
//               x 8  r sky light scale (void_light.z or 1), g night 0..1, b dark 0..1, a overcast 0..1
//               x 9  r sun-glow amount / 1.5, g moon-halo amount, b star amount, a glow spread / 2
//               x 10 r sun visibility, g moon visibility, b moon opacity, a flash step
//               x 11 r golden hour, g blue hour, b sunset palette position / 4, a moon brightness by phase
//   y 6       horizon colour away from the sun for haze kind x (0..15)
//   y 7       the same toward the sun
//   y 8       cloud faces: x 0 top, 1 sun side, 2 shade side, 3 base, 4 storm base, 5 rim
//   y 9       sprite palettes: x 0..3 sun core, middle, rim, halo; x 8..11 moon lit, maria, terminator, dark side;
//             x 16..18 stars dim, mid, bright
//   y 10..12  the dome with the moon's halo ring 1..3 (x as rows 0..4)
//   y 13..30  reserved (written black): rows for other modules' palettes go here
//   y 31      x 0: 'W' 'K' version 1 255, the check that prepare ran

#ifndef WELKIN_SKY_TABLE
#define WELKIN_SKY_TABLE

#ifndef WU_colortex5
#define WU_colortex5
uniform sampler2D colortex5;
#endif

#define SKY_TABLE_W 64
#define SKY_TABLE_H 32

#define SKY_ROW_DOME      0
#define SKY_ROW_LIGHT     5
#define SKY_ROW_HAZE_AWAY 6
#define SKY_ROW_HAZE_SUN  7
#define SKY_ROW_CLOUD     8
#define SKY_ROW_SPRITE    9
#define SKY_ROW_MOON_DOME 9  // + ring 1..3
#define SKY_ROW_CHECK     31

#define SKY_X_SUN        0
#define SKY_X_MOON       1
#define SKY_X_AMBIENT    2
#define SKY_X_SHADOW     3
#define SKY_X_HOR_AWAY   4
#define SKY_X_HOR_SUN    5
#define SKY_X_ZENITH     6
#define SKY_X_FLASH      7
#define SKY_X_STATE_LIGHT 8
#define SKY_X_STATE_GLOW  9
#define SKY_X_STATE_SHOW  10
#define SKY_X_STATE_TIME  11

#define SKY_CLOUD_TOP     0
#define SKY_CLOUD_SUNSIDE 1
#define SKY_CLOUD_SHADE   2
#define SKY_CLOUD_BASE    3
#define SKY_CLOUD_STORM   4
#define SKY_CLOUD_RIM     5

vec4 skyTable(int x, int y) {
	return texelFetch(colortex5, ivec2(x, y), 0);
}

// --- for the light ramp (lib/light.glsl), deferred and forward programs ---

// The sky's ambient light colour, ready to multiply by the sky light level (the ramp). Dark nights already in.
vec3 skyAmbientLight() {
	return skyTable(SKY_X_AMBIENT, SKY_ROW_LIGHT).rgb;
}

// The direct light of whichever body casts the shadows (shadowLightPosition), premultiplied by its strength.
vec3 skyDirectLight() {
	return skyTable(SKY_X_SHADOW, SKY_ROW_LIGHT).rgb;
}

vec3 skySunLight() {
	return skyTable(SKY_X_SUN, SKY_ROW_LIGHT).rgb;
}

vec3 skyMoonLight() {
	return skyTable(SKY_X_MOON, SKY_ROW_LIGHT).rgb;
}

// Lightning: the light a flash adds to anything under open sky (zero between flashes).
vec3 skyFlashLight() {
	return skyTable(SKY_X_FLASH, SKY_ROW_LIGHT).rgb;
}

// Gloam's gameplay sky-light scale (void_light.z, 1 without Gloam): the dark-nights factor.
float skyLightScale() {
	return skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).r;
}

// The night, 0 day .. 1 night, smooth.
float skyNightStep() {
	return skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).g;
}

// --- for haze, the far fade and fog ---

// The camera's horizon colour, away from the sun or toward it.
vec3 skyHorizon(bool towardSun) {
	return skyTable(towardSun ? SKY_X_HOR_SUN : SKY_X_HOR_AWAY, SKY_ROW_LIGHT).rgb;
}

vec3 skyZenith() {
	return skyTable(SKY_X_ZENITH, SKY_ROW_LIGHT).rgb;
}

// The horizon colour of haze kind `kind` (design table 2.4) at this hour and weather: what the haze module fades a
// far pixel into when the ray's samples carry that kind.
vec3 skyHazeHorizon(int kind, bool towardSun) {
	return skyTable(clamp(kind, 0, 15), towardSun ? SKY_ROW_HAZE_SUN : SKY_ROW_HAZE_AWAY).rgb;
}

// How far direction `dir` (unit, world) lies inside the sun's glow, 0..1: 1 straight at the sun, easing out as wide as
// the table says the glow reaches. The same reach the clouds' horizon uses (lib/clouds/shade.glsl wcHorizon).
float skyGlowToward(vec3 dir, vec3 sunW) {
	float spread = max(skyTable(SKY_X_STATE_GLOW, SKY_ROW_LIGHT).a * 2.0, 0.05);
	return 1.0 - smoothstep(0.0, spread, 1.0 - dot(dir, sunW));
}

// The haze kind's horizon along `dir`: the horizon away from the sun, warming smoothly into the one toward it inside
// the sun's glow, so the air has no seam across the screen.
vec3 skyHazeAir(int kind, vec3 dir, vec3 sunW) {
	return mix(skyHazeHorizon(kind, false), skyHazeHorizon(kind, true), skyGlowToward(dir, sunW));
}

// --- for clouds (vanilla's until M3, then the pack's voxel clouds) ---

vec3 skyCloud(int face) {
	return skyTable(face, SKY_ROW_CLOUD).rgb;
}

// --- checks ---

bool skyTableReady() {
	ivec4 m = ivec4(skyTable(0, SKY_ROW_CHECK) * 255.0 + 0.5);
	return m.x == 87 && m.y == 75 && m.z == 1;
}

#endif
