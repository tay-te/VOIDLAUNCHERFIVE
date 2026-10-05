package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.core.LodSelector;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.SampleCache;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.Tile;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * What a full horizon costs, measured on the Earth terrain model without starting the game: the tiles
 * the selector settles on around a camera, then every one of them sampled and meshed, on one thread and
 * on all of them, each tile on its own and with the neighbouring tiles' edges reused ({@link SampleCache}).
 *
 * <pre>./gradlew lodBench -Pseed=8675309</pre>
 *
 * The source here is the model alone (height, water, landform); in game the biome lookup and colours
 * are added on top. Timings are the best of a few runs: the machine is usually busy with something else.
 * A last line has the engine itself build the 8 km view with four workers, in its own order, to show how
 * much the sample cache finds there.
 */
public final class LodBench {
	private static final int RUNS = 3;

	public static void main(String[] args) {
		long seed = Long.parseLong(args.length > 0 ? args[0] : "8675309");
		TerrainModel model = TerrainModel.forSeed(TerrainModel.seedFor(seed));
		TerrainSource source = (x, z, cell, out) -> {
			Column c = model.sample(x, z);
			out.ground = c.height;
			if (c.inChannel()) {
				out.water = c.waterTop();
			} else if (out.ground < 63) {
				out.water = 63;
			}
			out.top = out.ground > 160 ? 0xF0F0F0 : 0x5A8A3A;
			out.side = 0x7F6B42;
			if (Boolean.getBoolean("lod.bench.trees")) {
				out.canopy = 10;
				out.cover = 0.6F;
			}
		};
		double camX = 0, camY = 120, camZ = 0;
		double detail = Double.parseDouble(System.getProperty("lod.bench.detail", "5"));
		// -Plod.bench.radii=8192,16384,32768 picks the radii
		double[] radii = java.util.Arrays.stream(System.getProperty("lod.bench.radii", "4096,8192,16384").split(","))
			.mapToDouble(Double::parseDouble).toArray();
		for (double radius : radii) {
			LodSelector.Store all = new LodSelector.Store() {
				@Override
				public boolean ready(long key) {
					return true;
				}

				@Override
				public boolean heightRange(long key, int[] out) {
					return false;
				}

				@Override
				public void want(long key, float priority) {
				}

				@Override
				public void touch(long key) {
				}
			};
			long s0 = System.nanoTime();
			LongList tiles = new LodSelector().select(all, camX, camY, camZ, radius, 12 * 16 - 24, 771, detail);
			long selectNanos = System.nanoTime() - s0;
			int[] levels = new int[TileKey.MAX_LEVEL + 1];
			for (int i = 0; i < tiles.size(); i++) {
				levels[TileKey.level(tiles.get(i))]++;
			}

			// warm up the model's plate cache and the JIT on a few tiles, then time the lot single-threaded
			TileBuilder warm = new TileBuilder();
			for (int i = 0; i < Math.min(20, tiles.size()); i++) {
				warm.build(source, tiles.get(i));
			}
			AtomicLong quads = new AtomicLong();
			AtomicLong bytes = new AtomicLong();
			int flat = 0;
			TileBuilder counter = new TileBuilder();
			for (int i = 0; i < tiles.size(); i++) {
				TileBuilder.Mesh mesh = counter.build(source, tiles.get(i));
				quads.addAndGet(mesh.quads());
				bytes.addAndGet(mesh.bytes());
				flat += mesh.firstIndex() != 0 ? 1 : 0;
			}
			long single = best(() -> {
				TileBuilder one = new TileBuilder();
				for (int i = 0; i < tiles.size(); i++) {
					one.build(source, tiles.get(i));
				}
			});
			long samples0 = TileBuilder.SAMPLES.get(), reused0 = TileBuilder.SAMPLES_REUSED.get();
			long singleReused = best(() -> {
				TileBuilder one = new TileBuilder(true, new SampleCache());
				for (int i = 0; i < tiles.size(); i++) {
					one.build(source, tiles.get(i));
				}
			});
			double reused = 100.0 * (TileBuilder.SAMPLES_REUSED.get() - reused0) / (TileBuilder.SAMPLES.get() - samples0);
			if (Boolean.getBoolean("lod.bench.light") || Boolean.getBoolean("lod.bench.faces")) {
				// coarse to fine, the order the game builds them in, lit, and what the blocky meshes are made of
				TileBuilder lit = new TileBuilder();
				long[][] faces = new long[TileKey.MAX_LEVEL + 1][2];
				dev.voidmc.lod.core.Heights heights = Boolean.getBoolean("lod.bench.light") ? new dev.voidmc.lod.core.Heights() : null;
				long light0 = TileBuilder.LIGHT_NANOS.get(), sample0 = TileBuilder.SAMPLE_NANOS.get();
				long[] order = new long[tiles.size()];
				for (int i = 0; i < order.length; i++) {
					order[i] = tiles.get(i);
				}
				java.util.Arrays.sort(order);
				for (int i = order.length - 1; i >= 0; i--) {
					TileBuilder.Mesh mesh = lit.build(source, order[i], heights);
					if (!mesh.smooth()) {
						java.nio.ByteBuffer v = mesh.vertices();
						int lvl = TileKey.level(order[i]);
						for (int q = 0; q < mesh.vertexCount() / 4; q++) {
							int face = v.getShort(q * 4 * TileBuilder.VERTEX_BYTES + 6) & 7;
							faces[lvl][face == 0 ? 0 : 1]++;
						}
					}
				}
				for (int l = 0; l <= TileKey.MAX_LEVEL; l++) {
					if (levels[l] > 0 && faces[l][0] + faces[l][1] > 0) {
						System.out.printf(Locale.ROOT, "  L%d: %.0f tops, %.0f walls a tile%n", l, faces[l][0] / (double) levels[l], faces[l][1] / (double) levels[l]);
					}
				}
				if (heights != null) {
					System.out.printf(Locale.ROOT, "  light: %.1f ms a tile, %d lookups went coarser; sampling %.1f ms a tile%n",
						(TileBuilder.LIGHT_NANOS.get() - light0) / 1e6 / tiles.size(), heights.coarser.get(), (TileBuilder.SAMPLE_NANOS.get() - sample0) / 1e6 / tiles.size());
				}
			}
			int threads = Runtime.getRuntime().availableProcessors();
			long parallel = best(() -> {
				ThreadLocal<TileBuilder> builders = ThreadLocal.withInitial(TileBuilder::new);
				IntStream.range(0, tiles.size()).parallel().forEach(i -> builders.get().build(source, tiles.get(i)));
			});
			long parallelReused = best(() -> {
				SampleCache cache = new SampleCache();
				ThreadLocal<TileBuilder> builders = ThreadLocal.withInitial(() -> new TileBuilder(true, cache));
				IntStream.range(0, tiles.size()).parallel().forEach(i -> builders.get().build(source, tiles.get(i)));
			});

			StringBuilder perLevel = new StringBuilder();
			for (int l = 0; l <= TileKey.MAX_LEVEL; l++) {
				if (levels[l] > 0) {
					perLevel.append(String.format(Locale.ROOT, " L%d:%d", l, levels[l]));
				}
			}
			System.out.printf(Locale.ROOT,
				"radius %5.0f m: %4d tiles (%s), select %.2f ms | %,d quads = %.1f MB, %d flat | build on 1 thread %.0f ms CPU (%.2f ms/tile),"
					+ " reusing edges %.0f ms (%.0f%% of samples reused) | on %d: %.0f ms, reusing edges %.0f ms%n",
				radius, tiles.size(), perLevel.toString().trim(), selectNanos / 1e6, quads.get(), bytes.get() / 1048576.0, flat,
				single / 1e6, single / 1e6 / tiles.size(), singleReused / 1e6, reused, threads, parallel / 1e6, parallelReused / 1e6);
		}
		engineOrder(source, camX, camY, camZ, detail);
		coast(model, source, detail);
	}

