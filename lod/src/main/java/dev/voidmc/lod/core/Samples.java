package dev.voidmc.lod.core;

import java.util.Arrays;

/**
 * One tile's worth of columns, sampled from a {@link TerrainSource} on a regular grid with a border, so the
 * meshers can look at a cell's neighbours (for walls, normals and occlusion) without asking another tile.
 * The blocky mesher samples cell centres, the smooth one cell corners.
 *
 * <p>After sampling, natural variation goes on top of what the source said: forests thin into clearings and
 * thicken again, and colours drift a little in brightness and saturation over a few hundred blocks. A biome
 * is one flat colour only on a map; on the ground it never is.
 */
final class Samples {
	static final int N = TileKey.TILE;
	static final int MAX_BORDER = 2;
	private static final int MAX = N + 1 + 2 * MAX_BORDER;
	private static final long SALT_COVER = 0x5EEDC0FEL;
	private static final long SALT_TONE = 0x70E5L;
	private static final long SALT_HEIGHT = 0x7A11L;

	// Every per-sample array: copy() lists each one, so a sample reused from a neighbour is the whole sample.
	final float[] ground;
	/** Where the land ends below (floating islands), or negative infinity for solid ground all the way down. */
	final float[] bottom;
	final int[] top;
	final int[] side;
	final int[] soil;
	final int[] soilDepth;
	final int[] rock;
	final int[][] bands;
	/** Per band, its material id (null where there are no bands). */
	final int[][] bandMaterials;
	final int[] bandHeight;
	final int[] bandOffset;
	final int[] water;
	final int[] waterColor;
	final boolean[] lava;
	final float[] canopy;
	final float[] cover;
	final int[] leaves;
	/** The crowns' colour from above: the leaves', or snow on them. */
	final int[] leavesTop;
	final float[] crown;
	final float[] conifer;
	final int[] topMaterial;
	final int[] sideMaterial;
	final int[] soilMaterial;
	final int[] rockMaterial;
	final int[] leavesMaterial;
	final boolean[] empty;
	/** The block light over the surface, 0..15: what the chunks showed lit, and lava, which always glows. */
	final byte[] light;
	/** The biome ({@link BiomeIds}), and whether the chunks the player loaded showed most of the cell. */
	final int[] biome;
	final boolean[] seen;
	final boolean[] bareWhenSteep;
	final float[] bareRise;
	/** The land under an overhang ({@link ColumnSample#deckBottom}, {@link Integer#MIN_VALUE} for none), and how it looks. */
	final int[] deckBottom;
	final int[] lowerGround;
	final int[] lowerWater;
	final int[] lowerTop;
	final int[] lowerSide;
	final int[] lowerTopMaterial;
	final int[] lowerSideMaterial;
	final int[] lowerWaterColor;
	private final ColumnSample sample = new ColumnSample();
	private final boolean variation;
	/** Which samples of the tile being sampled were reused from {@link SampleCache}, so the source is not asked again. */
	private final boolean[] known;
	int border;

	Samples(boolean variation) {
		this(variation, MAX * MAX);
	}

