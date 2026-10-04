package dev.voidmc.lod.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps a {@link ColumnStore} on disk, so the land a player has seen (and changed) is there again the next time
 * the world is joined, out to the horizon, before a single chunk has loaded. One compressed file per region of
 * 32 x 32 chunks ({@code r.<x>.<z>.vlod}), each chunk a few hundred bytes.
 *
 * <p>All file work runs on one background thread at minimum priority, shared by every world: a world's last
 * save is finished before the next world, or the same one joined again, reads anything. Files are written whole
 * to a temporary name and moved into place, so a crash mid-write leaves the old file. Regions are read when they
 * come within the LOD's reach (nearest first) and forgotten again once far out of it, so memory follows what can be
 * seen, not everything ever explored.
 *
 * <p>Materials are saved by name ({@link Names}), not by this session's ids, so a file outlives the registry
 * that wrote it; colours are saved as they were worked out.
 */
public final class ColumnFiles {
	/** Turns material ids into names that outlive the session, and back. */
	public interface Names {
		/** The name of material {@code id}, or null for none. */
		String name(int id);

		/** The id for a saved name, or 0 if nothing by that name exists any more. */
		int id(String name);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");
	private static final int MAGIC = 0x564C4F44;
	private static final int FORMAT = 1;
	private static final String SUFFIX = ".vlod";
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID LOD files");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final Path dir;
	private final ColumnStore store;
	private final Names names;
	// file thread only
	private Set<Long> onDisk;
	private final Set<Long> read = new HashSet<>();
	private final Map<Long, Long> saved = new HashMap<>();
	private volatile boolean closed;

	// statistics
	public final AtomicLong regionsRead = new AtomicLong();
	public final AtomicLong regionsWritten = new AtomicLong();
	public final AtomicLong bytesRead = new AtomicLong();
	public final AtomicLong bytesWritten = new AtomicLong();
	public final AtomicLong chunksWritten = new AtomicLong();
	public final AtomicLong readNanos = new AtomicLong();
	public final AtomicLong writeNanos = new AtomicLong();

	public ColumnFiles(Path dir, ColumnStore store, Names names) {
		this.dir = dir;
		this.store = store;
		this.names = names;
	}

	public Path dir() {
		return this.dir;
	}

	/** Reads every saved region within {@code radius} blocks of {@code (x, z)} not yet read, nearest first. */
	public CompletableFuture<Void> loadAround(double x, double z, double radius) {
		return CompletableFuture.runAsync(() -> {
			List<Long> wanted = new ArrayList<>();
			for (long key : this.listing()) {
				if (!this.read.contains(key) && distance(key, x, z) <= radius) {
					wanted.add(key);
				}
			}
			wanted.sort((a, b) -> Double.compare(distance(a, x, z), distance(b, x, z)));
			for (long key : wanted) {
				if (this.closed) {
					return;
				}
				this.read(key);
			}
		}, IO);
	}

	/** Writes every region that has changed since it was last written. */
	public CompletableFuture<Void> saveChanged() {
		return CompletableFuture.runAsync(this::saveAll, IO);
	}

	/** Writes what changed, then forgets the regions further than {@code radius} blocks from {@code (x, z)}. */
	public CompletableFuture<Void> unloadBeyond(double x, double z, double radius) {
		return CompletableFuture.runAsync(() -> {
			for (ColumnStore.Region r : List.copyOf(this.store.regions())) {
				long key = ColumnStore.key(r.rx, r.rz);
				if (distance(key, x, z) <= radius) {
					continue;
				}
				long changes = r.changes();
				if (changes != this.saved.getOrDefault(key, 0L)) {
					this.save(r);
				}
				if (this.store.drop(r.rx, r.rz, changes)) {
					this.read.remove(key);
					this.saved.remove(key);
				}
			}
		}, IO);
	}

	/** Saves what changed and stops reading. The future completes once everything is on disk. */
	public CompletableFuture<Void> close() {
		this.closed = true;
		return this.saveChanged();
	}

	/** Completes once all file work queued so far, for every world, is done. */
	public static CompletableFuture<Void> flush() {
		return CompletableFuture.runAsync(() -> {
		}, IO);
	}

	private static double distance(long key, double x, double z) {
		double cx = ((int) (key >> 32) + 0.5) * ColumnStore.REGION_BLOCKS;
		double cz = ((int) key + 0.5) * ColumnStore.REGION_BLOCKS;
		// to the region's nearest point, roughly: its centre less half a diagonal
		return Math.max(0, Math.hypot(cx - x, cz - z) - ColumnStore.REGION_BLOCKS * 0.71);
	}

	private Path file(long key) {
		return this.dir.resolve("r." + (int) (key >> 32) + "." + (int) key + SUFFIX);
	}

