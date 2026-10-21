package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.TestSupport;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The country road router (docs/LIVING_WORLD_2.md §1.8): rivers, cliffs, grades, determinism, keeping clear. */
class RoadRouterTest {
	private static Route route(TerrainProbe t, int ax, int az, int bx, int bz, RoadRouter.Avoid avoid) {
		Route r = RoadRouter.route(t, t, ax, az, bx, bz, avoid);
		assertNotNull(r, "no route: " + RoadRouter.lastFailure());
		return r;
	}

	/** Every column within a block of the last, and never two rises (or falls) in any six steps, bridges aside. */
	static void assertGraded(Route r) {
		int n = r.size();
		for (int i = 1; i < n; i++) {
			assertTrue(Math.max(Math.abs(r.xs()[i] - r.xs()[i - 1]), Math.abs(r.zs()[i] - r.zs()[i - 1])) == 1, "columns are a king's move apart at " + i);
			if (r.types()[i] == Route.BRIDGE || r.types()[i - 1] == Route.BRIDGE) {
				continue;
			}
			assertTrue(Math.abs(r.ys()[i] - r.ys()[i - 1]) <= 1, "a step of " + (r.ys()[i] - r.ys()[i - 1]) + " at column " + i);
		}
		for (int i = 0; i + 6 < n; i++) {
			int changes = 0;
			boolean bridged = false;
			for (int k = i + 1; k <= i + 6; k++) {
				bridged |= r.types()[k] == Route.BRIDGE || r.types()[k - 1] == Route.BRIDGE;
				if (r.ys()[k] != r.ys()[k - 1]) {
					changes++;
				}
			}
			assertTrue(bridged || changes <= 1, changes + " rises in the six steps after column " + i + ": steeper than 1 in 6");
		}
	}

	@Test
	void aRiverIsBridgedOrForded() {
		TerrainProbe t = new SyntheticTerrain(SyntheticTerrain.Kind.RIVER, 7);
		Route r = route(t, -260, -30, 260, 50, RoadRouter.Avoid.NONE);
		assertGraded(r);
		boolean crossed = false;
		for (int i = 0; i < r.size(); i++) {
			int x = r.xs()[i];
			int z = r.zs()[i];
			boolean wet = t.isWater(x, z) && t.waterY(x, z) > t.groundY(x, z);
			if (wet) {
				assertTrue(r.types()[i] == Route.BRIDGE || r.types()[i] == Route.FORD, "a wet column on graded road at " + x + "," + z);
				crossed = true;
			}
			if (r.types()[i] == Route.BRIDGE) {
				assertTrue(r.ys()[i] >= r.decks()[i] + 2, "a bridge deck clears the water by two");
			}
		}
		assertTrue(crossed, "the road crossed the river");
		// the bridge is one plot from bank to bank
		List<RoadSegments.Segment> segs = RoadSegments.cut(1, RoadClass.KINGS_ROAD, r, dressing());
		long bridges = segs.stream().filter(RoadSegments.Segment::bridge).count();
		long fords = 0;
		for (byte b : r.types()) {
			fords += b == Route.FORD ? 1 : 0;
		}
		assertTrue(bridges >= 1 || fords > 0, "a bridge plot or a ford");
	}

	@Test
	void aCliffIsClimbedAtACartsGrade() {
		TerrainProbe t = new SyntheticTerrain(SyntheticTerrain.Kind.CLIFF, 3);
		Route r = route(t, -220, 0, 220, 20, RoadRouter.Avoid.NONE);
		assertGraded(r);
		int rise = Math.abs(r.ys()[r.size() - 1] - r.ys()[0]);
		assertTrue(rise >= 18, "it climbed the cliff (" + rise + ")");
		// the climb is spread out: at 1 in 6 a rise of 24 takes at least 120 columns
		assertTrue(r.size() >= rise * 5, r.size() + " columns for a rise of " + rise);
	}

