package dev.voidmc.atlas.core;

/**
 * The map's grids. A <em>region</em> is 512 x 512 blocks; at mip level 0 one map pixel is one block, so a region is
 * one 512 x 512 image. At level {@code L} a pixel covers 2^L x 2^L blocks and a <em>tile</em> (the unit a texture is
 * made of) is still 512 x 512 pixels, so it covers 2^L x 2^L regions. Each region keeps its own copy of every level
 * (512 &gt;&gt; L pixels square), and a level-L tile is assembled from the level-L images of the regions it covers.
 *
 * <p>All coordinates are block coordinates unless named otherwise; division rounds towards negative infinity
 * (arithmetic shifts), so the grids are seamless across zero.
 */
public final class Coords {
	public static final int REGION_SHIFT = 9;
	public static final int REGION_SIZE = 1 << REGION_SHIFT;
	public static final int REGION_MASK = REGION_SIZE - 1;
	/** The coarsest mip level: a pixel is 32 x 32 blocks, a tile 16384 blocks across. */
	public static final int MAX_LEVEL = 5;
	public static final int TILE_SIZE = REGION_SIZE;

	private Coords() {
	}

	/** The region a block is in. */
	public static int region(int block) {
		return block >> REGION_SHIFT;
	}

	/** A block's offset inside its region, 0..511. */
	public static int inRegion(int block) {
		return block & REGION_MASK;
	}

	/** The level-{@code level} tile a block is in. */
	public static int tile(int block, int level) {
		return block >> (REGION_SHIFT + level);
	}

	/** The pixel a block falls on inside its level-{@code level} tile, 0..511. */
	public static int pixel(int block, int level) {
		return (block >> level) & REGION_MASK;
	}

	/** The first block (west or north edge) of pixel {@code px} of tile {@code tile} at {@code level}. */
	public static int blockOf(int tile, int px, int level) {
		return ((tile << REGION_SHIFT) + px) << level;
	}

	/** The first block of a tile at a level. */
	public static int tileOrigin(int tile, int level) {
		return tile << (REGION_SHIFT + level);
	}

	/** Blocks along one side of a tile at a level. */
	public static int tileSpan(int level) {
		return REGION_SIZE << level;
	}

	/** The side of a region's own image at a level: 512, 256, ... 16. */
	public static int regionImageSize(int level) {
		return REGION_SIZE >> level;
	}

	/** Regions along one side of a tile at a level. */
	public static int regionsPerTile(int level) {
		return 1 << level;
	}

	/** Where region {@code region}'s level image sits inside its tile, in tile pixels (per axis). */
	public static int regionOffsetInTile(int region, int level) {
		return (region & ((1 << level) - 1)) * regionImageSize(level);
	}

	/**
	 * The level to draw at for a zoom of {@code pixelsPerBlock} screen pixels per block: the finest level whose pixels
	 * are still at least one screen pixel, so nothing is minified (minifying a nearest-filtered map shimmers).
	 */
	public static int levelFor(double pixelsPerBlock) {
		if (pixelsPerBlock >= 1.0) {
			return 0;
		}
		int level = (int) Math.ceil(-Math.log(pixelsPerBlock) / Math.log(2) - 1e-9);
		return Math.max(0, Math.min(MAX_LEVEL, level));
	}

	/** Packs two ints (a region's or a chunk's x and z) into a long key. */
	public static long pack(int x, int z) {
		return (long) x << 32 | (z & 0xFFFFFFFFL);
	}

	public static int unpackX(long key) {
		return (int) (key >> 32);
	}

	public static int unpackZ(long key) {
		return (int) key;
	}
}
