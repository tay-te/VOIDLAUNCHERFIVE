package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cliff towns use their cliffs (ART_REVIEW.md item 29): the face behind the square carries courses of cliff dwellings
 * climbing it, each set back on the roof of the one below, stair streets up the face beside them, and a lookout on the
 * crest; the quarter is planned the same way every time.
 */
class CliffFaceTest {
	static MiniSim grow(Culture culture, long seed, int days) {
		LayoutChooser.FORCED.set(LayoutType.CLIFF);
		try {
			return Plans.grow(culture, "cliff", seed, days);
		} finally {
			LayoutChooser.FORCED.remove();
		}
	}

	@ParameterizedTest(name = "{0} seed {1}")
	@CsvSource({"KARST, 1", "MESA, 7", "ALPINE, 3"})
	void theFaceClimbs(Culture culture, long seed) {
		MiniSim sim = grow(culture, seed, 240);
		PlanState state = sim.settlement.data(PlanModule.STATE);
		assertNotNull(state.reservation("cliff_face/0"), "the quarter was laid out behind the square");
		int built = 0;
		int top = 0;
		List<String> seen = new ArrayList<>();
		for (PlotView p : sim.settlement.plots()) {
			PlotSpec s = p.spec();
			if (!s.hasParam("face_column")) {
				continue;
			}
			int level = s.paramInt("level", 0);
			seen.add(s.paramInt("face_column", -1) + "/" + level + ":" + p.state());
			if (p.state() == PlotState.BUILT) {
				built++;
				top = Math.max(top, level);
				if (level > 0) {
					PlotView below = sim.settlement.plot(s.paramInt("stacked_on", -1));
					assertNotNull(below, "a course stands on the one below");
					assertEquals(below.spec().floorY() + below.spec().maxHeight(), s.floorY(), "on its roof");
				}
			}
		}
		System.out.println(culture + " face plots: " + seen);
		assertTrue(built >= 10, "the face is lived in: " + built + " dwellings built " + seen);
		assertTrue(top >= 2, "the face climbs at least three courses: " + top + " " + seen);
		int climbs = 0;
		for (RoadEdge e : state.graph.edges()) {
			if (e.role == RoadEdge.Role.STAIR && e.built >= e.length() && e.length() > 12) {
				climbs++;
			}
		}
		assertTrue(climbs >= 2, "stair streets climb the face: " + climbs);
	}

	@ParameterizedTest(name = "{0} seed {1}")
	@CsvSource({"KARST, 1"})
	void aLookoutOnTheCrest(Culture culture, long seed) {
		MiniSim sim = grow(culture, seed, 300);
		boolean lookout = false;
		for (PlotView p : sim.settlement.plots()) {
			lookout |= p.spec().params().getBooleanOr("lookout", false);
		}
		assertTrue(lookout, "a lookout stands on the crest at the head of the climb");
	}

	@ParameterizedTest(name = "{0} seed {1}")
	@CsvSource({"KARST, 2"})
	void theSameQuarterEveryTime(Culture culture, long seed) {
		MiniSim a = grow(culture, seed, 120);
		MiniSim b = grow(culture, seed, 120);
		assertEquals(specs(a), specs(b), "the planner is deterministic on the face");
	}

	private static List<String> specs(MiniSim sim) {
		List<String> out = new ArrayList<>();
		for (PlotView p : sim.settlement.plots()) {
			out.add(p.id() + " " + p.spec().archetype() + " " + p.spec().origin() + " " + p.spec().params());
		}
		return out;
	}
}
