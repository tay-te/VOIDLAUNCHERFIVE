#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <void_lod:lodinfo.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 horizontal;

layout(location = 0) out vec4 fragColor;

#ifdef LOD_CLIP
// A 4x4 ordered dither: the seam to vanilla's chunks fades in over LodClip.y blocks instead of a hard line.
float bayer4(vec2 p) {
    ivec2 i = ivec2(p) & 3;
    int m[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return (float(m[i.x + i.y * 4]) + 0.5) / 16.0;
}
#endif

void main() {
#ifdef LOD_CLIP
    // Only the tiles that reach into vanilla's area use this variant: everywhere else the shader has no
    // discard, so the GPU keeps its early depth test for the bulk of the LOD.
    float d = length(horizontal);
    float t = (d - LodClip.x) / max(LodClip.y, 1.0);
    if (t < bayer4(gl_FragCoord.xy)) {
        discard;
    }
#endif
    fragColor = apply_fog(vertexColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
