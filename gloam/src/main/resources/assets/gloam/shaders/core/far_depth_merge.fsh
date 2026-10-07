#version 330
#extension GL_ARB_separate_shader_objects : require

// The far land's depth as the shader pack drew it through VOID LOD (its own perspective, reversed: 0 where there is no
// far land), and the matrix from that perspective to the game's.
uniform sampler2D FarDepth;

layout(std140) uniform GloamFarDepth {
    // the LOD's clip space (OpenGL's -1..1 depth) to the game's clip space
    mat4 FarToMain;
    // xy: the far depth's size over the screen's
    vec4 FarDepthScale;
};

layout(location = 0) in vec2 texCoord;

// Writes the far land into the main depth buffer once the pack has finished its picture, in the game's perspective, so
// the far air drawn over the picture next is hidden behind far ridges as it is behind the chunks.
void main() {
    float stored = texelFetch(FarDepth, ivec2(gl_FragCoord.xy * FarDepthScale.xy), 0).r;
    if (stored <= 0.0) {
        discard;
    }
    vec4 clip = FarToMain * vec4(texCoord * 2.0 - 1.0, 1.0 - 2.0 * stored, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w, 0.0, 1.0);
}
