package dev.voidmc.atlas.core.store;

import java.nio.file.Path;

/**
 * Something {@link SafeFile} did on its own that the player may want to hear about: a damaged file was moved aside,
 * a file was brought back from a backup, or a backup could not be made. The UI turns these into a short notice
 * ("Waypoints restored from the backup of 14:20").
 *
 * @param kind   what happened
 * @param file   the file it happened to (the main file, not the backup)
 * @param detail a plain-English line for the log: why, and from which backup
 */
public record StorageEvent(Kind kind, Path file, String detail) {
	public enum Kind {
		/** The main file was unreadable and has been replaced by its newest readable backup. */
		RESTORED,
		/** The main file was unreadable and has been moved aside to {@code <name>.corrupt}. */
		CORRUPT_MOVED_ASIDE,
		/** A backup was due but could not be written (disk full, permissions, or the file itself is damaged). */
		BACKUP_FAILED
	}
}
