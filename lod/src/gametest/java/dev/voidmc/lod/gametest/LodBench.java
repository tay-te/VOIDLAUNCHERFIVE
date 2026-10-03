package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.LodSelector;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * What a full horizon costs, measured on the Earth terrain model without starting the game: the tiles
 * the selector settles on around a camera, then every one of them sampled and meshed, on one thread and
 * on all of them.
 *
 * <pre>./gradlew lodBench -Pseed=8675309</pre>
 *
 * The source here is the model alone (height, water, landform); in game the biome lookup and colours
 * are added on top.
 */
public final class LodBench {
	public static void main(String[] args) {
		long seed = Long.parseLong(args.length > 0 ? args[0] : "8675309");
		TerrainModel model = TerrainModel.forSeed(TerrainModel.seedFor(seed));
		TerrainSource source = (x, z, cell, out) -> {
			Column c = model.sample(x, z);
			out.ground = (int) Math.ceil(c.height);
			if (c.inChannel()) {
				out.water = c.waterTop();
			} else if (out.ground < 63) {
				out.water = 63;
			}
			out.groundColor = out.ground > 160 ? 0xF0F0F0 : 0x5A8A3A;
			if (Boolean.getBoolean("lod.bench.trees")) {
				out.canopy = 10;
				out.cover = 0.6F;
			}
		};
		double camX = 0, camY = 120, camZ = 0;
		for (double radius : new double[]{4096, 8192, 16384}) {
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
			LongList tiles = new LodSelector().select(all, camX, camY, camZ, radius, 12 * 16 - 24, 771, Double.parseDouble(System.getProperty("lod.bench.detail", "5")));
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
			long t0 = System.nanoTime();
			TileBuilder one = new TileBuilder();
			for (int i = 0; i < tiles.size(); i++) {
				quads.addAndGet(one.build(source, tiles.get(i)).quads());
			}
			long single = System.nanoTime() - t0;

			int threads = Runtime.getRuntime().availableProcessors();
			ThreadLocal<TileBuilder> builders = ThreadLocal.withInitial(TileBuilder::new);
			long t1 = System.nanoTime();
			IntStream.range(0, tiles.size()).parallel().forEach(i -> builders.get().build(source, tiles.get(i)));
			long parallel = System.nanoTime() - t1;

			StringBuilder perLevel = new StringBuilder();
			for (int l = 0; l <= TileKey.MAX_LEVEL; l++) {
				if (levels[l] > 0) {
					perLevel.append(String.format(Locale.ROOT, " L%d:%d", l, levels[l]));
				}
			}
			System.out.printf(Locale.ROOT,
				"radius %5.0f m: %4d tiles (%s), select %.2f ms | %,d quads = %.1f MB | build %.0f ms on 1 thread (%.2f ms/tile), %.0f ms on %d%n",
				radius, tiles.size(), perLevel.toString().trim(), selectNanos / 1e6, quads.get(), quads.get() * TileBuilder.QUAD_BYTES / 1048576.0,
				single / 1e6, single / 1e6 / tiles.size(), parallel / 1e6, threads);
		}
	}
}
