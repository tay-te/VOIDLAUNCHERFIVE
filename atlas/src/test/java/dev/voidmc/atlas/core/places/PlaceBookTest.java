package dev.voidmc.atlas.core.places;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.Discovery;
import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.DiscoveryStore;
import dev.voidmc.atlas.core.detect.Sighting;
import dev.voidmc.atlas.core.detect.StructureKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The place book: how places move on (and never back), how sightings merge, how exact data wins, how natural groups
 * are replaced, and how it all survives a save, a damaged file and an older Atlas's discoveries.
 */
class PlaceBookTest {
	private static final String OW = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	@TempDir
	Path dir;

	private final List<String> warnings = new ArrayList<>();

	private PlaceBook book() {
		return new PlaceBook(this.dir.resolve("places.json"), this.warnings::add);
	}

	private static Sighting surface(StructureKind kind, int x, int y, int z) {
		return new Sighting(kind, x, y, z, false);
	}

	private static Sighting under(StructureKind kind, int x, int y, int z) {
		return new Sighting(kind, x, y, z, true);
	}

	private static Place natural(String kind, int x, int y, int z, DiscoveryState state, long sighted) {
		return new Place(kind + "@" + x + "," + z, kind, "", "NATURAL", Tier.MINOR, state, Source.seen(), x, y, z, OW, false, 0, sighted, 0);
	}

	// --- states ----------------------------------------------------------------------------------------------------

	@Test
	void aSurfaceSightingIsSightedAtOnce() {
		PlaceBook book = this.book();
		Place p = book.sight(OW, surface(StructureKind.VILLAGE, 100, 70, 200), 1000);
		assertEquals("minecraft:village@100,200", p.id());
		assertEquals(DiscoveryState.SIGHTED, p.state());
		assertEquals(1000, p.sighted());
		assertEquals(0, p.discovered());
		assertEquals(Source.Kind.SEEN, p.source().kind());
		assertEquals(Tier.MAJOR, p.tier());
		assertEquals(List.of(p), book.visible(OW));
		assertTrue(book.visible(NETHER).isEmpty());
	}

	@Test
	void anUndergroundSightingIsUnknownUntilThePlayerIsNearInThreeDimensions() {
		PlaceBook book = this.book();
		Place p = book.sight(OW, under(StructureKind.DUNGEON, 0, -20, 0), 1000);
		assertEquals(DiscoveryState.UNKNOWN, p.state());
		assertEquals(0, p.sighted());
		assertTrue(book.visible(OW).isEmpty(), "unknown places are not shown");
		// straight above it, just out of sight: being above a dungeon is not finding it
		double cy = -20 + 0.5;
		assertTrue(book.proximity(OW, 0.5, cy + PlaceBook.UNDERGROUND_SIGHT + 1, 0.5, 256, 2000).isEmpty());
		assertEquals(DiscoveryState.UNKNOWN, book.get(p.id()).state());
		// within sight
		List<Place> changed = book.proximity(OW, 0.5, cy + PlaceBook.UNDERGROUND_SIGHT - 1, 0.5, 256, 3000);
		assertEquals(1, changed.size());
		assertEquals(DiscoveryState.SIGHTED, book.get(p.id()).state());
		assertEquals(3000, book.get(p.id()).sighted());
		assertEquals(1, book.visible(OW).size());
		// between reach and sight: still only sighted
		book.proximity(OW, 0.5, cy, 0.5 + PlaceBook.UNDERGROUND_REACH + 1, 256, 4000);
		assertEquals(DiscoveryState.SIGHTED, book.get(p.id()).state());
		// within reach
		book.proximity(OW, 0.5, cy, 0.5 + PlaceBook.UNDERGROUND_REACH - 1, 256, 5000);
		Place found = book.get(p.id());
		assertEquals(DiscoveryState.DISCOVERED, found.state());
		assertEquals(3000, found.sighted());
		assertEquals(5000, found.discovered());
	}

	@Test
	void aSurfacePlaceIsDiscoveredWithinReachHorizontally() {
		PlaceBook book = this.book();
		Place p = book.sight(OW, surface(StructureKind.DESERT_PYRAMID, 0, 70, 0), 1000);
		// far above, but just out of reach horizontally
		book.proximity(OW, 0.5, 300, 0.5 + PlaceBook.SURFACE_REACH + 1, 256, 2000);
		assertEquals(DiscoveryState.SIGHTED, book.get(p.id()).state());
		// far above, within reach horizontally: height does not matter on the surface
		book.proximity(OW, 0.5, 300, 0.5 + PlaceBook.SURFACE_REACH - 1, 256, 3000);
		assertEquals(DiscoveryState.DISCOVERED, book.get(p.id()).state());
		assertEquals(3000, book.get(p.id()).discovered());
		assertEquals(1000, book.get(p.id()).sighted());
	}

