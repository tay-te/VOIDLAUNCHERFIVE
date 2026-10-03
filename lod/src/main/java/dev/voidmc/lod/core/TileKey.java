package dev.voidmc.lod.core;

/**
 * A tile of the LOD quadtree, packed into one {@code long} so the hot maps and queues never box.
 *
 * <p>A tile at level {@code L} is {@value #TILE} x {@value #TILE} cells, each {@code 1 << L} blocks on a side,
 * so it spans {@code TILE << L} blocks. Tile {@code (tx, tz)} at that level starts at block
 * {@code (tx << (SHIFT + L), tz << (SHIFT + L))}; its four children at level {@code L - 1} are
 * {@code (2tx + i, 2tz + j)}. Level 0 is one block per cell, the same detail as the chunks it borders.
 *
 * <p>Layout: 5 bits of level, then 29 bits each of tx and tz (two's complement). 29 bits of tiles at
 * level 0 is 2^34 blocks either way, comfortably past the world border.
 */
public final class TileKey {
	/** Cells per tile side. 64 keeps a tile's mesh big enough to batch and small enough to cull. */
	public static final int TILE = 64;
	public static final int SHIFT = 6;
	/** The coarsest level: 512-block cells, 32 km tiles. */
	public static final int MAX_LEVEL = 9;

	private static final int BITS = 29;
	private static final long MASK = (1L << BITS) - 1;

	private TileKey() {
	}

	public static long of(int level, int tx, int tz) {
		return (long) level << (2 * BITS) | (tx & MASK) << BITS | tz & MASK;
	}

	public static int level(long key) {
		return (int) (key >>> (2 * BITS));
	}

	public static int tx(long key) {
		return (int) (key << (64 - 2 * BITS) >> (64 - BITS));
	}

	public static int tz(long key) {
		return (int) (key << (64 - BITS) >> (64 - BITS));
	}

	/** Blocks per cell at this level. */
	public static int cell(int level) {
		return 1 << level;
	}

	/** Blocks per tile side at this level. */
	public static int span(int level) {
		return TILE << level;
	}

	public static int minX(long key) {
		return tx(key) << (SHIFT + level(key));
	}

	public static int minZ(long key) {
		return tz(key) << (SHIFT + level(key));
	}

	public static long parent(long key) {
		return of(level(key) + 1, tx(key) >> 1, tz(key) >> 1);
	}

	public static long child(long key, int i) {
		return of(level(key) - 1, 2 * tx(key) + (i & 1), 2 * tz(key) + (i >> 1));
	}

	public static String toString(long key) {
		return "L" + level(key) + "[" + tx(key) + "," + tz(key) + "]";
	}
}
