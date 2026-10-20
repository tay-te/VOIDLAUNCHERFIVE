// VOID Welkin, atmos (welkin-atmos, 2026-10-16): the luminous sky. Built from the depth prototype SKY (welkin-depth
// c1383920..18ccd89a), for real. The sky table's dome (lib/sky/dome.glsl) is a smooth gradient of the hour's colours;
// this lays the light of a real sky over it, per pixel, in linear light, with no texture and no noise:
//
//   zenith   the top of the dome deepens and gains a little saturation by day: a clear, deep blue overhead
//   band     a broad, soft, luminous band along the horizon (no edge): the dome's own hue a little paler and brighter,
//            cool and clear away from the sun, warming to amber on the sun's side as the sun gets low
//   glow     a wide glow round the sun over its half of the dome (two Henyey-Greenstein lobes, a tight core and a broad
//            one), strongest while the sun is low and its light crosses the most air. The glow is MIXED toward the
//            sun's own hue at a brighter level, never added: an amber light added to a pale blue band sums to white
//            (the prototypes' white sun-side horizon on the steppe), mixed toward it the band turns amber-gold
//   cap      a soft, hue-keeping shoulder: a colour running past white is scaled down as a whole, so the glow near the
//            sun stays amber instead of clipping to white channel by channel
//
// All of it fades under an overcast and in rain (the sky table's overcast), and away at night (the moon's halo is the
// table's). The same light goes onto the haze's air (composite2), so the far land fades into the band the sky shows
// over it, and onto the clouds' far fog (lib/clouds/shade.glsl). Less gold than Complementary: the glow's hue is the
// sun's light a fifth of the way back toward white, and it only reaches the sun's side of the sky.
// Written from scratch for Welkin; no other pack's code.
//
//   SkyLum f = skyLumFrame(sunW);             once per pixel (a handful of table texels)
//   L = skyLuminousLin(L, d, f, amount);      scene light in, scene light out; amount 0..1 (HDR: no cap, no
//                                             golden headroom; the output transform's shoulder rolls the glow off)

#ifndef WELKIN_SKY_LUMINOUS
#define WELKIN_SKY_LUMINOUS

#include "/lib/settings.glsl"
#include "/lib/sky/table.glsl"

#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#ifndef WU_sunAngle
#define WU_sunAngle
uniform float sunAngle; // 0 sunrise, 0.25 noon, 0.5 sunset
#endif

// skies2 (welkin-skies, 2026-10-18): the day's air and zenith
#define WS_ZEN_SAT 0.80
#define WS_ZEN_LEVEL 0.96
#define WS_BAND_GREY 0.22
#define WS_BAND_BRIGHT 1.22
#define WS_AIR_HUE 0.75
#define WS_AIR_SUN 0.85
#define WS_BAND_GOLD 1.18
#define WS_GOLD_LEAD 0.8
#define WS_GLOW_DIM 0.25
#define WS_BAND_WARM_AWAY 0.12 // (Look D6)

// (HDR stage A) the aureole's strength in scene light (look4's terms x this), and its share by day against the golden
// hours'
#define SKY_AUREOLE 1.0
// the glow's level round a low sun (scene light)
#define SKY_GLOW_LOW_SUN 0.9
#define SKY_AUREOLE_DAY 0.45

struct SkyLum {
	vec3 sunW;
	vec3 hue;    // the sun's glow hue, linear, luma 1
	float day;   // 1 by day, 0 at night
	float low;   // 0 sun high .. 1 sun on the horizon
	float warm;  // the golden afternoon and morning: 0 sun high .. 1 from about 6 degrees down to the horizon
	vec3 gold;   // the glow's hue (linear, luma 1): the sun's own, led toward amber through the golden hours
	float up;    // the sun's glow is there: from just under the horizon up
	float clear; // 1 a clear sky .. about 0.1 under a full overcast
};

float skyLumLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// A Henyey-Greenstein lobe of asymmetry g at mu = cos(angle to the sun), 1 straight at the sun.
float skyLumHg(float mu, float g) {
	float a = 1.0 + g * g;
	return pow((a - 2.0 * g) / max(a - 2.0 * g * mu, 1e-4), 1.5);
}

SkyLum skyLumFrame(vec3 sunW) {
	SkyLum f;
	f.sunW = sunW;
	// (review round 1: by the sun's own height as well as the table's night step. The night step follows vanilla's
	// light ramp, which is already 0.7 at 18:30 and 05:30 with the sun still some 5 degrees up: the golden hours' glow,
	// the sunlit haze, the god rays and the beams were all cut to a third just when they should be at their boldest)
	f.day = max(1.0 - skyNightStep(), smoothstep(-0.10, 0.03, sunW.y));
	float overcast = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
	f.clear = (1.0 - 0.88 * overcast) * (1.0 - 0.5 * clamp(rainStrength, 0.0, 1.0));
	// the sky table's golden hour (Gloam's Twilight maths, 0..1; about 0.67 at 17:00 and 1 at 06:30) leads the warmth:
	// by the sun's height alone 17:00 (the sun some 26 degrees up) read as a plain afternoon
	float golden = skyTable(SKY_X_STATE_TIME, SKY_ROW_LIGHT).r;
	f.low = max(1.0 - smoothstep(0.05, 0.55, sunW.y), 0.75 * golden);
	f.up = smoothstep(-0.14, 0.02, sunW.y);
	f.warm = max(1.0 - smoothstep(0.08, 0.45, sunW.y), golden);
	// the sun's light (linear) while it is up; under the horizon the table's sun-side horizon carries the hue
	vec3 sunL = skyLandDirect();
	vec3 horL = pow(max(skyHorizon(true), vec3(1e-4)), vec3(2.2));
	float ls = skyLumLuma(sunL);
	vec3 h = ls > 1e-3 ? sunL / ls : horL / max(skyLumLuma(horL), 1e-4);
	h = mix(h, horL / max(skyLumLuma(horL), 1e-4), (1.0 - smoothstep(0.0, 0.06, sunW.y)) * 0.5);
	// never a channel past 1.9 of the luma (no neon orange)
	h = min(h, vec3(1.9));
	f.hue = h / max(skyLumLuma(h), 1e-4);
	// the glow's amber: a warm gold (less orange than Complementary's), by how golden the hour is
	// (golden, 2026-10-16: the hues deeper in linear light. A linear (1, 0.66, 0.36) shows on screen as a pale cream
	// (1, 0.83, 0.63): every glow, band and sunlit haze round a low sun read cream-white and washed out. These read
	// peach-gold, rose-peach and rose on screen, still with less yellow than Complementary's gold)
	const vec3 GOLD = vec3(1.0, 0.57, 0.31) / 0.6426;
	// (review round 1) the morning's a rose-gold, softer and pinker than the evening's amber (the warm pink morning
	// air of a clear dawn; the land's own dawn sun is rose-gold too)
	// (Look D3: a paler gold with its rose, was (1, 0.53, 0.42): the dawn read pastel salmon, not misty gold)
	const vec3 ROSE_GOLD = vec3(1.0, 0.60, 0.40) / 0.6706;
	float morning = sunAngle < 0.25 || sunAngle > 0.75 ? 1.0 : 0.0;
	// (review round 2) over the evening's last degrees the gold turns peach and rose, so a sunset over water reads
	// peach-rose with a lilac sky above, not a cream-yellow frame (less gold than Complementary)
	const vec3 PEACH = vec3(1.0, 0.45, 0.44) / 0.5662;
	vec3 eve = mix(GOLD, PEACH, 1.0 - smoothstep(0.03, 0.2, sunW.y));
	// (skies2: 0.8, was 0.6: the glow toward a golden-hour sun reads gold on screen, not cream)
	vec3 g = mix(f.hue, mix(eve, ROSE_GOLD, morning), WS_GOLD_LEAD * f.warm);
	f.gold = g / max(skyLumLuma(g), 1e-4);
	return f;
}

