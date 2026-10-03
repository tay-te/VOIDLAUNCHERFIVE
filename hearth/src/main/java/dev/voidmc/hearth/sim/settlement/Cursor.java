package dev.voidmc.hearth.sim.settlement;

/**
 * A construction progress point {@code (stage, step)} packed into one long so cursors compare with {@code <}: stage in
 * the high half, step in the low. {@code (stageCount, 0)} means complete.
 */
public final class Cursor {
	public static final long ZERO = 0L;

	private Cursor() {
	}

	public static long pack(int stage, int step) {
		return (long) stage << 32 | step & 0xFFFF_FFFFL;
	}

	public static int stage(long cursor) {
		return (int) (cursor >>> 32);
	}

	public static int step(long cursor) {
		return (int) cursor;
	}

	public static String toString(long cursor) {
		return stage(cursor) + ":" + step(cursor);
	}
}
