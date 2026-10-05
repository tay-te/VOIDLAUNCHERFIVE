package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.PlotFacts;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class SettlementClockTest {
	@Test
	void aCampGrowsIntoAVillage() {
		SimSettlement s = TestSupport.founded("flat", 1);
		int founders = s.population();
		for (int d = 1; d <= 120; d++) {
			SettlementClock.advance(s, d * SimRules.DAY);
		}
		int built = s.plotCounts().getOrDefault(PlotState.BUILT, 0);
		assertTrue(s.population() > founders * 3, "population " + s.population());
		assertTrue(built >= 8, "built " + built + " of " + s.plotCounts());
		assertTrue(s.beds() >= s.population() - SimRules.defaults().population.tentSlots() - 2, s.beds() + " beds for " + s.population());
		assertTrue(s.tier().ordinal() >= 2, "tier " + s.tier());
	}

	@Test
	void catchUpIsCappedPerStep() {
		SimSettlement s = TestSupport.founded("flat", 2);
		long simulated = SettlementClock.advance(s, 100 * SimRules.DAY);
		assertEquals(SimRules.defaults().maxCatchUpTicks, simulated);
		assertEquals(30 * SimRules.DAY, s.simTick());
		// and nothing happens going backwards
		assertEquals(0, SettlementClock.advance(s, 5 * SimRules.DAY));
	}

	@Test
	void sameSeedSameTown() {
		SimSettlement a = TestSupport.founded("hills", 9);
		SimSettlement b = TestSupport.founded("hills", 9);
		for (int d = 1; d <= 60; d++) {
			SettlementClock.advance(a, d * SimRules.DAY);
		}
		SettlementClock.advance(b, 30 * SimRules.DAY);
		SettlementClock.advance(b, 60 * SimRules.DAY);
		// stepping day by day or a month at a time cuts time into the same slices: the very same town, people included
		var ea = SimSettlement.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow();
		var eb = SimSettlement.CODEC.encodeStart(NbtOps.INSTANCE, b).getOrThrow();
		assertTrue(a.plots().size() > 3);
		assertEquals(ea, eb);
	}

	@Test
	void embodiedBuildersOwnTheirPlotForTenSeconds() {
		SimSettlement s = TestSupport.founded("flat", 4);
		SimPlot p = s.plots().iterator().next();
		PlotFacts f = s.facts(p);
		long before = p.cursor();
		s.setEmbodied(true);
		s.advanceConstruction(p.id(), 3);
		assertEquals(Cursor.pack(Cursor.stage(before), Cursor.step(before) + 3) <= p.cursor(), true);
		long held = p.cursor();
		// game time stands still in the test context, so the hold is in force: abstract labour goes elsewhere
		SettlementClock.advance(s, SimRules.DAY / 4);
		assertEquals(held, p.cursor());
		assertTrue(p.stage() < f.stageCount() || p.state() == PlotState.BUILT);
	}

	@Test
	void embodiedSettlementsBuildStepByStepWhereNoBuilderIs() {
		SimSettlement s = TestSupport.founded("flat", 12);
		s.setEmbodied(true);
		long before = 0;
		for (SimPlot p : s.plots()) {
			before += p.cursor();
		}
		// a second of real time at a time, as the clock steps an embodied settlement
		long t = 0;
		for (int i = 0; i < 100; i++) {
			t += 20;
			SettlementClock.advance(s, t);
		}
		long after = 0;
		for (SimPlot p : s.plots()) {
			after += p.cursor();
		}
		assertTrue(after > before, "abstract labour goes on, step by step, in an embodied settlement");
	}

	@Test
	void advanceConstructionWalksStagesAndFinishes() {
		SimSettlement s = TestSupport.founded("flat", 6);
		SimPlot p = null;
		for (SimPlot q : s.plots()) {
			if (q.spec().archetype().getPath().equals("landscape/well")) {
				p = q;
			}
		}
		assertTrue(p != null);
		int total = s.facts(p).totalSteps();
		s.advanceConstruction(p.id(), total + 50);
		assertEquals(PlotState.BUILT, p.state());
		assertEquals(s.facts(p).complete(), s.applicable(p));
	}

	@Test
	void abstractProgressIsAppliedByStageUnlessEmbodied() {
		SimSettlement s = TestSupport.founded("flat", 8);
		SettlementClock.advance(s, SimRules.DAY / 8);
		for (SimPlot p : s.plots()) {
			if (p.isActive()) {
				assertEquals(0, Cursor.step(s.applicable(p)), "abstract: whole stages only");
			}
		}
		s.setEmbodied(true);
		for (SimPlot p : s.plots()) {
			if (p.isActive()) {
				assertEquals(p.cursor(), s.applicable(p), "embodied: every step");
			}
		}
	}

	@Test
	void onlyTheTradesAnArchetypeListsAreHired() {
		// a tenement's workshop bench is furnishing: listed, a carpentry's benches hire carpenters, a house's hire nobody
		int[] sites = new int[dev.voidmc.hearth.api.JobType.values().length];
		sites[dev.voidmc.hearth.api.JobType.CARPENTER.ordinal()] = 4;
		var house = new dev.voidmc.hearth.api.ArchetypeInfo(dev.voidmc.hearth.Hearth.id("house/tenement"), dev.voidmc.hearth.api.ArchetypeCategory.HOUSING,
			java.util.Set.of(), 8, 8, 12, 8, 12, 8, 14, dev.voidmc.hearth.api.SettlementTier.CITY, java.util.Set.of(), java.util.Set.of(), java.util.List.of(),
			0, 1.0);
		var shop = new dev.voidmc.hearth.api.ArchetypeInfo(dev.voidmc.hearth.Hearth.id("work/carpentry"), dev.voidmc.hearth.api.ArchetypeCategory.WORKPLACE,
			java.util.Set.of(dev.voidmc.hearth.api.JobType.CARPENTER), 0, 7, 11, 8, 12, 8, 13, dev.voidmc.hearth.api.SettlementTier.HAMLET, java.util.Set.of(),
			java.util.Set.of(), java.util.List.of(), 0, 1.0);
		assertEquals(0, PlotFacts.listed(sites, house)[dev.voidmc.hearth.api.JobType.CARPENTER.ordinal()]);
		assertEquals(4, PlotFacts.listed(sites, shop)[dev.voidmc.hearth.api.JobType.CARPENTER.ordinal()]);
		assertEquals(4, sites[dev.voidmc.hearth.api.JobType.CARPENTER.ordinal()], "the blueprint's own count is left alone");
	}

	@Test
	void foundersBringWhatTheirOwnBuildingsCost() {
		SimSettlement s = TestSupport.founded("flat", 14);
		for (SimPlot p : s.plots()) {
			if (p.reason().equals(SimSettlement.FOUNDING) && p.isActive()) {
				assertTrue(s.tryStartStage(p.id()), "a founding plot never waits for goods: " + p.spec().archetype());
			}
		}
	}

	@Test
	void budgetShrinksWhenTheServerIsSlow() {
		var clock = SimConfig.Clock.DEFAULTS;
		assertEquals(2_000_000, SimServer.budgetNanos(clock, 10));
		assertEquals(250_000, SimServer.budgetNanos(clock, 45));
		long mid = SimServer.budgetNanos(clock, 32);
		assertTrue(mid > 250_000 && mid < 2_000_000, "mid " + mid);
	}
}
