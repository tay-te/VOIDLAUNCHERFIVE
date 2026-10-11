// VOID Welkin: gbuffers_skytextured. With SKY_PIXEL_SUN (the default) the sun and the moon are the dome's own pixel
// art (gbuffers_skybasic) and shaders.properties turns vanilla's quads off; their stages are discarded here as well,
// in case a mod draws them anyway. Everything else (the End sky, a resource pack's custom sky layers, and vanilla's
// sun and moon with SKY_PIXEL_SUN off) is its texture, unlit.

#include "/lib/common.glsl"
#include "/lib/sky/settings.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;
out vec4 glcolor;

void main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
}

#endif

#ifdef FRAGMENT_SHADER

in vec2 texcoord;
in vec4 glcolor;

#ifndef WU_gtexture
#define WU_gtexture
uniform sampler2D gtexture;
#endif
#ifndef WU_renderStage
#define WU_renderStage
uniform int renderStage;
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
#if defined SKY_PIXEL_SUN && !defined WORLD_NETHER && !defined WORLD_END
	if (renderStage == MC_RENDER_STAGE_SUN || renderStage == MC_RENDER_STAGE_MOON) {
		discard;
	}
#endif
	outColor = texture(gtexture, texcoord) * glcolor;
}

#endif
