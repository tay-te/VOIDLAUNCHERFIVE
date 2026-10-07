package dev.voidmc.atlas.core.journal;

import dev.voidmc.atlas.core.store.SafeFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Where the player has been, in {@value #CELL}-block cells (the cells areas are made of), per dimension: the part of
 * each area the player has explored themselves (walked, flown, sailed within {@value #REACH} blocks of), as against
 * the part merely seen on the map. Kept in its own crash-safe file ({@value #KIND}). Thread-safe.
 */
public final class VisitedCells {
	public static final String KIND = "visited";
	public static final int VERSION = 1;
	public static final int CELL = 8;
	/** How near the player must come for a cell to count as explored. */
	public static final int REACH = 32;

	private final Map<String, Set<Long>> byDimension = new ConcurrentHashMap<>();
	private final Object saveLock = new Object();
	private volatile long version;
	private volatile long savedVersion;

	public static long pack(int cx, int cz) {
		return (long) cx << 32 | cz & 0xFFFFFFFFL;
	}

	public static int cellX(long packed) {
		return (int) (packed >> 32);
	}

	public static int cellZ(long packed) {
		return (int) packed;
	}

	/** Marks the cells within {@value #REACH} blocks of a position; returns how many were new. */
	public int visit(String dimension, int x, int z) {
		Set<Long> cells = this.byDimension.computeIfAbsent(dimension, d -> ConcurrentHashMap.newKeySet());
		int r = REACH / CELL;
		int cx = Math.floorDiv(x, CELL);
		int cz = Math.floorDiv(z, CELL);
		int added = 0;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (dx * dx + dz * dz <= r * r && cells.add(pack(cx + dx, cz + dz))) {
					added++;
				}
			}
		}
		if (added > 0) {
			this.version++;
		}
		return added;
	}

	public boolean visited(String dimension, int x, int z) {
		Set<Long> cells = this.byDimension.get(dimension);
		return cells != null && cells.contains(pack(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
	}

	public int count(String dimension) {
		Set<Long> cells = this.byDimension.get(dimension);
		return cells == null ? 0 : cells.size();
	}

	/** Every visited cell of a dimension (packed; see {@link #cellX}, {@link #cellZ}). */
	public void forEach(String dimension, LongConsumer action) {
		Set<Long> cells = this.byDimension.get(dimension);
		if (cells != null) {
			for (Long c : cells) {
				action.accept(c);
			}
		}
	}

	public boolean dirty() {
		return this.version != this.savedVersion;
	}

	// ---------------------------------------------------------------- persistence

	/** One line a dimension: its id, a tab, and its cells as base64 of big-endian (cx, cz) int pairs. */
	public String toPayload() {
		StringBuilder b = new StringBuilder();
		for (var e : new HashMap<>(this.byDimension).entrySet()) {
			Long[] cells = e.getValue().toArray(new Long[0]);
			ByteBuffer buf = ByteBuffer.allocate(cells.length * 8);
			for (Long c : cells) {
				buf.putLong(c);
			}
			b.append(e.getKey()).append('\t').append(Base64.getEncoder().encodeToString(buf.array())).append('\n');
		}
		return b.toString();
	}

	public static VisitedCells fromPayload(String payload) {
		VisitedCells v = new VisitedCells();
		for (String line : payload.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			int tab = line.indexOf('\t');
			if (tab <= 0) {
				throw new IllegalArgumentException("not a visited-cells line");
			}
			byte[] data = Base64.getDecoder().decode(line.substring(tab + 1).strip());
			if (data.length % 8 != 0) {
				throw new IllegalArgumentException("cut-short cells");
			}
			Set<Long> cells = v.byDimension.computeIfAbsent(line.substring(0, tab), d -> ConcurrentHashMap.newKeySet());
			ByteBuffer buf = ByteBuffer.wrap(data);
			while (buf.hasRemaining()) {
				cells.add(buf.getLong());
			}
		}
		return v;
	}

	public void save(SafeFile files, Path file) throws IOException {
		synchronized (this.saveLock) {
			long saving = this.version;
			files.write(file, KIND, VERSION, this.toPayload());
			this.savedVersion = saving;
		}
	}

	/** Reads the file (a damaged one is restored from a backup by SafeFile); none, or nothing readable: empty. */
	public static VisitedCells load(SafeFile files, Path file) {
		VisitedCells[] parsed = new VisitedCells[1];
		SafeFile.Result r = files.read(file, KIND, VERSION, s -> {
			try {
				parsed[0] = fromPayload(s);
				return true;
			} catch (RuntimeException e) {
				return false;
			}
		});
		return r.present() && parsed[0] != null ? parsed[0] : new VisitedCells();
	}
}
