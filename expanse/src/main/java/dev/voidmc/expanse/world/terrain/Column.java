package dev.voidmc.expanse.world.terrain;

/**
 * Everything the terrain model says about one block column. Instances are reused by a per-thread cache,
 * so read what you need from one before sampling another column.
 */
public final class Column {
	/** Where the ground stands: solid below this height, for the density function. */
	public float height;
	/** The nearest river: its distance from the column, half its width, depth and water surface (NaN if none is near). */
	public float riverDist;
	public float riverHalfWidth;
	public float riverDepth;
	public float riverWater;
	/** Climate values for the biome source, all in [-1, 1]. */
	public float continentalness;
	public float erosion;
	public float weirdness;
	/** Added to temperature: the air is colder up a mountain. */
	public float lapse;
	/** Temperature (with the lapse) and humidity for the biome source, on vanilla's scale. */
	public float temperature;
	public float humidity;
	/** The {@link Landform} the column belongs to, which then has the biome. */
	public int landform;
	/** The surface of the lake or lava lying in a crater over this column (very low if none), and which it is. */
	public float crater;
	public boolean craterLava;
	/**
	 * A lake over this column or on its shore: the y of its top water block (very low if none), and how far
	 * inside its shoreline the column is (negative on the shore round it, where the land holds the water in).
	 */
	public float lake;
	public float lakeEdge;
	/** In a fjord below sea level: the sea fills it, not the river. */
	public boolean fjord;
	/** Diagnostics: distance from the coast (positive inland), uplift, height above the nearest river. */
	public float coast;
	public float uplift;
	public float aboveRiver;
	public int plateX;
	public int plateZ;
	public boolean continental;
	/** The country the column lies in (Countries: 0 none, 1 heartland, 2 rock city, 3 volcanic hills, 4 lake district,
	 * 5 gorges, 6 old forest), and how strongly (1 well inside, 0 at a border with another kind). */
	public int country;
	public float countryStrength;
	/** The rock under its soil: 0 stone, 1 sandstone, 2 limestone, 3 basalt (laid by the chunk generator). */
	public int rock;
	/**
	 * A place a builder would pick (for VOID Hearth's castles): {@link #SITE_HILLTOP} on the level top of a lone
	 * volcanic hill, {@link #SITE_RIM} along the top of an escarpment's edge, else {@link #SITE_NONE}.
	 */
	public int site;
	public static final int SITE_NONE = 0;
	public static final int SITE_HILLTOP = 1;
	public static final int SITE_RIM = 2;

	/** Whether standing water (a crater's or a lake's) lies over the column, or within {@code margin} blocks of its shore. */
	public boolean underWater(float margin) {
		return this.crater > this.height - margin || this.lake > -900 && this.lakeEdge > -margin;
	}

	public boolean nearRiver() {
		return !Float.isNaN(this.riverWater);
	}

	/** Whether the column lies in a river channel, where the water is. */
	public boolean inChannel() {
		return this.nearRiver() && !this.fjord && this.riverDist < this.riverHalfWidth;
	}

	/** The river's water surface as a block height: water fills every y below it. */
	public int waterTop() {
		return (int) Math.floor(this.riverWater + 0.5F);
	}

	/** The lowest water block's y, deepest mid-channel and shoaling toward the banks. */
	public int bed() {
		float across = this.riverDist / Math.max(0.5F, this.riverHalfWidth);
		float depth = this.riverDepth * (float) Math.sqrt(Math.max(0, 1 - across * across));
		return this.waterTop() - Math.max(1, Math.round(depth));
	}
}
