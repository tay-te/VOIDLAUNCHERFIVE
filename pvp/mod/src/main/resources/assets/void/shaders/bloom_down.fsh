// void — cosmetics bloom: downsample.
//
// From the full-size mask (u_peak = 1): sixteen point taps over the 4x4 block one target texel
// covers, kept as a blend of their mean and their brightest. Cosmetic light is pixel art — a
// one-pixel star, a two-pixel seam — and a plain average would divide it by sixteen before the
// blur ever saw it; keeping the peak lets a small light bloom like a small light.
// Between levels (u_peak = 0): the dual-filter downsample — the centre four times and the four
// diagonals half a target texel out, a round 2x reduction that already blurs.
#version 120

uniform sampler2D u_tex;
uniform vec2 u_texel;   // one source texel, in uv
uniform float u_peak;

varying vec2 v_uv;

void main() {
    if (u_peak > 0.5) {
        vec3 sum = vec3(0.0), peak = vec3(0.0);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                vec3 c = texture2D(u_tex, v_uv + u_texel * vec2(float(x) - 1.5, float(y) - 1.5)).rgb;
                sum += c;
                peak = max(peak, c);
            }
        }
        gl_FragColor = vec4(mix(sum * 0.0625, peak, 0.55), 1.0);
        return;
    }
    vec3 c = texture2D(u_tex, v_uv).rgb * 4.0
           + texture2D(u_tex, v_uv + u_texel * vec2(-1.0, -1.0)).rgb
           + texture2D(u_tex, v_uv + u_texel * vec2(1.0, -1.0)).rgb
           + texture2D(u_tex, v_uv + u_texel * vec2(-1.0, 1.0)).rgb
           + texture2D(u_tex, v_uv + u_texel * vec2(1.0, 1.0)).rgb;
    gl_FragColor = vec4(c * 0.125, 1.0);
}
