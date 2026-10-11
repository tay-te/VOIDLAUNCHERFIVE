// VOID Welkin sky (M2): the sky seen in a direction, from the sky table. Entry points:
//
//   SkyFrame skyFrame()                       once per fragment: sun, moon, path axis, the glow and show states
//   vec3 skyFull(vec3 dirW, SkyFrame f)       the whole sky: dome bands, glow rings, stars, moon, sun
//   vec3 skyDomeOnly(vec3 dirW, SkyFrame f)   the dome without bodies or stars (water reflections, clouds' backdrop)
//   vec3 skyHorizonFor(vec3 eyeDir, SkyFrame f, float dither)
//                                             the horizon band in that direction's azimuth, with the sun's glow
//                                             (the far fade, haze edges)
//
// dirW is a world-space direction (any length). The dome is a stepped lookup: the band (elevation) and the glow ring
// (nearness to the sun, or to the moon at night) are worked out at the centre of the direction's sky texel, dithered
// with a 4x4 Bayer on the sky grid at band and ring edges, and the colour is one texelFetch from the table.

#ifndef WELKIN_SKY_DOME
#define WELKIN_SKY_DOME

#include "/lib/common.glsl"
#include "/lib/sky/settings.glsl"
#include "/lib/sky/table.glsl"
#include "/lib/sky/uniforms.glsl"
#include "/lib/sky/celestial.glsl"

// how wide the dithered strip at a band or ring edge is, as a share of a step
#define SKY_EDGE_DITHER 0.3
// below this elevation (about -2.3 degrees) no sun, moon or star is drawn: the land hides them
#define SKY_BODY_CUTOFF -0.04

struct SkyFrame {
	vec3 sunW;
	vec3 moonW;
	vec3 axis;
	vec4 glow; // r sun-glow amount / 1.5, g moon halo, b stars, a spread / 2
	vec4 show; // r sun visibility, g moon visibility, b moon opacity, a flash
};

SkyFrame skyFrame() {
	SkyFrame f;
	f.sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	f.moonW = normalize(mat3(gbufferModelViewInverse) * moonPosition);
	f.axis = skyPathAxis(f.sunW);
	f.glow = skyTable(SKY_X_STATE_GLOW, SKY_ROW_LIGHT);
	f.show = skyTable(SKY_X_STATE_SHOW, SKY_ROW_LIGHT);
	return f;
}

// The dome's indices at sky-texel centre dc: x band (0 below the horizon, 1..5), y sun ring (0..4), z moon ring
// (0..3).
ivec3 skyDomeIndex(vec3 dc, float dither, SkyFrame f) {
	float h = dc.y;
	int band = 0;
	if (h >= 0.0) {
		// bands thin near the horizon, broad overhead: edges near 2.7, 11, 25 and 49 degrees
		band = 1 + int(clamp(floor(sqrt(h) * 4.6 + (dither - 0.5) * SKY_EDGE_DITHER), 0.0, 4.0));
	}
	float amount = f.glow.r * 1.5;
	float spread = max(f.glow.a * 2.0, 0.01);
	float shape = clamp(1.0 - (1.0 - dot(dc, f.sunW)) / spread, 0.0, 1.0);
	shape *= h >= 0.0 ? mix(1.0, 0.5, clamp(h * 2.5, 0.0, 1.0)) : 0.6; // the glow hugs the horizon, less below it
	int ring = int(clamp(floor(shape * amount * 4.0 + (dither - 0.5) * SKY_EDGE_DITHER * 1.5), 0.0, 4.0));
	float shapeM = clamp(1.0 - (1.0 - dot(dc, f.moonW)) / 0.05, 0.0, 1.0);
	int moonRing = int(clamp(floor(shapeM * f.glow.g * 3.0 + (dither - 0.5) * SKY_EDGE_DITHER * 1.5), 0.0, 3.0));
	return ivec3(band, ring, moonRing);
}

vec3 skyDomeColour(ivec3 i) {
	int row = i.z > i.y ? SKY_ROW_MOON_DOME + i.z : SKY_ROW_DOME + i.y;
	return skyTable(i.x, row).rgb;
}

float skyGridDither(ivec3 cell) {
#ifdef DITHER
	return bayer4(cell.yz);
#else
	return 0.5;
#endif
}

vec3 skyDomeOnly(vec3 dirW, SkyFrame f) {
	ivec3 cell = skyCell(dirW, SKY_TEXELS);
	return skyDomeColour(skyDomeIndex(skyCellDir(cell, SKY_TEXELS), skyGridDither(cell), f));
}

vec3 skyFull(vec3 dirW, SkyFrame f) {
	vec3 d = normalize(dirW);
	ivec3 cell = skyCell(d, SKY_TEXELS);
	vec3 dc = skyCellDir(cell, SKY_TEXELS);
	ivec3 idx = skyDomeIndex(dc, skyGridDither(cell), f);
#if SKY_DEBUG == 2
	return vec3(float(idx.x) / 5.0, float(idx.y) / 4.0, float(idx.z) / 3.0);
#endif
	vec3 col = skyDomeColour(idx);
	if (d.y < SKY_BODY_CUTOFF) {
		return col;
	}

	// stars, on the celestial sphere: the view direction in the sun's turning frame
	if (f.glow.b > 0.0) {
		vec3 c = vec3(dot(d, f.sunW), dot(d, f.axis), dot(d, cross(f.sunW, f.axis)));
		col = max(col, skyStars(c, f.glow.b, dc.y));
	}

#ifdef SKY_PIXEL_SUN
	ivec2 ip;
	// the moon: lit side, maria and terminator by phase; its dark side hides the stars at night
	if (f.show.g > 0.0 && skyArtPixel(d, f.moonW, f.axis, ip)) {
		int k = skyMoonPixel(ip, moonPhase & 7);
		if (k == 4) {
			col = mix(col, skyTable(11, SKY_ROW_SPRITE).rgb, f.show.g * step(0.75, f.show.b));
		} else if (k > 0) {
			col = mix(col, skyTable(7 + k, SKY_ROW_SPRITE).rgb, f.show.g * f.show.b);
		}
	}
	// the sun: core, middle, rim, and a counted halo; behind cloud a paler disc in steps
	if (f.show.r > 0.0 && skyArtPixel(d, f.sunW, f.axis, ip)) {
		int k = skySunPixel(ip);
		if (k == 4) {
			col = mix(col, skyTable(3, SKY_ROW_SPRITE).rgb, 0.5 * f.show.r);
		} else if (k > 0) {
			col = mix(col, skyTable(k - 1, SKY_ROW_SPRITE).rgb, f.show.r);
		}
	}
#endif
	return col;
}

vec3 skyFull(vec3 dirW) {
	return skyFull(dirW, skyFrame());
}

vec3 skyHorizonFor(vec3 eyeDir, SkyFrame f, float dither) {
	vec2 flat2 = eyeDir.xz;
	if (dot(flat2, flat2) < 1e-8) {
		flat2 = vec2(1.0, 0.0);
	}
	vec2 n2 = normalize(flat2);
	vec3 dc = normalize(vec3(n2.x, 0.02, n2.y));
	ivec3 idx = skyDomeIndex(dc, dither, f);
	idx.x = 1;
	return skyDomeColour(idx);
}

#endif