	/** Room for {@code capacity} samples: a tile's grid, or the edges of one that {@link SampleCache} keeps. */
	Samples(boolean variation, int capacity) {
		this.variation = variation;
		this.ground = new float[capacity];
		this.bottom = new float[capacity];
		this.top = new int[capacity];
		this.side = new int[capacity];
		this.soil = new int[capacity];
		this.soilDepth = new int[capacity];
		this.rock = new int[capacity];
		this.bands = new int[capacity][];
		this.bandMaterials = new int[capacity][];
		this.bandHeight = new int[capacity];
		this.bandOffset = new int[capacity];
		this.water = new int[capacity];
		this.waterColor = new int[capacity];
		this.lava = new boolean[capacity];
		this.canopy = new float[capacity];
		this.cover = new float[capacity];
		this.leaves = new int[capacity];
		this.leavesTop = new int[capacity];
		this.crown = new float[capacity];
		this.conifer = new float[capacity];
		this.topMaterial = new int[capacity];
		this.sideMaterial = new int[capacity];
		this.soilMaterial = new int[capacity];
		this.rockMaterial = new int[capacity];
		this.leavesMaterial = new int[capacity];
		this.empty = new boolean[capacity];
		this.light = new byte[capacity];
		this.biome = new int[capacity];
		this.seen = new boolean[capacity];
		this.bareWhenSteep = new boolean[capacity];
		this.bareRise = new float[capacity];
		this.deckBottom = new int[capacity];
		Arrays.fill(this.deckBottom, Integer.MIN_VALUE);
		this.lowerGround = new int[capacity];
		this.lowerWater = new int[capacity];
		this.lowerTop = new int[capacity];
		this.lowerSide = new int[capacity];
		this.lowerTopMaterial = new int[capacity];
		this.lowerSideMaterial = new int[capacity];
		this.lowerWaterColor = new int[capacity];
		this.known = new boolean[capacity];
	}

	/** Samples per row, border included. */
	int size;
	int cell;
	/** World position of sample (0, 0), the first one inside the border. */
	int x0;
	int z0;

	/**
	 * Samples tile {@code key}: cell centres ({@code corners} false, {@code TILE} samples a side) or cell corners
	 * ({@code TILE + 1} a side), plus {@code border} samples beyond each edge. With a {@code cache}, the samples it
	 * holds from the neighbouring tiles at this level (the columns a tile shares with them along its edges, and
	 * its border) are copied rather than asked of the source, and this tile's own edges go in for its neighbours.
	 */
	void sample(TerrainSource source, long key, boolean corners, int border, SampleCache cache) {
		int level = TileKey.level(key);
		this.cell = TileKey.cell(level);
		this.border = border;
		int inner = corners ? N + 1 : N;
		this.size = inner + 2 * border;
		int offset = corners ? 0 : this.cell >> 1;
		this.x0 = TileKey.minX(key) + offset;
		this.z0 = TileKey.minZ(key) + offset;
		Arrays.fill(this.known, 0, this.size * this.size, false);
		source.beginTile(key, this.x0, this.z0, this.cell, border, this.size);
		try {
			this.sampleGrid(source, key, corners, border, cache, inner);
		} finally {
			source.endTile();
		}
		this.bareSteep();
	}

	/**
	 * Tops and walls in rock the columns that strip to it where steep ({@link ColumnSample#bareWhenSteep}), where the land
	 * between their neighbours in the grid rises more than the column's {@link ColumnSample#bareRise} over four blocks:
	 * the chunks' cliffs, from afar. Not where the chunks were seen (their own blocks came with them), and not at the
	 * grid's rim, which has no neighbours. A cell's slope is its average, so far out only slopes steep over a whole cell
	 * show as rock, as from afar they do.
	 */
	private void bareSteep() {
		int n = this.size;
		// the neighbours are two cells apart: the rise over four blocks is theirs times 4 / (2 cell)
		float scale = 2.0F / this.cell;
		for (int j = 1; j < n - 1; j++) {
			for (int i = 1; i < n - 1; i++) {
				int k = i + j * n;
				if (!this.bareWhenSteep[k] || this.seen[k] || this.empty[k]) {
					continue;
				}
				float gx = this.ground[k + 1] - this.ground[k - 1];
				float gz = this.ground[k + n] - this.ground[k - n];
				if (Math.max(Math.abs(gx), Math.abs(gz)) * scale <= this.bareRise[k]) {
					continue;
				}
				this.top[k] = this.side[k] = this.soil[k] = this.rock[k];
				this.topMaterial[k] = this.sideMaterial[k] = this.soilMaterial[k] = this.rockMaterial[k];
				this.cover[k] = 0;
			}
		}
	}

