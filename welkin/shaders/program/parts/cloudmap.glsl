// VOID Welkin: the raw cloud map (M3), prepare pass 1. Works out every cloud cell the frame can see, once, into colortex7
// (lib/clouds/map.glsl): one 160 x 160 square of cells per ring, centred on the camera's cell of that ring. Prepare
// pass 2 (cloudfinish.glsl) adds edges and blocks into colortex8, which everything after reads, a texel a column.
//
// Runs before the gbuffers (Iris: shadow, prepare, gbuffers, deferred, translucents, composite), so deferred has it.
// Its viewport is colortex7's size (size.buffer.colortex7 = 160 1280 in shaders.properties). Overworld only.

#include "/lib/common.glsl"
#include "/lib/clouds/buffers.glsl"

#ifdef VERTEX_SHADER

void main() {
	gl_Position = ftransform();
}

#endif

#ifdef FRAGMENT_SHADER

#define WC_STAGE_RAW
#include "/lib/clouds/map.glsl"

/* RENDERTARGETS: 7 */
layout(location = 0) out vec4 outMap;

void main() {
	ivec2 p = ivec2(gl_FragCoord.xy);
	int lv = min(p.y / WC_MAP, WC_MAX_LEVEL);
	ivec2 local = ivec2(p.x, p.y - lv * WC_MAP);
	CloudEnv e = welkinCloudEnv();
	TCache tq = wcTCacheNew();
	ivec2 cell = wcMapAnchor(lv, e) + local - WC_MAP_HALF;
	outMap = wcMapEncode(wcColumn(cell, lv, -1e9, 1e9, e, tq), e);
}

#endif
