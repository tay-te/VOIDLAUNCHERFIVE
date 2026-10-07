#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <gloam:farinfo.glsl>

// One triangle over the whole screen; each corner carries the point on the near plane it looks through, relative to the
// camera, so every pixel knows its ray. Homogeneous, divided per pixel, so the interpolation stays exact.
layout(location = 0) out vec4 nearPoint;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    vec2 ndc = uv * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
    // reversed depth: the near plane is at 1
    nearPoint = inverse(ProjMat * FarModelView) * vec4(ndc, 1.0, 1.0);
}
