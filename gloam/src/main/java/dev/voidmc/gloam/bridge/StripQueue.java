package dev.voidmc.gloam.bridge;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * The hand-over of finished strips from a worker to the render thread. A worker posts whole {@link TileStrip.Batch}es;
 * the render thread writes them in order, a batch that is not {@code spreadable} all in one frame (U, whose bytes are
 * relative to the height it was sampled at, must never be half old and half new), a spreadable one (W's rebuild,
 * 256 KB a tile) at most {@code budget} lines a frame.
 */
final class StripQueue {
	private final ConcurrentLinkedQueue<TileStrip.Batch> batches = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<TileStrip> spreading = new ArrayDeque<>();
	/** Lines written by the last {@link #drain}. */
	int lastLines;

	/** Worker: hands a batch over. */
	void post(TileStrip.Batch batch) {
		if (batch != null && !batch.strips().isEmpty()) {
			this.batches.add(batch);
		}
	}

	/**
	 * Render thread: writes what is waiting into {@code bytes}, at most {@code budget} lines of spreadable batches, and
	 * tells {@code written} of every strip written (in order), so the caller can take the centres from them.
	 */
	void drain(byte[] bytes, LandTiles.Dirty dirty, int budget, Consumer<TileStrip> written) {
		int lines = 0;
		while (true) {
			if (this.spreading.isEmpty()) {
				TileStrip.Batch b = this.batches.poll();
				if (b == null) {
					break;
				}
				if (!b.spreadable()) {
					for (TileStrip s : b.strips()) {
						TileStrip.write(bytes, s, dirty);
						written.accept(s);
						lines++;
					}
					continue;
				}
				this.spreading.addAll(b.strips());
			}
			while (!this.spreading.isEmpty() && lines < budget) {
				TileStrip s = this.spreading.poll();
				TileStrip.write(bytes, s, dirty);
				written.accept(s);
				lines++;
			}
			if (lines >= budget) {
				break;
			}
		}
		this.lastLines = lines;
	}

	/** Whether anything is still waiting. */
	boolean waiting() {
		return !this.spreading.isEmpty() || !this.batches.isEmpty();
	}

	/** Drops everything waiting (a new world). Render thread. */
	void clear() {
		this.batches.clear();
		this.spreading.clear();
	}
}
