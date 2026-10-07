#version 330
#extension GL_ARB_separate_shader_objects : require

// VOID Gloam's own lights (fireflies): vanilla's particle vertex shader without the lightmap, so a light shows at its
// own colour however dark the night, the sky light or the ambient colour.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV2;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    texCoord0 = UV0;
    // the lightmap is read (the pipeline binds it) but never darkens: max() keeps it at least white
    vertexColor = Color * min(max(sample_lightmap(Sampler2, UV2), vec4(1.0)), vec4(1.0));
}
