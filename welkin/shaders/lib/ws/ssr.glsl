// VOID Welkin water: reflections of terrain in near water (WS_WATER_SSR, on HIGH), for composite2, before the air.
// gbuffers_water added the sky's reflection with weight info.b (colortex4); where a short screen-space ray from the
// water finds opaque land or trees, this swaps that share of sky for the land's pixel. Near water only (info.a is the
// near-water kind, inside WS_SSR_MAX_DIST); never on far water or the LOD.
//
// Bounded and steady: 12 steps growing 1.4x from 0.5 blocks (about 70 blocks of reach), 4 halving steps to refine, a
// fixed start (no jitter, so nothing crawls without TAA), and a smooth fade toward the screen's edges and the ray's
// end, so a reflection never cuts off in a hard line.
//
// Needs colortex0 (the scene) and colortex4 (the water info) declared, lib/depth.glsl with WELKIN_DEPTH_BEHIND
// (depthtex0, depthtex1), lib/sky/dome.glsl, and the gbufferProjection, gbufferModelView and view size uniforms.

#ifndef WELKIN_WS_SSR
#define WELKIN_WS_SSR

vec3 wsScreenFromView(vec3 v) {
	vec4 c = gbufferProjection * vec4(v, 1.0);
	return (c.xyz / c.w) * 0.5 + 0.5;
}

// The opaque scene's view-space depth (z) at a screen position, the LOD's land included (it stands past the chunks).
float wsSceneZ(vec2 suv, vec2 res) {
	ivec2 q = clamp(ivec2(suv * res), ivec2(0), ivec2(res) - 1);
	Surface b = surfaceBehind(q, suv);
	return b.sky ? -1e7 : b.view.z;
}

