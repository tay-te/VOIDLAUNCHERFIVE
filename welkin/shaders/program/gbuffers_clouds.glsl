// VOID Welkin: gbuffers_clouds. Vanilla's clouds (clouds=fancy until milestone M3, when the pack draws its own voxel
// clouds and this goes): their own colour, untextured. The chunks' depth buffer knows nothing of the far land, so a
// cloud behind an LOD mountain is cut here against the LOD's depth (dhDepthTex0). Each cloud pixel is marked in
// colortex2 (unlit, material MAT_CLOUD; unblended, shaders.properties), so deferred leaves it alone and composite's mist
// and debug views never take a cloud's depth for ground.

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec4 glcolor;
out vec3 viewPos;

void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
	viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/depth.glsl"

in vec4 glcolor;
in vec3 viewPos;

uniform float alphaTestRef;
uniform float viewWidth;
uniform float viewHeight;

/* RENDERTARGETS: 0,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outFlags;

void main() {
	if (glcolor.a < alphaTestRef) {
		discard;
	}
#ifdef DISTANT_HORIZONS
	float dh = texelFetch(dhDepthTex0, ivec2(gl_FragCoord.xy), 0).r;
	if (dh < 1.0) {
		vec3 land = viewFromDepth(gl_FragCoord.xy / vec2(viewWidth, viewHeight), dh, dhProjectionInverse);
		if (length(land) < length(viewPos)) {
			discard;
		}
	}
#endif
	outColor = glcolor;
	outFlags = vec4(0.0, float(MAT_CLOUD) / 255.0, 0.0, 1.0);
}

#endif