	/**
	 * The 8 km view from a coast, half of it open sea: how many smooth tiles come out flat, and what they would
	 * have cost as full grids.
	 */
	private static void coast(TerrainModel model, TerrainSource source, double detail) {
		int bx = 0, bz = 0;
		double best = 1;
		for (int x = -32768; x <= 32768; x += 4096) {
			for (int z = -32768; z <= 32768; z += 4096) {
				int sea = 0;
				for (int j = 0; j < 16; j++) {
					for (int i = 0; i < 16; i++) {
						sea += model.sample(x + (i - 8) * 1024, z + (j - 8) * 1024).height < 40 ? 1 : 0;
					}
				}
				if (Math.abs(sea / 256.0 - 0.5) < best) {
					best = Math.abs(sea / 256.0 - 0.5);
					bx = x;
					bz = z;
				}
			}
		}
		LongList tiles = new LodSelector().select(new LodSelector.Store() {
			@Override
			public boolean ready(long key) {
				return true;
			}

			@Override
			public boolean heightRange(long key, int[] out) {
				return false;
			}

			@Override
			public void want(long key, float priority) {
			}

			@Override
			public void touch(long key) {
			}
		}, bx, 80, bz, 8192, 12 * 16 - 24, 771, detail);
		TileBuilder builder = new TileBuilder();
		long bytes = 0, full = 0;
		int flat = 0, smooth = 0;
		int grid = (TileKey.TILE + 1) * (TileKey.TILE + 1) + 4 * (TileKey.TILE + 1);
		for (int i = 0; i < tiles.size(); i++) {
			TileBuilder.Mesh mesh = builder.build(source, tiles.get(i));
			bytes += mesh.bytes();
			full += mesh.firstIndex() != 0 ? (long) grid * TileBuilder.VERTEX_BYTES : mesh.bytes();
			flat += mesh.firstIndex() != 0 ? 1 : 0;
			smooth += mesh.smooth() ? 1 : 0;
		}
		System.out.printf(Locale.ROOT, "coast at %d,%d (%.0f%% sea), 8 km: %d tiles, %d smooth, %d of them flat | %.1f MB, %.1f MB as full grids%n", bx, bz,
			100 * (0.5 - best), tiles.size(), smooth, flat, bytes / 1048576.0, full / 1048576.0);
	}

