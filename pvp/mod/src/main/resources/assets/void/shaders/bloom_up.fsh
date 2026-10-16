// void — cosmetics bloom: dual-filter upsample (Bjorge, "Bandwidth-efficient rendering"). Eight
// bilinear taps on a diamond round the target texel, the diagonals weighted double: a wide, round
// blur for the cost of one pass, scaled by u_w and added onto the level above (blend ONE, ONE).
#version 120

uniform sampler2D u_tex;
uniform vec2 u_hp;      // half a source texel, in uv
uniform float u_w;

varying vec2 v_uv;

void main() {
    vec3 c = texture2D(u_tex, v_uv + vec2(-u_hp.x * 2.0, 0.0)).rgb
           + texture2D(u_tex, v_uv + vec2(-u_hp.x, u_hp.y)).rgb * 2.0
           + texture2D(u_tex, v_uv + vec2(0.0, u_hp.y * 2.0)).rgb
           + texture2D(u_tex, v_uv + vec2(u_hp.x, u_hp.y)).rgb * 2.0
           + texture2D(u_tex, v_uv + vec2(u_hp.x * 2.0, 0.0)).rgb
           + texture2D(u_tex, v_uv + vec2(u_hp.x, -u_hp.y)).rgb * 2.0
           + texture2D(u_tex, v_uv + vec2(0.0, -u_hp.y * 2.0)).rgb
           + texture2D(u_tex, v_uv + vec2(-u_hp.x, -u_hp.y)).rgb * 2.0;
    gl_FragColor = vec4(c * (u_w / 12.0), 1.0);
}
