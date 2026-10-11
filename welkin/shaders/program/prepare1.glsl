// VOID Welkin slot prepare1: the raw cloud map (M3 clouds, program/parts/cloudmap.glsl). Overworld only; disabled
// elsewhere in shaders.properties. tools/wrappers.sh makes the per-dimension wrappers from this file.
#if defined WORLD_OVERWORLD
#include "/program/parts/cloudmap.glsl"
#else
#include "/program/parts/copy.glsl"
#endif
