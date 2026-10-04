package dev.voidmc.hearth.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.sim.chains.Recipe;
import dev.voidmc.hearth.sim.chains.SimData;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The resource economy's data as it ships: every file parses, every chain can be fed. */
class SimDataTest {
	private static SimData data() {
		TestSupport.boot();
		return SimData.builtin();
	}

	@Test
	void everythingParses() {
		SimData d = data();
		assertTrue(d.recipes().size() >= 30, "recipes: " + d.recipes().size());
		for (ResourceSite.Kind k : ResourceSite.Kind.values()) {
			assertTrue(d.site(k).perCell() > 0, k + " has a site table");
		}
		for (JobType j : new JobType[] {JobType.NONE, JobType.FORESTER, JobType.QUARRIER, JobType.MINER, JobType.FARMER, JobType.FISHER,
			JobType.HERDER, JobType.HUNTER, JobType.CLAY_DIGGER, JobType.SALTER, JobType.BEEKEEPER}) {
			assertNotNull(d.gathering(j), j + " gathers");
		}
		assertTrue(d.land().size() > 40, "biome table entries " + d.land().size());
		assertNotNull(d.extraction(Hearth.id("extract/mine")));
		assertEquals(JobType.QUARRIER, d.extraction(Hearth.id("extract/quarry_pit")).job());
		assertTrue(d.edible()[Goods.FISH.ordinal()] > 0 && d.edible()[Goods.FOOD.ordinal()] == 1);
		assertTrue(d.perishable()[Goods.MEAT.ordinal()] > d.perishable()[Goods.GRAIN.ordinal()]);
		assertTrue(d.cool(Hearth.id("storage/granary")));
		assertNotNull(d.specialty(net.minecraft.resources.Identifier.fromNamespaceAndPath("expanse", "amber")));
	}

	@Test
	void workplacesSelectTheirRecipes() {
		SimData d = data();
		var sawmill = d.recipesFor(JobType.FORESTER, Hearth.id("work/sawmill"));
		assertFalse(sawmill.isEmpty());
		assertTrue(sawmill.stream().anyMatch(r -> r.outputs()[Goods.WOOD.ordinal()] > 0));
		// a forester with no sawmill fells, he does not saw
		assertTrue(d.recipesFor(JobType.FORESTER, null).isEmpty());
		// the smith's tools come first among his recipes
		assertEquals(Goods.TOOLS, best(d.recipesFor(JobType.SMITH, Hearth.id("work/smithy")).getFirst()));
	}

	private static Goods best(Recipe r) {
		Goods best = Goods.FOOD;
		for (Goods g : Goods.VALUES) {
			if (r.outputs()[g.ordinal()] > r.outputs()[best.ordinal()]) {
				best = g;
			}
		}
		return best;
	}

	/**
	 * Every good a recipe needs is made somewhere: gathered from a kind of site, or the output of another recipe. A chain
	 * with a missing link would leave a workshop idle for ever.
	 */
	@Test
	void everyInputHasASource() {
		SimData d = data();
		Set<Goods> made = EnumSet.noneOf(Goods.class);
		for (Recipe r : d.recipes()) {
			for (Goods g : Goods.VALUES) {
				if (r.outputs()[g.ordinal()] > 0) {
					made.add(g);
				}
			}
		}
		for (SimData.Gathering g : d.gatheringByJob().values()) {
			for (SimData.Gathering.Entry e : g.sites()) {
				for (Goods x : Goods.VALUES) {
					if (e.yields()[x.ordinal()] > 0) {
						made.add(x);
					}
				}
			}
		}
		for (Recipe r : d.recipes()) {
			for (Goods g : Goods.VALUES) {
				if (r.inputs()[g.ordinal()] > 0) {
					assertTrue(made.contains(g), r.id() + " needs " + g + ", which nothing makes or gathers");
				}
			}
		}
		// and the goods blueprints ask for are all made
		for (Goods g : new Goods[] {Goods.WOOD, Goods.DRESSED_STONE, Goods.BRICK, Goods.GLASS, Goods.METAL, Goods.CANDLES, Goods.CLOTH, Goods.TOOLS}) {
			assertTrue(made.contains(g), g + " is made");
		}
	}
}
