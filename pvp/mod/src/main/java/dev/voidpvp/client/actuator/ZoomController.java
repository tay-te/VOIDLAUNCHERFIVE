package dev.voidpvp.client.actuator;

/**
 * The Zoom actuator's arithmetic: the factor the player's FOV is multiplied by
 * while the zoom key is held (§6.7). Kept out of the Mixin so the easing can be
 * tested; the Mixin does nothing but multiply the return value of
 * {@code GameRenderer.getFov}.
 */
public final class ZoomController {

    /** How much of the remaining distance is covered per frame at 60 fps. */
    private static final double EASE_PER_FRAME = 0.35;

    private double factor = 1.0;
    private boolean held;

    /**
     * Advances one frame.
     *
     * @param zoomHeld    whether the zoom key is down and the mod is on
     * @param fovDivisor  the loadout's {@code fov_divisor}
     * @param smooth      ease rather than snap
     * @param frameFactor frame time relative to 1/60 s, so easing is
     *                    frame-rate independent; 1 for a 60 fps frame
     * @return the multiplier to apply to FOV; 1 means untouched
     */
    public double update(boolean zoomHeld, double fovDivisor, boolean smooth,
                         double frameFactor) {
        held = zoomHeld;
        double divisor = fovDivisor < 1.1 ? 1.1 : (fovDivisor > 10 ? 10 : fovDivisor);
        double target = zoomHeld ? 1.0 / divisor : 1.0;
        if (!smooth) {
            factor = target;
            return factor;
        }
        double step = EASE_PER_FRAME * (frameFactor <= 0 ? 1 : Math.min(frameFactor, 4));
        if (step >= 1) {
            factor = target;
        } else {
            factor += (target - factor) * step;
        }
        if (Math.abs(target - factor) < 0.0005) {
            factor = target;
        }
        return factor;
    }

    /** The current multiplier, without advancing. */
    public double factor() {
        return factor;
    }

    /** True while the zoom is not fully released, so the Mixin stays engaged. */
    public boolean isActive() {
        return held || Math.abs(factor - 1.0) > 0.0005;
    }

    public void reset() {
        factor = 1.0;
        held = false;
    }
}
