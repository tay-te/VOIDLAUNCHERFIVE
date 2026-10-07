#version 330
#extension GL_ARB_separate_shader_objects : require

// A sheet of ground mist lying flat (MistMote), drawn pixel-true (docs/STYLE.md). What shows is the mist the eye looks
// through: the slab's depth (the vertex alpha, as optical depth) divided by how steeply the eye looks down or up through
// it, so a sheet is a thin veil seen from above and a white band seen across, as the far mist over VOID LOD is drawn.
//
// With GLOAM_SOFT (SoftMist, the usual case without a shader pack) the sheet is drawn on the world's own texel grid:
// 16 texels a block near the camera (8, 4, 2 further off), its opacity in four flat steps with a Bayer 4x4 dither on
// that grid between them (the rims, the top of the bank, where it thins into the ground behind it), and its colour
// picked from a four-tone mist palette. The sheet's place and size come in the vertex data (MistMote): the texture
// coordinate is its centre (world x, z modulo 256) and the colour carries its half-width and a seed.
// Without it (improved transparency), the stepped mist sprite is drawn as it is, its opacity stepped the same way.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in vec3 relative;

layout(location = 0) out vec4 fragColor;

// How many opacity steps above nothing, and how much of the way between two the dither spans (the rest is flat).
const float LEVELS = 4.0;
const float DITHER = 0.7;

/** How opaque the slab is along the eye's path through it (0..1), from its optical depth looked straight through. */
float pathOpacity(float tau, float steep, float edgeOn) {
    // capped, so a single sheet seen dead edge-on does not turn into a solid line
    float across = 1.0 / max(steep, 0.1);
    // Looked down into from above, a few metres of mist are a thin veil the ground shows through, not a floor: the
    // slab's depth is eased toward a ceiling as the eye looks down more steeply, and kept whole when seen across.
    float eased = 0.35 * (1.0 - exp(-tau / 0.35));
    return 1.0 - exp(-mix(eased, tau, edgeOn) * across);
}

/** How much of a sheet near the eye's own height shows (a sheet level with the eye is a ruled line out to the horizon). */
float eyeLevel(float dy, float edgeOn) {
    // above the eye, seen nearly edge-on, over a wider band: a sheet overhead seen far off is a streak hanging in the air
    return smoothstep(0.1, mix(0.8, 2.0, dy > 0.0 ? edgeOn : 0.0), abs(dy));
}

#ifdef GLOAM_SOFT
#include <minecraft:projection.glsl>

// The depth of the scene behind, copied out of the main pass (SoftMist).
uniform sampler2D SceneDepth;

layout(std140) uniform GloamMist {
    // xyz: the camera's world position modulo 256 (as the sheets' centres are)
    vec4 MistCamera;
    // rgb: the clouds' sunlit colour, which the mist's light tones lean to (as the far banks do)
    vec4 MistCloud;
};

const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

float bayer(ivec2 t) {
    return (BAYER[(t.y & 3) * 4 + (t.x & 3)] + 0.5) / 16.0;
}

// The distance along the view axis to a depth buffer value (the game's projection, reversed or not).
float viewDistance(float depth) {
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    float ndc = depth;
#else
    float ndc = depth * 2.0 - 1.0;
#endif
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

float hash(ivec2 p) {
    uint h = uint(p.x) * 374761393u + uint(p.y) * 668265263u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return float((h ^ (h >> 16u)) & 0xffffu) / 65535.0;
}

/** Value noise with a lattice cell of one unit of p, smooth between its corners. */
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    ivec2 c = ivec2(i);
    float a = hash(c), b = hash(c + ivec2(1, 0)), d = hash(c + ivec2(0, 1)), e = hash(c + ivec2(1, 1));
    return mix(mix(a, b, f.x), mix(d, e, f.x), f.y);
}

