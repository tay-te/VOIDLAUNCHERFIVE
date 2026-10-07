#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <gloam:farinfo.glsl>
#include <gloam:pixel.glsl>

uniform sampler2D CloudNoise;

layout(location = 0) in vec3 relative;
layout(location = 1) in vec4 curtain;
layout(location = 2) flat in vec4 shape;

layout(location = 0) out vec4 fragColor;

// A curtain of rain under a far shower or storm, drawn pixel-true (docs/STYLE.md): stacked columns of pixel streaks on
// the curtain's own texel grid (coarser with distance), each column its own darkness and its streaks sliding down in
// whole texels as the rain falls; its opacity in three flat steps; its sides, its base where it reaches the ground and
// its top where it goes into the cloud dithered on the texel grid.
void main() {
    float u = curtain.x, v = curtain.y;
    float rain = curtain.z, storm = curtain.w;
    float width = shape.x, height = shape.y, seed = shape.z;
    // the curtain in blocks: across from its left edge, up from its foot
    vec2 blocks = vec2((u * 0.5 + 0.5) * width, v * height);
    vec2 fw = fwidth(blocks);
    float texel = texelBlocks(max(fw.x, fw.y));
    vec2 cell = floor(blocks / texel);
    ivec2 p = ivec2(mod(cell, 4096.0));
    vec2 centre = (cell + 0.5) * texel;
    float cu = centre.x / max(width, 1.0) * 2.0 - 1.0, cv = centre.y / max(height, 1.0);
    // ragged sides: the edge wanders up the curtain
    float wander = texture(CloudNoise, vec2(seed, cv * height / 1600.0)).r - 0.5;
    float side = 1.0 - smoothstep(0.3, 1.0, abs(cu + wander * 0.5));
    // columns a few texels wide, each its own darkness; streaks in them sliding down in whole texels
    float column = floor(centre.x / (texel * 3.0));
    float shade = fract(sin(column * 12.9898 + seed * 78.233) * 43758.5453);
    float fall = floor(FarCamera.y * 6.0);
    float streakRow = floor(cell.y + fall + shade * 37.0);
    float streak = step(0.45, fract(sin((column * 7.13 + floor(streakRow / 5.0)) * 91.7) * 4375.85));
    // densest under the cloud, thinner toward the ground; fading out at the foot and into the cloud at the top
    float up = smoothstep(0.0, 0.08, cv) * mix(0.75, 1.0, cv) * (1.0 - smoothstep(0.85, 1.0, cv));
    float opacity = rain * side * up * (0.5 + 0.25 * shade + 0.35 * streak);
    float dist = length(relative);
    opacity *= smoothstep(300.0, 900.0, dist) * (1.0 - smoothstep(0.8 * FarCamera.w, FarCamera.w, dist));
    // three flat steps, dithered between
    float level = steps(clamp(opacity * mix(1.2, 1.7, storm), 0.0, 1.0), 3.0, p);
    if (level <= 0.0) {
        discard;
    }
    // two greys: the curtain and its darker streaks, darker again under a storm
    vec3 light = mix(FogColor.rgb, FarCloudColor.rgb * 0.55, 0.65);
    vec3 color = light * (streak > 0.5 ? 0.82 : 1.0) * mix(1.0, 0.62, storm);
    float haze = floor((1.0 - exp(-dist / (FarAnchor.w * 1.6))) * 3.0) / 3.0;
    color = mix(color, FogColor.rgb, haze * 0.6);
    fragColor = vec4(color, mix(0.3, 0.8, level) * (1.0 - haze * 0.45));
}
