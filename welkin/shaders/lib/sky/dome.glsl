// VOID Welkin sky (M2): the sky seen in a direction, from the sky table. Entry points:
//
//   SkyFrame skyFrame()                       once per fragment: sun, moon, path axis, the glow and show states
//   vec3 skyFullScene(vec3 dirW, SkyFrame f)  the whole sky: dome bands, glow rings, stars, moon, sun (scene light)
//   vec3 skyDomeOnly(vec3 dirW, SkyFrame f)   the dome without bodies or stars (water reflections; scene light)
//   vec3 skyHorizonFor(vec3 eyeDir, SkyFrame f)
//                                             the horizon band in that direction's azimuth (the far fade; scene light)
//   vec3 skyDomeColour(vec3 q)                the authored dome colour (display values, rows 0..12) for the
//                                             modules that still author their colours in display values (clouds)
//
// dirW is a world-space direction (any length). The dome is a smooth lookup into the table: the band (elevation) and
// the glow ring (nearness to the sun, or to the moon at night) are worked out as continuous positions for the exact
// view direction, eased into soft bands at the table's bands and rings (lib/common.glsl softBands, SOFT_BANDS of the
// way), and the colour is blended from the neighbouring table texels: a smooth gradient with gentle banding, no edge,
// no dither. Only the stars keep the sky's pixel grid (lib/sky/celestial.glsl), and the sun and moon their art grid.

#ifndef WELKIN_SKY_DOME
#define WELKIN_SKY_DOME

#include "/lib/common.glsl"
#include "/lib/sky/settings.glsl"
#include "/lib/sky/table.glsl"
#include "/lib/sky/uniforms.glsl"
#include "/lib/sky/celestial.glsl"
// atmos (welkin-atmos): the luminous sky
#include "/lib/sky/luminous.glsl"
#include "/lib/hdr.glsl"

// below this elevation (about -2.3 degrees) no sun, moon or star is drawn: the land hides them
#define SKY_BODY_CUTOFF -0.04
// how far under the horizon (as a direction's y) the dome takes to darken into the band below it
#define SKY_BELOW_EASE 0.035

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

// The dome's position in the table for unit direction d, all continuous: x band (0 below the horizon, 1 the horizon
// .. 5 the zenith), y sun ring (0..4), z moon ring (0..3).
vec3 skyDomePos(vec3 d, SkyFrame f) {
	float h = d.y;
	float band;
	if (h >= 0.0) {
		// band centres near 0.7, 4, 11, 21 and 40 degrees: thin near the horizon, broad overhead
		// (the lowest bands, where a dawn or dusk sky changes fastest, are not terraced: there a soft band read as a
		// hard step)
		float x = clamp(sqrt(h) * 4.6 - 0.5, 0.0, 4.0) / 4.0;
		band = 1.0 + 4.0 * mix(x, softBands(x, 4.0), smoothstep(0.25, 0.6, x));
	} else {
		band = 1.0 - smoothstep(0.0, SKY_BELOW_EASE, -h);
	}
	float amount = f.glow.r * 1.5;
	float spread = max(f.glow.a * 2.0, 0.01);
	float shape = clamp(1.0 - (1.0 - dot(d, f.sunW)) / spread, 0.0, 1.0);
	shape *= mix(0.6, mix(1.0, 0.5, clamp(h * 2.5, 0.0, 1.0)), smoothstep(-0.02, 0.0, h)); // hugs the horizon
	// the rings start where the stepped version's first ring did (a quarter of the glow's strength; a third for the
	// moon's), so a faint glow does not tint the whole dome, and reach the last at full strength
	// (the rings hug the horizon, where they are not terraced either: in the low sky they lay as hard steps)
	float rx = clamp((shape * amount * 4.0 - 0.5) / 3.5, 0.0, 1.0);
	// (round 1: eased at both ends, so the glow's edge has no kink: a corner in the gradient read as a seam between a
	// rose half and a grey-blue half of a dusk sky)
	rx = rx * rx * (3.0 - 2.0 * rx);
	float ring = 4.0 * mix(rx, softBands(rx, 4.0), smoothstep(0.08, 0.3, h));
	float shapeM = clamp(1.0 - (1.0 - dot(d, f.moonW)) / 0.05, 0.0, 1.0);
	float moonRing = 3.0 * softBands(clamp((shapeM * f.glow.g * 3.0 - 0.5) / 2.5, 0.0, 1.0), 3.0);
	return vec3(band, ring, moonRing);
}

// One table row at a smooth band position x (0..5).
vec3 skyRowAt(float x, int row) {
	float i = min(floor(x), 4.0);
	int a = int(i);
	return mix(skyTable(a, row).rgb, skyTable(a + 1, row).rgb, clamp(x - i, 0.0, 1.0));
}

