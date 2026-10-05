package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.core.ColumnFiles;
import dev.voidmc.lod.core.GeneratorCache;
import dev.voidmc.lod.core.LodSelector;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import dev.voidmc.lod.source.ExpanseSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * What the generator cache costs on a first visit and saves on the next: the whole horizon built on four threads (as
 * the game's preset runs it) straight from the terrain model, then through a fresh cache, then from that cache on disk.
 * The model's plates are built beforehand, so the comparison is the cache's own cost, not the plates'.
 *
 * <pre>./gradlew cacheBench -Plod.bench.radii=32768</pre>
 */
public final class CacheBench {
	private static final ColumnFiles.Names NAMES = new ColumnFiles.Names() {
		@Override
		public String name(int id) {
			return "n" + id;
		}

		@Override
		public int id(String name) {
			return Integer.parseInt(name.substring(1));
		}
	};

	public static void main(String[] args) throws Exception {
		long seed = Long.parseLong(args.length > 0 ? args[0] : "8675309");
		double radius = Double.parseDouble(System.getProperty("lod.bench.radii", "32768").split(",")[0]);
		TerrainModel model = TerrainModel.forSeed(TerrainModel.seedFor(seed));
		model.reservePlates(ExpanseSource.platesFor(radius));
		TerrainSource plain = (x, z, cell, out) -> {
			Column c = model.sample(x, z);
			out.ground = c.height;
			if (c.inChannel()) {
				out.water = c.waterTop();
			} else if (out.ground < 63) {
				out.water = 63;
			}
			out.top = out.ground > 160 ? 0xF0F0F0 : 0x5A8A3A;
			out.side = 0x7F6B42;
			out.canopy = 10;
			out.cover = 0.6F;
		};
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
		LongList tiles = new LodSelector().select(all, 0, 120, 0, radius, 12 * 16 - 24, 771, 5);
		ForkJoinPool pool = new ForkJoinPool(4);
		Path dir = Files.createTempDirectory("void-lod-cache-bench");

		time(pool, tiles, plain, "warming the plates");
		time(pool, tiles, plain, "straight from the model");
		GeneratorCache cold = new GeneratorCache(plain, dir, NAMES, 1);
		time(pool, tiles, cold, "through a fresh cache");
		long t0 = System.nanoTime();
		cold.close().get(120, TimeUnit.SECONDS);
		System.out.printf(Locale.ROOT, "[cache] pages saved %.1f s after, %s%n", (System.nanoTime() - t0) / 1e9, cold.stats());
		long bytes;
		try (var files = Files.walk(dir)) {
			bytes = files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
		}
		System.out.printf(Locale.ROOT, "[cache] %d tiles, %.1f MB on disk%n", tiles.size(), bytes / 1048576.0);
		GeneratorCache warm = new GeneratorCache(plain, dir, NAMES, 1);
		time(pool, tiles, warm, "from the cache on disk");
		System.out.println("[cache] " + warm.stats());
		pool.shutdown();
	}

	private static void time(ForkJoinPool pool, LongList tiles, TerrainSource source, String what) throws Exception {
		ThreadLocal<TileBuilder> builders = ThreadLocal.withInitial(() -> new TileBuilder(false));
		long t0 = System.nanoTime();
		pool.submit(() -> IntStream.range(0, tiles.size()).parallel().forEach(i -> builders.get().build(source, tiles.get(i)))).get();
		System.out.printf(Locale.ROOT, "[cache] %-26s %6.0f ms for %d tiles on 4 threads%n", what, (System.nanoTime() - t0) / 1e6, tiles.size());
	}
}
