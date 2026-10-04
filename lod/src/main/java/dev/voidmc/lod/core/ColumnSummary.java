package dev.voidmc.lod.core;

/**
 * Sums over a square of known columns, from which one LOD cell is drawn: how many columns are known and how many
 * of them are under water, their heights and colours added up by kind, and the materials most of them are made
 * of. Sums rather than means, so the summary of a square is the plain sum of its quarters however unevenly they
 * are known, and the same columns always give the same cell whichever way they were added up; the means are
 * taken only when a sample is written.
 *
 * <p>A column counts one of two ways. Up close ({@code exact}, cells of a block or two, next to the chunks) a leaf
 * block is a block like any other: a crown is ground, so a tree stands where the tree is. Far out it is a
 * statistic: a forest is a canopy of some height over some share of the ground, which is what the smooth tiles
 * draw.
 *
 * <p>A finished summary is an {@code int[SIZE]}; this is the accumulator that makes and merges them. One per thread.
 */
final class ColumnSummary {
	static final int KNOWN = 0;
	static final int VOID = 1;
	static final int WET = 2;
	static final int CANOPY_N = 3;
	static final int CONIFER_N = 4;
	static final int LAVA_N = 5;
	static final int GROUND_DRY = 6;
	static final int GROUND_WET = 7;
	static final int WATER = 8;
	static final int CANOPY_H = 9;
	static final int SOIL_DEPTH = 10;
	/** Colours, three sums each (red, green, blue). */
	static final int TOP = 11;
	static final int BED = 14;
	static final int SIDE = 17;
	static final int SOIL = 20;
	static final int ROCK = 23;
	static final int WATER_COLOR = 26;
	static final int LEAVES = 29;
	/** Everything before this is a sum; after it, the materials of the commonest surfaces. */
	static final int SUMS = 32;
	static final int M_TOP = 32;
	static final int M_SIDE = 33;
	static final int M_SOIL = 34;
	static final int M_ROCK = 35;
	static final int M_BED = 36;
	static final int M_LEAVES = 37;
	static final int SIZE = 38;
	/** A crown seen from afar is darker than one leaf block in full light: its leaves shade each other. */
	static final float CROWN_SHADE = 0.82F;

	/** The surfaces met while adding columns, with how many columns each covered: the commonest gives the materials. */
	private static final class Tally {
		final Surface[] surfaces = new Surface[16];
		final int[] counts = new int[16];
		int size;

		void add(Surface s) {
			for (int i = 0; i < this.size; i++) {
				if (this.surfaces[i] == s) {
					this.counts[i]++;
					return;
				}
			}
			if (this.size < this.surfaces.length) {
				this.surfaces[this.size] = s;
				this.counts[this.size++] = 1;
			}
		}

		int best() {
			int best = -1;
			for (int i = 0; i < this.size; i++) {
				if (best < 0 || this.counts[i] > this.counts[best]) {
					best = i;
				}
			}
			return best;
		}

		void clear() {
			java.util.Arrays.fill(this.surfaces, 0, this.size, null);
			this.size = 0;
		}
	}

	final int[] s = new int[SIZE];
	private final Tally dry = new Tally();
	/** Columns counted exactly whose top is a crown: their faces are the leaves'. */
	private final Tally crowns = new Tally();
	private final Tally wet = new Tally();
	private final Tally leaves = new Tally();
	/** For merged summaries: the weight behind the materials taken so far. */
	private int bestDry;
	private int bestWet;
	private int bestLeaves;

	void clear() {
		java.util.Arrays.fill(this.s, 0);
		this.dry.clear();
		this.crowns.clear();
		this.wet.clear();
		this.leaves.clear();
		this.bestDry = 0;
		this.bestWet = 0;
		this.bestLeaves = 0;
	}

	int known() {
		return this.s[KNOWN];
	}

	int none() {
		return this.s[VOID];
	}

