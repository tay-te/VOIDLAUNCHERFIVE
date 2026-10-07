#version 330
#extension GL_ARB_separate_shader_objects : require

// A sheet of ground mist lying flat (MistMote), drawn as the far mist over VOID LOD is (far_mist.fsh), so the two meet
// without a seam: what shows is the mist the eye looks through, the slab's depth (the vertex alpha, as optical depth)
// divided by how steeply the eye looks down or up through it, so a sheet is a faint veil seen from above and a white
// band seen across. Coloured like the far banks: the fog's colour leaning to the air's own (the vertex colour).

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in vec3 relative;

layout(location = 0) out vec4 fragColor;

void main() {
    float sheet = texture(Sampler0, texCoord0).a * ColorModulator.a;
    if (sheet < 0.004) {
        discard;
    }
    float dist = length(relative);
    // capped, so a single sheet seen dead edge-on does not turn into a hard white line
    float across = 1.0 / max(abs(relative.y) / max(dist, 1e-3), 0.1);
    float opacity = (1.0 - exp(-vertexColor.a * across)) * sheet;
    if (opacity < 0.003) {
        discard;
    }
    vec3 color = mix(FogColor.rgb, vertexColor.rgb, 0.6);
    fragColor = apply_fog(vec4(color, opacity * 0.92), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
