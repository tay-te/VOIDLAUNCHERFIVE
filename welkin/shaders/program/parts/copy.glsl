// VOID Welkin: a plain copy of the scene, for a pass slot a dimension does not use (its program is also disabled in
// shaders.properties; this keeps the source valid if Iris compiles it anyway).
#include "/lib/common.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
	outColor = texelFetch(colortex0, ivec2(gl_FragCoord.xy), 0);
}
#endif