	/** Adds a packed column ({@link ChunkColumns}) made of {@code surface} (null for the void). */
	void add(int packed, Surface surface, boolean exact) {
		int[] s = this.s;
		if (packed == ChunkColumns.VOID) {
			s[VOID]++;
			return;
		}
		int ground = ChunkColumns.ground(packed);
		int depth = ChunkColumns.depth(packed);
		int canopy = ChunkColumns.canopy(packed);
		s[KNOWN]++;
		if (exact && canopy > 0) {
			// a crown, from close by: leaf blocks down to the trunk's foot, like the chunks show it
			s[GROUND_DRY] += ground + canopy;
			s[SOIL_DEPTH] += canopy + surface.soilDepth();
			color(s, TOP, surface.leaves());
			color(s, SIDE, surface.leaves());
			color(s, SOIL, surface.leaves());
			color(s, ROCK, surface.rock());
			this.crowns.add(surface);
			return;
		}
		if (depth > 0) {
			s[WET]++;
			s[GROUND_WET] += ground;
			s[WATER] += ground + depth;
			s[LAVA_N] += surface.lava() ? 1 : 0;
			color(s, BED, surface.top());
			color(s, WATER_COLOR, surface.waterColor());
			this.wet.add(surface);
			return;
		}
		s[GROUND_DRY] += ground;
		s[SOIL_DEPTH] += surface.soilDepth();
		color(s, TOP, surface.top());
		color(s, SIDE, surface.side());
		color(s, SOIL, surface.soil());
		color(s, ROCK, surface.rock());
		this.dry.add(surface);
		if (canopy > 0) {
			s[CANOPY_N]++;
			s[CANOPY_H] += canopy;
			s[CONIFER_N] += surface.conifer() ? 1 : 0;
			color(s, LEAVES, surface.leaves());
			this.leaves.add(surface);
		}
	}

	private static void color(int[] s, int at, int c) {
		s[at] += (c >> 16) & 0xFF;
		s[at + 1] += (c >> 8) & 0xFF;
		s[at + 2] += c & 0xFF;
	}

	/** Adds a finished summary: its sums, and its materials where it carries more weight than any merged before. */
	void merge(int[] child) {
		int[] s = this.s;
		for (int i = 0; i < SUMS; i++) {
			s[i] += child[i];
		}
		int dry = child[KNOWN] - child[WET];
		if (dry > this.bestDry) {
			this.bestDry = dry;
			s[M_TOP] = child[M_TOP];
			s[M_SIDE] = child[M_SIDE];
			s[M_SOIL] = child[M_SOIL];
			s[M_ROCK] = child[M_ROCK];
		}
		if (child[WET] > this.bestWet) {
			this.bestWet = child[WET];
			s[M_BED] = child[M_BED];
		}
		if (child[CANOPY_N] > this.bestLeaves) {
			this.bestLeaves = child[CANOPY_N];
			s[M_LEAVES] = child[M_LEAVES];
		}
	}

	/** Settles the materials from the columns added, where they outweigh what merged summaries brought. */
	private void materials() {
		int[] s = this.s;
		int d = this.dry.best();
		int c = this.crowns.best();
		int dc = d < 0 ? 0 : this.dry.counts[d];
		int cc = c < 0 ? 0 : this.crowns.counts[c];
		if (cc > dc && cc > this.bestDry) {
			Surface crown = this.crowns.surfaces[c];
			s[M_TOP] = s[M_SIDE] = s[M_SOIL] = crown.leavesMaterial();
			s[M_ROCK] = crown.rockMaterial();
			this.bestDry = cc;
		} else if (dc > this.bestDry) {
			Surface ground = this.dry.surfaces[d];
			s[M_TOP] = ground.topMaterial();
			s[M_SIDE] = ground.sideMaterial();
			s[M_SOIL] = ground.soilMaterial();
			s[M_ROCK] = ground.rockMaterial();
			this.bestDry = dc;
		}
		int w = this.wet.best();
		if (w >= 0 && this.wet.counts[w] > this.bestWet) {
			s[M_BED] = this.wet.surfaces[w].topMaterial();
			this.bestWet = this.wet.counts[w];
		}
		int l = this.leaves.best();
		if (l >= 0 && this.leaves.counts[l] > this.bestLeaves) {
			s[M_LEAVES] = this.leaves.surfaces[l].leavesMaterial();
			this.bestLeaves = this.leaves.counts[l];
		}
	}

