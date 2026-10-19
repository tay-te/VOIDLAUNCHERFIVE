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
#define RAY_REACH 1.06
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
#define RAY_GAIN 10.0
#define RAY_AIR 90.0
#define RAY_NEAR_FLOOR 0.3
#define RAY_SKY 0.32
// (Look D1) the beams over the far land and water: from RAY_FAR_START to RAY_FAR_END blocks they thin to this share
#define RAY_FAR_SHARE 0.3
#define RAY_FAR_START 60.0
#define RAY_FAR_END 400.0

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

// (Look D2) One radial pass in log distance: RAY_TAPS points from uv toward the sun, each `step` (natural log) nearer
// it than the last (the distance to the sun times exp(-step) a tap), each weighted by its distance to the sun to the
// power RAY_WEIGHT_POW. A pass is the same at every scale, so the three chained passes join without holes at any
// distance from the sun (the even steps of rayBlur left the last stretch to the sun to one point, and a gap there
// was drawn again as ghost copies down the beam), and every line reaches the sun.
//   RAY_WEIGHT_POW 1 is the plain mean along the line (a gap of width w at distance r lights the line beyond it at
//   about w / distance: beams fade fast); 0 weighs each octave of distance alike (a gap's beam holds its strength all
//   the way out, the nearest stretch round the sun counts most). Between, the beams carry farther than the plain
//   mean, the sun's own disc does not dominate.
#define RAY_LOG_SPAN 5.6   // the three passes' reach in log distance: the line runs to exp(-5.6) = 1/270 of the way
#define RAY_LOG_OVERLAP 2.0 // each pass's span over the next pass's step
#define RAY_WEIGHT_POW 1.0
vec3 rayBlurLog(sampler2D src, vec2 uv, vec2 sunUV, float step) {
	vec2 v = uv - sunUV;
	float f = 1.0;
	float w = 1.0;
	float q = exp(-step);
	float qw = exp(-step * RAY_WEIGHT_POW);
	vec3 s = vec3(0.0);
	float ws = 0.0;
	for (int j = 0; j < RAY_TAPS; j++) {
		s += w * textureLod(src, sunUV + v * f, 0.0).rgb;
		ws += w;
		f *= q;
		w *= qw;
	}
	return s / ws;
}
// the passes' steps: the coarse pass (3) takes most of the span, each finer pass spans RAY_LOG_OVERLAP of the next one's
// step
#define RAY_LOG_STEP3 (RAY_LOG_SPAN / (float(RAY_TAPS) + RAY_LOG_OVERLAP + RAY_LOG_OVERLAP * RAY_LOG_OVERLAP / float(RAY_TAPS)))
#define RAY_LOG_STEP2 (RAY_LOG_STEP3 * RAY_LOG_OVERLAP / float(RAY_TAPS))
#define RAY_LOG_STEP1 (RAY_LOG_STEP2 * RAY_LOG_OVERLAP / float(RAY_TAPS))

// ---- (Look D2) robust rays: the beams are the STRUCTURE of light and occluders round the sun, not the open sky's mass.
// The radial blur turns any shape near the sun into its radial smear: a small gap a thin beam, a large gap (or the
// sun's disc in one) a cone of light as wide as the gap seen from the sun, a solid fan with the gap's outline. That
// unbroken part is the glow round the sun, which the dome's aureole and the bloom already draw. So final keeps what
// the angular neighbourhood round the sun does not share: each pixel's shafts less the mean of the shafts at the same
// distance from the sun, a few degrees round either way (RAY_HP_STEP x 1..RAY_HP_RING, a smooth kernel), and only
// RAY_FLAT_KEEP of the shared part. A beam narrower than the kernel keeps nearly all its light; a fan wider than it
// keeps its edges as soft beams and a little glow.
#define RAY_HP_RING 4
#define RAY_HP_STEP 0.105 // radians a ring step (6 degrees): the kernel reaches 24 degrees either way
#define RAY_HP 1.0        // how much of the shared (flat) part is taken off
#define RAY_FLAT_KEEP 0.12 // what is kept of it (a faint glow in the beams' colour)

vec3 rayStructure(sampler2D tex, vec2 uv, vec2 sunUV, float aspect, vec3 r0) {
	const vec3 W = vec3(0.2126, 0.7152, 0.0722);
	float y0 = dot(r0, W);
	if (y0 < 1e-6) return vec3(0.0);
	vec2 v = (uv - sunUV) * vec2(aspect, 1.0);
	float sum = 0.0;
	float wsum = 0.0;
	for (int i = 1; i <= RAY_HP_RING; i++) {
		float a = RAY_HP_STEP * float(i);
		float c = cos(a);
		float s = sin(a);
		float w = exp(-0.5 * float(i * i) / float(RAY_HP_RING * RAY_HP_RING) * 4.0); // a Gaussian over the ring
		vec2 p1 = sunUV + vec2(c * v.x - s * v.y, s * v.x + c * v.y) / vec2(aspect, 1.0);
		vec2 p2 = sunUV + vec2(c * v.x + s * v.y, -s * v.x + c * v.y) / vec2(aspect, 1.0);
		bool in1 = all(greaterThanEqual(p1, vec2(0.0))) && all(lessThanEqual(p1, vec2(1.0)));
		bool in2 = all(greaterThanEqual(p2, vec2(0.0))) && all(lessThanEqual(p2, vec2(1.0)));
		float y1 = in1 ? dot(textureLod(tex, p1, 0.0).rgb, W) : 0.0;
		float y2 = in2 ? dot(textureLod(tex, p2, 0.0).rgb, W) : 0.0;
		// off the screen a side is unknown: the other side stands in for it
		y1 = in1 ? y1 : (in2 ? y2 : y0);
		y2 = in2 ? y2 : y1;
		sum += w * (y1 + y2);
		wsum += 2.0 * w;
	}
	float flatY = sum / wsum;
	float sharedY = min(y0, flatY);
	float keep = max(y0 - RAY_HP * flatY, 0.0) + RAY_FLAT_KEEP * sharedY;
	return r0 * (keep / y0);
}

