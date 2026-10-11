// VOID Welkin: the near atmosphere. The scene so far, with the air laid over it, or a debug view (DEBUG_VIEW,
// lib/settings.glsl).
//
// Order per pixel:
//   1. water fog (lib/water.glsl), so the far water's bed (LOD) and the near water's bed (chunks) read the same under
//      the same depth of water; while the eye is in water it owns the view and nothing below runs
//   2. overworld, eye in open air: the haze by country (M2, lib/haze/) from the camera out, cut where valley mist
//      begins: the haze behind the mist, then the valley mist (M1, lib/mist.glsl), then the haze in front. The haze
//      fades into the sky table's horizon for its kind, and draws the world's far edge (deferred's FAR_FADE is off
//      while it is on). Cloud pixels (colortex6 < 999, M3) are skipped: the clouds fogged themselves by ring.
//   3. debug views
// Still to come here: the underground air (M5) and the near-water reflections (M6, HIGH).

#define WELKIN_DEPTH_BEHIND
#include "/lib/common.glsl"
#include "/lib/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/water.glsl"
#include "/lib/void.glsl"
#include "/lib/fade.glsl"
#if defined VALLEY_MIST && defined WORLD_OVERWORLD
#define WELKIN_VALLEY_MIST
#include "/lib/mist.glsl"
#endif
#if defined WORLD_OVERWORLD
#include "/lib/sky/table.glsl"
#include "/lib/haze/haze.glsl"
#if HAZE_DEBUG > 0 && HAZE_DEBUG < 4
#include "/lib/haze/haze_debug.glsl"
#endif
#endif

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;
#endif
#if defined WORLD_OVERWORLD && defined CLOUDS
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
#endif
#endif

#if DEBUG_VIEW != 0
#include "/lib/debug.glsl"
#endif
#if SKY_DEBUG == 1
#include "/lib/sky/debug.glsl"
#endif

#ifdef DEBUG_JOIN
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif

#ifndef WELKIN_GBUFFER_SAMPLERS
#define WELKIN_GBUFFER_SAMPLERS
#ifndef WU_colortex1
#define WU_colortex1
uniform sampler2D colortex1;
#endif
#ifndef WU_colortex2
#define WU_colortex2
uniform sampler2D colortex2;
#endif
#endif

// Whether the chunks' land is at `q` (1), the LOD's (0), or neither (-1: the sky, or vanilla's clouds, marked in
// colortex2 by gbuffers_clouds).
int joinSide(ivec2 q) {
	q = clamp(q, ivec2(0), ivec2(viewWidth, viewHeight) - 1);
	Surface s = surfaceAt(q, (vec2(q) + 0.5) / vec2(viewWidth, viewHeight));
	vec4 flags = texelFetch(colortex2, q, 0);
	bool cloud = flags.x < 0.5 && int(flags.y * 255.0 + 0.5) == MAT_CLOUD;
	return s.sky || cloud ? -1 : (s.chunk ? 1 : 0);
}

// A magenta line, two pixels wide, where the chunks' land meets the LOD's.
vec3 joinLine(ivec2 p, vec3 c) {
	int here = joinSide(p);
	if (here < 0) {
		return c;
	}
	for (int i = 0; i < 4; i++) {
		ivec2 o = ivec2(i == 0 ? 2 : (i == 1 ? -2 : 0), i == 2 ? 2 : (i == 3 ? -2 : 0));
		int there = joinSide(p + o);
		if (there >= 0 && there != here) {
			return vec3(1.0, 0.0, 1.0);
		}
	}
	return c;
}
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
#if DEBUG_VIEW == 0
	// 1. water fog
	color.rgb = waterFog(p, texcoord, color.rgb);
#if defined WORLD_OVERWORLD
	if (isEyeInWater == 0) {
		Surface s = surfaceAt(p, texcoord);
		vec3 eyePos = mat3(gbufferModelViewInverse) * s.view;
		bool cloud = false;
#if defined CLOUDS
		cloud = s.sky && texelFetch(colortex6, p, 0).r < 999.0; // M3: a cloud here; it has its own ring fog
#endif
		// 2. haze behind the mist, the valley mist, haze in front. The split is 0 until M1 exposes the slab's entry
		// distance (MistRay.enter): then near mist banks stay crisp and far ones are hazed exactly.
		HzState st = hzState();
		bool towardSun = dot(eyePos, mat3(gbufferModelViewInverse) * sunPosition) > 0.0;
		vec3 air = skyHazeHorizon(st.kind, towardSun);
		vec3 n = s.sky ? vec3(0.0, 1.0, 0.0) : hzScreenNormal(eyePos);
		WelkinHaze h = welkinHaze(st, eyePos, n, s.sky, 0.0, air, gl_FragCoord.xy);
		if (cloud) {
			h.back.a = 0.0;
			h.front.a = 0.0;
		}
		color.rgb = welkinHazeBack(color.rgb, h);
#ifdef WELKIN_VALLEY_MIST
		color.rgb = valleyMist(p, texcoord, color.rgb);
#endif
		color.rgb = welkinHazeFront(color.rgb, h);
#if HAZE_DEBUG > 0 && HAZE_DEBUG < 4
		welkinHazeDebug(st, h, eyePos, s.sky, color.rgb);
#endif
	}
#endif
#else
	color.rgb = debugView(p, texcoord, color.rgb);
#endif
#ifdef DEBUG_JOIN
	color.rgb = joinLine(p, color.rgb);
#endif
#if SKY_DEBUG == 1
	color.rgb = skyDebugOverlay(p, color.rgb); // the sky table, 8x, bottom left
#endif
	outColor = color;
}

#endif
