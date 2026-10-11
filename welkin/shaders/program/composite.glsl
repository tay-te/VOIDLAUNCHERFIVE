// VOID Welkin slot composite: the voxel clouds (M3, program/parts/clouds.glsl), first after the translucents. Overworld
// only; the atmosphere is composite2 in every dimension.
#if defined WORLD_OVERWORLD
#include "/program/parts/clouds.glsl"
#else
#include "/program/parts/copy.glsl"
#endif
