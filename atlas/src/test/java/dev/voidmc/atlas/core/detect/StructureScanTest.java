package dev.voidmc.atlas.core.detect;

import static dev.voidmc.atlas.core.detect.StructureKind.ANCIENT_CITY;
import static dev.voidmc.atlas.core.detect.StructureKind.BASTION;
import static dev.voidmc.atlas.core.detect.StructureKind.CAVE_MOUTH;
import static dev.voidmc.atlas.core.detect.StructureKind.DESERT_PYRAMID;
import static dev.voidmc.atlas.core.detect.StructureKind.DUNGEON;
import static dev.voidmc.atlas.core.detect.StructureKind.END_CITY;
import static dev.voidmc.atlas.core.detect.StructureKind.FORTRESS;
import static dev.voidmc.atlas.core.detect.StructureKind.GEODE;
import static dev.voidmc.atlas.core.detect.StructureKind.IGLOO;
import static dev.voidmc.atlas.core.detect.StructureKind.JUNGLE_TEMPLE;
import static dev.voidmc.atlas.core.detect.StructureKind.MANSION;
import static dev.voidmc.atlas.core.detect.StructureKind.MINESHAFT;
import static dev.voidmc.atlas.core.detect.StructureKind.MONUMENT;
import static dev.voidmc.atlas.core.detect.StructureKind.OCEAN_RUIN;
import static dev.voidmc.atlas.core.detect.StructureKind.PILLAGER_OUTPOST;
import static dev.voidmc.atlas.core.detect.StructureKind.RUINED_PORTAL;
import static dev.voidmc.atlas.core.detect.StructureKind.SHIPWRECK;
import static dev.voidmc.atlas.core.detect.StructureKind.STRONGHOLD;
import static dev.voidmc.atlas.core.detect.StructureKind.TRAIL_RUINS;
import static dev.voidmc.atlas.core.detect.StructureKind.TRIAL_CHAMBERS;
import static dev.voidmc.atlas.core.detect.StructureKind.VILLAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every detector against the vanilla structure it is for, and against the closest natural or player-built look-alike,
 * built block by block in a {@link FakeWorld}. Each case asserts exactly which kinds are sighted.
 */
class StructureScanTest {
	// --- helpers ---------------------------------------------------------------------------------------------------

