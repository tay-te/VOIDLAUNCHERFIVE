package dev.voidpvp.client.cosmetic;

/**
 * The cosmetics layer's frame budget. Every player's cosmetic pass adds its time here; once per
 * frame {@link #endFrame} folds the total into a smoothed average, and {@link #quality} — a factor
 * on every LOD distance in {@link CosmeticRenderer} — eases down while the average is over
 * {@link #TARGET_NANOS} and back up when there is room. Twenty fully dressed players should land
 * under half a millisecond; on a machine where they do not, distant capes stop simulating before
 * the frame rate notices.
 */
public final class Budget {

    public static final long TARGET_NANOS = 500_000L;

    private static long frameNanos;
    private static double averageNanos;
    private static float quality = 1f;
    private static long lastReport;

    private Budget() {
    }

    static void add(long nanos) {
        frameNanos += nanos;
    }

    /** Once per rendered frame. */
    public static void endFrame() {
        averageNanos = averageNanos * 0.95 + frameNanos * 0.05;
        if (averageNanos > TARGET_NANOS) {
            quality = Math.max(0.35f, quality - 0.01f);
        } else if (averageNanos < TARGET_NANOS * 0.6) {
            quality = Math.min(1f, quality + 0.002f);
        }
        frameNanos = 0;
    }

    public static float quality() {
        return quality;
    }

    /** Smoothed per-frame cost of the cosmetics layer, microseconds. */
    public static double averageMicros() {
        return averageNanos / 1000.0;
    }

    /** Rate-limited one-line report for dev logs; null when it is not time yet. */
    public static String report() {
        long now = System.currentTimeMillis();
        if (now - lastReport < 5000) {
            return null;
        }
        lastReport = now;
        return String.format(java.util.Locale.ROOT, "cosmetics: %.0f us/frame avg, quality %.2f, %d trail particles",
                averageMicros(), quality, Trails.live());
    }
}
