#version 330
#extension GL_ARB_separate_shader_objects : require

// A sheet of ground mist lying flat (MistMote), drawn as the far mist over VOID LOD is (far_mist.fsh), so the two meet
// without a seam: what shows is the mist the eye looks through, the slab's depth (the vertex alpha, as optical depth)
// divided by how steeply the eye looks down or up through it, so a sheet is a faint veil seen from above and a white
// band seen across. Coloured like the far banks: the fog's colour leaning to the air's own (the vertex colour).

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef GLOAM_SOFT
// Soft sheets (SoftMist): the depth of the scene behind, copied out of the main pass, so a sheet thins away where the
// ground comes up close behind it instead of ending in a straight line along a slope or a step.
#include <minecraft:projection.glsl>
uniform sampler2D SceneDepth;

// The distance along the view axis to a depth buffer value (the game's projection, reversed or not).
float viewDistance(float depth) {
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    float ndc = depth;
#else
    float ndc = depth * 2.0 - 1.0;
#endif
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}
#endif

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
    float steep = abs(relative.y) / max(dist, 1e-3);
    float edgeOn = smoothstep(0.35, 0.12, steep);
    // capped, so a single sheet seen dead edge-on does not turn into a hard white line
    float across = 1.0 / max(steep, 0.1);
    // Looked down into from above, a few metres of mist (however dense the air says it is) are a thin veil the ground
    // shows through, not a floor: the slab's depth is eased toward a ceiling as the eye looks down more steeply, and
    // kept whole when seen across, where the long path through it makes the white band.
    float tau = vertexColor.a;
    float eased = 0.35 * (1.0 - exp(-tau / 0.35));
    tau = mix(eased, tau, edgeOn);
    float opacity = (1.0 - exp(-tau * across)) * sheet;
    // a sheet level with the eye is seen dead edge-on all the way out, a ruled line across the land however soft its
    // edges: let it go there, the sheets above and below carry the mist at that height (above the eye, seen nearly
    // edge-on, over a wider band: a sheet overhead seen far off from under it is a streak hanging in the air)
    opacity *= smoothstep(0.1, mix(0.8, 2.0, relative.y > 0.0 ? edgeOn : 0.0), abs(relative.y));
#ifdef GLOAM_SOFT
    float here = viewDistance(gl_FragCoord.z);
    float behind = viewDistance(texelFetch(SceneDepth, ivec2(gl_FragCoord.xy), 0).r);
    // how far the scene lies behind the sheet: nothing within a couple of blocks (more far off, where a block is a few
    // pixels), and the whole sheet beyond; nothing behind at all (the sky) counts as far
    float gap = behind > 0.0 ? behind - here : 1e6;
    // (looked down into, nothing meets the ground edge-on: only the last block fades, so low sheets keep showing)
    float soft = mix(1.0, 2.0 + 0.04 * here, edgeOn);
    float fade = smoothstep(0.0, 1.0, clamp(gap / soft, 0.0, 1.0));
    opacity *= fade;
#endif
    if (opacity < 0.003) {
        discard;
    }
    vec3 color = mix(FogColor.rgb, vertexColor.rgb, 0.6);
    fragColor = apply_fog(vec4(color, opacity * 0.92), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
