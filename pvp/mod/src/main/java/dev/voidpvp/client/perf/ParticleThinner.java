package dev.voidpvp.client.perf;

/**
 * Keeps a fraction of a stream of particles, evenly rather than at random.
 *
 * <p>An accumulator, not {@code Math.random() < fraction}: at 0.25 a 64-piece block-break burst
 * keeps exactly 16, spread through the burst, every time. Random thinning would keep anything
 * from 8 to 24 and occasionally drop a whole small burst, which reads as flicker rather than as
 * fewer particles. Render-thread only, so the state needs no synchronisation.</p>
 */
public final class ParticleThinner {

    /** Block-break and block-hit dust. */
    public static final ParticleThinner BLOCK_BREAK = new ParticleThinner();
    /** The three explosion particle types. */
    public static final ParticleThinner EXPLOSIONS = new ParticleThinner();

    /**
     * {@code ParticleType} ids of {@code EXPLOSION}, {@code LARGE_EXPLOSION} and
     * {@code HUGE_EXPLOSION}: 0, 1 and 2 in the 1.8.9 enum, which is the id the world spawn path
     * carries. Literal rather than read off the enum so this class stays loadable in tests.
     */
    private static final int EXPLOSION = 0;
    private static final int HUGE_EXPLOSION = 2;

    private double carry;

    /** Whether the next particle in the stream survives at this keep fraction. */
    public boolean keep(double fraction) {
        if (fraction >= 1) {
            return true;
        }
        if (fraction <= 0) {
            return false;
        }
        carry += fraction;
        if (carry >= 1) {
            carry -= 1;
            return true;
        }
        return false;
    }

    public static boolean isExplosion(int particleTypeId) {
        return particleTypeId >= EXPLOSION && particleTypeId <= HUGE_EXPLOSION;
    }
}