	@Test
	void statesNeverGoBackwards() {
		PlaceBook book = this.book();
		long t = 1000;
		Place v = book.sight(OW, surface(StructureKind.VILLAGE, 100, 70, 100), t);
		book.proximity(OW, 100.5, 70, 100.5, 256, t + 1);
		assertEquals(DiscoveryState.DISCOVERED, book.get(v.id()).state());
		long discovered = book.get(v.id()).discovered();

		// far away
		assertTrue(book.proximity(OW, 5000, 70, 5000, 256, t + 2).isEmpty());
		assertEquals(DiscoveryState.DISCOVERED, book.get(v.id()).state());
		// seen again
		Place again = book.sight(OW, surface(StructureKind.VILLAGE, 104, 70, 100), t + 3);
		assertEquals(v.id(), again.id());
		assertEquals(DiscoveryState.DISCOVERED, again.state());
		assertEquals(discovered, again.discovered());
		// exact data with a lower state: it takes the heuristic place's state over, then keeps it
		int[] box = {60, 60, 60, 160, 90, 160};
		Place exact = book.exact(OW, "minecraft:village_plains", "minecraft:village", box, false, DiscoveryState.SIGHTED, t + 4);
		assertEquals(DiscoveryState.DISCOVERED, exact.state());
		exact = book.exact(OW, "minecraft:village_plains", "minecraft:village", box, false, DiscoveryState.UNKNOWN, t + 5);
		assertEquals(DiscoveryState.DISCOVERED, exact.state());

		// a natural place replaced with SIGHTED
		long g = PlaceBook.regionGroup(0, 0);
		Place peak = natural(Place.PEAK, 300, 150, 300, DiscoveryState.SIGHTED, t);
		book.replaceGroup(OW, g, List.of(peak), t);
		assertNotNull(book.enter(peak.id(), t + 6));
		book.replaceGroup(OW, g, List.of(peak), t + 7);
		assertEquals(DiscoveryState.DISCOVERED, book.get(peak.id()).state());
		assertEquals(t + 6, book.get(peak.id()).discovered());

		// a report with UNKNOWN
		Place hamlet = new Place("hearth:hamlet/1", "hearth:hamlet", "Oakridge", "VILLAGE", Tier.MAJOR, DiscoveryState.DISCOVERED,
			Source.plugin("hearth:settlements"), 900, 70, 900, OW, false, 30, 0, 0);
		book.report(hamlet, t + 8);
		Place reported = book.report(new Place(hamlet.id(), hamlet.kind(), "Oakridge", "VILLAGE", Tier.MAJOR, DiscoveryState.UNKNOWN, hamlet.source(), 900,
			70, 900, OW, false, 30, 0, 0), t + 9);
		assertEquals(DiscoveryState.DISCOVERED, reported.state());
		assertEquals(t + 8, reported.discovered());
	}

