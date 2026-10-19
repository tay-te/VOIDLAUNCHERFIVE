package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.NearbySites;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** {@code /hearth settlement list}'s histories nobody has visited yet, and the ids {@code tp} reads them back by. */
class NearbySitesTest {
	private static Atlas atlas(long seed) {
		TestSupport.boot();
		SimConfig d = SimConfig.DEFAULTS;
		SimConfig.Atlas a = d.atlas();
		SimConfig.Atlas everywhere = new SimConfig.Atlas(true, 1.0, a.siteMargin(), 160, a.ageSkew(), a.villageClearance(), a.structureClearance(),
			a.guardMargin(), a.treeReach(), a.adoptMargin(), a.planCache());
		SimRules rules = new SimRules(new SimConfig(d.dimensions(), false, d.embody(), d.clock(), d.applier(), everywhere, d.labour(), d.production(),
			d.population(), d.storage(), d.founding(), d.daughters(), d.resources()));
		return new Atlas(Level.OVERWORLD, new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed), null, rules, null, b -> Culture.TEMPERATE);
	}

	@Test
	void historicIdsDecodeToTheirRegion() {
		for (int[] r : new int[][] {{0, 0}, {-1, 0}, {0, -1}, {-3, 7}, {123, -456}, {-(1 << 20), (1 << 20) - 1}}) {
			long id = Atlas.historicId(r[0], r[1]);
			assertEquals(r[0], Atlas.historicRegionX(id), "x of " + r[0] + "," + r[1]);
			assertEquals(r[1], Atlas.historicRegionZ(id), "z of " + r[0] + "," + r[1]);
		}
	}

	@Test
	void listsReadyHistoriesNearestFirstAndNeverPlansOnTheCaller() {
		Atlas atlas = atlas(1234);
		// nothing planned yet: nothing listed, every region in range counted as being surveyed, none computed here
		NearbySites.Result cold = NearbySites.around(atlas, 0, 0, 1500, id -> false, false);
		assertTrue(cold.sites().isEmpty());
		assertEquals(16, cold.surveying(), "regions -2..1 square touch a 1500 m circle round 0,0");
		assertEquals(0, atlas.cachedPlans());

		List<RegionPlan> settled = new ArrayList<>();
		for (int rx = -2; rx <= 1; rx++) {
			for (int rz = -2; rz <= 1; rz++) {
				RegionPlan p = atlas.plan(rx, rz);
				if (p.settled()) {
					settled.add(p);
				}
			}
		}
		assertFalse(settled.isEmpty(), "hills at density 1 settle somewhere round the origin");
		NearbySites.Result warm = NearbySites.around(atlas, 0, 0, 1500, id -> false, false);
		assertEquals(0, warm.surveying());
		assertFalse(warm.sites().isEmpty());
		int last = -1;
		for (NearbySites.Unvisited u : warm.sites()) {
			assertTrue(u.distance() <= 1500 && u.distance() >= last, "sorted and in range: " + u);
			last = u.distance();
			assertTrue(Atlas.isHistoric(u.id()));
			RegionPlan p = atlas.planIfReady(Atlas.historicRegionX(u.id()), Atlas.historicRegionZ(u.id()));
			assertNotNull(p);
			assertEquals(p.site().settlementId(), u.id());
			assertEquals(p.site().center(), u.center());
			assertEquals(p.history().tier(), u.tier());
			double dx = u.center().getX() + 0.5;
			double dz = u.center().getZ() + 0.5;
			assertEquals((int) Math.sqrt(dx * dx + dz * dz), u.distance());
			assertFalse(u.name().isEmpty());
		}

		// a town already live (or adopted) is not offered again
		long first = warm.sites().get(0).id();
		NearbySites.Result known = NearbySites.around(atlas, 0, 0, 1500, id -> id == first, false);
		assertEquals(warm.sites().size() - 1, known.sites().size());
		assertTrue(known.sites().stream().noneMatch(u -> u.id() == first));
	}

	@Test
	void surveyStartsPlansOffTheCaller() throws Exception {
		Atlas atlas = atlas(77);
		NearbySites.Result r = NearbySites.around(atlas, 500, 500, 300, id -> false, true);
		assertEquals(1, r.surveying());
		// the worker plans it: wait for it, then the next ask has no region left to survey
		atlas.planAsync(0, 0).get();
		assertEquals(0, NearbySites.around(atlas, 500, 500, 300, id -> false, false).surveying());
	}
}
