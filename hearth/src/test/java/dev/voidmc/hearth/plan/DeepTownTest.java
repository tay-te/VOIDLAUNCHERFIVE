package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.deep.LevelView;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CavedTerrain;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A mining town over a realm founds a deep town in it (LIVING_WORLD.md §8.2): a lift from the surface down to the realm
 * floor, a square at its foot, streets, the hall, dwellings cut into the wall — every deep plot with its {@code realm}
 * param, standing on the realm floor, and each level sound by the planner's invariants on its own.
 */
class DeepTownTest {
	static MiniSim grow(long seed, int days) {
		SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.STEEP, seed, 0.3F);
		int floor = t.groundY(0, 0) - 70;
		CavedTerrain caved = new CavedTerrain(t, 70, 30, 150, floor, "glowcap_wilds");
		LayoutChooser.FORCED.set(LayoutType.MOUNTAIN);
		try {
			MiniSim sim = new MiniSim(Culture.ALPINE, caved, seed, Plans.PLANNER);
			sim.run(days);
			return sim;
		} finally {
			LayoutChooser.FORCED.remove();
		}
	}

	@Test
	void mineReachesTheRealm() {
		MiniSim sim = grow(6, 220);
		PlanState state = sim.settlement.data(PlanModule.STATE);
		CavedTerrain caved = (CavedTerrain) sim.terrain;
		long mines = sim.settlement.plots().stream().filter(p -> p.spec().archetype().equals(Archetypes.MINE)
			|| p.spec().archetype().equals(Archetypes.SHAFT)).count();
		assertTrue(mines > 0, "the mining town works its ore");
		assertNotNull(state.deep, "a deep town was founded in the realm");
		List<PlotView> lifts = sim.settlement.plots().stream().filter(p -> p.spec().archetype().equals(Archetypes.DEEP_LIFT)).map(p -> (PlotView) p)
			.toList();
		assertEquals(1, lifts.size(), "one lift down to the realm");
		PlotSpec lift = lifts.get(0).spec();
		assertEquals(caved.theRealm().id(), lift.params().getLongOr("realm", 0));
		assertTrue(lift.params().getIntOr("top_y", 0) > lift.floorY() + 30, "the lift runs from the street down to the realm floor");
		int deep = 0;
		for (PlotView p : sim.settlement.plots()) {
			PlotSpec s = p.spec();
			if (LevelView.level(s) != 1) {
				continue;
			}
			deep++;
			assertEquals(caved.theRealm().id(), s.params().getLongOr("realm", 0), "every deep plot names its realm: " + s.archetype());
			int x = s.origin().getX() + s.sizeX() / 2;
			int z = s.origin().getZ() + s.sizeZ() / 2;
			int f = caved.floorY(x, z, caved.theRealm().floorY());
			assertTrue(f == Integer.MIN_VALUE || Math.abs(s.floorY() - f) <= 12, "a deep plot stands on the realm floor: " + s.archetype() + " at "
				+ s.origin() + " floor " + f);
			assertTrue(s.params().contains("local"), "deep plots build in the realm's own materials");
		}
		assertTrue(deep >= 6, "the deep town has streets and buildings: " + deep);
		assertTrue(sim.settlement.plots().stream().anyMatch(p -> p.spec().archetype().equals(Archetypes.DEEP_HALL)), "the deep hall");
		assertTrue(sim.settlement.plots().stream().anyMatch(p -> p.spec().archetype().equals(Archetypes.DEEP_DWELLING)), "deep dwellings");
		// each level holds the planner's invariants on its own
		Catalogue c = Catalogue.current();
		for (boolean down : new boolean[]{false, true}) {
			var level = down ? LevelView.deep(sim.settlement, state, 10) : LevelView.surface(sim.settlement, state);
			var overlaps = PlanChecks.overlaps(level, c);
			assertTrue(overlaps.isEmpty(), (down ? "deep" : "surface") + " overlaps: " + overlaps.subList(0, Math.min(5, overlaps.size())));
			var access = PlanChecks.access(level, c);
			assertTrue(access.isEmpty(), (down ? "deep" : "surface") + " access: " + access.subList(0, Math.min(5, access.size())));
		}
		for (String line : sim.log()) {
			if (line.contains("the deep")) {
				System.out.println(line);
			}
		}
	}
}
