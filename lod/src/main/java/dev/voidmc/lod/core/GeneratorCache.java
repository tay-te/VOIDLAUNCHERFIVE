package dev.voidmc.lod.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * The generator's answers, kept: every sample a tile asked of the world generator, a page per tile (its border
 * included), saved in the world's folder, so the horizon of a world visited before is built from disk instead of
 * the generator. Sampling is nearly all of a tile's build, and on a long
 * horizon the generator has to work out hundreds of Expanse's plates first; read back, a tile costs its meshing.
 *
 * <p>It wraps the generator alone, under the chunks the client has seen ({@link IngestSource}), so what it keeps is
 * exactly what the generator answers, before natural variation ({@link Samples}). Pages are stamped with the
 * generator's identity (the seed, the mods' versions, the resource packs that colour the land): a page from another
 * generator is dropped, not used. Materials are saved by name ({@link ColumnFiles.Names}), as the seen land is.
 *
 * <p>Thread-safe without locks: a worker reads its tile's page when it begins the tile ({@link #beginTile}), fills
 * what is missing as the tile asks, and hands it to the file thread when it ends; no page is shared between threads.
 * A tile's border overlaps its neighbours', so a few hundred samples per tile are kept twice: one read per tile.
 */
public final class GeneratorCache implements TerrainSource {
	private static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");
	private static final int MAGIC = 0x56474E43;
	private static final int FORMAT = 4;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID LOD generator cache");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** Ints a sample keeps: its colours, depths, water, materials (11 to 15) and biome (16). */
	static final int INTS = 17;

	/** One tile's page: its grid of samples, border included, those asked for so far. */
	static final class Page {
		final long key;
		final int size;
		final int count;
		final boolean[] known;
		final float[] f;
		final int[] i;
		final byte[] flags;
		/** Per sample: which of {@link #colors} its bands are (-1 none), and which of {@link #materials} their materials. */
		final short[] bands;
		final short[] bandMaterials;
		final List<int[]> colors = new ArrayList<>();
		final List<int[]> materials = new ArrayList<>();
		boolean dirty;

		Page(long key, int size) {
			this.key = key;
			this.size = size;
			this.count = size * size;
			this.known = new boolean[this.count];
			this.f = new float[this.count * 7];
			this.i = new int[this.count * INTS];
			this.flags = new byte[this.count];
			this.bands = new short[this.count];
			this.bandMaterials = new short[this.count];
			Arrays.fill(this.bands, (short) -1);
			Arrays.fill(this.bandMaterials, (short) -1);
		}

		/** The index of {@code table} in {@code tables}, added if new (bands arrays are shared, few per page). */
		static short table(List<int[]> tables, int[] table) {
			if (table == null) {
				return -1;
			}
			for (int t = 0; t < tables.size(); t++) {
				int[] have = tables.get(t);
				if (have == table || Arrays.equals(have, table)) {
					return (short) t;
				}
			}
			tables.add(table);
			return (short) (tables.size() - 1);
		}

		void put(int s, ColumnSample c) {
			float[] f = this.f;
			int fo = s * 7;
			f[fo] = c.ground;
			f[fo + 1] = c.bottom;
			f[fo + 2] = c.canopy;
			f[fo + 3] = c.cover;
			f[fo + 4] = c.crown;
			f[fo + 5] = c.conifer;
			f[fo + 6] = c.light;
			int[] i = this.i;
			int io = s * INTS;
			i[io] = c.top;
			i[io + 1] = c.side;
			i[io + 2] = c.soil;
			i[io + 3] = c.soilDepth;
			i[io + 4] = c.rock;
			i[io + 5] = c.bandHeight;
			i[io + 6] = c.bandOffset;
			i[io + 7] = c.water;
			i[io + 8] = c.waterColor;
			i[io + 9] = c.leaves;
			i[io + 10] = c.leavesTop;
			i[io + 11] = c.topMaterial;
			i[io + 12] = c.sideMaterial;
			i[io + 13] = c.soilMaterial;
			i[io + 14] = c.rockMaterial;
			i[io + 15] = c.leavesMaterial;
			i[io + 16] = c.biome;
			this.flags[s] = (byte) ((c.lava ? 1 : 0) | (c.empty ? 2 : 0));
			this.bands[s] = table(this.colors, c.bands);
			this.bandMaterials[s] = table(this.materials, c.bands == null ? null : c.bandMaterials);
			this.known[s] = true;
			this.dirty = true;
		}

