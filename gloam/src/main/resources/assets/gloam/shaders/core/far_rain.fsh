#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <gloam:farinfo.glsl>

uniform sampler2D CloudNoise;

layout(location = 0) in vec3 relative;
layout(location = 1) in vec4 curtain;
layout(location = 2) flat in vec4 shape;

layout(location = 0) out vec4 fragColor;

// A curtain of rain under a far shower or storm: a grey veil from the cloud base to the ground, heaviest in its middle,
// ragged at its sides, streaked and leaning with the wind, the streaks sliding down as it falls.
void main() {
    float u = curtain.x, v = curtain.y;
    float rain = curtain.z, storm = curtain.w;
    float width = shape.x, height = shape.y, seed = shape.z;
    // ragged sides: the edge wanders up the curtain
    float wander = texture(CloudNoise, vec2(seed, v * height / 1600.0)).r - 0.5;
    float side = 1.0 - smoothstep(0.25, 1.0, abs(u + wander * 0.5));
    // streaks, leaning with the wind, sliding down
    float lean = (u * width + v * height * 0.25) / 48.0;
    float streak = texture(CloudNoise, vec2(lean / 16.0 + seed, v * height / 2400.0 + FarCamera.y * 0.02)).a;
    float shafts = texture(CloudNoise, vec2(u * width / 4096.0 + seed * 3.0, 0.37)).g;
    // densest under the cloud, thinning a little toward the ground, and gone into the cloud's base at the top
    float up = smoothstep(0.0, 0.04, v) * mix(0.7, 1.0, v) * (1.0 - smoothstep(0.88, 1.0, v));
    float opacity = rain * side * up * (0.45 + 0.35 * streak + 0.4 * shafts);
    float dist = length(relative);
    // the rain at the camera is vanilla's; the curtains begin a little way off, and fade before the far air's edge
    opacity *= smoothstep(300.0, 900.0, dist) * (1.0 - smoothstep(0.8 * FarCamera.w, FarCamera.w, dist));
    // grey, between the haze and the cloud's underside, darker under a storm
    vec3 color = mix(FogColor.rgb, FarCloudColor.rgb * 0.5, 0.7) * mix(1.0, 0.6, storm);
    float haze = 1.0 - exp(-dist / (FarAnchor.w * 1.6));
    color = mix(color, FogColor.rgb, haze * 0.6);
    fragColor = vec4(color, clamp(opacity * mix(1.1, 1.6, storm), 0.0, 0.85) * (1.0 - haze * 0.45));
}
