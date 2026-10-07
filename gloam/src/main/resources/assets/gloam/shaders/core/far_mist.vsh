#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <gloam:farinfo.glsl>

// A vertex of the mist's top (FarMist): its place from the mesh's anchor, and how deep the mist lies under it (in 48ths
// of 48 blocks, 0 where the ground stands above it) and how thick it is there.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 MistData;

layout(location = 0) out vec3 relative;
layout(location = 1) out vec2 mist;

void main() {
    relative = Position - FarAnchor.xyz;
    gl_Position = ProjMat * FarModelView * vec4(relative, 1.0);
    mist = vec2(MistData.x * 48.0, MistData.y);
}
