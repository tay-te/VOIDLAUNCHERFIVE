package dev.voidmc.atlas.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.Palette;
import dev.voidmc.atlas.core.RegionImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.render.TextureSetup;
import org.lwjgl.system.MemoryUtil;

/**
 * The map's tiles on the GPU. Tiles (512 x 512 pixels at any mip level) live in slots of a few 2048 x 2048 atlas
 * pages, so a whole map is one draw call per page. A missing tile is assembled on the composer thread from its
 * regions' images (dithering the explored edge as it goes) and uploaded on the render thread within a time budget;
 * when a region is drawn again only the changed rectangle of each resident tile is patched. Least recently drawn
 * tiles give their slots up first. Only the backend's own texture writes are used, so it works on OpenGL and Vulkan.
 */
public final class TileCache {
	public static final int PAGE = 2048;
	public static final int SLOT = Coords.TILE_SIZE;
	private static final int PER_SIDE = PAGE / SLOT;

	public static final class Page {
		final GpuTexture texture;
		final GpuTextureView view;
		final TextureSetup setup;

		Page(int index, GpuSampler sampler) {
			var device = RenderSystem.getDevice();
			this.texture = device.createTexture(() -> "Atlas map tiles " + index, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				GpuFormat.RGBA8_UNORM, PAGE, PAGE, 1, 1);
			this.view = device.createTextureView(this.texture);
			this.setup = TextureSetup.singleTexture(this.view, sampler);
		}

		public TextureSetup setup() {
			return this.setup;
		}

		void close() {
			this.view.close();
			this.texture.close();
		}
	}

	public static final class Slot {
		public final Page page;
		/** The slot's top-left in its page, in pixels. */
		public final int px;
		public final int py;
		volatile long key = -1;
		volatile int gen;
		volatile boolean ready;
		long lastUsed;

		Slot(Page page, int px, int py) {
			this.page = page;
			this.px = px;
			this.py = py;
		}
	}

	private record Upload(Slot slot, int gen, long key, int x, int y, int w, int h, ByteBuffer data, boolean full) {
	}

	private final int maxPages;
	private final GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
	private final List<Page> pages = new ArrayList<>();
	private final List<Slot> slots = new ArrayList<>();
	private final ConcurrentHashMap<Long, Slot> byKey = new ConcurrentHashMap<>();
	/** Tiles assembled and found to hold nothing: not drawn, not asked for again until a region in them changes. */
	private final Set<Long> empty = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<Upload> uploads = new ConcurrentLinkedQueue<>();
	private final ExecutorService composer;
	private final AtomicInteger queued = new AtomicInteger();
	private long frame;
	private int requestsThisFrame;
	/** How many new tiles may be asked for each frame (the composer works through them in order). */
	private static final int REQUESTS_PER_FRAME = 6;
	private static final int MAX_QUEUED = 24;

