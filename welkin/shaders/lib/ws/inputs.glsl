// VOID Welkin water: the world state the water, the shore and the seasons read, from VOID Gloam when its uniforms are
// live (lib/void.glsl voidMode() == VOID_MODE_FULL), else built-in defaults (DESIGN.md 2.5): a breeze that freshens
// with vanilla's rain (the same fallback the leaves sway in, lib/sway.glsl), no seasons, no shore band. The near
// tiles (N0, for the shore) are read whenever the texture is Gloam's, with the header's centre.
//
// Needs lib/common.glsl, lib/void.glsl and lib/sway.glsl first (the program includes them).

#ifndef WELKIN_WS_INPUTS
#define WELKIN_WS_INPUTS

#ifndef WU_rainStrength
#define WU_rainStrength
uniform float rainStrength;
#endif
#ifndef WU_wetness
#define WU_wetness
uniform float wetness;
#endif
#ifndef WU_cameraPositionInt
#define WU_cameraPositionInt
uniform ivec3 cameraPositionInt;
#endif
#ifndef WU_cameraPositionFract
#define WU_cameraPositionFract
uniform vec3 cameraPositionFract;
#endif
#ifndef WU_frameTimeCounter
#define WU_frameTimeCounter
uniform float frameTimeCounter;
#endif
#ifndef VOID_U_WIND
#define VOID_U_WIND
#ifndef WU_void_wind
#define WU_void_wind
uniform vec4 void_wind;        // unit x, unit z, blocks/s, gust 0..1
#endif
#endif
#ifndef VOID_U_RAIN
#define VOID_U_RAIN
#ifndef WU_void_rain
#define WU_void_rain
uniform float void_rain;       // regional rain 0..1
#endif
#endif
#ifndef VOID_U_WETNESS
#define VOID_U_WETNESS
#ifndef WU_void_wetness
#define WU_void_wetness
uniform float void_wetness;    // ground wetness 0..1
#endif
#endif
#ifndef VOID_U_WARMTH
#define VOID_U_WARMTH
#ifndef WU_void_warmth
#define WU_void_warmth
uniform float void_warmth;     // -1 deep winter .. 1 high summer
#endif
#endif
#ifndef VOID_U_SEASON_STATE
#define VOID_U_SEASON_STATE
#ifndef WU_void_seasonState
#define WU_void_seasonState
uniform vec4 void_seasonState; // bare, turned, cold, snowline y
#endif
#endif
#ifndef VOID_U_CLIMATE
#define VOID_U_CLIMATE
#ifndef WU_void_climate
#define WU_void_climate
uniform vec4 void_climate;     // temperature -1..1, downfall, lowness, water (at the camera)
#endif
#endif

struct WsIn {
	vec4 wind;     // unit x, unit z, blocks/s, gust 0..1
	float rain;    // 0..1
	float wet;     // ground wetness 0..1
	float warmth;  // -1 winter .. 1 summer (0 without Gloam)
	vec4 season;   // bare, turned, cold, snowline y
	float temp;    // local temperature -1..1
	bool live;     // seasons are real (Gloam's uniforms), not defaults
	int mode;      // voidMode()
};

WsIn wsInputs() {
	WsIn i;
	i.mode = voidMode();
	i.wind = welkinWindFallback(rainStrength);
	i.rain = rainStrength;
	i.wet = wetness;
	i.warmth = 0.0;
	i.season = vec4(0.0, 0.0, 0.0, 1.0e5);
	i.temp = 0.3;
	i.live = false;
	if (i.mode == VOID_MODE_FULL) {
		i.wind = void_wind;
		i.rain = clamp(void_rain, 0.0, 1.0);
		i.wet = clamp(void_wetness, 0.0, 1.0);
		i.warmth = clamp(void_warmth, -1.0, 1.0);
		i.season = void_seasonState;
		i.temp = clamp(void_climate.x, -1.0, 1.0);
		i.live = true;
	}
	// a zero wind direction would make NaNs downstream
	if (dot(i.wind.xy, i.wind.xy) < 1e-4) {
		i.wind.xy = vec2(1.0, 0.0);
	}
	i.wind.xy = normalize(i.wind.xy);
	return i;
}

// The wind for the shadow pass and anything else that only needs it (as gbuffers_terrain's sway reads it).
vec4 wsWind() {
	vec4 w = (void_bridge == 1 && BRIDGE_MODE == 0) ? void_wind : welkinWindFallback(rainStrength);
	if (dot(w.xy, w.xy) < 1e-4) {
		w.xy = vec2(1.0, 0.0);
	}
	w.xy = normalize(w.xy);
	return w;
}

#endif
