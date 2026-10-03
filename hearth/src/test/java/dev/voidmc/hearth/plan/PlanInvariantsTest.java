package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Grows settlements of every layout on the synthetic terrains and checks what the planner promises: no plot on
 * another (save the overlaps PLANNING.md allows), no building on a road, every building's door on a road, roads within
 * their grade, buildings level or terraced, sizes within their archetype's ranges, nothing standing in water that
 * cannot.
 */
class PlanInvariantsTest {
	static Stream<Arguments> settlements() {
		return Stream.of(
			Arguments.of(Culture.TEMPERATE, "hills", LayoutType.CROSSROADS, 2L),
			Arguments.of(Culture.TEMPERATE, "hills", LayoutType.WALLED, 3L),
			Arguments.of(Culture.TEMPERATE, "flat", LayoutType.FARMSTEADS, 4L),
			Arguments.of(Culture.MEDITERRANEAN, "slope", LayoutType.HILLSIDE, 5L),
			Arguments.of(Culture.ALPINE, "steep", LayoutType.MOUNTAIN, 6L),
			Arguments.of(Culture.MESA, "cliff", LayoutType.CLIFF, 7L),
			Arguments.of(Culture.BAYOU, "lake", LayoutType.STILT, 8L),
			Arguments.of(Culture.MARITIME, "coast", LayoutType.LINEAR, 9L),
			Arguments.of(Culture.STEPPE, "flat", LayoutType.RING, 10L),
			Arguments.of(Culture.WISTERIA, "river", LayoutType.LINEAR, 11L)
		);
	}

	@ParameterizedTest(name = "{0} {2} on {1}")
	@MethodSource("settlements")
	void invariants(Culture culture, String terrain, LayoutType layout, long seed) {
		LayoutChooser.FORCED.set(layout);
		MiniSim sim;
		try {
			sim = Plans.grow(culture, terrain, seed, 160);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		Catalogue c = Catalogue.current();
		PlanConfig cfg = PlanConfig.DEFAULTS;
		assertTrue(sim.settlement.population() > 30, "the settlement grew: " + sim.settlement.population());
		check("overlaps", PlanChecks.overlaps(sim.settlement, c));
		check("access", PlanChecks.access(sim.settlement, c));
		check("grades", PlanChecks.grades(sim.settlement, cfg));
		check("slopes", PlanChecks.slopes(sim.settlement, sim.terrain, c, cfg));
		check("sizes", PlanChecks.sizes(sim.settlement, sim.terrain, c));
	}

	private static void check(String what, List<String> violations) {
		assertTrue(violations.isEmpty(), what + ": " + violations.size() + " violations, first: " + violations.subList(0, Math.min(8, violations.size())));
	}
}
