// VOID Welkin, atmos (welkin-atmos, 2026-10-16): the volumetric haze. Built from the depth prototype HAZE (welkin-depth
// c1383920..18ccd89a), for real. The country haze (lib/haze/haze.glsl, M2) is an exact fog with the land's colours but
// one light: the air in a ridge's shadow glowed as brightly as the air in the sun. This is the air's light and shadow:
//
// The walk (program/deferred_shafts.glsl, run as deferred5, half resolution, colortex13): each half-resolution texel
// walks its view ray once, HAZE_VOLUME_STEPS steps, the start offset by the 4x4 lighting noise:
//   near   from the eye to the shadow map's reach, even steps, each one compare of the shadow map (trees, houses,
//          the near land), times the far casters' terrain shadow line over the camera's cell
//   far    from there to the surface (or HV_SKY_END on the sky), steps growing with the distance, each lit by the
//          terrain's shadow line (lib/terrain.glsl: ridges, from land off the screen too) over its own cell
//   both   times the clouds' shadow over the point (lib/clouds/shadow.glsl: the gaps between clouds)
// and the haze's density at each step (hvDensity: thick low down, in valleys and the low ground, thinning up the
// ridges; the rays toward the horizon stay low longest and gather most), with its own transmittance. Out:
//   r  the sun shafts' term (shafts and bloom step): the sunlit air along the whole ray, near (the shadow map: crowns,
//      trunks, houses) and far (the terrain's shadow line: ridges), times the clouds' shadow (their gaps), each step
//      weighted by exp(-t / SHAFT_FALLOFF) and the sum over SHAFT_FALLOFF: 0 no lit air, 1 a long ray wholly in the sun
//   g  the haze's sunlit in-scatter (the integral of density x sunlit x transmittance, blocks of sea-level air)
//   b  its whole in-scatter (the same without the sunlit share)
//   a  the view distance (for the depth-aware resolve)
// The resolve (composite2, hvResolve): the 4x4 half-resolution texels round the pixel, each noise offset once, depth
// weighted, so the result is smooth and stable in a single frame and stops at a trunk's or a ridge's outline. Only
// these lighting terms carry noise, and it is resolved away: no grain.
//
// The light (hvComposite): optical depth from the whole in-scatter times the density of the air here: the camera's
// country (its haze kind: humid lowlands and lake lands thicker, dusty lands warm, alpine air thin), Gloam's haziness
// after the hour's thinning (a hazy morning, a clearer noon), rain and snow; it yields where Gloam's valley mist
// already lies and hands over to the far haze (composite1, WF_FAR_HAZE) at the near tiles' band, so no air is laid
// twice. Its colour is the luminous air itself (the horizon the far land fades into): in shadow the sky's light only,
// cooler and dimmer; in the sun a little brighter, and toward a low sun the forward scatter mixes it toward the sun's
// amber (mixed, never added, then the hue-keeping shoulder: no white sun-side horizon). Opacity is capped well under
// a veil: the land always reads through it. Written from scratch for Welkin; no other pack's code.

#ifndef WELKIN_HAZE_VOLUME
#define WELKIN_HAZE_VOLUME

#include "/lib/settings.glsl"
#include "/lib/haze/haze_config.glsl"

// ---- the haze's density, relative to the air at sea level over open ground
// the regional air thins over HV_H_SEA blocks of height above sea level; the ground layer hugs the land over HV_H_GROUND
// blocks and is thickest on low ground (it thins over HV_H_LOWLAND blocks of the ground's own height)
#define HV_H_SEA 110.0
#define HV_H_GROUND 26.0
#define HV_H_LOWLAND 170.0
#define HV_GROUND_SHARE 0.85
// the walk's own extinction per block of sea-level air (only for the walk's transmittance)
#define HV_SIGMA_MARCH 0.0006
// the haze's extinction per block of sea-level air at strength 100 in temperate air
#define HV_SIGMA 0.0012
// how far a sky ray is walked
#define HV_SKY_END 2400.0
#define HV_LAND_END 3000.0
// the near air stays clear: a step's haze counts by t^2 / (t^2 + HV_NEAR_CLEAR^2), so the crowns and the ground near
// the eye keep their darks (a few percent of bright air over a near shade lifted its 5 % luma by a third)
#define HV_NEAR_CLEAR 72.0
// the most the volumetric haze covers (the land always reads through)
#define HV_MAX_OPACITY 0.70
// the near steps' spacing: t grows as this power of the step (1 even)
#define HV_NEAR_POW 1.6

