#version 330
#extension GL_ARB_separate_shader_objects : require

// A light in the air (fireflies), added onto what is behind it (the pipeline blends additively), fading out into the fog
// with distance instead of turning the fog's colour.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd);
    float alpha = color.a * (1.0 - fog);
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(color.rgb, alpha);
}
