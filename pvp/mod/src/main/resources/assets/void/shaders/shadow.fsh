// void — surface shadow, fragment stage (§6.4).
//
// Draws one CSS `box-shadow` for a rounded rectangle: a signed distance field for
// the rounded box, then a smooth falloff across the blur radius.
//
// This exists because the overlay's CSS has no blurred shadows. A blurred
// box-shadow is the costliest primitive Ultralight's CPU rasteriser has, and it is
// worse than its area suggests — the shadow extends past its element, so changing
// one card dirties a rectangle much larger than the card. Measured in game on a mod
// toggle: 43-80 ms a repaint with the authored ramp, 15-25 ms without. Here the same
// effect is a handful of ALU instructions on a GPU that is already drawing the game.
//
// The falloff is a smoothstep rather than a true Gaussian. CSS defines the blur as a
// Gaussian of sigma = blur/2, and the two differ by a few percent in the middle of
// the ramp — invisible at these alphas, and far cheaper than sampling a kernel.
#version 120

uniform vec2 u_half;    // half the element's size, device pixels, spread applied
uniform float u_radius; // corner radius, device pixels, spread applied
uniform float u_blur;   // blur radius, device pixels
uniform vec4 u_color;   // straight-alpha shadow colour

varying vec2 v_pos;

// Distance from p to a rounded box centred on the origin. Negative inside.
float roundedBoxSdf(vec2 p, vec2 half_size, float radius) {
    vec2 q = abs(p) - half_size + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - radius;
}

void main() {
    float d = roundedBoxSdf(v_pos, u_half, u_radius);
    // A zero blur has to stay a hard edge; one device pixel of feather keeps it from
    // aliasing without softening it visibly.
    float feather = max(u_blur, 1.0);
    float a = 1.0 - smoothstep(-feather, feather, d);
    gl_FragColor = vec4(u_color.rgb, u_color.a * a);
}
