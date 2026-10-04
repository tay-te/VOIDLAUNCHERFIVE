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
			AtomicLong bytes = new AtomicLong();
			long t0 = System.nanoTime();
			TileBuilder one = new TileBuilder();
			long[][] faces = new long[TileKey.MAX_LEVEL + 1][2];
			dev.voidmc.lod.core.Heights heights = Boolean.getBoolean("lod.bench.light") ? new dev.voidmc.lod.core.Heights() : null;
			long light0 = TileBuilder.LIGHT_NANOS.get(), sample0 = TileBuilder.SAMPLE_NANOS.get();
			// coarse to fine, the order the game builds them in
			long[] order = new long[tiles.size()];
			for (int i = 0; i < order.length; i++) {
				order[i] = tiles.get(i);
			}
			java.util.Arrays.sort(order);
			for (int i = order.length - 1; i >= 0; i--) {
				TileBuilder.Mesh mesh = one.build(source, order[i], heights);
				quads.addAndGet(mesh.quads());
				bytes.addAndGet(mesh.bytes());
				if (Boolean.getBoolean("lod.bench.faces") && !mesh.smooth()) {
					java.nio.ByteBuffer v = mesh.vertices();
					int lvl = TileKey.level(order[i]);
					for (int q = 0; q < mesh.vertexCount() / 4; q++) {
						int face = v.getShort(q * 4 * TileBuilder.VERTEX_BYTES + 6) & 7;
						faces[lvl][face == 0 ? 0 : 1]++;
					}
				}
			}
			if (Boolean.getBoolean("lod.bench.faces")) {
				for (int l = 0; l <= TileKey.MAX_LEVEL; l++) {
					if (levels[l] > 0) {
						System.out.printf(Locale.ROOT, "  L%d: %.0f tops, %.0f walls a tile%n", l, faces[l][0] / (double) levels[l], faces[l][1] / (double) levels[l]);
					}
				}
			}
			long single = System.nanoTime() - t0;
			if (heights != null) {
				// again, every grid in place: what the sweeps alone cost
				long l1 = TileBuilder.LIGHT_NANOS.get();
				for (int i = order.length - 1; i >= 0; i--) {
					one.build(source, order[i], heights);
				}
				System.out.printf(Locale.ROOT, "  light, warm: %.1f ms a tile%n", (TileBuilder.LIGHT_NANOS.get() - l1) / 1e6 / tiles.size());
				System.out.printf(Locale.ROOT, "  light: %.1f ms a tile, %d lookups went coarser; sampling %.1f ms a tile%n",
					(TileBuilder.LIGHT_NANOS.get() - light0) / 1e6 / tiles.size(), heights.coarser.get(), (TileBuilder.SAMPLE_NANOS.get() - sample0) / 1e6 / tiles.size());
			}

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
				radius, tiles.size(), perLevel.toString().trim(), selectNanos / 1e6, quads.get(), bytes.get() / 1048576.0,
				single / 1e6, single / 1e6 / tiles.size(), parallel / 1e6, threads);
		}
	}
}
