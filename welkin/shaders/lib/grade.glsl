// VOID Welkin, look (welkin-look, 2026-10-18): the colour grade. The pack's own look, laid over the whole finished frame
// in final (after the beams and the bloom, before final's shoulder and its anti-banding). The direction (the user,
// 2026-10-18): Kingdom Come: Deliverance II's quiet, earthy countryside with Elden Ring's golden air. Muted and earthy
// by day, never candy-bright or cold; the golden hours and the misty dawns rich; nights moonlit and cool-neutral.
//
// One grade, five steps, all smooth functions of the pixel's own colour (no neighbourhood, no noise: textures and the
// pixel sun, moon and stars stay crisp):
//   1. the palette   greens lean toward moss and olive, the blues of the sky toward a soft blue-grey (never cyan)
//   2. saturation    a restrained curve: rich mids, calmer highs and lows, and the warm hues (fire, lamps, the golden
//                    light, straw and ochre) keep more of theirs than the cool ones, so warm light stands out
//   3. split tone    warm highlights, cool-neutral shadows, by the hour: a golden hour's gold against a cool shade
//   4. lift/gamma/gain   coloured, by the hour and the weather: midday calmer and a touch lower, golden hours richer,
//                    dawn soft and pearly, overcast cooler and softer, night moonlit
//   5. a filmic S    a gentle S in display values round the mids (more at the golden hours, less under an overcast),
//                    with a soft coloured toe so the shade stays rich and readable, never black
//
// The hour comes from the luminous sky's frame (lib/sky/luminous.glsl skyLumFrame), worked out once in final's vertex
// shader: GradeHour (warm, day, clear, morning) and the eye's openness to the sky (caves keep a neutral grade).

#ifndef WELKIN_GRADE
#define WELKIN_GRADE

#include "/lib/settings.glsl"

float gradeLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// (look2) how strongly the yellow-greens are drawn to moss, 0..1
#define GRADE_MOSS 0.85
// (Look D5) the earthy day: the hue (degrees, of linear light) the sunlit yellow-greens are drawn to (an olive) and the
// shaded ones (a moss), and the share of saturation the yellow-greens keep
#define GRADE_OLIVE_HUE 66.0
#define GRADE_MOSS_HUE 82.0
#define GRADE_OLIVE_SAT 0.92
#define GRADE_OLIVE_LIGHT 0.86
// (Look D5) the day sky's blue keeps this much saturation; the mids' gamma by day (under 1: deeper)
#define GRADE_SKY_SAT 0.74
#define GRADE_DAY_GAMMA 0.93
// (Look D5) the mid-afternoon's extra highlight roll
#define GRADE_AFTERNOON_ROLL 0.2

vec3 gradeHsv(vec3 c) {
	vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
	vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
	vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
	float d = q.x - min(q.w, q.y);
	return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 1e-10)), d / (q.x + 1e-10), q.x);
}

