// VOID Welkin: the one gbuffers program, specialised by defines in each program/gbuffers_*.glsl:
//
//   G_TEXTURED   sample the bound texture (gtexture)
//   G_DEFERRED   write albedo, normal, light map and material for deferred to light (terrain)
//   G_FLAG       light here, and write colortex2.x = 0 so deferred leaves the pixel alone (opaque-phase forward)
//   G_UNLIT      no lighting at all: the texture's own colour (spider eyes, glint, beacon, cracks)
//   G_ENTITY     apply entityColor (the hurt flash)
//   G_BLOCKID    read mc_Entity for the block.properties material
//   G_EMISSIVE   the whole program glows (emission 1)
//   G_WATERFLAG  also write colortex3: the water's own lit colour where the pixel is water, for composite's water fog
//
// Without G_DEFERRED the program lights itself with the same stepped ramp deferred uses (lib/light.glsl).

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec3 eyePos;
flat out int material;

uniform mat4 gbufferModelViewInverse;
#ifdef G_BLOCKID
in vec4 mc_Entity;
#endif

void main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	glcolor = gl_Color;
	normalW = normalize(mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal));
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
#ifdef G_BLOCKID
	material = materialFromBlockId(int(mc_Entity.x + 0.5));
#else
	material = MAT_NONE;
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

uniform sampler2D gtexture;
uniform float alphaTestRef;
#ifdef G_ENTITY
uniform vec4 entityColor;
#endif

#if !defined G_DEFERRED && !defined G_UNLIT
#include "/lib/light.glsl"
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
	if (material == MAT_EMISSIVE || material == MAT_LAVA) {
		emission = 0.85;
	}

#if defined G_DEFERRED
	outColor = vec4(albedo.rgb, 1.0);
	outNormal = vec4(octEncode(normalize(normalW)), lmcoord);
	outFlags = vec4(1.0, float(material) / 255.0, emission, 1.0);
#else
#ifdef G_UNLIT
	vec3 lit = albedo.rgb;
#else
	// a screen-pixel Bayer here: these are small things (entities, particles) on no world grid of their own
	float d = 0.5;
#ifdef DITHER
	d = bayer4(ivec2(gl_FragCoord.xy));
#endif
	vec3 lit = welkinLight(albedo.rgb, normalize(normalW), lmcoord, emission, d);
#endif
	outColor = vec4(lit, albedo.a);
#ifdef G_FLAG
	outFlags = vec4(0.0, 0.0, 0.0, 1.0);
#endif
#ifdef G_WATERFLAG
	outWater = material == MAT_WATER ? vec4(lit, 1.0) : vec4(0.0);
#endif
#endif
}

#endif
