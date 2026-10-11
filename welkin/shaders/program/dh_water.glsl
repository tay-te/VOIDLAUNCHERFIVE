// VOID Welkin: dh_water. The far water, drawn by VOID LOD after the world's translucents and before composite,
// lit here with the same stepped ramp (deferred has already run), blended per shaders.properties' blend.dh_water.
// Fades into the horizon with the far land (FAR_FADE). Marks its pixels in colortex3 for composite's water fog.

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec4 glcolor;
out vec2 lmcoord;
out vec3 eyePos;
flat out vec3 normalW;

uniform mat4 gbufferModelViewInverse;

void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
	lmcoord = lightmapLevel((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	normalW = gl_Normal;
	eyePos = (gbufferModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/grid.glsl"
#include "/lib/light.glsl"
#include "/lib/fade.glsl"
#include "/lib/depth.glsl"

in vec4 glcolor;
in vec2 lmcoord;
in vec3 eyePos;
flat in vec3 normalW;

uniform float clipDistance;
uniform mat4 gbufferProjection;
uniform float viewWidth;
uniform float viewHeight;

/* RENDERTARGETS: 0,3 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outWater;

void main() {
	float horizontal = length(eyePos.xz);
	if (horizontal < clipDistance) {
		discard;
	}
	// where the chunks' water (or anything of theirs) already stands here, at or in front of this, it is the same
	// water: drawing both would double its colour along the join
	float chunkDepth = texelFetch(depthtex0, ivec2(gl_FragCoord.xy), 0).r;
	if (chunkDepth < 1.0) {
		vec3 chunkView = viewFromDepth(gl_FragCoord.xy / vec2(viewWidth, viewHeight), chunkDepth, gbufferProjectionInverse);
		if (length(chunkView) < length(eyePos) + 2.0) {
			discard;
		}
	}
	float bpp = blocksPerPixelAt(length(eyePos), gbufferProjection[1][1], viewHeight);
	int level = gridLevel(bpp, 2.0);
	float d = 0.5;
#ifdef DITHER
	d = worldDither(eyePos, normalW, level);
#endif
	vec3 lit = welkinLight(glcolor.rgb, normalW, lmcoord, 0.0, d);
	float alpha = glcolor.a;
	// the water's own colour for composite's water fog, less of it where the far fade has taken over
	outWater = vec4(lit, 1.0);
#ifdef FAR_FADE
	float fade = farFade(horizontal, d);
	outWater.a = 1.0 - fade;
	lit = mix(lit, horizonColor(), fade);
	alpha = mix(alpha, 1.0, fade);
#endif
	outColor = vec4(lit, alpha);
}

#endif
