package dev.voidpvp.client.cosmetic;

/**
 * A critically damped spring toward a target, stepped by real frame time: every procedural motion
 * (wing spread, halo sway, camera ease) eases through one of these, so a change of state is a
 * glide whose speed does not depend on the frame rate, and nothing ever snaps.
 */
final class Spring {

    float value;
    float velocity;
    /** Natural frequency, rad/s: higher is snappier. */
    final float omega;

    Spring(float omega, float initial) {
        this.omega = omega;
        this.value = initial;
    }

    float step(float target, float dt) {
        if (dt <= 0) {
            return value;
        }
        dt = Math.min(dt, 0.1f);
        // Exact integration of x'' = -2w x' - w^2 (x - target) over dt.
        float w = omega;
        float x = value - target;
        float e = (float) Math.exp(-w * dt);
        float nx = (x + (velocity + w * x) * dt) * e;
        float nv = (velocity - w * (velocity + w * x) * dt) * e;
        value = target + nx;
        velocity = nv;
        return value;
    }

    void snap(float v) {
        value = v;
        velocity = 0;
    }
}
