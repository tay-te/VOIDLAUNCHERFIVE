#ifndef WELKIN_SHAFTS
#define WELKIN_SHAFTS

// VOID Welkin, workstream D (air and tone, 2026-10-13): sun shafts from the shadow map, subtle, at a low sun.
//
// The raw term (program/deferred_shafts.glsl, run as deferred5, half resolution, colortex13): for each half-resolution
// texel the view ray is walked from the eye toward the surface (at most SHAFT_REACH blocks, inside the shadow map's
// reach) in SHAFT_STEPS even steps whose start is offset by the 4x4-tiled lighting noise (lib/noise.glsl); each step is
// one hardware compare of the shadow map: is the air there in the sun? r: the sunlit length of the ray over
// SHAFT_REACH (0..1), a: the view distance of the surface, for the resolve.
//
// The resolve and the light (composite2, shaftsAt): the raw term is gathered over the 4x4 half-resolution texels round
// the pixel with depth weights (every noise offset of the tile is in the window, so a single frame is smooth and stable;
// a texel across a depth edge is left out, so a trunk's outline stays sharp), then turned into light: the sun's
// colour (the sun-side horizon's hue, as the haze's forward scatter) by a forward lobe toward the sun, by the sunlit
// length, gated to a low sun and to the near air. Under a canopy at a low sun the map's gaps leave beams of sunlit air
// between the trunks; in the open the long shadows of hills and woods streak it. Only ever adds light, at most about
// a dozen 8-bit levels; nearly nothing at noon. Only the lighting term is noisy and resolved; colour and texture never
// pass through it.
//
// Cost: SHAFT_STEPS compares per half-resolution texel; the pass returns at once while the gate is shut (noon, night),
// and composite2 skips the gather then.
//
// ---- atmos (welkin-atmos, shafts and bloom step, 2026-10-16): bold god rays, built from the depth prototype SHAFTS.
// Two parts, both smooth (the lighting noise only inside their raw terms, resolved over the 4x4 noise tile):
//   3D shafts  with the volumetric haze on, its walk (lib/haze/volume.glsl hvWalk) gives the term: the sunlit air along
//              the whole ray, near through the shadow map (crowns, trunks, houses), far through the terrain's shadow
//              line (ridges, mesas), both through the clouds' shadow (the gaps between clouds), weighted toward the eye
//              by exp(-t / SHAFT_FALLOFF). shaftLight turns it into the sun's own warm light by a strong forward lobe
//              and a broad one: bold beams toward a low sun, and side-on a soft directional glow rather than nothing.
//              Open from the horizon through the golden hours, a quarter at noon, thicker in hazy and misty air.
//   beams      (SHAFT_BEAMS) when the sun stands in or near the view: composite3 walks each half-resolution texel
//              toward the sun's place on the screen and gathers the open sky (no land, no LOD, no cloud) weighted by
//              the glow round the sun, so the light through gaps in a crown, between clouds and over a ridge's outline
//              streams toward the eye as radial beams; final gathers the 4x4 texels round each pixel and adds them.

#include "/lib/settings.glsl"

#define SHAFT_STEPS 12
#define SHAFT_REACH 96.0
// atmos: the 3D shafts' reach (lib/haze/volume.glsl): the air's weight falls by e over this many blocks from the eye
#define SHAFT_FALLOFF 220.0

#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif

// How open the gate is for the sun at world direction sunW: fully from about 12 degrees down to the horizon, easing to
// a quarter from about 45 degrees up (beams through a canopy at noon stay, faint), shut at night. Uniform over the
// frame. (atmos: was shut from 37 degrees up; the bold shafts keep a trace by day)
float shaftGate(vec3 sunW) {
	float h = sunW.y;
	return smoothstep(-0.02, 0.05, h) * mix(1.0, 0.25, smoothstep(0.2, 0.72, h));
}

