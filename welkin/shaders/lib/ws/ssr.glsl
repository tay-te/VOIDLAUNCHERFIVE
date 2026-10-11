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

vec3 wsWaterSSR(vec3 col, ivec2 px) {
	vec4 info = texelFetch(colortex4, px, 0);
	if (abs(info.a - WS_KIND_WATER) > 0.05 || info.b < 0.02) {
		return col;
	}
	vec2 res = vec2(viewWidth, viewHeight);
	vec2 uv = (vec2(px) + 0.5) / res;
	vec3 vp = viewFromDepth(uv, texelFetch(depthtex0, px, 0).r, gbufferProjectionInverse);
	float dist = length(vp);
	if (dist > WS_SSR_MAX_DIST) {
		return col;
	}
	vec3 nWorld = octDecode(info.rg);
	vec3 rw = reflect(mat3(gbufferModelViewInverse) * (vp / dist), nWorld);
	vec3 rd = mat3(gbufferModelView) * rw;
	if (rd.z > 0.25) {
		return col; // rays back toward the camera find nothing on screen
	}

	float stepLen = 0.5;
	vec3 p = vp;
	vec3 prev = vp;
	vec3 hit = vec3(-1.0);
	float travelled = 0.0;
	for (int i = 0; i < 12; i++) {
		prev = p;
		p += rd * stepLen;
		travelled += stepLen;
		vec3 s = wsScreenFromView(p);
		if (s.x <= 0.0 || s.y <= 0.0 || s.x >= 1.0 || s.y >= 1.0 || s.z >= 1.0) {
			break;
		}
		float d = texelFetch(depthtex1, ivec2(s.xy * res), 0).r;
		float sceneZ = viewFromDepth(s.xy, d, gbufferProjectionInverse).z;
		float diff = sceneZ - p.z; // > 0: the ray went behind the scene
		if (d < 1.0 && diff > 0.0 && diff < stepLen * 2.0 + 0.5) {
			vec3 lo = prev;
			vec3 hi = p;
			for (int j = 0; j < 4; j++) {
				vec3 mid = (lo + hi) * 0.5;
				vec3 ms = wsScreenFromView(mid);
				float mz = viewFromDepth(ms.xy, texelFetch(depthtex1, ivec2(ms.xy * res), 0).r, gbufferProjectionInverse).z;
				if (mz - mid.z > 0.0) {
					hi = mid;
				} else {
					lo = mid;
				}
			}
			hit = wsScreenFromView(hi);
			break;
		}
		stepLen *= 1.4;
	}
	if (hit.x < 0.0) {
		return col;
	}
	vec2 e = min(hit.xy, 1.0 - hit.xy);
	float fade = smoothstep(0.0, 0.08, min(e.x, e.y)) * (1.0 - smoothstep(0.6, 1.0, travelled / 70.0));
	vec3 hitCol = texelFetch(colortex0, ivec2(hit.xy * res), 0).rgb;
	rw.y = abs(rw.y); // the same sky gbuffers_water added
	vec3 sky = skyDomeOnly(rw, skyFrame());
	return col + info.b * (hitCol - sky) * fade;
}

#endif