float hvDensity(float y, float ground) {
	float sea = exp(-clamp(y - HAZE_SEA_LEVEL, -40.0, 600.0) / HV_H_SEA);
	float layer = exp(-max(y - ground, 0.0) / HV_H_GROUND) * exp(-clamp(ground - HAZE_SEA_LEVEL, 0.0, 600.0) / HV_H_LOWLAND);
	return sea + HV_GROUND_SHARE * layer;
}

// ---- the walk (deferred_shafts; define HV_WALK and include lib/shadow.glsl, lib/noise.glsl, lib/terrain.glsl first)
#ifdef HV_WALK
#if defined CLOUDS && CLOUD_SHADOWS > 0
#include "/lib/clouds/shadow.glsl"
#define HV_CLOUDS
#endif

// the far casters' and far points' sunlit share from the terrain's shadow line (1 without the terrain's light)
float hvLineVis(float y, vec2 line, float w) {
#ifdef WELKIN_TERRAIN_LIVE
	return mix(1.0, terrainLineVis(y, line.x, line.y), w);
#else
	return 1.0;
#endif
}

// shaftsOn: whether the shafts' term is wanted (the sun up and SUN_SHAFTS on); 0 in r otherwise.
vec4 hvWalk(ivec2 q, vec3 dirP, float dist, bool sky, bool shaftsOn) {
	float tEnd = sky ? HV_SKY_END : min(dist, HV_LAND_END);
#if defined WF_FAR_HAZE && defined DISTANT_HORIZONS
	// the far haze (composite1) owns the air past the near tiles' band (lib/haze/haze.glsl's hand-over)
	if (!sky) {
		tEnd = min(tEnd, 0.5 * (HZ_HANDOFF_START + HZ_HANDOFF_END) / max(length(dirP.xz), 1e-4));
	}
#endif
#ifdef WELKIN_SHADOWS_LIVE
	float nearEnd = min(tEnd, 0.97 * shadowReach());
#else
	float nearEnd = min(tEnd, 64.0);
#endif
	const int NN = (HAZE_VOLUME_STEPS * 3) / 5;
	const int NF = HAZE_VOLUME_STEPS - NN;
	float u = lightNoise(q, lightFrame());
	float camY = cameraPosition.y;
	// the camera's own cell: its ground for the near steps' density, its far casters' line for their light
	float g0 = HAZE_SEA_LEVEL;
	vec2 line0 = vec2(-1e4, 1.0);
	float w0 = 0.0;
#ifdef WELKIN_TERRAIN_LIVE
	{
		TerrainCell c0 = terrainAt(vec3(0.0));
		g0 = c0.w > 0.0 ? c0.ground : min(camY - 2.0, HAZE_SEA_LEVEL + 40.0);
		line0 = c0.line.zw;
		w0 = c0.w;
	}
#endif
#ifdef HV_CLOUDS
	CloudEnv ce = welkinCloudEnv();
#endif
	float shaft = 0.0, hzSun = 0.0, hzAll = 0.0, tau = 0.0;
	float cloudVis = 1.0;
	// (shafts and bloom step: the near steps crowd toward the eye, t growing as the 1.6th power of the step, so the beams
	// between trunks a few blocks off are walked as finely as the long shadows further out)
	for (int i = 0; i < NN; i++) {
		float s0 = float(i) / float(NN), s1 = (float(i) + 1.0) / float(NN), su = (float(i) + u) / float(NN);
		float t = nearEnd * pow(su, HV_NEAR_POW);
		float dtN = nearEnd * (pow(s1, HV_NEAR_POW) - pow(s0, HV_NEAR_POW));
		vec3 pp = dirP * t;
		float y = camY + pp.y;
#ifdef WELKIN_SHADOWS_LIVE
		float vis = texture(shadowtex1, shadowMapPos(pp));
#else
		float vis = 1.0;
#endif
		vis *= hvLineVis(y, line0, w0);
#ifdef HV_CLOUDS
		// (a cloud's shadow changes slowly along the near air: read every other step, which halves the walk's cost of it)
		if ((i & 1) == 0) {
			cloudVis = welkinCloudShadow(dirP * (t + 0.5 * dtN), ce);
		}
		vis *= cloudVis;
#endif
		shaft += vis * exp(-t / SHAFT_FALLOFF) * dtN;
		float rho = hvDensity(y, g0) * (t * t / (t * t + HV_NEAR_CLEAR * HV_NEAR_CLEAR));
		float tr = exp(-tau);
		hzSun += rho * vis * dtN * tr;
		hzAll += rho * dtN * tr;
		tau += rho * dtN * HV_SIGMA_MARCH;
	}
	if (tEnd > nearEnd + 1.0) {
		float r = tEnd / max(nearEnd, 1.0);
		for (int i = 0; i < NF; i++) {
			// steps growing with the distance (geometric between the near end and the surface)
			float a0 = float(i) / float(NF);
			float a1 = (float(i) + 1.0) / float(NF);
			float t = nearEnd * pow(r, (float(i) + u) / float(NF));
			float dt = nearEnd * (pow(r, a1) - pow(r, a0));
			vec3 pp = dirP * t;
			float y = camY + pp.y;
			float g = g0;
			float vis = 1.0;
#ifdef WELKIN_TERRAIN_LIVE
			TerrainCell tc = terrainAt(pp);
			g = tc.w > 0.0 ? mix(g0, tc.ground, tc.w) : g0;
			vis = hvLineVis(max(y, g), tc.line.xy, tc.w);
#endif
#ifdef HV_CLOUDS
			// (review round 1: every other far step, as the near ones: a cloud's shadow is broad out here)
			if ((i & 1) == 0) {
				cloudVis = welkinCloudShadow(pp, ce);
			}
			vis *= cloudVis;
#endif
			shaft += vis * exp(-t / SHAFT_FALLOFF) * dt;
			float rho = hvDensity(y, g) * (t * t / (t * t + HV_NEAR_CLEAR * HV_NEAR_CLEAR));
			float tr = exp(-tau);
			hzSun += rho * vis * dt * tr;
			hzAll += rho * dt * tr;
			tau += rho * dt * HV_SIGMA_MARCH;
		}
	}
	return vec4(shaftsOn ? shaft / SHAFT_FALLOFF : 0.0, hzSun, hzAll, dist);
}
#endif

