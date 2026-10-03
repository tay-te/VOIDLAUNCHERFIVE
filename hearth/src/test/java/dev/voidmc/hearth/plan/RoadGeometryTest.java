package dev.voidmc.hearth.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Profile;
import dev.voidmc.hearth.plan.road.Router;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The infrastructure contract's geometry (PLANNING.md): dense centrelines, corridors, grading, stairs and bridges. */
class RoadGeometryTest {
	@Test
	void simplifiedPolylineReproducesItsDensePath() {
		int[][] path = Router.densify(new int[]{0, 20, 26, 26, 40}, new int[]{0, 0, 6, 30, 44});
		int n = path[0].length;
		int[] ys = new int[n];
		for (int i = 0; i < n; i++) {
			ys[i] = 64 + i / 5 - (i > 40 ? (i - 40) / 3 : 0);
		}
		Polyline line = Polyline.fromDense(path[0], ys, path[1], 0, n - 1);
		Polyline.Dense d = line.dense();
		assertArrayEquals(path[0], d.xs());
		assertArrayEquals(path[1], d.zs());
		assertArrayEquals(ys, d.ys());
		for (int v = 1; v < line.vertexCount(); v++) {
			int run = Math.max(Math.abs(line.x(v) - line.x(v - 1)), Math.abs(line.z(v) - line.z(v - 1)));
			assertTrue(run <= Polyline.MAX_RUN, "runs between points stay within " + Polyline.MAX_RUN);
		}
	}

	@Test
	void corridorsAreTheirWidthAcross() {
		for (int width : new int[]{1, 3, 5, 7}) {
			Set<Long> cells = new HashSet<>();
			Polyline.corridor(new int[]{0, 64, 0, 0, 64, 20}, width, (x, z) -> cells.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)));
			int across = 0;
			for (int x = -10; x <= 10; x++) {
				if (cells.contains(((long) x << 32) ^ 10L)) {
					across++;
				}
			}
			assertEquals(width, across, "a straight road of width " + width);
		}
		// a diagonal one-wide path still connects side by side, so it can be walked
		Set<Long> diag = new HashSet<>();
		Polyline.corridor(new int[]{0, 64, 0, 10, 64, 10}, 1, (x, z) -> diag.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)));
		assertTrue(diag.contains((1L << 32) ^ 0L) || diag.contains(1L), "diagonal paths are four-connected");
	}

	@Test
	void steepGroundGetsStairsAndWaterGetsABridge() {
		Ground steep = new Ground(new SyntheticTerrain(SyntheticTerrain.Kind.STEEP, 3));
		int[][] up = Router.densify(new int[]{-20, 20}, new int[]{0, 0});
		Profile.Rules rules = new Profile.Rules(0.3, 1.0, true, 4, 40, 2, false);
		Profile p = Profile.grade(up[0], up[1], steep, rules);
		boolean stairs = false;
		for (int r = 0; r < p.runs.length; r += 3) {
			stairs |= p.runs[r + 1] == RoadEdge.STAIRS;
		}
		assertTrue(stairs, "a road straight up a mountainside climbs in stairs");
		for (int i = 1; i < p.usable; i++) {
			assertTrue(Math.abs(p.ys[i] - p.ys[i - 1]) <= 1, "one block at a time");
		}

		Ground river = new Ground(new SyntheticTerrain(SyntheticTerrain.Kind.RIVER, 3));
		int[][] across = Router.densify(new int[]{-40, 40}, new int[]{0, 0});
		Profile b = Profile.grade(across[0], across[1], river, new Profile.Rules(0.3, 1.0, false, 4, 40, 2, false));
		int deck = Integer.MIN_VALUE;
		for (int r = 0; r < b.runs.length; r += 3) {
			if (b.runs[r + 1] == RoadEdge.BRIDGE) {
				deck = b.runs[r + 2];
			}
		}
		assertTrue(deck != Integer.MIN_VALUE, "the road bridges the river");
		assertEquals(81, b.usable, "and carries on to the far bank");
		for (int x = -40; x <= 40; x++) {
			if (river.isWater(x, 0)) {
				assertTrue(deck >= river.water(x, 0) + 2, "the deck stands clear of the water");
			}
		}
	}
}
