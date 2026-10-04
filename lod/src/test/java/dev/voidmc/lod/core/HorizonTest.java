package dev.voidmc.lod.core;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Horizon culling against real meshes of rugged synthetic terrain: every point of every tile it hides must be
 * out of sight behind the surfaces of the tiles still drawn, checked by marching the line of sight over them.
 */
class HorizonTest {
	/** Ranges and valleys: ridges a couple of hundred blocks high every few kilometres, hills on them, lakes in the deepest valleys. */
	private static float height(double x, double z) {
		return (float) (90 + 110 * Math.sin(x / 650.0) * Math.cos(z / 800.0) + 35 * Math.sin(x / 170.0 + z / 230.0));
	}

	private static final TerrainSource RIDGES = (x, z, cell, out) -> {
		out.ground = height(x, z);
		if (out.ground < 20) {
			out.water = 20;
		}
		out.top = 0x60A040;
		out.canopy = out.ground < 120 ? 8 : 0;
		out.cover = 0.5F;
	};

	/** The tiles a selection settles on, each built when the selector first asks about it. */
	private static final class World implements LodSelector.Store {
		final Long2ObjectOpenHashMap<Built> tiles = new Long2ObjectOpenHashMap<>();
		final TileBuilder builder = new TileBuilder(false);

		Built get(long key) {
			Built b = this.tiles.get(key);
			if (b == null) {
				b = new Built(key, this.builder.build(RIDGES, key));
				this.tiles.put(key, b);
			}
			return b;
		}

		@Override
		public boolean ready(long key) {
			return true;
		}

		@Override
		public boolean heightRange(long key, int[] out) {
			Built b = this.get(key);
			out[0] = b.minY;
			out[1] = b.maxY;
			return true;
		}

		@Override
		public void want(long key, float priority) {
		}

		@Override
		public void touch(long key) {
		}
	}

	/** A built tile: its range, occluders, and its drawn surface as a height per point. */
	private static final class Built {
		final long key;
		final int minY;
		final int maxY;
		final short[] occluders;
		final boolean smooth;
		final int cell;
		/** Smooth: the grid's heights, {@code grid} a side; blocky: each cell's top. */
		final float[] heights;
		final int grid;

		Built(long key, TileBuilder.Mesh mesh) {
			this.key = key;
			this.minY = mesh.minY();
			this.maxY = mesh.maxY();
			this.occluders = mesh.occluders();
			this.smooth = mesh.smooth();
			this.cell = TileKey.cell(TileKey.level(key));
			ByteBuffer v = mesh.vertices().duplicate().order(ByteOrder.nativeOrder());
			if (this.smooth) {
				this.grid = mesh.firstIndex() == 0 ? TileKey.TILE + 1 : TileKey.TILE / Smooth.FLAT_STEP + 1;
				this.heights = new float[this.grid * this.grid];
				for (int i = 0; i < this.heights.length; i++) {
					this.heights[i] = v.getShort(i * TileBuilder.VERTEX_BYTES + 2) / 8.0F;
				}
			} else {
				this.grid = TileKey.TILE;
				this.heights = new float[TileKey.TILE * TileKey.TILE];
				java.util.Arrays.fill(this.heights, Float.NaN);
				for (int q = 0; q < mesh.vertexCount() / 4; q++) {
					int o = q * 4 * TileBuilder.VERTEX_BYTES;
					if ((v.getShort(o + 6) & 7) != TileBuilder.UP) {
						continue;
					}
					int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
					for (int c = 0; c < 4; c++) {
						int p = o + c * TileBuilder.VERTEX_BYTES;
						x0 = Math.min(x0, v.getShort(p));
						x1 = Math.max(x1, v.getShort(p));
						z0 = Math.min(z0, v.getShort(p + 4));
						z1 = Math.max(z1, v.getShort(p + 4));
					}
					float y = v.getShort(o + 2) / 8.0F;
					for (int j = z0; j < z1; j++) {
						for (int i = x0; i < x1; i++) {
							this.heights[i + j * TileKey.TILE] = y;
						}
					}
				}
			}
		}

