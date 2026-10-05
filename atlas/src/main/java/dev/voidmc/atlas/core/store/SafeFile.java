package dev.voidmc.atlas.core.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Crash-safe text files (JSON, or any other text) with a checksum, rolling backups and version upgrades.
 *
 * <p>A file is one header line, then the payload as UTF-8:
 * <pre>#atlas waypoints v2 crc32=1c291ca3 len=5120</pre>
 * The header names the kind of file (so a waypoints file copied over a worlds file is noticed), the payload's format
 * version, a CRC-32 of the payload bytes and their count, so a file cut short by a crash or garbled on disk is
 * recognised instead of read as nonsense.
 *
 * <p>Writing goes to {@code <name>.tmp}, which is flushed to the disk before it is moved over the old file in one
 * step: after a crash there is the old file or the new one, never half of one.
 *
 * <p>Reading accepts a file without a header (the plain {@code waypoints.json} older Atlas builds wrote) as version 0,
 * so it can be migrated. A file that cannot be read (bad checksum, cut short, another kind, a version newer than this
 * build knows, or a payload the caller rejects) is moved aside to {@code <name>.corrupt}, keeping the last
 * {@value #CORRUPT_KEPT} such copies, and the newest readable backup takes its place. Every such step is reported to
 * the listener so the UI can say so.
 *
 * <p>Backups live in {@code <dir>/backups/<name>.<yyyyMMdd-HHmmss>}; {@link #backup} keeps the newest few and makes at
 * most one per interval. The clock is injectable so tests can move time.
 *
 * <p>Not meant for two writers of the same file at once: each store owns its files and saves from one place.
 */
public final class SafeFile {
	public static final String MAGIC = "#atlas";
	public static final String CORRUPT_SUFFIX = ".corrupt";
	public static final String TEMP_SUFFIX = ".tmp";
	public static final String BACKUP_DIR = "backups";
	/** Damaged copies kept: {@code <name>.corrupt} (newest), {@code .corrupt.1}, {@code .corrupt.2}. */
	public static final int CORRUPT_KEPT = 3;
	public static final int DEFAULT_BACKUPS_KEPT = 5;
	public static final Duration DEFAULT_BACKUP_INTERVAL = Duration.ofMinutes(10);
	/** The header line is never longer than this; a longer first line is not a header. */
	private static final int HEADER_MAX = 200;

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern KIND = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final Pattern HEADER = Pattern.compile("#atlas ([A-Za-z0-9_.-]+) v(\\d{1,9}) crc32=([0-9a-f]{8}) len=(\\d{1,10})");

	private final Clock clock;
	private final Migrations migrations;
	private final Consumer<StorageEvent> listener;
	private final int backupsKept;
	private final Duration backupInterval;

	public SafeFile(Clock clock, Migrations migrations, Consumer<StorageEvent> listener, int backupsKept, Duration backupInterval) {
		if (backupsKept < 1) {
			throw new IllegalArgumentException("backupsKept " + backupsKept);
		}
		this.clock = Objects.requireNonNull(clock);
		this.migrations = Objects.requireNonNull(migrations);
		this.listener = listener == null ? e -> {
		} : listener;
		this.backupsKept = backupsKept;
		this.backupInterval = Objects.requireNonNull(backupInterval);
	}

	/** System clock, no migrations yet, {@value #DEFAULT_BACKUPS_KEPT} backups at most one per 10 minutes. */
	public SafeFile(Consumer<StorageEvent> listener) {
		this(Clock.systemDefaultZone(), new Migrations(), listener, DEFAULT_BACKUPS_KEPT, DEFAULT_BACKUP_INTERVAL);
	}

	public SafeFile() {
		this(null);
	}

	/** Where to register upgrades for the files this instance reads. */
	public Migrations migrations() {
		return this.migrations;
	}

	/**
	 * What a read found.
	 *
	 * @param payload  the text, upgraded to the version asked for; null if there was nothing readable
	 * @param version  the version the file (or the backup it came from) was written as; 0 for a legacy file
	 * @param legacy   the file had no header: an older Atlas wrote it
	 * @param upgraded migrations ran (the caller should save, so the file on disk is current)
	 * @param restored the main file was unreadable and this came from a backup
	 * @param backup   which backup, if restored
	 * @param lost     the main file was unreadable and no backup was either: the data is gone (the damaged file is
	 *                 still there as {@code .corrupt})
	 */
	public record Result(String payload, int version, boolean legacy, boolean upgraded, boolean restored, Path backup, boolean lost) {
		static Result missing() {
			return new Result(null, -1, false, false, false, null, false);
		}

		static Result gone() {
			return new Result(null, -1, false, false, false, null, true);
		}

		/** There is a payload (read straight, or restored). */
		public boolean present() {
			return this.payload != null;
		}
	}

	/** The header and payload of a file, checked. */
	record Decoded(String payload, int version, boolean legacy) {
	}

	// ---------------------------------------------------------------- writing

	/** Writes {@code payload} as a {@code kind} file of {@code version}, atomically. */
	public void write(Path file, String kind, int version, String payload) throws IOException {
		writeAtomic(file, encode(kind, version, payload));
	}

	/** The bytes of a file: the header line, then the payload as UTF-8. */
	public static byte[] encode(String kind, int version, String payload) {
		checkKind(kind);
		if (version < 0) {
			throw new IllegalArgumentException("version " + version);
		}
		byte[] body = payload.getBytes(StandardCharsets.UTF_8);
		CRC32 crc = new CRC32();
		crc.update(body);
		byte[] header = String.format("%s %s v%d crc32=%08x len=%d\n", MAGIC, kind, version, crc.getValue(), body.length).getBytes(StandardCharsets.US_ASCII);
		byte[] out = Arrays.copyOf(header, header.length + body.length);
		System.arraycopy(body, 0, out, header.length, body.length);
		return out;
	}

	/**
	 * Writes a file whole: to {@code <name>.tmp}, flushed to the disk, then moved over the old file in one step
	 * (or, where the file system cannot move atomically, replaced).
	 */
	public static void writeAtomic(Path file, byte[] data) throws IOException {
		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path tmp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
		try {
			try (FileChannel out = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buffer = ByteBuffer.wrap(data);
				while (buffer.hasRemaining()) {
					out.write(buffer);
				}
				out.force(true);
			}
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
				// a stale .tmp is harmless: the next write truncates it
			}
			throw e;
		}
		syncDirectory(dir);
	}

	/** Makes the rename itself durable where the platform allows it (not on Windows, where this quietly does nothing). */
	private static void syncDirectory(Path dir) {
		try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
			channel.force(true);
		} catch (IOException | RuntimeException ignored) {
			// directories cannot be opened like this everywhere; the data itself is already on disk
		}
	}

	// ---------------------------------------------------------------- reading

	/** Reads a {@code kind} file, upgrading it to {@code maxVersion}; see {@link #read(Path, String, int, Predicate)}. */
	public Result read(Path file, String kind, int maxVersion) {
		return this.read(file, kind, maxVersion, payload -> true);
	}

	/**
	 * Reads a {@code kind} file, upgrading it to {@code maxVersion} with the registered migrations. {@code valid} gets
	 * the upgraded payload and may reject it (say, JSON that does not parse): a rejected file counts as unreadable like
	 * a bad checksum. A legacy file has no checksum, so this is the only check it gets.
	 *
	 * <p>No file at all is not an error: the result is empty, not {@code lost}, and no backup is restored (the
	 * player may have deleted it on purpose).
	 */
	public Result read(Path file, String kind, int maxVersion, Predicate<String> valid) {
		checkKind(kind);
		byte[] data;
		try {
			data = Files.readAllBytes(file);
		} catch (NoSuchFileException e) {
			return Result.missing();
		} catch (IOException e) {
			if (!Files.exists(file)) {
				return Result.missing();
			}
			return this.recover(file, kind, maxVersion, valid, "it could not be read (" + e.getMessage() + ")");
		}
		try {
			Decoded decoded = decode(data, kind, maxVersion);
			String payload = this.upgrade(kind, decoded, maxVersion, valid);
			return new Result(payload, decoded.version(), decoded.legacy(), decoded.version() < maxVersion, false, null, false);
		} catch (IOException | RuntimeException e) {
			return this.recover(file, kind, maxVersion, valid, e.getMessage());
		}
	}

	private String upgrade(String kind, Decoded decoded, int maxVersion, Predicate<String> valid) throws IOException {
		String payload = this.migrations.apply(kind, decoded.version(), maxVersion, decoded.payload());
		if (!valid.test(payload)) {
			throw new IOException("its contents were rejected");
		}
		return payload;
	}

	private Result recover(Path file, String kind, int maxVersion, Predicate<String> valid, String why) {
		Path aside = moveAside(file);
		this.emit(new StorageEvent(StorageEvent.Kind.CORRUPT_MOVED_ASIDE, file,
			file.getFileName() + " is unreadable (" + why + ")" + (aside == null ? "; it could not be moved aside" : "; kept as " + aside.getFileName())));
		for (Path backup : backups(file)) {
			try {
				byte[] data = Files.readAllBytes(backup);
				Decoded decoded = decode(data, kind, maxVersion);
				String payload = this.upgrade(kind, decoded, maxVersion, valid);
				try {
					writeAtomic(file, data);
				} catch (IOException e) {
					// the payload is still good for this session; the next save writes the file again
				}
				this.emit(new StorageEvent(StorageEvent.Kind.RESTORED, file, file.getFileName() + " restored from backup " + backup.getFileName()));
				return new Result(payload, decoded.version(), decoded.legacy(), decoded.version() < maxVersion, true, backup, false);
			} catch (IOException | RuntimeException e) {
				// this backup is no good either: try the next older one
			}
		}
		return Result.gone();
	}

	/**
	 * Checks a file's bytes: header syntax, kind, version (at most {@code maxVersion}), length, checksum, and that the
	 * payload is UTF-8. Bytes without a header are a legacy (version 0) file, accepted if they are non-empty UTF-8 text
	 * without NUL bytes (a file zeroed by a power cut is not text).
	 */
	static Decoded decode(byte[] data, String kind, int maxVersion) throws IOException {
		if (data.length == 0) {
			throw new IOException("empty file");
		}
		if (!startsWith(data, MAGIC)) {
			for (byte b : data) {
				if (b == 0) {
					throw new IOException("NUL bytes in a text file");
				}
			}
			String text = utf8(data, startsWithBom(data) ? 3 : 0, data.length);
			if (text.isBlank()) {
				throw new IOException("blank file");
			}
			return new Decoded(text, 0, true);
		}
		int newline = -1;
		for (int i = 0; i < Math.min(data.length, HEADER_MAX); i++) {
			if (data[i] == '\n') {
				newline = i;
				break;
			}
		}
		if (newline < 0) {
			throw new IOException("cut short inside the header");
		}
		String header = new String(data, 0, newline, StandardCharsets.US_ASCII);
		Matcher m = HEADER.matcher(header);
		if (!m.matches()) {
			throw new IOException("malformed header '" + header + "'");
		}
		if (!m.group(1).equals(kind)) {
			throw new IOException("it is a '" + m.group(1) + "' file, not '" + kind + "'");
		}
		int version = Integer.parseInt(m.group(2));
		if (version > maxVersion) {
			throw new IOException("version " + version + " is newer than this Atlas reads (" + maxVersion + ")");
		}
		long length = Long.parseLong(m.group(4));
		int body = data.length - newline - 1;
		if (length != body) {
			throw new IOException(body < length ? "cut short: " + body + " of " + length + " bytes" : length + " bytes expected but " + body + " follow");
		}
		CRC32 crc = new CRC32();
		crc.update(data, newline + 1, body);
		if (crc.getValue() != Long.parseLong(m.group(3), 16)) {
			throw new IOException("checksum mismatch");
		}
		return new Decoded(utf8(data, newline + 1, data.length), version, false);
	}

	/** Whether the bytes are a file worth keeping as a backup: an intact header file of any kind, or legacy text. */
	static boolean intact(byte[] data) {
		if (data.length > 0 && startsWith(data, MAGIC)) {
			int newline = -1;
			for (int i = 0; i < Math.min(data.length, HEADER_MAX); i++) {
				if (data[i] == '\n') {
					newline = i;
					break;
				}
			}
			if (newline < 0) {
				return false;
			}
			Matcher m = HEADER.matcher(new String(data, 0, newline, StandardCharsets.US_ASCII));
			if (!m.matches()) {
				return false;
			}
			try {
				decode(data, m.group(1), Integer.MAX_VALUE);
				return true;
			} catch (IOException | RuntimeException e) {
				return false;
			}
		}
		try {
			decode(data, "any", 0);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// ---------------------------------------------------------------- corrupt copies

	/**
	 * Moves a damaged file to {@code <name>.corrupt}, shifting older damaged copies to {@code .corrupt.1} and
	 * {@code .corrupt.2} and dropping the oldest. Returns where it went, or null if it could not be moved.
	 */
	static Path moveAside(Path file) {
		String name = file.getFileName().toString();
		Path first = file.resolveSibling(name + CORRUPT_SUFFIX);
		try {
			Files.deleteIfExists(corruptCopy(file, CORRUPT_KEPT - 1));
			for (int i = CORRUPT_KEPT - 2; i >= 0; i--) {
				Path from = corruptCopy(file, i);
				if (Files.exists(from)) {
					Files.move(from, corruptCopy(file, i + 1), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			Files.move(file, first, StandardCopyOption.REPLACE_EXISTING);
			return first;
		} catch (IOException e) {
			return null;
		}
	}

	/** The {@code n}th newest damaged copy: {@code <name>.corrupt} for 0, {@code <name>.corrupt.<n>} after. */
	public static Path corruptCopy(Path file, int n) {
		String name = file.getFileName() + CORRUPT_SUFFIX;
		return file.resolveSibling(n == 0 ? name : name + "." + n);
	}

	// ---------------------------------------------------------------- backups

	/** The folder backups of {@code file} go in. */
	public static Path backupDir(Path file) {
		return file.toAbsolutePath().getParent().resolve(BACKUP_DIR);
	}

	/** The backups of {@code file}, newest first. */
	public static List<Path> backups(Path file) {
		Path dir = backupDir(file);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		Pattern pattern = backupPattern(file);
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(p -> pattern.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p))
				.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
				.toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * Copies {@code file} into its backup folder, if a backup is due: the file exists and is intact, it differs from
	 * the newest backup, and that backup is at least the backup interval old. Then only the newest backups are kept.
	 * Returns whether a backup was made. A damaged file is never backed up (it would push out good backups); that,
	 * and any failure to write, is reported as {@link StorageEvent.Kind#BACKUP_FAILED}.
	 */
	public boolean backup(Path file) {
		if (!Files.isRegularFile(file)) {
			return false;
		}
		try {
			byte[] data = Files.readAllBytes(file);
			if (!intact(data)) {
				this.emit(new StorageEvent(StorageEvent.Kind.BACKUP_FAILED, file, file.getFileName() + " is damaged; the older backups are kept as they are"));
				return false;
			}
			Instant now = this.clock.instant();
			List<Path> existing = backups(file);
			if (!existing.isEmpty()) {
				Path newest = existing.get(0);
				Instant last = this.stampOf(file, newest);
				if (last != null && !now.isBefore(last) && Duration.between(last, now).compareTo(this.backupInterval) < 0) {
					return false;
				}
				if (Arrays.equals(Files.readAllBytes(newest), data)) {
					return false;
				}
			}
			Path target = backupDir(file).resolve(file.getFileName() + "." + STAMP.format(now.atZone(this.clock.getZone())));
			writeAtomic(target, data);
			List<Path> all = backups(file);
			List<Path> drop = new ArrayList<>(all.subList(Math.min(this.backupsKept, all.size()), all.size()));
			for (Path old : drop) {
				Files.deleteIfExists(old);
			}
			return true;
		} catch (IOException | RuntimeException e) {
			this.emit(new StorageEvent(StorageEvent.Kind.BACKUP_FAILED, file, "could not back up " + file.getFileName() + " (" + e.getMessage() + ")"));
			return false;
		}
	}

	private Instant stampOf(Path file, Path backup) {
		Matcher m = backupPattern(file).matcher(backup.getFileName().toString());
		if (!m.matches()) {
			return null;
		}
		try {
			return LocalDateTime.parse(m.group(1), STAMP).atZone(this.clock.getZone()).toInstant();
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static Pattern backupPattern(Path file) {
		return Pattern.compile(Pattern.quote(file.getFileName().toString()) + "\\.(\\d{8}-\\d{6})");
	}

	// ---------------------------------------------------------------- helpers

	private void emit(StorageEvent event) {
		try {
			this.listener.accept(event);
		} catch (RuntimeException ignored) {
			// a broken listener must not break storage
		}
	}

	private static void checkKind(String kind) {
		if (kind == null || !KIND.matcher(kind).matches()) {
			throw new IllegalArgumentException("kind must be letters, digits, '_', '.' or '-': " + kind);
		}
	}

	private static boolean startsWith(byte[] data, String ascii) {
		if (data.length < ascii.length()) {
			return false;
		}
		for (int i = 0; i < ascii.length(); i++) {
			if (data[i] != ascii.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static boolean startsWithBom(byte[] data) {
		return data.length >= 3 && (data[0] & 0xff) == 0xEF && (data[1] & 0xff) == 0xBB && (data[2] & 0xff) == 0xBF;
	}

	private static String utf8(byte[] data, int from, int to) throws IOException {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(data, from, to - from))
				.toString();
		} catch (CharacterCodingException e) {
			throw new IOException("not UTF-8 text");
		}
	}
}
