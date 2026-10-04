package dev.voidmc.lod.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The smooth mesh, for tiles past the point where a block is a pixel: a continuous heightfield over the
 * cell corners, 65 x 65 vertices, with a skirt around the edge. Every smooth tile has the same topology, so
 * all of them share one index buffer ({@link #indices}) and a tile uploads only its vertices: 70 KB, a third
 * of what a blocky tile of the same land costs.
 *
 * <p>Each vertex says what a stair of blocks there looks like averaged over a pixel: the colour of the
 * tops, the colour of the risers (the surface block's side on gentle ground; soil, then rock, as the slope
 * steepens and each step gets taller), and the slope, from which the shader works out how much of each
 * the camera sees. Forests are a canopy raised over the ground as far as they are dense, their tops leaf,
 * their flanks shaded leaf. Valleys darken and ridges catch a little more light, the large-scale version
 * of the corner occlusion the blocky tiles do block by block.
 *
 * <p>A tile whose every vertex would be the same (open sea, a lake: one level, one colour) is the same plane
 * drawn with a coarser grid, {@value #FLAT_VERTICES} vertices instead of {@value #VERTICES}, from the second
 * part of the shared index buffer. It keeps a vertex every {@value #FLAT_STEP} cells rather than four corners,
 * because the vertex shader works out haze and the sky's reflection per vertex: over an eighth of a tile they
 * still interpolate to within a step of colour of the full grid.
 */
final class Smooth {
	private static final int N = TileKey.TILE;
	private static final int V = N + 1;
	static final int VERTICES = V * V + 4 * V;
	static final int INDICES = N * N * 6 + 4 * N * 6;
	/** A flat tile's grid: a vertex every this many cells, and its share of the index buffer, after the full grid's. */
	static final int FLAT_STEP = 8;
	private static final int FV = N / FLAT_STEP + 1;
	static final int FLAT_VERTICES = FV * FV + 4 * FV;
	static final int FLAT_INDICES = (FV - 1) * (FV - 1) * 6 + 4 * (FV - 1) * 6;
	static final int FLAT_FIRST_INDEX = INDICES;
	private static final int MAX = N + 1 + 2 * Samples.MAX_BORDER;

	private final TileBuilder out;
	private final Samples s;
	private final float[] surface = new float[MAX * MAX];
	private final float[] vy = new float[V * V];
	private final int[] vtop = new int[V * V];
	private final int[] vside = new int[V * V];
	private final int[] vgx = new int[V * V];
	private final int[] vgz = new int[V * V];
	private final int[] vflags = new int[V * V];
	/** Per sample over the grid and one beyond (R x R), before softening: colours, slope, water, canopy seen. */
	private static final int R = V + 2;
	private final int[] rtop = new int[R * R];
	private final int[] rside = new int[R * R];
	private final float[] rgx = new float[R * R];
	private final float[] rgz = new float[R * R];
	private final float[] rseen = new float[R * R];
	private final boolean[] rwet = new boolean[R * R];

	Smooth(TileBuilder out, Samples samples) {
		this.out = out;
		this.s = samples;
	}

	/** How high a forest's canopy lifts the surface, seen from afar: as tall as its trees, as far as it is dense. */
	static float blanket(float canopy, float cover) {
		if (canopy <= 0 || cover <= 0) {
			return 0;
		}
		return canopy * (float) Math.sqrt(Math.min(1, cover * 1.25F)) * 0.9F;
	}

	/** Meshes the samples; true if the tile came out flat, drawn with the flat grid's indices. */
	boolean mesh() {
		Samples s = this.s;
		int cell = s.cell;
		int b = s.border;
		for (int j = -b; j < V + b; j++) {
			for (int i = -b; i < V + b; i++) {
				int k = s.at(i, j);
				this.surface[k] = s.wet(k) ? s.water[k] : s.ground[k] + blanket(s.canopy[k], s.cover[k]);
			}
		}
		float inv = 1.0F / (2 * cell);
		// Colours for the grid and a ring of one beyond it, so they can be softened across tile edges alike.
		for (int j = -1; j <= V; j++) {
			for (int i = -1; i <= V; i++) {
				int k = s.at(i, j);
				int r = this.ring(i, j);
				float h = this.surface[k];
				if (s.wet(k)) {
					int c = Colors.water(s.waterColor[k], s.top[k], s.water[k] - s.ground[k], s.lava[k]);
					this.rtop[r] = c;
					this.rside[r] = c;
					this.rgx[r] = 0;
					this.rgz[r] = 0;
					this.rwet[r] = true;
					continue;
				}
				float gx = (this.surface[s.at(i + 1, j)] - this.surface[s.at(i - 1, j)]) * inv;
				float gz = (this.surface[s.at(i, j + 1)] - this.surface[s.at(i, j - 1)]) * inv;
				float slope = (float) Math.sqrt(gx * gx + gz * gz);

				float cover = s.cover[k];
				// seen at an angle, crowns hide more of the floor between them than their footprint
				float seen = s.canopy[k] > 0 ? 1 - (1 - cover) * (1 - cover) : 0;
				int top = Colors.mix(s.top[k], s.leaves[k], seen);
				int side = Colors.mix(this.riser(k, slope, h, cell), Colors.scale(s.leaves[k], 0.8F), seen);

				// valleys a little darker, ridges a little brighter; a canopy shades itself
				float ring = 0;
				int n = 0;
				for (int dz = -2; dz <= 2; dz += 2) {
					for (int dx = -2; dx <= 2; dx += 2) {
						if ((dx != 0 || dz != 0) && Math.abs(i + dx - V / 2) <= V / 2 + s.border && Math.abs(j + dz - V / 2) <= V / 2 + s.border) {
							ring += this.surface[s.at(i + dx, j + dz)];
							n++;
						}
					}
				}
				float cavity = (ring / Math.max(1, n) - h) / (4.0F * cell);
				float light = 1 - 0.3F * Colors.smooth(0, 0.35F, cavity) + 0.05F * Colors.smooth(0, 0.35F, -cavity);
				light *= 1 - 0.1F * seen;
				this.rtop[r] = Colors.scale(top, light);
				this.rside[r] = Colors.scale(side, light);
				this.rgx[r] = gx;
				this.rgz[r] = gz;
				this.rwet[r] = false;
				this.rseen[r] = seen;
			}
		}
		for (int j = 0; j < V; j++) {
			for (int i = 0; i < V; i++) {
				int v = i + j * V;
				int r = this.ring(i, j);
				this.vy[v] = this.surface[s.at(i, j)];
				boolean wet = this.rwet[r];
				// Soften colour edges over the neighbours (the same kind, land or water): a biome border, a snow
				// line, a forest's edge become a short blend instead of following the triangles in a sawtooth.
				float tr = 0, tg = 0, tb = 0, sr = 0, sg = 0, sb = 0, wsum = 0;
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						int o = this.ring(i + dx, j + dz);
						if (this.rwet[o] != wet) {
							continue;
						}
						float w = (dx == 0 ? 2 : 1) * (dz == 0 ? 2 : 1);
						int t = this.rtop[o], sd = this.rside[o];
						tr += Colors.r(t) * w;
						tg += Colors.g(t) * w;
						tb += Colors.b(t) * w;
						sr += Colors.r(sd) * w;
						sg += Colors.g(sd) * w;
						sb += Colors.b(sd) * w;
						wsum += w;
					}
				}
				// and a mottle of a cell, stronger under trees, where crowns and gaps alternate
				float mottle = 1;
				if (!wet) {
					long hash = Colors.hash(0x30771EL, s.x(i), s.z(j));
					mottle = 1 + (Colors.unit(hash, 0) - 0.5F) * (0.08F + 0.16F * this.rseen[r]);
				}
				float f = mottle / wsum;
				this.vtop[v] = Colors.rgb(tr * f, tg * f, tb * f);
				this.vside[v] = Colors.rgb(sr * f, sg * f, sb * f);
				this.vgx[v] = wet ? 128 : encode(this.rgx[r]);
				this.vgz[v] = wet ? 128 : encode(this.rgz[r]);
				this.vflags[v] = wet ? TileBuilder.SMOOTH | TileBuilder.WATER : TileBuilder.SMOOTH;
			}
		}

		float skirt = Math.min(4 + 2 * cell, 256);
		if (this.flat()) {
			// every vertex is vertex 0: the same plane and skirts on a coarser grid
			for (int v = 0; v < FV * FV; v++) {
				this.emit(0, 0, v % FV * FLAT_STEP, v / FV * FLAT_STEP);
			}
			for (int i = 0; i < FV; i++) {
				this.emit(0, skirt, i * FLAT_STEP, 0);
			}
			for (int i = 0; i < FV; i++) {
				this.emit(0, skirt, i * FLAT_STEP, N);
			}
			for (int j = 0; j < FV; j++) {
				this.emit(0, skirt, 0, j * FLAT_STEP);
			}
			for (int j = 0; j < FV; j++) {
				this.emit(0, skirt, N, j * FLAT_STEP);
			}
			return true;
		}
		for (int v = 0; v < V * V; v++) {
			this.emit(v, 0, v % V, v / V);
		}
		for (int i = 0; i < V; i++) {
			this.emit(i, skirt, i, 0);
		}
		for (int i = 0; i < V; i++) {
			this.emit(i + N * V, skirt, i, N);
		}
		for (int j = 0; j < V; j++) {
			this.emit(j * V, skirt, 0, j);
		}
		for (int j = 0; j < V; j++) {
			this.emit(N + j * V, skirt, N, j);
		}
		return false;
	}

	/** Whether every vertex of the grid is the same: height, colours, slopes and flags. */
	private boolean flat() {
		float y = this.vy[0];
		int top = this.vtop[0], side = this.vside[0], gx = this.vgx[0], gz = this.vgz[0], flags = this.vflags[0];
		for (int v = 1; v < V * V; v++) {
			if (this.vy[v] != y || this.vtop[v] != top || this.vside[v] != side || this.vgx[v] != gx || this.vgz[v] != gz
				|| this.vflags[v] != flags) {
				return false;
			}
		}
		return true;
	}

	private int ring(int i, int j) {
		return (i + 1) + (j + 1) * R;
	}

	private void emit(int v, float drop, int i, int j) {
		this.out.light(this.s.at(i, j));
		this.out.vertex(i, this.vy[v] - drop, j, this.vflags[v], this.vtop[v], this.vgx[v], this.vside[v], this.vgz[v]);
	}

	/**
	 * What the risers of a stair at this slope are made of. Under 45 degrees every step is one block, so the
	 * risers are the surface block's side; steeper, each step is {@code slope} blocks tall, one of them the
	 * surface block's side, the next few soil, the rest rock.
	 */
	private int riser(int k, float slope, float y, int cell) {
		Samples s = this.s;
		if (slope <= 1) {
			return s.side[k];
		}
		float soil = Math.min(s.soilDepth[k], slope - 1);
		float rock = slope - 1 - soil;
		// banded rock: the bands this vertex's share of the slope spans, averaged
		int reach = Math.round(cell * slope * 0.5F) + 1;
		int rockColor = s.rockBetween(k, Math.round(y) - 1 - reach, Math.round(y) - 1 + reach);
		float r = (Colors.r(s.side[k]) + Colors.r(s.soil[k]) * soil + Colors.r(rockColor) * rock) / slope;
		float g = (Colors.g(s.side[k]) + Colors.g(s.soil[k]) * soil + Colors.g(rockColor) * rock) / slope;
		float bl = (Colors.b(s.side[k]) + Colors.b(s.soil[k]) * soil + Colors.b(rockColor) * rock) / slope;
		return Colors.rgb(r, g, bl);
	}

	/** A slope (rise per run) in a byte, finest near flat, where the shading is most sensitive to it. */
	static int encode(float g) {
		float a = Math.min(Math.abs(g), 7.9F);
		int e = Math.round((float) Math.sqrt(a * 2048));
		e = Math.min(127, e);
		return 128 + (g < 0 ? -e : e);
	}

	static float decode(int b) {
		int e = b - 128;
		return e * Math.abs(e) / 2048.0F;
	}

	/**
	 * The index buffer every smooth tile shares, 16-bit: the grid as triangles, then the four skirts; then the
	 * same for a flat tile's coarser grid, from {@link #FLAT_FIRST_INDEX}.
	 */
	static ByteBuffer indices() {
		ByteBuffer buf = ByteBuffer.allocateDirect((INDICES + FLAT_INDICES) * 2).order(ByteOrder.nativeOrder());
		grid(buf, N + 1);
		grid(buf, FV);
		buf.flip();
		return buf;
	}

	/** A grid of {@code v} x {@code v} vertices as triangles, then its skirts, as the meshes lay them out. */
	private static void grid(ByteBuffer buf, int v) {
		int n = v - 1;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int a = i + j * v, b = i + (j + 1) * v, c = i + 1 + (j + 1) * v, d = i + 1 + j * v;
				// alternate the diagonal, so ridges show no grain in one direction
				if ((i + j & 1) == 0) {
					tri(buf, a, b, c);
					tri(buf, a, c, d);
				} else {
					tri(buf, a, b, d);
					tri(buf, b, c, d);
				}
			}
		}
		int base = v * v;
		for (int i = 0; i < n; i++) {
			// north edge, facing -z
			int t0 = i, t1 = i + 1, s0 = base + i, s1 = base + i + 1;
			quad(buf, t0, t1, s1, s0);
		}
		for (int i = 0; i < n; i++) {
			// south edge, facing +z
			int t0 = i + n * v, t1 = i + 1 + n * v, s0 = base + v + i, s1 = base + v + i + 1;
			quad(buf, t1, t0, s0, s1);
		}
		for (int j = 0; j < n; j++) {
			// west edge, facing -x
			int t0 = j * v, t1 = (j + 1) * v, s0 = base + 2 * v + j, s1 = base + 2 * v + j + 1;
			quad(buf, t1, t0, s0, s1);
		}
		for (int j = 0; j < n; j++) {
			// east edge, facing +x
			int t0 = n + j * v, t1 = n + (j + 1) * v, s0 = base + 3 * v + j, s1 = base + 3 * v + j + 1;
			quad(buf, t0, t1, s1, s0);
		}
	}

	private static void tri(ByteBuffer buf, int a, int b, int c) {
		buf.putShort((short) a).putShort((short) b).putShort((short) c);
	}

	private static void quad(ByteBuffer buf, int a, int b, int c, int d) {
		tri(buf, a, b, c);
		tri(buf, a, c, d);
	}
}
