// VOID Welkin slot prepare2: the finished cloud map (M3 clouds, program/parts/cloudfinish.glsl). Overworld only.
#if defined WORLD_OVERWORLD
#include "/program/parts/cloudfinish.glsl"
#else
#include "/program/parts/copy.glsl"
#endif