	/** Scans every chunk in the square, in order (or in reverse), feeding a {@link Detections}; all sightings made. */
	private static List<Sighting> sightings(FakeWorld w, int cx0, int cz0, int cx1, int cz1, boolean ceiling, boolean reverse) {
		StructureScan scan = new StructureScan();
		Detections detections = new Detections();
		List<Sighting> all = new ArrayList<>();
		List<int[]> order = new ArrayList<>();
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				order.add(new int[]{cx, cz});
			}
		}
		if (reverse) {
			java.util.Collections.reverse(order);
		}
		for (int[] o : order) {
			List<Sighting> here = new ArrayList<>();
			StructureScan.Tally tally = scan.scan(w.chunk(o[0], o[1]), w, ceiling, here::add);
			// a chunk never reports the same structure twice
			Set<StructureKind> kinds = EnumSet.noneOf(StructureKind.class);
			for (Sighting s : here) {
				assertTrue(kinds.add(s.kind()), "chunk " + o[0] + "," + o[1] + " reported " + s.kind() + " twice: " + here);
				assertEquals(o[0], s.x() >> 4, "sighting in its own chunk: " + s);
				assertEquals(o[1], s.z() >> 4, "sighting in its own chunk: " + s);
			}
			all.addAll(here);
			detections.chunk(o[0], o[1], tally, all::add);
		}
		return all;
	}

	private static List<Sighting> sightings(FakeWorld w, int cx, int cz, boolean ceiling) {
		return sightings(w, cx, cz, cx, cz, ceiling, false);
	}

	private static List<Sighting> sightings(FakeWorld w) {
		return sightings(w, 0, 0, false);
	}

	private static List<StructureKind> kinds(List<Sighting> s) {
		return s.stream().map(Sighting::kind).toList();
	}

	private static List<StructureKind> kinds(FakeWorld w) {
		return kinds(sightings(w));
	}

	private static List<StructureKind> nether(FakeWorld w) {
		List<Sighting> s = sightings(w, 0, 0, true);
		for (Sighting x : s) {
			assertFalse(x.underground(), "nothing is underground under the Nether's roof");
		}
		return kinds(s);
	}

	/** A bed: two blocks, foot and head. */
	private static void bed(FakeWorld w, int x, int y, int z) {
		w.set(x, y, z, "minecraft:red_bed").set(x, y, z + 1, "minecraft:red_bed");
	}

	private static FakeWorld ocean(int floor) {
		FakeWorld w = new FakeWorld(floor, "minecraft:sand");
		w.fill(-16, floor + 1, -16, 31, 62, 31, "minecraft:water");
		return w;
	}

	// --- village ---------------------------------------------------------------------------------------------------

	private static FakeWorld village(int bedX, int bedZ, int jobX, int jobZ) {
		FakeWorld w = new FakeWorld();
		w.set(8, 64, 8, "minecraft:bell");
		w.fill(7, 63, 7, 9, 63, 9, "minecraft:cobblestone");
		if (bedX != Integer.MIN_VALUE) {
			bed(w, bedX, 64, bedZ);
			bed(w, bedX + 3, 64, bedZ);
		}
		if (jobX != Integer.MIN_VALUE) {
			w.set(jobX, 64, jobZ, "minecraft:composter");
		}
		return w;
	}

	@Test
	void villageIsABellWithBedsAndAJobSiteInTheNeighbouringChunk() {
		FakeWorld w = village(2, 2, 20, 8);
		List<Sighting> s = sightings(w, -1, -1, 2, 2, false, false);
		assertEquals(List.of(VILLAGE), kinds(s));
		assertEquals(new Sighting(VILLAGE, 8, 64, 8, false), s.get(0), "at the bell, on the surface");
	}

	@Test
	void aBellWithOneBedAndAJobSiteIsAPlayersCampNotAVillage() {
		FakeWorld w = new FakeWorld();
		w.set(8, 64, 8, "minecraft:bell");
		bed(w, 2, 64, 2);
		w.set(20, 64, 8, "minecraft:composter");
		assertEquals(List.of(), kinds(sightings(w, -1, -1, 2, 2, false, false)));
	}

	@Test
	void villageIsFoundWhicheverChunkArrivesLast() {
		// the job site's chunk first, the bell's after: the bell is judged as it arrives
		FakeWorld w = village(2, 2, 20, 8);
		assertEquals(List.of(VILLAGE), kinds(sightings(w, -1, -1, 2, 2, false, true)));
	}

	@Test
	void villageEvidenceAcrossChunkBorders() {
		// beds to the north-west, the composter to the south-east: three chunks hold the village
		FakeWorld w = village(-6, -6, 30, 30);
		assertEquals(List.of(VILLAGE), kinds(sightings(w, -2, -2, 3, 3, false, false)));
	}

	@Test
	void bellAloneIsNoVillage() {
		assertEquals(List.of(), kinds(sightings(village(Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0), -2, -2, 2, 2, false, false)));
	}

	@Test
	void bellAndBedsWithoutWorkIsNoVillage() {
		assertEquals(List.of(), kinds(sightings(village(2, 2, Integer.MIN_VALUE, 0), -2, -2, 2, 2, false, false)));
	}

	@Test
	void bedsAndWorkMoreThanFortyEightBlocksAwayAreNoVillage() {
		// beds 62 blocks east, the composter 62 south: each chunk's nearest corner is 56 away
		FakeWorld w = village(70, 8, 8, 70);
		assertEquals(List.of(), kinds(sightings(w, -1, -1, 6, 6, false, false)));
	}

	// --- ruined portal ---------------------------------------------------------------------------------------------

	/** A broken portal frame, four wide and five high, at z = 8, with crying obsidian in it and netherrack round. */
	private static void portal(FakeWorld w, int x0, int y0, int z) {
		w.shell(x0, y0, z, x0 + 3, y0 + 4, z, "minecraft:obsidian");
		w.fill(x0 + 1, y0 + 1, z, x0 + 2, y0 + 3, z, "minecraft:air");
		w.set(x0, y0 + 1, z, "minecraft:crying_obsidian");
		w.set(x0 + 3, y0 + 3, z, "minecraft:crying_obsidian");
		w.set(x0 + 3, y0 + 4, z, "minecraft:air");
		w.set(x0 - 1, y0 - 1, z, "minecraft:netherrack").set(x0 + 4, y0 - 1, z + 1, "minecraft:magma_block");
		w.set(x0 + 1, y0, z + 1, "minecraft:gold_block");
	}

	@Test
	void ruinedPortalIsCryingObsidianInAnObsidianFrame() {
		FakeWorld w = new FakeWorld();
		portal(w, 5, 64, 8);
		List<Sighting> s = sightings(w);
		assertEquals(List.of(RUINED_PORTAL), kinds(s));
		assertFalse(s.get(0).underground());
	}

	@Test
	void loneCryingObsidianIsNoPortal() {
		FakeWorld w = new FakeWorld();
		w.set(8, 40, 8, "minecraft:crying_obsidian");
		w.set(4, 64, 4, "minecraft:crying_obsidian");
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void cryingObsidianInABastionIsNoPortal() {
		FakeWorld w = new FakeWorld(63, "minecraft:netherrack");
		w.shell(1, 64, 1, 14, 70, 14, "minecraft:polished_blackstone_bricks");
		w.set(8, 64, 8, "minecraft:obsidian").set(8, 65, 8, "minecraft:crying_obsidian").set(8, 66, 8, "minecraft:obsidian");
		w.set(9, 65, 8, "minecraft:obsidian").set(7, 65, 8, "minecraft:obsidian").set(8, 67, 8, "minecraft:crying_obsidian");
		// the blackstone is a bastion; the obsidian in it is not a portal
		assertEquals(List.of(BASTION), nether(w));
	}

	// --- shipwreck -------------------------------------------------------------------------------------------------

	@Test
	void shipwreckIsAChestInADrownedHull() {
		FakeWorld w = ocean(40);
		// keel and floor of planks, sides of stripped oak, a broken deck of trapdoors, stairs at the bow
		w.fill(3, 41, 6, 13, 41, 10, "minecraft:oak_planks");
		w.fill(3, 42, 6, 13, 43, 6, "minecraft:stripped_oak_log");
		w.fill(3, 42, 10, 13, 43, 10, "minecraft:stripped_oak_log");
		w.fill(5, 44, 7, 7, 44, 9, "minecraft:oak_trapdoor[waterlogged]");
		w.fill(2, 42, 7, 2, 43, 9, "minecraft:oak_stairs[waterlogged]");
		w.set(8, 42, 8, "minecraft:chest[waterlogged]");
		List<Sighting> s = sightings(w);
		assertEquals(List.of(SHIPWRECK), kinds(s));
		assertEquals(8, s.get(0).x());
		assertFalse(s.get(0).underground(), "lying on the sea floor");
	}

	@Test
	void chestInADryPlankHouseIsNoShipwreck() {
		FakeWorld w = new FakeWorld();
		w.shell(3, 64, 3, 13, 69, 13, "minecraft:oak_planks");
		w.fill(3, 64, 3, 3, 69, 3, "minecraft:oak_log");
		w.set(8, 65, 8, "minecraft:chest");
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void chestOnADockBesideTheWaterIsNoShipwreck() {
		FakeWorld w = new FakeWorld();
		// the shore at x < 8, the sea beyond; a plank deck on posts standing in the water
		w.fill(8, 56, -8, 31, 63, 24, "minecraft:water");
		w.fill(4, 64, 6, 14, 64, 10, "minecraft:oak_planks");
		for (int x : new int[]{8, 12}) {
			w.fill(x, 56, 6, x, 63, 6, "minecraft:oak_log");
			w.fill(x, 56, 10, x, 63, 10, "minecraft:oak_log");
		}
		w.set(10, 65, 8, "minecraft:chest");
		w.set(6, 65, 8, "minecraft:barrel");
		assertEquals(List.of(), kinds(w));
	}

	// --- desert pyramid --------------------------------------------------------------------------------------------

	/** The main hall: sandstone, the floor's middle a blue terracotta in a star of orange. */
	private static FakeWorld pyramidHall(String wall, boolean orange, boolean blue) {
		FakeWorld w = new FakeWorld(63, "minecraft:sand");
		w.shell(1, 63, 1, 15, 72, 15, wall);
		if (blue) {
			w.set(8, 63, 8, "minecraft:blue_terracotta");
		}
		if (orange) {
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
				w.set(8 + d[0], 63, 8 + d[1], "minecraft:orange_terracotta");
			}
		}
		return w;
	}

	@Test
	void desertPyramidIsTheStarOfTerracottaInASandstoneHall() {
		List<Sighting> s = sightings(pyramidHall("minecraft:sandstone", true, true));
		assertEquals(List.of(DESERT_PYRAMID), kinds(s));
		assertEquals(new Sighting(DESERT_PYRAMID, 8, 63, 8, false), s.get(0));
	}

	@Test
	void desertPyramidTrapIsTntUnderAPressurePlate() {
		FakeWorld w = new FakeWorld(63, "minecraft:sand");
		w.fill(3, 49, 3, 13, 58, 13, "minecraft:sandstone");
		w.fill(5, 54, 5, 11, 57, 11, "minecraft:air");
		w.set(8, 54, 8, "minecraft:stone_pressure_plate");
		w.fill(7, 52, 7, 9, 52, 9, "minecraft:tnt");
		w.set(5, 54, 8, "minecraft:chest");
		List<Sighting> s = sightings(w);
		assertEquals(List.of(DESERT_PYRAMID), kinds(s));
		// the room lies under the pyramid's own sandstone and a few blocks of sand: part of a structure seen on the
		// surface, not something hidden under the ground
		assertFalse(s.get(0).underground(), "the treasure room belongs to the pyramid above it");
	}

	@Test
	void blueTerracottaAloneInSandstoneIsNoPyramid() {
		assertEquals(List.of(), kinds(pyramidHall("minecraft:sandstone", false, true)));
	}

	@Test
	void terracottaStarWithoutSandstoneIsNoPyramid() {
		FakeWorld w = pyramidHall("minecraft:stone_bricks", true, true);
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void tntStoreWithoutAPressurePlateIsNoPyramid() {
		FakeWorld w = new FakeWorld(63, "minecraft:sand");
		w.shell(3, 63, 3, 13, 70, 13, "minecraft:sandstone");
		w.fill(6, 64, 6, 10, 64, 10, "minecraft:tnt");
		assertEquals(List.of(), kinds(w));
	}

	// --- jungle temple ---------------------------------------------------------------------------------------------

	/** A hall of cobblestone, every other block mossy, a chiseled stone brick in its face. */
	private static FakeWorld temple(boolean mossy, boolean chiseled) {
		FakeWorld w = new FakeWorld();
		w.shell(2, 63, 2, 14, 74, 14, "minecraft:cobblestone");
		if (mossy) {
			for (int y = 63; y <= 74; y++) {
				for (int z = 2; z <= 14; z++) {
					for (int x = 2; x <= 14; x++) {
						if ((x + y + z) % 2 == 0 && w.id(x, y, z).equals("minecraft:cobblestone")) {
							w.set(x, y, z, "minecraft:mossy_cobblestone");
						}
					}
				}
			}
		}
		if (chiseled) {
			w.set(8, 70, 2, "minecraft:chiseled_stone_bricks");
			w.set(8, 66, 14, "minecraft:chiseled_stone_bricks");
		}
		return w;
	}

	@ParameterizedTest
	@ValueSource(strings = {"minecraft:dispenser", "minecraft:tripwire_hook", "minecraft:sticky_piston"})
	void jungleTempleIsATrapInAMossyHall(String trap) {
		FakeWorld w = temple(true, true);
		w.set(4, 65, 8, trap);
		assertEquals(List.of(JUNGLE_TEMPLE), kinds(w));
	}

	@Test
	void dispenserInAPlainCobblestoneBuildIsNoTemple() {
		FakeWorld w = temple(false, true);
		w.set(4, 65, 8, "minecraft:dispenser");
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void mossyHallWithoutATrapIsNoTemple() {
		assertEquals(List.of(), kinds(temple(true, true)));
	}

	// --- palette counts: monument, mansion, end city, bastion ------------------------------------------------------

	@Test
	void monumentIsAChunkOfPrismarineWithDarkPrismarine() {
		FakeWorld w = ocean(40);
		w.fill(0, 41, 0, 15, 42, 3, "minecraft:prismarine_bricks");
		w.fill(6, 43, 6, 9, 43, 6, "minecraft:dark_prismarine");
		assertEquals(List.of(MONUMENT), kinds(w));
	}

	@Test
	void monumentIsAChunkOfPrismarineWithSeaLanterns() {
		FakeWorld w = ocean(40);
		w.fill(0, 41, 0, 15, 42, 3, "minecraft:prismarine");
		w.set(4, 43, 4, "minecraft:sea_lantern").set(10, 43, 4, "minecraft:sea_lantern");
		assertEquals(List.of(MONUMENT), kinds(w));
	}

	@Test
	void plainPrismarineIsNoMonument() {
		FakeWorld w = ocean(40);
		w.fill(0, 41, 0, 9, 41, 9, "minecraft:prismarine");
		assertEquals(List.of(), kinds(w));
		FakeWorld few = ocean(40);
		few.fill(0, 41, 0, 9, 41, 3, "minecraft:prismarine");
		few.fill(0, 42, 0, 9, 42, 0, "minecraft:dark_prismarine");
		assertEquals(List.of(), kinds(few), "40 prismarine and 10 dark is too little");
	}

	@Test
	void mansionIsDarkOakPlanksOnCobblestone() {
		FakeWorld w = new FakeWorld();
		w.fill(1, 63, 1, 14, 63, 2, "minecraft:cobblestone");
		w.fill(1, 64, 1, 14, 64, 14, "minecraft:dark_oak_planks");
		assertEquals(List.of(MANSION), kinds(w));
	}

	@Test
	void darkForestIsNoMansion() {
		FakeWorld w = new FakeWorld(63, "minecraft:grass_block");
		for (int[] t : new int[][]{{2, 2}, {10, 2}, {2, 10}, {10, 10}}) {
			w.fill(t[0], 64, t[1], t[0] + 1, 82, t[1] + 1, "minecraft:dark_oak_log");
		}
		w.fill(0, 83, 0, 15, 85, 15, "minecraft:dark_oak_leaves");
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void endCityIsPurpur() {
		FakeWorld w = new FakeWorld(63, "minecraft:end_stone");
		w.shell(4, 64, 4, 12, 70, 12, "minecraft:purpur_block");
		w.fill(8, 64, 8, 8, 70, 8, "minecraft:purpur_pillar");
		assertEquals(List.of(END_CITY), kinds(w));
		FakeWorld few = new FakeWorld(63, "minecraft:end_stone");
		few.fill(4, 64, 4, 8, 65, 6, "minecraft:purpur_block");
		assertEquals(List.of(), kinds(few), "30 purpur is a player's build");
	}

	@Test
	void bastionIsBlackstoneBricks() {
		FakeWorld w = new FakeWorld(40, "minecraft:netherrack");
		w.fill(0, 41, 0, 9, 42, 4, "minecraft:polished_blackstone_bricks");
		assertEquals(List.of(BASTION), nether(w));
		FakeWorld gilded = new FakeWorld(40, "minecraft:netherrack");
		gilded.fill(0, 41, 0, 9, 41, 3, "minecraft:polished_blackstone_bricks");
		gilded.set(5, 42, 2, "minecraft:gilded_blackstone");
		assertEquals(List.of(BASTION), nether(gilded));
	}

	@Test
	void someBlackstoneBricksAreNoBastion() {
		FakeWorld w = new FakeWorld(40, "minecraft:netherrack");
		w.fill(0, 41, 0, 9, 41, 3, "minecraft:polished_blackstone_bricks");
		w.fill(0, 42, 0, 9, 42, 0, "minecraft:blackstone");
		assertEquals(List.of(), nether(w));
	}

	// --- dungeon ---------------------------------------------------------------------------------------------------

	/** A vanilla dungeon round (x, y, z): a 7x7 room four high, cobblestone walls and a mostly mossy floor. */
	private static void dungeon(FakeWorld w, int x, int y, int z, boolean spawner) {
		w.shell(x - 4, y - 1, z - 4, x + 4, y + 4, z + 4, "minecraft:cobblestone");
		for (int dz = -4; dz <= 4; dz++) {
			for (int dx = -4; dx <= 4; dx++) {
				if (Math.floorMod(dx + dz, 4) != 0) {
					w.set(x + dx, y - 1, z + dz, "minecraft:mossy_cobblestone");
				}
			}
		}
		// the ceiling is the rock round it
		w.fill(x - 4, y + 4, z - 4, x + 4, y + 4, z + 4, "minecraft:stone");
		w.fill(x - 3, y, z - 3, x + 3, y + 3, z + 3, "minecraft:cave_air");
		w.set(x - 3, y, z + 3, "minecraft:chest");
		if (spawner) {
			w.set(x, y, z, "minecraft:spawner");
		}
	}

	@Test
	void dungeonIsASpawnerInAMossyCobblestoneRoom() {
		FakeWorld w = new FakeWorld();
		dungeon(w, 8, 20, 8, true);
		List<Sighting> s = sightings(w);
		assertEquals(List.of(new Sighting(DUNGEON, 8, 20, 8, true)), s, "at the spawner, underground");
	}

	@Test
	void dungeonAcrossAChunkBorderIsSeenFromTheSpawnersChunk() {
		FakeWorld w = new FakeWorld();
		// the spawner on the last column of chunk 0, most of the room in chunk 1
		dungeon(w, 15, 20, 8, true);
		assertEquals(List.of(DUNGEON), kinds(sightings(w, 0, 0, false)));
		assertEquals(List.of(), kinds(sightings(w, 1, 0, false)), "the room alone, without its spawner");
	}

	@Test
	void twoSpawnersOfADoubleDungeonAreOneSighting() {
		FakeWorld w = new FakeWorld();
		dungeon(w, 4, 20, 8, true);
		dungeon(w, 11, 20, 8, true);
		assertEquals(List.of(DUNGEON), kinds(w));
	}

	@Test
	void spawnerInPlainStoneIsNoDungeon() {
		FakeWorld w = new FakeWorld();
		w.fill(5, 20, 5, 11, 23, 11, "minecraft:cave_air");
		w.set(8, 20, 8, "minecraft:spawner");
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void mossyRoomWithoutASpawnerIsNoDungeon() {
		FakeWorld w = new FakeWorld();
		dungeon(w, 8, 20, 8, false);
		assertEquals(List.of(), kinds(w));
	}

	// --- mineshaft -------------------------------------------------------------------------------------------------

	/** A corridor along x at z 7..9, floor y, with oak supports every four blocks and, if asked, rails. */
	private static void corridor(FakeWorld w, int y, boolean rails) {
		w.fill(0, y, 7, 15, y + 2, 9, "minecraft:cave_air");
		for (int x = 2; x < 16; x += 4) {
			w.fill(x, y, 7, x, y + 1, 7, "minecraft:oak_fence");
			w.fill(x, y, 9, x, y + 1, 9, "minecraft:oak_fence");
			w.fill(x, y + 2, 7, x, y + 2, 9, "minecraft:oak_planks");
		}
		if (rails) {
			w.fill(0, y, 8, 15, y, 8, "minecraft:rail");
		}
	}

	@Test
	void mineshaftIsRailsSupportsAndCobwebUnderground() {
		FakeWorld w = new FakeWorld();
		corridor(w, 30, true);
		w.set(5, 32, 8, "minecraft:cobweb");
		List<Sighting> s = sightings(w);
		assertEquals(List.of(MINESHAFT), kinds(s));
		assertTrue(s.get(0).underground());
	}

	@Test
	void caveSpiderSpawnerInWebsIsAMineshaft() {
		FakeWorld w = new FakeWorld();
		corridor(w, 30, false);
		w.set(8, 30, 8, "minecraft:spawner");
		for (int[] d : new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {-1, 1, 0}, {1, 1, 0}, {0, 2, 0}, {0, 0, 1}, {0, 0, -1}}) {
			w.set(8 + d[0], 30 + d[1], 8 + d[2], "minecraft:cobweb");
		}
		List<Sighting> s = sightings(w);
		assertEquals(List.of(new Sighting(MINESHAFT, 8, 30, 8, true)), s);
	}

	@Test
	void railwayWithoutCobwebsIsNoMineshaft() {
		FakeWorld w = new FakeWorld();
		corridor(w, 30, true);
		assertEquals(List.of(), kinds(w));
	}

	// --- ancient city ----------------------------------------------------------------------------------------------

	@Test
	void ancientCityIsReinforcedDeepslate() {
		FakeWorld w = new FakeWorld();
		w.fill(6, -40, 8, 10, -40, 8, "minecraft:reinforced_deepslate");
		w.fill(4, -41, 4, 12, -41, 12, "minecraft:deepslate_tiles");
		List<Sighting> s = sightings(w);
		assertEquals(List.of(ANCIENT_CITY), kinds(s));
		assertTrue(s.get(0).underground());
	}

	@Test
	void ancientCityIsDeepslateBricksWithSculkAndAShrieker() {
		FakeWorld w = new FakeWorld();
		w.fill(0, -45, 0, 15, -45, 3, "minecraft:deepslate_tiles");
		w.fill(0, -45, 4, 7, -45, 5, "minecraft:polished_deepslate");
		w.fill(4, -44, 8, 8, -44, 9, "minecraft:sculk");
		w.set(6, -43, 8, "minecraft:sculk_shrieker");
		assertEquals(List.of(ANCIENT_CITY), kinds(w));
	}

	@Test
	void naturalDeepDarkIsNoAncientCity() {
		FakeWorld w = new FakeWorld();
		w.fill(0, -50, 0, 15, -40, 15, "minecraft:deepslate");
		w.fill(2, -40, 2, 13, -36, 13, "minecraft:cave_air");
		w.fill(2, -41, 2, 9, -41, 9, "minecraft:sculk");
		w.fill(2, -40, 2, 5, -40, 2, "minecraft:sculk_vein");
		w.set(6, -40, 6, "minecraft:sculk_shrieker").set(8, -40, 8, "minecraft:sculk_sensor").set(10, -41, 10, "minecraft:sculk_catalyst");
		assertEquals(List.of(), kinds(w));
	}

	// --- trial chambers --------------------------------------------------------------------------------------------

	@ParameterizedTest
	@ValueSource(strings = {"minecraft:trial_spawner", "minecraft:vault"})
	void trialChambersIsATrialSpawnerAmongTuffBricksAndCopper(String anchor) {
		FakeWorld w = new FakeWorld();
		w.shell(3, -21, 3, 13, -15, 13, "minecraft:tuff_bricks");
		w.fill(4, -21, 4, 12, -21, 12, "minecraft:polished_tuff");
		w.set(4, -18, 3, "minecraft:waxed_copper_bulb").set(12, -18, 3, "minecraft:waxed_oxidized_cut_copper");
		w.set(8, -20, 8, anchor);
		List<Sighting> s = sightings(w);
		assertEquals(List.of(TRIAL_CHAMBERS), kinds(s));
		assertTrue(s.get(0).underground());
	}

	@Test
	void trialSpawnerAloneIsNoTrialChambers() {
		FakeWorld stone = new FakeWorld();
		stone.fill(5, -20, 5, 11, -17, 11, "minecraft:cave_air");
		stone.set(8, -20, 8, "minecraft:trial_spawner");
		assertEquals(List.of(), kinds(stone));
		// natural tuff is not tuff bricks
		FakeWorld tuff = new FakeWorld();
		tuff.fill(2, -24, 2, 14, -14, 14, "minecraft:tuff");
		tuff.fill(5, -20, 5, 11, -17, 11, "minecraft:cave_air");
		tuff.set(8, -20, 8, "minecraft:trial_spawner");
		assertEquals(List.of(), kinds(tuff));
	}

	// --- geode -----------------------------------------------------------------------------------------------------

	private static void ball(FakeWorld w, int cx, int cy, int cz, double r, String id) {
		int n = (int) Math.ceil(r);
		for (int y = -n; y <= n; y++) {
			for (int z = -n; z <= n; z++) {
				for (int x = -n; x <= n; x++) {
					if (x * x + y * y + z * z <= r * r) {
						w.set(cx + x, cy + y, cz + z, id);
					}
				}
			}
		}
	}

	@Test
	void geodeIsBuddingAmethystInACalciteShell() {
		FakeWorld w = new FakeWorld();
		ball(w, 8, 0, 8, 6.5, "minecraft:smooth_basalt");
		ball(w, 8, 0, 8, 5.5, "minecraft:calcite");
		ball(w, 8, 0, 8, 4.5, "minecraft:amethyst_block");
		ball(w, 8, 0, 8, 3.5, "minecraft:air");
		for (int y = -5; y <= 5; y++) {
			for (int z = 3; z <= 13; z++) {
				for (int x = 3; x <= 13; x++) {
					if (w.id(x, y, z).equals("minecraft:amethyst_block") && Math.floorMod(x * 3 + y * 5 + z * 7, 6) == 0) {
						w.set(x, y, z, "minecraft:budding_amethyst");
					}
				}
			}
		}
		w.set(8, -3, 8, "minecraft:amethyst_cluster");
		List<Sighting> s = sightings(w);
		assertEquals(List.of(GEODE), kinds(s), "one geode, however many buds");
		assertTrue(s.get(0).underground());
	}

	@Test
	void amethystWithoutCalciteIsNoGeode() {
		FakeWorld w = new FakeWorld();
		w.fill(7, 20, 7, 9, 22, 9, "minecraft:amethyst_block");
		w.set(8, 21, 8, "minecraft:budding_amethyst");
		assertEquals(List.of(), kinds(w));
	}

	// --- trail ruins and ocean ruins -------------------------------------------------------------------------------

	@Test
	void trailRuinsAreSuspiciousGravelAmongTerracotta() {
		FakeWorld w = new FakeWorld(63, "minecraft:dirt");
		w.fill(4, 56, 4, 12, 62, 12, "minecraft:gravel");
		w.fill(4, 56, 4, 8, 58, 4, "minecraft:mud_bricks");
		w.set(5, 57, 6, "minecraft:light_blue_terracotta").set(10, 58, 6, "minecraft:terracotta");
		w.set(6, 56, 10, "minecraft:brown_glazed_terracotta").set(11, 57, 11, "minecraft:red_terracotta");
		w.set(8, 58, 8, "minecraft:suspicious_gravel");
		assertEquals(List.of(TRAIL_RUINS), kinds(w));
	}

	@Test
	void suspiciousGravelInPlainGravelIsNoRuin() {
		FakeWorld w = new FakeWorld(63, "minecraft:dirt");
		w.fill(4, 56, 4, 12, 62, 12, "minecraft:gravel");
		w.set(8, 58, 8, "minecraft:suspicious_gravel");
		assertEquals(List.of(), kinds(w));
	}

	@ParameterizedTest
	@ValueSource(strings = {"minecraft:stone_bricks|minecraft:suspicious_gravel", "minecraft:sandstone|minecraft:suspicious_sand",
		"minecraft:cracked_stone_bricks|minecraft:suspicious_sand"})
	void oceanRuinIsSuspiciousBlocksAmongDrownedMasonry(String spec) {
		String[] p = spec.split("\\|");
		FakeWorld w = ocean(40);
		w.fill(5, 41, 5, 11, 43, 5, p[0]);
		w.fill(5, 41, 11, 11, 42, 11, p[0]);
		w.fill(5, 41, 6, 5, 41, 10, "minecraft:mossy_stone_bricks");
		w.set(8, 40, 8, p[1]);
		assertEquals(List.of(OCEAN_RUIN), kinds(w));
	}

	@Test
	void suspiciousSandInADryWellIsNoRuin() {
		// a desert well: sandstone round one block of water, suspicious sand beside it
		FakeWorld w = new FakeWorld(63, "minecraft:sand");
		w.fill(6, 63, 6, 10, 63, 10, "minecraft:sandstone");
		w.set(8, 63, 8, "minecraft:water");
		w.set(8, 62, 8, "minecraft:suspicious_sand");
		w.fill(7, 64, 7, 9, 64, 9, "minecraft:sandstone_slab");
		w.set(8, 64, 8, "minecraft:air");
		assertEquals(List.of(), kinds(w));
	}

	// --- pillager outpost ------------------------------------------------------------------------------------------

	@Test
	void pillagerOutpostIsAWhiteBannerOnADarkOakAndBirchTower() {
		FakeWorld w = new FakeWorld();
		w.shell(4, 64, 4, 12, 86, 12, "minecraft:birch_planks");
		for (int[] c : new int[][]{{4, 4}, {12, 4}, {4, 12}, {12, 12}}) {
			w.fill(c[0], 64, c[1], c[0], 86, c[1], "minecraft:dark_oak_log");
		}
		w.set(8, 87, 8, "minecraft:white_banner");
		List<Sighting> s = sightings(w);
		// the banner and the counted planks both say outpost: one sighting
		assertEquals(List.of(PILLAGER_OUTPOST), kinds(s));
		assertFalse(s.get(0).underground());
		assertEquals(87, s.get(0).y(), "at the banner");
	}

	@Test
	void whiteBannerOnAnOakHouseIsNoOutpost() {
		FakeWorld w = new FakeWorld();
		w.shell(4, 64, 4, 12, 70, 12, "minecraft:oak_planks");
		w.set(8, 71, 8, "minecraft:white_banner").set(8, 66, 3, "minecraft:white_wall_banner");
		assertEquals(List.of(), kinds(w));
	}

	// --- igloo -----------------------------------------------------------------------------------------------------

	private static FakeWorld snowHut(boolean furnished) {
		FakeWorld w = new FakeWorld(63, "minecraft:snow_block");
		w.shell(5, 64, 5, 11, 68, 11, "minecraft:snow_block");
		w.set(6, 65, 6, "minecraft:furnace");
		w.set(10, 65, 6, "minecraft:crafting_table");
		if (furnished) {
			w.set(5, 66, 8, "minecraft:ice").set(11, 66, 8, "minecraft:ice");
			w.set(8, 65, 8, "minecraft:white_carpet").set(8, 65, 9, "minecraft:white_carpet").set(9, 65, 8, "minecraft:white_carpet");
			bed(w, 9, 65, 9);
		}
		return w;
	}

	@Test
	void iglooIsAFurnaceInASnowDomeWithIceAndCarpet() {
		assertEquals(List.of(IGLOO), kinds(snowHut(true)));
	}

	@Test
	void furnaceInABareSnowHutIsNoIgloo() {
		assertEquals(List.of(), kinds(snowHut(false)));
	}

	// --- stronghold ------------------------------------------------------------------------------------------------

	/** Stone bricks, some mossy and cracked, as a stronghold's are. */
	private static void bricks(FakeWorld w, int x0, int y0, int z0, int x1, int y1, int z1, boolean shell) {
		if (shell) {
			w.shell(x0, y0, z0, x1, y1, z1, "minecraft:stone_bricks");
		} else {
			w.fill(x0, y0, z0, x1, y1, z1, "minecraft:stone_bricks");
		}
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (w.id(x, y, z).equals("minecraft:stone_bricks")) {
						int h = Math.floorMod(x * 7 + y * 13 + z * 5, 10);
						if (h == 0) {
							w.set(x, y, z, "minecraft:mossy_stone_bricks");
						} else if (h == 1) {
							w.set(x, y, z, "minecraft:cracked_stone_bricks");
						}
					}
				}
			}
		}
	}

	@Test
	void strongholdIsTheRingOfPortalFramesInStoneBricks() {
		FakeWorld w = new FakeWorld();
		bricks(w, 0, 18, 0, 15, 26, 15, true);
		w.fill(1, 19, 1, 14, 25, 14, "minecraft:cave_air");
		w.fill(5, 19, 5, 11, 19, 11, "minecraft:stone_bricks");
		w.fill(7, 19, 7, 9, 19, 9, "minecraft:lava");
		for (int i = 7; i <= 9; i++) {
			w.set(i, 20, 6, "minecraft:end_portal_frame").set(i, 20, 10, "minecraft:end_portal_frame");
			w.set(6, 20, i, "minecraft:end_portal_frame").set(10, 20, i, "minecraft:end_portal_frame");
		}
		w.fill(0, 22, 7, 0, 23, 9, "minecraft:iron_bars");
		List<Sighting> s = sightings(w);
		assertEquals(List.of(STRONGHOLD), kinds(s));
		assertTrue(s.get(0).underground());
	}

	@Test
	void strongholdCorridorWithAnInfestedBrick() {
		FakeWorld w = new FakeWorld();
		bricks(w, 0, 20, 6, 15, 24, 10, false);
		w.fill(0, 21, 7, 15, 23, 9, "minecraft:cave_air");
		w.set(4, 22, 6, "minecraft:infested_stone_bricks");
		assertEquals(List.of(STRONGHOLD), kinds(w));
	}

	@Test
	void stoneBrickTunnelIsNoStronghold() {
		FakeWorld w = new FakeWorld();
		bricks(w, 0, 20, 6, 15, 24, 10, false);
		w.fill(0, 21, 7, 15, 23, 9, "minecraft:cave_air");
		assertEquals(List.of(), kinds(w));
	}

	@Test
	void singlePortalFrameInStoneIsNoStronghold() {
		FakeWorld w = new FakeWorld();
		w.fill(6, 20, 6, 10, 23, 10, "minecraft:cave_air");
		w.set(8, 20, 8, "minecraft:end_portal_frame");
		assertEquals(List.of(), kinds(w));
	}

	// --- fortress --------------------------------------------------------------------------------------------------

	@Test
	void fortressIsNetherBricksWithFences() {
		FakeWorld w = new FakeWorld(31, "minecraft:netherrack");
		w.fill(0, 70, 6, 15, 71, 10, "minecraft:nether_bricks");
		w.fill(0, 72, 6, 15, 72, 6, "minecraft:nether_brick_fence");
		w.fill(0, 72, 10, 15, 72, 10, "minecraft:nether_brick_fence");
		assertEquals(List.of(FORTRESS), nether(w));
	}

	@Test
	void blazeSpawnerOnNetherBricksIsAFortress() {
		FakeWorld w = new FakeWorld(31, "minecraft:netherrack");
		w.fill(6, 69, 6, 10, 69, 10, "minecraft:nether_bricks");
		w.set(8, 70, 8, "minecraft:spawner");
		assertEquals(List.of(FORTRESS), nether(w));
	}

	@Test
	void netherBricksAloneAreNoFortress() {
		FakeWorld w = new FakeWorld(31, "minecraft:netherrack");
		w.fill(0, 70, 6, 15, 71, 10, "minecraft:nether_bricks");
		assertEquals(List.of(), nether(w));
	}

	// --- cave mouth ------------------------------------------------------------------------------------------------

	/** A stone hill over x 6..15 up to y 75, and a tunnel two high into it at z 8 for {@code depth} blocks. */
	private static FakeWorld hill(int depth) {
		return hill(depth, 1);
	}

	/** As {@link #hill(int)}, the tunnel {@code width} blocks wide (centred on z 8). */
	private static FakeWorld hill(int depth, int width) {
		FakeWorld w = new FakeWorld(63, "minecraft:stone");
		w.fill(6, 64, 0, 15, 75, 15, "minecraft:stone");
		w.fill(0, 64, 0, 5, 64, 15, "minecraft:short_grass");
		if (depth > 0) {
			w.fill(6, 64, 8 - (width - 1) / 2, 5 + depth, 65, 8 + width / 2, "minecraft:air");
		}
		return w;
	}

	@Test
	void caveMouthIsATunnelIntoAHillside() {
		List<Sighting> s = sightings(hill(9));
		assertEquals(List.of(CAVE_MOUTH), kinds(s), "a passage that runs on past the probe leads somewhere");
		assertEquals(CAVE_MOUTH, s.get(0).kind());
		assertEquals(64, s.get(0).y(), "at the opening, never underground");
		assertFalse(s.get(0).underground());
	}

	@Test
	void aWideOpeningIsACaveMouthEvenIfShort() {
		assertEquals(List.of(CAVE_MOUTH), kinds(hill(7, 3)));
	}

	@Test
	void aNarrowShortTunnelIsNotNotable() {
		assertEquals(List.of(), kinds(hill(7)), "one block wide and ending within the probe: not worth a place");
	}

	@Test
	void overhangIsNoCaveMouth() {
		assertEquals(List.of(), kinds(hill(2)));
		assertEquals(List.of(), kinds(hill(0)));
	}

	@Test
	void houseDoorwayIsNoCaveMouth() {
		FakeWorld w = new FakeWorld();
		// a plank floor in the ground, walls four high, a roof, a doorway two high in the west wall
		w.fill(4, 63, 4, 12, 63, 12, "minecraft:oak_planks");
		w.shell(4, 63, 4, 12, 68, 12, "minecraft:cobblestone");
		w.fill(5, 63, 5, 11, 63, 11, "minecraft:oak_planks");
		w.fill(4, 64, 8, 4, 65, 8, "minecraft:air");
		w.fill(5, 64, 5, 11, 67, 11, "minecraft:air");
		assertEquals(List.of(), kinds(w));
	}
}
