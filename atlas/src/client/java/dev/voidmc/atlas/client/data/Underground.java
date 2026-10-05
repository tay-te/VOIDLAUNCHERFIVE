package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.under.Reveal;

/**
 * Writes what the underground reveal saw ({@link Reveal}) into the dimension's underground bands: for every column
 * where open space was seen in a band, the floor under it (with the water over it, or lava); where only wall was seen
 * and nothing is known yet, rock. Nothing else is ever written to these layers, so the underground map holds only
 * what the player has been near. Worker thread.
 */
final class Underground {
	private Underground() {
	}

	/**
	 * Writes a walk's result. {@code before}: the previous walk's, or null: a column it saw exactly the same (the same
	 * highest open block, or the same wall) is not read again — walks a few blocks apart overlap almost wholly.
	 */
	static void apply(DimensionData d, Reveal.Result r, Reveal.Result before, LevelBlocks world, ChunkScanner scanner) {
		for (int b = 0; b < r.bands; b++) {
			int band = r.band0 + b;
			int bandTop = DimensionData.bandTop(band);
			LayerData layer = null;
			RegionRaw raw = null;
			long rawKey = Long.MIN_VALUE;
			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int dz = 0; dz < r.size; dz++) {
				for (int dx = 0; dx < r.size; dx++) {
					int i = r.index(dx, dz, b);
					int top = r.top[i];
					boolean wall = r.wall[i];
					if (top == Reveal.NOTHING && !wall) {
						continue;
					}
					int x = r.x0 + dx;
					int z = r.z0 + dz;
					if (before != null && before.topAt(x, z, band) == top && before.wallAt(x, z, band) == wall) {
						continue;
					}
					if (layer == null) {
						layer = d.under(band);
					}
					long key = Coords.pack(Coords.region(x), Coords.region(z));
					if (key != rawKey) {
						raw = layer.raw(Coords.region(x), Coords.region(z), true);
						rawKey = key;
					}
					boolean changed;
					if (top != Reveal.NOTHING) {
						changed = scanner.underColumn(world, x, z, top, raw);
					} else {
						int lx = Coords.inRegion(x);
						int lz = Coords.inRegion(z);
						// only wall seen here: rock, unless something better is already known of the column
						if (raw.known(RegionRaw.index(lx, lz))) {
							continue;
						}
						changed = raw.set(lx, lz, bandTop, NamePalette.NONE, 0, NamePalette.NONE, RegionRaw.KNOWN | RegionRaw.ROCK);
					}
					if (changed) {
						minX = Math.min(minX, x);
						minZ = Math.min(minZ, z);
						maxX = Math.max(maxX, x);
						maxZ = Math.max(maxZ, z);
					}
				}
			}
			if (layer != null && minX <= maxX) {
				layer.dirty(minX, minZ, maxX + 1, maxZ + 1);
				Perf.REVEALED_COLUMNS.addAndGet((long) (maxX - minX + 1) * (maxZ - minZ + 1));
			}
		}
	}
}
