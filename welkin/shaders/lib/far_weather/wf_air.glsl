// VOID Welkin far weather: the far air, from the seam (WF_SEAM, 1912 blocks horizontal) out to the far land.
//
// The near atmosphere (composite2, lib/haze/haze.glsl welkinHaze) integrates the haze from the camera to the seam when
// WF_FAR_HAZE is on with LOD; this carries the same integral on from the seam, with the same media and densities, so
// the two pieces add up to one ray with no ring where they meet (the seam test):
//   clear air   the camera's kind of air, density s.haze.w * clearMul / max(2048, 0.75 x the world's reach) at
//               HAZE_SEA_LEVEL, thinning with height over 160 blocks (VOID LOD's lod.vsh haze())
//   rain        the camera's rain, constant density along the ray (VOID LOD's rain curtain)
//   murk        the far land's murk (its haze kinds, F tiles, blended bilinearly), thinning over the kind's height,
//               read once at the geometric middle of the far piece, as the near haze's last segment does
//   flat terms  a hazy country's curtain (void_haze.z) and the grey of far rain (W1 at the ray's middle), easing in
//               over WF_RAMP blocks past the seam so the density has no step there either
// Everything in closed form (hzLayer2), no marching. The colour is the air pulled toward the far kinds by the murk's
// share, as the near haze does. Smooth throughout (the look of 2026-10-11): no steps, no dither, no texel snapping.

#ifndef WELKIN_WF_AIR
#define WELKIN_WF_AIR

#include "/lib/far_weather/wf_config.glsl"

struct WfAir {
    bool  on;
    float t0;          // the seam along this ray
    vec3  dir;
    float camY;
    float rhoClear;    // clear air at HAZE_SEA_LEVEL, per block
    float rhoRain;     // the camera's rain, per block, constant
    float flatK;       // the far flat terms at full strength, per block (eased in over WF_RAMP)
    float rhoMurk;     // the far murk at its ground, per block
    float murkBase;    // its ground (world y)
    float murkH;       // its scale height
    vec3  color;
};

WfAir wfNoAir() {
    return WfAir(false, 1e9, vec3(0.0, 1.0, 0.0), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, vec3(0.0));
}

// The integral of a density that eases linearly from 0 at the seam to 1 at WF_RAMP blocks past it, over x blocks.
float wfRampIntegral(float x) {
    x = max(x, 0.0);
    return x < WF_RAMP ? x * x / (2.0 * WF_RAMP) : x - 0.5 * WF_RAMP;
}

// The optical depth of the far air from the seam to t along the ray, split at tc (x: to tc, y: to t).
vec2 wfAirTau2(WfAir a, float tc, float t) {
    if (!a.on || t <= a.t0) return vec2(0.0);
    tc = clamp(tc, a.t0, t);
    vec2 clear = hzLayer2(a.rhoClear, HAZE_SEA_LEVEL, HZ_CLEAR_H, a.t0, tc, t, a.dir, a.camY, HZ_CLEAR_CAP);
    vec2 murk = hzLayer2(a.rhoMurk, a.murkBase, a.murkH, a.t0, tc, t, a.dir, a.camY, HZ_MURK_CAP);
    // the flat terms ease in by horizontal distance past the seam (rays steeper than level reach it sooner)
    float hk = max(length(a.dir.xz), 1e-4);
    vec2 flatTau = a.flatK / hk * vec2(wfRampIntegral((tc - a.t0) * hk), wfRampIntegral((t - a.t0) * hk));
    vec2 rain = a.rhoRain * vec2(tc - a.t0, t - a.t0);
    return clear + murk + flatTau + rain;
}

float wfAirTau(WfAir a, float t) {
    return wfAirTau2(a, t, t).y;
}

// The far air's opacity from the seam to t.
float wfAirAlpha(WfAir a, float t) {
    return 1.0 - exp(-wfAirTau(a, t));
}

