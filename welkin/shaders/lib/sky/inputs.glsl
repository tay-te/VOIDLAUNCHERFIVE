// VOID Welkin sky (M2): what the sky table is built from, read once per prepare fragment.
//
// Every Gloam value goes through one rule, per uniform slot:
//   - the slot is live (its bit in the header's live mask, texel (11, 0)) and the uniforms are in (VOID_MODE_FULL):
//     the void_* uniform;
//   - live, but the Iris mixin did not apply (VOID_MODE_HEADER): the 16-bit header mirror (rows 1 and 2);
//   - not live, or no Gloam at all (VOID_MODE_NONE): the pack's own value, worked out from Iris's uniforms with the
//     same maths Gloam uses (lib/sky/palette.glsl), or vanilla's behaviour where Gloam would change gameplay light.
// A slot Gloam does not declare reads 0 as a uniform, which is why the live mask is checked before any uniform.
//
// Needs lib/void.glsl (voidMode, void_data). Include after lib/light.glsl if a program uses both.

#ifndef WELKIN_SKY_INPUTS
#define WELKIN_SKY_INPUTS

#include "/lib/common.glsl"
#include "/lib/void.glsl"
#include "/lib/sky/palette.glsl"

// Gloam's (contract 2.2). Each guarded, so lib/void.glsl may declare any of them first with the same guard.
#ifndef VOID_U_CLOUD_COVER
#define VOID_U_CLOUD_COVER
#ifndef WU_void_cloudCover
#define WU_void_cloudCover
uniform float void_cloudCover;
#endif
#endif
#ifndef VOID_U_STORM
#define VOID_U_STORM
#ifndef WU_void_storm
#define WU_void_storm
uniform float void_storm;
#endif
#endif
#ifndef VOID_U_HAZE
#define VOID_U_HAZE
#ifndef WU_void_haze
#define WU_void_haze
uniform vec4 void_haze;
#endif
#endif
#ifndef VOID_U_HAZE_KIND
#define VOID_U_HAZE_KIND
#ifndef WU_void_hazeKind
#define WU_void_hazeKind
uniform int void_hazeKind;
#endif
#endif
#ifndef VOID_U_LIGHT
#define VOID_U_LIGHT
#ifndef WU_void_light
#define WU_void_light
uniform vec4 void_light;
#endif
#endif
#ifndef VOID_U_SKY
#define VOID_U_SKY
#ifndef WU_void_sky
#define WU_void_sky
uniform vec4 void_sky;
#endif
#endif

// Iris's
#include "/lib/sky/uniforms.glsl"

// The uniform mirror's texel columns: Gloam's Contract.Slot ordinals.
#define VOID_SLOT_CLOUD_COVER 9
#define VOID_SLOT_STORM       12
#define VOID_SLOT_HAZE        18
#define VOID_SLOT_HAZE_KIND   19
#define VOID_SLOT_LIGHT       22
#define VOID_SLOT_SKY         23
#define VOID_LIVE_MASK_TEXEL  11

// The 32-bit mask of uniform slots Gloam fills, little-endian in header texel (11, 0).
uint skyLiveMask() {
	uvec4 b = uvec4(texelFetch(void_data, ivec2(VOID_LIVE_MASK_TEXEL, 0), 0) * 255.0 + 0.5);
	return b.x | (b.y << 8u) | (b.z << 16u) | (b.w << 24u);
}

bool skyLive(uint mask, int slot) {
	return ((mask >> uint(slot)) & 1u) != 0u;
}

// A float or vec4 slot from the mirror: 16 bits per component across the slot's range, the high byte in row 1 and
// the low byte in row 2, component k in channel k.
vec4 skyMirror(int slot, vec4 lo, vec4 hi) {
	vec4 a = floor(texelFetch(void_data, ivec2(slot, 1), 0) * 255.0 + 0.5);
	vec4 b = floor(texelFetch(void_data, ivec2(slot, 2), 0) * 255.0 + 0.5);
	return lo + (a * 256.0 + b) / 65535.0 * (hi - lo);
}

// An int slot from the mirror: its four little-endian bytes in row 1.
int skyMirrorInt(int slot) {
	uvec4 b = uvec4(texelFetch(void_data, ivec2(slot, 1), 0) * 255.0 + 0.5);
	return int(b.x | (b.y << 8u) | (b.z << 16u) | (b.w << 24u));
}