// How golden the hour is for the aureole (0..1): the warm hours with the sun up, from about 28 degrees of sun down to
// just under the horizon.
float skyGoldenHour(SkyLum f) {
	return f.up * smoothstep(0.50, 0.05, f.sunW.y) * smoothstep(-0.10, 0.0, f.sunW.y + 0.04);
}

// The sun's aureole toward unit direction d, scene light: a gold-white heart a few degrees across, a gold-amber glow to
// some ten degrees, a faint amber to a quarter of the sky (measured from the pixel disc's rim).
vec3 skyAureole(vec3 d, SkyLum f) {
	float mu = dot(d, f.sunW);
	float ang = acos(clamp(mu, -1.0, 1.0));
	float a = max(ang - float(SUN_SIZE) * float(SKY_ART_PIXEL) * 1e-4 * 0.85, 0.0);
	float heart = exp(-a / 0.028);
	float near = exp(-a / 0.10);
	float wide = exp(-ang / 0.40);
	const vec3 AMBER4 = vec3(1.0, 0.56, 0.24) / 0.6119;
	const vec3 WHITE_GOLD = vec3(1.0, 0.86, 0.62) / 0.8810;
	vec3 aurC = mix(mix(f.gold, AMBER4, 0.6), WHITE_GOLD, heart);
	return aurC * (1.9 * heart + 0.55 * near + 0.10 * wide);
}