	public TileCache(int maxPages) {
		this.maxPages = Math.max(1, maxPages);
		this.composer = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "Atlas composer");
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		});
	}

	public static long key(int layerUid, int level, int tx, int tz) {
		return ((long) layerUid & 0xFFF) << 51 | ((long) level & 7) << 48 | (tx & 0xFFFFFFL) << 24 | (tz & 0xFFFFFFL);
	}

	/** Starts a frame (render thread). */
	public void beginFrame() {
		this.frame++;
		this.requestsThisFrame = 0;
	}

	public int residentTiles() {
		return this.byKey.size();
	}

	public int pages() {
		return this.pages.size();
	}

	public int queued() {
		return this.queued.get() + this.uploads.size();
	}

	/**
	 * The slot holding a tile, if it is ready; otherwise null, and (with {@code request}) the tile is asked for. Render
	 * thread.
	 */
	public Slot get(LayerData layer, int level, int tx, int tz, boolean request) {
		long k = key(layer.uid, level, tx, tz);
		Slot s = this.byKey.get(k);
		if (s != null) {
			s.lastUsed = this.frame;
			return s.ready ? s : null;
		}
		if (!request || this.empty.contains(k) || !layer.tileExists(level, tx, tz)) {
			return null;
		}
		if (this.requestsThisFrame >= REQUESTS_PER_FRAME || this.queued.get() >= MAX_QUEUED) {
			return null;
		}
		s = this.allocate();
		if (s == null) {
			return null;
		}
		this.requestsThisFrame++;
		s.key = k;
		s.gen++;
		s.ready = false;
		s.lastUsed = this.frame;
		this.byKey.put(k, s);
		Slot slot = s;
		int gen = s.gen;
		this.queued.incrementAndGet();
		this.composer.execute(() -> {
			try {
				this.assemble(slot, gen, k, layer, level, tx, tz);
			} catch (Throwable t) {
				MapSession.LOGGER.warn("Atlas: could not assemble tile {} {} {}: {}", level, tx, tz, t.toString());
			} finally {
				this.queued.decrementAndGet();
			}
		});
		return null;
	}

	private Slot allocate() {
		for (Slot s : this.slots) {
			if (s.key == -1) {
				return s;
			}
		}
		if (this.pages.size() < this.maxPages) {
			Page page = new Page(this.pages.size(), this.sampler);
			this.pages.add(page);
			for (int y = 0; y < PER_SIDE; y++) {
				for (int x = 0; x < PER_SIDE; x++) {
					this.slots.add(new Slot(page, x * SLOT, y * SLOT));
				}
			}
			return this.allocate();
		}
		Slot oldest = null;
		for (Slot s : this.slots) {
			if (s.lastUsed < this.frame - 1 && (oldest == null || s.lastUsed < oldest.lastUsed)) {
				oldest = s;
			}
		}
		if (oldest != null) {
			this.byKey.remove(oldest.key);
			oldest.key = -1;
			oldest.ready = false;
			oldest.gen++;
		}
		return oldest;
	}

	/** Composer thread: a whole tile from its regions' images. */
	private void assemble(Slot slot, int gen, long key, LayerData layer, int level, int tx, int tz) {
		if (slot.gen != gen) {
			return;
		}
		int per = Coords.regionsPerTile(level);
		int size = Coords.regionImageSize(level);
		ByteBuffer data = null;
		for (int dz = 0; dz < per; dz++) {
			for (int dx = 0; dx < per; dx++) {
				if (slot.gen != gen) {
					if (data != null) {
						MemoryUtil.memFree(data);
					}
					return;
				}
				int rx = (tx << level) + dx;
				int rz = (tz << level) + dz;
				if (!layer.regionExists(rx, rz)) {
					continue;
				}
				int[] pixels = layer.levelPixels(rx, rz, level);
				if (pixels == null) {
					continue;
				}
				if (data == null) {
					data = MemoryUtil.memCalloc(SLOT * SLOT * 4);
				}
				write(pixels, size, 0, 0, size, size, data, SLOT, dx * size, dz * size, (tx << Coords.REGION_SHIFT) + dx * size, (tz << Coords.REGION_SHIFT) + dz * size);
			}
		}
		this.uploads.add(new Upload(slot, gen, key, 0, 0, SLOT, SLOT, data, true));
	}

	/**
	 * Copies a rectangle of region pixels into an RGBA buffer, dithering by coverage: {@code gx0, gz0} is the
	 * rectangle's first pixel in global pixel coordinates at its level, so the pattern lines up across tiles.
	 */
	private static void write(int[] pixels, int size, int x0, int z0, int x1, int z1, ByteBuffer out, int outStride, int ox, int oz, int gx0, int gz0) {
		for (int z = z0; z < z1; z++) {
			int row = ((oz + z - z0) * outStride + ox) * 4;
			for (int x = x0; x < x1; x++) {
				int p = pixels[z * size + x];
				int at = row + (x - x0) * 4;
				if (Palette.shows(p >>> 24, gx0 + x - x0, gz0 + z - z0)) {
					out.put(at, (byte) (p >> 16));
					out.put(at + 1, (byte) (p >> 8));
					out.put(at + 2, (byte) p);
					out.put(at + 3, (byte) 0xFF);
				} else {
					out.putInt(at, 0);
				}
			}
		}
	}

	/** A region was drawn again: patch the resident tiles over it, at every level (any thread). */
	public void regionChanged(MapSession.RegionChange change) {
		this.queued.incrementAndGet();
		this.composer.execute(() -> {
			try {
				this.patch(change.layer(), change.rx(), change.rz(), change.rect());
			} catch (Throwable t) {
				MapSession.LOGGER.warn("Atlas: could not patch tile: {}", t.toString());
			} finally {
				this.queued.decrementAndGet();
			}
		});
	}

	private void patch(LayerData layer, int rx, int rz, int[] rect) {
		for (int level = 0; level < RegionImage.LEVELS; level++) {
			int tx = rx >> level;
			int tz = rz >> level;
			long k = key(layer.uid, level, tx, tz);
			Slot slot = this.byKey.get(k);
			if (slot == null) {
				// it may hold something now
				this.empty.remove(k);
				continue;
			}
			int gen = slot.gen;
			int size = Coords.regionImageSize(level);
			int x0 = rect[0] >> level;
			int z0 = rect[1] >> level;
			int x1 = Math.min(size, (rect[2] + (1 << level) - 1) >> level);
			int z1 = Math.min(size, (rect[3] + (1 << level) - 1) >> level);
			if (x0 >= x1 || z0 >= z1) {
				continue;
			}
			int[] pixels = layer.levelPixels(rx, rz, level);
			if (pixels == null) {
				continue;
			}
			int w = x1 - x0;
			int h = z1 - z0;
			ByteBuffer data = MemoryUtil.memAlloc(w * h * 4);
			int ox = Coords.regionOffsetInTile(rx, level);
			int oz = Coords.regionOffsetInTile(rz, level);
			write(pixels, size, x0, z0, x1, z1, data, w, 0, 0, (tx << Coords.REGION_SHIFT) + ox + x0, (tz << Coords.REGION_SHIFT) + oz + z0);
			this.uploads.add(new Upload(slot, gen, k, ox + x0, oz + z0, w, h, data, false));
		}
	}

	/** Uploads what the composer has made, for at most {@code budgetNanos} (render thread). Returns the bytes written. */
	public long upload(long budgetNanos) {
		long start = System.nanoTime();
		long bytes = 0;
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		Upload u;
		while ((u = this.uploads.poll()) != null) {
			try {
				if (u.slot.gen != u.gen || u.slot.key != u.key) {
					continue;
				}
				if (u.data == null) {
					// nothing there: forget the slot, remember the tile is empty
					this.byKey.remove(u.key);
					u.slot.key = -1;
					u.slot.ready = false;
					this.empty.add(u.key);
					continue;
				}
				encoder.writeToTexture(u.slot.page.texture, u.data, 0, 0, u.slot.px + u.x, u.slot.py + u.y, u.w, u.h);
				bytes += (long) u.w * u.h * 4;
				Perf.uploads++;
				if (u.full) {
					u.slot.ready = true;
				}
			} finally {
				if (u.data != null) {
					MemoryUtil.memFree(u.data);
				}
			}
			if (System.nanoTime() - start > budgetNanos) {
				break;
			}
		}
		Perf.uploadBytes += bytes;
		return bytes;
	}

	/** Forgets every tile (a new world, a resource reload). Render thread. */
	public void clear() {
		Upload u;
		while ((u = this.uploads.poll()) != null) {
			if (u.data != null) {
				MemoryUtil.memFree(u.data);
			}
		}
		for (Slot s : this.slots) {
			s.key = -1;
			s.ready = false;
			s.gen++;
		}
		this.byKey.clear();
		this.empty.clear();
	}

	/** Frees the GPU pages. Render thread. */
	public void close() {
		this.clear();
		this.composer.shutdownNow();
		for (Page p : this.pages) {
			p.close();
		}
		this.pages.clear();
		this.slots.clear();
	}
}
