package dev.voidmc.hearth.client.overlay;

/**
 * {@link HearthState#revision}'s counter: it goes up by one whenever the fingerprint of the inputs differs from the last
 * one it saw, and never goes down. Pure.
 */
final class RevisionCounter {
	private long revision;
	private long lastPrint = Long.MIN_VALUE;

	/** The revision for this fingerprint of the inputs. */
	synchronized long next(long print) {
		if (print != this.lastPrint) {
			this.lastPrint = print;
			this.revision++;
		}
		return this.revision;
	}

	/** Folds a value into a fingerprint. */
	static long mix(long h, long v) {
		h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
		return h;
	}
}
