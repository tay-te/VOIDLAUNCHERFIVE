package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.society.Nobility;
import dev.voidmc.hearth.plan.society.Stations;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Housing by station and the social geography it makes (LIVING_WORLD.md §8.1): every home names the station it is for
 * and (most) the household; families live in homes of their station; the poor live out at the edge, merchants by the
 * market; a town raises its lord's keep on the castle hill with a close kept for the castle; with stations switched off
 * the planner plans homes as in phase 2.
 */
class StationHousingTest {
	static MiniSim town;

	@BeforeAll
	static void grow() {
		LayoutChooser.FORCED.set(LayoutType.CROSSROADS);
		try {
			town = Plans.grow(Culture.TEMPERATE, "hills", 51, 200);
		} finally {
			LayoutChooser.FORCED.remove();
		}
	}

	@Test
	void everyHomeNamesItsStation() {
		Catalogue c = Catalogue.current();
		int homes = 0;
		int households = 0;
		for (PlotView p : town.settlement.plots()) {
			var info = c.info(p.spec().archetype());
			if (info == null || info.category() != ArchetypeCategory.HOUSING || p.spec().archetype().equals(Archetypes.HERMIT_HUT)) {
				continue;
			}
			homes++;
			assertTrue(p.spec().params().contains("station"), "a home names its station: " + p.spec().archetype() + " " + p.id());
			if (p.spec().params().contains("household")) {
				households++;
				assertTrue(!p.spec().params().getCompoundOrEmpty("household").getStringOr("job", "").isEmpty(), "a household names its trade");
			}
		}
		assertTrue(homes > 15, "the town has homes: " + homes);
		assertTrue(households * 2 >= homes, "most homes are planned for a household: " + households + "/" + homes);
	}

	@Test
	void familiesLiveInHomesOfTheirStation() {
		int fitting = 0;
		for (Resident r : town.settlement.residents()) {
			var home = r.home >= 0 ? town.settlement.plot(r.home) : null;
			if (home != null && Stations.home(home.spec(), Catalogue.current().info(home.spec().archetype())) == Stations.of(r, town.settlement.tier())) {
				fitting++;
			}
		}
		assertTrue(fitting * 10 >= town.settlement.population() * 8, "most families live in a home of their station: " + fitting + "/"
			+ town.settlement.population());
	}

	@Test
	void thePoorAtTheEdgeMerchantsByTheMarket() {
		PlanState st = town.settlement.data(PlanModule.STATE);
		List<Double> poor = new ArrayList<>();
		List<Double> merchants = new ArrayList<>();
		for (PlotView p : town.settlement.plots()) {
			Station s = Stations.home(p.spec(), Catalogue.current().info(p.spec().archetype()));
			if (s == null || p.spec().params().contains("replaces")) {
				continue;
			}
			PlotSpec sp = p.spec();
			double d = Math.hypot(sp.origin().getX() + sp.sizeX() / 2.0 - st.heartX, sp.origin().getZ() + sp.sizeZ() / 2.0 - st.heartZ);
			if (s == Station.POOR) {
				poor.add(d);
			} else if (s == Station.MERCHANT) {
				merchants.add(d);
			}
		}
		assertTrue(!poor.isEmpty() && !merchants.isEmpty(), "both stations have homes: poor " + poor.size() + ", merchants " + merchants.size());
		double poorMean = poor.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double merchantMean = merchants.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		assertTrue(poorMean > merchantMean * 1.3, "the poor live farther out than the merchants: " + (int) poorMean + " vs " + (int) merchantMean);
	}

	@Test
	void theLordsKeepOnTheCastleHill() {
		PlanState st = town.settlement.data(PlanModule.STATE);
		PlotView keep = town.settlement.plots().stream().filter(p -> Nobility.isSeat(p.spec().archetype())).findFirst().orElse(null);
		assertNotNull(keep, "a town raises its lord's seat");
		assertEquals("lord", keep.spec().params().getStringOr("station", ""));
		assertNotNull(st.castleHill, "the castle hill was chosen");
		PlotSpec k = keep.spec();
		double fromHill = Math.hypot(k.origin().getX() + k.sizeX() / 2.0 - st.castleHill[0], k.origin().getZ() + k.sizeZ() / 2.0 - st.castleHill[1]);
		assertTrue(fromHill < 60, "the keep stands on the castle hill: " + (int) fromHill);
	}

	@Test
	void withoutStationsHomesAreAsBefore() {
		HearthPlanner plain = new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS.withLiving(new PlanConfig.Living(false, true, 320, true, 1.25, true,
			true, true, true, true, true, true, 0.25, -1)));
		LayoutChooser.FORCED.set(LayoutType.CROSSROADS);
		MiniSim sim;
		try {
			sim = new MiniSim(Culture.TEMPERATE, new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 52, 0.8F), 52, plain);
			sim.run(80);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		for (PlotView p : sim.settlement.plots()) {
			assertTrue(!p.spec().params().contains("station") || Nobility.isSeat(p.spec().archetype()), "no station without stations: " + p.spec().archetype());
		}
		assertTrue(sim.settlement.population() > 30, "and the town grows: " + sim.settlement.population());
	}
}
