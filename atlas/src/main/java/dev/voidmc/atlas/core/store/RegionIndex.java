package dev.voidmc.atlas.core.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * For every region of a map on disk: when it was last on screen and how many bytes its files take. This is what
 * {@link DiskCap} needs to choose what to delete when a map outgrows its disk budget: the regions nobody has looked at
 * for longest go first.
 *
 * <p>Saved through {@link SafeFile} as compact text, one region per line:
 * <pre>surface -3 7 1759680000000 412345</pre>
 * (layer, region x, region z, last viewed in epoch millis, bytes). Thread-safe: the map screen marks regions viewed
 * while the saver writes them.
 */
public final class RegionIndex {
	public static final String KIND = "regions";
	public static final int VERSION = 1;
	private static final Pattern LAYER = Pattern.compile("\\S+");
	private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::layer).thenComparingInt(Entry::rx).thenComparingInt(Entry::rz);

	/** One region of one layer. {@code lastViewed} is epoch millis; {@code bytes} its size on disk. */
	public record Entry(String layer, int rx, int rz, long lastViewed, long bytes) {
		public Entry {
			checkLayer(layer);
			if (bytes < 0) {
				throw new IllegalArgumentException("bytes " + bytes);
			}
		}
	}

	private record Key(String layer, int rx, int rz) {
	}

	private final Map<Key, Entry> entries = new HashMap<>();
	private long version;

	/** Marks a region as seen on screen at {@code now}. A region not yet indexed is added with size 0. */
	public synchronized void viewed(String layer, int rx, int rz, long now) {
		Key key = new Key(layer, rx, rz);
		Entry old = this.entries.get(key);
		long bytes = old == null ? 0 : old.bytes();
		long when = old == null ? now : Math.max(old.lastViewed(), now);
		this.entries.put(key, new Entry(layer, rx, rz, when, bytes));
		this.version++;
	}

	/**
	 * Records a region's size on disk after a save. A region not yet indexed counts as viewed at {@code now}: it was
	 * just mapped, around the player.
	 */
	public synchronized void sized(String layer, int rx, int rz, long bytes, long now) {
		Key key = new Key(layer, rx, rz);
		Entry old = this.entries.get(key);
		this.entries.put(key, new Entry(layer, rx, rz, old == null ? now : old.lastViewed(), bytes));
		this.version++;
	}

	/** Adds or replaces an entry as it is. */
	public synchronized void put(Entry entry) {
		this.entries.put(new Key(entry.layer(), entry.rx(), entry.rz()), entry);
		this.version++;
	}

	public synchronized Entry get(String layer, int rx, int rz) {
		return this.entries.get(new Key(layer, rx, rz));
	}

	public synchronized boolean remove(String layer, int rx, int rz) {
		boolean removed = this.entries.remove(new Key(layer, rx, rz)) != null;
		if (removed) {
			this.version++;
		}
		return removed;
	}

	/** Every entry, sorted by layer, then x, then z. */
	public synchronized List<Entry> entries() {
		List<Entry> out = new ArrayList<>(this.entries.values());
		out.sort(ORDER);
		return out;
	}

	public synchronized int size() {
		return this.entries.size();
	}

	public synchronized long totalBytes() {
		long total = 0;
		for (Entry e : this.entries.values()) {
			total += e.bytes();
		}
		return total;
	}

	/** Bumped by every change, so a saver can tell whether there is anything to save. */
	public synchronized long version() {
		return this.version;
	}

	/** The index as text, one region per line, in a stable order. */
	public String encode() {
		StringBuilder out = new StringBuilder();
		for (Entry e : this.entries()) {
			out.append(e.layer()).append(' ').append(e.rx()).append(' ').append(e.rz()).append(' ')
				.append(e.lastViewed()).append(' ').append(e.bytes()).append('\n');
		}
		return out.toString();
	}

	/** Reads {@link #encode}'s text, with any line endings. Blank lines are skipped; any malformed line is an error. */
	public static RegionIndex decode(String text) {
		RegionIndex index = new RegionIndex();
		int lineNo = 0;
		for (String line : text.split("\\R", -1)) {
			lineNo++;
			String trimmed = line.strip();
			if (trimmed.isEmpty()) {
				continue;
			}
			String[] parts = trimmed.split(" +");
			if (parts.length != 5) {
				throw new IllegalArgumentException("line " + lineNo + ": expected 5 fields, found " + parts.length);
			}
			try {
				index.put(new Entry(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Long.parseLong(parts[3]), Long.parseLong(parts[4])));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("line " + lineNo + ": " + e.getMessage());
			}
		}
		index.version = 0;
		return index;
	}

	/** Saves through {@code files} as a {@value #KIND} file. */
	public void save(SafeFile files, Path file) throws IOException {
		files.write(file, KIND, VERSION, this.encode());
	}

	/**
	 * Loads through {@code files}: a damaged index is moved aside and its newest good backup used; with none, the
	 * index starts empty (the caller rebuilds sizes from the files on disk; view times start over).
	 */
	public static RegionIndex load(SafeFile files, Path file) {
		SafeFile.Result result = files.read(file, KIND, VERSION, text -> {
			try {
				decode(text);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		});
		return result.present() ? decode(result.payload()) : new RegionIndex();
	}

	private static void checkLayer(String layer) {
		if (layer == null || !LAYER.matcher(layer).matches()) {
			throw new IllegalArgumentException("layer must be one word: " + layer);
		}
	}
}
