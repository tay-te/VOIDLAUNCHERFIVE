package dev.voidpvp.client.cosmetic;

/**
 * A damped harmonic spring toward a target, integrated exactly over each frame's time, so it
 * moves the same at 30 fps and at 400: the secondary-motion workhorse. Where {@link Spring} is
 * always critically damped (a glide that never overshoots, right for state changes), this one
 * takes a damping ratio, and below one it overshoots and rings down — a hat that wobbles once and
 * settles after a snap turn, a halo that bobs after a landing, a feather that follows through.
 *
 * <p>The coefficients of one step depend only on (omega, zeta, dt), so they live in a shared
 * {@link Params} that caches them for the last dt it saw. Every player's springs of one kind are
 * stepped with the same frame time, so with twenty players on screen the exp/sin/cos are paid
 * once per kind per frame, not once per spring.</p>
 */
final class DampedSpring {

    /** One spring kind: natural frequency (rad/s) and damping ratio (0..1). */
    static final class Params {
        final float omega;
        final float zeta;
        private final float wd;
        private float dt = -1f;
        /** The exact step for {@link #dt}: x' = e (c x + sx v), v' = e (sv v - svx x). */
        private float e, c, sx, sv, svx;

        Params(float omega, float zeta) {
            this.omega = omega;
            this.zeta = Math.max(0f, Math.min(1f, zeta));
            this.wd = omega * (float) Math.sqrt(Math.max(0.0, 1.0 - this.zeta * this.zeta));
        }

        void prepare(float h) {
            if (h == dt) {
                return;
            }
            dt = h;
            float a = zeta * omega;
            e = (float) Math.exp(-a * h);
            float sn;
            if (wd < 1e-3f * omega) {
                // Critically damped, the limit as wd -> 0:
                // x(h) = e ((1 + a h) x + h v); v(h) = e ((1 - a h) v - w^2 h x)
                c = 1f + a * h;
                sv = 1f - a * h;
                sn = h;
            } else {
                // x(h) = e (x cos + (v + a x) sin/wd); v(h) = e (v cos - (a v + w^2 x) sin/wd)
                float cs = (float) Math.cos(wd * h);
                sn = (float) Math.sin(wd * h) / wd;
                c = cs + a * sn;
                sv = cs - a * sn;
            }
            sx = sn;
            svx = omega * omega * sn;
        }
    }

    final Params params;
    float value;
    float velocity;

    DampedSpring(Params params, float initial) {
        this.params = params;
        this.value = initial;
    }

    /** Steps toward {@code target} by {@code dt} seconds (clamped to a tenth); returns the value. */
    float step(float target, float dt) {
        if (dt <= 0) {
            return value;
        }
        Params p = params;
        p.prepare(Math.min(dt, 0.1f));
        float x = value - target;
        float v = velocity;
        float nx = p.e * (p.c * x + p.sx * v);
        float nv = p.e * (p.sv * v - p.svx * x);
        value = target + nx;
        velocity = nv;
        return value;
    }

    /** Adds velocity: an impulse, in value units per second. */
    void kick(float dv) {
        velocity += dv;
    }

    void snap(float v) {
        value = v;
        velocity = 0;
    }
}
