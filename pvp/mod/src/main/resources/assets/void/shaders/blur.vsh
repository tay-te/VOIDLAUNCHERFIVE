// void — menu backdrop blur, vertex stage.
// GLSL 1.20: Minecraft 1.8.9 runs on an OpenGL 2.1 context through LWJGL 2,
// and macOS gives us nothing newer (§13). Fixed-function transform, because
// the quad is drawn with the fixed-function matrices.
#version 120

varying vec2 v_uv;

void main() {
    v_uv = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
