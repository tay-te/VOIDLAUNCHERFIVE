#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <gloam:farinfo.glsl>
#include <gloam:pixel.glsl>

uniform sampler2D CloudNoise;

layout(location = 0) in vec3 relative;
layout(location = 1) in vec2 mist;

layout(location = 0) out vec4 fragColor;

// The top of a bank of mist lying in a far valley, drawn pixel-true (docs/STYLE.md): world-space texels (16 a block near,
// coarser with distance), each texel one flat value. What a texel shows is the mist the eye looks down through to the
// ground: as deep as it lies there, longer the lower the eye looks across it. That comes out in four opacity steps, a
// bank's thin top and its margins against the slopes dithered between them, so far valley mist reads as stepped pixel
// fog banks rather than a smooth sheet. The ground (and every ridge standing out of it) is in the depth buffer and covers
// the sheet.
void main() {
    vec2 world = FarNoise.zw + relative.xz;
    // the texel: as fine as the screen allows, in whole powers of two of a block
    vec2 fw = fwidth(world);
    float texel = texelBlocks(max(fw.x, fw.y));
    vec2 cell = floor(world / texel);
    vec2 centre = (cell + 0.5) * texel;
    // the varyings at the texel's middle, from their slopes across the ground, so each texel is one flat value
    mat2 m = mat2(dFdx(world), dFdy(world));
    float det = determinant(m);
    vec2 toCentre = centre - world;
    vec2 gDepth = vec2(0.0), gThick = vec2(0.0);
    if (abs(det) > 1e-9) {
        mat2 inv = inverse(m);
        gDepth = vec2(dFdx(mist.x), dFdy(mist.x)) * inv;
        gThick = vec2(dFdx(mist.y), dFdy(mist.y)) * inv;
    }
    float depth = mist.x + dot(gDepth, toCentre);
    float thickness = mist.y + dot(gThick, toCentre);
    ivec2 p = ivec2(mod(cell, 4096.0));
    if (depth <= 0.05 || thickness <= 0.005) {
        discard;
    }
    float dist = length(relative);
    vec3 dir = relative / max(dist, 1e-3);
    // an uneven top: the mist heaps in slow billows and tongues and thins into holes, sampled nearest at the texel
    float drift = FarCamera.y * 0.8;
    vec2 at = centre;
    float broad = texture(CloudNoise, (at + vec2(drift, drift * 0.3)) / 3072.0).g;
    float tongues = texture(CloudNoise, (at - vec2(drift * 0.6, 0.0)) / 768.0).b;
    float lumps = broad * 0.6 + tongues * 0.4;
    float deep = depth * smoothstep(0.25, 0.7, lumps) * 1.6 * smoothstep(0.0, 14.0, depth);
    float across = min(deep / max(abs(dir.y), 0.05), deep * 6.0 + 60.0);
    float opacity = 1.0 - exp(-across * thickness * 0.06);
    // in from where the chunks end, out before the far air's edge
    float ground = length(relative.xz);
    opacity *= smoothstep(FarCamera.z * 0.9, FarCamera.z * 1.6, ground) * (1.0 - smoothstep(0.85 * FarCamera.w, FarCamera.w, ground));
    // four flat steps, never quite opaque, the steps between dithered on the texel grid
    float level = steps(opacity * 0.95, 4.0, p);
    if (level <= 0.0) {
        discard;
    }
    // The mist's palette: near-white on its heaps, a light blue-grey, the shadow tone in its hollows; on the low sun's
    // side of the sky a warm cream and peach instead. Scaled by the daylight, chosen per texel, never blended.
    float bright = max(max(FogColor.r, FogColor.g), FogColor.b);
    float sunward = max(dot(normalize(dir.xz + vec2(1e-4, 0.0)), vec2(sign(FarSun.x), 0.0)), 0.0);
    bool warm = FarSunrise.a * sunward * sunward > 0.18 + 0.2 * bayer4(p.yx);
    int tone = int(clamp(floor(lumps * 3.0 + bayer4(p + 2) * 0.8), 0.0, 2.0));
    vec3 cool[3] = vec3[3](vec3(0.72, 0.76, 0.84), vec3(0.85, 0.88, 0.93), vec3(0.97, 0.98, 1.0));
    vec3 sunny[3] = vec3[3](vec3(0.86, 0.72, 0.66), vec3(0.98, 0.85, 0.72), vec3(1.0, 0.95, 0.84));
    vec3 color = (warm ? sunny[tone] : cool[tone]) * min(1.0, bright * 1.1);
    // far banks sink into the air in two steps, like the land under them
    float haze = floor((1.0 - exp(-dist / FarAnchor.w)) * 3.0) / 3.0;
    color = mix(color, FogColor.rgb, haze * 0.4 * (1.0 - FarMap.w));
    fragColor = vec4(color, mix(0.25, 0.92, level) * (1.0 - haze * 0.4 * FarMap.w));
}
