package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import net.minecraft.server.level.ServerLevel;

/** Expanse's model as the showcase's {@link LodShowcase.Land}: loaded only where Expanse is. */
final class ExpanseLand implements LodShowcase.Land {
	private final TerrainModel model;

	private ExpanseLand(TerrainModel model) {
		this.model = model;
	}

	/** The land of {@code level}, or null if Expanse does not make it. */
	static LodShowcase.Land of(ServerLevel level) {
		if (level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth) {
			return new ExpanseLand(earth.model(level.getChunkSource().randomState()));
		}
		return null;
	}

	@Override
	public float height(int x, int z) {
		return this.model.sample(x, z).height;
	}

	/**
	 * The coarse far model against the full one, every {@code step} blocks within {@code reach} of (cx, cz): how many
	 * points the coarse one calls sea where the full one has dry land, how many sit inland by the coarse model's own
	 * coast distance (which the LOD now keeps dry), and how many were looked at.
	 */
	static String farCheck(ServerLevel level, int cx, int cz, int reach, int step) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
			return "not Expanse";
		}
		TerrainModel model = earth.model(level.getChunkSource().randomState());
		TerrainModel.FarColumn far = new TerrainModel.FarColumn();
		int points = 0, falseSea = 0, inland = 0, nearSea = 0;
		double meanDiff = 0;
		for (int x = cx - reach; x <= cx + reach; x += step) {
			for (int z = cz - reach; z <= cz + reach; z += step) {
				points++;
				model.sampleFar(x, z, far);
				var c = model.sample(x, z);
				boolean land = c.height >= 64 && !c.underWater(0);
				nearSea += land ? 0 : 1;
				if (far.ocean && land) {
					falseSea++;
					if (far.coast > 0) {
						inland++;
					}
				}
				meanDiff += far.height - c.height;
			}
		}
		return String.format(java.util.Locale.ROOT, "%d points, %d sea in the full model; %d where the far model says sea over dry land (%d of them inland by its coast), far minus full height %.1f on average",
			points, nearSea, falseSea, inland, meanDiff / points);
	}

	@Override
	public boolean river(int x, int z) {
		return this.model.sample(x, z).nearRiver();
	}
}
