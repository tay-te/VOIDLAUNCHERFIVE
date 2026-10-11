// VOID Welkin: the one gbuffers program, specialised by defines in each program/gbuffers_*.glsl:
//
//   G_TEXTURED   sample the bound texture (gtexture)
//   G_DEFERRED   write albedo, normal, light map and material for deferred to light (terrain)
//   G_FLAG       light here, and write colortex2.x = 0 so deferred leaves the pixel alone (opaque-phase forward)
//   G_UNLIT      no lighting at all: the texture's own colour (spider eyes, glint, beacon, cracks)
//   G_ENTITY     apply entityColor (the hurt flash)
//   G_BLOCKID    read mc_Entity for the block.properties material
//   G_EMISSIVE   the whole program glows (emission 1)
//   G_BLOCKENTITY  read blockEntityId for the material (block entities)
//   G_ITEMID     read currentRenderedItemId for the material (the hand and held items)
//   G_ENTITYID   read entityId: glowing mobs and effects glow (entities)
//   G_SWAY       move leaves and plants with the wind (lib/sway.glsl; gbuffers_terrain)
// colortex2 = (lit, M / 255, emission, L / 15); materials in lib/materials.glsl.
//   G_WATERFLAG  also write colortex3: the water's own lit colour where the pixel is water, for composite's water fog
//
// Without G_DEFERRED the program lights itself with the same light ramp deferred uses (lib/light.glsl).

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
#ifdef G_BLOCKID
in vec4 mc_Entity;
in vec4 at_midBlock;
#endif
#ifdef G_BLOCKENTITY
#ifndef WU_blockEntityId
#define WU_blockEntityId
uniform int blockEntityId;
#endif
#endif
#ifdef G_ITEMID
#ifndef WU_currentRenderedItemId
#define WU_currentRenderedItemId
uniform int currentRenderedItemId;
#endif
#endif
#ifdef G_SWAY
#include "/lib/void.glsl"
#ifndef WELKIN_DECL_MODELVIEW
#define WELKIN_DECL_MODELVIEW
#ifndef WU_gbufferModelView
#define WU_gbufferModelView
uniform mat4 gbufferModelView;
#endif
#endif
#ifndef WELKIN_DECL_CAMERA_INT
#define WELKIN_DECL_CAMERA_INT
#ifndef WU_cameraPositionInt
#define WU_cameraPositionInt
uniform ivec3 cameraPositionInt;
#endif
#ifndef WU_cameraPositionFract
#define WU_cameraPositionFract
uniform vec3 cameraPositionFract;
#endif
#endif
#ifndef WELKIN_DECL_FRAMETIME
#define WELKIN_DECL_FRAMETIME
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;
#endif
#endif
#ifndef WELKIN_DECL_RAIN
#define WELKIN_DECL_RAIN
#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#endif
#ifndef VOID_U_WIND
#define VOID_U_WIND
#ifndef WU_void_wind
#define WU_void_wind
uniform vec4 void_wind;
#endif
#endif
#include "/lib/sway.glsl"
#endif

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	glcolor = gl_Color;
	normalW = normalize(mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal));
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
	int id = -1;
#if defined G_BLOCKID
	id = welkinIdFromAttr(mc_Entity.x);
#elif defined G_BLOCKENTITY
	id = blockEntityId;
#elif defined G_ITEMID
	id = currentRenderedItemId;
#endif
	material = welkinMat(id);
	lightClass = welkinLight(id);
#ifdef G_BLOCKID
	blockEmit = welkinEmission(at_midBlock.w);
#else
	blockEmit = lightClass != L_NONE ? 1.0 : 0.0; // no at_midBlock: a light-class item or block entity glows
#endif
#ifdef G_SWAY
	// wind from Gloam when its uniforms are live, else a breeze that freshens with the rain
	vec4 wind = (void_bridge == 1 && BRIDGE_MODE == 0) ? void_wind : welkinWindFallback(rainStrength);
	// world position wrapped to 1024 blocks, so the sway phase stays precise far from the origin
	vec3 worldPos = vec3(cameraPositionInt & 1023) + cameraPositionFract + eyePos;
	eyePos += welkinSway(material, worldPos, at_midBlock.xyz, frameTimeCounter, wind);
	gl_Position = gl_ProjectionMatrix * (gbufferModelView * vec4(eyePos, 1.0));
#else
	gl_Position = ftransform();
#endif
}

#endif

#ifdef FRAGMENT_SHADER

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
#ifdef G_ENTITY
#ifndef WU_entityColor
#define WU_entityColor
uniform vec4 entityColor;
#endif
#endif
#ifdef G_ENTITYID
#ifndef WU_entityId
#define WU_entityId
uniform int entityId;
#endif
#endif

#if !defined G_DEFERRED && !defined G_UNLIT
#include "/lib/light.glsl"
#include "/lib/handlight.glsl"
#endif

#if defined G_DEFERRED
/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outFlags;
#elif defined G_FLAG
/* RENDERTARGETS: 0,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outFlags;
#elif defined G_WATERFLAG
/* RENDERTARGETS: 0,3 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outWater;
#else
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
#endif

void main() {
	vec4 albedo = glcolor;
#ifdef G_TEXTURED
	albedo *= texture(gtexture, texcoord);
#endif
	if (albedo.a < alphaTestRef) {
		discard;
	}
#ifdef G_ENTITY
	albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif

	float emission = 0.0;
#ifdef G_EMISSIVE
	emission = 1.0;
#endif
#ifdef EMISSIVE_BLOCKS
	emission = max(emission, welkinGlow(material, lightClass, albedo.rgb, blockEmit));
#ifdef G_ENTITYID
	int e = welkinEntity(entityId);
	if (e == E_GLOWING || e == E_EMISSIVE_FX) {
		emission = max(emission, step(0.72, max(albedo.r, max(albedo.g, albedo.b))) * 0.85);
	}
#endif
#endif

#if defined G_DEFERRED
	outColor = vec4(albedo.rgb, 1.0);
	outNormal = vec4(octEncode(normalize(normalW)), lmcoord);
	outFlags = vec4(1.0, float(material) / 255.0, emission, float(lightClass) / 15.0);
#else
#ifdef G_UNLIT
	vec3 lit = albedo.rgb;
#else
	float d = 0.5; // no dither: the ramp is smooth
	vec2 lm = lmcoord;
	lm.x = welkinHandBlockLight(lm.x, length(eyePos));
	vec3 lit = welkinLight(albedo.rgb, normalize(normalW), lm, emission, d);
#endif
	outColor = vec4(lit, albedo.a);
#ifdef G_FLAG
	outFlags = vec4(0.0, 0.0, 0.0, 0.0);
#endif
#ifdef G_WATERFLAG
	outWater = material == MAT_WATER ? vec4(lit, 1.0) : vec4(0.0);
#endif
#endif
}

#endif
