package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.PlainResidents;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's life before the world began: founded {@code age} days before day 0 and run forward through the same
 * model as a live one (the real planner and blueprints, abstract construction, population), with a random seeded from
 * the world seed and the region, touching nothing but the terrain probe. That is what makes it deterministic and safe
 * on a world generation thread: the same seed gives the same town, plot for plot, whichever chunk asks first.
 */
public final class History {
	private History() {
	}

	/** The world a history runs in: no events, no world, its own region for land. */
	public record Context(SimRules rules, TerrainProbe terrain, BlueprintService blueprints, @Nullable SettlementPlanner planner,
		ResidentFactory residents, BoundingBox land) implements SimContext {

		@Override
		public @Nullable HearthWorld eventWorld() {
			return null;
		}

		@Override
		public long gameTime() {
			return 0;
		}

		@Override
		public boolean tryPlannerCall() {
			return true;
		}

		@Override
		public boolean allows(SimSettlement settlement, PlotSpec spec) {
			BoundingBox r = SimPlot.reach(spec);
			return r.minX() >= this.land.minX() && r.maxX() <= this.land.maxX() && r.minZ() >= this.land.minZ() && r.maxZ() <= this.land.maxZ();
		}

		@Override
		public void plotsChanged(SimSettlement settlement) {
		}

		/** A history works no land beyond its region (so its sites never depend on a neighbour region's plan). */
		@Override
		public int territoryLimit(SimSettlement settlement, int wanted) {
			var c = settlement.center();
			int toEdge = Math.min(Math.min(c.getX() - this.land.minX(), this.land.maxX() - c.getX()),
				Math.min(c.getZ() - this.land.minZ(), this.land.maxZ() - c.getZ()));
			return Math.max(48, Math.min(wanted, toEdge));
		}

		@Override
		public void progressed(SimSettlement settlement, SimPlot plot) {
		}

		@Override
		public void placedByEntity(SimSettlement settlement, SimPlot plot, long from, long to) {
		}

		@Override
		public void dirty(SimSettlement settlement) {
		}
	}

	public static Context context(SimRules rules, TerrainProbe terrain, BlueprintService blueprints, @Nullable SettlementPlanner planner,
		BoundingBox land) {
		return new Context(rules, terrain, blueprints, planner, PlainResidents.INSTANCE, land);
	}

	/**
	 * Founds the settlement at the site and runs it to day 0.
	 *
	 * @param seed the history's own seed (world seed and region mixed)
	 */
	public static SimSettlement run(RegionPlan.Site site, ResourceKey<Level> dimension, long regionKey, long seed, Context ctx) {
		SimSettlement s = new SimSettlement(site.settlementId(), site.name(), site.culture(), dimension, site.center(), -site.age(), regionKey, seed);
		s.bind(ctx);
		var founding = ctx.rules().config.founding();
		SettlementClock.found(s, ctx, founding.residents(), founding.goodsMap(), founding.treasury());
		while (s.simTick() < 0) {
			if (SettlementClock.advance(s, 0) <= 0) {
				break;
			}
		}
		return s;
	}
}
