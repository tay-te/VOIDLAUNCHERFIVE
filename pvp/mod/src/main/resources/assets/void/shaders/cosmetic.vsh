// void — cosmetics material shader, vertex stage (cosmetic/CosmeticShader).
// GLSL 1.20 on Minecraft 1.8.9's OpenGL 2.1 context: the fixed-function matrices, lights,
// lightmap coordinate (unit 1, set as a current attribute by the entity renderer) and fog are
// read straight from the built-ins, so a cosmetic is lit by exactly the state the skin under it
// was lit by.
#version 120

// Contact occlusion: the wearer's torso or head as a box in its own pixel space (both matrices
// set once per player). A vertex near it, facing it, gets less light — wing roots, a hat's brim
// over the head, a cape's lining where it lies on the back.
uniform mat4 u_eyeToBody;
uniform mat4 u_eyeToHead;
uniform float u_occluder;  // 0 none, 1 torso, 2 head
// Material: from the second texture coordinate (models, per cube) or the uniform (capes).
uniform float u_matSrc;    // 1 = vertex
uniform float u_mat;

varying vec2 v_uv;
varying vec2 v_lm;
varying vec3 v_n;
varying vec3 v_pos;
varying vec4 v_col;
varying float v_mat;
varying float v_ao;

void main() {
    vec4 eye = gl_ModelViewMatrix * gl_Vertex;
    v_pos = eye.xyz;
    gl_Position = gl_ProjectionMatrix * eye;
    vec3 n = normalize(gl_NormalMatrix * gl_Normal);
    v_n = n;
    v_uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    v_lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
    v_col = gl_Color;
    v_mat = u_matSrc > 0.5 ? gl_MultiTexCoord2.s : u_mat;
    v_ao = 1.0;
    if (u_occluder > 0.5) {
        bool head = u_occluder > 1.5;
        mat4 m = head ? u_eyeToHead : u_eyeToBody;
        vec3 lo = head ? vec3(-4.0, -8.0, -4.0) : vec3(-4.0, 0.0, -2.0);
        vec3 hi = head ? vec3(4.0, 0.0, 4.0) : vec3(4.0, 12.0, 2.0);
        float radius = head ? 1.6 : 2.6;
        float strength = head ? 0.55 : 0.55;
        vec3 b = (m * eye).xyz;
        vec3 d = clamp(b, lo, hi) - b;
        float dist = length(d);
        vec3 nb = normalize(mat3(m) * n);
        float facing = dist > 0.001 ? max(dot(nb, d / dist), 0.0) : 1.0;
        float occ = 1.0 - smoothstep(0.0, radius, dist);
        v_ao = 1.0 - strength * occ * mix(0.15, 1.0, facing);
    }
}
