// VOID Welkin: the stepped light ramp. One function lights everything, in deferred for terrain and the LOD and
// forward for everything else, so near land, far land and entities share the same few flat light levels.
//
// The light is Minecraft's: sky light, block light and face shading, with a sun (or moon) term on top. Each term is
// rounded to LIGHT_STEPS flat levels; the edges between levels are Bayer-dithered on the world's 16-pixel grid by the
// caller's `dither` threshold (0.5 for none).

#ifndef WELKIN_LIGHT
#define WELKIN_LIGHT

#include "/lib/common.glsl"

uniform vec3 shadowLightPosition; // the sun by day, the moon by night, in view space
uniform vec3 sunPosition;
uniform float sunAngle;           // 0 sunrise, 0.25 noon, 0.5 sunset
uniform float rainStrength;
uniform float nightVision;
uniform mat4 gbufferModelViewInverse;

// How high the sun stands: -1..1.
float sunHeight() {
	return sin(sunAngle * 6.2831853);
}

// The day share of the light, 0 at night to 1 by day: a time fade, not a screen gradient.
float dayAmount() {
	return clamp(sunHeight() * 5.0 + 0.35, 0.0, 1.0);
}

vec3 lightDirWorld() {
	return normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
}

// Vanilla's light curve for a level in [0, 1] (darker mids than linear), then rounded to the ramp's steps.
float rampStep(float level, float dither) {
	float l = level / (3.2 - 2.2 * level);
	return steps(l, float(LIGHT_STEPS), dither);
}

// Face shading as Minecraft does it, continuous for entity normals: top 1.0, north/south 0.8, east/west 0.6,
// bottom 0.6.
float faceShade(vec3 n) {
	return clamp(0.8 + 0.2 * n.y - 0.2 * abs(n.x), 0.5, 1.0);
}

// The lit colour of `albedo` with world normal `n`, light map levels `lm` (x block, y sky, both 0..1), emission
// 0..1 and the dither threshold.
vec3 welkinLight(vec3 albedo, vec3 n, vec2 lm, float emission, float dither) {
	float face = faceShade(n);
	float block = rampStep(lm.x, dither);
	vec3 blockCol = vec3(1.00, 0.80, 0.56) * 1.15;
#if defined WORLD_NETHER
	vec3 ambient = vec3(0.52, 0.40, 0.34) * face;
	vec3 direct = vec3(0.0);
#elif defined WORLD_END
	vec3 ambient = vec3(0.44, 0.40, 0.52) * face;
	vec3 direct = vec3(0.0);
#else
	float sky = rampStep(lm.y, dither);
	float day = dayAmount();
	float low = 1.0 - clamp(sunHeight() * 3.5, 0.0, 1.0);
	vec3 skyDay = mix(vec3(0.60, 0.66, 0.76), vec3(0.62, 0.56, 0.58), low * 0.6);
	vec3 skyNight = vec3(0.14, 0.16, 0.25);
	vec3 skyCol = mix(skyNight, skyDay, day) * mix(1.0, 0.72, rainStrength);
	vec3 ambient = skyCol * sky * face;

	// the sun (or the moon) on faces turned to it, in three flat steps, only where the sky reaches
	vec3 sunCol = mix(vec3(1.00, 0.95, 0.86), vec3(1.00, 0.70, 0.42), low) * 0.50;
	vec3 moonCol = vec3(0.30, 0.36, 0.52) * 0.30;
	vec3 lightCol = mix(moonCol, sunCol, day) * (1.0 - rainStrength * 0.85);
	float ndl = dot(n, lightDirWorld());
	float lit = ndl > 0.6 ? 1.0 : (ndl > 0.2 ? 0.6 : (ndl > 0.0 ? 0.3 : 0.0));
	float open = steps(clamp((lm.y - 0.55) * 2.6, 0.0, 1.0), 2.0, dither);
	vec3 direct = lightCol * lit * open;
#endif
	vec3 light = ambient + direct + blockCol * block;
	light = max(light, vec3(0.045)); // never quite black: cave walls still read
	light = mix(light, max(light, vec3(0.55)), nightVision);
	return albedo * light + albedo * emission;
}

#endif
