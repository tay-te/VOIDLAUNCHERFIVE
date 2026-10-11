// VOID Welkin sky (M2): the Iris uniforms the sky libraries read, declared once. Names lib/light.glsl,
// lib/depth.glsl or lib/fade.glsl already declare sit behind their guards, so include those first when a program
// uses both (GLSL does not allow a uniform declared twice).

#ifndef WELKIN_SKY_UNIFORMS
#define WELKIN_SKY_UNIFORMS

#ifndef WELKIN_LIGHT
#ifndef WU_sunPosition
#define WU_sunPosition
uniform vec3 sunPosition;          // view space, length 100
#endif
#ifndef WU_sunAngle
#define WU_sunAngle
uniform float sunAngle;            // 0 sunrise, 0.25 noon, 0.5 sunset
#endif
#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4 gbufferModelViewInverse;
#endif
#endif
#if defined WORLD_NETHER || defined WORLD_END
#ifndef WELKIN_FADE
#ifndef WU_fogColor
#define WU_fogColor
uniform vec3 fogColor;
#endif
#endif
#endif
#ifndef WU_moonPosition
#define WU_moonPosition
uniform vec3 moonPosition;         // view space, length 100
#endif
#ifndef WU_moonPhase
#define WU_moonPhase
uniform int moonPhase;             // 0 full .. 4 new .. 7
#endif
#ifndef WU_worldTime
#define WU_worldTime
uniform int worldTime;             // ticks into the day
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;    // seconds, wraps at 3600
#endif
#ifndef WU_thunderStrength
#define WU_thunderStrength
uniform float thunderStrength;
#endif
#ifndef WU_blindness
#define WU_blindness
uniform float blindness;
#endif
#ifndef WU_darknessFactor
#define WU_darknessFactor
uniform float darknessFactor;
#endif
// shaders.properties: smooth(if(lightningBoltPosition.w > 0.5, 1.0, 0.0), 0.02, 0.45)
#ifndef WU_welkin_flash
#define WU_welkin_flash
uniform float welkin_flash;
#endif

#endif
