package dev.voidmc.lod.core;

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

	final float[] ground = new float[MAX * MAX];
	final float[] bottom = new float[MAX * MAX];
	final int[] top = new int[MAX * MAX];
	final int[] side = new int[MAX * MAX];
	final int[] soil = new int[MAX * MAX];
	final int[] soilDepth = new int[MAX * MAX];
	final int[] rock = new int[MAX * MAX];
	final int[][] bands = new int[MAX * MAX][];
	final int[] bandHeight = new int[MAX * MAX];
	final int[] bandOffset = new int[MAX * MAX];
	final int[] water = new int[MAX * MAX];
	final int[] waterColor = new int[MAX * MAX];
	final boolean[] lava = new boolean[MAX * MAX];
	final float[] canopy = new float[MAX * MAX];
	final float[] cover = new float[MAX * MAX];
	final int[] leaves = new int[MAX * MAX];
	/** The crowns' colour from above: the leaves', or snow on them. */
	final int[] leavesTop = new int[MAX * MAX];
	final float[] crown = new float[MAX * MAX];
	final float[] conifer = new float[MAX * MAX];
	final int[] topMaterial = new int[MAX * MAX];
	final int[] sideMaterial = new int[MAX * MAX];
	final int[] soilMaterial = new int[MAX * MAX];
	final int[] rockMaterial = new int[MAX * MAX];
	final int[] leavesMaterial = new int[MAX * MAX];
	final boolean[] empty = new boolean[MAX * MAX];

	private final ColumnSample sample = new ColumnSample();
	private final boolean variation;
	int border;

	Samples(boolean variation) {
		this.variation = variation;
	}

	/** Samples per row, border included. */
	int size;
	int cell;
	/** World position of sample (0, 0), the first one inside the border. */
	int x0;
	int z0;

	/**
	 * Samples tile {@code key}: cell centres ({@code corners} false, {@code TILE} samples a side) or cell corners
	 * ({@code TILE + 1} a side), plus {@code border} samples beyond each edge.
	 */
	void sample(TerrainSource source, long key, boolean corners, int border) {
		int level = TileKey.level(key);
		this.cell = TileKey.cell(level);
		this.border = border;
		int inner = corners ? N + 1 : N;
		this.size = inner + 2 * border;
		int offset = corners ? 0 : this.cell >> 1;
		this.x0 = TileKey.minX(key) + offset;
		this.z0 = TileKey.minZ(key) + offset;
		ColumnSample s = this.sample;
		for (int j = -border; j < inner + border; j++) {
			for (int i = -border; i < inner + border; i++) {
				int x = this.x0 + i * this.cell;
				int z = this.z0 + j * this.cell;
				s.clear();
				source.sample(x, z, this.cell, s);
				int k = this.at(i, j);
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
				this.empty[k] = s.empty;

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

	int rockBetween(int k, int y0, int y1) {
		return ColumnSample.rockBetween(this.bands[k], this.bandHeight[k], this.rock[k], y0 + this.bandOffset[k], y1 + this.bandOffset[k]);
	}
}
