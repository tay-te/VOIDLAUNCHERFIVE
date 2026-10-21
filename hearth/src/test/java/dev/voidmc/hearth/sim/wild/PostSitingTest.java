package dev.voidmc.hearth.sim.wild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.sim.atlas.Atlas;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The site overlay of posts (docs/LIVING_WORLD_2.md §7.2, §7.8). */
class PostSitingTest {
	private static final WildConfig.Wild CONFIG = WildConfig.Wild.DEFAULTS;

	/** A saddle at (512, 512): rising along x, falling along z, level round its middle, at y 140. */
	static int saddle(int x, int z) {
		double dx = x - 512;
		double dz = z - 512;
		return (int) Math.round(Math.max(70, Math.min(300, 140 + 0.02 * dx * dx - 0.01 * dz * dz)));
	}

	@Test
	void expansesPassRuleFindsASyntheticSaddle() {
		WildLand land = new WildLand(PostSitingTest::saddle, 1);
		int[] axis = new int[1];
		float s = PostSites.pass(land.probe(), 512, 512, axis);
		assertTrue(s >= 20, "a saddle rising 46 and falling 23 at 48: " + s);
		assertEquals(0, axis[0], "it rises along x");
		// not on its flank, nor on flat land
		assertEquals(0, PostSites.pass(land.probe(), 512 + 120, 512, axis));
		assertEquals(0, PostSites.pass(new WildLand((x, z) -> 140, 1).probe(), 0, 0, axis));
		// not below y 110 (Expanse's floor for a pass)
		assertEquals(0, PostSites.pass(new WildLand((x, z) -> saddle(x, z) - 60, 1).probe(), 512, 512, axis));
	}

	@Test
	void aHospiceStandsOnTheSaddle() {
		WildLand land = new WildLand(PostSitingTest::saddle, 7);
		List<PostSite> sites = PostSites.region(land, CONFIG, 0, 0);
		PostSite hospice = sites.stream().filter(p -> p.type() == PostType.HOSPICE).findFirst().orElse(null);
		assertNotNull(hospice, "a hospice on the saddle: " + sites);
		assertTrue(Math.abs(hospice.x() - 512) <= 48 && Math.abs(hospice.z() - 512) <= 48, "on the saddle: " + hospice);
		assertTrue(PostSites.pass(land.probe(), hospice.x(), hospice.z(), new int[1]) > 0, "its centre passes the pass rule");
	}

	@Test
	void sitingIsDeterministic() {
		for (long seed : new long[]{1, 2, 3, 20261003}) {
			WildLand a = hills(seed);
			WildLand b = hills(seed);
			for (int[] r : new int[][]{{0, 0}, {1, -1}, {-2, 3}}) {
				assertEquals(PostSites.region(a, CONFIG, r[0], r[1]), PostSites.region(b, CONFIG, r[0], r[1]), "seed " + seed + " region " + r[0] + "," + r[1]);
			}
		}
	}

	/** Upland hills, moor culture, a wood to the north, a lake to the south-east, two roads and a town. */
	static WildLand hills(long seed) {
		WildLand land = new WildLand((x, z) -> 115 + (int) Math.round(10 * Math.sin(x / 70.0 + seed) + 8 * Math.cos(z / 90.0 - seed)), seed,
			(x, z) -> Math.hypot(Math.floorMod(x, 1024) - 800, Math.floorMod(z, 1024) - 800) < 90);
		land.biome = (x, z) -> Math.floorMod(z, 1024) < 380 ? "dark_forest" : "meadow";
		land.culture = (x, z) -> Culture.MOORLAND;
		land.towns.add(new int[]{200, 900, 120});
		return land;
	}