// The dome's colour at a smooth table position: the sun's glow rings blended between rows 0..4, then the moon's halo
// (rows 10..12 over ring 0) added where it brightens the sky.
vec3 skyDomeColour(vec3 q) {
	float ri = min(floor(q.y), 3.0);
	int r = int(ri);
	vec3 c = mix(skyRowAt(q.x, SKY_ROW_DOME + r), skyRowAt(q.x, SKY_ROW_DOME + r + 1), clamp(q.y - ri, 0.0, 1.0));
	if (q.z > 0.0) {
		vec3 base = skyRowAt(q.x, SKY_ROW_DOME);
		float mi = min(floor(q.z), 2.0);
		int m = int(mi);
		vec3 a = m == 0 ? base : skyRowAt(q.x, SKY_ROW_MOON_DOME + m);
		vec3 b = skyRowAt(q.x, SKY_ROW_MOON_DOME + m + 1);
		c += max(mix(a, b, clamp(q.z - mi, 0.0, 1.0)) - base, 0.0);
	}
	return c;
}






// ==== (HDR stage A) the sky in scene-linear light (lib/hdr.glsl's unit), for gbuffers_skybasic ====
// The dome's colour points in the table's scene rows (14..21: the display rows through the output transform's
// inverse at exposure 1, so at exposure 1 the dome shows its tuned colours), blended in linear light; the luminous sky
// over them in linear light (skyLuminousLin: no cap, no golden headroom, the aureole); then the stars, the moon and
// the sun as emitters far brighter than the sky (crisp pixel art, glowing above it: the output transform's shoulder
// keeps their colour, the bloom spreads their light).

// the pixel sun's disc (its core; middle and rim by the art's own palette), the moon's lit face and the brightest star
#define SKY_SUN_RADIANCE 1500.0
#define SKY_SUN_RADIANCE_LOW 600.0
#define SKY_MOON_RADIANCE 2.0
#define SKY_STAR_RADIANCE 1.5

vec3 skyRowAtScene(float x, int row) {
	float i = min(floor(x), 4.0);
	int a = int(i);
	return mix(skyTable(a, row).rgb, skyTable(a + 1, row).rgb, clamp(x - i, 0.0, 1.0));
}

vec3 skyDomeColourScene(vec3 q) {
	float ri = min(floor(q.y), 3.0);
	int r = int(ri);
	vec3 c = mix(skyRowAtScene(q.x, SKY_ROW_DOME_SCENE + r), skyRowAtScene(q.x, SKY_ROW_DOME_SCENE + r + 1),
		clamp(q.y - ri, 0.0, 1.0));
	if (q.z > 0.0) {
		vec3 base = skyRowAtScene(q.x, SKY_ROW_DOME_SCENE);
		float mi = min(floor(q.z), 2.0);
		int m = int(mi);
		vec3 a = m == 0 ? base : skyRowAtScene(q.x, SKY_ROW_MOON_SCENE + m);
		vec3 b = skyRowAtScene(q.x, SKY_ROW_MOON_SCENE + m + 1);
		c += max(mix(a, b, clamp(q.z - mi, 0.0, 1.0)) - base, 0.0);
	}
	return c;
}

// skyLowSunBandOver in linear light: the band's colour (a display colour in the table) taken to scene light once
vec3 skyLowSunBandOverScene(vec3 col, vec3 d, SkyFrame f) {
	vec4 b = skyLowSunBand();
	if (b.a <= 0.0) {
		return col;
	}
	vec2 sz = f.sunW.xz;
	float az = 0.0;
	if (dot(d.xz, d.xz) > 1e-8 && dot(sz, sz) > 1e-8) {
		az = dot(normalize(d.xz), normalize(sz));
	}
	float wAz = 0.5 + 0.5 * az;
	wAz *= wAz;
	float wEl = exp(-max(d.y, 0.0) / 0.10) * (1.0 - smoothstep(0.0, 2.0 * SKY_BELOW_EASE, -d.y));
	float w = clamp(b.a * wAz * wEl, 0.0, 1.0);
	vec3 bL = sceneFromAuthored(clamp(b.rgb, 0.0, 1.0));
	col = mix(col, max(col, bL), w);
	float wAnti = 0.5 - 0.5 * az;
	wAnti *= wAnti;
	float wElA = exp(-max(d.y, 0.0) / 0.22) * (1.0 - smoothstep(0.0, 2.0 * SKY_BELOW_EASE, -d.y));
	vec3 rose = sceneFromAuthored((vec3(0.86, 0.64, 0.70) * max(dot(b.rgb, vec3(0.2126, 0.7152, 0.0722)) * 0.62, 0.0)));
	float wa = clamp(b.a * wAnti * wElA * 0.55, 0.0, 1.0);
	return mix(col, max(col, rose), wa);
}

