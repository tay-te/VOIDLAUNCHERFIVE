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
	/** Of those, the land's: a pack-layout tile's water comes after it ({@link TileBuilder#packFormat}). */
	public int landIndices;
	/** Where the tile's indices start in the index buffer it is drawn with: a flat smooth tile's are past the grid's. */
	public int firstIndex;
	public boolean smooth;
	/** A far blocky tile with compact vertices ({@link TileBuilder#COMPACT_BYTES}). */
	public boolean compact;
	/** A smooth tile with void in it (between End islands): drawn with the shader variant that cuts it out. */
	public boolean holes;
	public int minY;
	public int maxY;
	/** The surface's lower bound per square of the tile ({@link TileBuilder.Mesh#occluders}), for occlusion culling. */
	public short[] occluders;
	/** The renderer's handle on the uploaded mesh. */
	public Object gpu;
	/** When this tile's mesh was made: a tile is rebuilt at most so often. */
	long builtNanos;
	/**
	 * The land under this tile changed since it was built (chunks ingested over it): how much, as an area in blocks,
	 * and when the first and the latest of those changes came. Zero area: up to date.
	 */
	float dirtyArea;
	long dirtySince;
	long dirtyLast;
	/** The newer build of this tile, made while this one stays on screen. */
	Tile rebuild;
	/** For such a build: the tile it will replace once it is on the GPU. */
	Tile replaces;

	Tile(long key) {
		this.key = key;
	}

	public boolean ready() {
		return this.state == State.READY;
	}

	public int bytes() {
		return this.vertices * (this.compact ? TileBuilder.COMPACT_BYTES : TileBuilder.VERTEX_BYTES);
	}
}
