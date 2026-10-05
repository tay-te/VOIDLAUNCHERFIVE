package dev.voidmc.lod.compat.iris;

import org.lwjgl.opengl.GL33C;

/**
 * GPU time spent between {@link #begin} and {@link #end}, summed over the spans of a frame and smoothed over frames,
 * without ever waiting on the GPU: a ring of queries, each read back a few frames later once its result is in.
 */
final class GlTimer {
	private static final int SLOTS = 4;
	/** Up to this many spans a frame (the land, then the water). */
	private static final int SPANS = 2;

	private final int[] queries = new int[SLOTS * SPANS];
	private final int[] used = new int[SLOTS];
	private int slot;
	private boolean open;
	/** The smoothed GPU time a frame, in ms; -1 until the first frame's result is in. */
	double millis = -1;

	GlTimer() {
		GL33C.glGenQueries(this.queries);
	}

	/** Starts a new frame: reads back whatever has finished, then takes the oldest slot. */
	void frame() {
		for (int s = 0; s < SLOTS; s++) {
			int n = this.used[s];
			if (n == 0 || s == this.slot) {
				continue;
			}
			if (GL33C.glGetQueryObjecti(this.queries[s * SPANS + n - 1], GL33C.GL_QUERY_RESULT_AVAILABLE) == 0) {
				continue;
			}
			long nanos = 0;
			for (int q = 0; q < n; q++) {
				nanos += GL33C.glGetQueryObjecti64(this.queries[s * SPANS + q], GL33C.GL_QUERY_RESULT);
			}
			double ms = nanos / 1e6;
			this.millis = this.millis < 0 ? ms : this.millis * 0.9 + ms * 0.1;
			this.used[s] = 0;
		}
		this.slot = (this.slot + 1) % SLOTS;
		// a slot still waiting on the GPU is dropped rather than waited for
		this.used[this.slot] = 0;
	}

	void begin() {
		if (this.open || this.used[this.slot] == SPANS) {
			return;
		}
		GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, this.queries[this.slot * SPANS + this.used[this.slot]]);
		this.open = true;
	}

	void end() {
		if (this.open) {
			GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
			this.used[this.slot]++;
			this.open = false;
		}
	}

	void free() {
		GL33C.glDeleteQueries(this.queries);
	}
}
