package dev.voidmc.atlas.core.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Files survive crashes and damage: checksums catch it, damaged files are kept aside, backups take their place. */
class SafeFileTest {
	@TempDir
	Path dir;

	MutableClock clock;
	List<StorageEvent> events;
	SafeFile files;
	Path file;

	/** A clock tests can move. */
	static final class MutableClock extends Clock {
		Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration d) {
			this.now = this.now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}
	}

	@BeforeEach
	void setUp() {
		this.clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
		this.events = new ArrayList<>();
		this.files = new SafeFile(this.clock, new Migrations(), this.events::add, 5, Duration.ofMinutes(10));
		this.file = this.dir.resolve("waypoints.json");
	}

	@Test
	void roundTripsWithAHeader() throws IOException {
		String payload = "{\"waypoints\":[{\"name\":\"Höhle ☃\"}]}";
		this.files.write(this.file, "waypoints", 2, payload);
		String text = Files.readString(this.file, StandardCharsets.UTF_8);
		assertTrue(text.startsWith("#atlas waypoints v2 crc32="), text);
		assertTrue(text.contains(" len=" + payload.getBytes(StandardCharsets.UTF_8).length + "\n"), text);
		SafeFile.Result r = this.files.read(this.file, "waypoints", 2);
		assertTrue(r.present());
		assertEquals(payload, r.payload());
		assertEquals(2, r.version());
		assertFalse(r.legacy());
		assertFalse(r.restored());
		assertFalse(r.upgraded());
		assertFalse(r.lost());
		assertTrue(this.events.isEmpty());
		assertFalse(Files.exists(this.dir.resolve("waypoints.json.tmp")), "the temporary file is moved into place");
	}

	@Test
	void emptyPayloadRoundTrips() throws IOException {
		this.files.write(this.file, "regions", 1, "");
		assertEquals("", this.files.read(this.file, "regions", 1).payload());
	}

	@Test
	void overwritesKeepOnlyTheLatest() throws IOException {
		this.files.write(this.file, "k", 1, "first");
		this.files.write(this.file, "k", 1, "second, longer than the first");
		this.files.write(this.file, "k", 1, "3");
		assertEquals("3", this.files.read(this.file, "k", 1).payload());
	}

	@Test
	void createsMissingFolders() throws IOException {
		Path deep = this.dir.resolve("mp").resolve("example.org").resolve("world-1").resolve("w.json");
		this.files.write(deep, "k", 1, "x");
		assertEquals("x", this.files.read(deep, "k", 1).payload());
	}

	@Test
	void aMissingFileIsEmptyNotLost() throws IOException {
		this.backupOf("k", 1, "old");
		Files.delete(this.file);
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertFalse(r.present());
		assertFalse(r.lost());
		assertFalse(r.restored(), "a deleted file is not brought back: the player may have deleted it");
		assertTrue(this.events.isEmpty());
	}

	@Test
	void legacyFileIsVersionZero() throws IOException {
		String json = "{\"version\":1,\"waypoints\":[]}";
		Files.writeString(this.file, json);
		SafeFile.Result r = this.files.read(this.file, "waypoints", 1);
		assertTrue(r.present());
		assertTrue(r.legacy());
		assertEquals(0, r.version());
		assertTrue(r.upgraded());
		assertEquals(json, r.payload());
	}

	@Test
	void legacyFileWithByteOrderMark() throws IOException {
		byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
		byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
		byte[] data = Arrays.copyOf(bom, 3 + body.length);
		System.arraycopy(body, 0, data, 3, body.length);
		Files.write(this.file, data);
		assertEquals("{\"a\":1}", this.files.read(this.file, "waypoints", 1).payload());
	}

	@Test
	void migrationsRunInOrder() throws IOException {
		this.files.migrations()
			.register("waypoints", 0, s -> s + "+1")
			.register("waypoints", 1, s -> s + "+2")
			.register("other", 0, s -> s + "+never");
		Files.writeString(this.file, "legacy");
		SafeFile.Result r = this.files.read(this.file, "waypoints", 2);
		assertEquals("legacy+1+2", r.payload());
		assertEquals(0, r.version());
		assertTrue(r.upgraded());

		this.files.write(this.file, "waypoints", 1, "one");
		assertEquals("one+2", this.files.read(this.file, "waypoints", 2).payload());
		this.files.write(this.file, "waypoints", 2, "two");
		SafeFile.Result current = this.files.read(this.file, "waypoints", 2);
		assertEquals("two", current.payload());
		assertFalse(current.upgraded());
	}

	@Test
	void aStepWithoutAMigrationPassesThrough() throws IOException {
		this.files.migrations().register("k", 1, s -> s.toUpperCase());
		this.files.write(this.file, "k", 0, "abc");
		assertEquals("ABC", this.files.read(this.file, "k", 2).payload());
	}

	@Test
	void migrationRegisteredTwiceIsAnError() {
		Migrations m = new Migrations().register("k", 0, s -> s);
		assertThrows(IllegalStateException.class, () -> m.register("k", 0, s -> s));
		assertTrue(m.has("k", 0));
		assertFalse(m.has("k", 1));
	}

	@Test
	void badChecksumIsMovedAsideAndBackupRestored() throws IOException {
		this.backupOf("waypoints", 1, "good");
		this.files.write(this.file, "waypoints", 1, "newer");
		flipLastByte(this.file);
		SafeFile.Result r = this.files.read(this.file, "waypoints", 1);
		assertTrue(r.restored());
		assertEquals("good", r.payload());
		assertNotNull(r.backup());
		assertTrue(r.backup().getFileName().toString().startsWith("waypoints.json.2026"));
		assertTrue(Files.exists(this.dir.resolve("waypoints.json.corrupt")));
		assertEquals(List.of(StorageEvent.Kind.CORRUPT_MOVED_ASIDE, StorageEvent.Kind.RESTORED), this.kinds());
		assertTrue(this.events.get(0).detail().contains("checksum"), this.events.get(0).detail());
		// the backup was put back in place, so the next read is clean
		this.events.clear();
		SafeFile.Result again = this.files.read(this.file, "waypoints", 1);
		assertEquals("good", again.payload());
		assertFalse(again.restored());
		assertTrue(this.events.isEmpty());
	}

	@Test
	void truncatedFileIsCorrupt() throws IOException {
		this.files.write(this.file, "k", 1, "a long enough payload to cut in half");
		byte[] data = Files.readAllBytes(this.file);
		Files.write(this.file, Arrays.copyOf(data, data.length - 10));
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertFalse(r.present());
		assertTrue(r.lost(), "no backup to fall back on");
		assertTrue(this.events.get(0).detail().contains("cut short"), this.events.get(0).detail());
		assertFalse(Files.exists(this.file));
		assertTrue(Files.exists(this.dir.resolve("waypoints.json.corrupt")));
	}

	@Test
	void truncatedInsideTheHeaderIsCorrupt() throws IOException {
		Files.writeString(this.file, "#atlas k v1 crc3");
		assertTrue(this.files.read(this.file, "k", 1).lost());
	}

	@Test
	void emptyAndZeroedFilesAreCorrupt() throws IOException {
		Files.write(this.file, new byte[0]);
		assertTrue(this.files.read(this.file, "k", 1).lost());
		Files.write(this.file, new byte[4096]);
		assertTrue(this.files.read(this.file, "k", 1).lost(), "a file of zero bytes after a power cut is not legacy text");
	}

	@Test
	void notUtf8IsCorrupt() throws IOException {
		Files.write(this.file, new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'});
		assertTrue(this.files.read(this.file, "k", 1).lost());
	}

	@Test
	void wrongKindIsCorrupt() throws IOException {
		this.files.write(this.file, "worlds", 1, "{}");
		SafeFile.Result r = this.files.read(this.file, "waypoints", 1);
		assertTrue(r.lost());
		assertTrue(this.events.get(0).detail().contains("'worlds'"), this.events.get(0).detail());
	}

	@Test
	void newerVersionIsMovedAsideAndOlderBackupUsed() throws IOException {
		this.backupOf("k", 1, "v1 data");
		this.files.write(this.file, "k", 3, "from the future");
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertTrue(r.restored());
		assertEquals("v1 data", r.payload());
		assertTrue(this.events.get(0).detail().contains("newer"), this.events.get(0).detail());
		assertEquals("#atlas k v3", Files.readString(this.dir.resolve("waypoints.json.corrupt")).substring(0, 11), "the newer file is kept, not lost");
	}

	@Test
	void validatorRejectsUnparseableLegacyJson() throws IOException {
		this.backupOf("waypoints", 0, "{\"waypoints\":[]}");
		Files.writeString(this.file, "{\"waypoints\":[");
		SafeFile.Result r = this.files.read(this.file, "waypoints", 1, SafeFileTest::parses);
		assertTrue(r.restored());
		assertEquals("{\"waypoints\":[]}", r.payload());
	}

	@Test
	void corruptBackupsAreSkippedNewestFirst() throws IOException {
		this.backupOf("k", 1, "oldest");
		this.clock.advance(Duration.ofMinutes(11));
		this.files.write(this.file, "k", 1, "middle");
		assertTrue(this.files.backup(this.file));
		this.clock.advance(Duration.ofMinutes(11));
		this.files.write(this.file, "k", 1, "newest");
		assertTrue(this.files.backup(this.file));
		List<Path> backups = SafeFile.backups(this.file);
		assertEquals(3, backups.size());
		flipLastByte(backups.get(0));
		flipLastByte(this.file);
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertTrue(r.restored());
		assertEquals("middle", r.payload());
		assertEquals(backups.get(1), r.backup());
	}

	@Test
	void noValidBackupMeansLost() throws IOException {
		this.backupOf("k", 1, "x");
		flipLastByte(SafeFile.backups(this.file).get(0));
		flipLastByte(this.file);
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertTrue(r.lost());
		assertFalse(r.present());
		assertEquals(List.of(StorageEvent.Kind.CORRUPT_MOVED_ASIDE), this.kinds());
	}

	@Test
	void keepsAtMostThreeCorruptCopies() throws IOException {
		for (int i = 0; i < 5; i++) {
			Files.writeString(this.file, "#atlas k v1 crc32=00000000 len=1\n" + i);
			assertTrue(this.files.read(this.file, "k", 1).lost());
		}
		assertEquals("#atlas k v1 crc32=00000000 len=1\n4", Files.readString(SafeFile.corruptCopy(this.file, 0)));
		assertEquals("#atlas k v1 crc32=00000000 len=1\n3", Files.readString(SafeFile.corruptCopy(this.file, 1)));
		assertEquals("#atlas k v1 crc32=00000000 len=1\n2", Files.readString(SafeFile.corruptCopy(this.file, 2)));
		assertFalse(Files.exists(SafeFile.corruptCopy(this.file, 3)));
		try (var list = Files.list(this.dir)) {
			assertEquals(3, list.filter(p -> p.getFileName().toString().contains(".corrupt")).count());
		}
	}

	@Test
	void backupNamingAndThrottle() throws IOException {
		this.files.write(this.file, "k", 1, "a");
		assertTrue(this.files.backup(this.file));
		assertEquals("waypoints.json.20261005-120000", SafeFile.backups(this.file).get(0).getFileName().toString());
		assertEquals(this.dir.resolve("backups"), SafeFile.backups(this.file).get(0).getParent());

		this.files.write(this.file, "k", 1, "b");
		this.clock.advance(Duration.ofMinutes(9));
		assertFalse(this.files.backup(this.file), "at most one backup per 10 minutes");
		this.clock.advance(Duration.ofMinutes(1));
		assertTrue(this.files.backup(this.file));
		assertEquals(2, SafeFile.backups(this.file).size());
		assertEquals("waypoints.json.20261005-121000", SafeFile.backups(this.file).get(0).getFileName().toString());
	}

	@Test
	void unchangedFileIsNotBackedUpAgain() throws IOException {
		this.files.write(this.file, "k", 1, "same");
		assertTrue(this.files.backup(this.file));
		this.clock.advance(Duration.ofHours(1));
		assertFalse(this.files.backup(this.file));
		assertEquals(1, SafeFile.backups(this.file).size());
	}

	@Test
	void keepsTheNewestFiveBackups() throws IOException {
		for (int i = 0; i < 8; i++) {
			this.files.write(this.file, "k", 1, "version " + i);
			assertTrue(this.files.backup(this.file));
			this.clock.advance(Duration.ofMinutes(15));
		}
		List<Path> backups = SafeFile.backups(this.file);
		assertEquals(5, backups.size());
		assertEquals("version 7", this.files.read(backups.get(0), "k", 1).payload());
		assertEquals("version 3", this.files.read(backups.get(4), "k", 1).payload());
	}

	@Test
	void customBackupCountAndInterval() throws IOException {
		SafeFile two = new SafeFile(this.clock, new Migrations(), this.events::add, 2, Duration.ZERO);
		for (int i = 0; i < 4; i++) {
			two.write(this.file, "k", 1, "v" + i);
			this.clock.advance(Duration.ofSeconds(1));
			assertTrue(two.backup(this.file));
		}
		assertEquals(2, SafeFile.backups(this.file).size());
	}

	@Test
	void backupsOfDifferentFilesDoNotMix() throws IOException {
		Path other = this.dir.resolve("worlds.json");
		this.files.write(this.file, "k", 1, "a");
		this.files.write(other, "k", 1, "b");
		assertTrue(this.files.backup(this.file));
		assertTrue(this.files.backup(other));
		assertEquals(1, SafeFile.backups(this.file).size());
		assertEquals(1, SafeFile.backups(other).size());
	}

	@Test
	void damagedFileIsNotBackedUp() throws IOException {
		this.files.write(this.file, "k", 1, "good");
		assertTrue(this.files.backup(this.file));
		this.clock.advance(Duration.ofHours(1));
		flipLastByte(this.file);
		assertFalse(this.files.backup(this.file));
		assertEquals(List.of(StorageEvent.Kind.BACKUP_FAILED), this.kinds());
		assertEquals(1, SafeFile.backups(this.file).size());
	}

	@Test
	void legacyFileCanBeBackedUp() throws IOException {
		Files.writeString(this.file, "{\"plain\":true}");
		assertTrue(this.files.backup(this.file));
		assertArrayEquals(Files.readAllBytes(this.file), Files.readAllBytes(SafeFile.backups(this.file).get(0)));
	}

	@Test
	void missingFileHasNothingToBackUp() {
		assertFalse(this.files.backup(this.file));
		assertTrue(this.events.isEmpty());
	}

	@Test
	void backupInTheFutureDoesNotBlockBackups() throws IOException {
		this.files.write(this.file, "k", 1, "a");
		this.clock.advance(Duration.ofHours(2));
		assertTrue(this.files.backup(this.file));
		this.clock.advance(Duration.ofHours(-1)); // the system clock was put back
		this.files.write(this.file, "k", 1, "b");
		assertTrue(this.files.backup(this.file));
	}

	@Test
	void restoredBackupIsUpgradedToo() throws IOException {
		Files.writeString(this.file, "legacy");
		assertTrue(this.files.backup(this.file));
		Files.writeString(this.file, "#atlas k v1 broken");
		this.files.migrations().register("k", 0, s -> s + "->v1");
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertTrue(r.restored());
		assertTrue(r.legacy());
		assertEquals("legacy->v1", r.payload());
	}

	@Test
	void failingMigrationCountsAsUnreadable() throws IOException {
		this.files.migrations().register("k", 0, s -> {
			throw new IllegalStateException("cannot upgrade");
		});
		Files.writeString(this.file, "legacy");
		assertTrue(this.files.read(this.file, "k", 1).lost());
	}

	@Test
	void listenerFailuresDoNotBreakReads() throws IOException {
		SafeFile noisy = new SafeFile(this.clock, new Migrations(), e -> {
			throw new RuntimeException("UI not ready");
		}, 5, Duration.ofMinutes(10));
		Files.writeString(this.file, "#atlas k v1 garbage\n");
		assertTrue(noisy.read(this.file, "k", 1).lost());
	}

	@Test
	void kindMustBeOneWord() {
		assertThrows(IllegalArgumentException.class, () -> this.files.write(this.file, "two words", 1, "x"));
		assertThrows(IllegalArgumentException.class, () -> this.files.write(this.file, "k", -1, "x"));
		assertThrows(IllegalArgumentException.class, () -> this.files.read(this.file, "", 1));
	}

	@Test
	void headerIsExact() {
		byte[] data = SafeFile.encode("worlds", 7, "hi");
		assertEquals("#atlas worlds v7 crc32=" + String.format("%08x", crc("hi")) + " len=2\nhi", new String(data, StandardCharsets.UTF_8));
	}

	// ---------------------------------------------------------------- every system

	@Test
	void writesOverAFileThatIsBeingRead() throws IOException {
		this.files.write(this.file, "k", 1, "old");
		// on Windows a rename over an open file is refused; the write must still land
		try (java.nio.channels.FileChannel reader = java.nio.channels.FileChannel.open(this.file, java.nio.file.StandardOpenOption.READ)) {
			this.files.write(this.file, "k", 1, "new");
		}
		assertEquals("new", this.files.read(this.file, "k", 1).payload());
		assertFalse(Files.exists(this.dir.resolve("waypoints.json.tmp")));
	}

	@Test
	void aWriteCutShortMidReplaceIsFoundInItsTemporaryFile() throws IOException {
		// the old file removed, the new one complete under .tmp, and then the crash
		Files.write(this.dir.resolve("waypoints.json.tmp"), SafeFile.encode("k", 1, "newest"));
		SafeFile.Result r = this.files.read(this.file, "k", 1);
		assertTrue(r.present());
		assertEquals("newest", r.payload());
		assertTrue(r.upgraded(), "so the caller saves it in place");
		assertFalse(r.lost());
		assertTrue(this.events.isEmpty());
	}

	@Test
	void aTemporaryFileThatIsNotWholeIsIgnored() throws IOException {
		byte[] whole = SafeFile.encode("k", 1, "newest");
		Path tmp = this.dir.resolve("waypoints.json.tmp");
		Files.write(tmp, java.util.Arrays.copyOf(whole, whole.length - 2));
		assertFalse(this.files.read(this.file, "k", 1).present(), "cut short while being written");
		Files.write(tmp, SafeFile.encode("other", 1, "newest"));
		assertFalse(this.files.read(this.file, "k", 1).present(), "another kind of file");
		Files.writeString(tmp, "{\"no\":\"header\"}");
		assertFalse(this.files.read(this.file, "k", 1).present(), "no header, so no checksum to trust");
		assertFalse(this.files.read(this.file, "k", 0).present(), "not even as a legacy file");
	}

	@Test
	void aFailedWriteLeavesTheOldFile() throws IOException {
		this.files.write(this.file, "k", 1, "old");
		// the temporary file's name taken by a folder: the write fails before anything is moved
		Files.createDirectories(this.dir.resolve("waypoints.json.tmp").resolve("in-the-way"));
		assertThrows(IOException.class, () -> this.files.write(this.file, "k", 1, "new"));
		assertEquals("old", this.files.read(this.file, "k", 1).payload());
	}

	// ---------------------------------------------------------------- helpers

	/** Writes {@code payload} and backs it up, leaving the main file in place. */
	private void backupOf(String kind, int version, String payload) throws IOException {
		this.files.write(this.file, kind, version, payload);
		assertTrue(this.files.backup(this.file));
		this.clock.advance(Duration.ofMinutes(11));
	}

	private List<StorageEvent.Kind> kinds() {
		return this.events.stream().map(StorageEvent::kind).toList();
	}

	private static void flipLastByte(Path p) throws IOException {
		byte[] data = Files.readAllBytes(p);
		data[data.length - 1] ^= 0x01;
		Files.write(p, data);
	}

	private static boolean parses(String json) {
		try {
			JsonParser.parseString(json).getAsJsonObject();
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static long crc(String s) {
		java.util.zip.CRC32 c = new java.util.zip.CRC32();
		c.update(s.getBytes(StandardCharsets.UTF_8));
		return c.getValue();
	}
}
