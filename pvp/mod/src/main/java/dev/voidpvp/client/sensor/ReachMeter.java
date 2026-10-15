package dev.voidpvp.client.sensor;

/**
 * The {@code reach} reading on the tick payload: how far the last landed attack reached
 * ({@code bridge.json}, {@code tick_payload.reach}; {@code schema/mods/reach.json}).
 *
 * <p><b>What is measured.</b> The distance from the eye to the point where the crosshair ray met
 * the target's hitbox — the same two points 1.8.9's own {@code GameRenderer.updateTargetedEntity}
 * compared against its 3-block limit to decide that the crosshair was on the entity at all. From
 * the disassembly: the eye is {@code cameraEntity.getCameraPosVec(tickDelta)}, the box is
 * {@code target.getBoundingBox().expand(m, m, m)} with {@code m = getTargetingMargin()} (0.1 for
 * every 1.8.9 entity), and the resulting {@code BlockHitResult(entity, pos)} carries the ray's
 * intercept as {@code pos} — or the eye itself when the eye is inside the box. So
 * {@link #toPoint} on {@code result.pos} reproduces the game's own number, not an approximation
 * of it; entity-centre distance would read ~0.3 blocks long, which is the error the schema
 * names.</p>
 *
 * <p>{@link #toBox} is the fallback for a result with no hit vector: the closest point of the
 * same expanded box. That is the shortest distance any ray from this eye could have measured, so
 * when it is wrong it is wrong on the honest side.</p>
 *
 * <p>Plain on purpose, like {@link HitTally}: the Mixin reads positions off the game and this
 * does the arithmetic and owns the counter, so {@code SensorsTest} can hold both.</p>
 */
public final class ReachMeter {

    /** The schema's ceiling. A survival hit cannot exceed ~3.1; creative reaches 6. */
    public static final double MAX_BLOCKS = 16;

    private int n;
    private double last;

    /** Eye to a known hit point. */
    public static double toPoint(double eyeX, double eyeY, double eyeZ,
                                 double hitX, double hitY, double hitZ) {
        double dx = hitX - eyeX;
        double dy = hitY - eyeY;
        double dz = hitZ - eyeZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Eye to the closest point of an axis-aligned box; 0 with the eye inside it. */
    public static double toBox(double eyeX, double eyeY, double eyeZ,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        return toPoint(eyeX, eyeY, eyeZ,
                clamp(eyeX, minX, maxX), clamp(eyeY, minY, maxY), clamp(eyeZ, minZ, maxZ));
    }

    /** Rounded to the 3 decimals the wire carries. */
    public static double round3(double blocks) {
        return Math.round(blocks * 1000.0) / 1000.0;
    }

    /**
     * One measured attack. Rounds, clamps to the schema's 0-16 and moves {@code n}, which is
     * what tells the page a new reading arrived: two hits at the same distance are still two
     * readings for the average.
     *
     * @return the recorded figure
     */
    public double record(double blocks) {
        double b = Double.isNaN(blocks) ? 0 : round3(clamp(blocks, 0, MAX_BLOCKS));
        last = b;
        n++;
        return b;
    }

    /** Measured attacks since the session began; 0 until the first, when the field is absent. */
    public int n() {
        return n;
    }

    /** The most recent figure; meaningless while {@link #n} is 0. */
    public double last() {
        return last;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
