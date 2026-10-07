package dev.voidmc.atlas.core.store;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Moving one file over another, the same way on Windows, macOS and Linux.
 *
 * <p>On macOS and Linux a rename replaces the target in one step even while someone has it open. On Windows it does
 * not: while any handle to the target is open (a reader on another Atlas thread, an antivirus scanner, the search
 * indexer, a backup or sync tool) an atomic replace fails with {@link AccessDeniedException}, and a handle opened
 * without delete sharing (as {@code java.io} streams and most other programs open files) makes even a plain replace
 * fail with a {@link FileSystemException} ("used by another process"). Those holders let go within milliseconds, so
 * such failures are retried for a short while.
 *
 * <p>{@link #replace} wants the atomic rename; where it cannot have it (a file system without atomic renames, or a
 * Windows target that stays open throughout the retries), it falls back to a plain replace, which on Windows deletes
 * the target first and so works past readers that allow deletion. That fallback is not atomic: a crash in between
 * leaves the new file only under its temporary name, which {@link SafeFile#read} knows to look for.
 */
public final class FileMoves {
	/** How long to wait before each retry, in milliseconds: about 0.15 s in all, per kind of move. */
	static final long[] WAITS_MS = {5, 10, 20, 40, 80};

	private FileMoves() {
	}

	/** One move, for retrying. */
	@FunctionalInterface
	interface Move {
		void run() throws IOException;
	}

	/** How to wait between retries (tests pass one that does not). */
	@FunctionalInterface
	interface Sleeper {
		void sleep(long ms) throws InterruptedException;
	}

	/**
	 * Moves {@code from} over {@code to}, atomically where the file system allows: readers see the old file or the new
	 * one, never neither and never half of one.
	 */
	public static void replace(Path from, Path to) throws IOException {
		replace(from, to, Thread::sleep);
	}

	static void replace(Path from, Path to, Sleeper sleeper) throws IOException {
		replace(() -> Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE),
			() -> Files.move(from, to, StandardCopyOption.REPLACE_EXISTING), sleeper);
	}

	/** {@link #replace(Path, Path)} with the two kinds of move given, for tests. */
	static void replace(Move atomic, Move plain, Sleeper sleeper) throws IOException {
		try {
			retrying(atomic, sleeper);
			return;
		} catch (AtomicMoveNotSupportedException e) {
			// a file system without atomic renames (some network and FUSE ones; or another volume): replace plainly
		} catch (IOException e) {
			if (!isTransient(e)) {
				throw e;
			}
			// Windows, the target held open all this time: a plain replace gets past readers that allow deletion
		}
		retrying(plain, sleeper);
	}

	/** Moves {@code from} to {@code to}, replacing it if it exists; not necessarily atomic (for setting files aside). */
	public static void move(Path from, Path to) throws IOException {
		move(from, to, Thread::sleep);
	}

	static void move(Path from, Path to, Sleeper sleeper) throws IOException {
		retrying(() -> Files.move(from, to, StandardCopyOption.REPLACE_EXISTING), sleeper);
	}

	/** Runs {@code move}, retrying it after each wait in {@link #WAITS_MS} while it fails in a way that may pass. */
	static void retrying(Move move, Sleeper sleeper) throws IOException {
		for (int attempt = 0; ; attempt++) {
			try {
				move.run();
				return;
			} catch (IOException e) {
				if (attempt >= WAITS_MS.length || !isTransient(e)) {
					throw e;
				}
				try {
					sleeper.sleep(WAITS_MS[attempt]);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	/**
	 * Whether a failed move may work if tried again shortly: access denied (on Windows, a target open elsewhere or
	 * still being deleted) or a bare file system error (a sharing or lock violation). Not a missing file, an existing
	 * one, a non-empty directory or a file system that cannot move atomically: those will fail the same way again.
	 */
	static boolean isTransient(IOException e) {
		return e instanceof AccessDeniedException || e.getClass() == FileSystemException.class;
	}
}