		void get(int s, ColumnSample c) {
			float[] f = this.f;
			int fo = s * 7;
			c.ground = f[fo];
			c.bottom = f[fo + 1];
			c.canopy = f[fo + 2];
			c.cover = f[fo + 3];
			c.crown = f[fo + 4];
			c.conifer = f[fo + 5];
			c.light = f[fo + 6];
			int[] i = this.i;
			int io = s * INTS;
			c.top = i[io];
			c.side = i[io + 1];
			c.soil = i[io + 2];
			c.soilDepth = i[io + 3];
			c.rock = i[io + 4];
			c.bandHeight = i[io + 5];
			c.bandOffset = i[io + 6];
			c.water = i[io + 7];
			c.waterColor = i[io + 8];
			c.leaves = i[io + 9];
			c.leavesTop = i[io + 10];
			c.topMaterial = i[io + 11];
			c.sideMaterial = i[io + 12];
			c.soilMaterial = i[io + 13];
			c.rockMaterial = i[io + 14];
			c.leavesMaterial = i[io + 15];
			c.biome = i[io + 16];
			c.lava = (this.flags[s] & 1) != 0;
			c.empty = (this.flags[s] & 2) != 0;
			c.bands = this.bands[s] < 0 ? null : this.colors.get(this.bands[s]);
			c.bandMaterials = this.bandMaterials[s] < 0 ? null : this.materials.get(this.bandMaterials[s]);
		}
	}

	private final TerrainSource generator;
	private final Path dir;
	private final ColumnFiles.Names names;
	private final long identity;
	/** The tile each worker is sampling: its page and where its grid stands. */
	private static final class Current {
		Page page;
		int x0;
		int z0;
		int cell;
		int border;
		/** An answer was not known yet (a server's): the page is not saved, and asked again next time. */
		boolean incomplete;
	}

	private final ThreadLocal<Current> current = ThreadLocal.withInitial(Current::new);
	private volatile boolean closed;

	// statistics
	public final AtomicLong hits = new AtomicLong();
	public final AtomicLong misses = new AtomicLong();
	public final AtomicLong pagesRead = new AtomicLong();
	public final AtomicLong pagesWritten = new AtomicLong();

	/**
	 * @param dir where the pages are kept (null: memory only)
	 * @param identity what the generator is (its seed, the mods, the resource packs): pages of any other are dropped
	 */
	public GeneratorCache(TerrainSource generator, Path dir, ColumnFiles.Names names, long identity) {
		this.generator = generator;
		this.dir = dir;
		this.names = names;
		this.identity = identity;
	}

	public TerrainSource generator() {
		return this.generator;
	}

	@Override
	public long version() {
		return this.generator.version();
	}

	/** What the generator covers, and any tile whose page is on disk: a server's land seen before needs no asking. */
	@Override
	public boolean covers(int minX, int minZ, int maxX, int maxZ) {
		if (this.dir != null && !this.closed) {
			int span = maxX - minX;
			int level = Integer.numberOfTrailingZeros(span / TileKey.TILE);
			long key = TileKey.of(level, Math.floorDiv(minX, span), Math.floorDiv(minZ, span));
			// blocky tiles sample 66 a side, smooth ones 69
			if (Files.exists(this.file(key, TileKey.TILE + 2)) || Files.exists(this.file(key, TileKey.TILE + 5))) {
				return true;
			}
		}
		return this.generator.covers(minX, minZ, maxX, maxZ);
	}

	@Override
	public Decorator decorator() {
		return this.generator.decorator();
	}

	@Override
	public boolean keepsAnswers() {
		return !this.closed;
	}

