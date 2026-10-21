package dev.voidmc.hearth.plan.wild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.wild.PostSite;
import dev.voidmc.hearth.sim.wild.PostType;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Each post type's programme on synthetic land (docs/LIVING_WORLD_2.md §7.8). */
class PostPlannerTest {
	static {
		TestSupport.boot();
	}

	static PostSite site(PostType type, TerrainProbe t) {
		return PostPlannerTestSites.site(type, t);
	}

	static TerrainProbe terrain(PostType type) {
		return PostPlannerTestSites.terrain(type);
	}

	@Test
	void everyProgrammeFitsItsSquareWithDoorsOnItsPaths() {
		var service = BuildModule.standalone();
		for (PostType type : PostType.values()) {
			TerrainProbe t = terrain(type);
			PostSite site = site(type, t);
			List<PlotSpec> plots = PostLayouts.layOut(site, Culture.TEMPERATE, t);
			assertTrue(plots.size() >= 2, type + ": " + plots);
			LongOpenHashSet paths = new LongOpenHashSet();
			List<PlotSpec> buildings = new ArrayList<>();
			for (PlotSpec p : plots) {
				assertNotNull(service.archetype(p.archetype()), type + ": no archetype " + p.archetype());
				int x0 = p.origin().getX();
				int z0 = p.origin().getZ();
				assertTrue(x0 >= site.minX() && z0 >= site.minZ() && x0 + p.sizeX() - 1 <= site.maxX() && z0 + p.sizeZ() - 1 <= site.maxZ(),
					type + ": " + p.archetype() + " leaves the square " + site.minX() + ".." + site.maxX() + "," + site.minZ() + ".." + site.maxZ() + ": "
						+ p.origin() + " " + p.sizeX() + "x" + p.sizeZ());
				if (p.archetype().getPath().startsWith("infra/")) {
					int[] pts = p.paramInts("points").orElseThrow();
					Polyline.corridor(pts, p.paramInt("width", 3), (x, z) -> paths.add(ChunkPos.pack(x, z)));
				} else {
					buildings.add(p);
				}
			}
			// no two buildings on the same ground
			for (int i = 0; i < buildings.size(); i++) {
				for (int j = 0; j < i; j++) {
					assertFalse(PlotGeometry.overlaps(PlotGeometry.bounds(buildings.get(i)), PlotGeometry.bounds(buildings.get(j))),
						type + ": " + buildings.get(i).archetype() + " overlaps " + buildings.get(j).archetype());
				}
			}
			// every door opens onto a path, a lane or the road (fields and pens too: their gates)
			for (PlotSpec b : buildings) {
				if (type == PostType.TOLL_GATE && b.archetype().getPath().equals("law/toll_gate")) {
					continue; // its door is the road
				}
				int[] door = PlotGeometry.entrance(b);
				boolean onPath = false;
				for (int dx = -1; dx <= 1 && !onPath; dx++) {
					for (int dz = -1; dz <= 1 && !onPath; dz++) {
						onPath = paths.contains(ChunkPos.pack(door[0] + dx, door[1] + dz)) && (dx == 0 || dz == 0);
					}
				}
				assertTrue(onPath, type + ": the door of " + b.archetype() + " at " + door[0] + "," + door[1] + " opens onto no path");
			}
			// a building's middle is never in the water (the fishery's back is)
			for (PlotSpec b : buildings) {
				int mx = b.origin().getX() + b.sizeX() / 2;
				int mz = b.origin().getZ() + b.sizeZ() / 2;
				assertTrue(!t.isWater(mx, mz) || b.archetype().getPath().equals("work/fishery"), type + ": " + b.archetype() + " stands in the water");
			}
		}
	}

	@Test
	void theTollGateStandsAstrideTheRoad() {
		TerrainProbe t = terrain(PostType.TOLL_GATE);
		PostSite site = site(PostType.TOLL_GATE, t);
		PlotSpec gate = PostLayouts.layOut(site, Culture.TEMPERATE, t).stream().filter(p -> p.archetype().getPath().equals("law/toll_gate")).findFirst()
			.orElseThrow();
		// the road runs along x at z = 0: the gate faces along it, and the road passes through the middle of its frontage
		assertEquals(Direction.Axis.X, gate.facing().getAxis());
		int z0 = gate.origin().getZ();
		int z1 = z0 + gate.sizeZ() - 1;
		assertTrue(z0 < 0 && z1 > 0 && (z0 + z1) / 2 == 0, "astride z 0: " + z0 + ".." + z1);
		int x0 = gate.origin().getX();
		assertTrue(x0 <= 0 && x0 + gate.sizeX() - 1 >= 0, "on the road's column 0");
		assertEquals(t.groundY(0, 0), gate.floorY(), "level with the road");
		assertTrue(gate.params().getBooleanOr("over_road", false));
	}

	@Test
	void theHospiceStandsOnItsSite() {
		TerrainProbe t = terrain(PostType.HOSPICE);
		PostSite site = site(PostType.HOSPICE, t);
		PlotSpec hospice = PostLayouts.layOut(site, Culture.ALPINE, t).stream().filter(p -> p.archetype().equals(PostLayouts.HOSPICE)).findFirst()
			.orElseThrow();
		int[] b = PlotGeometry.bounds(hospice);
		// the refuge's court spans the saddle's middle line across the way over (its back to the rising ground)
		assertTrue(b[0] <= site.x() && b[2] >= site.x(), "the hospice spans the site's middle across: " + java.util.Arrays.toString(b));
		assertEquals(site.facing(), hospice.facing(), "its gate faces the way in");
	}

	@Test
	void theLayoutIsDeterministicAndEveryBlueprintDraws() {
		var service = BuildModule.standalone();
		for (PostType type : PostType.values()) {
			TerrainProbe t = terrain(type);
			PostSite site = site(type, t);
			List<PlotSpec> a = PostLayouts.layOut(site, Culture.TEMPERATE, t);
			List<PlotSpec> b = PostLayouts.layOut(site, Culture.TEMPERATE, t);
			assertEquals(a, b, type + " is not deterministic");
			for (PlotSpec p : a) {
				BlueprintImpl bp = service.compiled(p, t);
				assertTrue(bp.totalSteps() > 0, type + ": " + p.archetype() + " draws nothing");
			}
		}
	}

	@Test
	void theCampsTwoGroundsAreApartAndAlike() {
		TerrainProbe t = terrain(PostType.NOMAD_CAMP);
		PostSite site = site(PostType.NOMAD_CAMP, t);
		List<PlotSpec> g0 = PostLayouts.camp(site, Culture.STEPPE, t, 0);
		List<PlotSpec> g1 = PostLayouts.camp(site, Culture.STEPPE, t, 1);
		assertEquals(g0.size(), g1.size());
		assertTrue(g0.stream().filter(p -> p.archetype().equals(PostLayouts.YURT)).count() == 4, "four yurts: " + g0);
		for (PlotSpec p : g0) {
			for (PlotSpec q : g1) {
				if (!p.archetype().getPath().startsWith("infra/") && !q.archetype().getPath().startsWith("infra/")) {
					assertFalse(PlotGeometry.overlaps(PlotGeometry.bounds(p), PlotGeometry.bounds(q)), "the grounds overlap");
				}
			}
		}
	}
}
