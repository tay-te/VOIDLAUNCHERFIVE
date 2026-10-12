// VOID Welkin: prepare_terrain, run as Iris's prepare3 (tools/wrappers.sh maps it). The terrain's light for the frame
// (lib/terrain.glsl): every cell of Gloam's near land tile, its shadow lines toward the light and its slope, into
// colortex15 (256 x 512, size.buffer.colortex15 in shaders.properties; this pass's viewport is that size). Overworld
// only, with TERRAIN_SHADOWS; elsewhere disabled in shaders.properties.

#include "/lib/common.glsl"

#ifdef VERTEX_SHADER

void main() {
	gl_Position = ftransform();
}

#endif

#ifdef FRAGMENT_SHADER

#define WELKIN_TERRAIN_BUILD
#include "/lib/light.glsl"
#include "/lib/shadow.glsl"
#include "/lib/terrain.glsl"

/* RENDERTARGETS: 15 */
layout(location = 0) out vec4 outTerrain;

void main() {
#ifdef WELKIN_TERRAIN_LIVE
	float nearReach = 0.0;
#if defined SHADOWS
	nearReach = max(shadowReach() - 12.0, 0.0);
#endif
	outTerrain = terrainBuild(ivec2(gl_FragCoord.xy), lightDirWorld(), nearReach);
#else
	outTerrain = vec4(0.0);
#endif
}

#endif
