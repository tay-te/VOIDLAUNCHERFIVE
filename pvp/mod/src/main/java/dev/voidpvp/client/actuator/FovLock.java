package dev.voidpvp.client.actuator;

/**
 * The FOV changer's arithmetic (§6.7): which parts of vanilla's field-of-view multiplier survive.
 *
 * <p>1.8.9 has exactly one number here. {@code GameRenderer.getFov} multiplies the player's
 * {@code GameOptions.fov} by an eased {@code movementFovMultiplier}, and that multiplier is eased
 * towards whatever {@code AbstractClientPlayerEntity.getSpeed()} last returned. That one method
 * computes <b>both</b> of this mod's switches and hands back their product:</p>
 *
 * <pre>
 *   speed = (flying ? 1.1 : 1) * ((movementSpeed / walkSpeed + 1) / 2)   // lock_sprint
 *   bow   = 1 - min(useTicks / 20, 1)^2 * 0.15                           // lock_bow
 *   return speed * bow
 * </pre>
 *
 * <p>So suppressing one of the two is not a matter of skipping a branch — there is no branch to
 * skip — it is arithmetic on the product, and that is why it lives here rather than in the Mixin:
 * a Mixin cannot be unit-tested and this is the part that can be wrong.</p>
 *
 * <p>{@link #bowFactor} recomputes the bow half from the same two inputs vanilla reads, in the
 * same order and with the same rounding, so that {@code apply(…, lock_bow only)} divides out
 * exactly what vanilla multiplied in rather than an approximation of it.</p>
 */
public final class FovLock {

    private FovLock() {
    }

    /** Vanilla's bow multiplier: 1 with no bow drawn, easing to 0.85 at a full draw. */
    public static float bowFactor(boolean drawingBow, int useTicks) {
        if (!drawingBow) {
            return 1f;
        }
        float f = useTicks / 20f;
        if (f > 1f) {
            f = 1f;
        } else {
            f = f * f;
        }
        return 1f - f * 0.15f;
    }

    /**
     * The multiplier {@code getSpeed()} should return with this mod's switches applied.
     *
     * @param on         {@code fov.on}
     * @param lockSprint {@code fov.lock_sprint} — drop the movement-speed half
     * @param lockBow    {@code fov.lock_bow} — drop the bow-pull half
     * @param vanilla    what {@code getSpeed()} was about to return
     * @param bowFactor  the bow half of {@code vanilla}, from {@link #bowFactor}
     * @return the value to return instead, or {@code vanilla} when nothing is locked
     */
    public static float apply(boolean on, boolean lockSprint, boolean lockBow,
                              float vanilla, float bowFactor) {
        if (!on || (!lockSprint && !lockBow)) {
            return vanilla;
        }
        if (lockSprint && lockBow) {
            // Both halves gone, so the multiplier is the identity and the player's own `fov`
            // is what reaches the projection untouched.
            return 1f;
        }
        if (lockSprint) {
            // Only the bow's zoom survives. Recomputed rather than divided out of `vanilla`:
            // `vanilla` carries the speed half we are discarding, and a division would carry
            // its rounding error into a value we already know exactly.
            return bowFactor;
        }
        // lock_bow alone: keep the speed half by dividing the bow half back out. Vanilla's own
        // range for it is [0.85, 1], so this cannot divide by zero — but `getSpeed` is reached
        // with a walk speed of 0 and NaN guards of its own, and a NaN fed to the projection is
        // a black frame rather than a wide one, so the guard is here too.
        if (!(bowFactor > 0f) || Float.isNaN(vanilla) || Float.isInfinite(vanilla)) {
            return vanilla;
        }
        float unbowed = vanilla / bowFactor;
        return Float.isNaN(unbowed) || Float.isInfinite(unbowed) ? vanilla : unbowed;
    }
}
