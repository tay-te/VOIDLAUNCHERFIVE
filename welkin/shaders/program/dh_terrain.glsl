// VOID Welkin: dh_terrain. The far land, drawn by VOID LOD through the Distant Horizons layout (Iris patches this
// program for it). The same G-buffer as gbuffers_terrain, so deferred lights near and far land with one ramp.
//
// Kept trivial on purpose: LOD's auto-tune counts half of this program's GPU time against its radius. No texture
// fetch; the far texture is a hash on the world's 16-pixel grid, coarser with distance. The only discard cuts what
// the chunks cover (clipDistance, set by VOID LOD to where the chunks end), and only fragments that near pay for it.

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec4 glcolor;
out vec2 lmcoord;
out vec3 eyePos;
flat out vec3 normalW;
flat out int material;
flat out int lightClass;

#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif

void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	// Distant Horizons normals are world axes already
	normalW = gl_Normal;
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
	material = welkinDhMat(dhMaterialId);
	lightClass = welkinDhLight(dhMaterialId);
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/grid.glsl"

in vec4 glcolor;
in vec2 lmcoord;
in vec3 eyePos;
flat in vec3 normalW;
flat in int material;
flat in int lightClass;

#ifndef WU_clipDistance
#define WU_clipDistance
uniform float clipDistance;
#endif
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4 gbufferProjection;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif

/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outFlags;

void main() {
	float horizontal = length(eyePos.xz);
	if (horizontal < clipDistance) {
		discard;
	}
	vec3 albedo = glcolor.rgb;
#ifdef FAR_TEXTURE
	// one shade per world texel (the far land's stand-in for block texture), texels doubling in size so each spans
	// FAR_TEXTURE_PIXELS pixels; faint, and fading out with distance so hazy far land never turns to pixel noise
	float dist = length(eyePos);
	float bpp = blocksPerPixelAt(dist, gbufferProjection[1][1], viewHeight);
	int level = gridLevel(bpp, float(FAR_TEXTURE_PIXELS));
	float texel = exp2(float(level)) / 16.0;
	ivec3 cell = worldTexel(eyePos - normalW * (0.5 * texel), level);
	float h = hash01(cell);
	float amount = 1.0 - smoothstep(1024.0, 6144.0, dist);
	albedo *= 1.0 + (h < 0.3 ? -0.05 : (h > 0.78 ? 0.04 : 0.0)) * amount;
#endif
	if (matIsLeaves(material)) {
		// a far crown is one flat colour, the texture's average; near leaves show the darker leaves behind their holes,
		// so the far crowns come down to their tone (a little darker, a little less saturated)
		albedo = mix(vec3(dot(albedo, vec3(0.2126, 0.7152, 0.0722))), albedo, 0.88) * 0.86;
	}
	float emission = lightClass != L_NONE ? 0.85 : 0.0; // lava and lit blocks; far land has no glow mask
	outColor = vec4(albedo, 1.0);
	outNormal = vec4(octEncode(normalW), lmcoord);
	outFlags = vec4(1.0, float(material) / 255.0, packEmitClass(emission, lightClass), 1.0);
}

#endif
