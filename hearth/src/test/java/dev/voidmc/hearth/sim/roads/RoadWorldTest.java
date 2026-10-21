package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** The network over the atlas's sites (§1.2): a region's roads are the global network's, and their plots are indexed by chunk. */
class RoadWorldTest {
	static SimRules rules(double density) {
		SimConfig d = SimConfig.DEFAULTS;
		SimConfig.Atlas a = d.atlas();
		SimConfig.Atlas atlas = new SimConfig.Atlas(true, density, a.siteMargin(), a.maxAgeDays(), a.ageSkew(), a.villageClearance(), a.structureClearance(),
			a.guardMargin(), a.treeReach(), a.adoptMargin(), a.planCache());
		return new SimRules(new SimConfig(d.dimensions(), false, d.embody(), d.clock(), d.applier(), atlas, d.labour(), d.production(), d.population(),
			d.storage(), d.founding(), d.daughters(), d.resources()));
	}

	static RoadWorld world(long seed, double density) {
		TestSupport.boot();
		SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.HILLS, seed);
		Atlas atlas = new Atlas(Level.OVERWORLD, t, null, rules(density), null, b -> Culture.TEMPERATE);
		return new RoadWorld(Level.OVERWORLD, atlas, t, t, c -> Hearth.id(c.getSerializedName()), RoadsConfig.DEFAULTS, (x, z) -> false);
	}

	@Test
	void aRegionsRoadsAreTheNetworksRoadsThatCrossIt() {
		RoadWorld w = world(20261003, 0.4);
		// the network over a wide square of sites, brute force
		List<RoadGraph.Node> nodes = new ArrayList<>();
		for (int rx = -10; rx <= 10; rx++) {
			for (int rz = -10; rz <= 10; rz++) {
				RegionPlan.Site s = w.atlas().site(rx, rz);
				if (s != null) {
					nodes.add(RoadWorld.node(s));
				}
			}
		}
		assertTrue(nodes.size() > 30, "sites " + nodes.size());
		List<RoadGraph.Edge> all = RoadGraph.all(nodes, w.rules());
		for (int[] r : new int[][]{{0, 0}, {1, -1}, {-2, 1}}) {
			int x0 = r[0] << Atlas.REGION_BITS;
			int z0 = r[1] << Atlas.REGION_BITS;
			Set<Long> expect = new TreeSet<>();
			for (RoadGraph.Edge e : all) {
				RoadGraph.Node a = find(nodes, e.a());
				RoadGraph.Node b = find(nodes, e.b());
				if (RoadWorld.reaches(a.x(), a.z(), b.x(), b.z(), x0, z0, x0 + 1023, z0 + 1023)) {
					expect.add(e.id());
				}
			}
			Set<Long> local = new TreeSet<>();
			for (RoadGraph.Edge e : w.edgesNear(r[0], r[1])) {
				local.add(e.id());
			}
			assertEquals(expect, local, "the roads that may cross region " + r[0] + "," + r[1]);
		}
	}

	private static RoadGraph.Node find(List<RoadGraph.Node> nodes, long id) {
		for (RoadGraph.Node n : nodes) {
			if (n.id() == id) {
				return n;
			}
		}
		throw new AssertionError("no node " + id);
	}

	@Test
	void regionsIndexTheirPlotsByChunkAndPlanTheirStrips() {
		RoadWorld w = world(77, 0.6);
		long t0 = System.nanoTime();
		RoadWorld.Region region = null;
		for (int rx = 0; rx < 4 && (region == null || region.ways().isEmpty()); rx++) {
			region = w.region(rx, 0);
		}
		long took = System.nanoTime() - t0;
		assertFalse(region.ways().isEmpty(), "a region with roads");
		System.out.printf("[roads] region %d,%d: %d ways, %d chunks with plots, %.0f ms (%s)%n", region.rx(), region.rz(), region.ways().size(),
			region.byChunk().size(), took / 1e6, w.describe());
		int checked = 0;
		for (var e : region.byChunk().long2ObjectEntrySet()) {
			int cx = ChunkPos.getX(e.getLongKey());
			int cz = ChunkPos.getZ(e.getLongKey());
			for (long ref : e.getValue()) {
				Way way = region.ways().get((int) (ref >>> 32));
				RoadSegments.Segment s = way.segments().get((int) ref);
				assertTrue(s.touchesChunk(cx, cz), "a plot listed under a chunk it does not touch");
				if (way.edge().historic() && checked < 200) {
					// the centre of its first vertex is planned ground (trees keep off it)
					int[] pts = s.spec().params().getIntArray("points").orElseThrow();
					if (pts[0] >> 4 == cx && pts[2] >> 4 == cz) {
						assertTrue(region.isPlanned(pts[0], pts[2]), "a built road's column is planned");
						checked++;
					}
				}
			}
		}
		// the same region asked again is the same object (cached)
		assertTrue(region == w.region(region.rx(), region.rz()));
	}
}
