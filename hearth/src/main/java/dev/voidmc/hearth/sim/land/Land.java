package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.sim.terrain.GeneratorProbe;
import org.jspecify.annotations.Nullable;

/**
 * The land as the survey reads it: the terrain probe's ground and water, what each column's biome gives, and the open
 * spaces under it. Nothing here loads a chunk, so a survey runs on any thread. Thread-safe.
 */
public interface Land {
	TerrainProbe terrain();

	BiomeLand at(int x, int z);

	@Nullable UndergroundProbe underground();

	/** The land of a level's probe (biomes read from the generator), or of a synthetic probe (temperate country). */
	static Land of(TerrainProbe terrain, @Nullable UndergroundProbe underground) {
		if (terrain instanceof GeneratorProbe g) {
			// the survey compares places, so the cheaper view does (the Expanse model without its grid correction)
			GeneratorProbe rough = g.rough();
			return new Land() {
				@Override
				public TerrainProbe terrain() {
					return rough;
				}

				@Override
				public BiomeLand at(int x, int z) {
					return BiomeResources.of(g.biomeHolder(x, z));
				}

				@Override
				public @Nullable UndergroundProbe underground() {
					return underground;
				}
			};
		}
		return new Plain(terrain, underground);
	}

	/**
	 * Land with no biomes to read: woods come and go on a value noise a few hundred blocks across (so a synthetic town
	 * has woods, fields and pasture to work), the climate follows the probe's temperature.
	 */
	record Plain(TerrainProbe terrain, @Nullable UndergroundProbe underground) implements Land {
		private static final int CELL = 96;

		@Override
		public BiomeLand at(int x, int z) {
			float t = this.terrain.temperature(x, z);
			return BiomeLand.plain(t, cover(x, z));
		}

		private float cover(int x, int z) {
			int cx = Math.floorDiv(x, CELL);
			int cz = Math.floorDiv(z, CELL);
			float fx = (x - cx * CELL) / (float) CELL;
			float fz = (z - cz * CELL) / (float) CELL;
			float a = hash(cx, cz);
			float b = hash(cx + 1, cz);
			float c = hash(cx, cz + 1);
			float d = hash(cx + 1, cz + 1);
			float sx = fx * fx * (3 - 2 * fx);
			float sz = fz * fz * (3 - 2 * fz);
			float v = a + (b - a) * sx + (c - a) * sz + (a - b - c + d) * sx * sz;
			// about half the land is wooded, a few woods dense
			return Math.max(0, Math.min(1, (v - 0.45F) * 2F));
		}

		private float hash(int x, int z) {
			long h = this.terrain.seed() ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
			h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
			h ^= h >>> 29;
			return (h >>> 40) / (float) (1 << 24);
		}
	}
}