	private Set<Long> listing() {
		if (this.onDisk == null) {
			this.onDisk = new HashSet<>();
			try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir, "r.*" + SUFFIX)) {
				for (Path p : files) {
					String[] parts = p.getFileName().toString().split("\\.");
					if (parts.length == 4) {
						try {
							this.onDisk.add(ColumnStore.key(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
						} catch (NumberFormatException e) {
							// not one of ours
						}
					}
				}
			} catch (NoSuchFileException e) {
				// nothing saved yet
			} catch (IOException e) {
				LOGGER.warn("VOID LOD: could not list {}", this.dir, e);
			}
		}
		return this.onDisk;
	}

	private void read(long key) {
		this.read.add(key);
		Path file = this.file(key);
		long t0 = System.nanoTime();
		try (InputStream in = Files.newInputStream(file)) {
			long size = Files.size(file);
			ChunkColumns[] chunks = read(in, this.names, this.store, (int) (key >> 32), (int) key);
			this.store.load((int) (key >> 32), (int) key, chunks);
			this.regionsRead.incrementAndGet();
			this.bytesRead.addAndGet(size);
		} catch (NoSuchFileException e) {
			// gone since the listing
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("VOID LOD: could not read {}, it will be written anew", file, e);
		}
		this.readNanos.addAndGet(System.nanoTime() - t0);
	}

	private void saveAll() {
		for (ColumnStore.Region r : List.copyOf(this.store.regions())) {
			long key = ColumnStore.key(r.rx, r.rz);
			if (r.changes() != this.saved.getOrDefault(key, 0L)) {
				this.save(r);
			}
		}
	}

	private void save(ColumnStore.Region r) {
		long key = ColumnStore.key(r.rx, r.rz);
		if (this.listing().contains(key) && !this.read.contains(key)) {
			// what is on disk has to be in the region before the region replaces it
			this.read(key);
		}
		long changes = r.changes();
		long t0 = System.nanoTime();
		Path file = this.file(key);
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(this.dir);
			int chunks;
			try (OutputStream out = Files.newOutputStream(temp)) {
				chunks = write(r, this.names, out);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			this.saved.put(key, changes);
			this.listing().add(key);
			this.read.add(key);
			this.regionsWritten.incrementAndGet();
			this.chunksWritten.addAndGet(chunks);
			this.bytesWritten.addAndGet(Files.size(file));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("VOID LOD: could not write {}", file, e);
		}
		this.writeNanos.addAndGet(System.nanoTime() - t0);
	}

	// ---- the format ---------------------------------------------------------------------------------------

