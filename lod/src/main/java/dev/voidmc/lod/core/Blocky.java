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
 *
 * <p>Columns with no ground at all (the void between End islands) draw nothing, and the land around them
 * reads as what it is, a slab floating over the void: walls hang from its rim down to its underside, and
 * the underside itself is drawn, step by step, for when the camera is below it.
 *
 * <p>Every face also says what block face it is made of (the sample's material ids), so the shader can draw the
 * block textures themselves: tops merge only where they are made of the same thing, and walls break into a
 * band per material.
 */
final class Blocky {
	private static final int N = TileKey.TILE;
	private static final int W = N + 2;
	/** Six bits a channel: past where the shader's per-block grain hides the step, and it lets tops merge. */
	private static final int COLOR_MASK = 0xFCFCFC;
	/** The top of an empty column, and the underside of ground that goes all the way down: lower than anything. */
	private static final int DEEP = Integer.MIN_VALUE / 4;
	/** How far an island's rim hangs when the source cannot say where its underside is. */
	private static final int RIM_DEPTH = 12;
	/**
	 * How deep a shader pack's water shows its bed in detail. Deeper, the bed only needs to be about as deep as it is, so
	 * the water over it darkens as the chunks' does: it steps down in {@link #BED_STEP}s to at most {@link #BED_FLOOR},
	 * and merges into a few big rectangles instead of following every bump of the sea floor.
	 */
	private static final int BED_DEPTH = 12;
	private static final int BED_STEP = 8;
	private static final int BED_FLOOR = 44;

	private final TileBuilder out;
	private final Samples s;
	private final int[] top = new int[W * W];
	private final int[] ground = new int[W * W];
	private final int[] color = new int[W * W];
	/** What each cell's top is made of: its surface block, its tree's leaves, or 0 for water. */
	private final int[] material = new int[W * W];
	private final int[] crown = new int[W * W];
	/** Where a crown's underside is: below it, the wall is the shade under the trees. */
	private final int[] under = new int[W * W];
	private final int[] side = new int[W * W];
	private final int[] soil = new int[W * W];
	private final int[] rock = new int[W * W];
	/** Where the column's ground ends below: an island's underside, or {@link #DEEP}. */
	private final int[] base = new int[W * W];
	private final boolean[] wet = new boolean[W * W];
	private final byte[] ao = new byte[N * N * 4];
	private final boolean[] done = new boolean[N * N];
	private final float[] shade = new float[2];

	Blocky(TileBuilder out, Samples samples) {
		this.out = out;
		this.s = samples;
	}

	/** The world's own trees for this tile, block by block, or null: stand-in crowns ({@link Trees}). */
	private Voxels voxels;
	private byte[] grid = new byte[0];

	void trees(Voxels voxels) {
		this.voxels = voxels;
	}

	void mesh() {
		boolean floating = this.cells();
		this.occlusion();
		this.tops();
		this.walls(4 + 3 * this.s.cell);
		if (this.out.pack()) {
			this.beds(4 + 3 * this.s.cell);
		}
		if (floating) {
			this.floors();
			this.steps();
		}
		if (this.voxels != null && this.voxels.size > 0) {
			this.voxelFaces();
		}
	}

	/**
	 * The trees, block by block: every face of a log or leaf block that is not against another, or the ground, with
	 * vanilla's smooth-lighting corners (each corner darker for each block beside it in the layer it faces, split
	 * along the brighter diagonal), rings on a log's ends and bark on its sides, leaves in their own texture. On a
	 * tile of cells two blocks across, the blocks are taken two by two by two: a cell stands where a few of its eight
	 * blocks are a tree's, a log where they are mostly logs, so a crown keeps its outline and a trunk its place.
	 */
	private void voxelFaces() {
		Voxels v = this.voxels;
		Samples s = this.s;
		int c = s.cell;
		int minX = s.x0 - (c >> 1), minZ = s.z0 - (c >> 1);
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int n = 0; n < v.size; n++) {
			int yc = Math.floorDiv(v.y[n], c);
			lo = Math.min(lo, yc);
			hi = Math.max(hi, yc);
		}
		int layers = hi - lo + 3;
		int base = lo - 1;
		int size = W * W * layers;
		if (this.grid.length < size) {
			this.grid = new byte[size];
			this.leafCount = new short[size];
			this.logCount = new short[size];
			this.leafRep = new int[size];
			this.logRep = new int[size];
		} else {
			Arrays.fill(this.grid, 0, size, (byte) 0);
			Arrays.fill(this.leafCount, 0, size, (short) 0);
			Arrays.fill(this.logCount, 0, size, (short) 0);
		}
		for (int n = 0; n < v.size; n++) {
			int gi = Math.floorDiv(v.x[n] - minX, c) + 1, gj = Math.floorDiv(v.z[n] - minZ, c) + 1;
			if (gi < 1 || gj < 1 || gi > N || gj > N) {
				continue;
			}
			int g = (gi + gj * W) * layers + Math.floorDiv(v.y[n], c) - base;
			if (v.kind[n] == Voxels.LOG) {
				if (this.logCount[g]++ == 0) {
					this.logRep[g] = n;
				}
			} else if (this.leafCount[g]++ == 0) {
				this.leafRep[g] = n;
			}
		}
		// a cell of eight blocks needs two of them to stand: a lone leaf at a crown's rim is under a pixel out there
		int need = c == 1 ? 1 : 2;
		for (int g = 0; g < size; g++) {
			int leaves = this.leafCount[g], logs = this.logCount[g];
			if (leaves + logs >= need) {
				this.grid[g] = logs * 2 >= leaves ? Voxels.LOG : Voxels.LEAVES;
			}
		}
		for (int g = 0; g < size; g++) {
			byte kind = this.grid[g];
			if (kind == 0) {
				continue;
			}
			int yi = g % layers, column = g / layers;
			int gi = column % W, gj = column / W;
			int i = gi - 1, j = gj - 1, yc = yi + base;
			int n = kind == Voxels.LOG ? this.logRep[g] : this.leafRep[g];
			boolean log = kind == Voxels.LOG;
			int axis = v.axis[n];
			int yb = yc * c, yt = yb + c;
			this.out.light(s.at(i, j));
			for (int f = 0; f < 6; f++) {
				int nx = f == 4 ? -1 : f == 5 ? 1 : 0, ny = f == 0 ? 1 : f == 1 ? -1 : 0, nz = f == 2 ? -1 : f == 3 ? 1 : 0;
				if (this.solid(gi + nx, yc + ny, gj + nz, base, layers)) {
					continue;
				}
				// a log's rings on the ends its axis runs through, bark elsewhere
				boolean ring = log && (axis == 0 ? ny != 0 : axis == 1 ? nx != 0 : nz != 0);
				int color = ring || !log ? v.topColor[n] : v.sideColor[n];
				if (log && c > 1) {
					// a trunk thinner than the cell: as much of it bark as the trunk fills, the rest the shade under the crown
					// (a thin trunk is a line of bark between shadows: from afar, mostly the shadow)
					float fill = Math.min(1, this.logCount[g] / (float) (c * c * c / 2));
					color = Colors.scale(color, 0.2F + 0.8F * fill * fill);
				}
				color &= COLOR_MASK;
				int m = TileBuilder.material(ring || !log ? v.topMaterial[n] : v.sideMaterial[n]);
				switch (f) {
					case 0 -> this.voxelQuad(TileBuilder.UP, color, m, base, layers, gi, yc, gj, 0, 1, 0,
						i, yt, j, -1, 0, 0, 0, 0, -1,
						i, yt, j + 1, -1, 0, 0, 0, 0, 1,
						i + 1, yt, j + 1, 1, 0, 0, 0, 0, 1,
						i + 1, yt, j, 1, 0, 0, 0, 0, -1);
					case 1 -> this.voxelQuad(TileBuilder.DOWN, color, m, base, layers, gi, yc, gj, 0, -1, 0,
						i, yb, j, -1, 0, 0, 0, 0, -1,
						i + 1, yb, j, 1, 0, 0, 0, 0, -1,
						i + 1, yb, j + 1, 1, 0, 0, 0, 0, 1,
						i, yb, j + 1, -1, 0, 0, 0, 0, 1);
					case 2 -> this.voxelQuad(2, color, m, base, layers, gi, yc, gj, 0, 0, -1,
						i, yt, j, -1, 0, 0, 0, 1, 0,
						i + 1, yt, j, 1, 0, 0, 0, 1, 0,
						i + 1, yb, j, 1, 0, 0, 0, -1, 0,
						i, yb, j, -1, 0, 0, 0, -1, 0);
					case 3 -> this.voxelQuad(3, color, m, base, layers, gi, yc, gj, 0, 0, 1,
						i + 1, yt, j + 1, 1, 0, 0, 0, 1, 0,
						i, yt, j + 1, -1, 0, 0, 0, 1, 0,
						i, yb, j + 1, -1, 0, 0, 0, -1, 0,
						i + 1, yb, j + 1, 1, 0, 0, 0, -1, 0);
					case 4 -> this.voxelQuad(4, color, m, base, layers, gi, yc, gj, -1, 0, 0,
						i, yt, j + 1, 0, 0, 1, 0, 1, 0,
						i, yt, j, 0, 0, -1, 0, 1, 0,
						i, yb, j, 0, 0, -1, 0, -1, 0,
						i, yb, j + 1, 0, 0, 1, 0, -1, 0);
					default -> this.voxelQuad(5, color, m, base, layers, gi, yc, gj, 1, 0, 0,
						i + 1, yt, j, 0, 0, -1, 0, 1, 0,
						i + 1, yt, j + 1, 0, 0, 1, 0, 1, 0,
						i + 1, yb, j + 1, 0, 0, 1, 0, -1, 0,
						i + 1, yb, j, 0, 0, -1, 0, -1, 0);
				}
			}
		}
	}

	private short[] leafCount = new short[0];
	private short[] logCount = new short[0];
	private int[] leafRep = new int[0];
	private int[] logRep = new int[0];

	/** Whether the cell at grid column (gi, gj) and height y (in cells) is a tree's or the ground's. */
	private boolean solid(int gi, int y, int gj, int base, int layers) {
		if (gi < 0 || gj < 0 || gi >= W || gj >= W) {
			return false;
		}
		int yi = y - base;
		if (yi >= 0 && yi < layers && this.grid[(gi + gj * W) * layers + yi] != 0) {
			return true;
		}
		// the ground: a cell of blocks whose middle is under it (y in cells of the tile's size)
		int k = gi + gj * W;
		int c = this.s.cell;
		return !this.s.empty[k] && y * c + (c >> 1) < this.ground[k];
	}

	/**
	 * One face of a tree's block at grid (gi, y, gj) facing (nx, ny, nz): four corners, each its position and the two
	 * directions along the face toward it, shaded by vanilla's corner rule in the layer the face looks into.
	 */
	private void voxelQuad(int face, int color, int m, int base, int layers, int gi, int y, int gj, int nx, int ny, int nz,
		int x0, int y0, int z0, int a0x, int a0y, int a0z, int c0x, int c0y, int c0z,
		int x1, int y1, int z1, int a1x, int a1y, int a1z, int c1x, int c1y, int c1z,
		int x2, int y2, int z2, int a2x, int a2y, int a2z, int c2x, int c2y, int c2z,
		int x3, int y3, int z3, int a3x, int a3y, int a3z, int c3x, int c3y, int c3z) {
		int li = gi + nx, ly = y + ny, lj = gj + nz;
		int l0 = this.corner(li, ly, lj, a0x, a0y, a0z, c0x, c0y, c0z, base, layers);
		int l1 = this.corner(li, ly, lj, a1x, a1y, a1z, c1x, c1y, c1z, base, layers);
		int l2 = this.corner(li, ly, lj, a2x, a2y, a2z, c2x, c2y, c2z, base, layers);
		int l3 = this.corner(li, ly, lj, a3x, a3y, a3z, c3x, c3y, c3z, base, layers);
		int k0 = Colors.scale(color, Colors.AO[l0]), k1 = Colors.scale(color, Colors.AO[l1]);
		int k2 = Colors.scale(color, Colors.AO[l2]), k3 = Colors.scale(color, Colors.AO[l3]);
		TileBuilder o = this.out;
		// split along the brighter diagonal, as vanilla's smooth lighting does
		if (l1 + l3 < l0 + l2) {
			o.vertex(x1, y1, z1, face, k1, 255, m, 0);
			o.vertex(x2, y2, z2, face, k2, 255, m, 0);
			o.vertex(x3, y3, z3, face, k3, 255, m, 0);
			o.vertex(x0, y0, z0, face, k0, 255, m, 0);
		} else {
			o.vertex(x0, y0, z0, face, k0, 255, m, 0);
			o.vertex(x1, y1, z1, face, k1, 255, m, 0);
			o.vertex(x2, y2, z2, face, k2, 255, m, 0);
			o.vertex(x3, y3, z3, face, k3, 255, m, 0);
		}
	}

	/** Vanilla's corner rule: both sides taken is fully dark, else a step for each side and the corner. */
	private int corner(int li, int ly, int lj, int ax, int ay, int az, int cx, int cy, int cz, int base, int layers) {
		boolean s1 = this.solid(li + ax, ly + ay, lj + az, base, layers);
		boolean s2 = this.solid(li + cx, ly + cy, lj + cz, base, layers);
		if (s1 && s2) {
			return 3;
		}
		boolean c = this.solid(li + ax + cx, ly + ay + cy, lj + az + cz, base, layers);
		return (s1 ? 1 : 0) + (s2 ? 1 : 0) + (c ? 1 : 0);
	}

	/** Each cell's top, what it is made of, and its tree. True if any of the tile's ground floats over the void. */
	private boolean cells() {
		Samples s = this.s;
		double centre = s.cell == 1 ? 0.5 : 0;
		// Tree crowns in steps of a quarter of a cell: where cells are a few pixels across, a smaller step is under a
		// pixel, and crowns that come out level need no wall between them. (Not the ground: rounding it lifts a beach a
		// block above the sea a whole step out of it.)
		int q = Math.max(1, s.cell / 4);
		boolean floating = false;
		for (int j = -1; j <= N; j++) {
			for (int i = -1; i <= N; i++) {
				int k = s.at(i, j);
				this.crown[k] = -1;
				if (s.empty[k]) {
					this.ground[k] = this.top[k] = this.base[k] = DEEP;
					this.wet[k] = false;
					continue;
				}
				int g = (int) Math.ceil(s.ground[k] - 1e-3F);
				this.ground[k] = g;
				this.base[k] = Float.isInfinite(s.bottom[k]) ? DEEP : Math.min(g - 1, (int) Math.floor(s.bottom[k]));
				floating |= this.base[k] != DEEP && i >= 0 && j >= 0 && i < N && j < N;
				this.side[k] = s.side[k] & COLOR_MASK;
				this.soil[k] = s.soil[k] & COLOR_MASK;
				this.rock[k] = s.rock[k] & COLOR_MASK;
				int water = s.water[k];
				if (water != Integer.MIN_VALUE && water > g) {
					this.wet[k] = true;
					this.top[k] = water;
					this.color[k] = Colors.water(s.waterColor[k], s.top[k], water - s.ground[k], s.lava[k]) & COLOR_MASK;
					this.material[k] = 0;
					continue;
				}
				this.wet[k] = false;
				this.top[k] = g;
				this.color[k] = s.top[k] & COLOR_MASK;
				this.material[k] = s.topMaterial[k];
				if (s.canopy[k] > 0 && this.voxels == null) {
					float h = Trees.crown(s.x(i) + centre, s.z(j) + centre, s.canopy[k], s.cover[k], s.crown[k], s.conifer[k], this.shade);
					int height = q == 1 ? Math.max(1, Math.round(h)) : Math.round(h / q) * q;
					if (h >= 1 && height == 0) {
						// a crown lower than a step: the forest's roof, flat, in its leaves' colour
						this.color[k] = Colors.scale(s.leaves[k], this.shade[0] * 1.15F) & COLOR_MASK;
					} else if (h >= 1) {
						this.top[k] = g + height;
						// one tree's leaves in full light: the looks' colour is a whole crown's, self-shading included
						int leaves = Colors.scale(s.leaves[k], this.shade[0] * 1.15F);
						// its top as it is from above: in a cold forest, the snow on the boughs
						this.color[k] = (s.leavesTop[k] == s.leaves[k] ? leaves : Colors.scale(s.leavesTop[k], this.shade[0] * 1.15F)) & COLOR_MASK;
						this.material[k] = s.leavesMaterial[k];
						// its flanks get Minecraft's face shading in the shader; under the crown it is darker still
						this.crown[k] = leaves & COLOR_MASK;
						this.under[k] = g + Math.round(this.shade[1]);
					}
				}
			}
		}
		return floating;
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

	/** Greedy rectangles over cells that agree on height, colour, material, water and (flat) occlusion. */
	private void tops() {
		Arrays.fill(this.done, false);
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				if (this.done[i + j * N]) {
					continue;
				}
				int k = this.s.at(i, j);
				int base = (i + j * N) * 4;
				if (this.s.empty[k]) {
					this.done[i + j * N] = true;
					continue;
				}
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
		int o = this.s.at(i, j);
		if (this.done[i + j * N] || this.s.empty[o] || !this.uniform(i, j) || this.ao[(i + j * N) * 4] != level) {
			return false;
		}
		return this.top[o] == this.top[k] && this.color[o] == this.color[k] && this.wet[o] == this.wet[k]
			&& (this.out.compact() || this.material[o] == this.material[k]) && this.out.sameLight(o, k);
	}

	private void top(int i, int j, int w, int d, int k, int a0, int a1, int a2, int a3) {
		int flags = this.wet[k] ? TileBuilder.UP | TileBuilder.WATER : TileBuilder.UP;
		this.out.light(k);
		int y = this.top[k];
		int c = this.color[k];
		int m = TileBuilder.material(this.material[k]);
		int b0 = ao(a0), b1 = ao(a1), b2 = ao(a2), b3 = ao(a3);
		// split along the brighter diagonal, so a dark corner darkens its own triangle, not a stripe
		if (b1 + b3 > b0 + b2) {
			this.out.vertex(i, y, j + d, flags, c, b1, m, 0);
			this.out.vertex(i + w, y, j + d, flags, c, b2, m, 0);
			this.out.vertex(i + w, y, j, flags, c, b3, m, 0);
			this.out.vertex(i, y, j, flags, c, b0, m, 0);
		} else {
			this.out.vertex(i, y, j, flags, c, b0, m, 0);
			this.out.vertex(i, y, j + d, flags, c, b1, m, 0);
			this.out.vertex(i + w, y, j + d, flags, c, b2, m, 0);
			this.out.vertex(i + w, y, j, flags, c, b3, m, 0);
		}
	}

	private static int ao(int level) {
		return Math.round(Colors.AO[level] * 255);
	}

	/**
	 * A wall on every cell edge that stands above the cell beyond it, merged along runs of cells built the
	 * same way. Edges on the tile's border also hang a skirt below the lower side. Over the void, the wall
	 * hangs from the rim down to the island's underside.
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
						// for a pack, whose water shows what is under it, a shore goes down to the bed beyond, not the surface
						f = this.out.pack() && !this.wet[k] && this.wet[n] ? this.bed(n) : this.top[n];
						if (this.s.empty[n]) {
							// the rim of an island: down to its underside, with no floor to darken toward
							b = this.base[k] != DEEP ? this.base[k] : this.ground[k] - RIM_DEPTH;
							f = b - 256;
						} else {
							// water's skirt needs only a block: the surface beyond is flat, and a deep one shows through
							// vanilla's translucent water where the LOD meets the chunks
							b = Math.max(border ? Math.min(t, f) - (this.wet[k] ? 1 : skirt) : f, this.base[k]);
						}
						has = !this.s.empty[k] && t > b;
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

	/**
	 * The bed under the water, for a shader pack: its water program blends the surface over whatever lies beneath, so the
	 * LOD draws the floor the water stands on, as the chunks have it, merged into rectangles where it is level, with
	 * walls of rock where it steps down (and a skirt at the tile's edge). Deeper than {@link #BED_DEPTH} the floor goes
	 * down in steps. Our own renderer draws water opaque and skips it.
	 */
	private void beds(int skirt) {
		Samples s = this.s;
		Arrays.fill(this.done, false);
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				int k = s.at(i, j);
				if (this.done[i + j * N] || !this.wet[k] || s.empty[k]) {
					continue;
				}
				int w = 1;
				while (i + w < N && this.bedMergeable(i + w, j, k)) {
					w++;
				}
				int d = 1;
				grow:
				while (j + d < N) {
					for (int a = 0; a < w; a++) {
						if (!this.bedMergeable(i + a, j + d, k)) {
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
				int y = this.bed(k);
				int c = s.top[k] & COLOR_MASK;
				int m = TileBuilder.material(s.topMaterial[k]);
				int ao = ao(0);
				this.out.light(k);
				this.out.vertex(i, y, j, TileBuilder.UP, c, ao, m, 0);
				this.out.vertex(i, y, j + d, TileBuilder.UP, c, ao, m, 0);
				this.out.vertex(i + w, y, j + d, TileBuilder.UP, c, ao, m, 0);
				this.out.vertex(i + w, y, j, TileBuilder.UP, c, ao, m, 0);
			}
		}
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
						k = alongX ? s.at(a, row) : s.at(row, a);
						if (this.wet[k] && !s.empty[k]) {
							int n = k + out;
							t = this.bed(k);
							// the floor beyond: another bed, or the land's own top (its walls come down to this bed already)
							f = s.empty[n] ? t - skirt : this.wet[n] ? this.bed(n) : Math.min(t, this.top[n]);
							b = border ? Math.min(t, f) - skirt : f;
							has = t > b;
						}
					}
					if (runStart >= 0 && (!has || t != rt || b != rb || f != rf || this.rock[k] != this.rock[rk]
						|| s.rockMaterial[k] != s.rockMaterial[rk] || !this.out.sameLight(k, rk))) {
						this.out.light(rk);
						this.band(dir, edge, runStart, a, rt, rb, rf, this.rock[rk], s.rockMaterial[rk]);
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

	/** The height of the bed a pack's water shows over column {@code k}: its ground, or a stepped floor if it lies deep. */
	private int bed(int k) {
		int depth = this.top[k] - this.ground[k];
		if (depth <= BED_DEPTH) {
			return this.ground[k];
		}
		return this.top[k] - (BED_DEPTH + (Math.min(depth, BED_FLOOR) - BED_DEPTH) / BED_STEP * BED_STEP);
	}

	private boolean deepBed(int k) {
		return this.ground[k] < this.top[k] - BED_DEPTH;
	}

	private boolean bedMergeable(int i, int j, int k) {
		int o = this.s.at(i, j);
		if (this.done[i + j * N] || !this.wet[o] || this.s.empty[o] || this.bed(o) != this.bed(k)) {
			return false;
		}
		// a floor too deep to see takes the colour and light of its rectangle's first column
		return this.deepBed(o) && this.deepBed(k) || (this.s.top[o] & COLOR_MASK) == (this.s.top[k] & COLOR_MASK)
			&& this.s.topMaterial[o] == this.s.topMaterial[k] && this.out.sameLight(o, k);
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
			&& (this.crown[a] < 0 || s.leavesMaterial[a] == s.leavesMaterial[b])
			&& this.side[a] == this.side[b] && s.sideMaterial[a] == s.sideMaterial[b]
			&& this.soil[a] == this.soil[b] && s.soilMaterial[a] == s.soilMaterial[b]
			&& this.rock[a] == this.rock[b] && s.rockMaterial[a] == s.rockMaterial[b] && s.soilDepth[a] == s.soilDepth[b]
			&& s.bands[a] == s.bands[b] && s.bandMaterials[a] == s.bandMaterials[b] && s.bandHeight[a] == s.bandHeight[b]
			&& s.bandOffset[a] == s.bandOffset[b];
	}

	/** One run of wall, from {@code t} down to {@code b}, in the bands its column is made of. */
	private void wall(int dir, int edge, int a0, int a1, int t, int b, int floor, int k) {
		this.out.light(k);
		if (this.wet[k]) {
			// water's own face, flagged as water: a skirt under the surface at a tile's edge shows through
			// vanilla's translucent water at the seam, and must mirror the sky as the surface beside it does,
			// not stand there as a dark band
			this.band(dir, edge, a0, a1, t, b, floor, this.color[k], 0, TileBuilder.WATER);
			return;
		}
		int g = this.ground[k];
		if (this.crown[k] >= 0) {
			int under = Math.min(t, this.under[k]);
			int shade = Colors.scale(this.crown[k], 0.45F) & COLOR_MASK;
			if (this.s.cell >= 4 && Math.max(b, g) < under) {
				// where cells are 4 blocks or more, the crown's flank and the shade under it are a pixel or two each: one
				// face, its colour running from the leaves down into the shade (trees are most of a far forest's faces)
				this.band(dir, edge, a0, a1, t, Math.max(b, g), floor, this.crown[k], shade, this.s.leavesMaterial[k], 0);
			} else {
				this.band(dir, edge, a0, a1, t, Math.max(b, under), floor, this.crown[k], this.s.leavesMaterial[k]);
				this.band(dir, edge, a0, a1, under, Math.max(b, g), floor, shade, this.s.leavesMaterial[k]);
			}
		}
		// Bands thinner than about a quarter of a cell are under a pixel where the cells are a few pixels across:
		// past that they only cost quads. The one-block rim of the surface block goes first, then the soil, then
		// rock strata, and what is left is drawn in one quad, in the colour of the rock it spans.
		int cell = this.s.cell;
		int soilTop = g;
		if (cell < 8) {
			this.band(dir, edge, a0, a1, Math.min(t, g), Math.max(b, g - 1), floor, this.side[k], this.s.sideMaterial[k]);
			soilTop = g - 1;
		}
		int soilBottom = g - 1 - this.s.soilDepth[k];
		int rockTop = soilTop;
		if ((soilTop - soilBottom) * 4 >= cell) {
			this.band(dir, edge, a0, a1, Math.min(t, soilTop), Math.max(b, soilBottom), floor, this.soil[k], this.s.soilMaterial[k]);
			rockTop = soilBottom;
		}
		int hi = Math.min(t, rockTop);
		if (hi <= b) {
			return;
		}
		if (this.s.bands[k] == null) {
			this.band(dir, edge, a0, a1, hi, b, floor, this.rock[k], this.s.rockMaterial[k]);
			return;
		}
		if (this.s.bandHeight[k] * 4 < cell) {
			this.band(dir, edge, a0, a1, hi, b, floor, this.s.rockBetween(k, b, hi) & COLOR_MASK);
			return;
		}
		int bh = this.s.bandHeight[k];
		// banded rock: a quad per run of one colour and material, down to a couple of bands under the floor; a skirt
		// below that is plain
		int lo = Math.max(b, floor - 2 * bh);
		if (lo >= hi) {
			this.band(dir, edge, a0, a1, hi, b, floor, this.s.rockAt(k, hi - 1) & COLOR_MASK, this.s.rockMaterialAt(k, hi - 1));
			return;
		}
		int y = hi;
		while (y > lo) {
			int c = this.s.rockAt(k, y - 1) & COLOR_MASK;
			int m = this.s.rockMaterialAt(k, y - 1);
			int bottom = y - 1;
			while (bottom > lo && (this.s.rockAt(k, bottom - 1) & COLOR_MASK) == c && this.s.rockMaterialAt(k, bottom - 1) == m) {
				bottom--;
			}
			this.band(dir, edge, a0, a1, y, bottom, floor, c, m);
			y = bottom;
		}
		if (lo > b) {
			this.band(dir, edge, a0, a1, lo, b, floor, this.s.rockAt(k, lo - 1) & COLOR_MASK, this.s.rockMaterialAt(k, lo - 1));
		}
	}

	/** The undersides of floating ground, facing down: greedy rectangles over cells that end at the same height. */
	private void floors() {
		Arrays.fill(this.done, false);
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				int k = this.s.at(i, j);
				if (this.done[i + j * N] || this.base[k] == DEEP) {
					continue;
				}
				int y = this.base[k];
				int c = this.s.rockAt(k, y) & COLOR_MASK;
				int w = 1;
				while (i + w < N && this.floorMergeable(i + w, j, y, c)) {
					w++;
				}
				int d = 1;
				grow:
				while (j + d < N) {
					for (int a = 0; a < w; a++) {
						if (!this.floorMergeable(i + a, j + d, y, c)) {
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
				this.out.unlit();
				this.out.vertex(i, y, j, TileBuilder.DOWN, c, 255, 0, 0);
				this.out.vertex(i + w, y, j, TileBuilder.DOWN, c, 255, 0, 0);
				this.out.vertex(i + w, y, j + d, TileBuilder.DOWN, c, 255, 0, 0);
				this.out.vertex(i, y, j + d, TileBuilder.DOWN, c, 255, 0, 0);
			}
		}
	}

	private boolean floorMergeable(int i, int j, int y, int c) {
		int o = this.s.at(i, j);
		return !this.done[i + j * N] && this.base[o] == y && (this.s.rockAt(o, y) & COLOR_MASK) == c;
	}

	/**
	 * The steps of an underside: where a floating column reaches lower than its neighbour, the face between
	 * them, from the neighbour's underside down to its own, merged along runs like the walls above.
	 */
	private void steps() {
		for (int dir = 0; dir < 4; dir++) {
			boolean alongX = dir < 2;
			int out = switch (dir) {
				case 0 -> -W;
				case 1 -> W;
				case 2 -> -1;
				default -> 1;
			};
			for (int row = 0; row < N; row++) {
				int edge = dir == 0 || dir == 2 ? row : row + 1;
				int runStart = -1;
				int rt = 0, rb = 0, rc = 0;
				for (int a = 0; a <= N; a++) {
					int t = 0, b = 0, c = 0;
					boolean has = false;
					if (a < N) {
						int k = alongX ? this.s.at(a, row) : this.s.at(row, a);
						int n = k + out;
						if (this.base[k] != DEEP && this.base[n] != DEEP) {
							t = Math.min(this.base[n], this.ground[k]);
							b = this.base[k];
							c = this.s.rockAt(k, b) & COLOR_MASK;
							has = t > b;
						}
					}
					if (runStart >= 0 && (!has || t != rt || b != rb || c != rc)) {
						this.out.unlit();
						this.band(dir, edge, runStart, a, rt, rb, rb - 256, rc);
						runStart = -1;
					}
					if (has && runStart < 0) {
						runStart = a;
						rt = t;
						rb = b;
						rc = c;
					}
				}
			}
		}
	}

	private void band(int dir, int edge, int a0, int a1, int hi, int lo, int floor, int c) {
		this.band(dir, edge, a0, a1, hi, lo, floor, c, 0, 0);
	}

	private void band(int dir, int edge, int a0, int a1, int hi, int lo, int floor, int c, int material) {
		this.band(dir, edge, a0, a1, hi, lo, floor, c, material, 0);
	}

	/** One band of wall in colour {@code c}, made of {@code material}, with {@code water} flagging water's own faces. */
	private void band(int dir, int edge, int a0, int a1, int hi, int lo, int floor, int c, int material, int water) {
		this.band(dir, edge, a0, a1, hi, lo, floor, c, c, material, water);
	}

	/** A band whose colour runs from {@code c} along its top to {@code cl} along its foot. */
	private void band(int dir, int edge, int a0, int a1, int hi, int lo, int floor, int c, int cl, int material, int water) {
		if (hi <= lo) {
			return;
		}
		int face = dir + 2 | water;
		int ah = Math.max(0, Math.min(255, hi - floor));
		int al = Math.max(0, Math.min(255, lo - floor));
		int m = TileBuilder.material(material);
		TileBuilder o = this.out;
		switch (dir) {
			case 0 -> {
				o.vertex(a0, hi, edge, face, c, ah, m, 0);
				o.vertex(a1, hi, edge, face, c, ah, m, 0);
				o.vertex(a1, lo, edge, face, cl, al, m, 0);
				o.vertex(a0, lo, edge, face, cl, al, m, 0);
			}
			case 1 -> {
				o.vertex(a1, hi, edge, face, c, ah, m, 0);
				o.vertex(a0, hi, edge, face, c, ah, m, 0);
				o.vertex(a0, lo, edge, face, cl, al, m, 0);
				o.vertex(a1, lo, edge, face, cl, al, m, 0);
			}
			case 2 -> {
				o.vertex(edge, hi, a1, face, c, ah, m, 0);
				o.vertex(edge, hi, a0, face, c, ah, m, 0);
				o.vertex(edge, lo, a0, face, cl, al, m, 0);
				o.vertex(edge, lo, a1, face, cl, al, m, 0);
			}
			default -> {
				o.vertex(edge, hi, a0, face, c, ah, m, 0);
				o.vertex(edge, hi, a1, face, c, ah, m, 0);
				o.vertex(edge, lo, a1, face, cl, al, m, 0);
				o.vertex(edge, lo, a0, face, cl, al, m, 0);
			}
		}
	}
}
