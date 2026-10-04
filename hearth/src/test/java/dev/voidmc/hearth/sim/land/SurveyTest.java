package dev.voidmc.hearth.sim.land;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SurveyTest {
	private static List<Survey.Found> survey(String terrain, int radius) {
		TestSupport.boot();
		SyntheticTerrain t = SyntheticTerrain.named(terrain, 5);
		Land land = Land.of(t, null);
		return Survey.run(land, new BlockPos(0, t.groundY(0, 0), 0), radius, SimData.builtin(), Survey.Params.DEFAULTS, (x, z) -> false);
	}

	private static Set<ResourceSite.Kind> kinds(List<Survey.Found> found) {
		Set<ResourceSite.Kind> k = EnumSet.noneOf(ResourceSite.Kind.class);
		for (Survey.Found f : found) {
			k.add(f.kind());
		}
		return k;
	}

	@Test
	void flatTemperateLandHasFieldsPastureAndWoods() {
		Set<ResourceSite.Kind> k = kinds(survey("flat", 256));
		assertTrue(k.containsAll(Set.of(ResourceSite.Kind.FERTILE, ResourceSite.Kind.PASTURE, ResourceSite.Kind.WOODS)), k.toString());
		assertTrue(!k.contains(ResourceSite.Kind.STONE) && !k.contains(ResourceSite.Kind.FISH), "flat dry land has no quarry or fishing: " + k);
	}

	@Test
	void aRiverGivesFishAndClay() {
		Set<ResourceSite.Kind> k = kinds(survey("river", 256));
		assertTrue(k.contains(ResourceSite.Kind.FISH) && k.contains(ResourceSite.Kind.CLAY), k.toString());
	}

	@Test
	void aMountainsideGivesStoneAndOre() {
		List<Survey.Found> found = survey("steep", 256);
		Set<ResourceSite.Kind> k = kinds(found);
		assertTrue(k.contains(ResourceSite.Kind.STONE) && k.contains(ResourceSite.Kind.ORE), k.toString());
		for (Survey.Found f : found) {
			if (f.kind() == ResourceSite.Kind.ORE) {
				assertTrue(f.ore() > 0 && f.fuel() > 0, "ore estimate " + f.ore() + " / coal " + f.fuel());
				assertTrue(f.regen() == 0, "ore never comes back");
			}
			if (f.kind() == ResourceSite.Kind.STONE) {
				assertEquals("minecraft:stone", f.material().toString());
			}
		}
	}

	@Test
	void theSameLandGivesTheSameSites() {
		List<Survey.Found> a = survey("hills", 300);
		List<Survey.Found> b = survey("hills", 300);
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals(a.get(i).kind(), b.get(i).kind());
			assertEquals(a.get(i).center(), b.get(i).center());
			assertEquals(a.get(i).capacity(), b.get(i).capacity(), 1e-9);
		}
	}

	@Test
	void moreTerritoryMoreLand() {
		double small = 0;
		double big = 0;
		for (Survey.Found f : survey("flat", 96)) {
			small += f.capacity();
		}
		for (Survey.Found f : survey("flat", 384)) {
			big += f.capacity();
		}
		// several times the land (each kind keeps only its richest, nearest sites, so not sixteen times)
		assertTrue(big > small * 2.5, small + " -> " + big);
		assertTrue(survey("flat", 512).size() <= Survey.Params.DEFAULTS.maxSites());
	}

	@Test
	void anotherTownsCoreIsNotSurveyed() {
		TestSupport.boot();
		SyntheticTerrain t = SyntheticTerrain.named("flat", 5);
		List<Territory.Core> cores = List.of(new Territory.Core(new BlockPos(150, 64, 0), 120));
		List<Survey.Found> found = Survey.run(Land.of(t, null), new BlockPos(0, 64, 0), 256, SimData.builtin(), Survey.Params.DEFAULTS,
			(x, z) -> Territory.inCore(x, z, cores));
		for (Survey.Found f : found) {
			assertTrue(!Territory.inCore(f.center().getX(), f.center().getZ(), cores), f.center() + " lies in the neighbour's core");
		}
		// and the territory stops short of the neighbour's core
		assertEquals(48, Territory.radius(new BlockPos(0, 64, 0), 256, 16, List.of(new Territory.Core(new BlockPos(100, 64, 0), 90))));
		assertEquals(256, Territory.radius(new BlockPos(0, 64, 0), 256, 16, List.of()));
	}

	@Test
	void aResurveyKeepsWhatWasTaken() {
		SimSettlement s = TestSupport.founded("flat", 21);
		LandState land = s.land();
		assertTrue(land.territory() >= 96 && !land.sites().isEmpty());
		SimSite first = land.sites().getFirst();
		int id = first.id();
		first.remaining = first.capacity / 4;
		Lands.surveyNow(s, s.context());
		SimSite again = land.site(id);
		assertTrue(again != null, "the site keeps its id");
		assertEquals(0.25, again.fill(), 0.01);
		assertEquals(land.sites().size(), land.views().size());
	}
}