	/**
	 * Writes a region: a table of material names, a table of the surfaces its chunks use, then each chunk as its
	 * palette and its columns in planes (heights as differences from the column before, then water, leaves and
	 * surface indices), all deflated. Returns how many chunks it wrote.
	 */
	static int write(ColumnStore.Region region, Names names, OutputStream raw) throws IOException {
		int n = ColumnStore.REGION_CHUNKS * ColumnStore.REGION_CHUNKS;
		ChunkColumns[] chunks = new ChunkColumns[n];
		Map<Surface, Integer> surfaces = new IdentityHashMap<>();
		List<Surface> surfaceList = new ArrayList<>();
		Map<Integer, Integer> materials = new HashMap<>();
		List<String> materialNames = new ArrayList<>();
		int count = 0;
		for (int i = 0; i < n; i++) {
			ChunkColumns c = region.chunk(i);
			if (c == null) {
				continue;
			}
			chunks[i] = c;
			count++;
			for (Surface s : c.palette) {
				if (surfaces.putIfAbsent(s, surfaceList.size()) == null) {
					surfaceList.add(s);
					for (int m : new int[]{s.topMaterial(), s.sideMaterial(), s.soilMaterial(), s.rockMaterial(), s.leavesMaterial()}) {
						if (m != 0 && !materials.containsKey(m)) {
							String name = names.name(m);
							if (name != null) {
								materials.put(m, materialNames.size() + 1);
								materialNames.add(name);
							}
						}
					}
				}
			}
		}
		Deflater deflater = new Deflater(6);
		try {
			DeflaterOutputStream zip = new DeflaterOutputStream(raw, deflater, 8192);
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zip, 16384));
			out.writeInt(MAGIC);
			out.writeInt(FORMAT);
			out.writeInt(region.rx);
			out.writeInt(region.rz);
			varint(out, materialNames.size());
			for (String name : materialNames) {
				out.writeUTF(name);
			}
			varint(out, surfaceList.size());
			for (Surface s : surfaceList) {
				for (int color : new int[]{s.top(), s.side(), s.soil(), s.rock(), s.leaves(), s.waterColor()}) {
					out.writeByte(color >> 16);
					out.writeByte(color >> 8);
					out.writeByte(color);
				}
				out.writeByte(s.soilDepth());
				out.writeByte((s.lava() ? 1 : 0) | (s.conifer() ? 2 : 0));
				for (int m : new int[]{s.topMaterial(), s.sideMaterial(), s.soilMaterial(), s.rockMaterial(), s.leavesMaterial()}) {
					varint(out, materials.getOrDefault(m, 0));
				}
			}
			varint(out, count);
			for (int i = 0; i < n; i++) {
				ChunkColumns c = chunks[i];
				if (c == null) {
					continue;
				}
				out.writeShort(i);
				out.writeByte(c.palette.length);
				for (Surface s : c.palette) {
					varint(out, surfaces.get(s));
				}
				int last = 0;
				for (int packed : c.columns) {
					int g = packed & 0xFFF;
					out.writeShort(g - last);
					last = g;
				}
				for (int packed : c.columns) {
					out.writeByte(ChunkColumns.depth(packed));
				}
				for (int packed : c.columns) {
					out.writeByte(ChunkColumns.canopy(packed));
				}
				for (int packed : c.columns) {
					out.writeByte(ChunkColumns.look(packed));
				}
			}
			out.flush();
			zip.finish();
		} finally {
			deflater.end();
		}
		return count;
	}

	/** Reads a region written by {@link #write}: its chunks by index, nulls where none were saved. */
	static ChunkColumns[] read(InputStream raw, Names names, ColumnStore store, int rx, int rz) throws IOException {
		Inflater inflater = new Inflater();
		try {
			DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, inflater, 8192), 16384));
			if (in.readInt() != MAGIC) {
				throw new IOException("not a VOID LOD region");
			}
			int format = in.readInt();
			if (format != FORMAT) {
				throw new IOException("unknown format " + format);
			}
			if (in.readInt() != rx || in.readInt() != rz) {
				throw new IOException("region file holds another region");
			}
			int[] materials = new int[varint(in) + 1];
			for (int i = 1; i < materials.length; i++) {
				materials[i] = names.id(in.readUTF());
			}
			Surface[] surfaces = new Surface[varint(in)];
			int[] colors = new int[6];
			int[] refs = new int[5];
			for (int i = 0; i < surfaces.length; i++) {
				for (int c = 0; c < 6; c++) {
					colors[c] = in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte();
				}
				int soilDepth = in.readUnsignedByte();
				int flags = in.readUnsignedByte();
				for (int m = 0; m < 5; m++) {
					int ref = varint(in);
					refs[m] = ref < materials.length ? materials[ref] : 0;
				}
				surfaces[i] = store.intern(new Surface(colors[0], colors[1], colors[2], colors[3], colors[4], colors[5], soilDepth,
					(flags & 1) != 0, (flags & 2) != 0, refs[0], refs[1], refs[2], refs[3], refs[4]));
			}
			ChunkColumns[] chunks = new ChunkColumns[ColumnStore.REGION_CHUNKS * ColumnStore.REGION_CHUNKS];
			int count = varint(in);
			int[] ground = new int[256];
			for (int k = 0; k < count; k++) {
				int index = in.readUnsignedShort();
				Surface[] palette = new Surface[in.readUnsignedByte()];
				for (int p = 0; p < palette.length; p++) {
					palette[p] = surfaces[varint(in)];
				}
				int last = 0;
				for (int c = 0; c < 256; c++) {
					last += in.readShort();
					ground[c] = last;
				}
				int[] columns = new int[256];
				for (int c = 0; c < 256; c++) {
					columns[c] = ground[c] | in.readUnsignedByte() << 12;
				}
				for (int c = 0; c < 256; c++) {
					columns[c] |= in.readUnsignedByte() << 20;
				}
				for (int c = 0; c < 256; c++) {
					int look = in.readUnsignedByte();
					if (columns[c] != ChunkColumns.VOID && look >= palette.length) {
						throw new IOException("column refers to surface " + look + " of " + palette.length);
					}
					columns[c] |= look << 26;
				}
				chunks[index] = new ChunkColumns(columns, palette);
			}
			return chunks;
		} finally {
			inflater.end();
		}
	}

	private static void varint(DataOutputStream out, int v) throws IOException {
		while ((v & ~0x7F) != 0) {
			out.writeByte(v & 0x7F | 0x80);
			v >>>= 7;
		}
		out.writeByte(v);
	}

	private static int varint(DataInputStream in) throws IOException {
		int v = 0;
		for (int shift = 0; shift < 32; shift += 7) {
			int b = in.readUnsignedByte();
			v |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return v;
			}
		}
		throw new IOException("varint too long");
	}
}
