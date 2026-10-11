// VOID Welkin: the uniforms the clouds read that the skeleton's libraries do not declare yet. GLSL refuses a uniform
// declared twice, so when lib/void.glsl (or any lib the clouds include) gains one of these, delete its line here.
//
// Already declared elsewhere and used by the clouds: void_data, void_bridge, void_version (lib/void.glsl);
// cameraPositionInt, cameraPositionFract (lib/grid.glsl); gbufferModelViewInverse, sunPosition, shadowLightPosition,
// sunAngle, rainStrength (lib/light.glsl); depthtex0, gbufferProjectionInverse, dhDepthTex0, dhProjectionInverse
// (lib/depth.glsl); fogColor, far (lib/fade.glsl).

#ifndef WELKIN_CLOUD_UNIFORMS
#define WELKIN_CLOUD_UNIFORMS

// Gloam (contract v1, design 2.2). Undeclared by Gloam they read 0; the clouds only read them in VOID_MODE_FULL.
#ifndef VOID_U_SEED
#define VOID_U_SEED
#ifndef WU_void_seed
#define WU_void_seed
uniform int void_seed;            // hash of the world seed, 31 bits
#endif
#endif
#ifndef VOID_U_FRONT
#define VOID_U_FRONT
#ifndef WU_void_front
#define WU_void_front
uniform float void_front;         // the front at the camera
#endif
#endif
#ifndef VOID_U_SHOWERS
#define VOID_U_SHOWERS
#ifndef WU_void_showers
#define WU_void_showers
uniform float void_showers;       // 0..0.6
#endif
#endif
#ifndef VOID_U_MAX_STORM
#define VOID_U_MAX_STORM
#ifndef WU_void_maxStorm
#define WU_void_maxStorm
uniform float void_maxStorm;      // the strongest storm within reach, 0..1 (Gloam FarAir.maxStorm): the tower gate
#endif
#endif
#ifndef VOID_U_LIGHT
#define VOID_U_LIGHT
#ifndef WU_void_light
#define WU_void_light
uniform vec4 void_light;          // moon light, star factor, sky light scale (gameplay), dark-nights setting
#endif
#endif
#ifndef VOID_U_CLOUD_LAYERS
#define VOID_U_CLOUD_LAYERS
#ifndef WU_void_cloudLayers
#define WU_void_cloudLayers
uniform vec4 void_cloudLayers;    // low (-1 none), main, high (-1 none), tower top: world y
#endif
#endif
#ifndef VOID_U_WEATHER_SHIFT
#define VOID_U_WEATHER_SHIFT
#ifndef WU_void_weatherShift
#define WU_void_weatherShift
uniform vec4 void_weatherShift;   // xy W0's frame shift (mod 32768), zw W1's (mod 262144): frame = world + shift
#endif
#endif

// Iris.
#ifndef WU_cloudHeight
#define WU_cloudHeight
uniform float cloudHeight;        // the dimension's cloud height (vanilla's deck)
#endif
#ifndef WU_heightLimit
#define WU_heightLimit
uniform int heightLimit;          // the dimension's height
#endif
#ifndef WU_bedrockLevel
#define WU_bedrockLevel
uniform int bedrockLevel;         // its lowest y
#endif
#ifndef WU_worldTime
#define WU_worldTime
uniform int worldTime;
#endif
#ifndef WU_worldDay
#define WU_worldDay
uniform int worldDay;
#endif
#ifndef WU_thunderStrength
#define WU_thunderStrength
uniform float thunderStrength;
#endif
#ifndef WU_lightningBoltPosition
#define WU_lightningBoltPosition
uniform vec4 lightningBoltPosition; // w 1 while a bolt is out
#endif

#endif
