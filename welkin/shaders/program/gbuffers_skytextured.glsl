// VOID Welkin: gbuffers_skytextured. The sun and moon (and the End sky): vanilla's pixel-art textures, unlit.

#include "/lib/common.glsl"

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

uniform sampler2D gtexture;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	outColor = texture(gtexture, texcoord) * glcolor;
}

#endif
