// VOID Welkin: far weather (P5, from the staged module M4), the second composite pass in the overworld: after the voxel
// clouds (composite), before the near atmosphere (composite2). It lays, over the scene:
//   1. the water fog (lib/water.glsl), which in the overworld moved here from composite2 so the far air and the far
//      layers lie over a lake's water as over the land (not over a cloud in front of a lake, as before)
//   2. the far air past the near atmosphere's seam, the far valley mist and the rain curtains
//      (lib/far_weather/wf_far_weather.glsl)
// and passes colortex6 on, with the curtain's distance on sky pixels whose curtain it finished (see wf_far_weather).

#define WELKIN_DEPTH_BEHIND
#include "/lib/common.glsl"
#include "/lib/buffers.glsl"
#include "/lib/clouds/buffers.glsl"

#ifdef VERTEX_SHADER

out vec2 texcoord;

void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}

#endif

#ifdef FRAGMENT_SHADER

#include "/lib/water.glsl"
#include "/lib/void.glsl"
#include "/lib/fade.glsl"
#include "/lib/mist.glsl"
#include "/lib/sky/table.glsl"
#include "/lib/haze/haze.glsl"
#include "/lib/clouds/map.glsl"
#include "/lib/far_weather/wf_far_weather.glsl"
#include "/lib/underground/ug_common.glsl"
// the defects pass: foam along the shore
#include "/lib/ws/shore.glsl"

in vec2 texcoord;

#ifndef WU_colortex0
#define WU_colortex0
uniform sampler2D colortex0;
#endif
#ifndef WU_colortex6
#define WU_colortex6
uniform sampler2D colortex6;
#endif

/* RENDERTARGETS: 0,6 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outCloud;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 color = texelFetch(colortex0, p, 0);
	float km = texelFetch(colortex6, p, 0).r;
#if DEBUG_VIEW == 0
	// 1. water fog, as composite2 laid it before P5 (not over a cloud in front of a lake, seen from above the deck)
	bool cloud = isEyeInWater == 0 && km < 999.0;
	if (!cloud) {
		color.rgb = waterFog(p, texcoord, color.rgb);
		color.rgb = wsShoreFoam(p, texcoord, color.rgb); // (the defects pass) foam along the shore
	}
	// 2. far weather; at cave mouths it hands over to the cave air (M5) as composite2's near air does, by ug.amount
	vec3 fogged = color.rgb;
	color.rgb = welkinFarWeather(p, texcoord, color.rgb, km);
	color.rgb = mix(color.rgb, fogged, ugState().amount);
#endif
	outColor = color;
	outCloud = vec4(km, 0.0, 0.0, 1.0);
}

#endif