	@Override
	public void beginTile(long key, int x0, int z0, int cell, int border, int size) {
		this.generator.beginTile(key, x0, z0, cell, border, size);
		Current c = this.current.get();
		c.page = null;
		c.incomplete = false;
		if (this.closed) {
			return;
		}
		Page page = this.read(key, size);
		c.page = page != null ? page : new Page(key, size);
		c.x0 = x0;
		c.z0 = z0;
		c.cell = cell;
		c.border = border;
	}

	@Override
	public void endTile() {
		this.generator.endTile();
		Current c = this.current.get();
		Page page = c.page;
		c.page = null;
		if (page != null && page.dirty && !c.incomplete) {
			this.save(page);
		}
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		Current c = this.current.get();
		Page page = c.page;
		int s = -1;
		if (page != null && cell == c.cell) {
			int dx = x - c.x0;
			int dz = z - c.z0;
			if (dx % cell == 0 && dz % cell == 0) {
				int i = dx / cell + c.border;
				int j = dz / cell + c.border;
				if (i >= 0 && j >= 0 && i < page.size && j < page.size) {
					s = i + j * page.size;
				}
			}
		}
		if (s < 0) {
			// not on the tile's grid: asked uncached
			this.generator.sample(x, z, cell, out);
			return;
		}
		if (page.known[s]) {
			page.get(s, out);
			this.hits.incrementAndGet();
			return;
		}
		this.misses.incrementAndGet();
		this.generator.sample(x, z, cell, out);
		if (out.empty && this.generator.partial()) {
			c.incomplete = true;
			return;
		}
		page.put(s, out);
	}

	/** A tile's page file: by level, then the tile, and its grid's size (corners and centres differ). */
	private Path file(long key, int size) {
		return this.dir.resolve("l" + TileKey.level(key)).resolve(TileKey.tx(key) + "." + TileKey.tz(key) + "." + size + ".vgen");
	}

	private Page read(long key, int size) {
		if (this.dir == null) {
			return null;
		}
		Path file = this.file(key, size);
		try (InputStream in = Files.newInputStream(file)) {
			Page page = read(in, key, size, this.identity, this.names);
			if (page != null) {
				this.pagesRead.incrementAndGet();
				// marked as used, for the trim (Windows rarely keeps access times)
				IO.execute(() -> {
					try {
						Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
					} catch (IOException ignored) {
						// a page that cannot be touched is only trimmed a little sooner
					}
				});
			}
			return page;
		} catch (NoSuchFileException e) {
			return null;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("VOID LOD: could not read the generator's page {}; sampling it again", file, e);
			return null;
		}
	}

	/** Saves {@code page} on the file thread: the worker that filled it has let it go. */
	private void save(Page page) {
		if (this.dir == null || this.closed) {
			return;
		}
		IO.execute(() -> {
			Path file = this.file(page.key, page.size);
			try {
				Files.createDirectories(file.getParent());
				Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
				try (OutputStream out = Files.newOutputStream(tmp)) {
					write(page, this.identity, this.names, out);
				}
				try {
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
				}
				this.pagesWritten.incrementAndGet();
			} catch (IOException e) {
				LOGGER.warn("VOID LOD: could not save the generator's page {}", file, e);
			}
		});
	}

	/**
	 * Stops keeping answers; done once the file thread has written every page handed to it, and trimmed the pages to
	 * {@value #MAX_BYTES} bytes, the least recently read or written going first: they are only the generator's answers,
	 * worked out again if that land is ever seen from afar again.
	 */
	public CompletableFuture<Void> close() {
		this.closed = true;
		return CompletableFuture.runAsync(this::trim, IO);
	}

	/** How much of the disk the pages may take in one world's folder: a 32 km horizon is about 12 MB. */
	static final long MAX_BYTES = 256L << 20;

