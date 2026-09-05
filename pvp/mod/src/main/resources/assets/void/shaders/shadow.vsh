// void — surface shadow, vertex stage (§6.4).
// GLSL 1.20: Minecraft 1.8.9 is an OpenGL 2.1 context through LWJGL 2, and macOS
// offers nothing newer (§13). Fixed-function transform, matching blur.vsh, because
// the quad is drawn with the fixed-function matrices.
#version 120

varying vec2 v_pos;

void main() {
    // The quad's texture coordinates carry the fragment's position in the shadow's
    // own space (device pixels, origin at the element's centre), so the fragment
    // stage never needs the projection.
    v_pos = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
