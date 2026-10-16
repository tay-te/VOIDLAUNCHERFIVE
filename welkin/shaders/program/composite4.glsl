// VOID Welkin: composite4, the depth prototype BLOOM's gather (the depth A/B sheet, 2026-10-16). Half resolution, into
// colortex9 (free after the ambient occlusion's passes). The bright source (composite3, colortex14) is read from six
// of its mip levels, each through a 3x3 tent at that level's own texel size (smooth, never a box), and summed with
// weights that add to 1 (energy-conserving: the halo spreads the bright light, it never makes more of it): a tighter
// core from the finer levels, a wide soft halo from the coarser. final lays it on before its shoulder.

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

const bool colortex14MipmapEnabled = true;

in vec2 texcoord;

#ifndef WU_colortex14
#define WU_colortex14
uniform sampler2D colortex14;
#endif

/* RENDERTARGETS: 9 */
layout(location = 0) out vec4 outGlow;

vec3 tentAt(vec2 uv, float lod) {
	vec2 px = 1.0 / vec2(textureSize(colortex14, int(lod)));
	vec3 c = textureLod(colortex14, uv, lod).rgb * 4.0;
	c += (textureLod(colortex14, uv + vec2(px.x, 0.0), lod).rgb + textureLod(colortex14, uv - vec2(px.x, 0.0), lod).rgb
		+ textureLod(colortex14, uv + vec2(0.0, px.y), lod).rgb + textureLod(colortex14, uv - vec2(0.0, px.y), lod).rgb) * 2.0;
	c += textureLod(colortex14, uv + px, lod).rgb + textureLod(colortex14, uv - px, lod).rgb
		+ textureLod(colortex14, uv + vec2(px.x, -px.y), lod).rgb + textureLod(colortex14, uv + vec2(-px.x, px.y), lod).rgb;
	return c / 16.0;
}

void main() {
	vec3 g = vec3(0.0);
#if WD_BLOOM
	vec2 uv = (gl_FragCoord.xy) / vec2(textureSize(colortex14, 0));
	// the core (levels 1, 2) and the halo (3 .. 6); weights sum to 1
	g += tentAt(uv, 1.0) * 0.20;
	g += tentAt(uv, 2.0) * 0.20;
	g += tentAt(uv, 3.0) * 0.19;
	g += tentAt(uv, 4.0) * 0.17;
	g += tentAt(uv, 5.0) * 0.14;
	g += tentAt(uv, 6.0) * 0.10;
#endif
	outGlow = vec4(g, 1.0);
}

#endif
