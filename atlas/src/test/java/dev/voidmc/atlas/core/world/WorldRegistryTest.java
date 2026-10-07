package dev.voidmc.atlas.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.store.SafeFile;
import dev.voidmc.atlas.core.store.StorageEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The worlds behind one server address: kept, renamed, saved, and adopted from the old seed-hash folders. */
class WorldRegistryTest {
	@TempDir
	Path dir;

	@Test
	void createNoteSaveAndLoad() throws IOException {
		SafeFile files = new SafeFile();
		WorldRegistry registry = WorldRegistry.load(files, this.dir);
		assertEquals(0, registry.size());
		assertFalse(registry.migrated());
		assertFalse(Files.exists(registry.file()), "nothing to save, nothing written");

		WorldRegistry.World a = registry.create(1000);
		assertEquals("world-1", a.id());
		assertEquals("World 1", a.name());
		assertEquals(this.dir.resolve("world-1"), registry.folder(a.id()));
		registry.noteSeed(a.id(), 0x1234_5678_9abc_def0L);
		registry.noteSeed(a.id(), 0); // hidden seed: ignored
		registry.noteSeed(a.id(), -42);
		registry.noteDimension(a.id(), "minecraft:overworld", new WorldRegistry.DimensionFacts(false, -64, 384));
		registry.noteDimension(a.id(), "minecraft:the_nether", null);
		registry.noteSpawn(a.id(), "minecraft:overworld", new WorldRegistry.Spawn(16, 70, -32));
		registry.touch(a.id(), 5000);
		registry.touch(a.id(), 4000); // never back in time
		registry.rename(a.id(), "  Survival  ");
		WorldRegistry.World b = registry.create(2000);
		assertEquals("world-2", b.id());
		assertEquals("World 2", b.name());
		registry.save();
		assertTrue(Files.readString(registry.file()).startsWith("#atlas worlds v1 "));

		WorldRegistry back = WorldRegistry.load(files, this.dir);
		assertEquals(2, back.size());
		WorldRegistry.World a2 = back.get("world-1");
		assertEquals("Survival", a2.name());
		assertEquals(Set.of(0x1234_5678_9abc_def0L, -42L), a2.seedHashes());
		assertEquals(Set.of("minecraft:overworld", "minecraft:the_nether"), a2.dimensions());
		assertEquals(Map.of("minecraft:overworld", new WorldRegistry.DimensionFacts(false, -64, 384)), a2.facts());
		assertEquals(Map.of("minecraft:overworld", new WorldRegistry.Spawn(16, 70, -32)), a2.spawns());
		assertEquals(1000, a2.created());
		assertEquals(5000, a2.lastUsed());
		assertEquals(List.of("world-1", "world-2"), back.worlds().stream().map(WorldRegistry.World::id).toList());
	}

	@Test
	void adoptsOldSeedHashFoldersOldestFirst() throws IOException {
		Path newest = folder("deadbeefdeadbeef", 3_000_000);
		Path oldest = folder("00ff", 1_000_000);
		Path middle = folder("A1B2C3D4", 2_000_000);
		folder("backups", 500); // not a world folder
		folder("world one", 600); // not a world folder
		Files.writeString(this.dir.resolve("cafe"), "a file, not a folder");

		WorldRegistry registry = WorldRegistry.load(new SafeFile(), this.dir);
		assertTrue(registry.migrated());
		List<WorldRegistry.World> worlds = registry.worlds();
		assertEquals(List.of("00ff", "A1B2C3D4", "deadbeefdeadbeef"), worlds.stream().map(WorldRegistry.World::id).toList());
		assertEquals(List.of("World 1", "World 2", "World 3"), worlds.stream().map(WorldRegistry.World::name).toList());
		assertEquals(Set.of(0xffL), worlds.get(0).seedHashes());
		assertEquals(Set.of(0xA1B2C3D4L), worlds.get(1).seedHashes());
		assertEquals(Set.of(0xdeadbeefdeadbeefL), worlds.get(2).seedHashes());
		assertEquals(1_000_000, worlds.get(0).created());
		assertEquals(oldest, registry.folder("00ff"));
		assertTrue(Files.exists(registry.file()), "the adoption is saved straight away");

		// the next load reads worlds.json and does not adopt again
		WorldRegistry again = WorldRegistry.load(new SafeFile(), this.dir);
		assertFalse(again.migrated());
		assertEquals(3, again.size());
		assertEquals("World 3", again.get("deadbeefdeadbeef").name());
		assertTrue(Files.isDirectory(newest) && Files.isDirectory(middle));
	}

