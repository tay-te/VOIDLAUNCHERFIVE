// VOID Welkin (Look D9): the meadow's variety. Minecraft tints every grass block and blade of a biome one flat colour,
// so a sunlit field reads as one even sheet. KCD II's fields are patchwork: drier straw-olive swathes, lusher deep-moss
// hollows. This lays a slow world-space variation over the grass's albedo (blocks, blades, tall grass, ground cover;
// the far land's grass too): two octaves of smooth value noise, some 40 and 12 blocks across, drawing the colour a
// little toward a dry olive-ochre or a deep moss. Smooth over many blocks, so it is no grain; the texture's own
// texels are untouched in their pattern, only their colour leans.

#ifndef WELKIN_VARIETY
#define WELKIN_VARIETY

#include "/lib/materials.glsl"

// how far the colour leans (0 none)
#define VARIETY_AMOUNT 1.0
// the two leans, as multipliers on the linear-ish albedo (display values here; their luminance is near 1)
#define VARIETY_DRY vec3(1.14, 1.02, 0.74)
#define VARIETY_LUSH vec3(0.78, 0.95, 0.90)

float varietyHash(vec2 c) {
	c = mod(c, 4096.0);
	return fract(sin(dot(c, vec2(127.1, 311.7))) * 43758.5453);
}

float varietyNoise(vec2 x) {
	vec2 i = floor(x);
	vec2 f = x - i;
	vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
	float a = varietyHash(i), b = varietyHash(i + vec2(1.0, 0.0));
	float c = varietyHash(i + vec2(0.0, 1.0)), d = varietyHash(i + vec2(1.0, 1.0));
	return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

bool varietyMaterial(int m) {
	return m == M_GRASS_BLOCK || m == M_PLANT || m == M_PLANT_TALL_LOWER || m == M_PLANT_TALL_UPPER
		|| m == M_GROUND_COVER || m == M_CROP;
}

// albedo: display-value colour; world: the point's world position (xz)
vec3 varietyAlbedo(vec3 albedo, vec2 world) {
	// only the tinted grass's own colour (a yellow-green to green hue, some saturation): dirt, flowers and stems keep theirs
	float mx = max(albedo.r, max(albedo.g, albedo.b));
	float mn = min(albedo.r, min(albedo.g, albedo.b));
	float sat = (mx - mn) / max(mx, 1e-4);
	float grassy = smoothstep(0.12, 0.3, sat) * smoothstep(0.92, 1.05, albedo.g / max(albedo.r, 1e-4) + 0.25)
		* step(albedo.b, albedo.g);
	if (grassy <= 0.0) {
		return albedo;
	}
	float v = 0.62 * varietyNoise(world / 41.0) + 0.38 * varietyNoise(world / 12.5 + vec2(17.3, 5.1));
	v = clamp((v - 0.5) * 2.4, -1.0, 1.0); // -1 lush .. 1 dry
	vec3 lean = v > 0.0 ? mix(vec3(1.0), VARIETY_DRY, v) : mix(vec3(1.0), VARIETY_LUSH, -v);
	return albedo * mix(vec3(1.0), lean, grassy * VARIETY_AMOUNT);
}

#endif
