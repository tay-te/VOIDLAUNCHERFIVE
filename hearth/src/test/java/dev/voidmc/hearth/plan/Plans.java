package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;

/** Shared setup for the planner's tests: the catalogue double registered, a planner, settlements grown offline. */
final class Plans {
	static final HearthPlanner PLANNER;

	static {
		HearthServices.setBlueprints(new CatalogueDouble());
		PLANNER = new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS);
	}

	private Plans() {
	}

	static MiniSim grow(Culture culture, String terrain, long seed, int days) {
		SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase()), seed,
			culture == Culture.DESERT || culture == Culture.MESA ? 2.0F : 0.8F);
		MiniSim sim = new MiniSim(culture, t, seed, PLANNER);
		sim.run(days);
		return sim;
	}
}
