package dev.voidmc.lod.core;

import java.util.Arrays;

/**
 * The blocky mesh, for tiles near the chunks: a flat top per cell, merged greedily into rectangles where
 * neighbours agree, and a wall wherever a cell stands above its neighbour, merged along runs. Tile edges
 * hang a skirt below the surface, so a neighbouring tile that put the ground a little lower never opens a
 * crack to the sky.
 *
 * <p>What makes it look like Minecraft rather than coloured boxes:
 * <ul>
 *   <li>Walls are made of what the ground is made of: the surface block's side for the top block (grass
 *       side, snowy grass, sand), the soil under it, then rock, or the rock's bands in badlands and
 *       canyons. A grassy hill's step is a strip of green over dirt, a cliff is stone.</li>
 *   <li>Vanilla's ambient occlusion: each top corner darkens with the neighbours that stand above it, and
 *       each wall darkens along its lowest block, where it meets the floor.</li>
 *   <li>Trees, one crown each ({@link Trees}), on a forest floor.</li>
 * </ul>
 */
final class Blocky {
	private static final int N = TileKey.TILE;
	private static final int W = N + 2;
	/** Six bits a channel: past where the shader's per-block grain hides the step, and it lets tops merge. */
	private static final int COLOR_MASK = 0xFCFCFC;

	private final TileBuilder out;
	private final Samples s;
	private final int[] top = new int[W * W];
	private final int[] ground = new int[W * W];
	private final int[] color = new int[W * W];
	private final int[] crown = new int[W * W];
	/** Where a crown's underside is: below it, the wall is the shade under the trees. */
	private final int[] under = new int[W * W];
	private final int[] side = new int[W * W];
	private final int[] soil = new int[W * W];
	private final int[] rock = new int[W * W];
	private final boolean[] wet = new boolean[W * W];
	private final byte[] ao = new byte[N * N * 4];
	private final boolean[] done = new boolean[N * N];
	private final float[] shade = new float[2];

	Blocky(TileBuilder out, Samples samples) {
		this.out = out;
		this.s = samples;
	}

	void mesh() {
		this.cells();
		this.occlusion();
		this.tops();
		this.walls(4 + 3 * this.s.cell);
	}

	/** Each cell's top, what it is made of, and its tree. */
	private void cells() {
		Samples s = this.s;
		double centre = s.cell == 1 ? 0.5 : 0;
		for (int j = -1; j <= N; j++) {
			for (int i = -1; i <= N; i++) {
				int k = s.at(i, j);
				int g = (int) Math.ceil(s.ground[k] - 1e-3F);
				this.ground[k] = g;
				this.crown[k] = -1;
				this.side[k] = s.side[k] & COLOR_MASK;
				this.soil[k] = s.soil[k] & COLOR_MASK;
				this.rock[k] = s.rock[k] & COLOR_MASK;
				int water = s.water[k];
				if (water != Integer.MIN_VALUE && water > g) {
					this.wet[k] = true;
					this.top[k] = water;
					this.color[k] = Colors.water(s.waterColor[k], s.top[k], water - s.ground[k], s.lava[k]) & COLOR_MASK;
					continue;
				}
				this.wet[k] = false;
				this.top[k] = g;
				this.color[k] = s.top[k] & COLOR_MASK;
				if (s.canopy[k] > 0) {
					float h = Trees.crown(s.x(i) + centre, s.z(j) + centre, s.canopy[k], s.cover[k], s.crown[k], this.shade);
					if (h >= 1) {
						this.top[k] = g + Math.max(1, Math.round(h));
						// one tree's leaves in full light: the looks' colour is a whole crown's, self-shading included
						int leaves = Colors.scale(s.leaves[k], this.shade[0] * 1.15F);
						this.color[k] = leaves & COLOR_MASK;
						// its flanks get Minecraft's face shading in the shader; under the crown it is darker still
						this.crown[k] = leaves & COLOR_MASK;
						this.under[k] = g + Math.round(this.shade[1]);
					}
				}
			}
		}
	}

