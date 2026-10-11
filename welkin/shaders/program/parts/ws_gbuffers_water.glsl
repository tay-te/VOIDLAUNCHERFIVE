// VOID Welkin: gbuffers_water's body (M6 water_shadows, the water half). Every translucent block the chunks draw:
// water with ripples, the sky's reflection, the sun's glint and its body's opacity from the depth it covers
// (lib/ws/water.glsl); glass, stained glass and ice lit by the pack's ramp with the sky's reflection; slime, honey and
// portals lit only. All of it through the near shadow map (lib/shadow.glsl), so trees shade water and glass.
// Premultiplied into the scene (colortex0); the water marks (colortex3) for the valley mist and the under-water fog;
// the water info (colortex4) for composite2's reflections of terrain (WS_WATER_SSR).

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec3 eyePos;
flat out int material;
flat out int lightClass;
flat out float blockEmit;

#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
in vec4 mc_Entity;
in vec4 at_midBlock;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	glcolor = gl_Color;
	normalW = normalize(mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal));
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
	int id = welkinIdFromAttr(mc_Entity.x);
	material = welkinMat(id);
	lightClass = welkinLight(id);
	blockEmit = welkinEmission(at_midBlock.w);
	gl_Position = ftransform();
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/light.glsl"
#include "/lib/handlight.glsl"
#include "/lib/shadow.glsl"
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

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalW;
in vec3 eyePos;
flat in int material;
flat in int lightClass;
flat in float blockEmit;

#ifndef WU_gtexture
#define WU_gtexture
uniform sampler2D gtexture;
#endif
#ifndef WU_alphaTestRef
#define WU_alphaTestRef
uniform float alphaTestRef;
#endif
#ifndef WU_depthtex1
#define WU_depthtex1
uniform sampler2D depthtex1;
#endif
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

// 0 the scene (premultiplied), 3 the water marks, 4 the water info: blend lines in shaders.properties
/* RENDERTARGETS: 0,3,4 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outWater;
layout(location = 2) out vec4 outInfo;

void main() {
	// texture and derivatives before any branch
	vec4 tex = texture(gtexture, texcoord);
	float footprint = max(length(dFdx(eyePos)), length(dFdy(eyePos)));
	vec3 nW = normalize(normalW);
	float dist = length(eyePos);
	vec2 lm = lmcoord;
	lm.x = welkinHandBlockLight(lm.x, dist);
	vec3 L = lightDirWorld();
	SkyFrame sf = skyFrame();

	if (matIsWater(material)) {
		// the opaque surface behind the water along this pixel's ray (depthtex1 holds no translucents)
		float d1 = texelFetch(depthtex1, ivec2(gl_FragCoord.xy), 0).r;
		float behind = 1.0e4;
		if (d1 < 1.0) {
			vec4 v = gbufferProjectionInverse * vec4(vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), d1) * 2.0 - 1.0, 1.0);
			behind = length(v.xyz / v.w);
		}
		WsIn inp = wsInputs();
		WsSeason sea = wsSeasonNow(inp);
		WsWater w;
		w.playerPos = eyePos;
		w.geoNormal = nW;
		w.lm = lm;
		w.tint = glcolor.rgb;
		w.tex = tex.rgb;
		w.behind = behind;
		w.footprint = footprint;
		w.far = false;
		w.sunShare = vec3(1.0);
		w.reach = welkinSunReach(eyePos, nW, lm.y, material, sunFacing(dot(nW, L)), w.sunShare);
#ifdef WELKIN_WS_CLOUD_SHADOW
		// the decks' shadows, as deferred lays them on the land
		if (lm.y > 0.0) {
			w.sunShare *= welkinCloudShadow(eyePos, welkinCloudEnv());
		}
#endif
		outColor = wsShadeWater(w, inp, sea, sf, outInfo);
		outWater = vec4(outColor.rgb / max(outColor.a, 1e-3), 1.0);
		return;
	}

	vec4 albedo = tex * glcolor;
	if (albedo.a < alphaTestRef) {
		discard;
	}
	float emission = 0.0;
#ifdef EMISSIVE_BLOCKS
	emission = welkinGlow(material, lightClass, albedo.rgb, blockEmit);
#endif
	float facing = sunFacingMat(nW, L, material);
	vec3 sunShare = vec3(1.0);
	float reach = welkinSunReach(eyePos, nW, lm.y, material, facing, sunShare);
	vec3 lit = welkinLightCore(albedo.rgb, nW, lm, emission, sunShare, vec3(1.0), reach, facing);
	outColor = wsShadeGlass(lit, albedo.a, eyePos, nW, lm.y, material, sf, outInfo);
	outWater = vec4(0.0);
}

#endif
