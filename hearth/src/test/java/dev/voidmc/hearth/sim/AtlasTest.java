package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class AtlasTest {
	private static SimRules rules(double density) {
		SimConfig d = SimConfig.DEFAULTS;
		SimConfig.Atlas a = d.atlas();
		SimConfig.Atlas everywhere = new SimConfig.Atlas(true, density, a.siteMargin(), 160, a.ageSkew(), a.villageClearance(), a.structureClearance(),
			a.guardMargin(), a.adoptMargin(), a.planCache());
		return new SimRules(new SimConfig(d.dimensions(), d.embody(), d.clock(), d.applier(), everywhere, d.labour(), d.production(), d.population(),
			d.storage(), d.founding()));
	}

	private static Atlas atlas(double density, long seed) {
		TestSupport.boot();
		return new Atlas(Level.OVERWORLD, new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed), null, rules(density), null, b -> Culture.TEMPERATE);
	}

	@Test
	void historiesAreDeterministicAndStayInTheirRegion() {
		RegionPlan a = null;
		for (int rx = 0; rx < 4 && (a == null || !a.settled()); rx++) {
			a = atlas(1.0, 1234).plan(rx, 1);
		}
		assertNotNull(a);
		assertTrue(a.settled(), "a region of hills should be settled at density 1");
		RegionPlan b = atlas(1.0, 1234).plan(a.regionX(), a.regionZ());
		assertEquals(a.site(), b.site());
		assertEquals(a.plots(), b.plots());
		assertEquals(a.history().population(), b.history().population());
		assertTrue(a.plots().size() >= 3 || a.site().age() < 10, a.plots().size() + " historic plots for age " + a.site().age());

		BoundingBox region = Atlas.regionBox(a.regionX(), a.regionZ());
		int margin = SimConfig.DEFAULTS.atlas().siteMargin();
		assertTrue(a.site().center().getX() >= region.minX() + margin && a.site().center().getX() <= region.maxX() - margin);
		for (SimPlot p : a.history().plots()) {
			BoundingBox r = p.reach();
			assertTrue(r.minX() >= region.minX() && r.maxX() <= region.maxX() && r.minZ() >= region.minZ() && r.maxZ() <= region.maxZ(),
				"plot " + p + " leaves its region");
		}
		// every historic plot is findable from each chunk it touches, and its ground is planned
		for (RegionPlan.HistoricPlot hp : a.plots()) {
			BoundingBox r = SimPlot.reach(hp.spec());
			int[] idx = a.byChunk().get(ChunkPos.pack(r.minX() + 1 >> 4, r.minZ() + 1 >> 4));
			assertNotNull(idx);
			int[] col = new int[2];
			PlotColumns.forEach(hp.spec(), 0, (x, z) -> {
				col[0] = x;
				col[1] = z;
			});
			assertTrue(a.isPlanned(col[0], col[1]));
		}
		assertFalse(a.isPlanned(region.minX() + 2, region.minZ() + 2));
		assertEquals(Atlas.historicId(a.regionX(), a.regionZ()), a.site().settlementId());
	}

	@Test
	void densityZeroSettlesNothing() {
		Atlas atlas = atlas(0.0, 99);
		for (int rx = -2; rx <= 2; rx++) {
			assertFalse(atlas.plan(rx, 0).settled());
		}
	}

	@Test
	void differentSeedsDifferentHistories() {
		List<RegionPlan.Site> sites = new java.util.ArrayList<>();
		for (long seed : new long[]{1, 2}) {
			RegionPlan p = atlas(1.0, seed).plan(0, 0);
			if (p.settled()) {
				sites.add(p.site());
			}
		}
		if (sites.size() == 2) {
			assertFalse(sites.get(0).center().equals(sites.get(1).center()) && sites.get(0).name().equals(sites.get(1).name()));
		}
	}

	@Test
	void historicIdsAreDistinctAndMarked() {
		assertTrue(Atlas.isHistoric(Atlas.historicId(-3, 7)));
		assertFalse(Atlas.isHistoric(12));
		assertFalse(Atlas.historicId(1, 2) == Atlas.historicId(2, 1));
		assertFalse(Atlas.historicId(-1, 0) == Atlas.historicId(0, -1));
	}
}
