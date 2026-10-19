// VOID Welkin: gbuffers_skybasic. The sky dome, drawn per pixel from the prepare pass's sky table (lib/sky/dome.glsl):
// a smooth gradient from the horizon to the zenith with gentle bands, the dusk palette's glow round the sun and the
// moon's halo; then the stars on the sky's own pixel grid (count from Gloam's void_light, twinkling), the moon by
// phase and the sun as crisp pixel art.
//
// Every piece of sky geometry vanilla and Iris send through here (the sky disc, Iris's horizon, the dark disc below)
// gets the same per-pixel colour from its view direction, so the geometry only decides coverage. Vanilla's stars and
// sunrise fan are discarded: the dome draws its own. Sky pixels keep colortex2 clear, so deferred leaves them alone.
// The Nether and the End keep vanilla's colours, as a smooth gradient.

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

#ifndef WU_renderStage
#define WU_renderStage
uniform int renderStage;
#endif
#ifndef WU_viewWidth
#define WU_viewWidth
uniform float viewWidth;
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
#ifndef WELKIN_DEPTH
#ifndef WU_gbufferProjectionInverse
#define WU_gbufferProjectionInverse
uniform mat4 gbufferProjectionInverse;
#endif
#endif

#include "/lib/hdr.glsl"

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

#if !defined WORLD_NETHER && !defined WORLD_END

#include "/lib/sky/dome.glsl"

void main() {
	if (renderStage == MC_RENDER_STAGE_STARS || renderStage == MC_RENDER_STAGE_SUNSET) {
		discard;
	}
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 view = gbufferProjectionInverse * ndc;
	vec3 dir = mat3(gbufferModelViewInverse) * (view.xyz / view.w);
	outColor = vec4(skyFullScene(dir), 1.0); // (HDR stage A: the sky in scene-linear light, lib/sky/dome.glsl)
}

#else

#ifndef WU_skyColor
#define WU_skyColor
uniform vec3 skyColor;
#endif
#ifndef WU_fogColor
#define WU_fogColor
uniform vec3 fogColor;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif

void main() {
	if (renderStage == MC_RENDER_STAGE_STARS || renderStage == MC_RENDER_STAGE_SUNSET) {
		outColor = vec4(sceneFromAuthored(glcolor.rgb), glcolor.a); // (vanilla's authored colour as scene light)
		return;
	}
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 view = gbufferProjectionInverse * ndc;
	vec3 dir = normalize(mat3(gbufferModelViewInverse) * (view.xyz / view.w));
	float t = sqrt(clamp(dir.y * 2.4 + 0.08, 0.0, 1.0));
	outColor = vec4(sceneFromAuthored(mix(fogColor, skyColor, softBands(t, 5.0))), 1.0); // (scene light)
}

#endif

#endif
