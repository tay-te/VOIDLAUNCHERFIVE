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

uniform mat4 gbufferModelViewInverse;

void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	// Distant Horizons normals are world axes already
	normalW = gl_Normal;
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
	int m = dhMaterialId;
	material = m == DH_BLOCK_LEAVES ? MAT_LEAVES
		: m == DH_BLOCK_GRASS ? MAT_FOLIAGE
		: m == DH_BLOCK_WATER ? MAT_WATER
		: m == DH_BLOCK_SNOW ? MAT_SNOW
		: m == DH_BLOCK_SAND ? MAT_SAND
		: m == DH_BLOCK_LAVA ? MAT_LAVA
		: m == DH_BLOCK_ILLUMINATED ? MAT_EMISSIVE
		: MAT_STONE;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/grid.glsl"

in vec4 glcolor;
in vec2 lmcoord;
in vec3 eyePos;
flat in vec3 normalW;
flat in int material;

uniform float clipDistance;
uniform mat4 gbufferProjection;
uniform float viewHeight;

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
	// one shade per world texel, three flat shades, texels doubling in size so each spans FAR_TEXTURE_PIXELS pixels
	float bpp = blocksPerPixelAt(length(eyePos), gbufferProjection[1][1], viewHeight);
	int level = gridLevel(bpp, float(FAR_TEXTURE_PIXELS));
	float texel = exp2(float(level)) / 16.0;
	ivec3 cell = worldTexel(eyePos - normalW * (0.5 * texel), level);
	float h = hash01(cell);
	albedo *= h < 0.3 ? 0.93 : (h > 0.78 ? 1.06 : 1.0);
#endif
	float emission = material == MAT_EMISSIVE || material == MAT_LAVA ? 0.85 : 0.0;
	outColor = vec4(albedo, 1.0);
	outNormal = vec4(octEncode(normalW), lmcoord);
	outFlags = vec4(1.0, float(material) / 255.0, emission, 1.0);
}

#endif
