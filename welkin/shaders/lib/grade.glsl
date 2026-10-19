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
#define GRADE_MOSS 1.0

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
		L = mix(L, moss, 0.50 * green * (1.0 - night)); // (look3: 0.50, was 0.75: the woods' greens had gone grey)
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
			// (look3: a gentler pull, 0.88..0.76 of the saturation and 0.97..0.93 of the light, was 0.80..0.62 and 0.93..0.84:
			// noon's meadow had gone a grey olive; it stays moss and olive, but alive)
			vec3 m2 = gradeRgb(vec3(th / 360.0, hsv.y * mix(1.0, 0.97, yg), hsv.z));
			m2 *= mix(0.90, 0.80, yg) * gradeLuma(L) / max(gradeLuma(m2), 1e-6);
			L = mix(L, m2, band * GRADE_MOSS);
		}
		// blue-grey: a third of the saturation out, a breath of warmth in
		vec3 bg = mix(vec3(y), L, 0.90) * vec3(1.03, 1.0, 0.97); // (look3: 0.80: noon's sky had gone a leaden grey; look2 0.58)
		bg *= y / max(gradeLuma(bg), 1e-6);
		L = mix(L, bg, min(0.85 * blue + 0.7 * cyan, 1.0) * (1.0 - 0.5 * night));
	}

	// ---- 2. saturation: a restrained curve; the warm hues keep more
	{
		float yd = pow(max(gradeLuma(L), 0.0), 1.0 / 2.2);
		float base = mix(0.80, 0.90, warm);         // midday muted (look3: 0.80, look2's 0.68 read grey and dead), golden hours richer
		base = mix(base, 0.74, over);                 // overcast and rain greyer
		base = mix(base, 0.70, night * open);         // moonlight is nearly colourless
		float s = mix(base, 1.04, keepWarm);
		// (look3) the open sky's blue keeps more by day: a soft hazy blue, not a leaden grey
		float blueK = clamp((L.b - max(L.r, L.g)) / max(L.b, 1e-4) * 3.0, 0.0, 1.0) * day * (1.0 - over);
		s = mix(s, max(s, 0.90), blueK);
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
		vec3 hiGold = mix(vec3(1.10, 0.99, 0.80), vec3(1.07, 0.98, 0.88), morn); // (look2: richer gold)
		vec3 hiOver = vec3(1.0, 1.0, 0.99);
		vec3 hiNight = vec3(0.96, 0.99, 1.04);
		vec3 hi = mix(mix(hiDay, hiGold, warm), hiOver, over * 0.7);
		hi = mix(hi, hiNight, night);
		// the shadows: cool-neutral, cooler against the golden light, a blue-violet at night
		vec3 loDay = vec3(0.955, 1.0, 1.06); // (look2: cooler, was 0.97 1.0 1.035)
		vec3 loGold = vec3(0.93, 0.98, 1.11); // (look2: cooler against the gold, was 0.95 0.985 1.07)
		vec3 loNight = vec3(0.93, 0.98, 1.10);
		vec3 lo = mix(mix(loDay, loGold, warm), loNight, night);
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
		vec3 gDay = vec3(0.95, 0.935, 0.90); // (look3: sunlit, warm-neutral, was look2's 0.86 0.85 0.82, which killed the sun)
		vec3 gGold = mix(vec3(1.03, 0.985, 0.92), vec3(1.015, 0.975, 0.95), morn);
		vec3 gOver = vec3(0.94, 0.95, 0.96);
		vec3 gNight = vec3(1.04, 1.10, 1.20); // (look2: moonlit and readable, was 0.92 0.97 1.04)
		vec3 gain = mix(mix(gDay, gGold, warm), gOver, over);
		gain = mix(gain, gNight, night * open * (1.0 - keepWarm));
		gain = mix(gain, vec3(dot(gain, vec3(0.2126, 0.7152, 0.0722))), 0.5 * blueG * (1.0 - night));
		// lift: a soft coloured toe, so the shade reads coloured rather than black (cool-neutral by day, a plum-blue
		// under the golden light, a blue-grey at night)
		vec3 lDay = vec3(0.010, 0.012, 0.016);
		vec3 lGold = vec3(0.014, 0.011, 0.018);
		vec3 lOver = vec3(0.016, 0.017, 0.019);
		vec3 lNight = vec3(0.009, 0.012, 0.020); // (look2: a moonlit blue-grey toe, was 0.004 0.006 0.011)
		vec3 lift = mix(mix(lDay, lGold, warm), lOver, over);
		lift = mix(lift, lNight, night);
		// gamma: the mids a little lower by day (calm), held at the golden hour, a little higher under an overcast
		float gamma = mix(mix(0.98, 1.0, warm), 1.03, over); // (look3: noon 0.98, was 0.95)
		// (look2) moonlit nights a little more open in the mids, so the land reads under the moon
		gamma = mix(gamma, 1.14, night * open);
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
		yd2 -= mix(0.11, 0.08, warm) * roll * roll * day * open * (1.0 - keepWarm); // (look3: 0.11, was 0.17)
		d *= yd2 / max(yd, 1e-5);
	}

	// the white point stays: what was at or past white (the pixel sun's disc, the moon, a flame's core, a glint) keeps
	// its own colour, so the disc stays a crisp white in its graded glow instead of melting into it
	float wp = smoothstep(0.72, 0.97, min(d0.r, min(d0.g, d0.b)));
	d = mix(d, d0, wp * wp);
	return gradeLimit(mix(c, d, k));
}

#endif
