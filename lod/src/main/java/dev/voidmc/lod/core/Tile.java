package dev.voidmc.lod.core;

/**
 * One tile's place in the cache: waiting, being built, built and uploading, on the GPU, or dropped. The
 * render thread owns it; workers read {@link #wantedFrame} to skip jobs nobody wants any more and move
 * {@link #state} from {@code QUEUED} to {@code BUILDING}.
 */
public final class Tile {
	public enum State { QUEUED, BUILDING, BUILT, READY, DEAD }

	public final long key;
	public volatile State state = State.QUEUED;
	/** Whether a job for this tile is sitting in the queue. */
	volatile boolean inQueue;
	public volatile float priority;
	/** The last frame the selection asked for this tile. */
	public volatile long wantedFrame;
	/** The last frame the selection drew this tile or fell back on it. */
	public long usedFrame;
	/** When the selection last drew, kept or wanted this tile. */
	public long usedNanos;
	/** Quads, or their equivalent for a smooth tile: for the statistics. */
	public int quads;
	public int vertices;
	public int indices;
	public boolean smooth;
	public int minY;
	public int maxY;
	/** The renderer's handle on the uploaded mesh. */
	public Object gpu;

	Tile(long key) {
		this.key = key;
	}

	public boolean ready() {
		return this.state == State.READY;
	}

	public int bytes() {
		return this.vertices * TileBuilder.VERTEX_BYTES;
	}
}
