package dev.voidmc.atlas.client;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Atlas's own costs, counted as they happen: per frame on the client thread (frame time, ingest, minimap, map), and
 * totals from the workers (chunks scanned, regions drawn). While {@link #record} is on, every frame's numbers are
 * kept so the performance test can take averages, percentiles and the worst frame.
 */
public final class Perf {
	public static final AtomicLong SCAN_NANOS = new AtomicLong();
	public static final AtomicLong SCANNED = new AtomicLong();
	public static final AtomicInteger RENDERS = new AtomicInteger();
	public static long uploads;
	public static long uploadBytes;
	public static long tilesDrawn;

	// this frame
	private static long frameStart;
	private static long ingestNanos;
	private static long minimapNanos;
	private static long mapNanos;
	private static int frameTiles;

	// recording
	private static boolean recording;
	private static int n;
	private static long[] frames = new long[0];
	private static long[] ingest = new long[0];
	private static long[] minimap = new long[0];
	private static long[] map = new long[0];
	private static int[] tiles = new int[0];
	private static long lastFrameNanos;

	private Perf() {
	}

	/** Called at the start of every frame (render thread). */
	public static void frame() {
		long now = System.nanoTime();
		if (frameStart != 0) {
			lastFrameNanos = now - frameStart;
			if (recording) {
				if (n == frames.length) {
					int size = Math.max(1024, n * 2);
					frames = Arrays.copyOf(frames, size);
					ingest = Arrays.copyOf(ingest, size);
					minimap = Arrays.copyOf(minimap, size);
					map = Arrays.copyOf(map, size);
					tiles = Arrays.copyOf(tiles, size);
				}
				frames[n] = lastFrameNanos;
				ingest[n] = ingestNanos;
				minimap[n] = minimapNanos;
				map[n] = mapNanos;
				tiles[n] = frameTiles;
				n++;
			}
		}
		frameStart = now;
		ingestNanos = 0;
		minimapNanos = 0;
		mapNanos = 0;
		frameTiles = 0;
	}

	public static long lastFrameNanos() {
		return lastFrameNanos;
	}

	public static void ingest(long nanos) {
		ingestNanos += nanos;
	}

	public static void minimap(long nanos) {
		minimapNanos += nanos;
	}

	public static void map(long nanos) {
		mapNanos += nanos;
	}

	public static void tiles(int count) {
		frameTiles += count;
		tilesDrawn += count;
	}

	public static void startRecording() {
		n = 0;
		recording = true;
	}

	/** Stops recording and returns what was recorded. */
	public static Recording stopRecording() {
		recording = false;
		return new Recording(Arrays.copyOf(frames, n), Arrays.copyOf(ingest, n), Arrays.copyOf(minimap, n), Arrays.copyOf(map, n), Arrays.copyOf(tiles, n));
	}

	/** Every frame's numbers, in nanoseconds (tiles: the count drawn). */
	public record Recording(long[] frames, long[] ingest, long[] minimap, long[] map, int[] tiles) {
		public int count() {
			return this.frames.length;
		}

		public static double avgMs(long[] v) {
			if (v.length == 0) {
				return 0;
			}
			double sum = 0;
			for (long x : v) {
				sum += x;
			}
			return sum / v.length / 1e6;
		}

		public static double maxMs(long[] v) {
			long max = 0;
			for (long x : v) {
				max = Math.max(max, x);
			}
			return max / 1e6;
		}

		public static double percentileMs(long[] v, double p) {
			if (v.length == 0) {
				return 0;
			}
			long[] sorted = v.clone();
			Arrays.sort(sorted);
			int i = (int) Math.min(sorted.length - 1, Math.max(0, Math.ceil(p * sorted.length) - 1));
			return sorted[i] / 1e6;
		}

		public double avgTiles() {
			if (this.tiles.length == 0) {
				return 0;
			}
			long sum = 0;
			for (int t : this.tiles) {
				sum += t;
			}
			return (double) sum / this.tiles.length;
		}
	}
}