	private void sampleGrid(TerrainSource source, long key, boolean corners, int border, SampleCache cache, int inner) {
		int found = cache == null ? 0 : cache.fill(this, source, key, corners);
		ColumnSample s = this.sample;
		for (int j = -border; j < inner + border; j++) {
			for (int i = -border; i < inner + border; i++) {
				int k = this.at(i, j);
				if (this.known[k]) {
					continue;
				}
				int x = this.x0 + i * this.cell;
				int z = this.z0 + j * this.cell;
				s.clear();
				source.sample(x, z, this.cell, s);
				this.ground[k] = s.ground;
				this.bottom[k] = s.bottom;
				this.water[k] = s.water;
				this.waterColor[k] = s.waterColor;
				this.lava[k] = s.lava;
				this.soilDepth[k] = s.soilDepth;
				this.rock[k] = s.rock;
				this.bands[k] = s.bands;
				this.bandHeight[k] = Math.max(1, s.bandHeight);
				this.bandOffset[k] = s.bandOffset;
				this.crown[k] = s.crown;
				this.conifer[k] = s.conifer;
				this.topMaterial[k] = s.topMaterial;
				this.sideMaterial[k] = s.sideMaterial;
				this.soilMaterial[k] = s.soilMaterial;
				this.rockMaterial[k] = s.rockMaterial;
				this.leavesMaterial[k] = s.leavesMaterial;
				this.bandMaterials[k] = s.bands == null ? null : s.bandMaterials;
				this.empty[k] = s.empty;
				boolean lava = s.lava && s.water != Integer.MIN_VALUE && s.water > s.ground;
				this.light[k] = (byte) (lava ? 15 : Math.max(0, Math.min(15, Math.round(s.light))));
				this.biome[k] = s.biome;
				this.seen[k] = s.seen >= 0.5F;
				this.bareWhenSteep[k] = s.bareWhenSteep;
				this.bareRise[k] = s.bareRise;
				this.deckBottom[k] = s.deckBottom;
				this.lowerGround[k] = s.lowerGround;
				this.lowerWater[k] = s.lowerWater;
				this.lowerTop[k] = s.lowerTop;
				this.lowerSide[k] = s.lowerSide;
				this.lowerTopMaterial[k] = s.lowerTopMaterial;
				this.lowerSideMaterial[k] = s.lowerSideMaterial;
				this.lowerWaterColor[k] = s.lowerWaterColor;

				if (!this.variation) {
					this.top[k] = s.top;
					this.side[k] = s.side;
					this.soil[k] = s.soil;
					this.leaves[k] = s.leaves;
					this.leavesTop[k] = s.leavesTop < 0 ? s.leaves : s.leavesTop;
					this.cover[k] = s.cover;
					this.canopy[k] = s.canopy;
					continue;
				}
				// Natural variation, in world space so every tile and level agrees on it.
				float tone = Colors.fbm(SALT_TONE, x, z, 420, this.cell, 3);
				float f = 1 + 0.07F * tone;
				float sat = 1 + 0.12F * Colors.fbm(SALT_TONE + 7, x, z, 300, this.cell, 2);
				this.top[k] = Colors.vary(s.top, f, sat);
				this.side[k] = Colors.vary(s.side, f, sat);
				this.soil[k] = s.soil;
				float cover = s.cover;
				float canopy = s.canopy;
				this.leaves[k] = s.leaves;
				this.leavesTop[k] = s.leavesTop < 0 ? s.leaves : s.leavesTop;
				if (cover > 0 && canopy > 0) {
					float shade = 1 + 0.1F * Colors.fbm(SALT_TONE + 13, x, z, 260, this.cell, 3);
					this.leaves[k] = Colors.vary(s.leaves, shade, sat);
					this.leavesTop[k] = Colors.vary(this.leavesTop[k], shade, sat);
					// clearings and thickets: the biome's density is an average, not a rule
					float patch = Colors.fbm(SALT_COVER, x, z, 380, this.cell, 4);
					cover = Math.min(1, cover * Math.max(0, 1 + 0.85F * patch));
					canopy *= 1 + 0.18F * Colors.fbm(SALT_HEIGHT, x, z, 240, this.cell, 2);
				}
				this.cover[k] = cover;
				this.canopy[k] = canopy;
			}
		}
		if (cache != null) {
			cache.store(this, source, key, corners, found);
		}
	}