		/** The drawn surface's height at a point over the tile. */
		float surface(double x, double z) {
			double fx = (x - TileKey.minX(this.key)) / this.cell;
			double fz = (z - TileKey.minZ(this.key)) / this.cell;
			if (!this.smooth) {
				int i = Math.min(TileKey.TILE - 1, (int) fx), j = Math.min(TileKey.TILE - 1, (int) fz);
				return this.heights[i + j * TileKey.TILE];
			}
			int step = (TileKey.TILE) / (this.grid - 1);
			fx /= step;
			fz /= step;
			int i = Math.min(this.grid - 2, (int) fx), j = Math.min(this.grid - 2, (int) fz);
			float u = (float) (fx - i), w = (float) (fz - j);
			float a = this.heights[i + j * this.grid], b = this.heights[i + (j + 1) * this.grid];
			float c = this.heights[i + 1 + (j + 1) * this.grid], d = this.heights[i + 1 + j * this.grid];
			// the triangles of Smooth.grid: diagonal a-c on even cells, b-d on odd ones
			if ((i + j & 1) == 0) {
				return w >= u ? a + (b - a) * (w - u) + (c - a) * u : a + (d - a) * (u - w) + (c - a) * w;
			}
			return u + w <= 1 ? a + (d - a) * u + (b - a) * w : c + (b - c) * (1 - u) + (d - c) * (1 - w);
		}
	}

	@Test
	void occludersAreUnderTheSurfaceTheyStandFor() {
		World world = new World();
		for (int level : new int[]{0, 1, 2, 3, 5}) {
			for (int t = -2; t <= 2; t++) {
				Built b = world.get(TileKey.of(level, t, 1 - t));
				int g = TileBuilder.OCCLUDERS;
				int cells = TileKey.TILE / g;
				for (int sb = 0; sb < g; sb++) {
					for (int sa = 0; sa < g; sa++) {
						short h = b.occluders[sa + sb * g];
						// the surface over the square, at points across it and on its edges
						for (int j = 0; j <= 16; j++) {
							for (int i = 0; i <= 16; i++) {
								double x = TileKey.minX(b.key) + (sa * cells + i * cells / 16.0) * b.cell;
								double z = TileKey.minZ(b.key) + (sb * cells + j * cells / 16.0) * b.cell;
								if (!b.smooth && (i == 16 || j == 16)) {
									continue;
								}
								float y = b.surface(x, z);
								assertTrue(y >= h, TileKey.toString(b.key) + " square " + sa + "," + sb + ": surface " + y + " under occluder " + h);
							}
						}
					}
				}
			}
		}
	}

	@Test
	void hiddenTilesAreOutOfSight() {
		this.cullAndCheck();
	}

	@Test
	void hiddenBlockyTilesAreOutOfSight() {
		// every level blocky: the occluders come from cell centres, the surface is flat tops and walls
		int smoothFrom = TileBuilder.smoothFrom;
		TileBuilder.smoothFrom = TileKey.MAX_LEVEL + 1;
		try {
			this.occludersAreUnderTheSurfaceTheyStandFor();
			this.cullAndCheck();
		} finally {
			TileBuilder.smoothFrom = smoothFrom;
		}
	}

