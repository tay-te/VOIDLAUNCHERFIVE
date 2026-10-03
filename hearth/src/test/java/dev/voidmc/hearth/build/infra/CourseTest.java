package dev.voidmc.hearth.build.infra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.plan.road.Polyline;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The build module's {@link Course} reproduces the planner's {@link Polyline} exactly (PLANNING.md §7 is binding): the
 * same dense centreline and the same corridor columns, for every width and direction, so an infrastructure blueprint
 * covers precisely what the planner reserved.
 */
class CourseTest {
	@Test
	void denseLineAndCorridorMatchThePlanner() {
		Random r = new Random(20261003L);
		for (int trial = 0; trial < 400; trial++) {
			int vertices = 2 + r.nextInt(6);
			int[] pts = new int[vertices * 3];
			int x = r.nextInt(200) - 100;
			int y = 60 + r.nextInt(20);
			int z = r.nextInt(200) - 100;
			for (int v = 0; v < vertices; v++) {
				pts[v * 3] = x;
				pts[v * 3 + 1] = y;
				pts[v * 3 + 2] = z;
				x += r.nextInt(17) - 8;
				z += r.nextInt(17) - 8;
				y += r.nextInt(7) - 3;
			}
			int width = 1 + 2 * r.nextInt(4);
			Polyline line = new Polyline(pts);
			Course course = new Course(pts, width);
			Polyline.Dense dense = line.dense();
			assertEquals(dense.length(), course.steps(), "dense length, trial " + trial);
			for (int k = 0; k < dense.length(); k++) {
				assertArrayEquals(new int[]{dense.xs()[k], dense.ys()[k], dense.zs()[k]}, new int[]{course.dx(k), course.dy(k), course.dz(k)},
					"dense step " + k + ", trial " + trial);
			}
			TreeSet<Long> planner = new TreeSet<>();
			line.corridor(width, (cx, cz) -> planner.add(Course.key(cx, cz)));
			TreeSet<Long> ours = new TreeSet<>();
			for (Course.Col c : course.cols()) {
				ours.add(Course.key(c.x(), c.z()));
				// every column takes the level of its nearest centreline step, as the planner's maps do
				assertEquals(dense.nearest(c.x(), c.z()), c.k(), "nearest step, trial " + trial);
			}
			assertEquals(planner, ours, "corridor columns, trial " + trial + " width " + width);
		}
	}

	@Test
	void ringsMarkTheSidesNotTheEnds() {
		// a straight street five wide along x: rings 0,1,2,1,0 across it, the end caps never a side
		Course c = new Course(new int[]{0, 64, 0, 20, 64, 0}, 5);
		for (Course.Col col : c.cols()) {
			if (col.beyond()) {
				continue;
			}
			assertEquals(2 - Math.abs(col.z()), col.ring(), "ring at " + col.x() + "," + col.z());
		}
		assertTrue(c.straight());
	}
}
