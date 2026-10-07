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

	@Override
	public boolean river(int x, int z) {
		return this.model.sample(x, z).nearRiver();
	}
}