// (the defects pass, 2026-10-16: on MEDIUM too, and far: 70 blocks of reach found the bank at one's feet but never
// the far shore or the trees across a lake, and the LOD's land and water were not looked at at all. Now 18 steps
// growing 1.45x reach about 1.5 km, the LOD's land is met as the chunks' is, and the LOD's water reflects as well.)
vec3 wsWaterSSR(vec3 col, ivec2 px) {
	vec4 info = texelFetch(colortex4, px, 0);
	bool nearWater = info.a > 0.74; // (water2: 0.75 to 1, the near water's opacity)
	bool farWater = abs(info.a - WS_KIND_FAR_WATER) < 0.05;
	if (!(nearWater || farWater) || info.b < 0.02) {
		return col;
	}
	vec2 res = vec2(viewWidth, viewHeight);
	vec2 uv = (vec2(px) + 0.5) / res;
	Surface s0 = surfaceAt(px, uv);
	if (s0.sky) {
		return col;
	}
	vec3 vp = s0.view;
	float dist = length(vp);
	if (dist > WS_SSR_MAX_DIST) {
		return col;
	}
	vec3 nWorld = octDecode(info.rg);
	// far off, the waves' tilt is mostly too fine to show in a reflection: it is calmed, else rays tilted up by the
	// ripples found the far shore far below its mirror image, as stray dashes of sand across the open water
	float calm = mix(1.0, 0.3, smoothstep(24.0, 160.0, dist));
	nWorld = normalize(vec3(nWorld.x * calm, nWorld.y, nWorld.z * calm));
	vec3 rw = reflect(mat3(gbufferModelViewInverse) * (vp / dist), nWorld);
	vec3 rd = mat3(gbufferModelView) * rw;
	if (rd.z > 0.25) {
		return col; // rays back toward the camera find nothing on screen
	}

	// the first step scales with the distance, so far water does not crawl along its own surface
	float stepLen = max(0.5, dist * 0.02);
	vec3 p = vp;
	vec3 prev = vp;
	vec3 hit = vec3(-1.0);
	float travelled = 0.0;
	for (int i = 0; i < WS_SSR_STEPS; i++) {
		prev = p;
		p += rd * stepLen;
		travelled += stepLen;
		vec3 sp = wsScreenFromView(p);
		if (sp.x <= 0.0 || sp.y <= 0.0 || sp.x >= 1.0 || sp.y >= 1.0 || p.z >= -0.05) {
			break;
		}
		float diff = wsSceneZ(sp.xy, res) - p.z; // > 0: the ray went behind the scene
		if (diff > 0.0 && diff < stepLen * 2.0 + 0.5 + 0.01 * travelled) {
			vec3 lo = prev;
			vec3 hi = p;
			for (int j = 0; j < 5; j++) {
				vec3 mid = (lo + hi) * 0.5;
				vec3 ms = wsScreenFromView(mid);
				if (wsSceneZ(ms.xy, res) - mid.z > 0.0) {
					hi = mid;
				} else {
					lo = mid;
				}
			}
			// a hit must be on the scene (not a step past an edge onto the land far behind) and above the water
			// (nothing under the surface is mirrored)
			vec3 hs = wsScreenFromView(hi);
			float err = abs(wsSceneZ(hs.xy, res) - hi.z);
			vec3 hw = mat3(gbufferModelViewInverse) * (hi - vp);
			if (err < 0.6 + 0.015 * length(hi) && hw.y > -0.3) {
				hit = hs;
			}
			break;
		}
		stepLen *= 1.45;
	}
	if (hit.x < 0.0) {
		// (water2, 2026-10-18) no land met: the ray goes on to the sky. Where its direction lands on the screen's own
		// sky or clouds, the water takes that pixel (the clouds, lit pink at dawn and silver under the moon, and the
		// sky's own gradient) instead of the bare sky table, fading to the table toward the screen's edges. The sun's
		// own disc is left to the water's glint.
		// (the sky takes the waves a little calmer, so the clouds' crisp outlines break into soft streaks, not shards)
		vec3 nS = normalize(vec3(nWorld.x * 0.6, nWorld.y, nWorld.z * 0.6));
		vec3 rwC = reflect(mat3(gbufferModelViewInverse) * (vp / dist), nS);
		vec4 ci = gbufferProjection * vec4(mat3(gbufferModelView) * rwC, 0.0);
		if (ci.w <= 1e-4) {
			return col;
		}
		vec2 su = (ci.xy / ci.w) * 0.5 + 0.5;
		vec2 eS = min(su, 1.0 - su);
		float fS = smoothstep(0.0, 0.14, min(eS.x, eS.y));
		if (fS <= 0.0) {
			return col;
		}
		ivec2 sq = clamp(ivec2(su * res), ivec2(0), ivec2(res) - 1);
		bool skyPx = surfaceAt(sq, su).sky;
		float ck = texelFetch(colortex6, sq, 0).r;
		if (!skyPx && !(ck > 0.0 && ck < 999.0)) {
			return col;
		}
		vec3 sunW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
		fS *= 1.0 - smoothstep(0.9965, 0.9992, dot(rwC, sunW));
		vec3 rwS = rw;
		rwS.y = abs(rwS.y);
		vec3 skyS = skyDomeOnly(rwS, skyFrame());
		// a short streak down the screen, as a reflection on a rippled surface stretches: five taps, any that leave the
		// sky (a far shore's edge) take the centre's
		vec3 pixS = hdrBridgeToDisplay(texelFetch(colortex0, sq, 0).rgb); // (HDR stage A: bridged)
		vec3 accS = pixS * 0.32;
		float sp = max(2.0, res.y * 0.0045);
		for (int k = -2; k <= 2; k++) {
			if (k == 0) {
				continue;
			}
			ivec2 tq = clamp(sq + ivec2(0, int(float(k) * sp)), ivec2(0), ivec2(res) - 1);
			vec2 tu = (vec2(tq) + 0.5) / res;
			float tk = texelFetch(colortex6, tq, 0).r;
			bool ok = surfaceAt(tq, tu).sky || (tk > 0.0 && tk < 999.0);
			accS += (ok ? hdrBridgeToDisplay(texelFetch(colortex0, tq, 0).rgb) : pixS) * (abs(k) == 1 ? 0.22 : 0.12);
		}
		pixS = accS;
		return col + info.b * (pixS - skyS) * fS * WS_SSR_SKY_SHARE;
	}
	vec2 e = min(hit.xy, 1.0 - hit.xy);
	float fade = smoothstep(0.0, 0.06, min(e.x, e.y)) * (1.0 - smoothstep(0.7, 1.0, travelled / WS_SSR_REACH));
	vec3 hitCol = hdrBridgeToDisplay(texelFetch(colortex0, clamp(ivec2(hit.xy * res), ivec2(0), ivec2(res) - 1), 0).rgb);
	rw.y = abs(rw.y); // the same sky the water added
	vec3 sky = skyDomeOnly(rw, skyFrame());
	return col + info.b * (hitCol - sky) * fade;
}

#endif