	/** Vanilla's corner rule: a corner with both sides above it is fully dark, else one step per occluder. */
	private void occlusion() {
		int[] dx = {-1, -1, 1, 1};
		int[] dz = {-1, 1, 1, -1};
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				int k = this.s.at(i, j);
				int base = (i + j * N) * 4;
				int t = this.top[k];
				for (int c = 0; c < 4; c++) {
					if (this.wet[k]) {
						this.ao[base + c] = 0;
						continue;
					}
					boolean s1 = this.top[k + dx[c]] > t;
					boolean s2 = this.top[k + dz[c] * W] > t;
					boolean corner = this.top[k + dx[c] + dz[c] * W] > t;
					this.ao[base + c] = (byte) (s1 && s2 ? 3 : (s1 ? 1 : 0) + (s2 ? 1 : 0) + (corner ? 1 : 0));
				}
			}
		}
	}

	private boolean uniform(int i, int j) {
		int base = (i + j * N) * 4;
		byte a = this.ao[base];
		return this.ao[base + 1] == a && this.ao[base + 2] == a && this.ao[base + 3] == a;
	}

	/** Greedy rectangles over cells that agree on height, colour, water and (flat) occlusion. */
	private void tops() {
		Arrays.fill(this.done, false);
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				if (this.done[i + j * N]) {
					continue;
				}
				int k = this.s.at(i, j);
				int base = (i + j * N) * 4;
				if (!this.uniform(i, j)) {
					// occlusion varies across this cell: it is its own quad, shaded corner by corner
					this.done[i + j * N] = true;
					this.top(i, j, 1, 1, k, this.ao[base], this.ao[base + 1], this.ao[base + 2], this.ao[base + 3]);
					continue;
				}
				byte level = this.ao[base];
				int w = 1;
				while (i + w < N && this.mergeable(i + w, j, k, level)) {
					w++;
				}
				int d = 1;
				grow:
				while (j + d < N) {
					for (int a = 0; a < w; a++) {
						if (!this.mergeable(i + a, j + d, k, level)) {
							break grow;
						}
					}
					d++;
				}
				for (int b = 0; b < d; b++) {
					for (int a = 0; a < w; a++) {
						this.done[i + a + (j + b) * N] = true;
					}
				}
				this.top(i, j, w, d, k, level, level, level, level);
			}
		}
	}

	private boolean mergeable(int i, int j, int k, byte level) {
		if (this.done[i + j * N] || !this.uniform(i, j) || this.ao[(i + j * N) * 4] != level) {
			return false;
		}
		int o = this.s.at(i, j);
		return this.top[o] == this.top[k] && this.color[o] == this.color[k] && this.wet[o] == this.wet[k] && this.out.sameLight(o, k);
	}

	private void top(int i, int j, int w, int d, int k, int a0, int a1, int a2, int a3) {
		int flags = this.wet[k] ? TileBuilder.UP | TileBuilder.WATER : TileBuilder.UP;
		this.out.light(k);
		int y = this.top[k];
		int c = this.color[k];
		int b0 = ao(a0), b1 = ao(a1), b2 = ao(a2), b3 = ao(a3);
		// split along the brighter diagonal, so a dark corner darkens its own triangle, not a stripe
		if (b1 + b3 > b0 + b2) {
			this.out.vertex(i, y, j + d, flags, c, b1, 0, 0);
			this.out.vertex(i + w, y, j + d, flags, c, b2, 0, 0);
			this.out.vertex(i + w, y, j, flags, c, b3, 0, 0);
			this.out.vertex(i, y, j, flags, c, b0, 0, 0);
		} else {
			this.out.vertex(i, y, j, flags, c, b0, 0, 0);
			this.out.vertex(i, y, j + d, flags, c, b1, 0, 0);
			this.out.vertex(i + w, y, j + d, flags, c, b2, 0, 0);
			this.out.vertex(i + w, y, j, flags, c, b3, 0, 0);
		}
	}

	private static int ao(int level) {
		return Math.round(Colors.AO[level] * 255);
	}

	/**
	 * A wall on every cell edge that stands above the cell beyond it, merged along runs of cells built the
	 * same way. Edges on the tile's border also hang a skirt below the lower side.
	 */
	private void walls(int skirt) {
		for (int dir = 0; dir < 4; dir++) {
			boolean alongX = dir < 2;
			int out = switch (dir) {
				case 0 -> -W;
				case 1 -> W;
				case 2 -> -1;
				default -> 1;
			};
			for (int row = 0; row < N; row++) {
				boolean border = dir == 0 || dir == 2 ? row == 0 : row == N - 1;
				int edge = dir == 0 || dir == 2 ? row : row + 1;
				int runStart = -1;
				int rk = 0, rt = 0, rb = 0, rf = 0;
				for (int a = 0; a <= N; a++) {
					int k = 0, t = 0, b = 0, f = 0;
					boolean has = false;
					if (a < N) {
						k = alongX ? this.s.at(a, row) : this.s.at(row, a);
						int n = k + out;
						t = this.top[k];
						f = this.top[n];
						b = border ? Math.min(t, f) - skirt : f;
						has = t > b;
					}
					if (runStart >= 0 && (!has || t != rt || b != rb || f != rf || !this.sameColumn(k, rk))) {
						this.wall(dir, edge, runStart, a, rt, rb, rf, rk);
						runStart = -1;
					}
					if (has && runStart < 0) {
						runStart = a;
						rk = k;
						rt = t;
						rb = b;
						rf = f;
					}
				}
			}
		}
	}

	private boolean sameColumn(int a, int b) {
		if (this.wet[a] != this.wet[b] || !this.out.sameLight(a, b)) {
			return false;
		}
		if (this.wet[a]) {
			return this.color[a] == this.color[b];
		}
		Samples s = this.s;
		return this.ground[a] == this.ground[b] && this.crown[a] == this.crown[b] && (this.crown[a] < 0 || this.under[a] == this.under[b])
			&& this.side[a] == this.side[b]
			&& this.soil[a] == this.soil[b] && this.rock[a] == this.rock[b] && s.soilDepth[a] == s.soilDepth[b]
			&& s.bands[a] == s.bands[b] && s.bandHeight[a] == s.bandHeight[b] && s.bandOffset[a] == s.bandOffset[b];
	}

	/** One run of wall, from {@code t} down to {@code b}, in the bands its column is made of. */
	private void wall(int dir, int edge, int a0, int a1, int t, int b, int floor, int k) {
		this.out.light(k);
		if (this.wet[k]) {
			this.band(dir, edge, a0, a1, t, b, floor, this.color[k]);
			return;
		}
		int g = this.ground[k];
		if (this.crown[k] >= 0) {
			int under = Math.min(t, this.under[k]);
			this.band(dir, edge, a0, a1, t, Math.max(b, under), floor, this.crown[k]);
			this.band(dir, edge, a0, a1, under, Math.max(b, g), floor, Colors.scale(this.crown[k], 0.45F) & COLOR_MASK);
		}
		this.band(dir, edge, a0, a1, Math.min(t, g), Math.max(b, g - 1), floor, this.side[k]);
		int soilBottom = g - 1 - this.s.soilDepth[k];
		this.band(dir, edge, a0, a1, Math.min(t, g - 1), Math.max(b, soilBottom), floor, this.soil[k]);
		int hi = Math.min(t, soilBottom);
		if (hi <= b) {
			return;
		}
		if (this.s.bands[k] == null) {
			this.band(dir, edge, a0, a1, hi, b, floor, this.rock[k]);
			return;
		}
		int bh = this.s.bandHeight[k];
		// banded rock: a quad per run of one colour, down to a couple of bands under the floor; a skirt below
		// that is plain
		int lo = Math.max(b, floor - 2 * bh);
		if (lo >= hi) {
			this.band(dir, edge, a0, a1, hi, b, floor, this.s.rockAt(k, hi - 1) & COLOR_MASK);
			return;
		}
		int y = hi;
		while (y > lo) {
			int c = this.s.rockAt(k, y - 1) & COLOR_MASK;
			int bottom = y - 1;
			while (bottom > lo && (this.s.rockAt(k, bottom - 1) & COLOR_MASK) == c) {
				bottom--;
			}
			this.band(dir, edge, a0, a1, y, bottom, floor, c);
			y = bottom;
		}
		if (lo > b) {
			this.band(dir, edge, a0, a1, lo, b, floor, this.s.rockAt(k, lo - 1) & COLOR_MASK);
		}
	}

	private void band(int dir, int edge, int a0, int a1, int hi, int lo, int floor, int c) {
		if (hi <= lo) {
			return;
		}
		int face = dir + 2;
		int ah = Math.max(0, Math.min(255, hi - floor));
		int al = Math.max(0, Math.min(255, lo - floor));
		TileBuilder o = this.out;
		switch (dir) {
			case 0 -> {
				o.vertex(a0, hi, edge, face, c, ah, 0, 0);
				o.vertex(a1, hi, edge, face, c, ah, 0, 0);
				o.vertex(a1, lo, edge, face, c, al, 0, 0);
				o.vertex(a0, lo, edge, face, c, al, 0, 0);
			}
			case 1 -> {
				o.vertex(a1, hi, edge, face, c, ah, 0, 0);
				o.vertex(a0, hi, edge, face, c, ah, 0, 0);
				o.vertex(a0, lo, edge, face, c, al, 0, 0);
				o.vertex(a1, lo, edge, face, c, al, 0, 0);
			}
			case 2 -> {
				o.vertex(edge, hi, a1, face, c, ah, 0, 0);
				o.vertex(edge, hi, a0, face, c, ah, 0, 0);
				o.vertex(edge, lo, a0, face, c, al, 0, 0);
				o.vertex(edge, lo, a1, face, c, al, 0, 0);
			}
			default -> {
				o.vertex(edge, hi, a0, face, c, ah, 0, 0);
				o.vertex(edge, hi, a1, face, c, ah, 0, 0);
				o.vertex(edge, lo, a1, face, c, al, 0, 0);
				o.vertex(edge, lo, a0, face, c, al, 0, 0);
			}
		}
	}
}
