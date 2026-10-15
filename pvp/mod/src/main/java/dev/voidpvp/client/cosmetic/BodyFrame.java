package dev.voidpvp.client.cosmetic;

/**
 * The mapping between a player's model space (pixels, y down, origin at the neck, as the player
 * model is drawn) and world space, for one frame — the inverse of what
 * {@code LivingEntityRenderer.render} does to the GL matrix before the model draws:
 *
 * <pre>
 *   world = entityPos + Ry(180 - bodyYaw) · S(-1,-1,1) · 0.9375 · ((m + root) / 16 + (0, -1.5078125, 0))
 * </pre>
 *
 * {@code 0.9375} is {@code PlayerEntityRenderer.scale}, {@code -1.5078125} the renderer's own
 * translate, {@code root} the emote root offset this mod applies in {@code setupTransforms}, and
 * {@code entityPos} the interpolated position with the sneak drop already applied. Plain fields,
 * no allocation: it is filled once per player per frame and used per particle.
 */
final class BodyFrame {

    private static final double S = 0.9375;
    private static final double LIFT = -1.5078125;

    double ex, ey, ez;
    double sin, cos;
    double rootX, rootY, rootZ;

    void set(double x, double y, double z, float bodyYawDeg, float rx, float ry, float rz) {
        ex = x;
        ey = y;
        ez = z;
        double th = Math.toRadians(180.0 - bodyYawDeg);
        sin = Math.sin(th);
        cos = Math.cos(th);
        rootX = rx;
        rootY = ry;
        rootZ = rz;
    }

    /** Model pixels to world; writes out[0..2]. */
    void toWorld(double mx, double my, double mz, double[] out) {
        double bx = ((mx + rootX) / 16.0) * S;
        double by = ((my + rootY) / 16.0 + LIFT) * S;
        double bz = ((mz + rootZ) / 16.0) * S;
        // S(-1,-1,1)
        bx = -bx;
        by = -by;
        // Ry(theta): x' = x cos + z sin, z' = -x sin + z cos
        out[0] = ex + bx * cos + bz * sin;
        out[1] = ey + by;
        out[2] = ez - bx * sin + bz * cos;
    }

    /** World to model pixels; writes out[0..2]. */
    void toModel(double wx, double wy, double wz, double[] out) {
        double vx = wx - ex, vy = wy - ey, vz = wz - ez;
        // Ry(-theta)
        double rx = vx * cos - vz * sin;
        double rz = vx * sin + vz * cos;
        rx = -rx;
        vy = -vy;
        out[0] = rx / S * 16.0 - rootX;
        out[1] = (vy / S - LIFT) * 16.0 - rootY;
        out[2] = rz / S * 16.0 - rootZ;
    }

    /** A model-space direction (no translation) to world. */
    void dirToWorld(double mx, double my, double mz, double[] out) {
        double bx = -mx, by = -my, bz = mz;
        out[0] = bx * cos + bz * sin;
        out[1] = by;
        out[2] = -bx * sin + bz * cos;
    }
}
