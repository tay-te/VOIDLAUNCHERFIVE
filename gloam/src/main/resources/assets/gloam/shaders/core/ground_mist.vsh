#version 330
#extension GL_ARB_separate_shader_objects : require

// VOID Gloam's ground mist (MistMote): vanilla's particle vertex shader, also handing on the vertex's place from the
// camera (particle vertices are already camera-relative), so the fragment shader can tell how flat the eye looks across
// the sheet.

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
layout(location = 4) out vec3 relative;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    texCoord0 = UV0;
#ifdef GLOAM_SOFT
    // the colour carries the sheet's size and seed, not a tint (MistMote); it is drawn in the mist palette, full bright
    vertexColor = Color;
#else
    vertexColor = vec4(Color.rgb * sample_lightmap(Sampler2, UV2).rgb, Color.a);
#endif
    relative = Position;
}
