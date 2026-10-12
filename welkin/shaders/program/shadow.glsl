// VOID Welkin: shadow (and shadow_solid, with WELKIN_SHADOW_SOLID). The near shadow map (lib/shadow.glsl): the chunks inside shadowDistance and the entities, seen
// from the sun (or the moon), through the fisheye distortion. Overworld only, and only with SHADOWS on
// (shaders.properties); the LOD never draws here (dhShadow.enabled=false).
//
// Leaves and plants sway exactly as gbuffers_terrain sways them (lib/sway.glsl, the same inputs), so their shadows
// move with them. Opaque and cutout blocks write depth; translucents also write what light they let through into
// shadowcolor0, stored at half: stained glass its colour, water a pale blue-green with alpha 0 (receivers then also
// fade it with the depth the light travels).

#define WELKIN_SHADOW_PASS
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"

#ifdef VERTEX_SHADER

in vec4 mc_Entity;
in vec4 at_midBlock;

out vec2 texcoord;
out vec4 glcolor;
flat out int material;

#ifndef WU_shadowModelView
#define WU_shadowModelView
uniform mat4 shadowModelView;
#endif
#ifndef WU_shadowModelViewInverse
#define WU_shadowModelViewInverse
uniform mat4 shadowModelViewInverse;
#endif

#if defined WAVING_LEAVES || defined WAVING_PLANTS || defined WAVING_WATER_PLANTS
#define WELKIN_SHADOW_SWAY
#include "/lib/void.glsl"
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
	glcolor = gl_Color;
	material = welkinMat(welkinIdFromAttr(mc_Entity.x)); // entities have no mc_Entity: M_GENERIC, no sway
	// Faces turned away from the light are left out where something closed stands behind them: every solid block
	// (shadow_solid) and leaves. What the light meets first of a closed shape is always one of its faces turned to it,
	// so the map is the same, at about half the drawing (most of the pass's time went on leaves' faces). The sun is
	// at +z in shadow view space.
#ifdef WELKIN_SHADOW_SOLID
	bool closed = true;
#else
	bool closed = matIsLeaves(material);
#endif
	if (closed && (gl_NormalMatrix * gl_Normal).z < -0.02) {
		gl_Position = vec4(0.0, 0.0, -2.0, 1.0); // outside the clip volume: nothing is drawn
		return;
	}
	vec4 view = gl_ModelViewMatrix * gl_Vertex; // shadow view space
	// round 3: under the moon only the casters within its shorter reach (lib/shadow.glsl shadowReach), by the block's
	// centre so a block's faces go together (creatures, with no block centre, cast within half the map's reach anyway)
	if (sunAngle >= 0.5) {
		vec3 centre = (shadowModelViewInverse * view).xyz + at_midBlock.xyz / 64.0;
		if (length(centre.xz) > shadowReach() + SHADOW_NIGHT_MARGIN) {
			gl_Position = vec4(0.0, 0.0, -2.0, 1.0); // outside the clip volume: nothing is drawn
			return;
		}
	}
#ifdef WELKIN_SHADOW_SWAY
	// the same offset gbuffers_terrain adds, from the same camera-relative position, wind and clock
	vec3 playerPos = (shadowModelViewInverse * view).xyz;
	// past 48 blocks a sway is far less than a map texel: it fades out from 32 and is not worked out at all past 48
	float swayKeep = 1.0 - smoothstep(32.0, 48.0, length(playerPos));
	if (swayKeep > 0.0) {
		vec4 wind = (void_bridge == 1 && BRIDGE_MODE == 0) ? void_wind : welkinWindFallback(rainStrength);
		vec3 worldPos = vec3(cameraPositionInt & 1023) + cameraPositionFract + playerPos;
		playerPos += welkinSway(material, worldPos, at_midBlock.xyz, frameTimeCounter, wind) * swayKeep;
		view = shadowModelView * vec4(playerPos, 1.0);
	}
#endif
	// Low plants (grass, flowers, crops, ground cover) cast only near the eye, where the map's texels are a small
	// share of a block; farther out their shadows would be blobs a block wide that dim a whole meadow. They shrink
	// smoothly to their foot between 12 and 28 blocks, so no shadow pops. Tall plants, vines and leaves always cast.
	if (material == M_PLANT || material == M_CROP || material == M_GROUND_COVER) {
		vec3 pp = (shadowModelViewInverse * view).xyz;
		float keep = 1.0 - smoothstep(12.0, 28.0, length(pp));
		if (keep <= 0.0) {
			gl_Position = vec4(0.0, 0.0, -2.0, 1.0); // outside the clip volume: nothing is drawn
			return;
		}
		vec3 foot = pp + at_midBlock.xyz / 64.0 - vec3(0.0, 0.5, 0.0);
		view = shadowModelView * vec4(mix(foot, pp, keep), 1.0);
	}
	gl_Position = gl_ProjectionMatrix * view;
	gl_Position.xyz = shadowDistort(gl_Position.xyz);
}

#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;
in vec4 glcolor;
flat in int material;

#ifndef WU_gtexture
#define WU_gtexture
uniform sampler2D gtexture;
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outShadow;

void main() {
#ifdef WELKIN_SHADOW_SOLID
	// solid blocks: no texture, no alpha test (and so no discard: the depth test runs early)
	outShadow = vec4(0.5, 0.5, 0.5, 1.0);
#else
	if (matIsWater(material)) {
		outShadow = vec4(vec3(0.70, 0.86, 0.88) * 0.5, 0.0);
		return;
	}
	vec4 c = texture(gtexture, texcoord) * glcolor;
	if (c.a < 0.1) {
		discard;
	}
	// stained glass and other translucents: their colour, as strong as they are opaque; opaque: neutral (decodes to 1)
	outShadow = c.a < 0.95 ? vec4(mix(vec3(1.0), c.rgb, c.a) * 0.5, 1.0) : vec4(0.5, 0.5, 0.5, 1.0);
#endif
}

#endif
