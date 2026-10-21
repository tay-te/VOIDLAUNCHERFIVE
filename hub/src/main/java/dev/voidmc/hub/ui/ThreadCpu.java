package dev.voidmc.hub.ui;

/**
 * The CPU a thread itself spent, precisely: Windows' {@code QueryThreadCycleTime} (the cycles the thread ran), turned
 * into nanoseconds with a calibrated cycle rate. The Java thread CPU time ({@code ThreadMXBean}) is only updated on the
 * 15.6 ms scheduler tick on Windows, too coarse for one paint. Wall time is not CPU time: on a busy machine (another
 * game running) the UI thread, which runs below the game's priority, waits for a core, and its wall-clock paint times
 * measure the other program. The perf gates compare budgets with this.
 *
 * <p>Unavailable (every method answers -1) off Windows or when JNA cannot reach kernel32.
 */
public final class ThreadCpu {

	private static final Object FN = lookup();
	private static volatile double cyclesPerNs;

	private ThreadCpu() {
	}

	private static Object lookup() {
		try {
			if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
				return null;
			}
			return com.sun.jna.Function.getFunction("kernel32", "QueryThreadCycleTime");
		} catch (Throwable t) {
			return null;
		}
	}

	/** Whether this machine can say a thread's CPU precisely. */
	public static boolean available() {
		return FN != null;
	}

	/** The calling thread's cycle count so far, or -1. */
	public static long cycles() {
		if (FN == null) {
			return -1;
		}
		try {
			com.sun.jna.ptr.LongByReference out = new com.sun.jna.ptr.LongByReference();
			// GetCurrentThread()'s pseudo-handle is -2.
			int ok = ((com.sun.jna.Function) FN).invokeInt(new Object[] {new com.sun.jna.Pointer(-2L), out});
			return ok != 0 ? out.getValue() : -1;
		} catch (Throwable t) {
			return -1;
		}
	}

	/**
	 * Cycles per nanosecond of a running thread (the counter's constant rate), measured once: a few short spins on the
	 * calling thread, keeping the highest rate (a spin that was descheduled for a while reads low, never high).
	 */
	public static double cyclesPerNs() {
		double r = cyclesPerNs;
		if (r > 0 || FN == null) {
			return r;
		}
		synchronized (ThreadCpu.class) {
			if (cyclesPerNs > 0) {
				return cyclesPerNs;
			}
			double best = 0;
			for (int i = 0; i < 6; i++) {
				long c0 = cycles();
				long t0 = System.nanoTime();
				while (System.nanoTime() - t0 < 15_000_000L) {
					Thread.onSpinWait();
				}
				long c1 = cycles();
				long t1 = System.nanoTime();
				if (c0 >= 0 && c1 > c0) {
					best = Math.max(best, (c1 - c0) / (double) (t1 - t0));
				}
			}
			cyclesPerNs = best;
			return best;
		}
	}

	private static volatile long referenceQuiet;

	/**
	 * Cycles a fixed piece of arithmetic takes on the calling thread (the median of five runs), -1 when unknown. On a
	 * loaded machine the same work takes more cycles (a busy hyper-thread sibling, shared caches): {@link #slowdown}.
	 */
	public static long reference() {
		if (FN == null) {
			return -1;
		}
		// Like a paint: arithmetic over a few MB of memory (a busy sibling thread or cache contention slows it as it slows
		// the UI thread's raster work).
		int[] mem = REF_MEM;
		long[] runs = new long[5];
		long sink = 0;
		for (int r = 0; r < runs.length; r++) {
			long c0 = cycles();
			int x = r;
			for (int i = 0; i < 1_500_000; i++) {
				int j = (i * 9973 + x) & (mem.length - 1);
				mem[j] += x;
				x = x * 1103515245 + 12345 + mem[(j + 4096) & (mem.length - 1)];
			}
			sink += x;
			runs[r] = cycles() - c0;
		}
		java.util.Arrays.sort(runs);
		return sink == 42 ? runs[2] + 1 : runs[2];
	}

	private static final int[] REF_MEM = new int[1 << 21];  // 8 MB

	/** Remembers how fast this machine runs the reference when nothing else loads it (game tests, before any load). */
	public static void calibrateReference() {
		referenceQuiet = reference();
	}

	/**
	 * How much slower a thread runs now than on the quiet machine (1 = as quiet; 1.4 = another program's load makes the
	 * same work take 40 % more cycles). The gates divide the UI thread's CPU by it, so they measure the hub's work, not
	 * the machine's load.
	 */
	public static double slowdown() {
		long q = referenceQuiet;
		long now = reference();
		return q <= 0 || now <= 0 ? 1 : Math.max(1, now / (double) q);
	}

	/** Cycles as nanoseconds of CPU, or -1 when unknown. */
	public static long ns(long cycles) {
		double r = cyclesPerNs();
		return cycles < 0 || r <= 0 ? -1 : Math.round(cycles / r);
	}
}
