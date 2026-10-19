// VOID Welkin, HDR stage C (2026-10-19): the light shafts in scene light, a three-pass radial blur (after look pass 6,
// welkin-look6 1f973181, rebuilt on the scene-light pipeline). Written from scratch for Welkin; no other pack's code.
//
//   composite3  the source (half size, colortex12 rgb): each texel the light the open sky sends toward the eye there,
//               in exposed scene light (the sun's disc and its aureole included, eased under RAY_SRC_CAP so a beam is
//               the sky's light, not the disc's), times what the clouds in front let through (colortex6 g, their
//               transmittance), times how near the sun it stands on the screen; land, leaves and the far land give
//               nothing. The hour's strength (low sun strongest, none at night, under an overcast or in water) in.
//   composite5  the first radial blur (12 -> 13): RAY_TAPS taps toward the sun's place on the screen, a tiny step
//   composite6  the second (13 -> 12), each step RAY_TAPS times the last
//   composite7  the third (12 -> 13), its step RAY_TAPS times again: each texel averages RAY_TAPS^3 points along its
//               line toward the sun, smooth, no noise, no grain; gaps in a crown and between clouds stay beams. Then
//               the fall-off from the sun.
//   final       adds them as in-scattered light before the output transform, by how much air lies in front.

// how far toward the sun each texel's line reaches (of the way to the sun), and the taps per pass
#define RAY_REACH 0.92
#define RAY_TAPS 16
// how far each pass reaches past the next pass's step (no steps between the copies)
#define RAY_OVERLAP 4.0
// how wide the bright source round the sun is (screen heights): a tight core and a broad tail
#define RAY_CORE 0.16
#define RAY_TAIL 0.55
#define RAY_TAIL_SHARE 0.45
// how far the beams reach from the sun before they fade (screen heights)
#define RAY_FALL 0.7
// the most light one source texel gives (exposed scene light)
#define RAY_SRC_CAP 6.0
// final: the strength of the beams' in-scattered light, the air in front that holds them (blocks), the floor of it
// near the eye (beams under a crown), and their share over the open sky (the dome already glows there)
#define RAY_GAIN 8.0
#define RAY_AIR 90.0
#define RAY_NEAR_FLOOR 0.3
#define RAY_SKY 0.25

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
vec3 rayBlur(sampler2D src, vec2 uv, vec2 sunUV, float frac) {
	vec2 d = (sunUV - uv) * frac;
	vec3 s = vec3(0.0);
	for (int j = 0; j < RAY_TAPS; j++) {
		s += textureLod(src, uv + d * float(j), 0.0).rgb;
	}
	return s / float(RAY_TAPS);
}
