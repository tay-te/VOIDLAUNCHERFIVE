package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.dev.PlaceholderExcavations;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Extraction is construction run backwards (LIVING_WORLD.md §7.3): cuts are yield, dug by their own trade. */
class ExtractionTest {
	private static SimSite stoneSite(SimSettlement s) {
		SimSite best = null;
		for (SimSite site : s.land().sites()) {
			if (site.kind() == ResourceSite.Kind.STONE && (best == null || site.capacity() > best.capacity())) {
				best = site;
			}
		}
		return best;
	}

	private static SimPlot quarry(SimSettlement s, SimSite site) {
		CompoundTag params = new CompoundTag();
		params.putInt("site", site.id());
		params.putInt("depth", 4);
		BlockPos at = site.center();
		PlotSpec spec = new PlotSpec(PlaceholderExcavations.QUARRY_PIT, dev.voidmc.hearth.Hearth.id("temperate"), 7, new BlockPos(at.getX() - 5, at.getY(), at.getZ() - 5),
			11, 11, Direction.SOUTH, 6, 1, params);
		return s.addPlot(spec, -1, "test quarry");
	}

	@Test
	void quarriersDigTheirPitAndItsCutsAreYield() {
		SimSettlement s = TestSupport.founded("steep", 41);
		SimSite site = stoneSite(s);
		assertNotNull(site, "a mountainside has a stone face");
		SimPlot pit = quarry(s, site);
		PlotFacts f = s.facts(pit);
		assertNotNull(f.cuts(), "an excavation's cuts are counted");
		int cuts = 0;
		for (int c : f.cuts()) {
			cuts += c;
		}
		assertTrue(cuts > 100, "cuts " + cuts);
		// the pit employs quarriers from the day it is staked out
		s.recomputeDerived();
		assertTrue(s.jobSlots(JobType.QUARRIER) >= 1);
		double before = site.remaining();
		int stoneBefore = s.stockpile().get(Goods.STONE);
		for (int d = 1; d <= 20; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		boolean quarrier = false;
		for (Resident r : s.residents()) {
			quarrier |= r.job == JobType.QUARRIER && r.work == pit.id();
		}
		assertTrue(quarrier, "someone took the quarrier's job at the pit");
		assertTrue(pit.stage() > 0 || pit.state() == PlotState.BUILT, "the pit went down: stage " + pit.stage());
		assertTrue(site.remaining() < before, "the face was cut: " + before + " -> " + site.remaining());
		assertTrue(s.stockpile().get(Goods.STONE) > stoneBefore, "stone came in");
		Lands.link(s, s.ctx().data());
		assertEquals(pit.isActive() ? pit.id() : site.plot(), site.plot(), "the site knows its pit");
	}

	@Test
	void buildersLeaveExcavationsToTheirTrade() {
		SimSettlement s = TestSupport.founded("steep", 42);
		SimSite site = stoneSite(s);
		SimPlot pit = quarry(s, site);
		// nobody may take the quarrier's job: all adults are builders
		for (Resident r : s.residents()) {
			r.job = JobType.BUILDER;
		}
		long before = pit.cursor();
		// a slice of construction (no day boundary, so nobody changes trade) with only builders: the pit is not theirs
		SettlementClock.advance(s, SimRules.DAY / 4);
		assertEquals(before, pit.cursor(), "builders did not dig the pit");
	}

	@Test
	void abstractOnlyExtractionChangesNoTerrain() {
		SimSettlement s = TestSupport.founded("steep", 43);
		SimPlot pit = quarry(s, stoneSite(s));
		s.advanceConstruction(pit.id(), 50);
		assertTrue(s.applicable(pit) > 0 || pit.stage() == 0);
		SimConfig d = SimConfig.DEFAULTS;
		var x = d.resources().extraction();
		SimConfig.Resources res = new SimConfig.Resources(d.resources().land(), d.resources().chains(), d.resources().spoilage(),
			d.resources().prosperity(), d.resources().caravans(), d.resources().stations(),
			new SimConfig.Extraction(true, x.hoursPerDay(), x.seamFactor(), x.fellWoods(), x.fellBudgetMs(), x.fellAway(), x.maxFellingDebt()));
		SimRules abstractOnly = new SimRules(new SimConfig(d.dimensions(), false, d.embody(), d.clock(), d.applier(), d.atlas(), d.labour(),
			d.production(), d.population(), d.storage(), d.founding(), d.daughters(), res));
		var ctx = new dev.voidmc.hearth.sim.atlas.History.Context(abstractOnly, s.ctx().terrain(), s.ctx().blueprints(), s.ctx().planner(),
			s.ctx().residents(), TestSupport.region00());
		s.bind(ctx);
		assertEquals(Cursor.ZERO, s.applicable(pit), "nothing of an excavation is written when extraction is abstract only");
	}
}
