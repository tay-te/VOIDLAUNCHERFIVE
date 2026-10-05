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
	/** Structure detection on the workers: time and chunks. */
	public static final AtomicLong DETECT_NANOS = new AtomicLong();
	public static final AtomicLong DETECTED = new AtomicLong();
	/** Underground reveals on the workers: time, walks, open blocks walked, columns written. */
	public static final AtomicLong REVEAL_NANOS = new AtomicLong();
	/** Of which the walk itself (the rest is writing the slices). */
	public static final AtomicLong REVEAL_WALK_NANOS = new AtomicLong();
	public static final AtomicLong REVEALS = new AtomicLong();
	public static final AtomicLong REVEAL_OPENED = new AtomicLong();
	public static final AtomicLong REVEALED_COLUMNS = new AtomicLong();
	/** Landmark analysis on the workers: time per region, regions, and the joining of areas. */
	public static final AtomicLong ANALYSE_NANOS = new AtomicLong();
	public static final AtomicLong ANALYSED = new AtomicLong();
	public static final AtomicLong AREA_NANOS = new AtomicLong();
	public static long uploads;
	public static long uploadBytes;
	public static long tilesDrawn;

	// this frame
	private static long frameStart;
	private static long ingestNanos;
	private static long minimapNanos;
	private static long mapNanos;
	private static long tickNanos;
	private static long hudNanos;
	private static long pageNanos;
	private static long uploadNanos;
	private static long frameUploads;
	private static long frameUploadBytes;
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
				long[] gc = gcTotals();
				if (lastFrameNanos > 25_000_000L) {
					slowFrame(lastFrameNanos, gc[0] - gcCount, gc[1] - gcMillis);
				}
				gcCount = gc[0];
				gcMillis = gc[1];
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
		tickNanos = 0;
		hudNanos = 0;
		pageNanos = 0;
		uploadNanos = 0;
		frameUploads = uploads;
		frameUploadBytes = uploadBytes;
		frameTiles = 0;
	}

	private static long gcCount;
	private static long gcMillis;
	public static final java.util.List<String> SLOW = new java.util.ArrayList<>();

	private static long[] gcTotals() {
		long count = 0;
		long millis = 0;
		for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
			count += Math.max(0, gc.getCollectionCount());
			millis += Math.max(0, gc.getCollectionTime());
		}
		return new long[]{count, millis};
	}

	/** A slow frame while recording: where the time went (Atlas's parts, and the garbage collector's). */
	private static void slowFrame(long nanos, long collections, long gcMillis) {
		String line = String.format(java.util.Locale.ROOT,
			"%.1f ms frame: ingest %.2f, minimap %.2f, map %.2f (page %.2f), uploads %.2f (%d, %d KB), tick %.2f, hud %.2f, gc %d (%d ms)", nanos / 1e6,
			ingestNanos / 1e6, minimapNanos / 1e6, mapNanos / 1e6, pageNanos / 1e6, uploadNanos / 1e6, uploads - frameUploads,
			(uploadBytes - frameUploadBytes) >> 10, tickNanos / 1e6, hudNanos / 1e6, collections, gcMillis);
		if (SLOW.size() < 40) {
			SLOW.add(line);
		}
		AtlasClient.LOGGER.info("Atlas perf: slow {}", line);
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

	/** The worst minimap frame's phases (setup, tiles, markers, rest), in nanoseconds. */
	public static final long[] MINIMAP_SPIKE = new long[5];

	public static void minimapSpike(long setup, long tiles, long markers, long rest) {
		long total = setup + tiles + markers + rest;
		if (total > MINIMAP_SPIKE[0]) {
			MINIMAP_SPIKE[0] = total;
			MINIMAP_SPIKE[1] = setup;
			MINIMAP_SPIKE[2] = tiles;
			MINIMAP_SPIKE[3] = markers;
			MINIMAP_SPIKE[4] = rest;
		}
		if (Boolean.getBoolean("atlas.logStats")) {
			dev.voidmc.atlas.client.AtlasClient.LOGGER.info("Atlas: slow minimap frame {} ms (setup {}, tiles {}, markers {}, rest {})", total / 1e6, setup / 1e6,
				tiles / 1e6, markers / 1e6, rest / 1e6);
		}
	}

	/** Atlas's client tick (places moving on, providers, the exact data's request). */
	public static void tick(long nanos) {
		tickNanos += nanos;
	}

	/** The whole map page, built (not counting the GPU's work). */
	public static void page(long nanos) {
		pageNanos += nanos;
		// the page holds the tiles' draw: the map's cost this frame is the whole page
		mapNanos = Math.max(mapNanos, pageNanos);
	}

	/** Tile uploads at the start of the frame. */
	public static void upload(long nanos) {
		uploadNanos += nanos;
	}

	/** The toast and the compass. */
	public static void hud(long nanos) {
		hudNanos += nanos;
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
		long[] gc = gcTotals();
		gcCount = gc[0];
		gcMillis = gc[1];
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
