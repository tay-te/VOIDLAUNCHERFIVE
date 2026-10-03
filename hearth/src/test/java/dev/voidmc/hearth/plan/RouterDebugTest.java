package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.road.Router;
import dev.voidmc.hearth.plan.terrain.Ground;
import org.junit.jupiter.api.Test;

class RouterDebugTest {
	@Test
	void route() {
		if (System.getenv("HEARTH_PLAN_DEBUG") == null) {
			return;
		}
		Ground g = new Ground(new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 1));
		Occupancy occ = new Occupancy();
		for (int c : new int[]{1, 2, 4}) {
			for (double grade : new double[]{0.255, 0.33, 0.6}) {
				int[][] p = Router.route(g, occ, 0, 0, 70, 10, new Router.Params(c, 48, 0.255, grade, 9, 3, false, 600_000));
				System.out.println("cell " + c + " grade " + grade + " -> " + (p == null ? "null" : p[0].length));
			}
		}
		for (int x = -20; x <= 80; x += 10) {
			System.out.print(g.ground(x, 0) + " ");
		}
		System.out.println();
	}
}
