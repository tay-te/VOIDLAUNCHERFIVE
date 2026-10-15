package dev.voidpvp.client.bench;

import java.util.Arrays;

/**
 * Frame-time statistics for the benchmark.
 *
 * <p>"1% low" is the frame rate of the 99th-percentile frame time — the frame that is slower than
 * 99% of the others — which is how capture tools such as CapFrameX and PresentMon report it, and
 * the same definition the {@code fps} HUD mod's 1% low uses. Average FPS is frames over elapsed
 * time, not the mean of per-frame rates, which would overweight fast frames.</p>
 */
public final class FrameStats {

    private FrameStats() {
    }

    /** {@code {avgFps, low1Fps, low01Fps}} for frame times in milliseconds. */
    public static double[] summarize(float[] frameMs) {
        if (frameMs.length == 0) {
            return new double[] {0, 0, 0};
        }
        double sum = 0;
        for (float f : frameMs) {
            sum += f;
        }
        float[] sorted = frameMs.clone();
        Arrays.sort(sorted);
        double avg = sum <= 0 ? 0 : frameMs.length * 1000.0 / sum;
        return new double[] {avg, fps(percentile(sorted, 0.99)), fps(percentile(sorted, 0.999))};
    }

    /** The {@code q} quantile of an ascending array, nearest-rank. */
    static float percentile(float[] ascending, double q) {
        int idx = (int) Math.ceil(q * ascending.length) - 1;
        idx = Math.max(0, Math.min(ascending.length - 1, idx));
        return ascending[idx];
    }

    private static double fps(float ms) {
        return ms <= 0 ? 0 : 1000.0 / ms;
    }
}
