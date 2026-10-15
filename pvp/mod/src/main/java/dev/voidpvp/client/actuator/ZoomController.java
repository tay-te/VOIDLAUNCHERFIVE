package dev.voidpvp.client.actuator;

/**
 * The Zoom actuator's arithmetic: the factor the player's FOV is multiplied by
 * while the zoom key is held (§6.7). Kept out of the Mixin so the easing can be
 * tested; the Mixin does nothing but multiply the return value of
 * {@code GameRenderer.getFov}.
 *
 * <p><b>Scroll adjust.</b> While the key is held the mouse wheel moves the divisor
 * multiplicatively, {@link #SCROLL_STEP} per notch, inside the schema's own
 * {@code fov_divisor} range. Multiplicative because zoom is perceived as a ratio: 2x to 2.5x
 * and 8x to 10x feel like the same step, which a fixed additive step would not give. The
 * scrolled level lives only for the hold — releasing forgets it, so every press starts from
 * {@code fov_divisor} as the schema promises. Scrolling only moves the <em>target</em>; the
 * factor still travels there through {@link #update}'s easing, which is how {@code smooth}
 * applies to scroll changes with no extra code.</p>
 */
public final class ZoomController {

    /** How much of the remaining distance is covered per frame at 60 fps. */
    private static final double EASE_PER_FRAME = 0.35;

    /** Divisor multiplier per wheel notch: up is x1.25 (closer), down is /1.25. */
    public static final double SCROLL_STEP = 1.25;

    /** The schema's {@code fov_divisor} bounds, which scrolling also respects. */
    public static final double MIN_DIVISOR = 1.1;
    public static final double MAX_DIVISOR = 10;

    /**
     * Vanilla's sensitivity curve, {@code f = s * 0.6 + 0.2}; the mouse delta is then
     * multiplied by {@code f * f * f * 8} in {@code GameRenderer.render} and {@code tick}.
     */
    private static final double SENS_SCALE = 0.6;
    private static final double SENS_OFFSET = 0.2;

    private double factor = 1.0;
    private boolean held;
    /** The scrolled divisor for the current hold; NaN means "follow {@code fov_divisor}". */
    private double adjusted = Double.NaN;

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
        if (!zoomHeld) {
            // Released: the scrolled level belonged to that hold and goes with it.
            adjusted = Double.NaN;
        }
        double divisor = divisor(fovDivisor);
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

    /**
     * One mouse-wheel event, offered to the zoom before the hotbar sees it.
     *
     * <p>The caller passes whether the key is held <em>now</em> rather than this reading the
     * last frame's {@link #update}: the wheel is drained on the client tick, and a press that
     * lands between two frames is still a press. Only the sign of {@code wheelDelta} matters,
     * exactly as for vanilla's {@code PlayerInventory.scrollInHotbar} — LWJGL reports 120 per
     * notch on most mice and arbitrary small values on touchpads, and one event is one step.</p>
     *
     * @param zoomHeld   the zoom key is down, the mod is on and {@code scroll_adjust} is on
     * @param wheelDelta {@code Mouse.getEventDWheel()}; positive is away from the player
     * @param fovDivisor the loadout's {@code fov_divisor}, the level a fresh hold starts at
     * @return true when the wheel was consumed and the hotbar must not scroll. Also true at
     *         the clamp: a notch that cannot zoom further is still a notch aimed at the zoom,
     *         and spinning the hotbar instead would be a surprise mid-aim.
     */
    public boolean scroll(boolean zoomHeld, int wheelDelta, double fovDivisor) {
        if (!zoomHeld || wheelDelta == 0) {
            return false;
        }
        double base = divisor(fovDivisor);
        adjusted = clampDivisor(wheelDelta > 0 ? base * SCROLL_STEP : base / SCROLL_STEP);
        return true;
    }

    /** The divisor this hold is aiming for: the scrolled level, else {@code fov_divisor}. */
    public double divisor(double fovDivisor) {
        return Double.isNaN(adjusted) ? clampDivisor(fovDivisor) : adjusted;
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
        adjusted = Double.NaN;
    }

    /**
     * {@code zoom.scale_sensitivity}: the {@code GameOptions.sensitivity} value that makes the
     * mouse turn the view {@code fovFactor} times as far.
     *
     * <p>Vanilla turns a mouse delta into degrees as {@code delta * f^3 * 8} with
     * {@code f = s * 0.6 + 0.2}, and it computes {@code f} from the option at the point of
     * use. So the redirect answers the option read with an {@code s'} whose {@code f'} is
     * {@code f * cbrt(fovFactor)}: cubed, that is exactly {@code fovFactor} times the turn,
     * and at 4x the crosshair crosses the same share of the narrowed view per centimetre of
     * mouse as it crossed of the full view. Solving for {@code s'} rather than scaling the
     * delta keeps both of vanilla's readers — the raw path and the cinematic smoother in
     * {@code tick} — on one curve, because both start from the same field.</p>
     *
     * <p>{@code s'} can be negative at low sensitivities; that is fine, because nothing reads
     * it except the {@code * 0.6 + 0.2} that maps it straight back to a positive {@code f'}.
     * A factor of 1 returns the input bit for bit, so an idle zoom is vanilla.</p>
     */
    public static float scaledSensitivity(float sensitivity, double fovFactor) {
        if (!(fovFactor > 0) || Math.abs(fovFactor - 1.0) < 1e-9) {
            return sensitivity;
        }
        double f = sensitivity * SENS_SCALE + SENS_OFFSET;
        double scaled = f * Math.cbrt(fovFactor);
        return (float) ((scaled - SENS_OFFSET) / SENS_SCALE);
    }

    /** The per-delta multiplier vanilla derives from a sensitivity, {@code f^3 * 8}. */
    public static double turnMultiplier(float sensitivity) {
        double f = sensitivity * SENS_SCALE + SENS_OFFSET;
        return f * f * f * 8;
    }

    private static double clampDivisor(double d) {
        return d < MIN_DIVISOR ? MIN_DIVISOR : (d > MAX_DIVISOR ? MAX_DIVISOR : d);
    }
}
