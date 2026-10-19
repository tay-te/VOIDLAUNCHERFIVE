// VOID Welkin, atmos (welkin-atmos, shafts and bloom step, 2026-10-16): the bloom. Built from the depth prototype
// BLOOM (welkin-depth c1383920..18ccd89a), for real and stronger.
//
// Welkin's passes leave display values squeezed under white (lib/tone.glsl's shoulder for the land, the sky table's
// colours for the sky): a near-white pixel stands for much more light than it shows. The bloom gives that light its
// headroom back (bloomHeadroom: by luminance, up to about 9x at white, so a saturated sunlit flower with one channel at
// white does not take the sun's headroom and smear into a coloured cloud), keeps only the share past a soft threshold
// (bloomSource), and spreads it:
//
//   composite3  half resolution: each texel the 2x2 pixels under it, their bright shares averaged with the soft
//               weights 1 / (1 + 0.15 brightness) (one blazing pixel cannot flicker into a blotch) -> colortex14 (mipmapped)
//   composite4  half resolution: six of colortex14's mip levels, each through a 3x3 tent at its own texel size, summed
//               with weights that add to 1 (energy-conserving: the halo spreads the bright light, never makes more of
//               it): a tight core from the fine levels, a wide soft halo from the coarse ones -> colortex9
//   final       the halo laid on in linear light with headroom, before the shoulder: each pixel lends out the bright
//               share it gave the halo and takes back what the halo brings it, then goes back under its own headroom
//               (bloomApply). Textures are never filtered: only light past the threshold spreads, so a texel's pattern
//               stays crisp. The sky lends nothing out (the pixel sun, moon and stars keep their full brightness; their
//               halo only adds).
//
// Sources: the sun and the bright sky round it, sunlit cloud edges, pale sunlit stone, sand and snow, glints and the
// glitter path on water, and anything emissive (torches, lamps, lava: colortex2's emission adds headroom), so lamps
// glow at night. Written from scratch for Welkin; no other pack's code.

#ifndef WELKIN_BLOOM
#define WELKIN_BLOOM

#include "/lib/settings.glsl"

// the threshold and its knee, in linear light with headroom (1 = display white without headroom)
// (look2: 0.85, was 1.0)
#define BLOOM_THRESHOLD 0.85
#define BLOOM_KNEE 0.4
// how much of the bright share scatters into the halo at strength 100
// (look2: 1.3, was 0.5: the glow part of the air)
#define BLOOM_SCATTER 1.3
// the open sky's share of the source: a bright sky is a broad source, and wholly it washed the land under a grey-gold
// veil whenever the sun stood in the view; the sun and its glow still bloom
// (look2: 0.32, was 0.2: the golden-hour sky round the sun glows)
#define BLOOM_SKY 0.32
// the clouds' share of the source (the merge of phase 2, with welkin-clouds' lit clouds): a sunlit deck sits just under
// white over half the view, and wholly it veiled a peak over the cloud sea in milk; a cloud's body gives this share, a
// rim past white (the silver lining, added past the clouds' shoulder) its whole light
#define BLOOM_CLOUD 0.3
// how much of the frame's mean bright share is kept off every pixel's halo (bloomApply's flat veil)
#define BLOOM_FLAT 0.85

// (review round 1) How much view direction v (view space, unit) lies on the pixel sun's disc and its rim, 0..1, from
// sunV (view space, unit): the sun is drawn SUN_SIZE art pixels of SKY_ART_PIXEL 1e-4 radians in radius.
float bloomSunDisc(vec3 v, vec3 sunV) {
	float r = float(SUN_SIZE) * float(SKY_ART_PIXEL) * 1e-4;
	float a = sqrt(max(2.0 * (1.0 - dot(v, sunV)), 0.0));
	return 1.0 - smoothstep(0.95 * r, 1.5 * r, a);
}

float bloomLuma(vec3 c) {
	return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// How much of a cloud pixel's bright share goes into the bloom: BLOOM_CLOUD for the body, all of it for a rim past white.
float bloomCloudShare(vec3 display) {
	return mix(BLOOM_CLOUD, 1.0, smoothstep(1.0, 1.4, max(display.r, max(display.g, display.b))));
}

// How much more light a display colour (taken to linear: lin) stands for: 1 up to the mids, about 6 at white.
// `diffuse` (review round 1): 1 for a lit surface that does not glow (the land, not a lamp, not the sky or a cloud):
// its texture's near white is a pale albedo in plain daylight, not a light source, so it gets headroom only for what
// is lit past display white (a glint, the glitter, a sunlit wet sheen), never for being white; a sunlit daisy or a
// birch's bark took the sun's headroom and bloomed into a square halo.
float bloomHeadroom(vec3 lin, float emit, float diffuse) {
	float y = mix(bloomLuma(lin), max(lin.r, max(lin.g, lin.b)), 0.25);
	float s = smoothstep(0.5, 1.0, y);
	float h = 1.0 + 5.0 * s * s * s;
	float past = smoothstep(1.0, 1.35, max(lin.r, max(lin.g, lin.b)));
	h = mix(h, 1.0 + 5.0 * past, diffuse * (1.0 - smoothstep(0.0, 0.2, emit)));
	return h * (1.0 + 16.0 * emit); // (look2: 16, was 8: lamps and fire glow at night)
}

float bloomHeadroom(vec3 lin, float emit) {
	return bloomHeadroom(lin, emit, 0.0);
}

// The bright share of display colour `display` (linear, with headroom). emit 0..1 a glowing texel's own light.
vec3 bloomSource(vec3 display, float emit, float diffuse) {
	vec3 lin = pow(max(display, 0.0), vec3(2.2));
	lin *= bloomHeadroom(lin, emit, diffuse);
	float y = bloomLuma(lin);
	float soft = clamp(y - BLOOM_THRESHOLD + BLOOM_KNEE, 0.0, 2.0 * BLOOM_KNEE);
	soft = soft * soft / (4.0 * BLOOM_KNEE + 1e-5);
	float share = max(soft, y - BLOOM_THRESHOLD) / max(y, 1e-5);
	return lin * clamp(share, 0.0, 1.0);
}

// The halo `glow` (linear, with headroom) laid on display colour c; `lend` how much of the pixel's own bright share it
// lends out (1 wholly energy-conserving; final lends half on the land, so a lamp's small flame keeps its brightness
// beside its halo, and nothing on the sky). `veil` is the frame's mean bright share (composite4, colortex9's alpha): that
// much of what the halo brings a pixel is a flat veil over the whole frame, not a glow round anything, so BLOOM_FLAT of
// it is kept off (the merge of phase 2: a sunlit snow peak over a sunlit cloud sea filled the frame with source and the
// shadows went milky; the darks keep their depth, a lamp at night or the sun's glow is unchanged, as their frame's mean
// is small beside them). Display in, display out (before final's shoulder).
vec3 bloomApply(vec3 c, vec3 glow, float emit, float lend, float veil, float diffuse) {
	float k = BLOOM_SCATTER * float(BLOOM_STRENGTH) * 0.01;
	vec3 lin = pow(max(c, 0.0), vec3(2.2));
	float head = bloomHeadroom(lin, emit, diffuse);
	vec3 own = bloomSource(c, emit, diffuse);
	vec3 add = k * (glow - lend * own);
	add -= min(vec3(k * BLOOM_FLAT * veil), max(add, 0.0));
	vec3 hdr = lin * head + add;
	lin = max(hdr / head, 0.0);
	return pow(lin, vec3(1.0 / 2.2));
}

#endif
