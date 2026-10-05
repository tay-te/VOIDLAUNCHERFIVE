package dev.voidmc.atlas.core.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.Waypoint;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Xaero's Minimap files, read from a fake game directory with old and new layouts, damaged lines and a damaged file. */
class XaeroImportTest {
	static Path gameDir() {
		try {
			return Path.of(XaeroImportTest.class.getResource("/import/gamedir").toURI());
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ImportedWaypoint named(List<ImportedWaypoint> list, String world, String name) {
		return list.stream().filter(w -> w.world().equals(world) && w.name().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("no " + name + " in " + world + ": " + list));
	}

	@Test
	void scanFindsEveryReadableWaypoint() {
		List<String> warnings = new ArrayList<>();
		List<ImportedWaypoint> all = XaeroImport.scan(gameDir(), warnings::add);
		Map<String, List<ImportedWaypoint>> worlds = ImportedWaypoint.byWorld(all);
		assertEquals(List.of("Multiplayer_mc.example.net", "Multiplayer_old.server.org", "My World"), List.copyOf(worlds.keySet()));

		List<String> mc = worlds.get("Multiplayer_mc.example.net").stream().map(ImportedWaypoint::name).sorted().toList();
		// Home appears in both the new and old folders but is imported once; the backup folder is not read
		assertEquals(List.of("Base:North", "Death", "Home", "Old farm", "Portal hub", "Survivor"), mc);
		assertEquals(2, worlds.get("Multiplayer_old.server.org").size());
		assertEquals(1, worlds.get("My World").size());
		assertTrue(all.stream().allMatch(w -> w.source() == ImportedWaypoint.Source.XAERO));
		assertFalse(warnings.isEmpty(), "skipped lines and the unknown dimension are reported");
	}

	@Test
	void scanReturnsWorldsTogether() {
		List<ImportedWaypoint> all = XaeroImport.scan(gameDir());
		List<String> order = new ArrayList<>();
		for (ImportedWaypoint w : all) {
			if (order.isEmpty() || !order.get(order.size() - 1).equals(w.world())) {
				order.add(w.world());
			}
		}
		assertEquals(order.stream().distinct().toList(), order, "each world's waypoints are contiguous");
	}

	@Test
	void fieldsAreRead() {
		List<ImportedWaypoint> all = XaeroImport.scan(gameDir());
		String mc = "Multiplayer_mc.example.net";

		ImportedWaypoint home = named(all, mc, "Home");
		assertEquals(100, home.x());
		assertEquals(64, home.y());
		assertEquals(-200, home.z());
		assertEquals(DimensionIds.OVERWORLD, home.dimension());
		assertEquals(AtlasColours.fromChatColour(6), home.colour());
		assertFalse(home.death());
		assertFalse(home.disabled());

		ImportedWaypoint base = named(all, mc, "Base:North");
		assertNull(base.y(), "~ is an unknown height");
		assertEquals(-1500, base.x());
		assertEquals(3000, base.z());

		ImportedWaypoint death = named(all, mc, "Death");
		assertTrue(death.death());

		ImportedWaypoint farm = named(all, mc, "Old farm");
		assertTrue(farm.disabled());

		ImportedWaypoint hub = named(all, mc, "Portal hub");
		assertEquals(DimensionIds.NETHER, hub.dimension());
		assertEquals(12, hub.x());

		ImportedWaypoint city = named(all, "My World", "End city");
		assertEquals(DimensionIds.END, city.dimension());

		ImportedWaypoint spawn = named(all, "Multiplayer_old.server.org", "Spawn");
		assertEquals(0, spawn.x());
		assertEquals(70, spawn.y());
		assertEquals(AtlasColours.INK, spawn.colour());
	}

	@Test
	void damagedLinesAndFilesAreSkipped() {
		List<ImportedWaypoint> all = XaeroImport.scan(gameDir());
		for (String gone : new String[]{"Broken", "Too short", "Far away", "Garbled", "Cut off", "Twilight thing", "Backup only"}) {
			assertTrue(all.stream().noneMatch(w -> w.name().equals(gone)), gone + " should have been skipped");
		}
		// the line after the binary junk in the damaged file still reads
		ImportedWaypoint survivor = named(all, "Multiplayer_mc.example.net", "Survivor");
		assertEquals(-7, survivor.x());
		assertEquals(80, survivor.y());
		assertEquals(-9, survivor.z());
	}

	@Test
	void parseFileTakesTheWorldFromTheFolders() {
		Path file = gameDir().resolve("xaero/minimap/Multiplayer_mc.example.net/dim%-1/mw$default_1.txt");
		List<ImportedWaypoint> got = XaeroImport.parseFile(file, null);
		assertEquals(1, got.size());
		assertEquals("Multiplayer_mc.example.net", got.get(0).world());
		assertEquals(DimensionIds.NETHER, got.get(0).dimension());
		// an explicit dimension wins over the folder's
		assertEquals(DimensionIds.END, XaeroImport.parseFile(file, DimensionIds.END).get(0).dimension());
	}

	@Test
	void missingPlacesGiveNothing(@TempDir Path tmp) {
		assertTrue(XaeroImport.scan(tmp).isEmpty());
		assertTrue(XaeroImport.scan(tmp.resolve("does-not-exist")).isEmpty());
		assertTrue(XaeroImport.scan(null).isEmpty());
		assertTrue(XaeroImport.parseFile(tmp.resolve("nope.txt"), DimensionIds.OVERWORLD).isEmpty());
	}

	@Test
	void hugeFilesAreSkipped(@TempDir Path tmp) throws Exception {
		Path dim = Files.createDirectories(tmp.resolve("xaero/minimap/World/dim%0"));
		Path big = dim.resolve("mw$default_1.txt");
		StringBuilder sb = new StringBuilder("waypoint:A:A:1:2:3:0:false:0:gui.xaero_default\n");
		String filler = "#" + "x".repeat(1023) + "\n";
		while (sb.length() <= ImportFiles.MAX_FILE_BYTES) {
			sb.append(filler);
		}
		Files.writeString(big, sb);
		List<String> warnings = new ArrayList<>();
		assertTrue(XaeroImport.scan(tmp, warnings::add).isEmpty());
		assertTrue(warnings.stream().anyMatch(s -> s.contains("too big")));
	}

	@Test
	void lines() {
		ImportedWaypoint w = XaeroImport.parseLine("waypoint:a§§b§§c:A:-5:~:6:12:true:1:set:false:0:0:false", "W", DimensionIds.NETHER);
		assertEquals("a:b:c", w.name());
		assertNull(w.y());
		assertTrue(w.disabled());
		assertTrue(w.death());
		assertEquals(AtlasColours.fromChatColour(12), w.colour());
		assertEquals("W", w.world());

		ImportedWaypoint minimal = XaeroImport.parseLine("waypoint:Min:M:1:2:3", "W", DimensionIds.OVERWORLD);
		assertEquals(1, minimal.x());
		assertEquals(AtlasColours.INK, minimal.colour());
		assertFalse(minimal.death());

		ImportedWaypoint old = XaeroImport.parseLine("waypoint:gui.xaero_deathpoint_old:D:1:2:3:0:false:0", "W", DimensionIds.OVERWORLD);
		assertEquals("Old death", old.name());
		assertTrue(old.death());

		ImportedWaypoint badColour = XaeroImport.parseLine("waypoint:C:C:1:2:3:99:false:0", "W", DimensionIds.OVERWORLD);
		assertEquals(AtlasColours.fromChatColour(99 % 16), badColour.colour());

		ImportedWaypoint deepY = XaeroImport.parseLine("waypoint:Y:Y:1:99999:3", "W", DimensionIds.OVERWORLD);
		assertNull(deepY.y(), "an impossible height is treated as unknown");

		ImportedWaypoint blankName = XaeroImport.parseLine("waypoint::B:1:2:3", "W", DimensionIds.OVERWORLD);
		assertEquals("Waypoint", blankName.name());

		for (String bad : new String[]{"waypoint:A:A:1:2", "waypoint:A:A:x:2:3", "waypoint:A:A:1:y:3", "waypoint:A:A:1:2:3.5",
			"waypoints:A:A:1:2:3", "waypoint:A:A:30000001:2:3", "waypoint:A:A:1:2:-30000001", ""}) {
			assertNull(XaeroImport.parseLine(bad, "W", DimensionIds.OVERWORLD), bad);
		}
	}

	@Test
	void dimensionFolders() {
		assertEquals(DimensionIds.OVERWORLD, XaeroImport.dimensionOf("dim%0"));
		assertEquals(DimensionIds.NETHER, XaeroImport.dimensionOf("dim%-1"));
		assertEquals(DimensionIds.END, XaeroImport.dimensionOf("dim%1"));
		assertEquals(DimensionIds.NETHER, XaeroImport.dimensionOf("dim%minecraft$the_nether"));
		assertEquals("twilightforest:twilight_forest", XaeroImport.dimensionOf("dim%twilightforest$twilight_forest"));
		assertNull(XaeroImport.dimensionOf("dim%7"));
		assertNull(XaeroImport.dimensionOf("dim%"));
		assertNull(XaeroImport.dimensionOf("dim%$x"));
		assertNull(XaeroImport.dimensionOf("dim%abc"));
		assertNull(XaeroImport.dimensionOf("config.txt"));
		assertNull(XaeroImport.dimensionOf(null));
	}

	@Test
	void becomesAnAtlasWaypoint() {
		ImportedWaypoint w = new ImportedWaypoint(ImportedWaypoint.Source.XAERO, "W", "Grave", 1, null, 3, DimensionIds.NETHER, 4, true, true);
		Waypoint a = w.toWaypoint(64, 1234L);
		assertEquals("Grave", a.name());
		assertEquals(64, a.y());
		assertEquals(Waypoint.Kind.DEATH, a.kind());
		assertFalse(a.visible());
		assertEquals(1234L, a.created());
		assertEquals(4, a.colour());
		assertEquals(DimensionIds.NETHER, a.dimension());
	}
}
