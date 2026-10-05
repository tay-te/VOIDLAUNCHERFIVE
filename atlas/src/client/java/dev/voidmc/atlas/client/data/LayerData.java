package dev.voidmc.atlas.client.data;

import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionImage;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.TileRenderer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * One layer of one dimension's map ({@code surface}, {@code cave_3}, ...): its raw regions and their drawn images, in
 * memory while they are in use and on disk always. Chunk scans write columns into raw regions and mark rectangles
 * dirty; dirty rectangles are drawn a moment later (so a chunk's worth of writes is one draw) on the workers, and the
 * changed pixels are handed to the GPU tile cache.
 */
public final class LayerData {
	private static final long RENDER_DELAY_MS = 60;

	public final DimensionData dimension;
	public final String name;
	public final TileRenderer.Mode mode;
	/** A small number unique to this layer in the session, for the GPU tile keys. */
	public final int uid;

	private final ConcurrentHashMap<Long, RegionRaw> raws = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, RegionImage> images = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Long> lastUse = new ConcurrentHashMap<>();
	/** Regions that have a raw file or data: everything else is unexplored and needs no tile. */
	private final LongOpenHashSet existing = new LongOpenHashSet();
	/** Per level, the tiles with any existing region. */
	private final LongOpenHashSet[] existingTiles = new LongOpenHashSet[RegionImage.LEVELS];
	private final ConcurrentHashMap<Long, int[]> pending = new ConcurrentHashMap<>();
	/** Small levels of regions not otherwise in memory, for assembling zoomed-out tiles: (region, level) to pixels. */
	private final Map<Long, int[]> levelCache = new LinkedHashMap<>(256, 0.75F, true);
	private long levelCacheBytes;
	private static final long LEVEL_CACHE_BYTES = 48L << 20;
	private volatile long version;

	LayerData(DimensionData dimension, String name, TileRenderer.Mode mode, int uid) {
		this.dimension = dimension;
		this.name = name;
		this.mode = mode;
		this.uid = uid;
		for (int i = 0; i < this.existingTiles.length; i++) {
			this.existingTiles[i] = new LongOpenHashSet();
		}
		for (long key : dimension.files.regions(name)) {
			this.markExisting(Coords.unpackX(key), Coords.unpackZ(key));
		}
	}

	/** Bumped whenever any of the layer's pixels change. */
	public long version() {
		return this.version;
	}

	private void markExisting(int rx, int rz) {
		synchronized (this.existing) {
			if (this.existing.add(Coords.pack(rx, rz))) {
				for (int level = 0; level < this.existingTiles.length; level++) {
					this.existingTiles[level].add(Coords.pack(rx >> level, rz >> level));
				}
			}
		}
	}

	public boolean regionExists(int rx, int rz) {
		synchronized (this.existing) {
			return this.existing.contains(Coords.pack(rx, rz));
		}
	}

	/** Whether any region of a tile has ever been seen: if not, the tile need not be drawn or made. */
	public boolean tileExists(int level, int tx, int tz) {
		synchronized (this.existing) {
			return this.existingTiles[level].contains(Coords.pack(tx, tz));
		}
	}

	public int regionCount() {
		synchronized (this.existing) {
			return this.existing.size();
		}
	}

	private void touch(long key) {
		this.lastUse.put(key, System.nanoTime());
	}

	/** The raw region, loaded from disk or (with {@code create}) made empty; null if there is none and none is wanted. */
	public RegionRaw raw(int rx, int rz, boolean create) {
		long key = Coords.pack(rx, rz);
		RegionRaw raw = this.raws.get(key);
		if (raw == null) {
			raw = this.raws.computeIfAbsent(key, k -> {
				RegionRaw loaded = this.dimension.files.loadRaw(this.name, rx, rz);
				if (loaded == null && create) {
					loaded = new RegionRaw(rx, rz);
				}
				return loaded;
			});
		}
		if (raw != null) {
			this.touch(key);
			if (create) {
				this.markExisting(rx, rz);
			}
		}
		return raw;
	}

	/** The 3 x 3 block of raw regions round one, as {@link TileRenderer#render} wants them (nulls where unexplored). */
	public RegionRaw[] around(int rx, int rz) {
		RegionRaw[] around = new RegionRaw[9];
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (this.regionExists(rx + dx, rz + dz)) {
					around[(dz + 1) * 3 + dx + 1] = this.raw(rx + dx, rz + dz, false);
				}
			}
		}
		return around;
	}

	/**
	 * Marks the blocks {@code [x0, x1) x [z0, z1)} as changed: their pixels, and the two round them (the hillshade
	 * and the dithered edge reach that far), are drawn again shortly.
	 */
	public void dirty(int x0, int z0, int x1, int z1) {
		x0 -= 2;
		z0 -= 2;
		x1 += 2;
		z1 += 2;
		for (int rz = Coords.region(z0); rz <= Coords.region(z1 - 1); rz++) {
			for (int rx = Coords.region(x0); rx <= Coords.region(x1 - 1); rx++) {
				if (!this.regionExists(rx, rz)) {
					continue;
				}
				int ox = rx << Coords.REGION_SHIFT;
				int oz = rz << Coords.REGION_SHIFT;
				int[] rect = {Math.max(0, x0 - ox), Math.max(0, z0 - oz), Math.min(Coords.REGION_SIZE, x1 - ox), Math.min(Coords.REGION_SIZE, z1 - oz)};
				long key = Coords.pack(rx, rz);
				int[] before = this.pending.putIfAbsent(key, rect);
				if (before == null) {
					int frx = rx;
					int frz = rz;
					this.dimension.session.workers.schedule(() -> this.renderPending(frx, frz), RENDER_DELAY_MS, TimeUnit.MILLISECONDS);
				} else {
					this.pending.computeIfPresent(key, (k, r) -> new int[]{Math.min(r[0], rect[0]), Math.min(r[1], rect[1]), Math.max(r[2], rect[2]), Math.max(r[3], rect[3])});
				}
			}
		}
	}

	private void renderPending(int rx, int rz) {
		int[] rect = this.pending.remove(Coords.pack(rx, rz));
		if (rect == null) {
			return;
		}
		RegionImage image = this.imageForUpdate(rx, rz);
		if (image == null) {
			return;
		}
		synchronized (image) {
			this.dimension.session.renderer().render(this.around(rx, rz), image, rect[0], rect[1], rect[2], rect[3], this.mode);
		}
		this.version++;
		this.dropLevels(rx, rz);
		this.dimension.session.regionChanged(this, rx, rz, rect);
	}

	/**
	 * The full image of a region to draw into: in memory, on disk (if drawn with today's palette), or drawn afresh
	 * from its raw region. Null if the region is unexplored.
	 */
	public RegionImage imageForUpdate(int rx, int rz) {
		long key = Coords.pack(rx, rz);
		RegionImage image = this.images.get(key);
		if (image != null && image.complete()) {
			this.touch(key);
			return image;
		}
		if (!this.regionExists(rx, rz)) {
			return null;
		}
		image = this.images.computeIfAbsent(key, k -> {
			RegionImage loaded = this.dimension.files.loadImage(this.name, rx, rz, (1 << RegionImage.LEVELS) - 1, this.dimension.session.paletteVersion());
			if (loaded != null && loaded.complete()) {
				return loaded;
			}
			RegionImage fresh = new RegionImage(rx, rz, this.dimension.session.paletteVersion());
			for (int level = 0; level < RegionImage.LEVELS; level++) {
				fresh.levelOrCreate(level);
			}
			synchronized (fresh) {
				this.dimension.session.renderer().render(this.around(rx, rz), fresh, 0, 0, Coords.REGION_SIZE, Coords.REGION_SIZE, this.mode);
			}
			return fresh;
		});
		this.touch(key);
		return image;
	}

	/**
	 * A region's pixels at a level, for assembling a tile: from its full image if that is in memory, from the small
	 * levels cache, or read from disk alone (the image file keeps each level apart). Null if unexplored.
	 */
	public int[] levelPixels(int rx, int rz, int level) {
		long key = Coords.pack(rx, rz);
		RegionImage image = this.images.get(key);
		if (image != null && image.hasLevel(level)) {
			return image.level(level);
		}
		if (!this.regionExists(rx, rz)) {
			return null;
		}
		long cacheKey = key * 8 + level;
		synchronized (this.levelCache) {
			int[] cached = this.levelCache.get(cacheKey);
			if (cached != null) {
				return cached;
			}
		}
		int[] pixels;
		if (level == 0) {
			RegionImage full = this.imageForUpdate(rx, rz);
			return full == null ? null : full.level(0);
		}
		RegionImage loaded = this.dimension.files.loadImage(this.name, rx, rz, 1 << level, this.dimension.session.paletteVersion());
		if (loaded != null && loaded.hasLevel(level)) {
			pixels = loaded.level(level);
		} else {
			RegionImage full = this.imageForUpdate(rx, rz);
			if (full == null) {
				return null;
			}
			pixels = full.level(level);
		}
		synchronized (this.levelCache) {
			if (this.levelCache.put(cacheKey, pixels) == null) {
				this.levelCacheBytes += pixels.length * 4L;
			}
			var it = this.levelCache.entrySet().iterator();
			while (this.levelCacheBytes > LEVEL_CACHE_BYTES && it.hasNext()) {
				var e = it.next();
				this.levelCacheBytes -= e.getValue().length * 4L;
				it.remove();
			}
		}
		return pixels;
	}

	private void dropLevels(int rx, int rz) {
		long key = Coords.pack(rx, rz);
		synchronized (this.levelCache) {
			for (int level = 0; level < RegionImage.LEVELS; level++) {
				int[] gone = this.levelCache.remove(key * 8 + level);
				if (gone != null) {
					this.levelCacheBytes -= gone.length * 4L;
				}
			}
		}
	}

	/**
	 * Takes a whole region made elsewhere (the performance test's synthetic land, an import): draws it, writes its raw
	 * and image files, and keeps neither in memory; tiles over it are then assembled from disk like any revisited map.
	 */
	public void importRegion(RegionRaw raw) throws java.io.IOException {
		RegionImage image = new RegionImage(raw.x, raw.z, this.dimension.session.paletteVersion());
		for (int level = 0; level < RegionImage.LEVELS; level++) {
			image.levelOrCreate(level);
		}
		RegionRaw[] around = new RegionRaw[9];
		around[4] = raw;
		this.dimension.session.renderer().render(around, image, 0, 0, Coords.REGION_SIZE, Coords.REGION_SIZE, this.mode);
		this.dimension.files.saveRaw(this.name, raw);
		this.dimension.files.saveImage(this.name, image);
		this.markExisting(raw.x, raw.z);
		this.version++;
	}

	/** Every region's raw columns at a point, for the readouts: null if unexplored or not in memory. */
	public RegionRaw rawIfLoaded(int rx, int rz) {
		return this.raws.get(Coords.pack(rx, rz));
	}

	/**
	 * Saves what changed, and lets go of what has not been used for {@code idleNanos} (keeping at most
	 * {@code keepRaws} raw regions and {@code keepImages} images). Runs on the IO thread.
	 */
	public int saveAndTrim(long idleNanos, int keepRaws, int keepImages, boolean all) {
		int saved = 0;
		long now = System.nanoTime();
		for (var e : this.raws.entrySet()) {
			RegionRaw raw = e.getValue();
			if (raw.dirty()) {
				try {
					this.dimension.files.saveRaw(this.name, raw);
					saved++;
				} catch (Exception ex) {
					this.dimension.session.warn("Atlas: could not save " + this.name + " region " + raw.x + "," + raw.z + ": " + ex);
				}
			}
		}
		for (var e : this.images.entrySet()) {
			RegionImage image = e.getValue();
			if (image.dirty() && image.complete()) {
				try {
					synchronized (image) {
						this.dimension.files.saveImage(this.name, image);
					}
					saved++;
				} catch (Exception ex) {
					this.dimension.session.warn("Atlas: could not save " + this.name + " image " + image.x + "," + image.z + ": " + ex);
				}
			}
		}
		if (!all) {
			trim(this.raws, this.lastUse, now, idleNanos, keepRaws, this.pending, r -> !r.dirty());
			trim(this.images, this.lastUse, now, idleNanos, keepImages, this.pending, i -> !i.dirty());
		}
		return saved;
	}

	private static <T> void trim(ConcurrentHashMap<Long, T> map, ConcurrentHashMap<Long, Long> lastUse, long now, long idle, int keep,
		ConcurrentHashMap<Long, int[]> pending, java.util.function.Predicate<T> clean) {
		if (map.size() <= keep) {
			return;
		}
		List<Long> keys = new ArrayList<>(map.keySet());
		keys.sort((a, b) -> Long.compare(lastUse.getOrDefault(a, 0L), lastUse.getOrDefault(b, 0L)));
		int over = map.size() - keep;
		for (Long key : keys) {
			if (over <= 0) {
				break;
			}
			T value = map.get(key);
			if (value != null && !pending.containsKey(key) && now - lastUse.getOrDefault(key, 0L) > idle && clean.test(value)) {
				map.remove(key);
				over--;
			}
		}
	}

	public int rawsInMemory() {
		return this.raws.size();
	}

	public int imagesInMemory() {
		return this.images.size();
	}

	public int pendingRenders() {
		return this.pending.size();
	}
}
