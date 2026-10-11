// VOID Welkin: gbuffers_skybasic. The sky dome in flat bands: the vanilla sky colour overhead stepping down to the
// fog colour at the horizon, in five bands with Bayer-dithered edges. Stars and the sunrise fan keep vanilla's
// geometry; the fan's fade is cut into steps too. (M2 replaces the colours with the prepare pass's sky table.)

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

out vec4 glcolor;

void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
}

#endif

#ifdef FRAGMENT_SHADER

in vec4 glcolor;

uniform int renderStage;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float viewWidth;
uniform float viewHeight;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

float skyDither() {
#ifdef DITHER
	return bayer4(ivec2(gl_FragCoord.xy));
#else
	return 0.5;
#endif
}

void main() {
	if (renderStage == MC_RENDER_STAGE_STARS) {
		outColor = glcolor;
		return;
	}
	if (renderStage == MC_RENDER_STAGE_SUNSET) {
		// the sunrise and sunset fan: its colour, its fade in four steps
		outColor = vec4(glcolor.rgb, steps(glcolor.a, 4.0, skyDither()));
		return;
	}
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 view = gbufferProjectionInverse * ndc;
	vec3 dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
	float t = clamp(dir.y * 2.4 + 0.08, 0.0, 1.0);
	t = sqrt(t);
	float band = steps(t, 5.0, skyDither());
	outColor = vec4(mix(fogColor, skyColor, band), 1.0);
}

#endif