vec3 gradeRgb(vec3 c) {
	vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
	return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// (HDR stage C) The grade's gain and split tone can lift a bright warm channel past the output transform's path to
// white: a colour whose brightest channel passes 0.93 (display) eases back under white by giving up saturation, its
// luminance kept, so nothing the grade does flattens at 1.
vec3 gradeLimit(vec3 d) {
	d = max(d, 0.0);
	float m = max(d.r, max(d.g, d.b));
	if (m <= 0.93) {
		return d;
	}
	float y = min(gradeLuma(d), 0.99);
	float mt = 0.93 + 0.045 * (1.0 - exp(-(m - 0.93) / 0.045));
	return y >= mt ? vec3(y) : y + (d - y) * ((mt - y) / max(m - y, 1e-5));
}

// h: x warm (0 noon .. 1 golden), y day (1 day .. 0 night), z clear (1 clear .. ~0.1 overcast), w morning (1 before noon)
// open: the eye's openness to the sky (0 deep in a cave .. 1 outdoors)
vec3 welkinGrade(vec3 c, vec4 h, float open) {
	float k = float(GRADE_STRENGTH) * 0.01;
	float warm = h.x * h.y;
	float day = h.y;
	float night = 1.0 - day;
	float over = 1.0 - clamp((h.z - 0.1) / 0.9, 0.0, 1.0); // 0 clear .. 1 overcast
	float morn = h.w;
	// the hour's grade fades toward a plain neutral one in a cave
	warm *= open;
	over *= open;

	vec3 d0 = max(c, 0.0);
	vec3 L = pow(d0, vec3(2.2));
	float y = gradeLuma(L);
	float mx = max(L.r, max(L.g, L.b));
	float mn = min(L.r, min(L.g, L.b));
	float chroma = (mx - mn) / max(mx, 1e-4);

	// how warm-led the colour is: red led, over blue (fire, lamps, the golden light, straw, ochre, brick)
	// (look3: red clearly over green, 0.98..1.12, was 0.85..1.0: a yellow-green meadow, red about equal to green, counted as
	// warm and kept its lime; straw, ochre, fire and the golden light all stand well past it)
	float warmHue = clamp((L.r - L.b) / max(mx, 1e-4) * 1.6, 0.0, 1.0) * smoothstep(0.98, 1.12, L.r / max(L.g, 1e-4));
	// a glowing or lamp-lit warm pixel at night keeps its colour wholly (the fire must stand out)
	float keepWarm = warmHue * mix(0.5, 1.0, night);

	// ---- 1. the palette: greens toward a calmer moss, blue toward a soft blue-grey
	{
		// how green-led (green over both others, yellow-green included) and how blue-led the colour is
		float green = clamp((L.g - max(L.r, L.b)) / max(L.g, 1e-4) * 3.0, 0.0, 1.0);
		float blue = clamp((L.b - max(L.r, L.g)) / max(L.b, 1e-4) * 2.0, 0.0, 1.0);
		// cyan: green and blue both over red, close together
		float cyan = clamp((min(L.g, L.b) - L.r) / max(mx, 1e-4) * 2.0, 0.0, 1.0) * (1.0 - abs(L.g - L.b) / max(mx, 1e-4) * 3.0);
		cyan = clamp(cyan, 0.0, 1.0);
		// moss: a fifth of the saturation out, a little deeper, the yellow eased (a chartreuse meadow reads a calm
		// moss-olive, not a lime)
		vec3 moss = mix(vec3(y), L, 0.80) * vec3(1.0, 1.0, 1.06);
		moss *= 0.93 * y / max(gradeLuma(moss), 1e-6);
		L = mix(L, moss, 0.30 * green * (1.0 - night)); // (look3: 0.50, was 0.75: the woods' greens had gone grey)
		// (look2, 2026-10-18) the yellow-greens: a sunlit meadow at noon is a chartreuse with red near green (155, 152, 90),
		// which the green-led weight above barely sees. By hue: from a yellow-green (about 55 degrees) to a green (about
		// 160) the hue is drawn toward an olive-moss (86 degrees), a quarter of the saturation out and a little deeper.
		// Straw and ochre (under 45 degrees) keep theirs, so fields and the golden light stay warm
		{
			vec3 hsv = gradeHsv(L);
			float hd = hsv.x * 360.0;
			float band = smoothstep(40.0, 56.0, hd) * (1.0 - smoothstep(165.0, 190.0, hd)) * smoothstep(0.08, 0.25, hsv.y);
			band *= (1.0 - night) * (1.0 - 0.6 * keepWarm);
			float th = 96.0 + (hd - 96.0) * 0.2;
			// (the true greens, past about 90 degrees: leaves, a savanna's crowns, keep more of their colour and depth)
			float yg = 1.0 - smoothstep(75.0, 115.0, hd);
			// (Look D5) the old target (about 92 degrees) drew a sunlit meadow's yellow-green toward a fresh green. By day the
			// yellow-greens now go earthy by their light: the sunlit tops toward an olive (GRADE_OLIVE_HUE, red about equal to
			// green), the shaded and darker grass toward a moss (GRADE_MOSS_HUE); the hue turns, the light is kept
			// (by day only: through the golden hours, from warm about 0.3 to 0.55, the old moss target comes back, so 17:00
			// and later are as they were; sand and straw, under about 58 degrees, keep their light)
			float earthy = 1.0 - smoothstep(0.3, 0.55, warm);
			float olive = earthy * smoothstep(54.0, 66.0, hd);
			{
				float ydl = pow(max(gradeLuma(L), 0.0), 1.0 / 2.2);
				float thE = mix(GRADE_MOSS_HUE, GRADE_OLIVE_HUE, smoothstep(0.22, 0.62, ydl));
				th = mix(th, thE, yg * earthy);
			}
			// (look3: a gentler pull, 0.88..0.76 of the saturation and 0.97..0.93 of the light, was 0.80..0.62 and 0.93..0.84:
			// noon's meadow had gone a grey olive; it stays moss and olive, but alive)
			vec3 m2 = gradeRgb(vec3(th / 360.0, hsv.y * mix(1.0, mix(0.97, GRADE_OLIVE_SAT, olive), yg), hsv.z));
			m2 *= mix(0.97, mix(0.92, GRADE_OLIVE_LIGHT, olive), yg) * gradeLuma(L) / max(gradeLuma(m2), 1e-6);
			L = mix(L, m2, band * GRADE_MOSS);
		}
		// blue-grey: a third of the saturation out, a breath of warmth in
		vec3 bg = mix(vec3(y), L, 0.90) * vec3(1.03, 1.0, 0.97); // (look3: 0.80: noon's sky had gone a leaden grey; look2 0.58)
		bg *= y / max(gradeLuma(bg), 1e-6);
		L = mix(L, bg, min(0.85 * blue + 0.7 * cyan, 1.0) * (1.0 - 0.5 * night));
		// (D1) lavender and lilac (blue-led with red over green: the clouds' shaded flanks, a hazy dawn) by day: most of
		// their violet out toward a warm blue-grey, so the clouds and the air are never a cold lilac (the dusk's last
		// degrees keep theirs: final's dusk gradient lays them on after)
		{
			float vio = clamp((L.b - L.g) / max(L.b, 1e-4) * 3.0, 0.0, 1.0) * clamp((L.r - L.g) / max(L.b, 1e-4) * 4.0, 0.0, 1.0);
			float yv = gradeLuma(L);
			vec3 wg = mix(vec3(yv), L, 0.45) * vec3(1.02, 1.0, 0.97);
			wg *= yv / max(gradeLuma(wg), 1e-6);
			L = mix(L, wg, 0.75 * vio * day * (1.0 - 0.3 * warm));
		}
	}

	// ---- 2. saturation: a restrained curve; the warm hues keep more
	{
		float yd = pow(max(gradeLuma(L), 0.0), 1.0 / 2.2);
		float base = mix(0.96, 0.90, warm);         // midday muted (look3: 0.80, look2's 0.68 read grey and dead), golden hours richer
		base = mix(base, 0.74, over);                 // overcast and rain greyer
		base = mix(base, 0.70, night * open);         // moonlight is nearly colourless
		// (D1) the warm-led colours keep more than the rest, but by day only a little: a golden hour's whole land is warm-lit
		// (red sand, straw, brick), and wholly kept it read a saturated orange; fire and lamps at night keep all of theirs
		float s = mix(base, mix(mix(1.0, 0.94, warm), 1.04, night), keepWarm);
		// (look3) the open sky's blue keeps more by day: a soft hazy blue, not a leaden grey
		float blueK = clamp((L.b - max(L.r, L.g)) / max(L.b, 1e-4) * 3.0, 0.0, 1.0) * day * (1.0 - over);
		// (Look D5) a soft, slightly hazy blue by day, not a saturated one (was 0.90)
		s = mix(s, GRADE_SKY_SAT, blueK);
		// calmer highs and lows, richest in the mids
		s *= 1.0 - 0.22 * smoothstep(0.62, 0.98, yd) * (1.0 - keepWarm) * (1.0 - 0.6 * blueK);
		s *= 1.0 - mix(0.18, 0.06, day) * (1.0 - smoothstep(0.03, 0.22, yd)); // (look3: by day the shade keeps its colour)
		float yl = gradeLuma(L);
		L = max(mix(vec3(yl), L, s), 0.0);
	}

	// ---- 3. split tone: warm highlights, cool-neutral shadows
	{
		float yl = gradeLuma(L);
		float yd = pow(yl, 1.0 / 2.2);
		float wHi = smoothstep(0.30, 0.75, yd) * (1.0 - 0.7 * smoothstep(0.82, 1.0, yd));
		float wLo = 1.0 - smoothstep(0.06, 0.42, yd);
		// the highlights: a neutral warmth by day, gold through the golden hours (rose-gold in the morning)
		vec3 hiDay = vec3(1.05, 1.0, 0.92); // (look3: a warmer sunlit side, was 1.035 1.0 0.94)
		vec3 hiGold = mix(vec3(1.09, 0.99, 0.82), vec3(1.09, 0.975, 0.84), morn); // (look2: richer gold; D1: the morning a pink-gold; D3: a pale gold)
		vec3 hiOver = vec3(1.0, 1.0, 0.99);
		vec3 hiNight = vec3(0.96, 0.99, 1.04);
		vec3 hi = mix(mix(hiDay, hiGold, warm), hiOver, over * 0.7);
		hi = mix(hi, hiNight, night);
		// the shadows: cool-neutral, cooler against the golden light, a blue-violet at night
		vec3 loDay = vec3(0.92, 0.99, 1.12); // (look2: cooler, was 0.97 1.0 1.035)
		vec3 loGold = vec3(0.93, 0.98, 1.11); // (look2: cooler against the gold, was 0.95 0.985 1.07)
		vec3 loNight = vec3(0.93, 0.98, 1.10);
		// (D1) the morning's shade a soft rose-violet rather than the evening's cool blue: a misty, pink-gold dawn
		// (D3) a rose shade, not a violet: misty gold and rose, never a mauve-grey
		vec3 loMorn = vec3(1.02, 0.965, 1.0);
		vec3 lo = mix(mix(loDay, mix(loGold, loMorn, morn), warm), loNight, night);
		hi /= gradeLuma(hi);
		lo /= gradeLuma(lo);
		// (look3) the open sky's blue takes a fifth of the warm highlight, so it stays a blue (the warm light is on the land)
		float blueS = clamp((L.b - max(L.r, L.g)) / max(L.b, 1e-4) * 3.0, 0.0, 1.0) * day;
		L *= mix(vec3(1.0), hi, wHi * open * (1.0 - 0.8 * blueS)) * mix(vec3(1.0), lo, wLo);
	}

	// ---- 4. lift / gamma / gain (display values)
	// (look3) how blue-led the colour is now (the open sky): its gain stays neutral, the golden hour's warmth is for the land
	float blueG = clamp((L.b - max(L.r, L.g)) / max(L.b, 1e-4) * 3.0, 0.0, 1.0) * day;
	vec3 d = pow(max(L, 0.0), vec3(1.0 / 2.2));
	{
		// gain: midday calmer and a touch lower; golden hours warm and full; dawn pearly; overcast cool and soft; night
		// moonlit
		vec3 gDay = vec3(1.03, 1.01, 0.96); // (look3: sunlit, warm-neutral, was look2's 0.86 0.85 0.82, which killed the sun)
		vec3 gGold = mix(vec3(1.03, 0.985, 0.92), vec3(1.04, 0.98, 0.915), morn); // (D1: the morning a touch rosier; D3: rose-gold)
		vec3 gOver = vec3(0.94, 0.95, 0.96);
		// (Look D4) the deep night: no lift, no opening of the mids, a near-neutral moon-blue gain, so the blacks sit deep
		// and the moonlit faces and lamps carry the frame; the dusk and the blue hour (night part way in) keep the old
		// moonlit-readable terms
		float nd = smoothstep(0.5, 1.0, night);
		vec3 gNight = mix(vec3(1.04, 1.10, 1.20), vec3(0.97, 1.02, 1.12), nd); // (look2: moonlit and readable, was 0.92 0.97 1.04)
		vec3 gain = mix(mix(gDay, gGold, warm), gOver, over);
		gain = mix(gain, gNight, night * open * (1.0 - keepWarm));
		gain = mix(gain, vec3(dot(gain, vec3(0.2126, 0.7152, 0.0722))), 0.5 * blueG * (1.0 - night));
		// lift: a soft coloured toe, so the shade reads coloured rather than black (cool-neutral by day, a plum-blue
		// under the golden light, a blue-grey at night)
		vec3 lDay = vec3(0.003, 0.005, 0.009);
		vec3 lGold = vec3(0.014, 0.011, 0.018);
		vec3 lOver = vec3(0.016, 0.017, 0.019);
		vec3 lNight = mix(vec3(0.009, 0.012, 0.020), vec3(0.0015, 0.0025, 0.005), nd); // (look2: a moonlit blue-grey toe, was 0.004 0.006 0.011)
		vec3 lMorn = vec3(0.018, 0.013, 0.014); // (D3: the dawn's toe a soft rose, the mist's shade)
		vec3 lift = mix(mix(lDay, mix(lGold, lMorn, morn), warm), lOver, over);
		lift = mix(lift, lNight, night);
		// gamma: the mids a little lower by day (calm), held at the golden hour, a little higher under an overcast
		float gamma = mix(1.0, 1.03, over); // (look3: noon 0.98, was 0.95)
		// (Look D5) by day the mids sit a little deeper (the tops and white kept): a calmer, earthier land with its form
		gamma *= mix(1.0, GRADE_DAY_GAMMA, day * (1.0 - smoothstep(0.3, 0.55, warm)) * (1.0 - over) * open);
		// (look2) moonlit nights a little more open in the mids, so the land reads under the moon
		gamma = mix(gamma, mix(1.14, 1.0, nd), night * open);
		d = d * gain + lift * (1.0 - d);
		d = pow(max(d, 0.0), vec3(1.0 / gamma));
	}

	// ---- 5. the filmic S round the mids (on luminance, the hue kept)
	{
		float yd = gradeLuma(d);
		float amt = mix(0.22, 0.20, warm) * (1.0 - 0.6 * over) * mix(0.5, 1.0, day); // (look3: noon 0.16, was 0.10: the light models the land)
		float x = clamp(yd, 0.0, 1.0);
		float sx = x * x * (3.0 - 2.0 * x);
		float yd2 = mix(x, sx, amt);
		// (look2, 2026-10-18) the highlights roll off by day: the top fifth eases down, so a white cloud, a pale sky and
		// sunlit sand sit in a soft cream a step under white instead of a high-key glare (the golden hours keep more of
		// their glow, night is untouched)
		float roll = smoothstep(0.55, 1.0, x);
		// (Look D5) the mid-afternoon (warm about 0.1 to 0.4: 16:30) rolls its sunlit tops a little more, so they do not run
		// hot before the golden hour's glow takes over (17:00 is at about 0.6)
		float aft = smoothstep(0.04, 0.16, warm) * (1.0 - smoothstep(0.32, 0.52, warm));
		yd2 -= (mix(0.04, 0.08, warm) + GRADE_AFTERNOON_ROLL * aft) * roll * roll * day * open * (1.0 - keepWarm); // (look3: 0.11, was 0.17)
		d *= yd2 / max(yd, 1e-5);
	}

	// the white point stays: what was at or past white (the pixel sun's disc, the moon, a flame's core, a glint) keeps
	// its own colour, so the disc stays a crisp white in its graded glow instead of melting into it
	float wp = smoothstep(0.72, 0.97, min(d0.r, min(d0.g, d0.b)));
	d = mix(d, d0, wp * wp);
	return gradeLimit(mix(c, d, k));
}

#endif
