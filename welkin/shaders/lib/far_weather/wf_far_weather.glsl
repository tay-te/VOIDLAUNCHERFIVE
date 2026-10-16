// VOID Welkin far weather (P5, from the staged module M4): the far air past the near atmosphere's seam, far valley mist
// carrying on from the near mist, and rain curtains under rainy far weather cells. For composite1, after the clouds
// (composite) and before the near atmosphere (composite2), which lays the near haze and mist in front of all of it.
//
//   vec3 welkinFarWeather(ivec2 p, vec2 uv, vec3 scene, inout float km)
//
// `km` is colortex6 (lib/clouds/buffers.glsl): under 999, a cloud's distance in km. composite2 hazes a cloud as land
// at its distance (workstream D), but only up to the seam with WF_FAR_HAZE and LOD, so a cloud past the seam takes the
// far air's piece here, as the land does. A sky or cloud pixel with a curtain in front is finished here: the sky or
// cloud behind gets the near atmosphere's haze (welkinHaze / welkinHazeEx, as composite2 would lay it), the curtain its
// own haze for its distance (the far piece, then the near piece), and km takes the curtain's distance negated, so
// composite2 lays no haze, shafts or aerial perspective on the pixel again (its valley mist still lies in front of it
// against the sky).
//
// The air's colour (the merge onto the lighting, 2026-10-16): composite2 gives its haze the sun's forward scatter
// (lib/air.glsl airMie) and lays what the haze did not on the land as part of its aerial perspective, by the near
// haze's own opacity. So on the land the far air keeps the plain air (the scatter is laid once, by composite2, not
// twice), and everything composite2 does not touch again (a cloud's far piece, a finished curtain and what is behind
// it) takes the air with the scatter, as composite2's haze would have.
//
// Order per pixel: the far air over the land (or a cloud), then the far layers (mist and curtains, the farther first),
// each sunk into the far air for its own distance.

#ifndef WELKIN_WF_FAR_WEATHER
#define WELKIN_WF_FAR_WEATHER

#include "/lib/far_weather/wf_config.glsl"
#include "/lib/far_weather/wf_air.glsl"
#include "/lib/far_weather/wf_mist.glsl"
#include "/lib/far_weather/wf_curtains.glsl"
#include "/lib/air.glsl"

// The W1 rain at camera-relative point q, blended bilinearly between cells (no kilometre blocks in the far air);
// the clouds' fallback field without Gloam's tile.
float wfFarRainAt(vec2 q, CloudEnv e) {
	ivec2 m = e.frameInt + ivec2(floor(e.frameFract + q));
	if (e.w1) {
		float valid;
		vec4 w = wcTile(VOID_TILE_W1, 10, m + e.w1Off, e.w1Centre, valid);
		return w.b * valid;
	}
	return wcFallbackWeather(m, e).b;
}

vec3 wfOver(vec3 base, WfLayer layer) {
	return mix(base, layer.color, layer.alpha);
}

#if WF_DEBUG != 0
#include "/lib/far_weather/wf_debug.glsl"
#endif

vec3 welkinFarWeather(ivec2 p, vec2 uv, vec3 scene, inout float km) {
	if (isEyeInWater != 0) {
		return scene;
	}
	Surface s = surfaceAt(p, uv);
	vec3 eyePos = mat3(gbufferModelViewInverse) * s.view;
	float L = length(eyePos);
	vec3 dir = eyePos / max(L, 1e-4);
	bool cloud = km < 999.0;
	bool land = !s.sky && !cloud;
	float tLand = s.sky ? 1e9 : L;
	float tCloud = cloud ? km * 1000.0 : 1e9;

	HzState st = hzState();
	vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec3 air = skyHazeAir(st.kind, dir, sunW);
	vec3 airM = airMie(air, dir, sunW, st.kind); // with the forward scatter, as composite2's haze
	CloudEnv e = welkinCloudEnv();
	float hk = max(length(dir.xz), 1e-4);
	float t0 = WF_SEAM / hk;

	// the far air over the land, and over a cloud past the seam (composite2's haze stops at the seam)
	WfAir a = wfNoAir();
	if (land) {
		float farRain = L > t0 ? wfFarRainAt(dir.xz * 0.5 * (t0 + L), e) : 0.0;
		a = wfAirSetup(st, dir, L, air, farRain);
		scene = wfAirOver(a, scene, L);
	} else if (cloud && tCloud > t0) {
		WfAir ak = wfAirSetup(st, dir, tCloud, airM, wfFarRainAt(dir.xz * 0.5 * (t0 + tCloud), e));
		scene = wfAirOver(ak, scene, tCloud);
	}

	// far valley mist, on the land past the near tiles' band
	WfLayer mist = wfNoLayer();
#if defined WF_FAR_MIST && defined VALLEY_MIST
	if (land) {
		WfMistTiles mt = wfMistTiles();
		if (mt.on) {
			vec2 r = wfFarMistRay(dir, L, mt);
			mist.od = r.x;
			if (r.x > 0.0) {
				float cover = 1.0 - exp(-r.x * MIST_EXTINCTION * MIST_THICKNESS);
				// at night a thinner veil, as the near mist (lib/mist.glsl), so the two still add up to one mist
				float day = clamp(sunHeight() * 5.0 + 0.6, 0.0, 1.0);
				mist.alpha = MIST_OPACITY * mix(0.5, 1.0, day) * cover;
				mist.color = wfAirOver(a, mistColor(dir, cover), r.y);
				mist.t = r.y;
			}
		}
	}
#endif

	// rain curtains, up to the land and any nearer cloud
	WfLayer rain = wfNoLayer();
	float storm = 0.0;
#ifdef WF_CURTAINS
	rain = wfCurtains(dir, tLand, tCloud, e, storm);
	if (rain.alpha > 0.0) {
		if (land) {
			rain.color = wfAirOver(a, rain.color, rain.t);
		} else {
			// against the sky or a cloud the curtain hazes itself: the far piece from the seam, then the near piece
			// (welkinHaze stops at the seam with WF_FAR_HAZE and LOD, or runs to the curtain without them), in the
			// air with the forward scatter, as composite2 would
			float farRain = rain.t > t0 ? wfFarRainAt(dir.xz * 0.5 * (t0 + rain.t), e) : 0.0;
			WfAir ac = wfAirSetup(st, dir, rain.t, airM, farRain);
			vec3 c = wfAirOver(ac, rain.color, rain.t);
			WelkinHaze hc = welkinHaze(st, dir * rain.t, dir, false, 0.0, airM, gl_FragCoord.xy);
			rain.color = welkinHazeAll(c, hc);
			// what lies behind gets the haze composite2 would have laid on it (a cloud as land at its distance, without
			// the world's edge; the sky as sky), and the pixel is marked done: the curtain's distance, negated
			WelkinHaze hb = cloud
				? welkinHazeEx(st, dir * tCloud, vec3(0.0, 1.0, 0.0), false, 0.0, airM, gl_FragCoord.xy, false)
				: welkinHaze(st, eyePos, vec3(0.0, 1.0, 0.0), true, 0.0, airM, gl_FragCoord.xy);
			scene = welkinHazeAll(scene, hb);
			km = -clamp(rain.t / 1000.0, 0.001, 998.0);
		}
	}
#endif

	// the farther layer first
	if (mist.t >= rain.t) {
		scene = wfOver(wfOver(scene, mist), rain);
	} else {
		scene = wfOver(wfOver(scene, rain), mist);
	}

#if WF_DEBUG != 0
	scene = wfDebug(scene, p, s.sky, cloud, dir, L, e, a, mist, rain);
#endif
	return scene;
}

#endif