// A colour seen at distance t through the far air.
vec3 wfAirOver(WfAir a, vec3 c, float t) {
    return mix(c, a.color, wfAirAlpha(a, t));
}

// The far air along unit direction `dir` (eye-relative, world axes) for a ray ending at tEnd. `air` is the sky's
// horizon colour along dir for the camera's kind (skyHazeAir, as composite2 passes welkinHaze), `farRain` the W1 rain
// at the ray's middle (0..1, 0 when unknown).
WfAir wfAirSetup(HzState s, vec3 dir, float tEnd, vec3 air, float farRain) {
    WfAir a = wfNoAir();
#if defined HAZE_ENABLED && defined WF_FAR_HAZE && defined DISTANT_HORIZONS
    if (s.outdoor <= 0.0) return a;
    float hk = max(length(dir.xz), 1e-4);
    a.t0 = WF_SEAM / hk;
    if (tEnd <= a.t0) return a;
    a.on = true;
    a.dir = dir;
    a.camY = float(cameraPositionInt.y) + cameraPositionFract.y;
    float reach = hzWorldReach();
    // the same clear air and rain as welkinHaze, carried on
    a.rhoClear = s.haze.w * hzKindAir(s.here.kind).z / max(HZ_CLEAR_MIN, HZ_CLEAR_SHARE * reach);
    a.rhoRain = s.rain * s.outdoor * HAZE_RAIN * mix(1.0, 1.4, s.snow) / HZ_RAIN_REACH;
    a.flatK = (s.haze.z * WF_HAZE_CURTAIN_K + clamp(farRain, 0.0, 1.0) * WF_HAZE_RAIN_K * HAZE_RAIN) * s.outdoor;

    // the far land's murk, read once at the geometric middle of the far piece (as hzMurkSegment)
    float tS = sqrt(a.t0 * min(tEnd, max(reach, a.t0) * 2.0));
    HzLand land = hzLandIn(s, cameraPositionFract.xz + dir.xz * tS, true);
    vec3 ka = mix(hzKindAir(land.kind), hzKindAir(land.kind2), land.share2);
    float hz = max(ka.x * s.daily, s.haze.x * exp2(-tS / 1024.0));
    float murkMul = HAZE_STRENGTH * s.outdoor * (1.0 + 0.6 * s.rain);
    a.rhoMurk = hzMurkDensity(hz) * murkMul * (0.75 + 0.5 * land.low) * (1.0 + 0.3 * land.water);
    a.murkBase = land.ground;
    a.murkH = ka.y;

    // one colour: the air (after the rain look), pulled toward the far kinds as far as the murk's share goes
    vec3 airR = hzRainAir(air, s);
    vec3 col = airR;
    float murk = hzLayer2(a.rhoMurk, a.murkBase, a.murkH, a.t0, tEnd, tEnd, dir, a.camY, HZ_MURK_CAP).y;
    float total = wfAirTau(a, tEnd);
    if (murk > 1e-5) {
        vec4 look = hzKindLook(land.kind);
        vec3 tint = hzSeasonTint(look.rgb, land.kind, s);
        if (land.share2 > 0.0) {
            vec4 look2 = hzKindLook(land.kind2);
            tint = mix(tint, hzSeasonTint(look2.rgb, land.kind2, s), land.share2);
            look.a = mix(look.a, look2.a, land.share2);
        }
        if (s.haze.y > 0.0 && land.kind != HZ_K_DUSTY) {
            tint = mix(tint, hzKindLook(HZ_K_DUSTY).rgb, clamp(s.haze.y * 0.6, 0.0, 1.0));
        }
        vec3 kc = clamp(tint * hzLuma(airR), 0.0, 1.0);
        col = mix(airR, kc, clamp(murk / max(total, 1e-9), 0.0, 1.0) * look.a * hzKindStrength(s));
    }
    a.color = col;
#endif
    return a;
}

#endif
