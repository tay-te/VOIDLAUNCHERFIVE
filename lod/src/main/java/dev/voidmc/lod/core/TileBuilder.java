package dev.voidmc.lod.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns one tile of a {@link TerrainSource} into a mesh: samples a (TILE + 2)-square grid of columns (the
 * tile plus a one-cell border, so every edge knows its neighbour without asking another tile), reduces
 * each column to one visible top, and meshes the result as blocks.
 *
 * <p>The mesh is the blocky heightfield Minecraft looks like from afar: a flat top per cell, merged
 * greedily into rectangles wherever neighbours agree on height and colour, and a wall wherever a cell
 * stands above its neighbour, merged along runs. Tile edges get a skirt hanging below the surface, so a
 * coarser neighbour that guessed the ground a little lower never opens a crack to the sky.
 *
 * <p>One builder per worker thread: it owns its scratch arrays and its output grows in place, so a tile
 * costs no allocation beyond the copy handed to the renderer.
 *
 * <p>Vertex format, 12 bytes: {@code short x, y, z, face} then {@code byte r, g, b, a}. x and z are in cells
 * from the tile's corner (0..TILE), y in blocks; face is 0 up, 2 north, 3 south, 4 west, 5 east, with
 * {@link #WATER} or'd in for water surfaces. Quads are four vertices, wound counter-clockwise seen from
 * outside.
 */
public final class TileBuilder {
	public static final int VERTEX_BYTES = 12;
	public static final int QUAD_BYTES = 4 * VERTEX_BYTES;
	public static final int WATER = 8;

	private static final int N = TileKey.TILE;
	private static final int W = N + 2;
	private static final int ROCK = 0x7A7670;

	private final int[] top = new int[W * W];
	private final int[] color = new int[W * W];
	private final byte[] water = new byte[W * W];
	private final boolean[] done = new boolean[N * N];
	private final ColumnSample sample = new ColumnSample();
	private ByteBuffer out = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.nativeOrder());
	private int quads;
	private int minY;
	private int maxY;

	/** Samples and meshes tile {@code key}. The result stays valid until the next call. */
	public Mesh build(TerrainSource source, long key) {
		this.sampleGrid(source, key);
		return this.mesh(TileKey.level(key));
	}

	/** The sampled grid, row-major with the border: for tests and tools. */
	int[] tops() {
		return this.top;
	}

	void sampleGrid(TerrainSource source, long key) {
		int level = TileKey.level(key);
		int cell = TileKey.cell(level);
		int x0 = TileKey.minX(key) - cell;
		int z0 = TileKey.minZ(key) - cell;
		int half = cell >> 1;
		// Heights snap to a quarter of a cell: a 16-block cell steps in 4-block terraces. Sub-quantum
		// relief is invisible at the distance a cell that size is drawn, and snapping it away is what lets
		// far tops merge into large flat quads and drops the one-block walls between them.
		int quantum = Math.max(1, cell >> 2);
		ColumnSample s = this.sample;
		for (int j = 0; j < W; j++) {
			for (int i = 0; i < W; i++) {
				int x = x0 + i * cell + half;
				int z = z0 + j * cell + half;
				s.clear();
				source.sample(x, z, cell, s);
				int k = i + j * W;
				int y = snap(s.ground, quantum);
				int c = s.groundColor;
				if (s.canopy > 0 && s.cover > 0) {
					// A forest is a raised, darker blanket, as tall and as dark as it is dense: individual
					// trees would each cost five quads and are below a pixel wherever the LOD draws.
					float f = Math.min(1, s.cover * 1.25F);
					y += snap(Math.round(s.canopy * (float) Math.sqrt(f)), quantum);
					c = mix(c, s.canopyColor, f);
				}
				if (s.water != Integer.MIN_VALUE && s.water > y) {
					this.top[k] = s.water;
					this.color[k] = quantize(s.waterColor);
					this.water[k] = 1;
				} else {
					this.top[k] = y;
					this.color[k] = quantize(c);
					this.water[k] = 0;
				}
			}
		}
	}

	Mesh mesh(int level) {
		int cell = TileKey.cell(level);
		this.out.clear();
		this.quads = 0;
		this.minY = Integer.MAX_VALUE;
		this.maxY = Integer.MIN_VALUE;
		this.tops(cell);
		int skirt = 4 + 3 * cell;
		this.walls(cell, skirt);
		if (this.quads == 0) {
			this.minY = this.maxY = 0;
		}
		this.out.flip();
		return new Mesh(this.out, this.quads, this.minY, this.maxY);
	}

	/** Greedy rectangles over cells that agree on height, colour and water. */
	private void tops(int cell) {
		java.util.Arrays.fill(this.done, false);
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				if (this.done[i + j * N]) {
					continue;
				}
				int k = (i + 1) + (j + 1) * W;
				int h = this.top[k];
				int c = this.color[k];
				byte wet = this.water[k];
				int w = 1;
				while (i + w < N && !this.done[i + w + j * N] && this.same(k + w, h, c, wet)) {
					w++;
				}
				int d = 1;
				grow:
				while (j + d < N) {
					int row = (i + 1) + (j + d + 1) * W;
					for (int a = 0; a < w; a++) {
						if (this.done[i + a + (j + d) * N] || !this.same(row + a, h, c, wet)) {
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
				int face = wet != 0 ? WATER : 0;
				this.quad(i, h, j, i, h, j + d, i + w, h, j + d, i + w, h, j, face, c);
			}
		}
	}

	private boolean same(int k, int h, int c, byte wet) {
		return this.top[k] == h && this.color[k] == c && this.water[k] == wet;
	}

	/**
	 * A wall on every cell edge that stands above the cell beyond it, merged along runs that share top,
	 * bottom and colour. Edges on the tile's border also hang a skirt below the lower side.
	 */
	private void walls(int cell, int skirt) {
		// north (-z) and south (+z) walls run along x
		for (int dir = 0; dir < 2; dir++) {
			int dz = dir == 0 ? -1 : 1;
			for (int j = 0; j < N; j++) {
				boolean border = dir == 0 ? j == 0 : j == N - 1;
				int edge = dir == 0 ? j : j + 1;
				int runStart = -1;
				int rt = 0, rb = 0, rc = 0;
				for (int i = 0; i <= N; i++) {
					int t = 0, b = 0, c = 0;
					boolean has = false;
					if (i < N) {
						int k = (i + 1) + (j + 1) * W;
						int n = k + dz * W;
						t = this.top[k];
						b = border ? Math.min(t, this.top[n]) - skirt : this.top[n];
						has = t > b;
						if (has) {
							c = this.side(k, t - b, cell);
						}
					}
					if (runStart >= 0 && (!has || t != rt || b != rb || c != rc)) {
						if (dir == 0) {
							this.quad(runStart, rt, edge, i, rt, edge, i, rb, edge, runStart, rb, edge, 2, rc);
						} else {
							this.quad(i, rt, edge, runStart, rt, edge, runStart, rb, edge, i, rb, edge, 3, rc);
						}
						runStart = -1;
					}
					if (has && runStart < 0) {
						runStart = i;
						rt = t;
						rb = b;
						rc = c;
					}
				}
			}
		}
		// west (-x) and east (+x) walls run along z
		for (int dir = 0; dir < 2; dir++) {
			int dx = dir == 0 ? -1 : 1;
			for (int i = 0; i < N; i++) {
				boolean border = dir == 0 ? i == 0 : i == N - 1;
				int edge = dir == 0 ? i : i + 1;
				int runStart = -1;
				int rt = 0, rb = 0, rc = 0;
				for (int j = 0; j <= N; j++) {
					int t = 0, b = 0, c = 0;
					boolean has = false;
					if (j < N) {
						int k = (i + 1) + (j + 1) * W;
						int n = k + dx;
						t = this.top[k];
						b = border ? Math.min(t, this.top[n]) - skirt : this.top[n];
						has = t > b;
						if (has) {
							c = this.side(k, t - b, cell);
						}
					}
					if (runStart >= 0 && (!has || t != rt || b != rb || c != rc)) {
						if (dir == 0) {
							this.quad(edge, rt, j, edge, rt, runStart, edge, rb, runStart, edge, rb, j, 4, rc);
						} else {
							this.quad(edge, rt, runStart, edge, rt, j, edge, rb, j, edge, rb, runStart, 5, rc);
						}
						runStart = -1;
					}
					if (has && runStart < 0) {
						runStart = j;
						rt = t;
						rb = b;
						rc = c;
					}
				}
			}
		}
	}

	/** A wall's colour: the cell's own, weathering to bare rock as the drop grows into a cliff. */
	private int side(int k, int drop, int cell) {
		int c = this.color[k];
		if (this.water[k] != 0) {
			return c;
		}
		float rock = Math.min(1, Math.max(0, (drop - 2.0F * cell) / (6.0F * cell))) * 0.65F;
		return rock > 0 ? quantize(mix(c, ROCK, rock)) : c;
	}

	private void quad(int x0, int y0, int z0, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, int face, int c) {
		this.ensure(QUAD_BYTES);
		this.vertex(x0, y0, z0, face, c);
		this.vertex(x1, y1, z1, face, c);
		this.vertex(x2, y2, z2, face, c);
		this.vertex(x3, y3, z3, face, c);
		this.quads++;
		int lo = Math.min(Math.min(y0, y1), Math.min(y2, y3));
		int hi = Math.max(Math.max(y0, y1), Math.max(y2, y3));
		if (lo < this.minY) {
			this.minY = lo;
		}
		if (hi > this.maxY) {
			this.maxY = hi;
		}
	}

	private void vertex(int x, int y, int z, int face, int c) {
		ByteBuffer b = this.out;
		b.putShort((short) x).putShort((short) y).putShort((short) z).putShort((short) face);
		b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 0xFF);
	}

	private void ensure(int bytes) {
		if (this.out.remaining() < bytes) {
			ByteBuffer bigger = ByteBuffer.allocateDirect(this.out.capacity() * 2).order(ByteOrder.nativeOrder());
			this.out.flip();
			bigger.put(this.out);
			this.out = bigger;
		}
	}

	static int snap(int y, int quantum) {
		return quantum == 1 ? y : Math.floorDiv(y + (quantum >> 1), quantum) * quantum;
	}

	/** Five bits a channel: invisible at the distances LOD draws, and it lets far more cells merge. */
	static int quantize(int c) {
		return c & 0xF8F8F8;
	}

	static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	/**
	 * A built mesh. {@code vertices} is the builder's own buffer: copy it before building another tile.
	 */
	public record Mesh(ByteBuffer vertices, int quads, int minY, int maxY) {
		public int bytes() {
			return this.quads * QUAD_BYTES;
		}
	}
}
