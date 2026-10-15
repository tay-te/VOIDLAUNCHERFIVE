package dev.voidpvp.client.bench;

/**
 * Position along the benchmark's closed street loop at a given time, pure so it is testable and
 * so every phase of a run flies exactly the same path.
 */
public final class BenchPath {

    private BenchPath() {
    }

    /**
     * {@code {x, z, yaw}} at {@code t} seconds along the closed polyline {@code corners}
     * travelled at {@code speed} blocks per second. Yaw is Minecraft's: 0 faces +z, 90 faces -x.
     */
    public static double[] pose(double[][] corners, double speed, double t) {
        int n = corners.length;
        double total = 0;
        for (int i = 0; i < n; i++) {
            total += segment(corners, i);
        }
        double d = total <= 0 ? 0 : (speed * t) % total;
        if (d < 0) {
            d += total;
        }
        for (int i = 0; i < n; i++) {
            double len = segment(corners, i);
            if (d <= len || i == n - 1) {
                double[] a = corners[i];
                double[] b = corners[(i + 1) % n];
                double f = len <= 0 ? 0 : Math.min(1, d / len);
                double dx = b[0] - a[0];
                double dz = b[1] - a[1];
                double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                return new double[] {a[0] + dx * f, a[1] + dz * f, yaw};
            }
            d -= len;
        }
        return new double[] {corners[0][0], corners[0][1], 0};
    }

    private static double segment(double[][] c, int i) {
        double[] a = c[i];
        double[] b = c[(i + 1) % c.length];
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }
}
