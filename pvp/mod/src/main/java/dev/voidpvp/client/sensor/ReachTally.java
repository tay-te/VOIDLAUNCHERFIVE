package dev.voidpvp.client.sensor;

/**
 * The distance of the last attack that landed, and the rule that keeps it a readout rather than
 * an indicator.
 *
 * <p>Plain and free of Minecraft types on purpose, like {@link HitTally} and
 * {@link KeyStateTracker}: the Mixins that feed this cannot be unit-tested, so everything that
 * <em>decides</em> lives here and they only hand over facts. {@code SensorsTest} walks the rule
 * below; without this class it would be a rule expressed as two injection points and a comment.</p>
 *
 * <h2>The rule, and why it is the whole class</h2>
 *
 * <p>{@code docs/mod-roster.md} §6.1 draws the line this mod lives on, and draws it precisely:</p>
 *
 * <blockquote>Reach display, honestly. It is allowed as a HUD readout of your <em>own</em> attack
 * distance — but if you implement it by ray-marching toward an entity rather than reporting an
 * actual attack event, you have built a reach <em>indicator</em>, which is the disallowed thing.
 * Class it {@code grey}, compute it only from a landed attack, never predict.</blockquote>
 *
 * <p>So this class has two inputs and they are deliberately not symmetrical. {@link #picked} is a
 * <b>sample</b>: the crosshair is on an entity and this is how far away the hit point is. It
 * publishes nothing. {@link #landed} is the <b>latch</b>: an attack has actually been delivered,
 * so the sample becomes the reading. Between attacks the reading does not move, and what it says
 * is a fact about something that has already happened.</p>
 *
 * <p><b>Why sampling at all is not the disallowed thing.</b> The number {@link #picked} takes is
 * not computed by us and would be computed if this mod did not exist: {@code
 * GameRenderer.updateTargetedEntity} evaluates {@code result.pos.distanceTo(cameraPos)} at
 * offsets 479-483 for its own three-block cutoff, every frame, in vanilla. We read the same two
 * vectors it already has. What §6.1 forbids is <em>displaying</em> a distance to something you
 * have not hit — telling a player how far away a target is, live, so they can act on it. Nothing
 * here can do that: {@link #reach()} does not change when the crosshair moves.</p>
 *
 * <p><b>Why the sample is needed rather than measuring at attack time.</b> {@code doAttack} runs
 * during the tick, and the raycast it consults was performed during a render frame with that
 * frame's {@code tickDelta}. Recomputing the eye position at attack time would use a different
 * eye — up to about 0.28 blocks away at sprint speed — from the one that decided the hit, which
 * on a figure players quote to two decimal places is not a rounding error, it is a different
 * number. Latching the value from the frame that actually picked the target is what makes the
 * reading the geometry of <em>that swing</em>.</p>
 *
 * <h2>What the number is</h2>
 *
 * <p>Eye to the point on the target's hitbox the ray struck — vanilla's {@code result.pos},
 * which for an entity pick is written at {@code updateTargetedEntity} offsets 552-566 as
 * {@code new BlockHitResult(targetedEntity, hitVec)}. Not eye-to-centre and not
 * hitbox-to-hitbox: the ray hit a surface, and the distance to that surface is the one quantity
 * the client actually knows. Every other definition is a reconstruction.</p>
 */
public final class ReachTally {

    /** No reading yet. A reach of zero is not a swing, so the absent case needs its own value. */
    public static final double NONE = -1;

    /**
     * How far a swing can be from the last sample before the sample is thrown away.
     *
     * <p>The sample comes from a render frame and the attack from a tick, so they are never the
     * same instant; the question is how much staleness makes the number a fiction. Two ticks is
     * 100 ms — long enough that a frame always lands inside it at any playable frame rate, short
     * enough that a swing which connects after the crosshair has left the target cannot report
     * the distance it had while it was on it.</p>
     *
     * <p>The failure this prevents is specific: {@code doAttack} reads {@code MinecraftClient
     * .result}, which is whatever the last frame wrote, so a client that has stopped rendering —
     * a stall, a resource pack reload — would otherwise attack against a sample from before it.
     * Without the window that reports a real distance, from the wrong moment, with no way to
     * tell.</p>
     */
    private static final long SAMPLE_WINDOW_MS = 100;

    private double sample = NONE;
    private long sampleAt = Long.MIN_VALUE;
    private double reach = NONE;

    /**
     * The crosshair resolved onto an entity this frame, that far away.
     *
     * <p>Publishes nothing. Call it only when the pick is an entity — a block or a miss has no
     * attack distance, and letting one through would make the next swing at air report the range
     * of the wall behind it.</p>
     *
     * @param blocks {@code result.pos.distanceTo(cameraEntity.getCameraPosVec(tickDelta))}
     * @param now    {@code System.currentTimeMillis()}, for {@link #SAMPLE_WINDOW_MS}
     */
    public void picked(double blocks, long now) {
        if (blocks < 0 || Double.isNaN(blocks)) {
            return;
        }
        sample = blocks;
        sampleAt = now;
    }

    /** The crosshair is on nothing attackable, so there is no sample to latch. */
    public void pickedNothing() {
        sample = NONE;
        sampleAt = Long.MIN_VALUE;
    }

    /**
     * An attack was delivered onto a live, attackable entity — the reading moves.
     *
     * <p>Called with {@link HitTally}'s own definition of landed rather than a second one, so the
     * two counters cannot disagree about what a hit is. If the sample is missing or stale the
     * reading is left alone: a swing whose geometry we cannot state honestly leaves the last
     * honest one on screen rather than replacing it with a guess.</p>
     */
    public void landed(long now) {
        if (sample == NONE || now - sampleAt > SAMPLE_WINDOW_MS) {
            return;
        }
        reach = sample;
    }

    /**
     * The distance of the last landed attack, in blocks, or {@link #NONE}.
     *
     * <p>Rounded to two decimal places, which is the precision the figure is quoted at and is
     * also as much as the source supports: the sample is interpolated between two tick positions,
     * so a third decimal would be reporting the interpolation rather than the swing.</p>
     */
    public double reach() {
        return reach == NONE ? NONE : Math.round(reach * 100.0) / 100.0;
    }

    /** Back to no reading. For a world change, and for a test that wants a clean tally. */
    public void reset() {
        sample = NONE;
        sampleAt = Long.MIN_VALUE;
        reach = NONE;
    }
}
