package dev.voidpvp.client.sensor;

/**
 * How far the last hit actually moved you — `docs/mod-roster.md` §3.2 #6 and §5, measured rather
 * than scored.
 *
 * <p>Plain and free of Minecraft types, like {@link HitTally} and {@link ReachTally}: the Mixin
 * that feeds this cannot be unit-tested, so everything that <em>decides</em> lives here.</p>
 *
 * <h2>Why this reports a distance and not a timing error</h2>
 *
 * <p>The roster's row, and Lunar's mod, describe jump-timing feedback: "did your jump land in the
 * window, how often, drifting over a session". Scoring that needs a model of what the right
 * moment <em>is</em>, and the model did not survive reading the game.</p>
 *
 * <p>1.8.9's {@code LivingEntity.method_6109} — take knockback — is, in full:</p>
 *
 * <pre>
 *   if (random.nextDouble() &lt; knockbackResistance) return;
 *   velocityDirty = true;
 *   float dist = sqrt(dx*dx + dz*dz);
 *   velocityX /= 2;  velocityY /= 2;  velocityZ /= 2;
 *   velocityX -= dx / dist * 0.4F;
 *   velocityY += 0.4F;
 *   velocityZ -= dz / dist * 0.4F;
 *   if (velocityY &gt; 0.4) velocityY = 0.4;
 * </pre>
 *
 * <p>Note what that does and does not say. The horizontal result is
 * {@code yourSpeed / 2 - impulse}, so <b>the velocity you already had is half of the answer</b> —
 * moving into a hit genuinely takes less of it. But there is no jump term: {@code velocityY} is
 * assigned {@code +0.4} and clamped to {@code 0.4} whatever you were doing, so a jump neither
 * adds to nor subtracts from the vertical result. Whatever "jump reset" does, it is not in this
 * method, and everything else that could explain it — ground friction against air drag over the
 * ticks that follow — is a claim about {@code Entity.move} that this mod would be asserting
 * rather than reading.</p>
 *
 * <p><b>So this measures the outcome instead.</b> A trainer that scores your timing against a
 * rule the client has not established is a trainer that teaches its author's guess. A trainer
 * that tells you how far each hit moved you is telling you a fact, and the player learns the
 * mechanic from the number rather than from us. It is also the only version of this that cannot
 * become wrong when somebody's understanding of 1.8 knockback changes.</p>
 *
 * <h2>A fixed window, and why it is not "until you stop"</h2>
 *
 * <p>Displacement is measured over {@link #WINDOW_TICKS} from the moment the server moved you,
 * and not until you come to rest. Two reasons, and the second is the one that decides it:</p>
 *
 * <ul>
 *   <li>"At rest" is not a moment. It is a threshold on a velocity that decays asymptotically,
 *       and every threshold makes the figure depend on the number chosen rather than on the hit.
 *   <li><b>A fixed window makes two hits comparable</b>, which is the whole point of a trainer.
 *       A hit that ends against a wall and one that ends in open air are the same knockback and
 *       different distances-to-rest; over half a second they are the same figure.
 * </ul>
 */
public final class KnockbackTally {

    /** No reading yet. A knockback of zero is a hit that did not move you, which is a reading. */
    public static final double NONE = -1;

    /**
     * Ticks of displacement that make the figure — half a second.
     *
     * <p>Long enough to contain the whole of a normal knockback: the impulse is 0.4 blocks/tick
     * and horizontal drag takes it under a tenth of that well inside ten ticks. Short enough that
     * ordinary running does not dominate it — half a second of sprint is about 2.8 blocks and a
     * hit adds several on top, so the two are still told apart.</p>
     */
    static final int WINDOW_TICKS = 10;

    /**
     * Ticks a latched knockback waits for confirmation that it was a hit.
     *
     * <p>{@code setVelocityClient} is the server moving you and it is not only hits — an
     * explosion, a fishing rod and a piston all arrive the same way. The confirmation is
     * {@code hurtTime}, which vanilla sets to 10 on damage and counts down, so a positive reading
     * on the tick after the velocity landed means the move came with damage.</p>
     *
     * <p>Two ticks rather than one because the velocity and the damage arrive as two packets and
     * nothing orders them. One tick would drop the knockback whenever they came the other way
     * round, which is a reading that vanishes for no reason the player can see.</p>
     */
    private static final int CONFIRM_TICKS = 2;

    private boolean armed;
    private long armedAt;
    private double originX;
    private double originZ;
    private boolean confirmed;
    private double blocks = NONE;
    private boolean groundedAtHit;

    /**
     * The server moved you: {@code Entity.setVelocityClient} on the client player.
     *
     * <p>Records where you were and nothing else. Whether it counts is decided by the ticks that
     * follow — see {@link #tick}.</p>
     */
    public void moved(double x, double z, boolean onGround, long tick) {
        // A second push inside an open window restarts it rather than being ignored. A combo
        // lands hits four ticks apart, and a window that kept measuring the first one would
        // report the pair as one enormous knockback — the figure would grow with the combo
        // rather than describe a hit.
        armed = true;
        armedAt = tick;
        originX = x;
        originZ = z;
        confirmed = false;
        groundedAtHit = onGround;
    }

    /**
     * One client tick.
     *
     * @param x        current position
     * @param z        current position
     * @param hurtTime {@code LivingEntity.hurtTime} — the damage confirmation
     * @param tick     a monotonic client tick counter
     * @return true when a reading was completed on this tick
     */
    public boolean tick(double x, double z, int hurtTime, long tick) {
        if (!armed) {
            return false;
        }
        long age = tick - armedAt;
        if (age <= CONFIRM_TICKS && hurtTime > 0) {
            confirmed = true;
        }
        if (age > CONFIRM_TICKS && !confirmed) {
            // Moved without being hurt: a fishing rod, a piston, a teleport correction. Not a
            // knockback reading, and reporting it as one would put a figure on the HUD for
            // something that never hit the player.
            armed = false;
            return false;
        }
        if (age < WINDOW_TICKS) {
            return false;
        }
        double dx = x - originX;
        double dz = z - originZ;
        blocks = Math.round(Math.sqrt(dx * dx + dz * dz) * 100.0) / 100.0;
        armed = false;
        return true;
    }

    /** Distance the last confirmed hit moved you over the window, in blocks, or {@link #NONE}. */
    public double blocks() {
        return blocks;
    }

    /** Whether you were on the ground at the instant the server moved you. */
    public boolean groundedAtHit() {
        return groundedAtHit;
    }

    /** Back to no reading. For a world change, and for a test that wants a clean tally. */
    public void reset() {
        armed = false;
        confirmed = false;
        blocks = NONE;
    }
}
