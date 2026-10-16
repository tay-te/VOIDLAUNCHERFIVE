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

#include "/lib/settings.glsl"

#define SHAFT_STEPS 12
#define SHAFT_REACH 96.0

#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif

// How open the gate is for the sun at world direction sunW: fully from about 9 degrees down to the horizon, about half
// at 16:30, shut from about 37 degrees up (the pass then costs nothing) and at night. Uniform over the frame.
float shaftGate(vec3 sunW) {
	float h = sunW.y;
	return smoothstep(0.0, 0.05, h) * (1.0 - smoothstep(0.15, 0.6, h));
}

// ---- the resolve and the light, for composite2 (define WELKIN_SHAFTS_READ and include lib/air.glsl first)
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
	float sum = 0.0, wsum = 0.0;
	for (int y = -2; y < 2; y++) {
		for (int x = -2; x < 2; x++) {
			vec4 r = texelFetch(colortex13, clamp(qc + ivec2(x, y), ivec2(0), hi), 0);
			float tol = 0.06 * min(dist, r.a) + 0.4;
			float w = exp(-abs(r.a - dist) / tol);
			sum += r.r * w;
			wsum += w;
		}
	}
	return wsum > 1e-3 ? sum / wsum : texelFetch(colortex13, clamp(qc, ivec2(0), hi), 0).r;
}

// The depth prototypes (HAZE, SHAFTS): the whole raw term (r shafts, g the haze's sunlit in-scatter, b its whole
// in-scatter) gathered as shaftResolve gathers r.
vec3 wdVolResolve(ivec2 p, float dist) {
	ivec2 qc = p >> 1;
	ivec2 hi = textureSize(colortex13, 0) - 1;
	vec3 sum = vec3(0.0);
	float wsum = 0.0;
	for (int y = -2; y < 2; y++) {
		for (int x = -2; x < 2; x++) {
			vec4 r = texelFetch(colortex13, clamp(qc + ivec2(x, y), ivec2(0), hi), 0);
			float tol = 0.06 * min(dist, r.a) + 0.4;
			float w = exp(-abs(r.a - dist) / tol);
			sum += r.rgb * w;
			wsum += w;
		}
	}
	return wsum > 1e-3 ? sum / wsum : texelFetch(colortex13, clamp(qc, ivec2(0), hi), 0).rgb;
}

// The shafts' light for view direction dirW (world, unit), `lit` the resolved sunlit share, in display values to add.
// On the sky (`sky`) a fraction: there the sunlit air only brightens the glow round the sun the dome and the forward
// scatter already draw; the beams show against the land, the trunks and the crowns.
vec3 shaftLight(vec3 dirW, vec3 sunW, int kind, float lit, bool sky) {
	float mu = dot(dirW, sunW);
#if WD_SHAFTS
	{
		// depth prototype SHAFTS: real god rays. The air's sunlit share along the ray (near: the shadow map, far: the
		// terrain's shadow line, both: the clouds' shadow; deferred_shafts wdVolume) scatters the sun's light toward
		// the eye by a strong forward lobe plus a broad one, so looking toward a low sun the beams between trunks, under
		// cloud gaps and over ridges stand out bright and warm, and side-on they still streak the air. The gate is open
		// from the horizon to the mid afternoon (a trace at noon); a soft cap keeps the brightest beams under white.
		float lobeS = 0.45 * airHg(mu, 0.86) + 0.4 * airHg(mu, 0.5) + 0.15;
		vec3 sunSideS = skyHazeHorizon(kind, true);
		vec3 colS = mix(sunSideS / max(dot(sunSideS, vec3(0.2126, 0.7152, 0.0722)), 1e-3), vec3(1.0), 0.15);
		float h = sunW.y;
		float gateS = smoothstep(-0.02, 0.06, h) * mix(1.0, 0.18, smoothstep(0.25, 0.85, h));
		// (the beams carry it: where the whole ray is sunlit, open air with nothing to cut it, it is only a glow, at
		// less than half; where the ray is partly shadowed, beams and the gaps between them, it shows whole)
		float openAir = smoothstep(0.7, 1.0, lit);
		float xS = (WD_ALL == 1 ? 0.75 : 1.0) * 2.2 * (sky ? 0.8 : 1.0) * lobeS * gateS * lit * mix(1.0, 0.42, openAir)
			* (1.0 - skyNightStep());
		return colS * (0.34 * (1.0 - exp(-xS / 0.34)));
	}
#endif
	float lobe = 0.55 * airHg(mu, 0.8) + 0.45 * airHg(mu, 0.35);
	vec3 sunSide = skyHazeHorizon(kind, true);
	vec3 col = sunSide / max(dot(sunSide, vec3(0.2126, 0.7152, 0.0722)), 1e-3);
	float x = (float(SHAFT_STRENGTH) * 0.01 * 0.22 * (sky ? 0.3 : 1.0)) * lobe * shaftGate(sunW) * lit
		* (1.0 - skyNightStep());
	// a soft cap: never more than about a dozen 8-bit levels (0.05), however long the sunlit air
	return col * (0.05 * (1.0 - exp(-x / 0.05)));
}
#endif

#endif
