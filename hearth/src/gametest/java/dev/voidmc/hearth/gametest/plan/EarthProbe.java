package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.expanse.world.terrain.Column;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.hearth.api.TerrainProbe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * A terrain probe over Expanse's Earth terrain model, for previews on real worlds: ground and water exactly as VOID
 * LOD reads them (rivers in their channels above sea level, the sea below it, crater lakes). No biomes: the preview's
 * culture argument stands in for the land's.
 */
public final class EarthProbe implements TerrainProbe {
	private static final int SEA = 63;
	private final TerrainModel model;
	private final long seed;

	public EarthProbe(long worldSeed) {
		this.seed = worldSeed;
		this.model = TerrainModel.forSeed(TerrainModel.seedFor(worldSeed));
	}

	@Override
	public long seed() {
		return this.seed;
	}

	@Override
	public int minY() {
		return -64;
	}

	@Override
	public int maxY() {
		// Expanse's taller overworld (2026-10-07: ranges to ~600)
		return 640;
	}

	@Override
	public synchronized int site(int x, int z) {
		return switch (this.model.sample(x, z).site) {
			case Column.SITE_HILLTOP -> SITE_HILLTOP;
			case Column.SITE_RIM -> SITE_RIM;
			default -> SITE_NONE;
		};
	}

	@Override
	public int seaLevel() {
		return SEA;
	}

	@Override
	public synchronized int groundY(int x, int z) {
		Column c = this.model.sample(x, z);
		int ground = (int) Math.ceil(c.height);
		if (c.inChannel()) {
			ground = Math.min(ground, c.bed());
		}
		return ground;
	}

	@Override
	public synchronized int waterY(int x, int z) {
		Column c = this.model.sample(x, z);
		int ground = (int) Math.ceil(c.height);
		int water = NO_WATER;
		if (c.inChannel()) {
			water = c.waterTop();
			ground = Math.min(ground, c.bed());
		} else if (c.fjord || ground < SEA) {
			water = SEA;
		}
		if (c.crater > ground) {
			water = Math.max(water, (int) Math.ceil(c.crater));
		}
		return water != NO_WATER && water > ground ? water : NO_WATER;
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return null;
	}

	@Override
	public float temperature(int x, int z) {
		return this.model.sample(x, z).temperature;
	}

	/**
	 * A good settlement site near a point: dry, gentle ground above the sea, with water within reach — searched on a
	 * coarse grid within {@code radius} blocks, as the sim's atlas would.
	 */
	public int[] findSite(int cx, int cz, int radius) {
		int[] best = {cx, cz};
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int dz = -radius; dz <= radius; dz += 16) {
			for (int dx = -radius; dx <= radius; dx += 16) {
				int x = cx + dx;
				int z = cz + dz;
				int g = groundY(x, z);
				if (waterY(x, z) != NO_WATER || g < SEA + 2) {
					continue;
				}
				double relief = 0;
				for (int k = 0; k < 8; k++) {
					double a = k * Math.PI / 4;
					relief = Math.max(relief, Math.abs(groundY(x + (int) (Math.cos(a) * 24), z + (int) (Math.sin(a) * 24)) - g));
				}
				boolean water = false;
				for (int r = 24; r <= 96 && !water; r += 24) {
					for (int k = 0; k < 8 && !water; k++) {
						double a = k * Math.PI / 4;
						water = waterY(x + (int) (Math.cos(a) * r), z + (int) (Math.sin(a) * r)) != NO_WATER;
					}
				}
				double score = -relief * 1.5 + (water ? 20 : 0) - Math.hypot(dx, dz) * 0.02;
				if (score > bestScore) {
					bestScore = score;
					best = new int[]{x, z};
				}
			}
		}
		return best;
	}
}
