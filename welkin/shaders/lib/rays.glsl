// VOID Welkin, look pass 6 (2026-10-19): the screen-space light shafts (SHAFT_BEAMS), rebuilt as a multi-pass radial
// blur. Written from scratch for Welkin; no other pack's code.
//
//   composite3  the source (half size, colortex12 r): each texel the light the open sky sends toward the eye there,
//               a soft, continuous value: the sky's own brightness (against the sun-side horizon's), weighted by how
//               near the sun it stands on the screen. A cloud gives its lit, thin parts (its brightness past the sky's)
//               and not its dark body; the land, leaves and the far land give nothing.
//   composite5  the first radial blur (12 -> 13): RAY_TAPS taps toward the sun's place on the screen, a tiny step
//   composite6  the second (13 -> 12), each step RAY_TAPS times the last
//   composite7  the third (12 -> 13), its step RAY_TAPS times again, so each texel averages RAY_TAPS^3 points (1000)
//               along its line toward the sun: smooth, with no noise and no grain, and leaf gaps and cloud edges stay
//               resolved as beams. This pass also gives the light its colour and the hour's strength (rgb, display).
//   final       lays the beams on as light in the air: scaled by how much air lies between the eye and the pixel, with
//               a floor near the eye so the beams under a crown keep the near air they live in.

// how far toward the sun each texel's line reaches (of the way to the sun), and the taps per pass
#define RAY_REACH 0.92
#define RAY_TAPS 10
// how wide the bright source round the sun is (screen heights): a tight core and a broad tail
#define RAY_CORE 0.16
#define RAY_TAIL 0.55
#define RAY_TAIL_SHARE 0.45
// how far the beams reach from the sun before they fade (screen heights)
#define RAY_FALL 0.7
// how much the openness of a line toward the sun is sharpened (1: as it is), so beams stand against dark lanes
#define RAY_CONTRAST 1.0
// how much less a wholly open line gives than a broken one (of its light)
#define RAY_FLAT 0.85
// how much a cloud's lit, thin parts give (of the open sky's)
#define RAY_CLOUD 0.7

// The sun's place on the screen (xy, 0..1) and whether it stands in front of the eye (z).
vec3 raySunScreen(mat4 proj, vec3 sunView) {
	vec4 clip = proj * vec4(sunView, 1.0);
	return vec3(clip.w > 0.0 ? clip.xy / clip.w * 0.5 + 0.5 : vec2(0.5), clip.w > 0.0 ? 1.0 : 0.0);
}

// How much the sun well off the screen still sends: none from more than about a screen height past the edge.
float rayOnScreen(vec2 sunUV, float aspect) {
	return 1.0 - smoothstep(0.2, 0.9, length(max(abs(sunUV - 0.5) - 0.5, 0.0) * vec2(aspect, 1.0)));
}

// The source's weight by the screen distance from the sun (screen heights).
float rayNear(float far) {
	return mix(exp(-far / RAY_CORE), exp(-far / RAY_TAIL), RAY_TAIL_SHARE);
}

// How bright the beams are by the screen distance from the sun (screen heights).
float rayFall(float far) {
	return mix(exp(-far / RAY_CORE), exp(-far / RAY_FALL), 0.7);
}

// One radial pass: the mean of RAY_TAPS points from uv toward the sun, `frac` of the way between neighbours.
vec4 rayBlur(sampler2D src, vec2 uv, vec2 sunUV, float frac) {
	vec2 d = (sunUV - uv) * frac;
	vec4 s = vec4(0.0);
	for (int j = 0; j < RAY_TAPS; j++) {
		s += textureLod(src, uv + d * float(j), 0.0);
	}
	return s / float(RAY_TAPS);
}