// (HDR stage A) The luminous sky in scene-linear light: `L` the dome's light toward unit direction d (scene-linear,
// lib/hdr.glsl), the same light laid over it as skyLuminous does, with no cap and no golden headroom (the output
// transform's shoulder rolls the glow off), and the sun's aureole in linear light at every hour.
#define SKY_DAWN_BELT 0.6
#define SKY_DAWN_BELT_LEVEL 1.3
vec3 skyLuminousLin(vec3 L, vec3 d, SkyLum f, float amount) {
#ifdef SKY_LUMINOUS
	float k = amount * float(SKY_GLOW) * 0.01 * f.clear * f.day;
	if (k <= 0.0) {
		return L;
	}
	float y = skyLumLuma(L);
	float hp = max(d.y, 0.0);
	float mu = dot(d, f.sunW);
	vec2 dz = d.xz, sz = f.sunW.xz;
	float az = dot(dz, dz) > 1e-8 && dot(sz, sz) > 1e-8 ? dot(normalize(dz), normalize(sz)) : 0.0;
	float side = 0.5 + 0.5 * az;

	// the zenith: deeper and a little richer overhead (less while the sun is low: a golden-hour zenith stays light)
	float zen = smoothstep(0.15, 0.95, hp);
	// (skies2, 2026-10-18: a softer, hazier blue overhead, less saturated, not deeper: the direction's blue-grey sky, never
	// a saturated cyan. Was 1.15 saturation at 0.84 brightness)
	vec3 rich = max(mix(vec3(y), L, WS_ZEN_SAT), 0.0) * WS_ZEN_LEVEL * vec3(1.02, 1.0, 0.97);
	// (and none at dusk and dawn: it darkened the 18:30 sky below the sunset's own light)
	L = mix(L, rich, zen * 0.6 * min(k, 1.0) * (1.0 - 0.5 * f.low) * (1.0 - 0.85 * f.warm) * smoothstep(-0.02, 0.10, f.sunW.y));

	// the luminous band: broad and soft, its own hue paler and brighter; cool away from the sun, on the sun's side
	// warming toward the sun's hue as it sinks (its brightness kept: only the hue moves), and a touch of that warmth
	// all round the horizon while the sun is low
	// (review round 2: by day, away from the golden hours, the band is wider and whiter, the luminous haze over a midday
	// horizon both reference packs show; the golden hours keep round 1's coloured band)
	float mid = (1.0 - f.warm) * f.up;
	// (skies2, 2026-10-18: the day's band is coloured air, not a milky white strip: grey 0.46 and 1.62 times the sky
	// were a near-white band the far land dissolved into. Now a little paler than the sky, a soft blue-grey away from
	// the sun warming to a gold glow toward it, so far ridges stand in layered, coloured air)
	const vec4 BAND_DAY = vec4(0.46, WS_BAND_GREY, WS_BAND_BRIGHT, 0.9);
	float band = exp(-hp / mix(0.36, BAND_DAY.x, mid)) * (1.0 - 0.45 * smoothstep(0.0, 0.12, -d.y));
	// (review round 1: less grey in it, so the band keeps the sky's colour instead of a near white strip)
	// (skies2: the golden hours' band 1.2 the sky, was 1.45: it read as a cream-white wash the mesa's far land vanished in)
	vec3 pale = mix(L, vec3(y), mix(0.16, BAND_DAY.y, mid)) * mix(WS_BAND_GOLD, BAND_DAY.z, mid);
	{
		// (skies2) the day's air hue: blue-grey away from the sun, a soft gold toward it (linear, luma 1)
		const vec3 AIR_AWAY = vec3(0.86, 1.00, 1.30);
		const vec3 AIR_SUN = vec3(1.20, 0.98, 0.66);
		vec3 airHue = mix(AIR_AWAY, AIR_SUN, side * side * side * WS_AIR_SUN);
		// (by day and through the golden hours; the last degrees of a sunset keep their rose and lilac)
		float eve = 1.0 - smoothstep(0.03, 0.2, f.sunW.y);
		pale = mix(pale, airHue * skyLumLuma(pale), WS_AIR_HUE * f.up * (1.0 - 0.8 * eve));
	}
	vec3 coolPale = pale * vec3(0.98, 1.0, 1.03);
	// (Look D6) the band's warmth keeps to the sun's side: away from it the golden hour's band stays blue-grey air (it had
	// taken 0.45 of the gold all round, and the far land faded into one milky cream band)
	float warm = f.warm * f.up * mix(WS_BAND_WARM_AWAY, 1.0, side * side * side);
	vec3 bandC = mix(coolPale, f.gold * skyLumLuma(pale), clamp(0.80 * warm, 0.0, 1.0));
	L = mix(L, bandC, clamp(band * mix(0.85, BAND_DAY.w, mid) * k, 0.0, 1.0));
	y = skyLumLuma(L);

	// the golden hours warm the whole dome a little (its brightness kept), the zenith least
	// (review round 2: a clear morning a little more: its rose-gold reads as a warm rose-peach air, not a neutral cream)
	// (Look D3: 0.38 in the morning, was 0.28, and from the first light, before the sun is up: a misty gold dawn)
	bool mornD = sunAngle < 0.25 || sunAngle > 0.75;
	float dawnAir = mornD ? 0.38 : 0.20;
	float upD = mornD ? max(f.up, smoothstep(-0.21, -0.03, f.sunW.y)) : f.up;
	// (golden, 2026-10-16: the zenith none of it, was half: gold mixed into the blue overhead read as a cream-grey wash)
	// (Look D6: an evening's warmth mostly on the sun's side, so the air away from it stays blue-grey)
	float dawnSide = mornD ? 1.0 : mix(0.35, 1.0, side * side);
	L = mix(L, f.gold * y, clamp(dawnAir * dawnSide * f.warm * upD * (1.0 - zen) * (1.0 - smoothstep(0.04, 0.4, hp)) * min(k, 1.0), 0.0, 1.0));
	// (Look D3) the dawn's rose belt: from the first light until the sun is a few degrees up, a band of rose-gold light low
	// over the horizon, most away from the sun (the earth's shadow's lit rim), so a twilit shore glows rose over its mist
	// instead of hanging a mauve-grey sky
	if (mornD) {
		float pre = smoothstep(-0.22, -0.07, f.sunW.y) * (1.0 - smoothstep(0.04, 0.18, f.sunW.y));
		float belt = smoothstep(-0.01, 0.05, d.y) * exp(-max(d.y - 0.05, 0.0) / 0.16);
		const vec3 BELT = vec3(1.0, 0.66, 0.56) / 0.7250;
		L = mix(L, BELT * y * SKY_DAWN_BELT_LEVEL, clamp(SKY_DAWN_BELT * pre * belt * mix(0.55, 1.0, 1.0 - side), 0.0, 1.0));
		y = skyLumLuma(L);
	}
	// (look3, 2026-10-18) an evening sky has a gradient across it, not one salmon from edge to edge: gold round the sun
	// (the glow below), a dusky rose and lilac across the sky, a soft blue-grey away from the sun and overhead. By the
	// evening's last degrees (none at 17:00, most as the sun touches the horizon); the mornings keep their own pearl
	float eveK = sunAngle < 0.5 ? 1.0 - smoothstep(0.03, 0.30, f.sunW.y) : 0.0;
	float upk = smoothstep(0.02, 0.6, hp);
	if (eveK > 0.0) {
		float away = 1.0 - side;
		// across: lilac; away and high: a blue-grey (linear, luma 1)
		const vec3 LILAC = vec3(0.98, 0.80, 1.20) / 0.8606;
		const vec3 DUSK_BLUE = vec3(0.62, 0.80, 1.18) / 0.8045;
		vec3 dusk = mix(LILAC, DUSK_BLUE, clamp(away * away + 0.6 * upk * upk, 0.0, 1.0));
		// (up the sky too, over the sun's side as well: the glow below takes the sun's own part of it back to gold)
		float wD = smoothstep(0.25, 0.9, away) * 0.75 + 0.60 * upk * (1.0 - 0.45 * side * side);
		wD = clamp(wD, 0.0, 0.85) * eveK * f.up * min(k, 1.0);
		// (a little dimmer away from the sun: the dusk side of the sky)
		L = mix(L, dusk * y * mix(1.0, 0.86, away), wD);
		y = skyLumLuma(L);
	}
	// the glow round the sun: mixed toward the sun's hue at a brighter level (amber at a low sun, never white)
	float lobe = 0.35 * skyLumHg(mu, 0.93) + 0.65 * skyLumHg(mu, 0.45);
	float glow = (0.22 + 0.55 * f.low) * f.up;
	// (skies2: a little lower through the golden hours, so the glow keeps its gold instead of rolling into cream)
	float tl = max(y, 0.10) * (1.2 + 1.2 * lobe) * (1.0 - WS_GLOW_DIM * f.warm * (1.0 - f.low * 0.5));
	// (review round 2) round a low sun the glow stays under the disc's own light (about 0.8 on screen), so the pixel
	// sun reads as a solid bright disc in its glow; it had blazed the sky round it to the disc's own cream and the
	// disc showed only as its saturated rim (a hollow ring), or not at all
	// (golden, 2026-10-16: 0.42, was 0.6: the air round a low sun a deep glowing peach under the disc, not a cream-white
	// field the disc vanished into)
	// (HDR stage B) round a low sun the glow's own level stays moderate (scene light), so the sky round it holds its
	// gold and amber and the disc and its aureole stand above it
	tl = min(tl, mix(8.0, SKY_GLOW_LOW_SUN, f.low * f.up));
	vec3 target = f.gold * tl;
	float lobeW = lobe * glow * k;
	if (eveK > 0.0) {
		// (look3) an evening glow with a gradient in it: amber-gold close round the sun, the peach-rose of f.gold farther
		// out, and up the sky the broad lobe gives way to the dusk's rose and lilac above (it had laid one salmon over the
		// whole of the sun's half of the sky)
		float nearS = smoothstep(0.82, 0.995, mu);
		const vec3 AMBER = vec3(1.0, 0.60, 0.28) / 0.6620;
		target = mix(f.gold, AMBER, nearS * eveK) * tl;
		lobeW *= 1.0 - 0.55 * eveK * upk * (1.0 - nearS);
	}
	L = mix(L, target, clamp(lobeW, 0.0, 0.92));
	// the dome lit: the whole sky a little brighter by day, the sun's half more while the sun is low
	L *= 1.0 + k * (0.10 + 0.14 * f.low * f.up * side);

	// (HDR stage A) the sun's aureole, in linear light: a gold-white heart a few degrees across, a gold glow to some ten
	// degrees, a faint amber to a quarter of the sky (look4's terms, now at every hour the sun is up, strongest through
	// the golden hours); the sky under it keeps its own light (no headroom: the curve's shoulder holds the peak)
	// (the aureole is the dome's own (amount 1): the air and the clouds' far fog take the forward scatter their own way,
	// lib/air.glsl airMie)
	if (amount >= 0.99) {
		L += skyAureole(d, f) * (SKY_AUREOLE * mix(SKY_AUREOLE_DAY, 1.0, skyGoldenHour(f)) * min(k, 1.0));
	}
	return L;
#else
	return L;
#endif
}

