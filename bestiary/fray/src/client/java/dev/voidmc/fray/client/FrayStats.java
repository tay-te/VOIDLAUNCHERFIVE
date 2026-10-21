package dev.voidmc.fray.client;

import java.util.Arrays;

/**
 * Cost probes for the frame-time budget (ANIMATION.md §10): time spent sampling (at extract) and applying (after each
 * setupAnim), how many of each, and the time between rendered frames. Plain fields on the render thread; tests read
 * and reset them.
 */
public final class FrayStats {
	public static long sampleNanos;
	/** Poses sampled (cache misses) and extractions served (every extraction of an animated entity, every pass). */
	public static long samples;
	public static long extracts;
	/** Increments every rendered frame (never reset): the per-frame sample cache's key. */
	public static long frameId;
	public static long applyNanos;
	public static long applies;
	/** Applies made while Iris draws its shadow pass. */
	public static long shadowApplies;
	public static long rootApplies;
	public static long fpDraws;
	/** Rendered frames since the last reset. */
	public static long frames;

	private static final int RING = 4096;
	private static final long[] FRAME_NANOS = new long[RING];
	private static long lastFrame;

	private FrayStats() {
	}

	public static void reset() {
		sampleNanos = samples = extracts = applyNanos = applies = shadowApplies = rootApplies = fpDraws = frames = 0;
		lastFrame = 0;
	}

	public static void frame() {
		frameId++;
		long now = System.nanoTime();
		if (lastFrame != 0) {
			FRAME_NANOS[(int) (frames % RING)] = now - lastFrame;
			frames++;
		}
		lastFrame = now;
	}

	/** Frame times (ns) recorded since the last reset (up to the ring's size), sorted. */
	public static long[] sortedFrames() {
		int n = (int) Math.min(frames, RING);
		long[] out = Arrays.copyOf(FRAME_NANOS, n);
		Arrays.sort(out);
		return out;
	}

	public static String line() {
		return String.format(java.util.Locale.ROOT, "extracts %d, samples %d (%.2f us per extract), applies %d (%.2f us each, %d in shadow passes), roots %d, fp draws %d, frames %d",
			extracts, samples, extracts == 0 ? 0 : sampleNanos / 1000.0 / extracts, applies, applies == 0 ? 0 : applyNanos / 1000.0 / applies, shadowApplies, rootApplies,
			fpDraws, frames);
	}
}