	@Test
	void aSteepFlankIsClimbedBySwitchbacksNotStraightUp() {
		TerrainProbe t = new SyntheticTerrain(SyntheticTerrain.Kind.STEEP, 5);
		// up the mountainside (it rises toward +x at about 1 in 1.4)
		Route r = route(t, -40, 0, 60, 0, RoadRouter.Avoid.NONE);
		assertGraded(r);
		double straight = Math.hypot(100, 0);
		assertTrue(r.length() > straight * 1.25, "the road winds (" + Math.round(r.length()) + " blocks for " + Math.round(straight) + ")");
	}

	@Test
	void theSameLandGivesTheSameRoad() {
		TerrainProbe a = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 42);
		TerrainProbe b = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 42);
		Route r1 = route(a, -400, 120, 380, -200, RoadRouter.Avoid.NONE);
		Route r2 = route(b, -400, 120, 380, -200, RoadRouter.Avoid.NONE);
		assertArrayEquals(r1.xs(), r2.xs());
		assertArrayEquals(r1.ys(), r2.ys());
		assertArrayEquals(r1.zs(), r2.zs());
		assertArrayEquals(r1.types(), r2.types());
		assertGraded(r1);
		// the encoding for the road book keeps it exactly
		Route back = Route.decode(r1.encode());
		assertArrayEquals(r1.xs(), back.xs());
		assertArrayEquals(r1.ys(), back.ys());
		assertArrayEquals(r1.zs(), back.zs());
		assertArrayEquals(r1.types(), back.types());
	}

	@Test
	void itKeepsOutOfOtherTownsAndOffStructures() {
		TerrainProbe t = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 1);
		// a town's heart in the straight line, and a structure beside it
		RoadRouter.Avoid avoid = (x, z) -> {
			double d = Math.hypot(x, z);
			if (d < 100) {
				return Double.POSITIVE_INFINITY;
			}
			if (x >= 120 && x <= 180 && z >= -40 && z <= 40) {
				return Double.POSITIVE_INFINITY;
			}
			return d < 300 ? 4 * Math.pow(1 - (d - 100) / 200, 2) : 0;
		};
		Route r = route(t, -500, 0, 500, 0, avoid);
		for (int i = 0; i < r.size(); i++) {
			int x = r.xs()[i];
			int z = r.zs()[i];
			assertTrue(Math.hypot(x, z) >= 92, "through the town's heart at " + x + "," + z);
			assertTrue(!(x >= 124 && x <= 176 && z >= -36 && z <= 36), "over the structure at " + x + "," + z);
		}
		assertGraded(r);
	}

	@Test
	void plotsCoverTheRouteInStretchesOfAtMost48() {
		TestSupport.boot();
		TerrainProbe t = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, 9);
		Route r = route(t, 0, 0, 600, 300, RoadRouter.Avoid.NONE);
		List<RoadSegments.Segment> segs = RoadSegments.cut(5, RoadClass.TRACK, r, dressing());
		assertEquals(0, segs.getFirst().from());
		assertEquals(r.size() - 1, segs.getLast().to());
		for (int i = 0; i < segs.size(); i++) {
			RoadSegments.Segment s = segs.get(i);
			assertTrue(s.to() - s.from() <= RoadSegments.MAX_COLUMNS + 8, "a stretch of " + (s.to() - s.from()));
			if (i > 0) {
				assertEquals(segs.get(i - 1).to(), s.from(), "consecutive stretches share their end column");
			}
			assertTrue(s.spec().sizeX() <= 56 && s.spec().sizeZ() <= 56, "fits the road archetype");
			// the vertices reproduce the dense centreline
			int[] pts = s.spec().params().getIntArray("points").orElseThrow();
			assertEquals(r.xs()[s.from()], pts[0]);
			assertEquals(r.zs()[s.from()], pts[2]);
			assertEquals(r.xs()[s.to()], pts[pts.length - 3]);
			assertEquals(r.zs()[s.to()], pts[pts.length - 1]);
		}
	}

	static RoadSegments.Dressing dressing() {
		return new RoadSegments.Dressing(Identifier.fromNamespaceAndPath("hearth", "temperate"), Identifier.fromNamespaceAndPath("hearth", "temperate"),
			"Ashbrook\n1 league", "Coldbrook\n1 league", true);
	}
}
