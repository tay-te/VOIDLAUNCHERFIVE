// void — menu backdrop blur, fragment stage (§6.4).
// One pass of a separable 9-tap Gaussian, run twice: u_dir is one texel
// horizontally on the first pass and one texel vertically on the second.
// The weights are the linear-sampling optimisation of the 1-6-15-20-15-6-1
// binomial kernel, so nine taps cost five samples.
#version 120

uniform sampler2D u_tex;
uniform vec2 u_dir;

varying vec2 v_uv;

void main() {
    vec4 sum = texture2D(u_tex, v_uv) * 0.2270270270;
    sum += (texture2D(u_tex, v_uv + u_dir * 1.3846153846)
          + texture2D(u_tex, v_uv - u_dir * 1.3846153846)) * 0.3162162162;
    sum += (texture2D(u_tex, v_uv + u_dir * 3.2307692308)
          + texture2D(u_tex, v_uv - u_dir * 3.2307692308)) * 0.0702702703;
    gl_FragColor = vec4(sum.rgb, 1.0);
}
