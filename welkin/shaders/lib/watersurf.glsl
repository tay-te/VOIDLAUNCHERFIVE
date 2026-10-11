// VOID Welkin: the water's surface, for gbuffers_water (the chunks' water) and dh_water (the LOD's). One function, so
// near and far water read the same.
//
// The surface is the water's own colour lit like the land (its sky fill and, where it reaches, the sun) with the sky
// laid over it by a stylized Fresnel: a quarter of the sky looking straight down, most of it at a grazing angle, in
// the sky table's horizon and zenith colours along the reflected ray (warming toward the sun's glow). The reflection
// and the sky's fill stay whole in the shade; the shadow map and the clouds only take the sun's diffuse and its glint
// away, so shaded water stays a mid blue-grey close to the sky's colour instead of going navy. No noise, no waves:
// Minecraft's water is flat, and its gradient is smooth.

#ifndef WELKIN_WATER_SURFACE
#define WELKIN_WATER_SURFACE

#include "/lib/light.glsl"

#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif

struct WaterSurface {
	vec3 color;  // the surface, to blend over the bed
	float alpha; // its opacity (the reflection makes it less see-through)
	vec3 deep;   // what composite's water fog fades a deep bed into (lib/water.glsl)
};

// How much of the sky the surface shows, from the cosine between the view and the normal.
float waterFresnel(float cosV) {
	float g = 1.0 - clamp(cosV, 0.0, 1.0);
	return 0.24 + 0.66 * g * g * g * g;
}

// The sky seen in direction r (unit, world): horizon to zenith, warming toward the sun inside its glow.
vec3 waterSkyColour(vec3 r) {
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec3 hor = mix(skyHorizon(false), skyHorizon(true), skyGlowToward(normalize(vec3(r.x, max(r.y, 0.0), r.z)), sunW));
	return mix(hor, skyZenith(), smoothstep(0.0, 0.7, r.y));
}

// albedo: the water's texture times its biome tint; n: world normal; eyePos: camera-relative position; lm: light map
// levels; sunShare, sunReach, facing: as for welkinLightCore (sunReach from the shadow map where it covers).
WaterSurface welkinWaterSurface(vec4 albedo, vec3 n, vec3 eyePos, vec2 lm, vec3 sunShare, float sunReach, float facing) {
	WaterSurface w;
	vec3 v = normalize(eyePos);
	vec3 r = reflect(v, n);
	float f = waterFresnel(abs(dot(v, n)));
	// the sky only shows where the sky is: under a roof or in a cave the reflection fades with the sky light
	float open = smoothstep(0.45, 0.95, lm.y);
	vec3 sky = waterSkyColour(r) * open;
	// the water's own colour, lit: the sky's fill always, the sun's diffuse where it reaches
	vec3 body = welkinLightCore(albedo.rgb, n, lm, 0.0, sunShare, vec3(1.0), sunReach, facing);
	// the sun's glint: a smooth spot round the sun's reflection, only where the sun reaches
	vec3 l = lightDirWorld();
	float spec = pow(clamp(dot(r, l), 0.0, 1.0), 180.0) * 1.6 + pow(clamp(dot(r, l), 0.0, 1.0), 24.0) * 0.12;
	vec3 glint = skyDirectLight() * spec * sunReach * sunShare;
	w.color = mix(body, sky, f) + glint;
	w.alpha = mix(albedo.a, 1.0, f * 0.8);
	// the deep colour: the body's colour in the water's own shade, a share of the sky in it, so a deep pool in the
	// shade reads as water and not as a navy hole
	w.deep = mix(body * 0.62, sky * 0.7, 0.3) + glint * 0.5;
	return w;
}

#endif