	private void trim() {
		if (this.dir == null || !Files.isDirectory(this.dir)) {
			return;
		}
		try (var walk = Files.walk(this.dir)) {
			List<Path> files = new ArrayList<>(walk.filter(p -> p.toString().endsWith(".vgen")).toList());
			long total = 0;
			Map<Path, Long> sizes = new HashMap<>();
			Map<Path, Long> times = new HashMap<>();
			for (Path p : files) {
				var attributes = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
				sizes.put(p, attributes.size());
				times.put(p, attributes.lastModifiedTime().toMillis());
				total += attributes.size();
			}
			if (total <= MAX_BYTES) {
				return;
			}
			files.sort(java.util.Comparator.comparingLong(times::get));
			for (Path p : files) {
				if (total <= MAX_BYTES * 3 / 4) {
					break;
				}
				Files.deleteIfExists(p);
				total -= sizes.get(p);
			}
		} catch (IOException e) {
			LOGGER.warn("VOID LOD: could not trim the generator's pages in {}", this.dir, e);
		}
	}

	public String stats() {
		long h = this.hits.get(), m = this.misses.get();
		return String.format(java.util.Locale.ROOT, "generator cache %.0f%% of %,d samples from it, %d pages read, %d written", h + m == 0 ? 0 : 100.0 * h / (h + m),
			h + m, this.pagesRead.get(), this.pagesWritten.get());
	}

	// ---- the format ---------------------------------------------------------------------------------------
	// A page is one deflated block, little-endian: a header (magic, format, identity, tile, grid size, material names,
	// band tables), then each field for every sample in turn (planes deflate far better than samples one by one), so
	// both ways are bulk copies rather than a call per value.

	/** Bytes a sample takes in the planes: its flags, 7 floats, {@value #INTS} ints and 2 shorts. */
	private static final int SAMPLE_BYTES = 1 + 7 * 4 + INTS * 4 + 2 * 2;

