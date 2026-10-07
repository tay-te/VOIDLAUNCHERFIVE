package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.core.LodSelector;
import dev.voidmc.lod.core.LongList;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import dev.voidmc.lod.source.ExpanseSource;
import java.util.Locale;

/**
 * What the horizon's quads are: per level, tops and walls (skirts at a tile's edge among them) and how many of each the
 * trees add, so the faces worth saving are counted rather than guessed. {@code ./gradlew faceBench}
 */
public final class FaceBench {
	public static void main(String[] args) {
		long seed = Long.parseLong(args.length > 0 ? args[0] : "8675309");
		double radius = Double.parseDouble(System.getProperty("lod.bench.radii", "8192").split(",")[0]);
		TerrainModel model = TerrainModel.forSeed(TerrainModel.seedFor(seed));
		model.reservePlates(ExpanseSource.platesFor(radius));
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
		for (boolean trees : new boolean[]{false, true}) {
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
				if (trees) {
					out.canopy = 10;
					out.cover = 0.6F;
				}
			};
			long[][] faces = new long[TileKey.MAX_LEVEL + 1][3];
			int[] count = new int[TileKey.MAX_LEVEL + 1];
			TileBuilder builder = new TileBuilder(false);
			int[] v = new int[4];
			int n = TileKey.TILE;
			for (int t = 0; t < tiles.size(); t++) {
				long key = tiles.get(t);
				TileBuilder.Mesh mesh = builder.build(source, key);
				int level = TileKey.level(key);
				count[level]++;
				for (int q = 0; q < mesh.vertexCount() / 4; q++) {
					TileBuilder.decode(mesh, q * 4, v);
					int face = v[3] & 7;
					if (face == TileBuilder.UP) {
						faces[level][0]++;
						continue;
					}
					// a wall standing on the tile's edge, facing out: a skirt
					int x0 = v[0], z0 = v[2];
					TileBuilder.decode(mesh, q * 4 + 2, v);
					boolean edge = x0 == v[0] && (x0 == 0 || x0 == n) || z0 == v[2] && (z0 == 0 || z0 == n);
					faces[level][edge ? 2 : 1]++;
				}
			}
			System.out.printf(Locale.ROOT, "[faces] radius %.0f, trees %s%n", radius, trees);
			long tops = 0, walls = 0, skirts = 0;
			for (int l = 0; l <= TileKey.MAX_LEVEL; l++) {
				if (count[l] == 0) {
					continue;
				}
				tops += faces[l][0];
				walls += faces[l][1];
				skirts += faces[l][2];
				System.out.printf(Locale.ROOT, "[faces]   L%d (%d tiles): %,.0f tops, %,.0f walls, %,.0f skirts a tile%n", l, count[l],
					faces[l][0] / (double) count[l], faces[l][1] / (double) count[l], faces[l][2] / (double) count[l]);
			}
			System.out.printf(Locale.ROOT, "[faces]   all: %,d tops, %,d walls, %,d skirts = %,d quads%n", tops, walls, skirts, tops + walls + skirts);
		}
	}
}