// ---- the resolve and the light (composite2; define HV_READ and include lib/sky/luminous.glsl, lib/haze/haze.glsl)
#ifdef HV_READ
#ifndef WU_colortex13
#define WU_colortex13
uniform sampler2D colortex13;
#endif

// The raw terms gathered over the 4x4 half-resolution texels round full-resolution pixel p (each of the noise tile's 16
// offsets once), weighted by how near each texel's surface lies to this pixel's (dist, blocks; 1e4 for the sky).
vec3 hvResolve(ivec2 p, float dist) {
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

// How thick the air is here, against temperate air at strength 100: by the camera's country (its haze kind), Gloam's
// haziness after the hour's thinning, rain and snow; less where the valley mist (mist 0..1) already lies.
float hvDensityMul(HzState st, float mist, vec3 sunW) {
	int k = hzKindIndex(st.kind);
	// 1 temperate, 2 humid_lowland, 3 misty_lakes, 4 forest_damp, 5 dusty, 6 clear_alpine
	vec3 a = vec3(equal(ivec3(k), ivec3(1, 2, 3)));
	vec3 b = vec3(equal(ivec3(k), ivec3(4, 5, 6)));
	float kind = dot(a, vec3(1.0, 1.3, 1.35)) + dot(b, vec3(1.2, 1.25, 0.45));
	float m = kind * mix(0.8, 1.4, clamp(st.haze.x, 0.0, 1.0));
	m *= (1.0 + 1.4 * st.rain) * (1.0 + 0.5 * st.snow * st.rain);
	m *= 1.0 - 0.6 * clamp(mist, 0.0, 1.0);
	m *= st.outdoor * smoothstep(-0.10, 0.04, sunW.y);
	return m * float(HAZE_VOLUME_STRENGTH) * 0.01;
}

// The haze's colour: `air` the plain luminous air in this direction (display), `fsun` the sunlit share of the haze
// along the ray, mu the view's cosine to the sun.
vec3 hvColour(vec3 air, float fsun, float mu, SkyLum f) {
	vec3 A = pow(max(air, 0.0), vec3(2.2));
	float y = skyLumLuma(A);
	// in shadow: the sky's light only, cooler and dimmer
	vec3 shade = A * vec3(0.80, 0.90, 1.06) * 0.50;
#if R2_SKY != 0
	// (review round 2) from mid-afternoon into the golden hours the air warms sooner and more (a warm cream at 16:30
	// to 17:00, rose-peach on a clear morning), and its shaded part keeps a little of that warmth instead of a cool grey
	float warmK = smoothstep(0.0, 0.6, f.warm) * f.up;
	shade = mix(shade, A * vec3(0.96, 0.90, 0.92) * 0.52, 0.5 * warmK);
#else
	float warmK = f.warm * f.up;
#endif
	// in the sun: brighter, and in the sun's own light: the sunlit share of the in-scatter takes the sun's hue (amber
	// through the golden hours, plain at noon), most toward the sun (the forward scatter), less side-on
	float fwd = (0.30 + 0.70 * f.low) * (0.55 * skyLumHg(mu, 0.80) + 0.45 * skyLumHg(mu, 0.40)) * f.up;
	vec3 lit = A * (1.12 + 0.55 * fwd);
#if R2_SKY != 0
	float sunShare = clamp((0.42 + 0.45 * fwd) * warmK, 0.0, 0.85);
#else
	float sunShare = clamp((0.30 + 0.45 * fwd) * warmK, 0.0, 0.8);
#endif
	lit = mix(lit, f.gold * skyLumLuma(lit), sunShare);
	vec3 L = mix(shade, lit, fsun);
	// (at night and in a closed sky the light is the plain air's)
	L = mix(A, L, f.day * mix(0.35, 1.0, f.clear));
	return pow(skyLumCapLin(L), vec3(1.0 / 2.2));
}

// The volumetric haze over `color` (display): raw the resolved terms, dir the view (world, unit), air the plain air
// (after the luminous sky), sky whether nothing is drawn here, mist the valley mist at the camera (0..1).
vec3 hvComposite(vec3 color, vec3 raw, HzState st, vec3 dir, vec3 sunW, vec3 air, bool sky, float mist, SkyLum f) {
#ifdef HAZE_VOLUME
	float dens = hvDensityMul(st, mist, sunW);
	float a = (1.0 - exp(-HV_SIGMA * dens * raw.b)) * (sky ? 0.16 : 1.0);
	if (a <= 0.0) {
		return color;
	}
	float fsun = clamp(raw.g / max(raw.b, 1e-4), 0.0, 1.0);
	return mix(color, hvColour(air, fsun, dot(dir, sunW), f), min(a, HV_MAX_OPACITY));
#else
	return color;
#endif
}
#endif

#endif