	/** The summary so far, as a new array. */
	int[] freeze() {
		this.materials();
		return this.s.clone();
	}

	private int mean(int at, int n) {
		float f = 1.0F / n;
		return Colors.rgb(this.s[at] * f, this.s[at + 1] * f, this.s[at + 2] * f);
	}

	/**
	 * Writes what the known columns average to into {@code out}: water if most of them are under it, otherwise the
	 * dry ground with whatever water there is mixed into its colour (a river narrower than the cell is a thread of
	 * its water's colour), and the share of it under leaves. False if no column is known.
	 */
	boolean write(ColumnSample out) {
		int[] s = this.s;
		int known = s[KNOWN];
		if (known == 0) {
			return false;
		}
		this.materials();
		int wet = s[WET];
		int dry = known - wet;
		if (wet * 2 > known) {
			int water = Math.round(s[WATER] / (float) wet);
			int bed = this.mean(BED, wet);
			out.ground = Math.min(s[GROUND_WET] / (float) wet, water - 0.5F);
			out.water = water;
			out.top = bed;
			out.waterColor = this.mean(WATER_COLOR, wet);
			out.lava = s[LAVA_N] * 2 > wet;
			out.side = dry > 0 ? this.mean(SIDE, dry) : bed;
			out.soil = dry > 0 ? this.mean(SOIL, dry) : bed;
			out.rock = dry > 0 ? this.mean(ROCK, dry) : bed;
			out.soilDepth = dry > 0 ? Math.round(s[SOIL_DEPTH] / (float) dry) : 1;
			out.topMaterial = s[M_BED];
			out.sideMaterial = dry > 0 ? s[M_SIDE] : s[M_BED];
			out.soilMaterial = dry > 0 ? s[M_SOIL] : s[M_BED];
			out.rockMaterial = dry > 0 ? s[M_ROCK] : s[M_BED];
			out.canopy = 0;
			out.cover = 0;
			return true;
		}
		out.ground = s[GROUND_DRY] / (float) dry;
		int top = this.mean(TOP, dry);
		if (wet > 0) {
			// the water in the cell, as it looks from above, mixed in by the share it covers
			int waterColor = this.mean(WATER_COLOR, wet);
			float depth = (s[WATER] - s[GROUND_WET]) / (float) wet;
			int surface = Colors.water(waterColor, this.mean(BED, wet), depth, s[LAVA_N] * 2 > wet);
			top = Colors.mix(top, surface, wet / (float) known);
			out.waterColor = waterColor;
		}
		out.top = top;
		out.side = this.mean(SIDE, dry);
		out.soil = this.mean(SOIL, dry);
		out.rock = this.mean(ROCK, dry);
		out.soilDepth = Math.round(s[SOIL_DEPTH] / (float) dry);
		out.topMaterial = s[M_TOP];
		out.sideMaterial = s[M_SIDE];
		out.soilMaterial = s[M_SOIL];
		out.rockMaterial = s[M_ROCK];
		int trees = s[CANOPY_N];
		if (trees > 0) {
			float canopy = s[CANOPY_H] / (float) trees;
			out.canopy = canopy;
			out.cover = trees / (float) dry;
			out.leaves = Colors.scale(this.mean(LEAVES, trees), CROWN_SHADE);
			out.conifer = s[CONIFER_N] / (float) trees;
			out.crown = 2 + canopy / 7;
			out.leavesMaterial = s[M_LEAVES];
		} else {
			out.canopy = 0;
			out.cover = 0;
		}
		return true;
	}
}
