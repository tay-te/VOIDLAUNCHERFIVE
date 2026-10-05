package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.client.BlockLooks;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.Palette;
import dev.voidmc.atlas.core.TileRenderer;
import dev.voidmc.atlas.core.WaypointStore;
import dev.voidmc.atlas.core.detect.Sighting;
import dev.voidmc.atlas.core.places.PlaceBook;
import dev.voidmc.atlas.core.under.Reveal;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything Atlas keeps for one world (a singleplayer save, or a server): its folder, the block and biome names its
 * region files use, its waypoints and places ({@link PlaceBook}), its dimensions' maps and landmarks, and the worker
 * threads that read chunks, reveal the underground, analyse the land and draw tiles. Made when a world is joined, closed
 * (and saved) when it is left.
 */
public final class MapSession {
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Atlas");

	public final String worldKey;
	public final Path dir;
	public final NamePalette blockNames;
	public final NamePalette biomeNames;
	public final WaypointStore waypoints;
	public final PlaceBook places;
	public final Landmarks landmarks;
	/** A hash of the world's seed (and key): the same every session, for names. */
	public final long seedHash;
	public final ScheduledThreadPoolExecutor workers;
	/** Saves happen here, one at a time, so a slow disk never stalls the workers. */
	public final ExecutorService io;
	private final ConcurrentHashMap<String, DimensionData> dimensions = new ConcurrentHashMap<>();
	private final AtomicInteger layerUids = new AtomicInteger();
	private volatile BlockLooks looks;
	private volatile int looksVersion;
	private final ThreadLocal<TileRenderer> renderers = new ThreadLocal<>();
	private final ThreadLocal<Integer> rendererVersion = new ThreadLocal<>();
	private final ThreadLocal<ChunkScanner> scanners;
	private final ThreadLocal<Reveal> reveals = ThreadLocal.withInitial(Reveal::new);
	private final Consumer<RegionChange> changes;
	private volatile boolean closed;
	private volatile boolean caveFluids = true;

	/** A drawn rectangle of a region changed (region pixels, level 0). */
	public record RegionChange(LayerData layer, int rx, int rz, int[] rect) {
	}

	public MapSession(String worldKey, Path dir, int threads, long seedHash, Consumer<RegionChange> changes) {
		this.worldKey = worldKey;
		this.seedHash = seedHash;
		this.dir = dir;
		this.changes = changes;
		NamePalette blocks;
		NamePalette biomes;
		try {
			blocks = NamePalette.load(dir.resolve("blocks.txt"));
			biomes = NamePalette.load(dir.resolve("biomes.txt"));
		} catch (IOException e) {
			this.warn("Atlas: could not read the name palettes in " + dir + " (" + e + "); the map of this world starts afresh");
			blocks = new NamePalette();
			biomes = new NamePalette();
		}
		this.blockNames = blocks;
		this.biomeNames = biomes;
		this.files = new dev.voidmc.atlas.core.store.SafeFile(e -> {
			this.warn("Atlas: " + e.kind() + " " + e.file() + (e.detail() == null ? "" : " (" + e.detail() + ")"));
			var listener = STORAGE_EVENTS;
			if (listener != null) {
				listener.accept(e);
			}
		});
		this.waypoints = new WaypointStore(dir.resolve("waypoints.json"), this::warn).withSafeFile(this.files);
		this.waypoints.load();
		this.places = new PlaceBook(dir.resolve("places.json"), this::warn).withSafeFile(this.files);
		this.places.load();
		this.landmarks = new Landmarks(this);
		this.scanners = ThreadLocal.withInitial(() -> new ChunkScanner(this.blockNames, this.biomeNames));
		this.workers = new ScheduledThreadPoolExecutor(Math.max(1, threads), daemon("Atlas worker", Thread.MIN_PRIORITY + 1));
		this.workers.setRemoveOnCancelPolicy(true);
		this.io = Executors.newSingleThreadExecutor(daemon("Atlas IO", Thread.NORM_PRIORITY - 1));
	}

