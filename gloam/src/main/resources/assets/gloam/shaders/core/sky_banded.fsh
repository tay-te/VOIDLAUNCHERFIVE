#version 330
#extension GL_ARB_separate_shader_objects : require

// VOID Gloam's sky disc (the vanilla path, no shader pack): vanilla's sky, whose colour fades into the fog colour
// toward the horizon, drawn in a few flat bands instead of a smooth gradient (docs/STYLE.md: steps, not gradients).

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;

layout(location = 0) out vec4 fragColor;

// how many bands the sky has between its zenith colour and the fog colour at the horizon
const float BANDS = 5.0;

void main() {
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd);
    // the band this pixel falls in: the zenith keeps the sky's own colour, the last band is the horizon's
    float band = min(floor(fog * BANDS) / (BANDS - 1.0), 1.0);
    fragColor = vec4(mix(ColorModulator.rgb, FogColor.rgb, band * FogColor.a), ColorModulator.a);
}