	@Test
	void eachForwardMoveIsReportedOnce() {
		PlaceBook book = this.book();
		Place p = book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1000);
		book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1001);
		book.proximity(OW, 0.5, 70, 0.5, 256, 1002);
		book.proximity(OW, 0.5, 70, 0.5, 256, 1003);
		book.sight(OW, surface(StructureKind.VILLAGE, 2, 70, 0), 1004);
		List<Place> changes = book.drainChanges();
		assertEquals(2, changes.size());
		assertEquals(p.id(), changes.get(0).id());
		assertEquals(DiscoveryState.SIGHTED, changes.get(0).state());
		assertEquals(DiscoveryState.DISCOVERED, changes.get(1).state());
		assertTrue(book.drainChanges().isEmpty(), "drained");
		// an unknown place moving nowhere is no change; one moving to sighted is
		Place d = book.sight(OW, under(StructureKind.DUNGEON, 500, -30, 500), 1005);
		assertTrue(book.drainChanges().isEmpty());
		book.proximity(OW, 500.5, -29.5 + PlaceBook.UNDERGROUND_SIGHT - 1, 500.5, 256, 1006);
		List<Place> later = book.drainChanges();
		assertEquals(1, later.size());
		assertEquals(d.id(), later.get(0).id());
	}

	// --- merging ---------------------------------------------------------------------------------------------------

	@Test
	void sightingsOfOneKindCloseTogetherAreOnePlace() {
		PlaceBook book = this.book();
		int r = StructureKind.VILLAGE.mergeRadius;
		Place first = book.sight(OW, surface(StructureKind.VILLAGE, 0, 64, 0), 1000);
		Place second = book.sight(OW, surface(StructureKind.VILLAGE, r - 10, 64, 0), 1001);
		assertEquals(first.id(), second.id());
		assertEquals(1, book.size());
		assertTrue(second.x() > 0 && second.x() < r - 10, "the middle moves towards the second sighting: " + second.x());
		assertEquals(1000, second.sighted(), "the first sighting's time stays");
		// out of the merge radius of the (moved) middle
		Place far = book.sight(OW, surface(StructureKind.VILLAGE, second.x() + r + 10, 64, 0), 1002);
		assertNotEquals(first.id(), far.id());
		assertEquals(2, book.size());
		// another kind on the same spot never merges
		Place portal = book.sight(OW, surface(StructureKind.RUINED_PORTAL, second.x(), 64, 0), 1003);
		assertNotEquals(first.id(), portal.id());
		assertEquals(StructureKind.RUINED_PORTAL.id, portal.kind());
		assertEquals(3, book.size());
	}

	@Test
	void aSurfaceSightingOfAnUndergroundPlaceBringsItToTheSurface() {
		PlaceBook book = this.book();
		Place mine = book.sight(OW, under(StructureKind.MINESHAFT, 0, -30, 0), 1000);
		assertTrue(mine.underground());
		assertEquals(DiscoveryState.UNKNOWN, mine.state());
		Place seen = book.sight(OW, surface(StructureKind.MINESHAFT, 10, 60, 0), 2000);
		assertEquals(mine.id(), seen.id());
		assertFalse(seen.underground());
		assertEquals(DiscoveryState.SIGHTED, seen.state());
		assertEquals(2000, seen.sighted());
		assertEquals(1, book.visible(OW).size());
		// and a later underground sighting does not send it back under
		assertFalse(book.sight(OW, under(StructureKind.MINESHAFT, 5, -30, 0), 3000).underground());
	}

	// --- exact data ------------------------------------------------------------------------------------------------

	@Test
	void exactDataFoldsInTheSightingsItExplainsAndWins() {
		PlaceBook book = this.book();
		Place village = book.sight(OW, surface(StructureKind.VILLAGE, 100, 70, 100), 1000);
		book.proximity(OW, 100.5, 70, 100.5, 256, 2000);
		assertEquals(DiscoveryState.DISCOVERED, book.get(village.id()).state());
		// same family, outside the bounds but within the merge radius of its middle (110, 110)
		Place near = book.sight(OW, surface(StructureKind.VILLAGE, 200, 70, 110), 3000);
		// another family inside the bounds: the heuristics were wrong
		Place portal = book.sight(OW, surface(StructureKind.RUINED_PORTAL, 110, 70, 110), 3000);
		// same family, far away: another village
		Place other = book.sight(OW, surface(StructureKind.VILLAGE, 400, 70, 110), 3000);
		assertEquals(4, book.size());

		int[] box = {60, 60, 60, 160, 90, 160};
		Place exact = book.exact(OW, "minecraft:village_plains", "minecraft:village", box, false, DiscoveryState.UNKNOWN, 4000);
		assertEquals("minecraft:village_plains@110,110", exact.id());
		assertEquals(Source.Kind.EXACT, exact.source().kind());
		assertEquals(DiscoveryState.DISCOVERED, exact.state(), "the heuristic place's state carries over");
		assertEquals(1000, exact.sighted(), "the earliest sighting");
		assertEquals(2000, exact.discovered());
		assertArrayEquals(new int[]{2, 1}, book.precision());
		assertNull(book.get(village.id()));
		assertNull(book.get(near.id()));
		assertNull(book.get(portal.id()));
		assertNotNull(book.get(other.id()));
		assertEquals(2, book.size());

		// later sightings inside its bounds are part of it (or mistakes)
		assertNull(book.sight(OW, surface(StructureKind.VILLAGE, 120, 70, 120), 5000));
		assertNull(book.sight(OW, under(StructureKind.DUNGEON, 100, 55, 100), 5000));
		// but not in another dimension
		assertNotNull(book.sight(NETHER, surface(StructureKind.VILLAGE, 120, 70, 120), 5000));
		assertEquals(3, book.size());
	}

	@Test
	void exactDataWithoutSightingsStartsAtItsOwnState() {
		PlaceBook book = this.book();
		Place p = book.exact(OW, "minecraft:ancient_city", "minecraft:ancient_city", new int[]{-100, -50, -100, 100, -20, 100}, true,
			DiscoveryState.UNKNOWN, 1000);
		assertEquals(DiscoveryState.UNKNOWN, p.state());
		assertTrue(p.underground());
		assertTrue(book.visible(OW).isEmpty());
		assertArrayEquals(new int[]{0, 0}, book.precision());
		// distance is to its bounds: inside the box is within reach
		book.proximity(OW, 90, -30, 90, 256, 2000);
		assertEquals(DiscoveryState.DISCOVERED, book.get(p.id()).state());
	}

	// --- natural groups --------------------------------------------------------------------------------------------

	@Test
	void replacingAGroupKeepsWhatIsStillThere() {
		PlaceBook book = this.book();
		long g = PlaceBook.regionGroup(0, 0);
		Place a = natural(Place.PEAK, 100, 150, 100, DiscoveryState.SIGHTED, 1000);
		Place b = natural(Place.WATERFALL, 300, 90, 300, DiscoveryState.SIGHTED, 1000);
		book.replaceGroup(OW, g, List.of(a, b), 1000);
		assertEquals(2, book.size());
		book.enter(a.id(), 2000);
		book.rename(b.id(), "Silverfall");

		// analysed again: a is still there by id (a little higher), b moved a few blocks, c is new
		Place a2 = natural(Place.PEAK, 100, 152, 100, DiscoveryState.SIGHTED, 3000);
		Place b2 = natural(Place.WATERFALL, 310, 90, 300, DiscoveryState.SIGHTED, 3000);
		Place c = natural(Place.RAVINE, 450, 40, 450, DiscoveryState.SIGHTED, 3000);
		book.replaceGroup(OW, g, List.of(a2, b2, c), 3000);
		assertEquals(3, book.size());
		Place ka = book.get(a.id());
		assertEquals(DiscoveryState.DISCOVERED, ka.state());
		assertEquals(152, ka.y(), "the new analysis's figures");
		assertEquals(1000, ka.sighted());
		assertEquals(2000, ka.discovered());
		assertNull(book.get(b.id()), "the old id is gone");
		Place kb = book.get(b2.id());
		assertEquals("Silverfall", kb.name(), "the nearest of the same kind keeps its name");
		assertEquals(1000, kb.sighted());
		assertEquals(3000, book.get(c.id()).sighted());

		// gone from the land: dropped
		book.replaceGroup(OW, g, List.of(a2), 4000);
		assertEquals(1, book.size());
		assertNotNull(book.get(a.id()));
	}

	@Test
	void aPlaceStillThereByIdKeepsItsStateWhateverTheOrder() {
		PlaceBook book = this.book();
		long g = PlaceBook.regionGroup(0, 0);
		Place a = natural(Place.WATERFALL, 100, 90, 100, DiscoveryState.SIGHTED, 1000);
		book.replaceGroup(OW, g, List.of(a), 1000);
		book.enter(a.id(), 2000);
		// a new fall close by, listed before the one that is still there
		Place fresh = natural(Place.WATERFALL, 120, 90, 100, DiscoveryState.SIGHTED, 3000);
		book.replaceGroup(OW, g, List.of(fresh, a), 3000);
		assertEquals(2, book.size());
		assertEquals(DiscoveryState.DISCOVERED, book.get(a.id()).state());
		assertEquals(2000, book.get(a.id()).discovered());
		assertEquals(DiscoveryState.SIGHTED, book.get(fresh.id()).state(), "the new one does not take the old one's state");
	}

	@Test
	void groupsDoNotAffectEachOther() {
		PlaceBook book = this.book();
		long g1 = PlaceBook.regionGroup(0, 0);
		long g2 = PlaceBook.regionGroup(1, 0);
		Place a = natural(Place.PEAK, 100, 150, 100, DiscoveryState.SIGHTED, 1000);
		Place q = natural(Place.PEAK, 600, 150, 100, DiscoveryState.SIGHTED, 1000);
		book.replaceGroup(OW, g1, List.of(a), 1000);
		book.replaceGroup(OW, g2, List.of(q), 1000);
		book.enter(a.id(), 2000);
		// a peak of the other group right next to a: not matched with it
		Place q2 = natural(Place.PEAK, 105, 150, 100, DiscoveryState.SIGHTED, 3000);
		book.replaceGroup(OW, g2, List.of(q2), 3000);
		assertNull(book.get(q.id()), "q is gone from its group");
		assertEquals(DiscoveryState.DISCOVERED, book.get(a.id()).state());
		assertEquals(DiscoveryState.SIGHTED, book.get(q2.id()).state());
		assertEquals(2, book.size());
		// emptying one group leaves the other
		book.replaceGroup(OW, g1, List.of(), 4000);
		assertNull(book.get(a.id()));
		assertNotNull(book.get(q2.id()));
		// and another dimension's group of the same key is its own
		book.replaceGroup(NETHER, g2, List.of(), 4000);
		assertNotNull(book.get(q2.id()));
	}

	@Test
	void regionGroupsAreDistinctAndNeverTheSpecialGroups() {
		Set<Long> seen = new HashSet<>();
		Set<Long> regions = new HashSet<>();
		int[] far = {-58_594, -1_000, 0, 1_000, 58_593};
		for (int rx = -40; rx <= 40; rx++) {
			for (int rz = -40; rz <= 40; rz++) {
				regions.add(Coords.pack(rx, rz));
			}
		}
		for (int a : far) {
			for (int b : far) {
				regions.add(Coords.pack(a, b));
			}
		}
		for (long r : regions) {
			seen.add(PlaceBook.regionGroup(Coords.unpackX(r), Coords.unpackZ(r)));
		}
		assertEquals(regions.size(), seen.size(), "no two regions share a group");
		assertFalse(seen.contains(0L));
		assertFalse(seen.contains(PlaceBook.AREAS));
		assertFalse(seen.contains(PlaceBook.REPORTS));
	}

	// --- regions, reports, edits -----------------------------------------------------------------------------------

	@Test
	void aRegionIsDiscoveredByWalkingIntoIt() {
		PlaceBook book = this.book();
		Place meadow = new Place("atlas:region@256,256", Place.REGION, "Larkspur Meadow", "REGION", Tier.MAJOR, DiscoveryState.SIGHTED, Source.seen(), 256,
			70, 256, OW, false, 200, 1000, 0);
		book.replaceGroup(OW, PlaceBook.AREAS, List.of(meadow), 1000);
		// standing on its label is not discovering it: regions move on by entering them
		assertTrue(book.proximity(OW, 256.5, 70, 256.5, 256, 2000).isEmpty());
		Place entered = book.enter(meadow.id(), 3000);
		assertNotNull(entered);
		assertEquals(DiscoveryState.DISCOVERED, entered.state());
		assertEquals(3000, entered.discovered());
		assertNull(book.enter(meadow.id(), 4000), "already discovered");
		assertNull(book.enter("atlas:region@nowhere", 4000));
	}

	@Test
	void aReportWithoutAStateIsJudgedByDistance() {
		PlaceBook book = this.book();
		Place hamlet = new Place("hearth:hamlet/7", "hearth:hamlet", "Oakridge", "VILLAGE", Tier.MAJOR, DiscoveryState.UNKNOWN,
			Source.plugin("hearth:settlements"), 1000, 70, 1000, OW, false, 0, 0, 0);
		Place p = book.report(hamlet, 1000);
		assertEquals(DiscoveryState.UNKNOWN, p.state());
		assertTrue(book.visible(OW).isEmpty());
		int view = 128;
		book.proximity(OW, 1000.5 + view + 10, 70, 1000.5, view, 2000);
		assertEquals(DiscoveryState.UNKNOWN, book.get(p.id()).state(), "out of view");
		book.proximity(OW, 1000.5 + view - 10, 70, 1000.5, view, 3000);
		assertEquals(DiscoveryState.SIGHTED, book.get(p.id()).state(), "in view");
		assertEquals(3000, book.get(p.id()).sighted());
		book.proximity(OW, 1000.5 + PlaceBook.SURFACE_REACH - 1, 70, 1000.5, view, 4000);
		assertEquals(DiscoveryState.DISCOVERED, book.get(p.id()).state(), "within reach");
		// a report with a state starts there
		Place told = book.report(new Place("void:keep", "void:keep", "The Keep", "LANDMARK", Tier.MAJOR, DiscoveryState.SIGHTED, Source.voidServer(), 0,
			80, 0, OW, false, 0, 0, 0), 5000);
		assertEquals(DiscoveryState.SIGHTED, told.state());
		assertEquals(5000, told.sighted());
	}

	@Test
	void forgetAndRename() {
		PlaceBook book = this.book();
		Place p = book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1000);
		long v = book.version();
		book.rename(p.id(), "Brackenford");
		assertEquals("Brackenford", book.get(p.id()).name());
		assertTrue(book.version() > v);
		v = book.version();
		book.rename(p.id(), "Brackenford");
		assertEquals(v, book.version(), "the same name is no change");
		book.rename("nothing@0,0", "Nowhere");
		assertTrue(book.forget(p.id()));
		assertFalse(book.forget(p.id()));
		assertNull(book.get(p.id()));
		assertEquals(0, book.size());
	}

	// --- files -----------------------------------------------------------------------------------------------------

	@Test
	void everythingSurvivesASaveAndLoad() throws IOException {
		PlaceBook book = this.book();
		book.load();
		// a sighting seen twice (weight 2, middle at 30)
		Place v = book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1000);
		book.sight(OW, surface(StructureKind.VILLAGE, 60, 70, 0), 1001);
		assertEquals(30, book.get(v.id()).x());
		// an exact structure with bounds
		int[] box = {1000, 40, 1000, 1040, 60, 1040};
		Place temple = book.exact(OW, "expanse:karst_pagoda", "expanse:karst_pagoda", box, false, DiscoveryState.SIGHTED, 1002);
		// an underground one in another dimension
		Place fort = book.exact(NETHER, "minecraft:fortress", "minecraft:fortress", new int[]{0, 40, 0, 100, 80, 100}, true, DiscoveryState.DISCOVERED,
			1003);
		assertTrue(fort.id().endsWith("@" + NETHER));
		// a natural place in a group
		long g = PlaceBook.regionGroup(-3, 7);
		Place peak = natural(Place.PEAK, -1400, 190, 3700, DiscoveryState.SIGHTED, 1004);
		book.replaceGroup(OW, g, List.of(peak), 1004);
		book.rename(peak.id(), "Greytooth");
		// a report from another mod
		Place hamlet = book.report(new Place("hearth:hamlet/1", "hearth:hamlet", "Oakridge", "VILLAGE", Tier.MAJOR, DiscoveryState.DISCOVERED,
			Source.plugin("hearth:settlements"), 900, 70, -900, OW, false, 40, 0, 0), 1005);
		// an unknown underground sighting
		Place dungeon = book.sight(OW, under(StructureKind.DUNGEON, -50, -20, -50), 1006);
		assertTrue(book.dirty());
		book.save();
		assertFalse(book.dirty());
		List<Place> before = book.all();

		PlaceBook back = this.book();
		back.load();
		assertFalse(back.dirty());
		assertEquals(before, back.all(), "every field of every place");
		assertEquals(Source.plugin("hearth:settlements"), back.get(hamlet.id()).source());
		assertEquals(Tier.MAJOR, back.get(hamlet.id()).tier());
		assertEquals(DiscoveryState.UNKNOWN, back.get(dungeon.id()).state());
		assertEquals("Greytooth", back.get(peak.id()).name());
		assertTrue(this.warnings.isEmpty());

		// the box: a sighting inside it is still absorbed
		assertNull(back.sight(OW, surface(StructureKind.VILLAGE, 1020, 50, 1020), 2000));
		assertEquals(temple, back.get(temple.id()));
		// the weight: a third sighting moves the middle a third of the way
		assertEquals(50, back.sight(OW, surface(StructureKind.VILLAGE, 90, 70, 0), 2001).x());
		// the group: another group leaves the peak alone, its own drops it
		back.replaceGroup(OW, PlaceBook.regionGroup(-3, 8), List.of(), 2002);
		assertNotNull(back.get(peak.id()));
		back.replaceGroup(OW, g, List.of(), 2003);
		assertNull(back.get(peak.id()));
	}

	@Test
	void aNewUndergroundSightingMakesTheBookDirty() throws IOException {
		PlaceBook book = this.book();
		book.load();
		book.save();
		assertFalse(book.dirty());
		book.sight(OW, under(StructureKind.DUNGEON, 0, -20, 0), 1000);
		assertTrue(book.dirty(), "an unknown place is still worth saving");
	}

	@Test
	void aCorruptFileIsKeptAsideAndTheBookStartsEmpty() throws IOException {
		Path file = this.dir.resolve("places.json");
		Files.writeString(file, "{\"places\": [ {\"id\": \"a\", \"kind\": ", StandardCharsets.UTF_8);
		PlaceBook book = this.book();
		book.load();
		assertEquals(0, book.size());
		assertEquals(1, this.warnings.size());
		assertTrue(Files.exists(this.dir.resolve("places.json.corrupt")));
		assertFalse(Files.exists(file));
		assertTrue(book.dirty(), "so the next save writes a good file");
		book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1000);
		book.save();
		PlaceBook back = this.book();
		back.load();
		assertEquals(1, back.size());
	}

	@Test
	void aBrokenEntryIsCorruptToo() throws IOException {
		Path file = this.dir.resolve("places.json");
		Files.writeString(file, "{\"places\": [ {\"id\": \"a\"} ]}", StandardCharsets.UTF_8);
		PlaceBook book = this.book();
		book.load();
		assertEquals(0, book.size());
		assertTrue(Files.exists(this.dir.resolve("places.json.corrupt")));
	}

	@Test
	void anOlderAtlasDiscoveriesAreMigrated() throws IOException {
		DiscoveryStore old = new DiscoveryStore(this.dir.resolve("discoveries.json"), s -> {
		});
		Discovery village = new Discovery("minecraft:village_plains", "Plains village", "VILLAGE", 100, 70, -200, OW, 12_345L, false);
		Discovery city = new Discovery("minecraft:ancient_city", "Ancient city", "LANDMARK", -300, -40, 800, OW, 23_456L, true);
		Discovery fort = new Discovery("minecraft:fortress", "Nether fortress", "DUNGEON", 40, 70, -160, NETHER, 34_567L, false);
		old.add(village);
		old.add(city);
		old.add(fort);
		old.save();

		PlaceBook book = this.book();
		book.load();
		assertEquals(3, book.size());
		assertTrue(book.dirty(), "migrated, so saved as places.json");
		for (Discovery d : List.of(village, city)) {
			Place p = book.get(d.id());
			assertNotNull(p, d.id());
			assertEquals(DiscoveryState.DISCOVERED, p.state());
			assertEquals(Source.Kind.EXACT, p.source().kind());
			assertEquals(d.structure(), p.kind());
			assertEquals(d.name(), p.name());
			assertEquals(d.category(), p.category());
			assertEquals(d.discovered(), p.sighted());
			assertEquals(d.discovered(), p.discovered());
			assertEquals(d.underground(), p.underground());
			assertEquals(d.y(), p.y());
		}
		assertEquals(Tier.MAJOR, book.get(village.id()).tier());
		// another dimension's ids carry it, as exact data's do
		Place f = book.get(fort.id() + "@" + NETHER);
		assertNotNull(f);
		assertEquals(NETHER, f.dimension());
		assertEquals(3, book.discoveries().size());

		// exact data for the same structure later lands on the migrated place
		Place exact = book.exact(OW, "minecraft:village_plains", "minecraft:village", new int[]{90, 60, -210, 110, 80, -190}, false,
			DiscoveryState.UNKNOWN, 50_000);
		assertEquals(village.id(), exact.id());
		assertEquals(3, book.size());
		assertEquals(DiscoveryState.DISCOVERED, exact.state());
		assertEquals(12_345L, exact.discovered());

		book.save();
		assertTrue(Files.exists(this.dir.resolve("places.json")));
		PlaceBook back = this.book();
		back.load();
		assertFalse(back.dirty());
		assertEquals(book.all(), back.all());
		// the exact place's bounds were saved too
		assertNull(back.sight(OW, surface(StructureKind.VILLAGE, 100, 70, -200), 60_000));
	}

	@Test
	void anEmptyFolderLoadsAnEmptyBook() {
		PlaceBook book = this.book();
		book.load();
		assertEquals(0, book.size());
		assertTrue(this.warnings.isEmpty());
	}

	// --- reading ---------------------------------------------------------------------------------------------------

	@Test
	void visibleIsASnapshotMadeAgainOnlyAfterAChange() {
		PlaceBook book = this.book();
		book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1000);
		List<Place> a = book.visible(OW);
		assertSame(a, book.visible(OW));
		assertThrows(UnsupportedOperationException.class, () -> a.add(a.get(0)));
		// nothing moved on
		book.proximity(OW, 5000, 70, 5000, 256, 2000);
		assertSame(a, book.visible(OW));
		book.sight(OW, surface(StructureKind.SHIPWRECK, 1000, 62, 0), 3000);
		List<Place> b = book.visible(OW);
		assertNotSame(a, b);
		assertEquals(2, b.size());
		assertEquals(1, a.size(), "an old snapshot does not change");
		assertSame(b, book.visible(OW));
	}

	@Test
	void discoveriesAreTheDiscoveredStructuresOnly() {
		PlaceBook book = this.book();
		Place v = book.sight(OW, surface(StructureKind.VILLAGE, 0, 70, 0), 1000);
		book.proximity(OW, 0.5, 70, 0.5, 256, 2000);
		book.sight(OW, surface(StructureKind.RUINED_PORTAL, 1000, 70, 1000), 3000);
		Place cave = book.sight(OW, surface(StructureKind.CAVE_MOUTH, 5, 66, 5), 3000);
		book.proximity(OW, 5.5, 66, 5.5, 256, 3500);
		assertEquals(DiscoveryState.DISCOVERED, book.get(cave.id()).state());
		Place peak = natural(Place.PEAK, 20, 150, 20, DiscoveryState.DISCOVERED, 4000);
		book.replaceGroup(OW, PlaceBook.regionGroup(0, 0), List.of(peak), 4000);
		assertEquals(DiscoveryState.DISCOVERED, book.get(peak.id()).state());

		List<Discovery> found = book.discoveries();
		assertEquals(1, found.size());
		Discovery d = found.get(0);
		Place p = book.get(v.id());
		assertEquals(new Discovery(p.kind(), p.name(), p.category(), p.x(), p.y(), p.z(), OW, 2000, false), d);
		assertEquals("minecraft:village", d.structure());
	}

	// --- names from other mods -------------------------------------------------------------------------------------

	@Test
	void aNamerNamesStructuresByIdAndCaveMouthsAsNatural() {
		PlaceBook book = this.book();
		List<String> asked = new ArrayList<>();
		book.setNamer((dimension, kind, structureId, x, z) -> {
			asked.add(kind + " " + structureId);
			return switch (kind) {
				case Place.STRUCTURE -> structureId.equals("minecraft:village") ? new PlaceBook.Named("Hollin", Source.plugin("expanse:names")) : null;
				case Place.CAVE_MOUTH -> new PlaceBook.Named("Black Hole", Source.plugin("expanse:names"));
				default -> null;
			};
		});
		Place village = book.sight(OW, surface(StructureKind.VILLAGE, 100, 70, 200), 1000);
		assertEquals("Hollin", village.name());
		// a structure keeps "seen by you" (so the game's data can still fold it in); only its name is the mod's
		assertEquals(Source.Kind.SEEN, village.source().kind());
		Place dungeon = book.sight(OW, under(StructureKind.DUNGEON, 400, 20, 400), 1000);
		assertEquals("Dungeon", dungeon.name(), "no name from the mod: Atlas's own");
		Place mouth = book.sight(OW, surface(StructureKind.CAVE_MOUTH, 900, 70, 900), 1000);
		assertEquals("Black Hole", mouth.name());
		assertEquals(Source.plugin("expanse:names"), mouth.source());
		// the same village seen again is not named again
		book.sight(OW, surface(StructureKind.VILLAGE, 104, 70, 204), 1001);
		assertEquals(List.of("atlas:structure minecraft:village", "atlas:structure atlas:dungeon", "atlas:cave_mouth null"), asked);
		int[] box = {80, 60, 180, 120, 80, 220};
		Place exact = book.exact(OW, "minecraft:village_plains", "minecraft:village", box, false, DiscoveryState.SIGHTED, 1002);
		assertEquals(dev.voidmc.atlas.core.StructureNames.name("minecraft:village_plains"), exact.name());
		assertEquals("atlas:structure minecraft:village_plains", asked.get(asked.size() - 1));
	}

	@Test
	void aNamerThatThrowsLeavesAtlassName() {
		PlaceBook book = this.book();
		book.setNamer((dimension, kind, structureId, x, z) -> {
			throw new IllegalStateException("broken");
		});
		assertEquals("Village", book.sight(OW, surface(StructureKind.VILLAGE, 100, 70, 200), 1000).name());
	}

	@Test
	void aNaturalPlaceTakesTheNewNameWhenAnotherNamerHasIt() {
		PlaceBook book = this.book();
		Place plain = new Place("atlas:region@0,0", Place.REGION, "Forest", "REGION", Tier.MAJOR, DiscoveryState.SIGHTED, Source.seen(), 0, 64, 0, OW,
			false, 100, 1000, 0);
		book.replaceGroup(OW, PlaceBook.AREAS, List.of(plain), 1000);
		Place again = new Place("atlas:region@0,0", Place.REGION, "Forest 2", "REGION", Tier.MAJOR, DiscoveryState.SIGHTED, Source.seen(), 0, 64, 0, OW,
			false, 120, 1001, 0);
		book.replaceGroup(OW, PlaceBook.AREAS, List.of(again), 1001);
		assertEquals("Forest", book.get("atlas:region@0,0").name(), "a name once given stays");
		Place expanse = new Place("atlas:region@0,0", Place.REGION, "Larkspur Meadow", "REGION", Tier.MAJOR, DiscoveryState.SIGHTED,
			Source.plugin("expanse:names"), 0, 64, 0, OW, false, 120, 1002, 0);
		book.replaceGroup(OW, PlaceBook.AREAS, List.of(expanse), 1002);
		assertEquals("Larkspur Meadow", book.get("atlas:region@0,0").name());
		assertEquals("EXPANSE", book.get("atlas:region@0,0").source().label());
	}

	// --- claims: another mod's towns --------------------------------------------------------------------------------

	@Test
	void aClaimKeepsVillageSightingsOutAndForgetsThoseMade() {
		PlaceBook book = this.book();
		Place before = book.sight(OW, surface(StructureKind.VILLAGE, 100, 70, 100), 1000);
		Place outside = book.sight(OW, surface(StructureKind.VILLAGE, 900, 70, 900), 1000);
		Place dungeon = book.sight(OW, under(StructureKind.DUNGEON, 110, 20, 110), 1000);
		book.claim("hearth:kestrel_ford", OW, 120, 120, 96);
		assertNull(book.get(before.id()), "the village sighted in the town is forgotten");
		assertNotNull(book.get(outside.id()), "one outside the claim stays");
		assertNotNull(book.get(dungeon.id()), "only villages give way to a town");
		assertNull(book.sight(OW, surface(StructureKind.VILLAGE, 130, 70, 140), 1001), "no new village inside it");
		book.unclaim("hearth:kestrel_ford");
		assertNotNull(book.sight(OW, surface(StructureKind.VILLAGE, 130, 70, 140), 1002), "after the claim goes, sightings count again");
	}
}