	private static ThreadFactory daemon(String name, int priority) {
		AtomicInteger n = new AtomicInteger();
		return r -> {
			Thread t = new Thread(r, name + " " + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(priority);
			return t;
		};
	}

	/** Hears about damaged files set aside and files restored from a backup (the client turns them into islands). */
	public static volatile Consumer<dev.voidmc.atlas.core.store.StorageEvent> STORAGE_EVENTS;
	/** The files kept safe: waypoints, places, the region index. */
	public final dev.voidmc.atlas.core.store.SafeFile files;
	/** When each region was last on screen: (layer path, packed region) to epoch millis, for the disk cap. */
	private final java.util.concurrent.ConcurrentHashMap<String, Long> views = new java.util.concurrent.ConcurrentHashMap<>();
	/** The disk cap in bytes (0: none) and where the player was last, for {@link #close}. */
	public volatile long diskCapBytes;
	public volatile String lastDimension = "minecraft:overworld";
	public volatile int lastRx;
	public volatile int lastRz;

	/** A region of a layer was on screen (any thread; cheap: kept once a minute per region). */
	public void viewed(DimensionData d, String layer, int rx, int rz) {
		String key = d.id.replace(':', '/') + "/" + layer + "|" + rx + "|" + rz;
		long now = System.currentTimeMillis();
		Long before = this.views.get(key);
		if (before == null || now - before > 60_000) {
			this.views.put(key, now);
		}
	}

	/**
	 * Keeps the map under the disk cap (IO thread, at close, when nothing of it is in memory): the region index is
	 * brought up to date from the files and the views, and the least recently viewed regions far from where the player
	 * was go first (never within 4 regions of the player, never one viewed in the last ten minutes).
	 */
	private void enforceDiskCap() {
		Path indexFile = this.dir.resolve("regions.idx");
		var index = dev.voidmc.atlas.core.store.RegionIndex.load(this.files, indexFile);
		long now = System.currentTimeMillis();
		java.util.Map<String, Path[]> filesOf = new java.util.HashMap<>();
		try (var walk = java.nio.file.Files.walk(this.dir)) {
			for (Path p : (Iterable<Path>) walk::iterator) {
				String name = p.getFileName().toString();
				if (!name.startsWith("r.") || !(name.endsWith(".raw") || name.endsWith(".img") || name.endsWith(".feat"))) {
					continue;
				}
				String[] parts = name.split("\\.");
				if (parts.length < 4) {
					continue;
				}
				String layer = this.dir.relativize(p.getParent()).toString().replace('\\', '/');
				int rx;
				int rz;
				try {
					rx = Integer.parseInt(parts[1]);
					rz = Integer.parseInt(parts[2]);
				} catch (NumberFormatException e) {
					continue;
				}
				String key = layer + "|" + rx + "|" + rz;
				Path[] list = filesOf.computeIfAbsent(key, k -> new Path[3]);
				list[name.endsWith(".raw") ? 0 : name.endsWith(".img") ? 1 : 2] = p;
			}
		} catch (IOException e) {
			return;
		}
		for (var e : filesOf.entrySet()) {
			String[] k = e.getKey().split("\\|");
			long bytes = 0;
			long modified = 0;
			for (Path p : e.getValue()) {
				if (p != null) {
					try {
						bytes += java.nio.file.Files.size(p);
						modified = Math.max(modified, java.nio.file.Files.getLastModifiedTime(p).toMillis());
					} catch (IOException ignored) {
						// gone meanwhile
					}
				}
			}
			int rx = Integer.parseInt(k[1]);
			int rz = Integer.parseInt(k[2]);
			var known = index.get(k[0], rx, rz);
			index.sized(k[0], rx, rz, bytes, known == null ? modified : known.lastViewed());
		}
		for (var v : this.views.entrySet()) {
			String[] k = v.getKey().split("\\|");
			index.viewed(k[0], Integer.parseInt(k[1]), Integer.parseInt(k[2]), v.getValue());
		}
		if (this.diskCapBytes > 0) {
			String playerDim = this.lastDimension.replace(':', '/');
			var plan = dev.voidmc.atlas.core.store.DiskCap.plan(index, this.diskCapBytes,
				layer -> layer.startsWith(playerDim + "/") ? new dev.voidmc.atlas.core.store.DiskCap.Region(this.lastRx, this.lastRz) : null, 4, now);
			for (var entry : plan) {
				Path[] list = filesOf.get(entry.layer() + "|" + entry.rx() + "|" + entry.rz());
				if (list != null) {
					for (Path p : list) {
						try {
							if (p != null) {
								java.nio.file.Files.deleteIfExists(p);
							}
						} catch (IOException ignored) {
							// left for next time
						}
					}
				}
				index.remove(entry.layer(), entry.rx(), entry.rz());
			}
			if (!plan.isEmpty()) {
				LOGGER.info("Atlas: {} far regions of {} let go to stay under the disk cap", plan.size(), this.worldKey);
			}
		}
		try {
			this.files.backup(indexFile);
			index.save(this.files, indexFile);
		} catch (IOException e) {
			this.warn("Atlas: could not save the region index: " + e);
		}
	}

	public void warn(String message) {
		LOGGER.warn(message);
	}

	public void setLooks(BlockLooks looks) {
		this.looks = looks;
		this.looksVersion++;
	}

	public BlockLooks looks() {
		return this.looks;
	}

	/** The version of the palette the images are drawn with: images from another are drawn again. */
	public int paletteVersion() {
		return Palette.VERSION;
	}

	int nextLayerUid() {
		return this.layerUids.incrementAndGet() & 0xFFF;
	}

	/** This thread's tile renderer (one per worker, made again when the block looks change). */
	public TileRenderer renderer() {
		TileRenderer r = this.renderers.get();
		Integer v = this.rendererVersion.get();
		if (r == null || v == null || v != this.looksVersion) {
			r = new TileRenderer(this.looks);
			this.renderers.set(r);
			this.rendererVersion.set(this.looksVersion);
		}
		return r;
	}

	public ChunkScanner scanner() {
		return this.scanners.get();
	}

	/** This worker's underground reveal. */
	public Reveal reveal() {
		return this.reveals.get();
	}

	/** A structure seen in a chunk's blocks (worker thread). */
	public void sight(String dimension, Sighting s) {
		this.places.sight(dimension, s, System.currentTimeMillis());
	}

	public DimensionData dimension(String id, boolean ceiling) {
		DimensionData d = this.dimensions.get(id);
		if (d != null) {
			return d;
		}
		boolean[] made = {false};
		d = this.dimensions.computeIfAbsent(id, k -> {
			made[0] = true;
			return new DimensionData(this, k, this.dir.resolve(k.replace(':', '/')), ceiling);
		});
		if (made[0]) {
			this.landmarks.opened(d);
		}
		return d;
	}

	public DimensionData dimensionIfOpen(String id) {
		return this.dimensions.get(id);
	}

	public java.util.Collection<DimensionData> dimensions() {
		return this.dimensions.values();
	}

	void regionChanged(LayerData layer, int rx, int rz, int[] rect) {
		Perf.RENDERS.incrementAndGet();
		this.changes.accept(new RegionChange(layer, rx, rz, rect));
		this.landmarks.regionDrawn(layer, rx, rz);
	}

	/** Whether underground water and lava are drawn (the "Water & lava" layer). */
	public boolean caveFluids() {
		return this.caveFluids;
	}

	/** Shows or hides underground water and lava: every underground image is drawn again. Render thread. */
	public void setCaveFluids(boolean show) {
		if (show == this.caveFluids) {
			return;
		}
		this.caveFluids = show;
		for (DimensionData d : this.dimensions.values()) {
			for (LayerData l : d.layers()) {
				if (l.mode == TileRenderer.Mode.CAVE) {
					l.forgetImages();
				}
			}
		}
	}

	public boolean closed() {
		return this.closed;
	}

	/** Saves what changed and trims memory, on the IO thread. */
	public void saveSoon(boolean all) {
		if (this.closed && !all) {
			return;
		}
		this.io.execute(() -> this.saveNow(all));
	}

	private void saveNow(boolean all) {
		int saved = 0;
		for (DimensionData d : this.dimensions.values()) {
			for (LayerData l : d.layers()) {
				saved += l.saveAndTrim(all ? 0 : 20_000_000_000L, 24, 32, all);
			}
		}
		try {
			if (this.blockNames.dirty()) {
				this.blockNames.save(this.dir.resolve("blocks.txt"));
			}
			if (this.biomeNames.dirty()) {
				this.biomeNames.save(this.dir.resolve("biomes.txt"));
			}
			this.waypoints.save();
			if (this.places.dirty()) {
				this.places.save();
			}
		} catch (IOException e) {
			this.warn("Atlas: could not save " + this.dir + ": " + e);
		}
		if (Boolean.getBoolean("atlas.dumpLooks") && this.looks != null) {
			try {
				this.looks.dump(this.dir.resolve("looks.tsv"));
			} catch (IOException | RuntimeException e) {
				this.warn("Atlas: could not write looks.tsv: " + e);
			}
		}
		if (saved > 0 && Boolean.getBoolean("atlas.logStats")) {
			LOGGER.info("Atlas: saved {} region files of {}", saved, this.worldKey);
		}
	}

	/** Stops the workers and saves everything, waiting up to a few seconds. */
	public void close() {
		this.closed = true;
		this.workers.shutdownNow();
		try {
			this.workers.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		this.io.execute(() -> this.saveNow(true));
		this.io.execute(this::enforceDiskCap);
		this.io.shutdown();
		try {
			if (!this.io.awaitTermination(8, TimeUnit.SECONDS)) {
				this.warn("Atlas: saving " + this.worldKey + " is taking long; it carries on in the background");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
