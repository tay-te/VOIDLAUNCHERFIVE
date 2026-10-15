package dev.voidpvp.client.perf;

/**
 * The occlusion test behind the {@code culling} mod: can any straight line from the camera reach
 * a box through cells that are not full opaque cubes?
 *
 * <h2>The ray</h2>
 * <p>{@link #clear} walks the cells a segment passes through with the Amanatides–Woo voxel
 * traversal — one step per cell boundary crossed, so the cost of a ray is the number of cells it
 * touches and never a sampling rate that could step over a one-block wall. The cell the eye is in
 * and the cell the target point is in are not tested: the first because the caller has already
 * refused to cull from inside a block, the second because a point on an entity's face can sit in
 * the cell of the wall it is leaning on.</p>
 *
 * <h2>The box</h2>
 * <p>{@link #boxVisible} answers for a whole box by casting to points on the faces that point
 * towards the eye — at most three faces, a 3×3 grid on each — and stops at the first clear one.
 * Points are pulled a hair towards the eye off the face and a little inwards from the edges, so a
 * point on the bottom of a mob standing on the ground lies in the air above the ground cell rather
 * than in it. Nine points per face is a deliberate bias: a false "hidden" is a visible pop, a false
 * "visible" is a few microseconds of drawing, and only the first is a bug.</p>
 *
 * <p>Pure and allocation-free, so it can run on the culling thread a few hundred times a pass.</p>
 */
public final class VoxelRaycast {

    /** How far off the face, towards the eye, a sample point is pulled. */
    static final double FACE_NUDGE = 0.01;
    /** How far in from a face's edges, as a fraction of the face, the outer samples sit. */
    static final double EDGE_INSET = 0.08;
    /** Longest ray walked, in cells. Past this the target is drawn rather than tested. */
    static final int MAX_STEPS = 512;

    private VoxelRaycast() {
    }

    /**
     * True when the segment from the eye to the target crosses no opaque cell, excluding the
     * cells containing its two ends.
     */
    public static boolean clear(BlockOpacity grid, double ox, double oy, double oz,
                                double tx, double ty, double tz) {
        int x = floor(ox);
        int y = floor(oy);
        int z = floor(oz);
        int endX = floor(tx);
        int endY = floor(ty);
        int endZ = floor(tz);

        double dx = tx - ox;
        double dy = ty - oy;
        double dz = tz - oz;

        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        // Parametric distance (0..1 along the segment) to the first boundary on each axis, and
        // between successive boundaries. An axis the ray does not move along never advances.
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
                : (stepX > 0 ? (x + 1 - ox) : (ox - x)) * tDeltaX;
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
                : (stepY > 0 ? (y + 1 - oy) : (oy - y)) * tDeltaY;
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : (stepZ > 0 ? (z + 1 - oz) : (oz - z)) * tDeltaZ;

        for (int steps = 0; steps < MAX_STEPS; steps++) {
            if (x == endX && y == endY && z == endZ) {
                return true;
            }
            // Advance across whichever boundary comes first.
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    if (tMaxX > 1) {
                        return true;
                    }
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    if (tMaxZ > 1) {
                        return true;
                    }
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    if (tMaxY > 1) {
                        return true;
                    }
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ > 1) {
                        return true;
                    }
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
            if (x == endX && y == endY && z == endZ) {
                return true;
            }
            if (grid.opaque(x, y, z)) {
                return false;
            }
        }
        // A ray this long is past any render distance this test is asked about; draw it.
        return true;
    }

    /**
     * True when any sample point on the eye-facing faces of the box is reachable from the eye.
     * An eye inside the box always sees it.
     */
    public static boolean boxVisible(BlockOpacity grid, double ex, double ey, double ez,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        if (ex >= minX && ex <= maxX && ey >= minY && ey <= maxY && ez >= minZ && ez <= maxZ) {
            return true;
        }
        // X faces.
        if (ex < minX && face(grid, ex, ey, ez, 0, minX - FACE_NUDGE, minY, maxY, minZ, maxZ)) {
            return true;
        }
        if (ex > maxX && face(grid, ex, ey, ez, 0, maxX + FACE_NUDGE, minY, maxY, minZ, maxZ)) {
            return true;
        }
        // Y faces.
        if (ey < minY && face(grid, ex, ey, ez, 1, minY - FACE_NUDGE, minX, maxX, minZ, maxZ)) {
            return true;
        }
        if (ey > maxY && face(grid, ex, ey, ez, 1, maxY + FACE_NUDGE, minX, maxX, minZ, maxZ)) {
            return true;
        }
        // Z faces.
        if (ez < minZ && face(grid, ex, ey, ez, 2, minZ - FACE_NUDGE, minX, maxX, minY, maxY)) {
            return true;
        }
        return ez > maxZ && face(grid, ex, ey, ez, 2, maxZ + FACE_NUDGE, minX, maxX, minY, maxY);
    }

    /**
     * Casts to a 3×3 grid on one face. {@code axis} is the face normal (0 = x, 1 = y, 2 = z) and
     * {@code plane} its coordinate; {@code (aMin..aMax, bMin..bMax)} span the other two axes in
     * x, y, z order.
     */
    private static boolean face(BlockOpacity grid, double ex, double ey, double ez, int axis,
                                double plane, double aMin, double aMax, double bMin, double bMax) {
        double aIn = (aMax - aMin) * EDGE_INSET;
        double bIn = (bMax - bMin) * EDGE_INSET;
        double a0 = aMin + aIn;
        double a2 = aMax - aIn;
        double b0 = bMin + bIn;
        double b2 = bMax - bIn;
        double a1 = (a0 + a2) * 0.5;
        double b1 = (b0 + b2) * 0.5;
        // Centre first: it is the likeliest to be seen, so a visible box usually costs one ray.
        for (int i = 0; i < 3; i++) {
            double a = i == 0 ? a1 : (i == 1 ? a0 : a2);
            for (int j = 0; j < 3; j++) {
                double b = j == 0 ? b1 : (j == 1 ? b0 : b2);
                double px;
                double py;
                double pz;
                if (axis == 0) {
                    px = plane;
                    py = a;
                    pz = b;
                } else if (axis == 1) {
                    px = a;
                    py = plane;
                    pz = b;
                } else {
                    px = a;
                    py = b;
                    pz = plane;
                }
                if (clear(grid, ex, ey, ez, px, py, pz)) {
                    return true;
                }
            }
        }
        return false;
    }

    static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
