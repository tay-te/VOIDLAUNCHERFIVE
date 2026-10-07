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
    // An uneven top: the mist heaps in slow billows and tongues and thins into holes, so the bank reads as air, not as
    // a lake's flat water. Three scales of drifting noise lift and sink the depth the eye looks through.
    vec2 world = FarNoise.zw + relative.xz;
    float drift = FarCamera.y * 0.8;
    float broad = texture(CloudNoise, (world + vec2(drift, drift * 0.3)) / 3072.0).g;
    float tongues = texture(CloudNoise, (world - vec2(drift * 0.6, 0.0)) / 768.0).b;
    float wisps = texture(CloudNoise, (world + vec2(0.0, drift)) / 192.0).a;
    float lumps = broad * 0.5 + tongues * 0.32 + wisps * 0.18;
    float deep = depth * smoothstep(0.22, 0.75, lumps) * 1.6;
    // thin at the margins, where the ground rises toward the mist's top
    deep *= smoothstep(0.0, 14.0, depth);
    // the eye's path through it, longer the lower it looks, but not without end: seen across, a bank stays a veil
    float across = min(deep / max(abs(dir.y), 0.05), deep * 6.0 + 60.0);
    float opacity = 1.0 - exp(-across * thickness * 0.06);
    // never quite opaque: treetops and the land show through faintly where it is thin
    opacity = min(opacity, mix(0.55, 0.9, smoothstep(10.0, 40.0, deep)));
    // in from where the chunks end (near mist is the sky's and the motes' to draw), out before the far air's edge
    float ground = length(relative.xz);
    opacity *= smoothstep(FarCamera.z * 0.9, FarCamera.z * 1.6, ground) * (1.0 - smoothstep(0.85 * FarCamera.w, FarCamera.w, ground));
    // Lit like the haze it is: a cool white-grey, brighter on its heaps and greyer in its hollows; toward a low sun it
    // takes the sun's warmth, away from it the sky's blue.
    float bright = max(max(FogColor.r, FogColor.g), FogColor.b);
    vec3 cool = vec3(bright) * vec3(0.9, 0.93, 0.98) * 1.08;
    vec3 color = mix(cool, FarCloudColor.rgb * vec3(0.93, 0.95, 1.0), 0.3) * mix(0.84, 1.08, lumps);
    float sunward = max(dot(normalize(dir.xz + vec2(1e-4, 0.0)), vec2(sign(FarSun.x), 0.0)), 0.0);
    vec3 warm = mix(vec3(1.0, 0.72, 0.5), FarSunrise.rgb, 0.5) * bright * 1.1;
    color = mix(color, warm, FarSunrise.a * sunward * sunward * 0.55);
    // far banks sink into the air like the land under them
    float haze = 1.0 - exp(-dist / FarAnchor.w);
    color = mix(color, FogColor.rgb, haze * 0.35 * (1.0 - FarMap.w));
    fragColor = vec4(min(color, vec3(1.0)), opacity * (1.0 - haze * 0.5 * FarMap.w));
}
