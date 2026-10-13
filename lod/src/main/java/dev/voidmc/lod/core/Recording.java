package dev.voidmc.lod.core;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * What an offline recorder (VOID Reel) needs of the far view, set through {@code LodApi}. Rendering a film, each
 * frame takes as long as it takes, so the engine's frame timers (cross-fades, the rebuild and swap gates) must run
 * on the film's clock rather than the wall's, or far land changes to the rhythm of whatever made some frames slow.
 * A frame blended from several renders (motion blur) must see one set of tiles in all of them. And a frame rendered
 * larger to be scaled down must choose the same tiles it would at its final size.
 */
public final class Recording {
	private static volatile LongSupplier clock;
	private static volatile BooleanSupplier hold;
	private static volatile double detailScale = 1;

	private Recording() {
	}

	/** The frame clock in nanoseconds: the recorder's while one is set, else {@link System#nanoTime()}. */
	public static long nanos() {
		LongSupplier c = clock;
		return c != null ? c.getAsLong() : System.nanoTime();
	}

	/** Whether this frame must draw exactly the tiles the last one did. */
	public static boolean held() {
		BooleanSupplier h = hold;
		return h != null && h.getAsBoolean();
	}

	/** What the pixels-per-cell budgets are multiplied by. */
	public static double detailScale() {
		return detailScale;
	}

	public static void setClock(LongSupplier nanos) {
		clock = nanos;
	}

	public static void setHold(BooleanSupplier held) {
		hold = held;
	}

	public static void setDetailScale(double scale) {
		if (!(scale > 0)) {
			throw new IllegalArgumentException("detail scale must be positive: " + scale);
		}
		detailScale = scale;
	}
}