	private void cullAndCheck() {
		// the lowest and highest ground near the origin
		double lx = 0, lz = 0, hx = 0, hz = 0;
		float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
		for (int x = -3000; x <= 3000; x += 25) {
			for (int z = -3000; z <= 3000; z += 25) {
				float h = height(x, z);
				if (h < low && h > 21) {
					low = h;
					lx = x;
					lz = z;
				}
				if (h > high) {
					high = h;
					hx = x;
					hz = z;
				}
			}
		}
		World world = new World();
		int total = 0;
		int culled = 0;
		double[][] cameras = {
			{lx, height(lx, lz) + 4, lz},
			{hx, high + 20, hz},
			{hx + 900, height(hx + 900, hz) + 30, hz},
			{hx, high + 500, hz},
		};
		for (double[] cam : cameras) {
			LongList draw = new LodSelector().select(world, cam[0], cam[1], cam[2], 4096, 0, 771, 6);
			Horizon horizon = new Horizon();
			horizon.begin(cam[0], cam[1], cam[2]);
			Long2ObjectOpenHashMap<Built> drawn = new Long2ObjectOpenHashMap<>();
			for (int i = 0; i < draw.size(); i++) {
				Built b = world.get(draw.get(i));
				int level = TileKey.level(b.key);
				horizon.add(TileKey.minX(b.key), TileKey.minZ(b.key), TileKey.span(level), b.maxY, b.occluders);
			}
			// what it costs a frame, once warm: the best of a few rounds of this thread's CPU time, as the machine may be busy
			java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
			double micros = Double.MAX_VALUE;
			for (int round = 0; round < 5; round++) {
				long t0 = bean.getCurrentThreadCpuTime();
				for (int r = 0; r < 4000; r++) {
					horizon.cull();
				}
				micros = Math.min(micros, (bean.getCurrentThreadCpuTime() - t0) / 1e3 / 4000);
			}
			for (int i = 0; i < draw.size(); i++) {
				if (!horizon.hidden(i)) {
					drawn.put(draw.get(i), world.get(draw.get(i)));
				}
			}
			for (int i = 0; i < draw.size(); i++) {
				if (horizon.hidden(i)) {
					assertOutOfSight(world.get(draw.get(i)), drawn, cam);
				}
			}
			System.out.printf(Locale.ROOT, "camera at %.0f,%.0f,%.0f: %d of %d tiles hidden, culling takes %.1f us%n", cam[0], cam[1], cam[2],
				horizon.culled(), draw.size(), micros);
			total += draw.size();
			culled += horizon.culled();
			if (cam == cameras[0]) {
				assertTrue(horizon.culled() > draw.size() / 10, "a valley floor sees little of the land around it: " + horizon.culled());
			}
		}
		assertTrue(culled > 0 && culled < total);
	}

	/** Every point of the tile's bounding box top is behind a drawn surface, seen from the camera. */
	private static void assertOutOfSight(Built hidden, Long2ObjectOpenHashMap<Built> drawn, double[] cam) {
		int span = TileKey.span(TileKey.level(hidden.key));
		for (int j = 0; j <= 8; j++) {
			for (int i = 0; i <= 8; i++) {
				double px = TileKey.minX(hidden.key) + span * i / 8.0;
				double pz = TileKey.minZ(hidden.key) + span * j / 8.0;
				if (!blocked(cam, px, hidden.maxY, pz, drawn)) {
					fail(String.format(Locale.ROOT, "%s hidden, but %.0f,%d,%.0f is in sight from %.0f,%.0f,%.0f", TileKey.toString(hidden.key), px,
						hidden.maxY, pz, cam[0], cam[1], cam[2]));
				}
			}
		}
	}

	/** Whether the line of sight to the point passes under a drawn surface on the way. */
	private static boolean blocked(double[] cam, double px, double py, double pz, Long2ObjectOpenHashMap<Built> drawn) {
		double dx = px - cam[0], dy = py - cam[1], dz = pz - cam[2];
		double length = Math.sqrt(dx * dx + dz * dz);
		double t = 0;
		while (t < 1) {
			double x = cam[0] + dx * t, z = cam[2] + dz * t;
			Built under = null;
			for (int level = 0; level <= TileKey.MAX_LEVEL && under == null; level++) {
				int span = TileKey.span(level);
				under = drawn.get(TileKey.of(level, Math.floorDiv((int) Math.floor(x), span), Math.floorDiv((int) Math.floor(z), span)));
			}
			double step = 0.25;
			if (under != null) {
				if (under.surface(x, z) > cam[1] + dy * t + 1e-3) {
					return true;
				}
				step = Math.max(0.25, under.cell / 8.0);
			}
			t += step / length;
		}
		return false;
	}
}