// ==== (Look D9) the moonlit night sky ====
// Night had read as "dark Minecraft": one flat navy dome. A moonlit painting's sky has a gradient: a silver glow round
// the moon (a tight halo and a broad glow over its side of the dome), lighter toward it, and a soft cool glow low along
// the horizon all round (the moonlit air), so the land's far ridges stand dark against a paler band. All of it as a
// share of the night dome's own horizon light (the table's), so a darker night is darker throughout, only in the deep
// night (the dusk and the blue hour keep their own sky), and the moon's part by its visibility (cloud, overcast).
// d: the view direction; moonW: the moon's direction (world); returns scene light to add.
#define SKY_MOON_HALO 30.0      // the tight halo, a few degrees round the moon (x the horizon's light)
#define SKY_MOON_GLOW 7.0      // the broad glow over the moon's side of the dome
#define SKY_NIGHT_HORIZON 4.0 // the cool glow low over the horizon
vec3 skyNightGlow(vec3 d, vec3 sunW, vec3 moonW) {
	float deep = smoothstep(-0.05, -0.20, sunW.y);
	if (deep <= 0.0) {
		return vec3(0.0);
	}
	float overcast = skyTable(SKY_X_STATE_LIGHT, SKY_ROW_LIGHT).a;
	float moonVis = skyTable(SKY_X_STATE_SHOW, SKY_ROW_LIGHT).g;
	vec3 ref = skyTable(1, SKY_ROW_DOME_SCENE).rgb; // the night dome's horizon band, scene light
	float lr = skyLumLuma(ref);
	float mu = clamp(dot(d, moonW), -1.0, 1.0);
	float ang = acos(mu);
	float mUp = smoothstep(-0.04, 0.12, moonW.y);
	float halo = exp(-ang / 0.10);
	float wide = exp(-ang / 0.55);
	// the horizon's glow: low along it, more on the moon's side (its light crossing the most air)
	float az = 0.0;
	if (dot(d.xz, d.xz) > 1e-8 && dot(moonW.xz, moonW.xz) > 1e-8) {
		az = dot(normalize(d.xz), normalize(moonW.xz));
	}
	float hor = exp(-max(d.y, 0.0) / 0.09) * (1.0 - smoothstep(0.0, 0.08, -d.y)) * mix(0.55, 1.0, (0.5 + 0.5 * az) * mUp);
	vec3 silver = vec3(0.82, 0.90, 1.0) / 0.8873;
	vec3 coolA = vec3(0.70, 0.84, 1.0) / 0.8152;
	vec3 g = silver * ((SKY_MOON_HALO * halo + SKY_MOON_GLOW * wide) * mUp * moonVis)
		+ coolA * (SKY_NIGHT_HORIZON * hor * mix(0.5, 1.0, moonVis));
	return g * lr * deep * (1.0 - 0.7 * overcast);
}

#endif
