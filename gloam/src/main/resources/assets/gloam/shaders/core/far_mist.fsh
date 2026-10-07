#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <gloam:farinfo.glsl>

uniform sampler2D CloudNoise;

layout(location = 0) in vec3 relative;
layout(location = 1) in vec2 mist;

layout(location = 0) out vec4 fragColor;

// The top of a bank of mist lying in a far valley. What shows is the mist the eye looks down through to the ground: as
// deep as it lies there, longer the lower the eye looks across it, so a bank is a thin veil seen from above and a white
// sea seen across; where the ground rises out of it the depth runs out and the bank's edge goes soft against the
// slope. The ground itself (and every ridge standing out of it) is in the depth buffer and covers the sheet.
void main() {
    float depth = mist.x;
    float thickness = mist.y;
    if (depth <= 0.05 || thickness <= 0.005) {
        discard;
    }
    float dist = length(relative);
    vec3 dir = relative / max(dist, 1e-3);
    float across = depth / max(abs(dir.y), 0.04);
    // drifting, uneven: it lies in tongues and thins in patches
    vec2 world = FarNoise.zw + relative.xz;
    float drift = FarCamera.y * 0.8;
    float n = texture(CloudNoise, (world + vec2(drift, drift * 0.3)) / 2048.0).g * 0.6
        + texture(CloudNoise, (world - vec2(drift * 0.6, 0.0)) / 512.0).b * 0.4;
    float opacity = 1.0 - exp(-across * thickness * 0.045 * (0.35 + 1.3 * n));
    // in from where the chunks end (near mist is the sky's and the motes' to draw), out before the far air's edge
    float ground = length(relative.xz);
    opacity *= smoothstep(FarCamera.z * 0.9, FarCamera.z * 1.6, ground) * (1.0 - smoothstep(0.85 * FarCamera.w, FarCamera.w, ground));
    // Lit like the haze it is: the horizon's colour, whiter where the light falls on it, warm toward a low sun.
    vec3 color = mix(FogColor.rgb, FarCloudColor.rgb, 0.6);
    float sunward = max(dot(normalize(dir.xz + vec2(1e-4, 0.0)), vec2(sign(FarSun.x), 0.0)), 0.0);
    color = mix(color, FarSunrise.rgb * max(max(color.r, color.g), color.b), FarSunrise.a * sunward * 0.5);
    // far banks sink into the air like the land under them
    float haze = 1.0 - exp(-dist / FarAnchor.w);
    color = mix(color, FogColor.rgb, haze * 0.6);
    fragColor = vec4(color, opacity * 0.92);
}
