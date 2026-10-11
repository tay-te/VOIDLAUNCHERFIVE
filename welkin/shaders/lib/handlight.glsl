// VOID Welkin: light from a glowing item in either hand (HANDHELD_LIGHT). Only a look: the game's light level,
// which mobs and Hearth's stealth use, does not change.
//
// Vanilla-style reach: an item of light level N lights a pixel d blocks from the eye at level N - d, the same
// falloff a placed block of that level would give. The result is a block-light level 0..1 that the caller maxes
// into its light map's block term BEFORE the ramp, so held light falls off along the same soft bands as torches.
//
//   lm.x = welkinHandBlockLight(lm.x, length(eyePos));                 // deferred and forward lighting
//   vec3 tint = welkinHandTint(lm.x, length(eyePos));                  // optional: colour of the held light
//
// Uniforms (Iris): heldBlockLightValue, heldBlockLightValue2 (0..15), heldItemId, heldItemId2 (item.properties).
// Cost: two uniform reads and a few ALU per pixel.
// Needs lib/settings.glsl and lib/materials.glsl first.
#ifndef WELKIN_HANDLIGHT
#define WELKIN_HANDLIGHT

#ifndef WELKIN_DECL_HELD
#define WELKIN_DECL_HELD
#ifndef WU_heldBlockLightValue
#define WU_heldBlockLightValue
uniform int heldBlockLightValue;
#endif
#ifndef WU_heldBlockLightValue2
#define WU_heldBlockLightValue2
uniform int heldBlockLightValue2;
#endif
#ifndef WU_heldItemId
#define WU_heldItemId
uniform int heldItemId;
#endif
#ifndef WU_heldItemId2
#define WU_heldItemId2
uniform int heldItemId2;
#endif
#endif

// The held level at distance d (blocks from the eye), 0..1.
float welkinHandLevel(float d) {
#ifdef HANDHELD_LIGHT
    float n = float(max(heldBlockLightValue, heldBlockLightValue2));
    // +0.5: the eye sits in the middle of a block, like a placed light's own block
    return clamp((n - d + 0.5) / 15.0, 0.0, 1.0);
#else
    return 0.0;
#endif
}

float welkinHandBlockLight(float blockLevel, float d) {
    return max(blockLevel, welkinHandLevel(d));
}

// Colour of the held light from the brighter hand's item class; the skeleton's block-light colour when the
// item has no light class. Blend weight is how much of the pixel's block light comes from the hand.
vec3 welkinHandTint(float blockLevel, float d) {
    vec3 base = welkinLightColour(L_GENERIC);
#ifdef HANDHELD_LIGHT
    float h = welkinHandLevel(d);
    if (h <= 0.0) return base;
    int id = heldBlockLightValue >= heldBlockLightValue2 ? heldItemId : heldItemId2;
    int l = welkinLight(id);
    vec3 c = l == L_NONE ? base : welkinLightColour(l);
    return h >= blockLevel ? c : base;   // flat: the stronger source wins the pixel, no blend
#else
    return base;
#endif
}

#endif
