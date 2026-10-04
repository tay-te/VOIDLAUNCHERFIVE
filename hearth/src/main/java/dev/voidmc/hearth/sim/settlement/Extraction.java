package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;

/**
 * Extraction is construction run backwards (docs/LIVING_WORLD.md §7.3, §8.5): a quarry pit, a mine, a clay pit or a
 * woodlot is a plot whose blueprint's steps are cuts, built through the single progress counter like any other — but by
 * its own trade (quarriers, miners, clay diggers, foresters) rather than by builders, and what each stage cut out of the
 * natural land is its <b>yield</b>: rock by the survey's rock, ore and coal by the site's estimate (times the seam
 * factor: a mine follows the veins), clay, sand, the logs of a woodlot. Never damage. The same amounts arrive whether
 * the stage was dug abstractly or step by step by embodied miners, because both finish stages through the same counter.
 */
public final class Extraction {
	private Extraction() {
	}

	/** Whether a plot is an excavation (its blueprint's cuts are counted). */
	public static boolean is(SimSettlement s, SimPlot p) {
		return s.ctx().data().extraction(p.spec().archetype()) != null;
	}

	/**
	 * A crew's worker-days into the excavations its trade works, at the builders' rate per hour for the configured hours a
	 * day; returns the worker-days left (no excavation, or each one waiting for the goods its next stage needs: props,
	 * rails, lanterns), which the crew spends gathering at the surface instead.
	 */
	static double dig(SimSettlement s, SimData.Extraction digs, double workerDays, SimRules r, SimContext c) {
		double perDay = r.labour.workPerBuilderHour() * r.resources.extraction().hoursPerDay();
		if (perDay <= 0) {
			return workerDays;
		}
		if (s.activeDigs() == 0) {
			return workerDays;
		}
		double pool = workerDays * perDay;
		SimData data = c.data();
		long now = c.gameTime();
		for (SimPlot p : s.plots.values()) {
			if (pool <= 0.5) {
				break;
			}
			if (!p.isActive()) {
				continue;
			}
			SimData.Extraction x = data.extraction(p.spec().archetype());
			if (x == null || x.job() != digs.job()) {
				continue;
			}
			if (s.embodied() && p.lastEmbodiedAdvance != Long.MIN_VALUE && now - p.lastEmbodiedAdvance < SettlementClock.EMBODIED_HOLD) {
				// a miner is at the face as an entity: abstract labour leaves this one to them
				continue;
			}
			pool = SettlementClock.labour(s, p, pool, c);
		}
		return Math.max(0, pool / perDay);
	}

	/**
	 * Credits what stage {@code stage} of an excavation cut out of the land, and takes it from the site it works. Called as
	 * the stage finishes (abstract labour or embodied steps alike).
	 */
	static void credit(SimSettlement s, SimPlot p, PlotFacts f, int stage) {
		SimContext c = s.ctx();
		SimData data = c.data();
		SimData.Extraction x = data.extraction(p.spec().archetype());
		int cuts = f.cuts()[stage];
		if (x == null || cuts <= 0) {
			return;
		}
		double[] frac = s.clock.fractions;
		for (int g = 0; g < x.yields().length; g++) {
			frac[g] += x.yields()[g] * cuts;
		}
		SimSite site = Lands.siteFor(s, p, data);
		if (site != null) {
			if (x.kind() == ResourceSite.Kind.ORE || x.kind() == ResourceSite.Kind.STONE) {
				double seam = x.kind() == ResourceSite.Kind.ORE ? c.rules().resources.extraction().seamFactor() : 1;
				frac[Goods.ORE.ordinal()] += cuts * site.ore() * seam;
				frac[Goods.FUEL.ordinal()] += cuts * site.fuel() * seam;
			}
			// the woodlot's own felling is in its steps: nothing owed to the world beyond them
			Lands.take(s, site, cuts, 0, false, c);
			site.worked(0);
		}
		s.markDirty();
	}
}
