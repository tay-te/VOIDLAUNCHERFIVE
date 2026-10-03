package dev.voidmc.lod.core;

/**
 * What a terrain source says about one LOD cell: where the ground is, what it looks like, and what stands
 * on it. Reused per worker thread; a source fills every field on every call.
 */
public final class ColumnSample {
	/** The y of the ground's top face: the highest solid block is {@code ground - 1}. */
	public int ground;
	/** The ground's colour, 0xRRGGBB. */
	public int groundColor;
	/** The y of the water's top face, or {@link Integer#MIN_VALUE} for none. */
	public int water;
	public int waterColor;
	/** How tall the vegetation over this cell stands above the ground, in blocks (0 for bare ground). */
	public int canopy;
	/** The fraction of the cell under that canopy, 0..1. */
	public float cover;
	public int canopyColor;

	public void clear() {
		this.ground = 0;
		this.groundColor = 0x7F7F7F;
		this.water = Integer.MIN_VALUE;
		this.waterColor = 0x3F76E4;
		this.canopy = 0;
		this.cover = 0;
		this.canopyColor = 0x48B518;
	}
}
