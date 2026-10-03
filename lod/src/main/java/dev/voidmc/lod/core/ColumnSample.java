package dev.voidmc.lod.core;

/**
 * What a terrain source says about one LOD cell: where the ground is, what it is made of, and what stands
 * on it. Reused per worker thread; {@link #clear} runs before every call, so a source only sets what it knows.
 *
 * <p>Colours are what each surface looks like from a distance, 0xRRGGBB, already tinted (grass and leaves
 * by their biome) but not yet shaded: the mesh adds Minecraft's face shading, ambient occlusion and haze.
 */
public final class ColumnSample {
	/** The ground's surface height in blocks, fractional where the source knows it: solid below this y. */
	public float ground;
	/** The top face of the surface block (grass top, snow, sand). */
	public int top;
	/** The side face of the surface block: grass side, snowy grass side, sand. */
	public int side;
	/** What lies under the surface block for {@link #soilDepth} blocks: dirt under grass, sandstone under sand. */
	public int soil;
	public int soilDepth;
	/** The rock below the soil: what a cliff shows. */
	public int rock;
	/**
	 * Rock laid down in horizontal bands (badlands terracotta, canyon walls): band {@code floorMod(y / bandHeight)}
	 * of this palette replaces {@link #rock}. Null for plain rock.
	 */
	public int[] bands;
	public int bandHeight;
	/** Shifts the bands up or down at this column, as vanilla's band offset noise does. */
	public int bandOffset;
	/** The y of the water's top face, or {@link Integer#MIN_VALUE} for none. */
	public int water;
	/** The water's biome tint. */
	public int waterColor;
	/** Lava rather than water. */
	public boolean lava;
	/** How tall the trees over this cell stand above the ground, in blocks (0 for none). */
	public float canopy;
	/** The fraction of the ground under crowns, 0..1. */
	public float cover;
	/** The leaves' colour. */
	public int leaves;
	/** A crown's radius in blocks. */
	public float crown;

	public void clear() {
		this.ground = 0;
		this.top = 0x7F7F7F;
		this.side = 0x7F7F7F;
		this.soil = 0x8A6346;
		this.soilDepth = 3;
		this.rock = 0x7E7E7E;
		this.bands = null;
		this.bandHeight = 4;
		this.bandOffset = 0;
		this.water = Integer.MIN_VALUE;
		this.waterColor = 0x3F76E4;
		this.lava = false;
		this.canopy = 0;
		this.cover = 0;
		this.leaves = 0x3A5F1C;
		this.crown = 2.5F;
	}

	/** The rock at height {@code y} (already shifted by the band offset): the band there, or the plain rock. */
	public static int rockAt(int[] bands, int bandHeight, int rock, int y) {
		return bands == null ? rock : bands[Math.floorMod(Math.floorDiv(y, bandHeight), bands.length)];
	}

	/** The average colour of the bands from {@code y0} up to {@code y1}: what a stack of them is from afar. */
	public static int rockBetween(int[] bands, int bandHeight, int rock, int y0, int y1) {
		if (bands == null) {
			return rock;
		}
		int from = Math.floorDiv(y0, bandHeight);
		int to = Math.max(from, Math.floorDiv(y1, bandHeight));
		int n = Math.min(to - from + 1, bands.length);
		float r = 0, g = 0, b = 0;
		for (int i = 0; i < n; i++) {
			int c = bands[Math.floorMod(from + i, bands.length)];
			r += (c >> 16) & 0xFF;
			g += (c >> 8) & 0xFF;
			b += c & 0xFF;
		}
		return Colors.rgb(r / n, g / n, b / n);
	}
}