// The dome without bodies or stars, in scene light (water and glass reflections, the puddles, the SSR's sky).
vec3 skyDomeOnly(vec3 dirW, SkyFrame f) {
	vec3 d = normalize(dirW);
	return skyLuminousLin(skyLowSunBandOverScene(skyDomeColourScene(skyDomePos(d, f)), d, f), d, skyLumFrame(f.sunW), 1.0)
		+ skyNightGlow(d, f.sunW, f.moonW); // (Look D9)
}

// The horizon band in eyeDir's azimuth, with the sun's glow there, in scene light (the far fade).
vec3 skyHorizonFor(vec3 eyeDir, SkyFrame f) {
	vec2 flat2 = eyeDir.xz;
	if (dot(flat2, flat2) < 1e-8) {
		flat2 = vec2(1.0, 0.0);
	}
	vec2 n2 = normalize(flat2);
	vec3 q = skyDomePos(normalize(vec3(n2.x, 0.02, n2.y)), f);
	q.x = 1.0;
	return skyLuminousLin(skyDomeColourScene(q), normalize(vec3(n2.x, 0.0, n2.y)), skyLumFrame(f.sunW), 1.0);
}

vec3 skyFullScene(vec3 dirW, SkyFrame f) {
	vec3 d = normalize(dirW);
	vec3 q = skyDomePos(d, f);
#if SKY_DEBUG == 2
	return vec3(q.x / 5.0, q.y / 4.0, q.z / 3.0);
#endif
	vec3 col = skyLowSunBandOverScene(skyDomeColourScene(q), d, f);
	col = skyLuminousLin(col, d, skyLumFrame(f.sunW), 1.0);
	col += skyNightGlow(d, f.sunW, f.moonW); // (Look D9)
	if (d.y < SKY_BODY_CUTOFF) {
		return col;
	}
	// the stars, behind the sky's own light (they fade out over a bright sky: a display luma of 0.08 .. 0.22)
	float skyLight = dot(col, vec3(0.2126, 0.7152, 0.0722));
	float behind = 1.0 - smoothstep(0.0039, 0.036, skyLight);
	if (f.glow.b * behind > 0.0) {
		vec3 c = vec3(dot(d, f.sunW), dot(d, f.axis), dot(d, cross(f.sunW, f.axis)));
		vec3 st = toLinear(skyStars(c, f.glow.b, d.y)) * SKY_STAR_RADIANCE;
		col = max(col, mix(col, st, behind));
	}
#ifdef SKY_PIXEL_SUN
	ivec2 ip;
	// the moon: lit side, maria and terminator by phase, an emitter; its dark side (the sky's own colour, darker) hides
	// the stars
	if (f.show.g > 0.0 && skyArtPixel(d, f.moonW, f.axis, ip)) {
		int k = skyMoonPixel(ip, moonPhase & 7);
		if (k == 4) {
			col = mix(col, sceneFromAuthored(skyTable(11, SKY_ROW_SPRITE).rgb), f.show.g * step(0.75, f.show.b));
		} else if (k > 0) {
			col = mix(col, toLinear(skyTable(7 + k, SKY_ROW_SPRITE).rgb) * SKY_MOON_RADIANCE, f.show.g * f.show.b);
		}
	}
	// the sun: core, middle, rim, an emitter far above its sky (dimmer and warmer low over the horizon), and a one-pixel
	// ring at a tenth of it; behind cloud a paler disc (the sun's visibility)
	if (f.show.r > 0.0 && skyArtPixel(d, f.sunW, f.axis, ip)) {
		int k = skySunPixel(ip);
		float rad = mix(SKY_SUN_RADIANCE_LOW, SKY_SUN_RADIANCE, smoothstep(0.0, 0.4, f.sunW.y));
		if (k == 4) {
			col = mix(col, max(col, toLinear(skyTable(3, SKY_ROW_SPRITE).rgb) * (0.1 * rad)), 0.3 * f.show.r);
		} else if (k > 0) {
			// (the core brightest, the middle and the rim a step under it, so the art reads by light as well as hue)
			float ring = k == 1 ? 1.0 : (k == 2 ? 0.6 : 0.35);
			col = mix(col, toLinear(skyTable(k - 1, SKY_ROW_SPRITE).rgb) * (rad * ring), f.show.r);
		}
	}
#endif
	return min(col, vec3(30000.0)); // (inside RGBA16F)
}

vec3 skyFullScene(vec3 dirW) {
	return skyFullScene(dirW, skyFrame());
}

#endif
