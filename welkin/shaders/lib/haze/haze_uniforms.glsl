#ifndef WELKIN_HAZE_UNIFORMS
#define WELKIN_HAZE_UNIFORMS

// Everything the haze and wet modules read. GLSL forbids declaring a uniform twice in one program, so each group is
// skipped when the Welkin lib that already declares it has been included first (its include guard is defined), or
// when the program defines the matching HZ_HAVE_* switch before including any haze file. Include the pack's own libs
// (void, grid, light, fade, water) BEFORE the haze files.
//
// Declaring a void_* uniform Gloam does not supply is harmless: Iris leaves it at 0, and void_bridge == 0 then keeps
// every read of the others switched off.

#ifndef WELKIN_GRID                                 // lib/grid.glsl
#ifndef WU_cameraPositionInt
#define WU_cameraPositionInt
uniform ivec3 cameraPositionInt;        // Iris 1.7+: the camera's block, exact at any distance
#endif
#ifndef WU_cameraPositionFract
#define WU_cameraPositionFract
uniform vec3  cameraPositionFract;      // and the rest of it, 0..1
#endif
#endif

#ifndef WELKIN_VOID                                 // lib/void.glsl
#if !defined VOID_U_DATA && !defined WELKIN_VOID
#define VOID_U_DATA
#ifndef WU_void_data
#define WU_void_data
uniform sampler2D void_data;            // gloam:pack/data, read only with texelFetch, only after the magic check
#endif
#endif
#if !defined VOID_U_BRIDGE && !defined WELKIN_VOID
#define VOID_U_BRIDGE
#ifndef WU_void_bridge
#define WU_void_bridge
uniform int   void_bridge;              // 1 when Gloam's uniform mixin is live
#endif
#endif
#if !defined VOID_U_VERSION && !defined WELKIN_VOID
#define VOID_U_VERSION
#ifndef WU_void_version
#define WU_void_version
uniform int   void_version;
#endif
#endif
#endif

#ifndef WELKIN_LIGHT                                // lib/light.glsl
#ifndef WU_sunAngle
#define WU_sunAngle
uniform float sunAngle;                 // 0 sunrise, 0.25 noon, 0.5 sunset
#endif
#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;             // vanilla rain level (Gloam drives it regionally)
#endif
#ifndef WU_gbufferModelViewInverse
#define WU_gbufferModelViewInverse
uniform mat4  gbufferModelViewInverse;
#endif
#ifndef WU_shadowLightPosition
#define WU_shadowLightPosition
uniform vec3  shadowLightPosition;
#endif
#endif

#ifndef WELKIN_FADE                                 // lib/fade.glsl
#ifndef WU_far
#define WU_far
uniform float far;                      // vanilla render distance in blocks
#endif
#ifndef WU_fogColor
#define WU_fogColor
uniform vec3  fogColor;
#endif
#endif
#if !defined WELKIN_FADE && defined DISTANT_HORIZONS
#ifndef WU_dhRenderDistance
#define WU_dhRenderDistance
uniform int   dhRenderDistance;         // VOID LOD's radius in blocks (PackLod.renderDistance)
#endif
#endif

#if !defined WELKIN_WATER && !defined HZ_HAVE_VIEW_UNIFORMS   // lib/water.glsl, or the program's own
#ifndef WU_gbufferProjection
#define WU_gbufferProjection
uniform mat4  gbufferProjection;        // only [1][1]: pixel footprint for the world texel grid
#endif
#ifndef WU_viewHeight
#define WU_viewHeight
uniform float viewHeight;
#endif
#endif

#ifndef HZ_HAVE_IRIS_EXTRA                          // not declared by any Welkin lib at M0
#ifndef WU_worldTime
#define WU_worldTime
uniform int   worldTime;                // 0..23999, for the daily thinning of the haze
#endif
#ifndef WU_wetness
#define WU_wetness
uniform float wetness;                  // Iris' eased rainStrength: the wet fallback without Gloam
#endif
#ifndef WU_biome_precipitation
#define WU_biome_precipitation
uniform int   biome_precipitation;      // 0 none, 1 rain, 2 snow
#endif
#ifndef WU_eyeBrightnessSmooth
#define WU_eyeBrightnessSmooth
uniform ivec2 eyeBrightnessSmooth;      // y: sky light at the eye, 0..240
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;         // puddle drops
#endif
#ifndef WU_skyColor
#define WU_skyColor
uniform vec3  skyColor;
#endif
#endif

#ifndef HZ_HAVE_BRIDGE_UNIFORMS                     // the rest of Gloam's uniforms (contract 2.2)
#ifndef VOID_U_SEASON
#define VOID_U_SEASON
#ifndef WU_void_season
#define WU_void_season
uniform int   void_season;              // 0 spring .. 3 winter
#endif
#endif
#ifndef VOID_U_SEASON_STATE
#define VOID_U_SEASON_STATE
#ifndef WU_void_seasonState
#define WU_void_seasonState
uniform vec4  void_seasonState;         // bare, turned, cold, snowline y
#endif
#endif
#ifndef VOID_U_RAIN
#define VOID_U_RAIN
#ifndef WU_void_rain
#define WU_void_rain
uniform float void_rain;                // regional rain at the camera, 0..1
#endif
#endif
#ifndef VOID_U_WETNESS
#define VOID_U_WETNESS
#ifndef WU_void_wetness
#define WU_void_wetness
uniform float void_wetness;             // ground wetness, 0..1
#endif
#endif
#ifndef VOID_U_HAZE
#define VOID_U_HAZE
#ifndef WU_void_haze
#define WU_void_haze
uniform vec4  void_haze;                // haziness (log), dust share, curtain, far haze thickness 0.6..2
#endif
#endif
#ifndef VOID_U_HAZE_KIND
#define VOID_U_HAZE_KIND
#ifndef WU_void_hazeKind
#define WU_void_hazeKind
uniform int   void_hazeKind;            // 0..15 (table 2.4)
#endif
#endif
#ifndef VOID_U_CLIMATE
#define VOID_U_CLIMATE
#ifndef WU_void_climate
#define WU_void_climate
uniform vec4  void_climate;             // temperature -1..1, downfall, lowness, water
#endif
#endif
#ifndef VOID_U_UNDERGROUND
#define VOID_U_UNDERGROUND
#ifndef WU_void_underground
#define WU_void_underground
uniform vec4  void_underground;         // depth factor, openness, realm, water kind
#endif
#endif
#ifndef VOID_U_NEAR_CENTRE
#define VOID_U_NEAR_CENTRE
#ifndef WU_void_nearCentre
#define WU_void_nearCentre
uniform ivec2 void_nearCentre;          // centre cell of N0/N1
#endif
#endif
#ifndef VOID_U_FAR_CENTRE
#define VOID_U_FAR_CENTRE
#ifndef WU_void_farCentre
#define WU_void_farCentre
uniform ivec2 void_farCentre;           // centre cell of F0/F1
#endif
#endif
#endif
#if defined VOID_GLOAM && !defined HZ_HAVE_WELKIN_RAINS
#ifndef WU_welkin_rainS
#define WU_welkin_rainS
uniform float welkin_rainS;             // smooth(void_rain, 6, 6) from shaders.properties (only with Gloam)
#endif
#endif

#endif
