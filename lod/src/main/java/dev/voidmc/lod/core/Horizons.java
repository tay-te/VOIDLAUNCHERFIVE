package dev.voidmc.lod.core;

/**
 * How much of the sky each column of a tile sees, and where the sun goes behind the land: the horizon along
 * the four axes, from the tile's own samples nearby and the {@link Heights} grids further out, to
 * {@value #REACH} blocks.
 *
 * <p>Minecraft's sun crosses the sky from east to west straight over the zenith, so the horizons along +x and
 * -x are exactly the elevations below which it is hidden: the shader compares the sun's elevation with them
 * and casts every mountain's shadow, at any time of day, with nothing rebuilt as the sun moves. All four
 * horizons together give how much of the sky a column sees (horizon-based ambient occlusion): a valley
 * between ranges sees a band of sky, a ridge all of it.
 *
 * <p>Each horizon is one sweep per row of samples, keeping the upper convex hull of the land passed so far:
 * the highest line of sight from any column is a tangent to it, found in a few steps. Further out the
 * hull is fed from coarser grids, ever sparser, so a tile costs some twenty thousand lookups however far
 * it looks: a millisecond or two, against tens for sampling it.
 */
final class Horizons {
	/** How far the land is searched for what hides the sun: past it the haze hides any shadow. */
	static final int REACH = 8192;
	/** Far samples per doubling of distance: a cliff's edge is never missed by more than this share of its distance. */
	private static final int PER_OCTAVE = 8;
	private static final int MAX = Samples.N + 1 + 2 * Samples.MAX_BORDER;
	private static final int MAX_FAR = 512;
	private static final int MAX_POINTS = MAX + MAX_FAR;

	/** Per sample: the sun's horizon to the east and to the west, in 255ths of a right angle. */
	final int[] east = new int[MAX * MAX];
	final int[] west = new int[MAX * MAX];
	/** Per sample: how much of the sky the land hides, in 255ths (0 open plain, 255 the bottom of a well). */
	final int[] occlusion = new int[MAX * MAX];

	private final float[] surface = new float[MAX * MAX];
	private final double[] slope = new double[MAX * MAX];
	private final double[] sinSum = new double[MAX * MAX];
	// one line of points, far ones first, and the hull over them
	private final double[] pt = new double[MAX_POINTS];
	private final double[] ph = new double[MAX_POINTS];
	private final int[] pk = new int[MAX_POINTS];
	private final double[] ht = new double[MAX_POINTS];
	private final double[] hh = new double[MAX_POINTS];
	private final double[] farAlong = new double[MAX_FAR];
	private final float[] farH = new float[MAX_FAR];

	/** Works out the horizons of every sample inside {@code s}'s grid, {@code inner} a side. */
	void compute(Samples s, Heights heights, int level, int inner) {
		int b = s.border;
		for (int j = -b; j < inner + b; j++) {
			for (int i = -b; i < inner + b; i++) {
				int k = s.at(i, j);
				this.surface[k] = s.empty[k] ? Float.NaN : Heights.surface(s.ground[k], s.water[k], s.canopy[k], s.cover[k]);
				this.sinSum[k] = 0;
			}
		}
		// beyond the tile's own samples, grids at least a level coarser, which were built before it
		int coarsest = TileKey.MAX_LEVEL;
		int first = Math.min(coarsest, level + 1);
		for (int dir = 0; dir < 4; dir++) {
			for (int line = 0; line < inner; line++) {
				this.sweep(s, heights, dir, line, inner, first, coarsest);
			}
			for (int j = 0; j < inner; j++) {
				for (int i = 0; i < inner; i++) {
					int k = s.at(i, j);
					double m = Math.max(0, this.slope[k]);
					if (dir == 0) {
						this.east[k] = encode(m);
					} else if (dir == 1) {
						this.west[k] = encode(m);
					}
					// sin(atan(m)): the share of the sky's dome, along this axis, that the land covers
					this.sinSum[k] += m / Math.sqrt(1 + m * m);
				}
			}
		}
		for (int j = 0; j < inner; j++) {
			for (int i = 0; i < inner; i++) {
				int k = s.at(i, j);
				this.occlusion[k] = (int) Math.round(Math.min(1, this.sinSum[k] * 0.25) * 255);
			}
		}
	}

