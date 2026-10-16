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
    private static long clothNanos;
    private static final long[] SPLIT = new long[6];
    private static final double[] SPLIT_AVG = new double[6];
    private static double clothAverage;
    private static double averageNanos;
    private static float quality = 1f;
    /** Dev: -Dvoid.dev.cosmetics.quality pins the LOD factor, for measuring at a fixed level. */
    private static final float PINNED = pinned();

    private static float pinned() {
        try {
            String v = System.getProperty("void.dev.cosmetics.quality");
            return v == null ? -1 : Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    private static long lastReport;

    private Budget() {
    }

    static void add(long nanos) {
        frameNanos += nanos;
    }

    /** Diagnostic split of the layer: 0 capes, 1 models, 2 glow. */
    static void split(int which, long nanos) {
        SPLIT[which] += nanos;
    }

    /** The part of the frame spent stepping cloth (already inside {@link #add}). */
    static void cloth(long nanos) {
        clothNanos += nanos;
    }

    /** Dev: an exact running total for A/B windows ({@link #window}). */
    private static long windowNanos;
    private static int windowFrames;

    /** Dev: mean layer cost in microseconds since the last call, and resets the window. */
    public static double window() {
        double us = windowFrames == 0 ? 0 : windowNanos / 1000.0 / windowFrames;
        windowNanos = 0;
        windowFrames = 0;
        return us;
    }

    /** Once per rendered frame. */
    public static void endFrame() {
        windowNanos += frameNanos;
        windowFrames++;
        averageNanos = averageNanos * 0.95 + frameNanos * 0.05;
        clothAverage = clothAverage * 0.95 + clothNanos * 0.05;
        clothNanos = 0;
        for (int i = 0; i < 6; i++) {
            SPLIT_AVG[i] = SPLIT_AVG[i] * 0.95 + SPLIT[i] * 0.05;
            SPLIT[i] = 0;
        }
        if (averageNanos > TARGET_NANOS) {
            quality = Math.max(0.35f, quality - 0.01f);
        } else if (averageNanos < TARGET_NANOS * 0.6) {
            quality = Math.min(1f, quality + 0.002f);
        }
        frameNanos = 0;
    }

    public static float quality() {
        return PINNED > 0 ? PINNED : quality;
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
        return String.format(java.util.Locale.ROOT,
                "cosmetics: %.0f us/frame avg (capes %.0f: sim %.0f [cloth %.0f], mesh %.0f, draw %.0f; models %.0f, glow %.0f), quality %.2f, %d trail particles, %d fps; %s",
                averageMicros(), SPLIT_AVG[0] / 1000.0, SPLIT_AVG[3] / 1000.0, clothAverage / 1000.0,
                SPLIT_AVG[4] / 1000.0, SPLIT_AVG[5] / 1000.0, SPLIT_AVG[1] / 1000.0, SPLIT_AVG[2] / 1000.0, quality, Trails.live(),
                net.minecraft.client.MinecraftClient.getCurrentFps(), Bloom.diagnostic());
    }
}