	static void write(Page page, long identity, ColumnFiles.Names names, OutputStream raw) throws IOException {
		// material ids by name, numbered in the order met (0: none)
		Map<Integer, Integer> refs = new HashMap<>();
		List<String> materialNames = new ArrayList<>();
		java.util.function.IntUnaryOperator ref = id -> {
			if (id == 0) {
				return 0;
			}
			Integer r = refs.get(id);
			if (r == null) {
				String name = names.name(id);
				if (name == null) {
					return 0;
				}
				materialNames.add(name);
				r = materialNames.size();
				refs.put(id, r);
			}
			return r;
		};
		int n = page.count;
		int[] ints = page.i;
		int[] materialRefs = new int[n * 5];
		for (int s = 0; s < n; s++) {
			for (int m = 0; m < 5; m++) {
				materialRefs[s * 5 + m] = ref.applyAsInt(ints[s * INTS + 11 + m]);
			}
		}
		// biomes by name too, numbered from 1 in the order met (0: none)
		Map<Integer, Integer> biomeRefs = new HashMap<>();
		List<String> biomeNames = new ArrayList<>();
		int[] biomes = new int[n];
		for (int s = 0; s < n; s++) {
			int id = ints[s * INTS + 16];
			String name = BiomeIds.name(id);
			if (name == null) {
				continue;
			}
			Integer r = biomeRefs.get(id);
			if (r == null) {
				biomeNames.add(name);
				r = biomeNames.size();
				biomeRefs.put(id, r);
			}
			biomes[s] = r;
		}
		List<int[]> materialTables = new ArrayList<>();
		for (int[] table : page.materials) {
			int[] mapped = table.clone();
			for (int k = 0; k < mapped.length; k++) {
				mapped[k] = ref.applyAsInt(mapped[k]);
			}
			materialTables.add(mapped);
		}
		java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream();
		DataOutputStream h = new DataOutputStream(header);
		h.writeInt(MAGIC);
		h.writeInt(FORMAT);
		h.writeLong(identity);
		h.writeLong(page.key);
		h.writeInt(page.size);
		h.writeShort(materialNames.size());
		for (String name : materialNames) {
			h.writeUTF(name);
		}
		h.writeShort(biomeNames.size());
		for (String name : biomeNames) {
			h.writeUTF(name);
		}
		for (List<int[]> tables : List.of(page.colors, materialTables)) {
			h.writeShort(tables.size());
			for (int[] table : tables) {
				h.writeShort(table.length);
				for (int v : table) {
					h.writeInt(v);
				}
			}
		}
		h.flush();
		java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(n * SAMPLE_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		for (int s = 0; s < n; s++) {
			b.put((byte) (page.known[s] ? page.flags[s] | 4 : 0));
		}
		for (int k = 0; k < 7; k++) {
			for (int s = 0; s < n; s++) {
				b.putFloat(page.f[s * 7 + k]);
			}
		}
		for (int k = 0; k < INTS; k++) {
			for (int s = 0; s < n; s++) {
				b.putInt(k == 16 ? biomes[s] : k >= 11 ? materialRefs[s * 5 + k - 11] : ints[s * INTS + k]);
			}
		}
		for (int s = 0; s < n; s++) {
			b.putShort(page.bands[s]);
		}
		for (int s = 0; s < n; s++) {
			b.putShort(page.bandMaterials[s]);
		}
		// fast compression: the file thread writes hundreds of pages while a horizon builds
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try (DeflaterOutputStream zip = new DeflaterOutputStream(raw, deflater, 32768)) {
			DataOutputStream out = new DataOutputStream(zip);
			out.writeInt(header.size());
			header.writeTo(out);
			out.write(b.array());
			out.flush();
			zip.finish();
		} finally {
			deflater.end();
		}
	}

	/** A page written by {@link #write}, or null if it is another generator's or another page's. */
	static Page read(InputStream raw, long key, int size, long identity, ColumnFiles.Names names) throws IOException {
		Inflater inflater = new Inflater();
		byte[] all;
		try (InflaterInputStream in = new InflaterInputStream(raw, inflater, 32768)) {
			all = in.readAllBytes();
		} finally {
			inflater.end();
		}
		java.nio.ByteBuffer whole = java.nio.ByteBuffer.wrap(all);
		int headerSize = whole.getInt();
		DataInputStream h = new DataInputStream(new java.io.ByteArrayInputStream(all, 4, headerSize));
		if (h.readInt() != MAGIC || h.readInt() != FORMAT || h.readLong() != identity || h.readLong() != key || h.readInt() != size) {
			return null;
		}
		int[] materials = new int[h.readUnsignedShort() + 1];
		for (int m = 1; m < materials.length; m++) {
			materials[m] = names.id(h.readUTF());
		}
		int[] biomes = new int[h.readUnsignedShort() + 1];
		biomes[0] = -1;
		for (int m = 1; m < biomes.length; m++) {
			biomes[m] = BiomeIds.id(h.readUTF());
		}
		Page page = new Page(key, size);
		for (int list = 0; list < 2; list++) {
			int count = h.readUnsignedShort();
			for (int t = 0; t < count; t++) {
				int[] table = new int[h.readUnsignedShort()];
				for (int k = 0; k < table.length; k++) {
					int v = h.readInt();
					table[k] = list == 0 ? v : v > 0 && v < materials.length ? materials[v] : 0;
				}
				(list == 0 ? page.colors : page.materials).add(table);
			}
		}
		int n = page.count;
		java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(all, 4 + headerSize, n * SAMPLE_BYTES).slice().order(java.nio.ByteOrder.LITTLE_ENDIAN);
		for (int s = 0; s < n; s++) {
			int v = b.get();
			page.known[s] = (v & 4) != 0;
			page.flags[s] = (byte) (v & 3);
		}
		java.nio.FloatBuffer floats = b.slice(n, n * 7 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
		for (int k = 0; k < 7; k++) {
			for (int s = 0; s < n; s++) {
				page.f[s * 7 + k] = floats.get();
			}
		}
		java.nio.IntBuffer ints = b.slice(n + n * 7 * 4, n * INTS * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		for (int k = 0; k < INTS; k++) {
			for (int s = 0; s < n; s++) {
				int v = ints.get();
				page.i[s * INTS + k] = k == 16 ? (v > 0 && v < biomes.length ? biomes[v] : -1)
					: k >= 11 ? (v > 0 && v < materials.length ? materials[v] : 0) : v;
			}
		}
		java.nio.ShortBuffer shorts = b.slice(n + n * (7 + INTS) * 4, n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer();
		shorts.get(page.bands);
		shorts.get(page.bandMaterials);
		return page;
	}
}