	/** Takes sample {@code k} from {@code from}'s sample {@code f}, unless it is already known; true if it was not. */
	boolean reuse(int k, Samples from, int f) {
		if (this.known[k]) {
			return false;
		}
		this.copy(k, from, f);
		this.known[k] = true;
		return true;
	}

	/** Copies sample {@code f} of {@code from} into sample {@code k}: every per-sample field, variation included. */
	void copy(int k, Samples from, int f) {
		this.deckBottom[k] = from.deckBottom[f];
		this.lowerGround[k] = from.lowerGround[f];
		this.lowerWater[k] = from.lowerWater[f];
		this.lowerTop[k] = from.lowerTop[f];
		this.lowerSide[k] = from.lowerSide[f];
		this.lowerTopMaterial[k] = from.lowerTopMaterial[f];
		this.lowerSideMaterial[k] = from.lowerSideMaterial[f];
		this.lowerWaterColor[k] = from.lowerWaterColor[f];
		this.ground[k] = from.ground[f];
		this.bottom[k] = from.bottom[f];
		this.top[k] = from.top[f];
		this.side[k] = from.side[f];
		this.soil[k] = from.soil[f];
		this.soilDepth[k] = from.soilDepth[f];
		this.rock[k] = from.rock[f];
		this.bands[k] = from.bands[f];
		this.bandMaterials[k] = from.bandMaterials[f];
		this.bandHeight[k] = from.bandHeight[f];
		this.bandOffset[k] = from.bandOffset[f];
		this.water[k] = from.water[f];
		this.waterColor[k] = from.waterColor[f];
		this.lava[k] = from.lava[f];
		this.canopy[k] = from.canopy[f];
		this.cover[k] = from.cover[f];
		this.leaves[k] = from.leaves[f];
		this.leavesTop[k] = from.leavesTop[f];
		this.crown[k] = from.crown[f];
		this.conifer[k] = from.conifer[f];
		this.topMaterial[k] = from.topMaterial[f];
		this.sideMaterial[k] = from.sideMaterial[f];
		this.soilMaterial[k] = from.soilMaterial[f];
		this.rockMaterial[k] = from.rockMaterial[f];
		this.leavesMaterial[k] = from.leavesMaterial[f];
		this.empty[k] = from.empty[f];
		this.light[k] = from.light[f];
		this.biome[k] = from.biome[f];
		this.seen[k] = from.seen[f];
		this.bareWhenSteep[k] = from.bareWhenSteep[f];
		this.bareRise[k] = from.bareRise[f];
	}

	int at(int i, int j) {
		return (i + this.border) + (j + this.border) * this.size;
	}

	int x(int i) {
		return this.x0 + i * this.cell;
	}

	int z(int j) {
		return this.z0 + j * this.cell;
	}

	boolean wet(int k) {
		return this.water[k] != Integer.MIN_VALUE && this.water[k] > this.ground[k];
	}

	int rockAt(int k, int y) {
		return ColumnSample.rockAt(this.bands[k], this.bandHeight[k], this.rock[k], y + this.bandOffset[k]);
	}

	int rockMaterialAt(int k, int y) {
		return ColumnSample.rockMaterialAt(this.bandMaterials[k], this.bandHeight[k], this.rockMaterial[k], y + this.bandOffset[k]);
	}

	int rockBetween(int k, int y0, int y1) {
		return ColumnSample.rockBetween(this.bands[k], this.bandHeight[k], this.rock[k], y0 + this.bandOffset[k], y1 + this.bandOffset[k]);
	}
}
