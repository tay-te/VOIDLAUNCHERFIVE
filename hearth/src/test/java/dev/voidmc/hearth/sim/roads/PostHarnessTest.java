package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.wild.PostSite;
import dev.voidmc.hearth.sim.wild.PostSites;
import dev.voidmc.hearth.sim.wild.PostType;
import dev.voidmc.hearth.sim.wild.PostWorld;
import dev.voidmc.hearth.sim.wild.WildConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The posts' dev harness (lane B3, docs/LIVING_WORLD_2.md §7.8): the site overlay over N × N regions of the roads'
 * synthetic hills (the real network over the atlas's sites), counting posts by type and timing the siting per region
 * ({@code -Dhearth.dev.posts=N}, 4 by default).
 */
class PostHarnessTest {
	@Test
	void postsOverNRegions() {
		int n = Integer.getInteger("hearth.dev.posts", 4);
		RoadWorld roads = RoadWorldTest.world(20261003, 0.5);
		Atlas atlas = roads.atlas();
		TerrainProbe t = roads.probe();
		Map<PostType, Integer> counts = new EnumMap<>(PostType.class);
		long total = 0;
		long worst = 0;
		int posts = 0;
		int kingsLong = 0;
		int bridgeRuns = 0;
		for (int rx = 0; rx < n; rx++) {
			for (int rz = 0; rz < n; rz++) {
				int frx = rx;
				int frz = rz;
				// the roads first (their cost is the network's, shared with world generation), then the posts alone
				List<PostSites.RoadLine> lines = new ArrayList<>();
				for (Way w : roads.region(rx, rz).ways()) {
					if (!w.edge().historic() || w.route() == null) {
						continue;
					}
					var r = w.route();
					lines.add(new PostSites.RoadLine(w.id(), w.kind() == RoadNetwork.RoadClass.KINGS_ROAD, r.xs(), r.ys(), r.zs(), r.types()));
					kingsLong += w.kind() == RoadNetwork.RoadClass.KINGS_ROAD && r.length() >= 1800 ? 1 : 0;
					int run = 0;
					for (byte b : r.types()) {
						run = b == Route.BRIDGE ? run + 1 : 0;
						bridgeRuns += run == 8 ? 1 : 0;
					}
				}
				PostSites.Land land = new PostSites.Land() {
					@Override
					public TerrainProbe probe() {
						return t;
					}

					@Override
					public String biome(int x, int z) {
						return Math.floorMod(z, 700) < 200 ? "forest" : "meadow";
					}

					@Override
					public Culture culture(int x, int z) {
						return Math.floorMod(x, 3000) < 600 ? Culture.STEPPE : Culture.TEMPERATE;
					}

					@Override
					public List<int[]> towns(int qx, int qz) {
						List<int[]> out = new ArrayList<>();
						for (int dx = -1; dx <= 1; dx++) {
							for (int dz = -1; dz <= 1; dz++) {
								RegionPlan.Site s = atlas.site(frx + dx, frz + dz);
								if (s != null) {
									out.add(new int[]{s.center().getX(), s.center().getZ(), PostWorld.townReach(s.age())});
								}
							}
						}
						return out;
					}

					@Override
					public List<PostSites.RoadLine> roads(int qx, int qz) {
						return lines;
					}

					@Override
					public boolean clear(int x, int z) {
						return true;
					}
				};
				long t0 = System.nanoTime();
				List<PostSite> sites = PostSites.region(land, WildConfig.Wild.DEFAULTS, rx, rz);
				long took = System.nanoTime() - t0;
				total += took;
				worst = Math.max(worst, took);
				for (PostSite s : sites) {
					counts.merge(s.type(), 1, Integer::sum);
					posts++;
				}
			}
		}
		System.out.println(String.format(Locale.ROOT, "[posts harness] %dx%d regions: %d posts %s; siting %.1f ms a region avg, %.1f ms worst "
			+ "(roads apart); %d long king's roads, %d bridge runs of 8+", n, n, posts, counts, total / 1e6 / (n * n), worst / 1e6, kingsLong, bridgeRuns));
		assertTrue(posts > 0, "posts over " + n + "x" + n + " regions");
	}
}
