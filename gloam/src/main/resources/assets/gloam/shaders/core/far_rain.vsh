#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <gloam:farinfo.glsl>

// A corner of a rain curtain (FarRain): its place from the camera; across it (-1..1) and up it (0 ground .. 1 cloud
// base), how hard it rains and the storm in it; its width and height in blocks and a seed of its own.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Curtain;
layout(location = 2) in vec4 Shape;

layout(location = 0) out vec3 relative;
layout(location = 1) out vec4 curtain;
layout(location = 2) flat out vec4 shape;

void main() {
    relative = Position;
    gl_Position = ProjMat * FarModelView * vec4(Position, 1.0);
    curtain = Curtain;
    shape = Shape;
}