	@Test
	void folderForHiddenSeedHasNoSeed() throws IOException {
		folder("0", 1000);
		WorldRegistry registry = WorldRegistry.load(new SafeFile(), this.dir);
		assertEquals(Set.of(), registry.get("0").seedHashes());
	}

	@Test
	void newWorldsAfterAdoptionContinueTheNumbering() throws IOException {
		folder("abc", 1000);
		folder("def", 2000);
		WorldRegistry registry = WorldRegistry.load(new SafeFile(), this.dir);
		WorldRegistry.World c = registry.create(3000);
		assertEquals("World 3", c.name());
		assertEquals("world-1", c.id());
	}

	@Test
	void createSkipsIdsWhoseFolderExists() throws IOException {
		WorldRegistry registry = WorldRegistry.empty(new SafeFile(), this.dir);
		Files.createDirectories(this.dir.resolve("world-1"));
		assertEquals("world-2", registry.create(1).id());
		assertEquals("world-3", registry.create(2).id());
	}

	@Test
	void createSkipsNamesInUse() {
		WorldRegistry registry = WorldRegistry.empty(new SafeFile(), this.dir);
		WorldRegistry.World a = registry.create(1);
		registry.create(2);
		registry.rename(a.id(), "World 3");
		assertEquals("World 4", registry.create(3).name());
	}

	@Test
	void lostRegistryReadoptsEveryWorldFolder() throws IOException {
		List<StorageEvent> events = new ArrayList<>();
		SafeFile files = new SafeFile(events::add);
		WorldRegistry registry = WorldRegistry.empty(files, this.dir);
		registry.create(1);
		registry.create(2);
		registry.save();
		folder("world-1", 1000);
		folder("world-2", 2000);
		folder("beef", 500);
		Files.writeString(registry.file(), "#atlas worlds v1 crc32=00000000 len=2\n{}");

		WorldRegistry back = WorldRegistry.load(files, this.dir);
		assertTrue(back.lost());
		assertTrue(back.migrated());
		assertEquals(List.of("beef", "world-1", "world-2"), back.worlds().stream().map(WorldRegistry.World::id).toList());
		assertEquals(StorageEvent.Kind.CORRUPT_MOVED_ASIDE, events.get(0).kind());
	}

	@Test
	void damagedRegistryComesBackFromBackup() throws IOException {
		SafeFile files = new SafeFile();
		WorldRegistry registry = WorldRegistry.empty(files, this.dir);
		registry.rename(registry.create(1).id(), "Main");
		registry.save();
		assertTrue(files.backup(registry.file()));
		Files.writeString(registry.file(), "{ not even close");
		WorldRegistry back = WorldRegistry.load(files, this.dir);
		assertFalse(back.lost());
		assertEquals("Main", back.get("world-1").name());
	}

	@Test
	void unknownWorldAndBlankNamesAreRefused() {
		WorldRegistry registry = WorldRegistry.empty(new SafeFile(), this.dir);
		assertThrows(IllegalArgumentException.class, () -> registry.touch("nope", 1));
		String id = registry.create(1).id();
		assertThrows(IllegalArgumentException.class, () -> registry.rename(id, "   "));
		assertTrue(registry.remove(id));
		assertFalse(registry.remove(id));
		assertNull(registry.get(id));
	}

	@Test
	void decodeRefusesPathsAsIds() {
		assertThrows(IllegalArgumentException.class, () -> WorldRegistry.decode("{\"worlds\":[{\"id\":\"../escape\"}]}"));
		assertThrows(IllegalArgumentException.class, () -> WorldRegistry.decode("{\"worlds\":[{\"id\":\"a\\\\b\"}]}"));
		assertThrows(IllegalArgumentException.class, () -> WorldRegistry.decode("{\"worlds\":[{\"id\":\"C:x\"}]}"), "a drive on Windows");
		assertEquals("x", WorldRegistry.decode("{\"worlds\":[{\"id\":\"x\"}]}").get(0).name(), "a world without a name shows its id");
	}

	private Path folder(String name, long modifiedMillis) throws IOException {
		Path p = Files.createDirectories(this.dir.resolve(name));
		Files.setLastModifiedTime(p, FileTime.fromMillis(modifiedMillis));
		return p;
	}
}
