// VOID Welkin: deferred. Lights the opaque world after the chunks and the LOD have drawn, before translucents.
//
// The surface is the chunks' (depthtex0) where they drew and the LOD's (dhDepthTex0) elsewhere, each through its own
// projection (lib/depth.glsl). Pixels marked in colortex2.x get the stepped light ramp (lib/light.glsl) with its band edges
// dithered on the world's 16-pixel grid; the rest lit themselves in their gbuffers program. Then the far edge fades
// into the horizon colour (lib/fade.glsl). The sky is left as gbuffers_skybasic drew it.

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

#include "/lib/depth.glsl"
#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/fade.glsl"

in vec2 texcoord;

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform mat4 gbufferProjection;
uniform float viewHeight;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
	Surface s = surfaceAt(p, texcoord);
	if (s.sky) {
		outColor = color;
		return;
	}
	vec3 eyePos = mat3(gbufferModelViewInverse) * s.view;
	float dist = length(s.view);
	vec4 flags = texelFetch(colortex2, p, 0);
	vec4 g = texelFetch(colortex1, p, 0);
	vec3 n = octDecode(g.xy);

	float d = 0.5;
#ifdef DITHER
	int level = gridLevel(blocksPerPixelAt(dist, gbufferProjection[1][1], viewHeight), 1.0);
	d = worldDither(eyePos, n, level);
#endif
#if DEBUG_VIEW != 4
	if (flags.x > 0.5) {
		color.rgb = welkinLight(color.rgb, n, g.zw, flags.z, d);
	}
#endif
#if defined FAR_FADE && DEBUG_VIEW == 0
	color.rgb = mix(color.rgb, horizonColor(), farFade(length(eyePos.xz), d));
#endif
	outColor = color;
}

#endif