// ---- the resolve and the light, for composite2 (define WELKIN_SHAFTS_READ and include lib/air.glsl first)
// atmos: the 3D shafts' gain and cap (shaftLight)
#define SHAFT_GAIN 1.7
// (look) the near air's gain
#define SHAFT_GAIN_NEAR 2.6
// (look) the near air's own reach for the beams' strength (blocks)
#define SHAFT_NEAR 32.0
#define SHAFT_CAP 0.50
// (look4) the near air's beams, by the sunlit length (shaftLight)
#define SHAFT_NEAR_BEAM 1.4
// (review round 1) on the open sky: the beams between clouds and over ridges toward a low sun
// (golden, 2026-10-16: 0.25, was 0.4: on the open sky the sunlit air only whitened the glow round a 17:00 to 18:00 sun
// into a milky disc; the beams (SHAFT_BEAMS) carry the rays over the sky)
#define SHAFT_SKY 0.25

#ifdef WELKIN_SHAFTS_READ
#ifndef WU_colortex13
#define WU_colortex13
uniform sampler2D colortex13;
#endif

// The raw term gathered over the 4x4 half-resolution texels round full-resolution pixel p (offsets -2..1: each of the
// noise tile's 16 offsets once), weighted by how near each texel's surface lies to this pixel's (dist, blocks; 1e4 for
// the sky), so the beams stop at a trunk's or a ridge's outline.
float shaftResolve(ivec2 p, float dist) {
	ivec2 qc = p >> 1;
	ivec2 hi = textureSize(colortex13, 0) - 1;
	float sum = 0.0, wsum = 0.0, tileSum = 0.0;
	for (int y = -2; y < 2; y++) {
		for (int x = -2; x < 2; x++) {
			vec4 r = texelFetch(colortex13, clamp(qc + ivec2(x, y), ivec2(0), hi), 0);
			float tol = 0.06 * min(dist, r.a) + 0.4;
			// (look3: dist < 0, a water pixel: the walk saw the bed, not the water, so every texel counts alike)
			float w = dist < 0.0 ? 1.0 : exp(-abs(r.a - dist) / tol);
			sum += r.r * w;
			wsum += w;
			tileSum += r.r;
		}
	}
	// (look3) where no texel matches, the plain mean of the whole tile (smooth), never one noisy texel
	return mix(tileSum / 16.0, sum / max(wsum, 1e-6), smoothstep(0.02, 0.5, wsum));
}

