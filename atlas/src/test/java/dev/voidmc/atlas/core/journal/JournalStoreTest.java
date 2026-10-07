package dev.voidmc.atlas.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.core.journal.JournalEvent.Kind;
import dev.voidmc.atlas.core.store.Migrations;
import dev.voidmc.atlas.core.store.SafeFile;
import dev.voidmc.atlas.core.store.StorageEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalStoreTest {
	static final String OW = "minecraft:overworld";
	static final String NETHER = "minecraft:the_nether";

	@TempDir
	Path dir;
	Path file;
	List<StorageEvent> reported;
	SafeFile files;

	@BeforeEach
	void setUp() {
		this.file = this.dir.resolve("journal.json");
		this.reported = new ArrayList<>();
		this.files = new SafeFile(Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC), new Migrations(), this.reported::add, 5,
			Duration.ZERO);
	}

	static Journal sampleJournal() {
		Journal j = new Journal();
		j.sample(OW, 0, 64, 0, 2, 1000);
		for (int i = 1; i <= 250; i++) {
			j.sample(OW, i * 5.0, 64 - i / 10.0, 0, 2, 1000 + i); // 1250 blocks, down to Y 39
		}
		j.sample(NETHER, 0, 90, 0, 3, 5000);
		j.sample(NETHER, 3, 90, 4, 3, 5001);
		j.placeDiscovered("v1", "Plains village", "minecraft:village", OW, 100, 70, 200, 2, 1500);
		j.placeDiscovered("p1", "", "atlas:peak", OW, -30, 180, 40, 3, 1600);
		j.areaEntered("a1", "Larkspur \"Meadow\"", "minecraft:plains", OW, 5, 64, 5, 2, 1100);
		j.areaProgress("a1", "Larkspur \"Meadow\"", "minecraft:plains", 400, 100, 2, 1200);
		j.areaProgress("a1", null, null, 400, 300, 3, 1300);
		j.setPlaceCounts(Map.of("minecraft:village", Map.of(DiscoveryState.DISCOVERED, 1, DiscoveryState.SIGHTED, 2)));
		return j;
	}

	@Test
	void saveLoadRoundTrip() throws IOException {
		Journal j = sampleJournal();
		assertTrue(j.dirty());
		j.save(this.files, this.file);
		assertFalse(j.dirty());
		assertTrue(Files.readString(this.file, StandardCharsets.UTF_8).startsWith("#atlas journal v1 "));

		Journal back = Journal.load(this.files, this.file);
		assertEquals(j.all(), back.all(), "every event, in order, field for field");
		assertEquals(j.stats(), back.stats());
		assertEquals(j.areas(), back.areas());
		assertFalse(back.dirty());

		// what was a first stays told
		assertNull(back.placeDiscovered("v1", "Plains village", "minecraft:village", OW, 100, 70, 200, 4, 9000));
		assertNull(back.areaEntered("a1", "x", "minecraft:plains", OW, 0, 0, 0, 4, 9000));
		assertNull(back.dimensionVisited(NETHER, 0, 0, 0, 4, 9000));
		// the kilometres already told are not told again; the next one is
		List<JournalEvent> made = new ArrayList<>();
		back.sample(OW, 0, 64, 0, 4, 9000);
		for (int i = 1; i <= 160; i++) {
			made.addAll(back.sample(OW, 0, 64, i * 5.0, 4, 9000 + i)); // 800 more: 1250 + 5 + 800 > 2000
		}
		assertEquals(List.of("Travelled 2 km"), made.stream().filter(e -> e.kind() == Kind.DISTANCE).map(JournalEvent::text).toList());
		// a new record is measured against the loaded one
		assertTrue(back.sample(OW, 0, 39, 800, 4, 9999).isEmpty());
		assertEquals("Reached Y 38", back.sample(OW, 0, 38, 800, 4, 10000).get(0).text());
	}

	@Test
	void summariesSurviveARoundTrip() throws IOException {
		Journal j = new Journal(20);
		for (int i = 0; i < 100; i++) {
			j.placeDiscovered("p" + i, "P" + i, "minecraft:village", OW, i, 64, i, i / 25, i);
		}
		j.save(this.files, this.file);
		Journal back = Journal.load(this.files, this.file, 20);
		assertEquals(j.all(), back.all());
		assertEquals(100, back.eventCount());
		assertTrue(back.all().stream().anyMatch(JournalEvent::isSummary));
	}

	@Test
	void noFileIsAnEmptyJournal() {
		Journal j = Journal.load(this.files, this.file);
		assertEquals(0, j.size());
		assertEquals(0.0, j.stats().distance());
		assertNull(j.stats().deepestY());
		assertTrue(this.reported.isEmpty());
	}

	@Test
	void aDamagedFileComesBackFromTheBackup() throws IOException {
		Journal j = sampleJournal();
		j.save(this.files, this.file);
		Journal saved = Journal.load(this.files, this.file);
		j.placeDiscovered("late", "Late find", "minecraft:village", OW, 0, 0, 0, 5, 7000);
		j.save(this.files, this.file); // backs up the first save, then writes the second
		assertFalse(SafeFile.backups(this.file).isEmpty());

		// garble a byte in the middle of the newest file
		byte[] data = Files.readAllBytes(this.file);
		data[data.length / 2] ^= 0x5a;
		Files.write(this.file, data);

		Journal back = Journal.load(this.files, this.file);
		assertEquals(saved.all(), back.all(), "the backup's journal");
		assertEquals(saved.stats(), back.stats());
		assertTrue(Files.exists(SafeFile.corruptCopy(this.file, 0)), "the damaged file is kept aside");
		assertTrue(this.reported.stream().anyMatch(e -> e.kind() == StorageEvent.Kind.RESTORED));
		// and the restored file reads cleanly again
		this.reported.clear();
		assertEquals(saved.all(), Journal.load(this.files, this.file).all());
		assertTrue(this.reported.isEmpty());
	}

	@Test
	void aWellFormedFileThatIsNoJournalComesBackFromTheBackup() throws IOException {
		Journal j = sampleJournal();
		j.save(this.files, this.file);
		this.files.backup(this.file);
		// a well-formed file (good checksum) whose payload is not a journal
		this.files.write(this.file, Journal.KIND, Journal.VERSION, "{\"something\":\"else\"}");
		Journal back = Journal.load(this.files, this.file);
		assertEquals(j.all(), back.all());
	}

	@Test
	void aDamagedFileWithNoBackupIsAnEmptyJournal() throws IOException {
		sampleJournal().save(this.files, this.file);
		Files.writeString(this.file, "#atlas journal v1 crc32=00000000 len=3\nabc");
		Journal back = Journal.load(this.files, this.file);
		assertEquals(0, back.size());
		assertTrue(Files.exists(SafeFile.corruptCopy(this.file, 0)));
	}

	@Test
	void jsonIsCompactAndReadable() {
		Journal j = sampleJournal();
		String json = j.toJson();
		assertFalse(json.contains("\n"));
		Journal back = Journal.fromJson(json, Journal.DEFAULT_CAP);
		assertEquals(j.all(), back.all());
		assertEquals("Entered Larkspur \"Meadow\"", back.all().stream().filter(e -> e.kind() == Kind.AREA).findFirst().orElseThrow().text());
	}
}
