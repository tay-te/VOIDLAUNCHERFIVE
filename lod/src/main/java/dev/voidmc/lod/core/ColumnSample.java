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
	/**
	 * Where the ground ends below, for land floating over the void (End islands): solid from here up to
	 * {@link #ground}. {@link Float#NEGATIVE_INFINITY} for ground that goes all the way down.
	 */
	public float bottom;
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
	/**
	 * What the crowns look like from straight above, where that is not {@link #leaves}: snow on the boughs in
	 * a cold forest. Negative for the leaves' own colour.
	 */
	public int leavesTop;
	/** A crown's radius in blocks. */
	public float crown;
	/** The share of the trees that are conifers (spruce, pine: cones) rather than broadleaf (oak, birch: domes). */
	public float conifer;
	/**
	 * The blocks the faces are made of, as ids from the source's material registry (0: unknown, drawn untextured):
	 * the top and side of the surface block, the soil, the rock, the leaves. Colours above stay the truth for
	 * what each looks like from afar; the ids let close tiles draw the textures themselves.
	 */
	public int topMaterial;
	public int sideMaterial;
	public int soilMaterial;
	public int rockMaterial;
	public int leavesMaterial;
	/** The material of each of {@link #bands}, parallel to it; null leaves banded rock {@link #rockMaterial}. */
	public int[] bandMaterials;
	/**
	 * The block light over the surface, 0..15, averaged over the cell: torches, lanterns and lava the player has seen, so
	 * a lit town glows at night far off. 0 where nothing is known of it.
	 */
	public float light;
	/** No ground at all in this column: the void between End islands. Nothing is drawn over it. */
	public boolean empty;

	public void clear() {
		this.ground = 0;
		this.bottom = Float.NEGATIVE_INFINITY;
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
		this.leavesTop = -1;
		this.crown = 2.5F;
		this.conifer = 0;
		this.topMaterial = 0;
		this.sideMaterial = 0;
		this.soilMaterial = 0;
		this.rockMaterial = 0;
		this.leavesMaterial = 0;
		this.bandMaterials = null;
		this.light = 0;
		this.empty = false;
	}

	/** The rock at height {@code y} (already shifted by the band offset): the band there, or the plain rock. */
	public static int rockAt(int[] bands, int bandHeight, int rock, int y) {
		return bands == null ? rock : bands[Math.floorMod(Math.floorDiv(y, bandHeight), bands.length)];
	}

	/** What the rock at height {@code y} is made of, band by band as {@link #rockAt} colours it. */
	public static int rockMaterialAt(int[] bandMaterials, int bandHeight, int rockMaterial, int y) {
		return bandMaterials == null ? rockMaterial : bandMaterials[Math.floorMod(Math.floorDiv(y, bandHeight), bandMaterials.length)];
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
