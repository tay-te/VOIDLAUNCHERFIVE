package dev.voidmc.hearth.plan.growth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.layout.Founder;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadGraph;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.SurveyedSettlement;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * A camp with no street but its square (its main roads never routed) lays its first lane out from the square
 * (requests/w7-tour.md §4: "no road could grow (no lane mouth, the main streets built out)" for camps with no streets).
 */
class FirstLaneTest {
	@Test
	void aCampWithOnlyItsSquareLaysALane() {
		HearthServices.setBlueprints(new CatalogueDouble());
		SyntheticTerrain land = new SyntheticTerrain(SyntheticTerrain.Kind.SLOPE, 3, 0.8F);
		BlockPos heart = new BlockPos(0, land.groundY(0, 0), 0);
		SurveyedSettlement camp = new SurveyedSettlement(3, "Camp", Culture.TEMPERATE, heart, 0);
		PlanState state = new PlanState();
		state.founded = true;
		state.heartX = 0;
		state.heartZ = 0;
		state.heartY = heart.getY();
		state.plazaHalf = 5;
		PlanContext ctx = new PlanContext(camp, state, land, RandomSource.create(3), Catalogue.current(), PlanConfig.DEFAULTS);
		ctx.occ = new Occupancy();
		state.occupancy = ctx.occ;
		state.reservations.add(new Reservation("square", -5, -5, 5, 5, Cell.PLAZA));
		ctx.occ.markRect(-5, -5, 5, 5, Cell.PLAZA);
		RoadGraph.Node node = state.graph.addNode(0, heart.getY(), 0, RoadGraph.NodeKind.HEART);
		Founder.squareEdge(ctx, node.id());
		assertTrue(FirstLane.wanted(ctx, "no road could grow (no lane mouth, the main streets built out, no dead end to run on)"), "a camp may lay one");
		assertTrue(FirstLane.lay(ctx), "a lane is laid");
		RoadEdge lane = null;
		for (RoadEdge e : state.graph.edges()) {
			if (e.role == RoadEdge.Role.LANE && (lane == null || e.built > lane.built)) {
				lane = e;
			}
		}
		assertTrue(lane != null && lane.built >= 8, "the lane is built: " + (lane == null ? "none" : lane.built + " of " + lane.length()));
		assertTrue(!state.queue.isEmpty(), "its road plots are queued");
	}
}
