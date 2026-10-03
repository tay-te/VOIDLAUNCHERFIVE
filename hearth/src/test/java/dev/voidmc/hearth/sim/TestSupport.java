package dev.voidmc.hearth.sim;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.History;
import dev.voidmc.hearth.sim.dev.PlaceholderBlueprints;
import dev.voidmc.hearth.sim.dev.PlaceholderPlanner;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Shared setup for the sim tests: vanilla bootstrapped, the placeholder services registered. */
public final class TestSupport {
	private static boolean ready;
	public static PlaceholderBlueprints blueprints;
	public static PlaceholderPlanner planner;

	private TestSupport() {
	}

	public static synchronized void boot() {
		if (!ready) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			blueprints = new PlaceholderBlueprints();
			planner = new PlaceholderPlanner();
			HearthServices.setBlueprints(blueprints);
			HearthServices.setPlanner(planner);
			ready = true;
		}
	}

	/** Swaps the placeholders for the real planner over the dev catalogue (for diagnostics). */
	public static synchronized void useRealPlanner() {
		var catalogue = new dev.voidmc.hearth.sim.dev.DevCatalogue(null);
		HearthServices.setBlueprints(catalogue);
		HearthServices.setPlanner(new dev.voidmc.hearth.plan.HearthPlanner(dev.voidmc.hearth.plan.PlanModule.STATE, dev.voidmc.hearth.plan.PlanConfig.DEFAULTS));
		realPlanner = true;
	}

	private static boolean realPlanner;

	public static SimRules rules() {
		return SimRules.defaults();
	}

	/** A history context whose land is the four regions round the origin. */
	public static History.Context context(TerrainProbe terrain) {
		boot();
		BoundingBox land = new BoundingBox(-1024, -2048, -1024, 1023, 4096, 1023);
		return realPlanner ? History.context(rules(), terrain, HearthServices.blueprints(), HearthServices.planner(), land)
			: History.context(rules(), terrain, blueprints, planner, land);
	}

	/** A freshly founded settlement on synthetic ground round the origin. */
	public static SimSettlement founded(String terrain, long seed) {
		boot();
		TerrainProbe t = SyntheticTerrain.named(terrain, seed);
		History.Context ctx = context(t);
		SimSettlement s = new SimSettlement(7, "Testbury", Culture.TEMPERATE, Level.OVERWORLD, new BlockPos(0, t.groundY(0, 0), 0), 0,
			SimSettlement.NO_REGION, seed);
		s.bind(ctx);
		var f = ctx.rules().config.founding();
		SettlementClock.found(s, ctx, f.residents(), f.goodsMap(), f.treasury());
		return s;
	}

	public static BoundingBox region00() {
		return Atlas.regionBox(0, 0);
	}
}
