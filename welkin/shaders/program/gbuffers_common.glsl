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
//   G_SHADOW     take the near shadow map's sun (lib/shadow.glsl) when lighting itself (entities, block entities,
//                translucents); the other self-lit programs use the ramp's sky-light gate
// colortex2 = (lit, M / 255, emission and L packed (packEmitClass), corner shading); materials in lib/materials.glsl.
// Terrain's vertex colour carries Minecraft's corner shading in its alpha (separateAo, shaders.properties): G_BLOCKID
// programs take it out of the albedo and hand it to the light ramp, which lays it on the sky's fill only.
//   G_WATERFLAG  also write colortex3: the water's own lit colour where the pixel is water, for composite's water fog
//   G_WEATHER    rain and snow: the texture's shape only, as a thin grey-white veil in the sky's light (gbuffers_weather)
//
// Without G_DEFERRED the program lights itself with the same light ramp deferred uses (lib/light.glsl).

#include "/lib/common.glsl"
#include "/lib/tone.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec3 eyePos;
flat out int material;
flat out int lightClass;
flat out float blockEmit;
#ifdef G_BLOCKID
out float plantH; // workstream C: how high up its plant a vertex stands, 0 root .. 1 tip
#endif

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
#if defined G_TEXTURED && !defined G_BLOCKID && !defined G_DEFERRED
	// particles carry no normal (the attribute reads as zero, and normalising it gave NaN light): a sprite turned to
	// the camera is lit as a level surface, the way the land round it is, never brighter for facing the sun or moon
	normalW = dot(gl_Normal, gl_Normal) > 0.01 ? normalize(mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal))
		: vec3(0.0, 1.0, 0.0);
#else
	normalW = normalize(mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal));
#endif
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
	// workstream C: the vertex's height in its block (at_midBlock: to the block's centre, 1/64 blocks); a two-block
	// plant's halves make one stalk
	plantH = clamp(0.5 - at_midBlock.y / 64.0, 0.0, 1.0);
	plantH = material == M_PLANT_TALL_LOWER ? 0.5 * plantH : (material == M_PLANT_TALL_UPPER ? 0.5 + 0.5 * plantH : plantH);
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
#ifdef G_BLOCKID
in float plantH;
#endif

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
#ifdef G_SHADOW
#include "/lib/shadow.glsl"
#endif
#ifdef G_WATERFLAG
#include "/lib/watersurf.glsl"
#ifndef WU_isEyeInWater
#define WU_isEyeInWater
uniform int isEyeInWater;
#endif
#endif
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
	float ao = 1.0;
#ifdef G_BLOCKID
	// separateAo: the corner shading is the vertex alpha, not part of the colour (nor of the alpha test)
	ao = glcolor.a;
	albedo.a = 1.0;
#if PLANT_ROOT_SHADE > 0
	// workstream C: a tuft or a stalk darkens toward its root (the sky's light is hidden down among the blades), from
	// PLANT_ROOT_SHADE % darker at the ground to none by 70 % of its height; it rides on Minecraft's corner shading, which
	// the light pass lays on a plant's fill and torch light whole (program/deferred.glsl)
	if (matHas(material, MATF_SSS) && !matIsLeaves(material)) {
		ao *= mix(1.0 - 0.01 * float(PLANT_ROOT_SHADE), 1.0, smoothstep(0.0, 0.7, plantH));
	}
#endif
#endif
#ifdef G_TEXTURED
	albedo *= texture(gtexture, texcoord);
#endif
	if (albedo.a < alphaTestRef) {
		discard;
	}
#ifdef G_NEARCUT
	// (the defects pass, 2026-10-16) a particle at the eye (a fall's spray drifting through it) is cut by the near plane
	// into a dark triangle over half the screen: none closer than a third of a block
	if (dot(eyePos, eyePos) < 0.11) {
		discard;
	}
