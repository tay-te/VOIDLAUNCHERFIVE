package dev.voidmc.atlas.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Moving files over others on every system: Windows refuses to rename over a file someone holds open, so those
 * refusals are retried and then a plain replace is tried; macOS and Linux rename in one step regardless.
 */
class FileMovesTest {
	@TempDir
	Path dir;

	final List<Long> slept = new ArrayList<>();
	final FileMoves.Sleeper sleeper = this.slept::add;

	/** A move that fails with {@code failure} the first {@code times} times it runs, then works. */
	static final class Flaky implements FileMoves.Move {
		final IOException failure;
		final int times;
		int runs;

		Flaky(IOException failure, int times) {
			this.failure = failure;
			this.times = times;
		}

		@Override
		public void run() throws IOException {
			this.runs++;
			if (this.runs <= this.times) {
				throw this.failure;
			}
		}
	}

	@Test
	void windowsRefusalsArePassing() {
		assertTrue(FileMoves.isTransient(new AccessDeniedException("x")), "a target open elsewhere, on Windows");
		assertTrue(FileMoves.isTransient(new FileSystemException("x", null, "used by another process")), "a sharing violation");
		assertFalse(FileMoves.isTransient(new NoSuchFileException("x")));
		assertFalse(FileMoves.isTransient(new FileAlreadyExistsException("x")));
		assertFalse(FileMoves.isTransient(new DirectoryNotEmptyException("x")));
		assertFalse(FileMoves.isTransient(new AtomicMoveNotSupportedException("a", "b", "other volume")));
		assertFalse(FileMoves.isTransient(new IOException("disk full")));
	}

	@Test
	void aRefusedMoveIsRetriedUntilItWorks() throws IOException {
		Flaky move = new Flaky(new AccessDeniedException("x"), 2);
		FileMoves.retrying(move, this.sleeper);
		assertEquals(3, move.runs);
		assertEquals(List.of(FileMoves.WAITS_MS[0], FileMoves.WAITS_MS[1]), this.slept, "short waits, growing");
	}

	@Test
	void aMoveRefusedThroughoutGivesUpWithTheLastError() {
		AccessDeniedException denied = new AccessDeniedException("x");
		Flaky move = new Flaky(denied, Integer.MAX_VALUE);
		assertSame(denied, assertThrows(AccessDeniedException.class, () -> FileMoves.retrying(move, this.sleeper)));
		assertEquals(FileMoves.WAITS_MS.length + 1, move.runs);
		long total = this.slept.stream().mapToLong(Long::longValue).sum();
		assertTrue(total < 500, "gives up within half a second: " + total + " ms");
	}

	@Test
	void otherFailuresAreNotRetried() {
		Flaky move = new Flaky(new NoSuchFileException("x"), 1);
		assertThrows(NoSuchFileException.class, () -> FileMoves.retrying(move, this.sleeper));
		assertEquals(1, move.runs);
		assertTrue(this.slept.isEmpty());
	}

	@Test
	void interruptedWhileWaitingGivesUpAndKeepsTheInterrupt() {
		Flaky move = new Flaky(new AccessDeniedException("x"), 5);
		try {
			assertThrows(AccessDeniedException.class, () -> FileMoves.retrying(move, ms -> {
				throw new InterruptedException();
			}));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void withoutAtomicRenamesTheFileIsReplacedPlainly() throws IOException {
		Flaky atomic = new Flaky(new AtomicMoveNotSupportedException("a", "b", "other volume"), Integer.MAX_VALUE);
		Flaky plain = new Flaky(new AccessDeniedException("x"), 0);
		FileMoves.replace(atomic, plain, this.sleeper);
		assertEquals(1, atomic.runs, "not retried: it cannot work");
		assertEquals(1, plain.runs);
	}

	@Test
	void aTargetHeldOpenThroughoutIsReplacedPlainly() throws IOException {
		Flaky atomic = new Flaky(new AccessDeniedException("x"), Integer.MAX_VALUE);
		Flaky plain = new Flaky(new FileSystemException("x", null, "used by another process"), 1);
		FileMoves.replace(atomic, plain, this.sleeper);
		assertEquals(FileMoves.WAITS_MS.length + 1, atomic.runs);
		assertEquals(2, plain.runs);
	}

	@Test
	void aRealFailureOfTheAtomicMoveIsNotPapered() {
		Flaky atomic = new Flaky(new NoSuchFileException("x"), Integer.MAX_VALUE);
		Flaky plain = new Flaky(new AccessDeniedException("x"), 0);
		assertThrows(NoSuchFileException.class, () -> FileMoves.replace(atomic, plain, this.sleeper));
		assertEquals(0, plain.runs);
	}

	// --- on the real file system -----------------------------------------------------------------------------------

	@Test
	void replacesAFileThatIsOpenForReading() throws IOException {
		Path file = this.dir.resolve("r.0.0.raw");
		Path tmp = this.dir.resolve("r.0.0.raw.tmp");
		Files.writeString(file, "old");
		Files.writeString(tmp, "new");
		// an NIO reader, as the map's workers read: on Windows an atomic rename over it is refused while it is open
		try (FileChannel reader = FileChannel.open(file, StandardOpenOption.READ)) {
			FileMoves.replace(tmp, file);
		}
		assertEquals("new", Files.readString(file));
		assertFalse(Files.exists(tmp));
	}

	@Test
	void waitsForAReaderThatDoesNotShareDeletion() throws Exception {
		Path file = this.dir.resolve("waypoints.json");
		Path tmp = this.dir.resolve("waypoints.json.tmp");
		Files.writeString(file, "old");
		Files.writeString(tmp, "new");
		// java.io streams (and most other programs: scanners, indexers, editors) open files without delete sharing:
		// on Windows nothing can replace the file until they close it, which this one does a moment later
		CountDownLatch opened = new CountDownLatch(1);
		AtomicInteger read = new AtomicInteger();
		Thread holder = new Thread(() -> {
			try (InputStream in = new FileInputStream(file.toFile())) {
				opened.countDown();
				Thread.sleep(40);
				read.set(in.read());
			} catch (IOException | InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}, "test reader");
		holder.start();
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		FileMoves.replace(tmp, file);
		holder.join(5000);
		assertEquals("new", Files.readString(file));
		assertEquals('o', read.get(), "the reader read the old file whole");
		assertFalse(Files.exists(tmp));
	}

	@Test
	void setsAsideOverAnOlderCopy() throws IOException {
		Path file = this.dir.resolve("places.json");
		Path aside = this.dir.resolve("places.json.corrupt");
		Files.writeString(file, "damaged", StandardCharsets.UTF_8);
		Files.writeString(aside, "older damage", StandardCharsets.UTF_8);
		FileMoves.move(file, aside);
		assertEquals("damaged", Files.readString(aside));
		assertFalse(Files.exists(file));
	}
}
