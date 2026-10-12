// VOID Welkin: shadow_solid. The shadow pass for solid blocks: program/shadow.glsl with no texture and no alpha test,
// so the depth test runs early, and with the faces turned away from the light left out.

#define WELKIN_SHADOW_SOLID
#include "/program/shadow.glsl"
