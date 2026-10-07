#version 330
#extension GL_ARB_separate_shader_objects : require

// VOID Gloam's sunrise and sunset fan (the vanilla path, no shader pack): vanilla's glow round the low sun, which fades
// smoothly from its middle to its rim, drawn in four flat steps of strength instead (docs/STYLE.md).

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

const float STEPS = 4.0;

void main() {
    float a = ceil(vertexColor.a * STEPS - 0.5) / STEPS;
    if (a <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, a) * ColorModulator;
}