	/** The elevation of a slope, rise over run, in 255ths of a right angle. */
	static int encode(double m) {
		return (int) Math.round(Math.atan(m) * (255 / (Math.PI / 2)));
	}

	/**
	 * One row (along x) or column (along z) of samples, looking one way along it: dir 0 east (+x), 1 west, 2
	 * south (+z), 3 north. Positions run along the look direction, so the far land comes first.
	 */
	private void sweep(Samples s, Heights heights, int dir, int line, int inner, int first, int coarsest) {
		int b = s.border;
		int cell = s.cell;
		boolean alongX = dir < 2;
		double sign = dir == 0 || dir == 2 ? 1 : -1;
		// the own sample furthest along the look direction
		int lastOwn = sign > 0 ? inner - 1 + b : -b;
		double edge = alongX ? s.x(lastOwn) : s.z(lastOwn);
		double across = alongX ? s.z(line) : s.x(line);
		// The far land, near to far: every grid column of a level whose cells are about an eighth of the distance,
		// so the samples thin out with distance as the detail that matters does, and a cliff is found within a cell.
		int far = 0;
		double along = edge;
		while (far < MAX_FAR) {
			double dist = Math.abs(along - edge);
			int lvl = first;
			while (lvl < coarsest && TileKey.cell(lvl + 1) * PER_OCTAVE <= dist) {
				lvl++;
			}
			int c = TileKey.cell(lvl);
			// the next grid column of that level past the last point
			along = sign > 0 ? (Math.floor(along / c) + 1) * c : (Math.ceil(along / c) - 1) * c;
			if (Math.abs(along - edge) > REACH) {
				break;
			}
			float h = alongX ? heights.at(along, across, lvl) : heights.at(across, along, lvl);
			if (!Float.isNaN(h)) {
				this.farAlong[far] = along;
				this.farH[far] = h;
				far++;
			}
		}
		int n = 0;
		for (int f = far - 1; f >= 0; f--) {
			this.pt[n] = sign * this.farAlong[f];
			this.ph[n] = this.farH[f];
			this.pk[n] = -1;
			n++;
		}
		// then the tile's own samples, from the far end back
		for (int o = 0; o < inner + 2 * b; o++) {
			int idx = sign > 0 ? inner - 1 + b - o : -b + o;
			int k = alongX ? s.at(idx, line) : s.at(line, idx);
			this.pt[n] = sign * (alongX ? s.x(idx) : s.z(idx));
			this.ph[n] = this.surface[k];
			this.pk[n] = idx >= 0 && idx < inner ? k : -1;
			n++;
		}
		// sweep back toward the camera's side, keeping the upper hull of everything passed
		int hn = 0;
		for (int p = 0; p < n; p++) {
			double t = this.pt[p], h = this.ph[p];
			int k = this.pk[p];
			if (k >= 0) {
				this.slope[k] = Double.isNaN(h) ? 0 : this.highest(hn, t, h);
			}
			if (Double.isNaN(h)) {
				continue;
			}
			// the newest point comes last: drop the points it makes hidden, those under the line from it on
			while (hn >= 2) {
				double ta = this.ht[hn - 2], ha = this.hh[hn - 2], tb = this.ht[hn - 1], hb = this.hh[hn - 1];
				double line2 = h + (ha - h) * (tb - t) / (ta - t);
				if (hb > line2) {
					break;
				}
				hn--;
			}
			this.ht[hn] = t;
			this.hh[hn] = h;
			hn++;
		}
	}

	/** The steepest line of sight from (t, h) to the hull, all of which lies ahead of it. */
	private double highest(int hn, double t, double h) {
		double best = Double.NEGATIVE_INFINITY;
		// slopes rise from the nearest hull point and then fall: walk out while they rise
		for (int q = hn - 1; q >= 0; q--) {
			double m = (this.hh[q] - h) / (this.ht[q] - t);
			if (m < best) {
				break;
			}
			best = m;
		}
		return best == Double.NEGATIVE_INFINITY ? 0 : best;
	}
}
