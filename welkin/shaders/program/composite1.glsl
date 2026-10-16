// VOID Welkin slot composite1: far weather (P5: water fog, far air, far valley mist, rain curtains;
// program/parts/far_weather.glsl). Overworld only, where it always runs (it lays the overworld's water fog); after the
// clouds (composite), before the near atmosphere (composite2).
#if defined WORLD_OVERWORLD
#include "/program/parts/far_weather.glsl"
#else
#include "/program/parts/copy.glsl"
#endif
