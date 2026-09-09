package dev.voidpvp.client.actuator;

/**
 * The Damage tint actuator's two pieces of arithmetic (§6.7): the vignette's alpha, and what is
 * left of vanilla's hurt-camera roll.
 *
 * <p><b>The roll's amplitude is 14 degrees and that is read out of the bytecode, not remembered.
 * </b> {@code GameRenderer.bobViewWhenHurt(F)V} in the named 1.8.9 jar
 * ({@code net.legacyfabric:yarn:1.8.9+build.604}) is, at offsets 100-134:</p>
 *
 * <pre>
 *   float g = entity.knockbackVelocity;              // MCP's attackedAtYaw
 *   GlStateManager.rotate(-g,        0, 1, 0);
 *   GlStateManager.rotate(-f * 14.0F, 0, 0, 1);      // ldc 14.0f — the only one in the method
 *   GlStateManager.rotate( g,        0, 1, 0);
 * </pre>
 *
 * <p>where {@code f = sin(t^4 * PI)} over the hurt animation. So the <em>direction</em> the hit
 * came from lives entirely in the two {@code knockbackVelocity} rotations about Y, and the
 * <em>amplitude</em> lives entirely in that one constant. That is what makes {@code reduced}
 * expressible at all: scaling the constant keeps both Y rotations exactly as they were, so the
 * roll still leans the way the hit came from — close to the only thing 1.8.9's client tells a
 * player about where they were hit from — while the part that costs them their aim shrinks.
 * {@code off} is the same constant at zero, at which point the two Y rotations are exact inverses
 * and the frame is untouched.</p>
 *
 * <p>The death spin a few instructions earlier — {@code rotate(40 - 8000/(deathTime + 200))} —
 * is deliberately not scaled by any of this. It is the death animation, not a hurt cue, and it
 * fires when the fight is already over.</p>
 */
public final class DamageTint {

    /** Vanilla's hurt-roll amplitude in degrees: the {@code ldc 14.0f} above. */
    public static final float VANILLA_SHAKE = 14f;

    /**
     * What {@code reduced} keeps.
     *
     * <p>A quarter. The setting's promise is "keeps the direction and takes most of the
     * amplitude", and 3.5 degrees is small enough that it no longer moves the crosshair off a
     * target at range and large enough that the lean is still legible — which is the half of the
     * roll that carries information. A value that rounded to nothing would be {@code off} under
     * another name, and this enum already has an {@code off}.</p>
     */
    public static final float REDUCED_FRACTION = 0.25f;

    private DamageTint() {
    }

    /**
     * The amplitude in degrees to use in place of vanilla's 14.
     *
     * @param on   {@code damage_tint.on}; with the mod off vanilla's own number is returned
     *             untouched, so a frame with the mod off rolls byte-identically to vanilla
     * @param mode {@code camera_shake}
     */
    public static float shakeDegrees(boolean on, String mode) {
        if (!on || mode == null || "vanilla".equals(mode)) {
            return VANILLA_SHAKE;
        }
        if ("off".equals(mode)) {
            return 0f;
        }
        if ("reduced".equals(mode)) {
            return VANILLA_SHAKE * REDUCED_FRACTION;
        }
        // An unknown mode is vanilla, which is the value that changes nothing.
        return VANILLA_SHAKE;
    }

    /**
     * The vignette's alpha for a health reading.
     *
     * <p>Ramps from nothing at {@code threshold} to {@code strength} at zero health, which is the
     * behaviour {@code schema/mods/damage_tint.json} spells out and which the two numbers alone
     * do not imply. The boundary is compared against a {@code float} health because
     * {@code LivingEntity.getHealth()} returns one, so a player regenerating past their threshold
     * leaves the vignette at exactly the value they set rather than half a heart later; at
     * exactly {@code threshold} the ramp has run out and the alpha is 0, so "draws at or below
     * threshold" and "ramps from nothing at threshold" are the same statement.</p>
     *
     * @param on        {@code damage_tint.on}
     * @param health    {@code LivingEntity.getHealth()}, on vanilla's 0-20 half-heart scale
     * @param threshold {@code threshold}, 1-20 half-hearts
     * @param strength  {@code strength}, the peak alpha at zero health
     * @return 0 when nothing should be drawn
     */
    public static float vignetteAlpha(boolean on, float health, int threshold, float strength) {
        if (!on || Float.isNaN(health) || Float.isNaN(strength) || strength <= 0f) {
            return 0f;
        }
        int limit = threshold < 1 ? 1 : (threshold > 20 ? 20 : threshold);
        if (health >= limit) {
            return 0f;
        }
        float peak = strength >= 1f ? 1f : strength;
        float remaining = health <= 0f ? 0f : health;
        return peak * (1f - remaining / limit);
    }

    /**
     * How far in from each edge the vignette fades, in the same pixels it is drawn in.
     *
     * <p>Measured off the shorter axis so the band is the same physical depth top and bottom as
     * it is left and right; a fraction of each axis independently would draw a wide, shallow
     * frame on a wide window. Just over a third, which is deep enough that the gradient is a
     * gradient rather than a line and shallow enough that the centre of the screen — where the
     * fight is — is untouched at any {@code strength}.</p>
     */
    public static float bandPixels(int width, int height) {
        int shorter = Math.min(width, height);
        return Math.max(1f, shorter * 0.35f);
    }
}
