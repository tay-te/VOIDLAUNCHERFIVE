// VOID Welkin sway: wind movement for leaves and plants, driven by the material class's sway kind.
// Call from gbuffers_terrain.vsh AND shadow.vsh with the same inputs so shadows move with the leaves.
//
//   vec3 off = welkinSway(m, worldPos, at_midBlock.xyz, frameTimeCounter, wind);
//   worldPos += off;      // worldPos = absolute block-space position (playerPos + cameraPosition)
//
// wind: void_wind when the bridge is live (unit x, unit z, blocks per second, gust 0..1),
//       otherwise welkinWindFallback(rainStrength).
// Cost: one sin/cos pair and a handful of multiplies per vertex, only for swaying classes.
// Needs lib/settings.glsl and lib/materials.glsl first.
#ifndef WELKIN_SWAY
#define WELKIN_SWAY

vec4 welkinWindFallback(float rain) {
    return vec4(0.8, 0.6, 2.5 + 6.0 * rain, 0.25 + 0.5 * rain);
}

// at_midBlock.xyz is (block centre - vertex) * 64, so a vertex above the centre has y < 0.
bool welkinTopVertex(vec3 midBlock)    { return midBlock.y < 0.0; }

vec3 welkinSway(int m, vec3 worldPos, vec3 midBlock, float t, vec4 wind) {
    int kind = matSway(m);
    if (kind == SWAY_NONE) return vec3(0.0);
#if SWAY_FREEZE != 0
    t = float(SWAY_FREEZE); // (Look D2) a test clock: the sway held, so a shot repeats (lib/settings.glsl)
#endif
#ifndef WAVING_LEAVES
    if (kind == SWAY_LEAVES) return vec3(0.0);
#endif
#ifndef WAVING_PLANTS
    if (kind == SWAY_PLANT || kind == SWAY_TALL_LOWER || kind == SWAY_TALL_UPPER || kind == SWAY_HANGING ||
        kind == SWAY_STALK || kind == SWAY_WALL || kind == SWAY_CROP) return vec3(0.0);
#endif
#ifndef WAVING_WATER_PLANTS
    if (kind == SWAY_UNDERWATER || kind == SWAY_FLOAT) return vec3(0.0);
#endif
    vec2 dir = normalize(wind.xy + vec2(1e-4, 0.0));
    float speed = clamp(wind.z, 0.0, 20.0);
    // Phase runs along the wind, so gusts visibly roll across a field.
    float phase = dot(worldPos.xz, dir) * 0.35 + worldPos.y * 0.2 - t * (0.9 + 0.12 * speed);
    float gust = 1.0 + wind.w * sin(phase * 0.23 - t * 0.31);
    // Amplitude in blocks: at most 2 texture pixels, so blocks never look rubbery.
    float amp = min((0.025 + 0.010 * speed) * gust * WAVE_STRENGTH, 0.125);
    float s = sin(phase), c = cos(phase * 1.3 + 1.7);
    vec3 lean = vec3(dir.x, 0.0, dir.y) * (0.6 + 0.4 * s) + vec3(-dir.y, 0.0, dir.x) * 0.3 * c;

    bool top = welkinTopVertex(midBlock);
    float w = 0.0;
    if (kind == SWAY_LEAVES)          { return (lean * 0.55 + vec3(0.0, 0.25 * c, 0.0)) * amp; }
    else if (kind == SWAY_PLANT)      w = top ? 1.0 : 0.0;
    else if (kind == SWAY_CROP)       w = top ? 0.6 : 0.0;
    else if (kind == SWAY_TALL_LOWER) w = top ? 0.5 : 0.0;
    else if (kind == SWAY_TALL_UPPER) w = top ? 1.0 : 0.5;
    else if (kind == SWAY_HANGING)    w = top ? 0.0 : 0.8;
    else if (kind == SWAY_STALK)      w = top ? 0.35 : 0.0;
    else if (kind == SWAY_WALL)       w = 0.25;
    else if (kind == SWAY_UNDERWATER) {
        // Slow current, not wind: kelp keeps moving on a still day.
        float u = sin(dot(worldPos.xz, vec2(0.31, 0.27)) - t * 0.8);
        return vec3(u, 0.0, 0.6 * cos(u + t * 0.5)) * (top ? 0.08 : 0.0) * WAVE_STRENGTH;
    } else if (kind == SWAY_FLOAT) {
        return vec3(0.02 * c, 0.015 * s, 0.02 * s) * WAVE_STRENGTH;
    }
    return lean * amp * w;
}

#endif
