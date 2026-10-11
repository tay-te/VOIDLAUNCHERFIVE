// VOID Welkin: dh_water's body (M6 water_shadows, the water half). The far water, drawn by VOID LOD after the world's
// translucents and before composite, shaded as the near water is (lib/ws/water.glsl) so the two meet without a seam:
// the same waves (most too small to draw out here, so they widen the sun's glitter path instead), the same sky in
// the reflection, the same light ramp with the sky-light gate in place of the shadow map (the LOD never casts or takes
// map shadows). Its opacity comes from the LOD's own bed (dhDepthTex1), one fetch.
//
// Kept light: LOD's auto-tune counts the pack's time against its radius. No texture; two depth fetches.
// Cut inside clipDistance and where the chunks' own water already stands (M0's rule), premultiplied (blend in
// shaders.properties), the water marks in colortex3 (alpha: how much the far fade leaves), the info in colortex4.

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec4 glcolor;
out vec2 lmcoord;
out vec3 eyePos;
flat out vec3 normalW;
flat out int isWater;

#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif

void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	normalW = gl_Normal; // Distant Horizons normals are world axes already
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
	isWater = dhMaterialId == DH_BLOCK_WATER ? 1 : 0;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/fade.glsl"
#include "/lib/depth.glsl"
#include "/lib/sky/dome.glsl"
#include "/lib/void.glsl"
#include "/lib/sway.glsl"
#include "/lib/ws/config.glsl"
#include "/lib/ws/inputs.glsl"
#include "/lib/ws/season.glsl"
#include "/lib/ws/ripples.glsl"
#include "/lib/ws/water.glsl"
// M3: the clouds' shadows on the water, as on the land
#if defined WORLD_OVERWORLD && defined CLOUDS && CLOUD_SHADOWS > 0
#include "/lib/clouds/shadow.glsl"
#define WELKIN_WS_CLOUD_SHADOW
#endif

in vec4 glcolor;
in vec2 lmcoord;
in vec3 eyePos;
flat in vec3 normalW;
flat in int isWater;

#ifndef WU_clipDistance
#define WU_clipDistance
uniform float clipDistance;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
#ifdef DISTANT_HORIZONS
#ifndef WU_dhDepthTex1
#define WU_dhDepthTex1
uniform sampler2D dhDepthTex1;
#endif
#endif

// 0 the scene (premultiplied), 3 the water marks, 4 the water info: blend lines in shaders.properties
/* RENDERTARGETS: 0,3,4 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outWater;
layout(location = 2) out vec4 outInfo;

void main() {
	float footprint = max(length(dFdx(eyePos)), length(dFdy(eyePos)));
	float horizontal = length(eyePos.xz);
	if (horizontal < clipDistance) {
		discard;
	}
	vec2 uv = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
	float dist = length(eyePos);
	// where the chunks' water (or anything of theirs) already stands here, at or in front of this, it is the same
	// water: drawing both would double its colour along the join
	float chunkDepth = texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r;
	if (chunkDepth < 1.0) {
		vec3 chunkView = viewFromDepth(uv, chunkDepth, gbufferProjectionInverse);
		if (length(chunkView) < dist + 2.0) {
			discard;
		}
	}
	vec3 n = normalize(normalW);
	vec4 c;
	if (isWater == 1) {
		// the LOD's bed behind this water, along the ray
		float behind = -1.0;
#ifdef DISTANT_HORIZONS
		float d1 = texelFetch(dhDepthTex1, ivec2(gl_FragCoord.xy), 0).r;
		behind = d1 < 1.0 ? length(viewFromDepth(uv, d1, dhProjectionInverse)) : 1.0e5;
#endif
		WsIn inp = wsInputs();
		WsSeason sea = wsSeasonNow(inp);
		WsWater w;
		w.playerPos = eyePos;
		w.geoNormal = n;
		w.lm = lmcoord;
		w.tint = glcolor.rgb;
		w.tex = vec3(0.75);
		w.behind = behind;
		w.footprint = footprint;
		w.far = true;
		w.reach = sunOpen(lmcoord.y);
		w.sunShare = vec3(1.0);
#ifdef WELKIN_WS_CLOUD_SHADOW
		// the decks' shadows, as deferred lays them on the LOD's land
		if (lmcoord.y > 0.0) {
			w.sunShare *= welkinCloudShadow(eyePos, welkinCloudEnv());
		}
#endif
		c = wsShadeWater(w, inp, sea, skyFrame(), outInfo);
	} else {
		// the LOD's other translucents: lit, no reflection
		c = vec4(welkinLight(glcolor.rgb, n, lmcoord, 0.0, 0.5) * glcolor.a, glcolor.a);
		outInfo = vec4(0.5, 0.5, 0.0, WS_KIND_OTHER);
	}
	// the water's own colour for the water marks, less of it where the far fade has taken over
	outWater = isWater == 1 ? vec4(c.rgb / max(c.a, 1e-3), 1.0) : vec4(0.0);
#if defined FAR_FADE && !(defined HAZE_ENABLED && defined WORLD_OVERWORLD)
	// with the haze on, composite2's haze draws the world's edge (as deferred does for the land)
	float fade = farFade(horizontal, 0.5);
	outWater.a *= 1.0 - fade;
	c = mix(c, vec4(horizonColor(), 1.0), fade);
#endif
	outColor = c;
}

#endif