#endif
#if defined G_WATERFLAG && !defined G_DEFERRED && !defined G_UNLIT
	// Minecraft draws water's top twice, once turned down for the view from under it. On a slope or a fall the quad's
	// corners are not in one plane, and the reversed copy splits it along the other diagonal, so seen from above half
	// of each step's copy stands over the surface and half under: every step showed a darker (double) and a lighter
	// triangle. From the air, a turned-away water face below the eye is that copy: it is left out.
	if (matIsWater(material) && !gl_FrontFacing && isEyeInWater != 1 && eyePos.y < 0.0) {
		discard;
	}
#endif
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
	outFlags = vec4(1.0, float(material) / 255.0, packEmitClass(emission, lightClass), ao);
#else
#ifdef G_UNLIT
	// (HDR stage A) a light of its own (the beacon's beam, a spider's eyes) in scene-linear light, a little past white;
	// the glint and the cracks blend onto what is there (vanilla's blending), so they stay factors
#if defined G_UNLIT_LIGHT
	vec3 lit = toLinear(albedo.rgb) * LAND_EMIT;
#else
	vec3 lit = albedo.rgb;
#endif
#else
	float d = 0.5; // no dither: the ramp is smooth
	vec2 lm = lmcoord;
	lm.x = welkinHandBlockLight(lm.x, length(eyePos));
#ifdef G_SHADOW
	vec3 nW = normalize(normalW);
#ifdef G_WATERFLAG
	vec3 nFace = nW;
	if (matIsWater(material)) {
		// water is lit as level water (lib/watersurf.glsl welkinWaterNormal: a fall's facets and a slope's tilted
		// quads would each take the sun differently), with no corner shading (Minecraft's own water has none)
		nW = welkinWaterNormal(nW);
		ao = 1.0;
	}
#endif
	float facing = sunFacingMat(nW, lightDirWorld(), material);
	vec3 sunShare = vec3(1.0);
	float reach = welkinSunReach(eyePos, nW, lm.y, material, facing, sunShare);
	vec3 lit = welkinLightFullLin(albedo.rgb, nW, lm, emission, sunShare, vec3(1.0), reach, facing, ao); // (HDR: linear)
#ifdef G_WATERFLAG
	vec3 waterDeep = vec3(0.0);
	if (matIsWater(material)) {
		WaterSurface ws = welkinWaterSurface(albedo, nFace, eyePos, lm, sunShare, reach, facing);
		lit = hdrBridgeToScene(ws.color); // (HDR stage A: bridged, unused path)
		albedo.a = ws.alpha;
		waterDeep = ws.deep;
	}
#endif
#else
	vec3 lit = welkinLightLin(albedo.rgb, normalize(normalW), lm, emission); // (HDR: linear)
#endif
#endif
#ifdef G_WEATHER
	{
		// rain: vanilla's streaks are saturated blue and near opaque. Here they are the texture's shape only, a pale
		// grey-white in the light that falls on them (the sky's fill, a little of the sun or moon, a torch nearby),
		// half as opaque; snow keeps its white. The air's own haze still lies over them in composite2.
		float lum = dot(albedo.rgb, vec3(0.2126, 0.7152, 0.0722));
		float blue = clamp((albedo.b - max(albedo.r, albedo.g)) * 3.0, 0.0, 1.0); // rain's texture is blue, snow's white
		vec3 veil = welkinLightLin(vec3(0.82, 0.85, 0.90), vec3(0.0, 1.0, 0.0), lm, 0.0); // (HDR: linear)
		lit = mix(lit, veil * mix(1.0, 0.8 + 0.4 * lum, 0.5), blue);
		albedo.a *= mix(1.0, 0.42, blue);
	}
#endif
	outColor = vec4(lit, albedo.a);
#ifdef G_FLAG
	outFlags = vec4(0.0, 0.0, 0.0, 0.0);
#endif
#ifdef G_WATERFLAG
	outWater = matIsWater(material) ? vec4(waterDeep, 1.0) : vec4(0.0);
#endif
#endif
}

#endif