	/**
	 * The best of {@link #RUNS} timings of {@code work}, in nanoseconds: of this thread's CPU time when the work runs
	 * on it alone, which other load on the machine disturbs less than the clock, else of the clock.
	 */
	private static long best(Runnable work) {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		long best = Long.MAX_VALUE;
		for (int r = 0; r < RUNS; r++) {
			long c0 = bean.getCurrentThreadCpuTime();
			long t0 = System.nanoTime();
			work.run();
			long wall = System.nanoTime() - t0;
			long cpu = bean.getCurrentThreadCpuTime() - c0;
			// work spread over a pool shows on this thread as a fraction of the clock: count the clock then
			best = Math.min(best, cpu > wall / 2 ? cpu : wall);
		}
		return best;
	}

	/** Has the engine build an 8 km view in its own order, as in game, and reports how much of it was reused. */
	private static void engineOrder(TerrainSource source, double camX, double camY, double camZ, double detail) {
		LodEngine engine = new LodEngine(4, 1L << 30);
		LodEngine.Gpu gpu = new LodEngine.Gpu() {
			@Override
			public boolean upload(Tile tile, ByteBuffer vertices) {
				tile.gpu = this;
				engine.uploaded(tile);
				return true;
			}

			@Override
			public void free(Tile tile) {
				tile.gpu = null;
			}
		};
		long samples0 = TileBuilder.SAMPLES.get();
		long reused0 = TileBuilder.SAMPLES_REUSED.get();
		long nanos0 = TileBuilder.SAMPLE_NANOS.get();
		engine.reset(source, gpu);
		long t0 = System.nanoTime();
		int frames = 0;
		do {
			engine.update(gpu, camX, camY, camZ, 8192, 12 * 16 - 24, 771, detail);
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				return;
			}
			frames++;
		} while (!engine.idle() && frames < 20_000);
		long built = engine.tilesBuilt.get();
		System.out.printf(Locale.ROOT, "engine, 8 km with 4 workers: %d tiles built in %.0f ms, %.2f ms sampling a tile, %.1f%% of samples reused%n",
			built, (System.nanoTime() - t0) / 1e6, (TileBuilder.SAMPLE_NANOS.get() - nanos0) / 1e6 / Math.max(1, built),
			100.0 * (TileBuilder.SAMPLES_REUSED.get() - reused0) / Math.max(1, TileBuilder.SAMPLES.get() - samples0));
		engine.close();
	}
}
