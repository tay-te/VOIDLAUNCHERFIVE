package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.testing.MiniSim;
import org.junit.jupiter.api.Test;

/**
 * A tracing tool, not a test: with {@code HEARTH_PLAN_DEBUG=1} (and optionally {@code HEARTH_PLAN_LAYOUT},
 * {@code _CULTURE}, {@code _TERRAIN}, {@code _SEED}, {@code _DAYS}) it grows a settlement with every need traced, then
 * prints the growth log, the road graph and the occupancy grid round the heart as text.
 * {@code HEARTH_PLAN_DEBUG=1 ./gradlew cleanTest test --tests '*PlanDebugTest*'}
 */
class PlanDebugTest {
	@Test
	void trace() {
		if (!Boolean.getBoolean("hearth.plan.debugtest") && System.getenv("HEARTH_PLAN_DEBUG") == null) {
			return;
		}
		Growth.trace = true;
		String layout = System.getenv("HEARTH_PLAN_LAYOUT");
		dev.voidmc.hearth.plan.layout.LayoutChooser.FORCED.set(layout == null ? null : dev.voidmc.hearth.plan.model.LayoutType.valueOf(layout));
		MiniSim sim = Plans.grow(Culture.valueOf(System.getenv().getOrDefault("HEARTH_PLAN_CULTURE", "TEMPERATE")),
			System.getenv().getOrDefault("HEARTH_PLAN_TERRAIN", "hills"), Long.parseLong(System.getenv().getOrDefault("HEARTH_PLAN_SEED", "1")), Integer.parseInt(System.getenv().getOrDefault("HEARTH_PLAN_DAYS", "30")));
		for (String s : sim.log()) {
			System.out.println(s);
		}
		PlanState st = sim.settlement.data(PlanModule.STATE);
		System.out.println("layout " + st.layout + " heart " + st.heartX + "," + st.heartZ);
		for (RoadEdge e : st.graph.edges()) {
			System.out.println("edge " + e.id + " " + e.role + " " + e.kind + " w" + e.width + " built " + e.built + "/" + e.length() + " runs "
				+ java.util.Arrays.toString(e.runs()));
		}
		Occupancy occ = st.occupancy;
		if (occ != null) {
			for (int z = st.heartZ - 40; z <= st.heartZ + 40; z++) {
				StringBuilder sb = new StringBuilder();
				for (int x = st.heartX - 60; x <= st.heartX + 60; x++) {
					byte c = occ.get(x, z);
					sb.append(switch (c) {
						case Cell.FREE -> '.';
						case Cell.ROAD -> '#';
						case Cell.PLAZA -> '+';
						case Cell.BUILDING -> 'B';
						case Cell.FARM -> 'f';
						case Cell.PLANNED_ROAD -> ':';
						case Cell.SETBACK -> ',';
						case Cell.RESERVED -> 'r';
						case Cell.WALL -> 'W';
						case Cell.LANDSCAPE -> 'l';
						default -> '?';
					});
				}
				System.out.println(sb);
			}
		}
		Growth.trace = false;
	}
}