struct SkyInputs {
	int mode;         // VOID_MODE_*
	float clock;      // ticks into the day, 0..24000
	float u;          // the clock folded onto the evening (skyDuskTime)
	float cloudCover; // the fronts' cloud at the camera (0 without Gloam)
	float cover;      // cloud and rain together: how much of the sky is closed, 0..1
	float rain;
	float storm;
	vec4 light;       // void_light: moon light share, stars (0..1), sky light scale, dark-nights setting
	float golden;     // void_sky.x, stepped
	float blue;       // void_sky.y, stepped
	float vivid;      // void_sky.z, about 0.75..1.25
	int sunsetIndex;  // void_sky.w, 0 gold .. 4 dusk blue
	int hazeKind;
	float hazeStrength; // 0..1: how strongly the haze kind tints the horizon
	float moon;       // the moon's brightness by phase, 0 new .. 1 full
	int moonPhase;
	float flash;      // lightning, 0..1 in thirds
	float blind;      // blindness or the darkness effect, 0..1
};

SkyInputs skyInputs() {
	SkyInputs s;
	s.mode = voidMode();
	uint live = s.mode == VOID_MODE_NONE ? 0u : skyLiveMask();
	bool full = s.mode == VOID_MODE_FULL;

	s.clock = float(worldTime);
	s.u = skyDuskTime(s.clock);
	s.moonPhase = moonPhase & 7;
	s.moon = SKY_MOON_PHASE_LIGHT[s.moonPhase];
	// Gloam drives rainStrength regionally itself (LevelWeatherMixin), so it is the camera's rain either way
	s.rain = rainStrength;

	s.cloudCover = 0.0;
	if (skyLive(live, VOID_SLOT_CLOUD_COVER)) {
		s.cloudCover = full ? void_cloudCover : skyMirror(VOID_SLOT_CLOUD_COVER, vec4(0.0), vec4(1.0)).x;
	}
	// GloamSky: cover = max(cloud overhead, rain); vanilla's thunder closes the sky too
	s.cover = clamp(max(s.cloudCover, max(s.rain, thunderStrength)), 0.0, 1.0);

	s.storm = thunderStrength;
	if (skyLive(live, VOID_SLOT_STORM)) {
		s.storm = full ? void_storm : skyMirror(VOID_SLOT_STORM, vec4(0.0), vec4(1.0)).x;
	}

	// Without Gloam the nights are vanilla's: every night lit alike, stars dimmed by rain only.
	// void_light.y is the star amount 0..1 (the contract's range; Gloam sends Night's 0..1.5 star factor / 1.5)
	s.light = vec4(1.0, 1.0 - s.rain, 1.0, 0.0);
	if (skyLive(live, VOID_SLOT_LIGHT)) {
		s.light = full ? void_light : skyMirror(VOID_SLOT_LIGHT, vec4(0.0), vec4(1.0));
	}

	// The dusk palette: Gloam's numbers if it sends them, else the same maths from the clock (vivid needs the
	// world seed, so it stays at an average evening).
	float clear = skyClear(s.cover);
	s.golden = skyStep(skyGolden(s.u) * clear, 3.0);
	s.blue = skyStep(skyBlue(s.u) * clear, 3.0);
	s.vivid = 1.0;
	s.sunsetIndex = skySunsetIndex(s.u);
	if (skyLive(live, VOID_SLOT_SKY)) {
		vec4 k = full ? void_sky : skyMirror(VOID_SLOT_SKY, vec4(0.0), vec4(1.0, 1.0, 1.0, 4.0));
		s.golden = k.x;
		s.blue = k.y;
		s.vivid = k.z;
		s.sunsetIndex = clamp(int(k.w + 0.5), 0, 4);
	}

	// One temperate haze without Gloam (design 2.5).
	s.hazeKind = 1;
	if (skyLive(live, VOID_SLOT_HAZE_KIND)) {
		s.hazeKind = full ? void_hazeKind : skyMirrorInt(VOID_SLOT_HAZE_KIND);
	}
	s.hazeKind = clamp(s.hazeKind, 0, 15);
	s.hazeStrength = 0.5;
	if (skyLive(live, VOID_SLOT_HAZE)) {
		// void_haze.x is Gloam's haziness, 0 clear .. 1 murkiest on Haze's log scale, after the hour's thinning (the
		// mirror's range is -8..8; settled in the wave-2 cross-check, the same reading as the haze and far weather)
		float haziness = full ? void_haze.x : skyMirror(VOID_SLOT_HAZE, vec4(-8.0, 0.0, 0.0, 0.0), vec4(8.0, 1.0, 1.0, 2.0)).x;
		s.hazeStrength = clamp(haziness, 0.0, 1.0);
	}

	// Lightning: Iris's bolt, smoothed in shaders.properties, in thirds. Gloam lets a bolt flash the sky only under
	// a storm (ClientLevelFlashMixin); with Gloam's storm known, the pack keeps the same gate.
	float flash = welkin_flash;
	if (skyLive(live, VOID_SLOT_STORM) && s.storm <= 0.25) {
		flash = 0.0;
	}
	s.flash = floor(clamp(flash, 0.0, 1.0) * 3.0 + 0.35) / 3.0;
	s.blind = clamp(max(blindness, darknessFactor), 0.0, 1.0);
	return s;
}

#endif
