// void — cosmetics bloom: composite. The quarter-size level (which the wider levels have already
// been added into) through a soft knee, so a crowd of glows saturates gracefully instead of
// clipping to white, added onto the scene (the blend is ONE, ONE).
#version 120

uniform sampler2D u_tex;
uniform float u_gain;

varying vec2 v_uv;

void main() {
    vec3 c = texture2D(u_tex, v_uv).rgb;
    gl_FragColor = vec4(vec3(1.0) - exp(-c * u_gain), 1.0);
}