// The shafts' light for view direction dirW (world, unit), `lit` the resolved term: in-scattered light to add (scene
// light since HDR stage B). f the
// luminous sky's frame (lib/sky/luminous.glsl: the sun's hue and gold, how golden the hour, how clear the sky); thick
// how thick the air is against temperate air (haziness, valley mist; 1 plain). On the sky (`sky`) a fraction: there the
// sunlit air only brightens the glow round the sun the dome already draws; the beams show against the land, the trunks
// and the crowns. (atmos: bold, in the sun's own warm light, soft-capped well under white)
// (review round 1) dist: the surface's view distance (blocks; anything large for the sky). The beams are the term
// against what the same ray would gather wholly in the sun: the open air's glow comes from the ray's length, and the
// share of it in shadow is taken out more than in proportion, so the crowns', ridges' and clouds' shadows stand as dark
// lanes between bright beams. (Before, the soft cap saturated every long ray toward the sun to the same level, a half
// shadowed ray kept 0.35 of a lit one's 0.40, and no beam could be seen.)
vec3 shaftLight(vec3 dirW, vec3 sunW, int kind, float lit, bool sky, SkyLum f, float thick, float dist) {
	float mu = dot(dirW, sunW);
	// a strong forward lobe (the beams toward the sun) and a broad one (side-on, a soft directional glow)
	// (look, welkin-look 2026-10-18) the near air (a few dozen blocks: between trunks, under crowns) is treated apart from
	// the far: it counts for more, scatters over a broader lobe with a floor (beams through a canopy read side-on too),
	// and keeps more of a half-shadowed ray. The far air (long rays over the open land, the water, the sky) stays as it
	// was, so no veil comes over the frame toward a low sun.
	float nearW = sky ? 0.0 : 1.0 - smoothstep(24.0, 110.0, dist);
	float lobe = 0.75 * airHg(mu, 0.88) + 0.25 * airHg(mu, 0.5) + 0.15 * nearW;
	// the sun's light: its own hue, led toward the warm gold through the golden hours (display, luma 1)
	vec3 hue = mix(f.hue, f.gold, 0.55); // (HDR stage B: linear, luma 1)
	hue /= max(dot(hue, vec3(0.2126, 0.7152, 0.0722)), 1e-3);
	// as bright as the sun-side horizon (never darker than a pale level)
	vec3 sunSide = sceneFromAuthored(skyHazeHorizon(kind, true));
	float level = max(dot(sunSide, vec3(0.2126, 0.7152, 0.0722)), 0.07);
	float gate = shaftGate(sunW) * f.clear * f.day * mix(0.75, 1.0, f.warm);
	// (the term to the 1.25th power: a ray half in shadow keeps less than half, so the beams stand clearer against the
	// shadows between them, and the short rays down to the near ground lift its darks less)
#ifdef HAZE_VOLUME
	float open = sky ? 1.0 : 1.0 - exp(-min(dist, 3000.0) / SHAFT_FALLOFF);
#else
	float open = sky ? 1.0 : clamp(dist / SHAFT_REACH, 0.0, 1.0);
#endif
	float ratio = clamp(max(lit, 0.0) / max(open, 1e-3), 0.0, 1.0);
	// (look, welkin-look 2026-10-18: the near air counts for more, so the beams between trunks and crowns a few dozen
	// blocks off show: by the physical weight alone a 20-block ray carried a twentieth of a long one's light and the
	// beams under a canopy stayed at two 8-bit levels)
	float amp = sky ? 1.0 : max(pow(open, 1.25), 1.0 - exp(-dist / SHAFT_NEAR));
	float x = float(SHAFT_STRENGTH) * 0.01 * mix(SHAFT_GAIN, SHAFT_GAIN_NEAR, nearW) * lobe * gate * amp * thick * (sky ? SHAFT_SKY : 1.0);
	// a soft cap on the open air's glow (never more than SHAFT_CAP of the sun-side level), then the shadowed share out
	// (look) in the near air a wholly sunlit ray (an open field, open water) gives back much of its extra: the near
	// air's strength is for the beams where light and shadow alternate (a canopy, trunks), not a veil over the open
	float beamy = mix(1.0, 1.0 - 0.75 * smoothstep(0.45, 0.95, ratio), nearW);
	vec3 farL = hue * level * (SHAFT_CAP * (1.0 - exp(-x / SHAFT_CAP))) * pow(ratio, mix(2.0, 1.5, nearW)) * beamy;
	// (look4, 2026-10-18) the near air's beams by the sunlit length itself, in blocks: a ray under a canopy that crosses a
	// column of light a few blocks thick, where the sun comes through a gap onto the floor, shows that column as a beam
	// (the share of the ray, to the 1.5th power, left such a column at a few percent: a 30-block ray crossing a 3-block
	// beam kept 0.035 of the light). Seen toward the sun most, but side-on and from behind too (the air's broad lobe):
	// light falling through a canopy reads as shafts from any side. A ray mostly in the sun (a clearing, the open
	// field) gives it back: the beams are where light and shade alternate
	float litB = max(lit, 0.0) * SHAFT_FALLOFF;
	float colB = (1.0 - exp(-litB / 6.0)) * (1.0 - 0.9 * smoothstep(0.12, 0.55, ratio));
	float lobeN = 0.45 + 0.55 * airHg(mu, 0.55);
	// (the canopy's beams stay through the afternoon: the gate's noon quarter only from about 40 degrees of sun)
	float gateN = max(gate, 0.7 * smoothstep(-0.02, 0.05, sunW.y) * (1.0 - smoothstep(0.55, 0.8, sunW.y)) * f.clear * f.day);
	vec3 nearL = hue * level * SHAFT_NEAR_BEAM * colB * lobeN * gateN * thick * float(SHAFT_STRENGTH) * 0.01;
	// (not at a grazing sun: then the near air over the field lies in the ridges' and the grass's long shadows, and the
	// sunlit air above it read as a milky smear over the near ground, not as beams; the far shafts carry that hour)
	return farL + nearL * nearW * smoothstep(0.08, 0.22, sunW.y);
}
#endif

#endif
