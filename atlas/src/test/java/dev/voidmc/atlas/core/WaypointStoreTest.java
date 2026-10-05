package dev.voidmc.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.Discovery;
import dev.voidmc.atlas.api.Waypoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Waypoints and discoveries survive a save and load; a damaged file is kept aside, not silently lost. */
class WaypointStoreTest {
	@TempDir
	Path dir;

	@Test
	void waypointsRoundTrip() throws IOException {
		WaypointStore store = new WaypointStore(this.dir.resolve("waypoints.json"), s -> {
		});
		Waypoint home = Waypoint.create("Home", 412, 71, -1288, "minecraft:overworld", 0);
		Waypoint mine = Waypoint.create("Mine shaft", 380, -42, -1200, "minecraft:overworld", 3);
		Waypoint fort = Waypoint.create("Fortress", 40, 70, -160, "minecraft:the_nether", 5).withVisible(false);
		store.put(home);
		store.put(mine);
		store.put(fort);
		store.save();
		WaypointStore back = new WaypointStore(this.dir.resolve("waypoints.json"), s -> {
		});
		back.load();
		assertEquals(3, back.all().size());
		assertEquals(home, back.get(home.id()));
		assertEquals(mine, back.get(mine.id()));
		assertEquals(fort, back.get(fort.id()));
		assertFalse(back.get(fort.id()).visible());
		assertEquals(2, back.in("minecraft:overworld").size());
		assertEquals(1, back.in("minecraft:the_nether").size());
	}

	@Test
	void editsReplaceByIdAndRemoveWorks() {
		WaypointStore store = new WaypointStore(this.dir.resolve("w.json"), s -> {
		});
		Waypoint w = Waypoint.create("Camp", 0, 64, 0, "minecraft:overworld", 1);
		store.put(w);
		long v = store.version();
		store.put(w.withName("Base camp").withColour(2));
		assertTrue(store.version() > v);
		assertEquals(1, store.all().size());
		assertEquals("Base camp", store.get(w.id()).name());
		assertEquals(2, store.get(w.id()).colour());
		assertTrue(store.remove(w.id()));
		assertFalse(store.remove(w.id()));
		assertNull(store.get(w.id()));
	}

	@Test
	void namesAndColoursAreCleanedUp() {
		Waypoint blank = Waypoint.create("   ", 0, 0, 0, "minecraft:overworld", -3);
		assertEquals("Waypoint", blank.name());
		assertTrue(blank.colour() >= 0 && blank.colour() < Waypoint.COLOURS.length);
		Waypoint longName = Waypoint.create("x".repeat(200), 0, 0, 0, "minecraft:overworld", 0);
		assertEquals(48, longName.name().length());
	}

	@Test
	void onlyTheLatestDeathsAreKept() {
		WaypointStore store = new WaypointStore(this.dir.resolve("w.json"), s -> {
		});
		for (int i = 0; i < 5; i++) {
			store.addDeath(i, 64, i, "minecraft:overworld", 1000 + i);
		}
		store.addDeath(0, 40, 0, "minecraft:the_nether", 5000);
		List<Waypoint> deaths = store.in("minecraft:overworld");
		assertEquals(WaypointStore.DEATHS_KEPT, deaths.size());
		assertTrue(deaths.stream().allMatch(w -> w.kind() == Waypoint.Kind.DEATH && w.created() >= 1002), "the oldest go first");
		assertEquals(1, store.in("minecraft:the_nether").size(), "each dimension keeps its own");
	}

	@Test
	void aCorruptFileIsKeptAsideAndTheListStartsEmpty() throws IOException {
		Path file = this.dir.resolve("waypoints.json");
		Files.writeString(file, "{\"waypoints\": [ {\"id\": \"a\", \"name\": ", StandardCharsets.UTF_8);
		List<String> warnings = new ArrayList<>();
		WaypointStore store = new WaypointStore(file, warnings::add);
		store.load();
		assertTrue(store.all().isEmpty());
		assertEquals(1, warnings.size());
		assertTrue(Files.exists(this.dir.resolve("waypoints.json.corrupt")));
		store.put(Waypoint.create("New", 1, 2, 3, "minecraft:overworld", 0));
		store.save();
		WaypointStore back = new WaypointStore(file, warnings::add);
		back.load();
		assertEquals(1, back.all().size());
	}

	@Test
	void discoveriesRoundTripOncePerPlace() throws IOException {
		DiscoveryStore store = new DiscoveryStore(this.dir.resolve("discoveries.json"), s -> {
		});
		Discovery temple = new Discovery("expanse:karst_pagoda", "Karst temple", "LANDMARK", 100, 80, -40, "minecraft:overworld", 1L, false);
		assertTrue(store.add(temple));
		assertFalse(store.add(temple), "the same place is found once");
		assertTrue(store.add(new Discovery("minecraft:ancient_city", "Ancient city", "LANDMARK", 0, -40, 0, "minecraft:overworld", 2L, true)));
		assertTrue(store.dirty());
		store.save();
		assertFalse(store.dirty());
		DiscoveryStore back = new DiscoveryStore(this.dir.resolve("discoveries.json"), s -> {
		});
		back.load();
		assertEquals(2, back.all().size());
		assertNotNull(back.all().stream().filter(d -> d.underground()).findFirst().orElse(null));
		assertEquals("EXPANSE", temple.source());
		assertEquals("expanse:karst_pagoda@100,-40", temple.id());
	}

	@Test
	void structureNamesReadWell() {
		assertEquals("Witch hut", StructureNames.name("minecraft:swamp_hut"));
		assertEquals("Frostwatch Citadel", StructureNames.name("expanse:frost_outpost"));
		assertEquals("Old tower", StructureNames.name("some_mod:old_tower"));
		assertEquals("Hut", StructureNames.name("some_mod:houses/hut"));
		assertEquals("VILLAGE", StructureNames.category("minecraft:village_plains", true));
		assertEquals("LANDMARK", StructureNames.category("expanse:karst_pagoda", false));
		assertEquals("DUNGEON", StructureNames.category("minecraft:trial_chambers", false));
		assertTrue(StructureNames.hidden("minecraft:buried_treasure"));
	}
}
