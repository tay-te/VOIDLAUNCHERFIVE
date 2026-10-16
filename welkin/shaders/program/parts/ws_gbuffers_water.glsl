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
#ifndef WU_isEyeInWater
#define WU_isEyeInWater
uniform int isEyeInWater;
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
	// (the defects pass, 2026-10-16) a flowing-water quad folded to a triangle (two corners meeting) arrives with no
	// normal, and lit as NaN it drew a black triangle (the eye in a fall saw one): it takes the face's own from the
	// screen derivatives, turned toward the eye
	vec3 nW = normalW;
	vec3 nFace = cross(dFdx(eyePos), dFdy(eyePos));
	nFace = dot(nFace, nFace) > 1e-20 ? normalize(nFace) : vec3(0.0, 1.0, 0.0);
	nFace = dot(nFace, eyePos) > 0.0 ? -nFace : nFace;
	nW = dot(nW, nW) > 0.25 && !any(isnan(nW)) ? normalize(nW) : nFace;
	float dist = length(eyePos);
	vec2 lm = lmcoord;
	lm.x = welkinHandBlockLight(lm.x, dist);
	vec3 L = lightDirWorld();
	SkyFrame sf = skyFrame();

	if (matIsWater(material)) {
		// (the lighting's fix, merged 2026-10-16) Minecraft draws water's top twice, once turned down for the view
		// from under it. On a slope or a fall the reversed copy splits the quad along the other diagonal, so seen from
		// above half of each step's copy stands over the surface: from the air, a turned-away water face is that copy
		// (below the eye), or a top seen from under its plane (a fall's step above the eye, whose turned-down copy
		// faces the eye), and it is left out; else a fall seen from below showed each step's top as the dark view from
		// under water beside its copy's bright sky, in triangles
		if (!gl_FrontFacing && isEyeInWater != 1) {
			discard;
		}
		// (the defects pass, 2026-10-16) and the turned-down copy itself, seen from the air below its plane (the steps of
		// a cascade over the eye, a fall's lip from its foot): it is the view from under water, split along the other
		// diagonal, and stood over the sheet as dark and pale triangles (and a black one with the eye in a fall). Only a
		// real underside stays: a level face at a block's foot (water over air).
		if (isEyeInWater != 1 && nW.y < -0.5) {
			float yIn = fract(eyePos.y + cameraPositionFract.y + 1e-3);
			if (nW.y > -0.999 || yIn > 0.01) {
				discard;
			}
		}
		// a fall's light: smooth lighting gives each corner of a sloped or upright water face its own sky light, which
		// shaded the halves of each step's quad apart (light and dark triangles); on the move, water takes the open sky's
		// light wherever a corner sees some of it
		if (nW.y < 0.985 && nW.y > -0.5) {
			lm.y = mix(lm.y, smoothstep(0.15, 0.6, lm.y), 1.0 - smoothstep(0.93, 0.985, nW.y));
		}
#if WS_DEBUG_VIEW == 5
		// the faces drawn: the face's own world normal as colour, a back face (seen from under water) in stripes
		outColor = vec4((nW * 0.5 + 0.5) * (gl_FrontFacing ? 1.0 : 0.4 + 0.6 * float((int(gl_FragCoord.y) >> 2) & 1)), 1.0);
		outWater = vec4(0.0);
		outInfo = vec4(0.5, 0.5, 0.0, WS_KIND_OTHER);
		return;
#endif
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
		// (the lighting's rule, merged 2026-10-16) the sun's reach as for level water: a fall's facets and a slope's
		// tilted quads would each meet the sun at their own angle, sunlit beside shaded in triangles
		vec3 nL = welkinWaterNormal(nW);
		w.reach = welkinSunReach(eyePos, nL, lm.y, material, sunFacing(dot(nL, L)), w.sunShare);
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

	// separateAo (shaders.properties): the vertex alpha is Minecraft's corner shading, not part of the colour or the
	// alpha test; the light lays it on the fill (as gbuffers_common does)
	vec4 albedo = tex * vec4(glcolor.rgb, 1.0);
	float ao = glcolor.a;
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
	vec3 lit = welkinLightFull(albedo.rgb, nW, lm, emission, sunShare, vec3(1.0), reach, facing, ao);
	outColor = wsShadeGlass(lit, albedo.a, eyePos, nW, lm.y, material, sf, outInfo);
	outWater = vec4(0.0);
}

#endif