void main() {
    vec3 world = relative + MistCamera.xyz;
    float dist = length(relative);
    // the texel grid: 16 a block near, coarser far off where a block is a few pixels (never a smooth blur)
    float density = 16.0 / exp2(clamp(floor(log2(max(dist, 1.0) / 24.0)), 0.0, 3.0));
    vec2 texel = floor(world.xz * density);
    vec2 at = (texel + 0.5) / density;
    // the sheet around its centre, by the texel: a round patch broken up by noise on five- and one-and-a-half-block
    // lattices, so it reads as a ragged bank and not a disc
    float size = max(vertexColor.r * 40.0, 1.0);
    float seed = floor(vertexColor.g * 255.0 + 0.5);
    vec2 d = at - texCoord0;
    d -= 256.0 * floor(d / 256.0 + 0.5);
    float r = length(d) / size;
    if (r > 0.97) {
        discard;
    }
    float ragged = (valueNoise(at / 5.0 + seed * 7.31) - 0.5) * 0.8 + (valueNoise(at / 1.5 + seed * 1.93) - 0.5) * 0.3;
    float shape = 1.0 - smoothstep(0.15, 0.95, r + ragged);
    float grain = valueNoise(at / 2.0 + seed * 3.17);
    float body = 0.5 + 0.5 * grain;

    float steep = abs(relative.y) / max(dist, 1e-3);
    float edgeOn = smoothstep(0.35, 0.12, steep);
    float v = shape * body * pathOpacity(vertexColor.a, steep, edgeOn) * eyeLevel(relative.y, edgeOn);
    // the mist at one's feet is seen through, not looked at: within a few blocks it thins away (in the same steps)
    v *= smoothstep(1.0, 4.5, dist);
    // where the scene comes up close behind the sheet (a slope or a step it runs into), it thins away into it, in the
    // same dithered steps; looked down into, nothing meets the ground edge-on, so only the last block fades
    float here = viewDistance(gl_FragCoord.z);
    float behind = viewDistance(texelFetch(SceneDepth, ivec2(gl_FragCoord.xy), 0).r);
    float gap = behind > 0.0 ? behind - here : 1e6;
    v *= clamp(gap / mix(1.0, 2.0 + 0.04 * here, edgeOn), 0.0, 1.0);

    // a few flat steps, an ordered dither on the world's texel grid where one meets the next (flat between)
    float k = min(floor(v * LEVELS + 0.5 + (bayer(ivec2(texel)) - 0.5) * DITHER), LEVELS);
    if (k < 1.0) {
        discard;
    }
    // the mist palette, the fog's shadow tone to near-white: thin steps in the mid tone, denser ones lighter, the
    // grain shifting a texel a tone (down to the shadow tone only in the thinnest)
    vec3 light = mix(FogColor.rgb, MistCloud.rgb, 0.6);
    vec3 tones[4] = vec3[4](mix(light, FogColor.rgb, 0.5), mix(light, FogColor.rgb, 0.2), light, min(light * 1.07 + 0.03, vec3(1.0)));
    int tone = clamp(int(k) + (grain > 0.7 ? 1 : 0) - (grain < 0.3 ? 1 : 0), 0, 3);
    fragColor = apply_fog(vec4(tones[tone], k / LEVELS * 0.9), sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

#else

void main() {
    float sheet = texture(Sampler0, texCoord0).a * ColorModulator.a;
    if (sheet < 0.004) {
        discard;
    }
    float dist = length(relative);
    float steep = abs(relative.y) / max(dist, 1e-3);
    float edgeOn = smoothstep(0.35, 0.12, steep);
    float v = sheet * pathOpacity(vertexColor.a, steep, edgeOn) * eyeLevel(relative.y, edgeOn);
    // the sprite's own steps and dither carry the edges; the opacity it is drawn with is stepped too
    float k = min(floor(v * LEVELS + 0.5), LEVELS);
    if (k < 1.0) {
        discard;
    }
    vec3 color = mix(FogColor.rgb, vertexColor.rgb, 0.6);
    fragColor = apply_fog(vec4(color, k / LEVELS * 0.9), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

#endif