	@Test
	void postsKeepTheirSpacingAndTheCapAndStayClearOfTowns() {
		int total = 0;
		for (long seed = 1; seed <= 6; seed++) {
			WildLand land = hills(seed);
			land.road(1, true, -200, 300, 1300, 330, i -> i >= 700 && i < 716);
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					List<PostSite> sites = PostSites.region(land, CONFIG, rx, rz);
					assertTrue(sites.size() <= CONFIG.perRegion(), sites.size() + " posts in region " + rx + "," + rz);
					total += sites.size();
					for (int i = 0; i < sites.size(); i++) {
						PostSite p = sites.get(i);
						assertTrue(p.inRegion(rx, rz), p + " outside its region");
						assertEquals(PostSite.key(rx, rz, i), p.key());
						assertEquals(rx, PostSite.regionX(p.key()));
						assertEquals(rz, PostSite.regionZ(p.key()));
						// its whole square in its region (Claims ask only the column's own region), 100 from the edges
						assertEquals(rx, Atlas.region(p.minX() - PostSites.EDGE + 1));
						assertEquals(rx, Atlas.region(p.maxX() + PostSites.EDGE - 1));
						assertEquals(rz, Atlas.region(p.minZ() - PostSites.EDGE + 1));
						assertEquals(rz, Atlas.region(p.maxZ() + PostSites.EDGE - 1));
						for (int[] t : land.towns) {
							assertTrue(Math.hypot(t[0] - p.x(), t[1] - p.z()) - t[2] >= (p.type() == PostType.TOLL_GATE ? PostSites.TOLL_CLEARANCE : PostSites.TOWN_CLEARANCE), p + " too near the town");
						}
						for (int j = 0; j < i; j++) {
							PostSite o = sites.get(j);
							assertTrue(Math.hypot(o.x() - p.x(), o.z() - p.z()) >= PostSites.SPACING, p + " too near " + o);
						}
						assertFalse(land.probe().isWater(p.x(), p.z()), p + " in the water");
					}
				}
			}
		}
		assertTrue(total >= 6, "posts sited over six worlds of nine regions: " + total);
	}

	@Test
	void theCapIsTheConfigs() {
		WildLand land = hills(4);
		land.road(1, true, -200, 300, 1300, 330, i -> i >= 700 && i < 716);
		assertTrue(PostSites.region(land, new WildConfig.Wild(true, 1, 6000, 12), 0, 0).size() <= 1);
		assertTrue(PostSites.region(land, new WildConfig.Wild(true, 0, 6000, 12), 0, 0).isEmpty());
		assertTrue(PostSites.region(land, new WildConfig.Wild(false, 3, 6000, 12), 0, 0).isEmpty());
	}

	@Test
	void roadPostsStandOnTheirRoad() {
		PostSite toll = null;
		PostSite mid = null;
		PostSites.RoadLine r = null;
		// the types are rolled per region (a toll gate 80 %, a caravan stop 70 %): over a few worlds both turn up
		for (long seed = 0; seed < 16 && (toll == null || mid == null); seed++) {
			WildLand land = new WildLand((x, z) -> 80, seed);
			// a long king's road across the region, a bridge 16 columns long at its 700th column (x about 300)
			r = land.road(5, true, -400, 500, 1500, 520, i -> i >= 700 && i < 716);
			for (PostSite p : PostSites.region(land, CONFIG, 0, 0)) {
				if (p.type() == PostType.TOLL_GATE && toll == null) {
					toll = p;
				}
				if ((p.type() == PostType.CARAVAN_STOP || p.type() == PostType.INN) && mid == null) {
					mid = p;
				}
			}
		}
		assertNotNull(toll, "a toll gate at the bridge's land end");
		// on the road itself, at a bridge end, facing along it
		assertTrue(near(r, toll.x(), toll.z(), 1), toll + " is not on the road");
		int tx = toll.x();
		assertTrue(tx >= r.xs()[716] + 4 && tx <= r.xs()[716] + 32 || tx <= r.xs()[699] - 4 && tx >= r.xs()[699] - 32, toll + " not off the bridge's end");
		assertTrue(toll.facing().getAxis() == net.minecraft.core.Direction.Axis.X, "facing along the road: " + toll.facing());
		assertTrue(toll.road().length >= 30, "the road's line kept for the layout");
		// the middle of the long king's road: a caravan stop or an inn, beside it and facing it
		assertNotNull(mid, "a stop at the middle of a 1 900-block road");
		assertFalse(near(r, mid.x(), mid.z(), 8), mid + " stands on the road");
		assertTrue(near(r, mid.x(), mid.z(), mid.type().radius), mid + " is not by the road");
		assertTrue(mid.facing().getAxis() == net.minecraft.core.Direction.Axis.Z, "facing the road: " + mid.facing());
	}

	@Test
	void aShorterKingsRoadHasItsTollAtItsMiddle() {
		PostSite toll = null;
		PostSites.RoadLine r = null;
		for (long seed = 0; seed < 16 && toll == null; seed++) {
			WildLand land = new WildLand((x, z) -> 80, seed);
			// 1 000 blocks between two towns 1 400 apart (their reach 100), no bridge
			land.towns.add(new int[]{-200, 500, 100});
			land.towns.add(new int[]{1200, 500, 100});
			r = land.road(9, true, 0, 500, 1000, 500, i -> false);
			for (PostSite p : PostSites.region(land, CONFIG, 0, 0)) {
				if (p.type() == PostType.TOLL_GATE) {
					toll = p;
				}
			}
		}
		assertNotNull(toll, "a toll at the border on the king's road");
		assertTrue(near(r, toll.x(), toll.z(), 1) && Math.abs(toll.x() - 500) <= 130, toll + " not on the road's middle");
	}

	private static boolean near(PostSites.RoadLine r, int x, int z, int d) {
		for (int i = 0; i < r.size(); i++) {
			if (Math.hypot(r.xs()[i] - x, r.zs()[i] - z) <= d) {
				return true;
			}
		}
		return false;
	}

	@Test
	void neverInsideATownsCore() {
		WildLand land = new WildLand(PostSitingTest::saddle, 7);
		land.towns.add(new int[]{560, 560, 150});
		for (PostSite p : PostSites.region(land, CONFIG, 0, 0)) {
			assertTrue(Math.hypot(560 - p.x(), 560 - p.z()) >= 150 + (p.type() == PostType.TOLL_GATE ? PostSites.TOLL_CLEARANCE : PostSites.TOWN_CLEARANCE), p + " in the town's land");
		}
	}

	@Test
	void nomadsHaveTwoGroundsAndAHarbourItsWater() {
		WildLand steppe = new WildLand((x, z) -> 75, 3);
		steppe.culture = (x, z) -> Culture.STEPPE;
		PostSite camp = PostSites.region(steppe, CONFIG, 0, 0).stream().filter(p -> p.type() == PostType.NOMAD_CAMP).findFirst().orElse(null);
		if (camp != null) {
			assertTrue(Math.abs(camp.gx()) + Math.abs(camp.gz()) >= 60, "two grounds apart: " + camp);
			assertTrue(camp.claims(camp.groundX(0), camp.groundZ(0)) && camp.claims(camp.groundX(1), camp.groundZ(1)));
		}
		// a coast along z = 600: land north of it at 66, sea south
		WildLand coast = new WildLand((x, z) -> Math.floorMod(z, 1024) < 600 ? 66 : 50, 5, (x, z) -> Math.floorMod(z, 1024) >= 600);
		coast.biome = (x, z) -> "beach";
		boolean harbour = false;
		for (long s = 0; s < 6 && !harbour; s++) {
			WildLand c = new WildLand((x, z) -> Math.floorMod(z, 1024) < 600 ? 66 : 50, s, (x, z) -> Math.floorMod(z, 1024) >= 600);
			for (PostSite p : PostSites.region(c, CONFIG, 0, 0)) {
				if (p.type() == PostType.HARBOUR) {
					harbour = true;
					assertEquals(net.minecraft.core.Direction.SOUTH, p.facing(), "the harbour faces the water");
				}
			}
		}
		assertTrue(harbour, "a harbour on a long coast in one of six worlds");
	}
}
