package dev.voidmc.hearth.sim.terrain;

import dev.voidmc.hearth.api.TerrainProbe;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

/** Picks the terrain probe for a level: the Expanse fast path on Earth worlds, the generator probe anywhere else. */
public final class Probes {
	private Probes() {
	}

	/** @param correct on Earth worlds, correct the model with the generator's real ground on a grid ({@link ExpanseProbe}) */
	public static GeneratorProbe create(ServerLevel level, boolean correct) {
		// -Dhearth.dev.sim.genericProbe=true forces the generator probe, to compare the two
		if (FabricLoader.getInstance().isModLoaded("expanse") && !Boolean.getBoolean("hearth.dev.sim.genericProbe")) {
			TerrainProbe earth = ExpanseProbe.tryCreate(level, correct);
			if (earth != null) {
				return (GeneratorProbe) earth;
			}
		}
		return new GeneratorProbe(level);
	}
}
