package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** JourneyMap's per-waypoint JSON (5.x), the guessed single-file layout, damaged files, and launcher instance folders. */
class JourneyMapImportTest {
	private static Path resource(String path) {
		try {
			return Path.of(JourneyMapImportTest.class.getResource(path).toURI());
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ImportedWaypoint named(List<ImportedWaypoint> list, String name) {
		return list.stream().filter(w -> w.name().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("no " + name + ": " + list));
	}

	@Test
	void scanFindsEveryReadableWaypoint() {
		List<String> warnings = new ArrayList<>();
		List<ImportedWaypoint> all = JourneyMapImport.scan(resource("/import/gamedir"), warnings::add);
		Map<String, List<ImportedWaypoint>> worlds = ImportedWaypoint.byWorld(all);
		assertEquals(List.of("mc~example~net", "Old Save", "play.other.net"), List.copyOf(worlds.keySet()));
		assertEquals(2, worlds.get("mc~example~net").size());
		assertEquals(2, worlds.get("Old Save").size());
		assertEquals(2, worlds.get("play.other.net").size());
		assertTrue(all.stream().allMatch(w -> w.source() == ImportedWaypoint.Source.JOURNEYMAP));
		// the broken JSON, the garbage file and the waypoint with no position are reported, not fatal
		assertTrue(warnings.size() >= 3, warnings::toString);
	}

	@Test
	void version5Fields() {
		List<ImportedWaypoint> all = JourneyMapImport.scan(resource("/import/gamedir"));

		ImportedWaypoint home = named(all, "Home");
		assertEquals("mc~example~net", home.world());
		assertEquals(100, home.x());
		assertEquals(64, home.y());
		assertEquals(-200, home.z());
		assertEquals(DimensionIds.OVERWORLD, home.dimension());
		assertEquals(AtlasColours.nearest(0xFF0000), home.colour());
		assertFalse(home.death());
		assertFalse(home.disabled());

		ImportedWaypoint death = named(all, "Death 2026-10-01 21:14");
		assertTrue(death.death());
		assertEquals(DimensionIds.NETHER, death.dimension());
		assertEquals(AtlasColours.STONE, death.colour());
	}

	@Test
	void olderNumericDimensionsAndDisabled() {
		List<ImportedWaypoint> all = JourneyMapImport.scan(resource("/import/gamedir"));
		ImportedWaypoint lake = named(all, "Lake");
		assertEquals(DimensionIds.OVERWORLD, lake.dimension(), "the first listed dimension is the one its position is in");
		assertTrue(lake.disabled());
		assertEquals(AtlasColours.nearest(0x55FF55), lake.colour());

		ImportedWaypoint fortress = named(all, "Fortress");
		assertEquals(DimensionIds.NETHER, fortress.dimension());
		assertEquals(-40, fortress.x());
	}

	@Test
	void guessedSingleFileLayout() {
		List<ImportedWaypoint> all = JourneyMapImport.scan(resource("/import/gamedir"));
		ImportedWaypoint market = named(all, "Market");
		assertEquals(300, market.x());
		assertEquals(72, market.y());
		assertEquals(DimensionIds.OVERWORLD, market.dimension());
		assertEquals(AtlasColours.nearest(0x5555FF), market.colour());

		ImportedWaypoint bastion = named(all, "Bastion");
		assertEquals(DimensionIds.NETHER, bastion.dimension());
		assertEquals(AtlasColours.nearest(0xFF5555), bastion.colour());
	}

	@Test
	void instanceFolderIsFollowedIntoDotMinecraft() {
		List<ImportedWaypoint> all = JourneyMapImport.scan(resource("/import/prism/instances/Survival"));
		assertEquals(1, all.size());
		assertEquals("Tower", all.get(0).name());
		assertEquals("Inst World", all.get(0).world());
	}

	@Test
	void parse() {
		List<String> warnings = new ArrayList<>();
		assertTrue(JourneyMapImport.parse("{", "W", "test", warnings::add).isEmpty());
		assertTrue(JourneyMapImport.parse("", "W", "test", warnings::add).isEmpty());
		assertTrue(JourneyMapImport.parse("42", "W", "test", warnings::add).isEmpty());
		assertTrue(JourneyMapImport.parse("{\"name\":\"x\"}", "W", "test", warnings::add).isEmpty());

		List<ImportedWaypoint> arr = JourneyMapImport.parse(
			"[{\"name\":\"A\",\"x\":1,\"z\":2},{\"name\":\"B\",\"x\":\"3\",\"y\":4,\"z\":5,\"dimension\":-1},{\"x\":99999999,\"z\":0}]",
			"W", "test", warnings::add);
		assertEquals(2, arr.size());
		assertNull(arr.get(0).y());
		assertEquals(DimensionIds.OVERWORLD, arr.get(0).dimension());
		assertEquals(AtlasColours.INK, arr.get(0).colour());
		assertEquals(3, arr.get(1).x());
		assertEquals(DimensionIds.NETHER, arr.get(1).dimension());

		ImportedWaypoint dead = JourneyMapImport.parse("{\"name\":\"D\",\"x\":1.7,\"y\":64,\"z\":-1.2,\"type\":\"death\",\"enable\":\"false\"}",
			"W", "test", warnings::add).get(0);
		assertTrue(dead.death());
		assertTrue(dead.disabled());
		assertEquals(1, dead.x());
		assertEquals(-2, dead.z());
	}

	@Test
	void missingPlacesGiveNothing(@TempDir Path tmp) {
		assertTrue(JourneyMapImport.scan(tmp).isEmpty());
		assertTrue(JourneyMapImport.scan(tmp.resolve("nope")).isEmpty());
		assertTrue(JourneyMapImport.scan(null).isEmpty());
	}

	@Test
	void bothImportersReadTheSameGameDirectory() {
		Path dir = resource("/import/gamedir");
		assertFalse(XaeroImport.scan(dir).isEmpty());
		assertFalse(JourneyMapImport.scan(dir).isEmpty());
	}
}
