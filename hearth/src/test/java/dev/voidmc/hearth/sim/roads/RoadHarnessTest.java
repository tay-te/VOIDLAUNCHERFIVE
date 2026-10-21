package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The roads' dev harness (docs/LIVING_WORLD_2.md §1.8): the network over N × N regions of hilly synthetic land
 * ({@code -Dhearth.dev.roads=N} or {@code HEARTH_DEV_ROADS=N}, 3 by default), logging its roads, the time per route
 * (p50 / p90; budget 60 ms p90 for a road of about 1 500 blocks) and the plots it cuts. In the game,
 * {@code /hearth roads stats} gives the same for the world, with the plots written and refused.
 */
class RoadHarnessTest {
	@Test
	void theNetworkOverNRegions() {
		String arg = System.getProperty("hearth.dev.roads", System.getenv().getOrDefault("HEARTH_DEV_ROADS", "3"));
		int n = Math.max(1, Integer.parseInt(arg.trim()));
		RoadWorld w = RoadWorldTest.world(20261003, 0.5);
		long t0 = System.nanoTime();
		Map<Long, Way> ways = new HashMap<>();
		for (int rx = 0; rx < n; rx++) {
			for (int rz = 0; rz < n; rz++) {
				for (Way way : w.region(rx, rz).ways()) {
					ways.putIfAbsent(way.id(), way);
				}
			}
		}
		long took = System.nanoTime() - t0;
		int kings = 0;
		int historic = 0;
		int plots = 0;
		int bridges = 0;
		int unroutable = 0;
		double blocks = 0;
		for (Way way : ways.values()) {
			kings += way.kind() == RoadClass.KINGS_ROAD ? 1 : 0;
			historic += way.edge().historic() ? 1 : 0;
			unroutable += way.route() == null ? 1 : 0;
			blocks += way.length();
			plots += way.segments().size();
			for (RoadSegments.Segment s : way.segments()) {
				bridges += s.bridge() ? 1 : 0;
			}
		}
		System.out.println(String.format(Locale.ROOT,
			"[roads harness] %dx%d regions in %.0f ms: %d roads (%d king's roads, %d built in history, %d without a way), %.0f blocks, %d plots (%d bridges); %s",
			n, n, took / 1e6, ways.size(), kings, historic, unroutable, blocks, plots, bridges, w.describe()));
		assertTrue(!ways.isEmpty(), "roads over " + n + "x" + n + " regions");
		assertTrue(unroutable <= Math.max(1, ways.size() / 10), unroutable + " of " + ways.size() + " roads found no way");
	}
}
