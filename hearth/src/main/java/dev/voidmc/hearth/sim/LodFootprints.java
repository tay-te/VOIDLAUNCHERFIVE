package dev.voidmc.hearth.sim;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.PlotColumns;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Settlements as VOID LOD sees them at the horizon: for any column, whether a finished building stands there and its
 * silhouette (wall colour under roof colour, eave and ridge heights), read from {@link Silhouette}s rather than blocks.
 * Read-only and thread-safe for LOD's workers; versioned per 1024-block region so LOD rebuilds only tiles whose region
 * changed. Built plots of live settlements come from their records (kept here as they finish), and the finished plots of
 * histories not yet adopted come from the atlas.
 *
 * <pre>
 *   LodFootprints f = LodFootprints.of(level);       // null where Hearth does not run
 *   long v = f.version(x >> 10, z >> 10);            // changed since the tile was built?
 *   int[] out = new int[3];
 *   if (f.sample(x, z, out)) { wall = out[0]; roof = out[1]; eave = out[2] >> 16; ridge = (short) out[2]; }
 * </pre>
 *
 * <h2>Cost (what a read may and may not do)</h2>
 * A read never computes. {@link #sample}, {@link #chunk} and {@link #version} look up a ready snapshot of the region (a
 * map of painted chunks) and copy at most 768 ints: well under a microsecond. A region whose snapshot is missing or
 * out of date reads as it last was (nothing at all the first time: {@code false}) and is queued for a small pool of
 * low-priority background threads ({@link #THREADS}); when its snapshot is ready its {@link #version} changes, and LOD,
 * which polls versions, rebuilds those tiles. So LOD sees {@code false} for a region until it is ready, then a version
 * change. The queue is worked nearest first (to the level's players, else in the order asked), at most
 * {@link #THREADS} regions at a time per level, and a queued region nobody has read or polled for {@link #STALE_MS}, or
 * farther than {@link #FAR_REGIONS} regions from every player, is dropped (a later read queues it again).
 *
 * <p>Building a snapshot paints live plots with their exact silhouettes (compiled once per plot, on the background
 * thread) and historic plots from a per-region list of painted rectangles kept for the life of the world. That list
 * comes from the atlas plan (the expensive part: it grows the history, 150 to 700 ms per settled region, unless the
 * atlas has it already; taken with {@link Atlas#planTransient} so the far view never pushes world generation's plans out
 * of the atlas cache), and each historic plot's silhouette is <i>typical</i>: the first plot of a kind (archetype,
 * style, tier and the params that shape it) is compiled exactly, and the rest of that kind take its eave height above
 * the floor, its roof rise scaled to their span and its colours, without a compile. The lists and the typical
 * silhouettes are saved per world ({@code data/hearth/lod/<dimension>.bin}, fingerprinted by seed, Hearth version and
 * sim config), so after a restart a historic region costs only the paint (about a millisecond), never a plan or a
 * compile. Typical against exact, over a thousand plots: eave 0.2 blocks off, ridge 0.9, colours about 20/255 per
 * channel (the seed's material variant); live plots (near the players) stay exact.
 *
 * <p>Measured ({@code LodFootprintsBench}, 256 regions of hills, real catalogue and planner, 2 threads): a read 0.0002 ms
 * (p99.9 under 0.04 ms while regions build); cold, 65 s of background CPU for the 256 plans, nothing else of note; with
 * the plans made, 16 ms per region (first compile of each kind; the exact compile per plot it replaces cost about 120 ms
 * per region); after a restart, 0.2 ms per region and no plans. Before (a read built the region on LOD's thread): plan
 * plus about 120 ms of compiles on the first read of each region.
 */
public final class LodFootprints {
	/** Background threads per level, and in all (a shared daemon pool at minimum priority). */
	public static final int THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4));
	/** A queued region not read or polled for this long is dropped. */
	public static final long STALE_MS = 20_000;
	/** A queued region this many regions (Chebyshev) from every player is dropped. */
	public static final int FAR_REGIONS = 24;
	/** Snapshots kept in memory; beyond it the least recently read are dropped (and rebuilt from the lists on demand). */
	static final int SNAPSHOT_CAP = 512;
	/** Bumped whenever what the file holds, or how a silhouette is worked out, changes. */
	private static final int FORMAT = 1;

	private static final ThreadPoolExecutor POOL;

	static {
		AtomicInteger n = new AtomicInteger();
		POOL = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
			Thread t = new Thread(r, "hearth-lod-footprints-" + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		POOL.allowCoreThreadTimeOut(true);
	}

	/** A finished building. Its silhouette is worked out on first use, on the background thread. */
	private static final class Entry {
		final long settlement;
		final int plot;
		final PlotSpec spec;
		private volatile @Nullable Silhouette silhouette;
		private volatile boolean failed;

		Entry(long settlement, int plot, PlotSpec spec) {
			this.settlement = settlement;
			this.plot = plot;
			this.spec = spec;
		}

		@Nullable Silhouette silhouette(TerrainProbe terrain) {
			Silhouette s = this.silhouette;
			if (s == null && !this.failed) {
				s = compile(this.spec, terrain);
				if (s == null) {
					this.failed = true;
				}
				this.silhouette = s;
			}
			return s;
		}
	}

	/** A region's columns: per chunk, 256 × {wall, roof, eave << 16 | ridge & 0xFFFF}, as of content version {@code content}. */
	private record Snapshot(long content, Long2ObjectMap<int[]> chunks) {
	}

	/** Painted rectangles, {@link #REC} ints each: x, z, sizeX, sizeZ, wall, roof, eave << 16 | ridge. */
	private static final int REC = 7;
	private static final int[] NOTHING = new int[0];
	private static final int IDLE = 0;
	private static final int QUEUED = 1;
	private static final int BUILDING = 2;

	private static final class Region {
		final int rx;
		final int rz;
		final List<Entry> live = new CopyOnWriteArrayList<>();
		/** What LOD polls: changes when a new snapshot is published. */
		volatile long version;
		/** Changes whenever anything in the region would draw differently. */
		volatile long content = 1;
		volatile @Nullable Snapshot snapshot;
		/** The historic plots' rectangles (from the atlas plan, or the file), once known. */
		volatile int @Nullable [] historic;
		volatile long wanted;
		volatile long read;
		/** {@link #IDLE}, {@link #QUEUED} or {@link #BUILDING}; changed only under the region's lock. */
		volatile int state;
		/** The order it was queued in (nearest first, then first asked). */
		long seq;

		Region(int rx, int rz) {
			this.rx = rx;
			this.rz = rz;
		}
	}

	/** How one kind of building stands, relative to its floor: what historic plots of that kind are drawn from. */
	private record Typical(int eaveRel, int ridgeRel, int span, int wall, int roof) {
		static final Typical NONE = new Typical(0, 0, -1, 0, 0);

		static Typical of(PlotSpec spec, Silhouette s) {
			return new Typical(s.eaveY() - spec.floorY(), s.ridgeY() - spec.floorY(), span(spec), s.wallColor(), s.roofColor());
		}

		Silhouette at(PlotSpec spec) {
			int eave = spec.floorY() + this.eaveRel;
			int rise = Math.max(0, this.ridgeRel - this.eaveRel);
			// a roof (its rise no more than its span) grows with the span; a tower or spire keeps its height
			if (this.span > 0 && rise <= this.span) {
				rise = Math.round(rise * span(spec) / (float) this.span);
			}
			int ridge = Math.max(eave, Math.min(eave + rise, spec.floorY() + Math.max(spec.maxHeight(), this.ridgeRel)));
			return new Silhouette(this.wall, this.roof, eave, ridge);
		}

		static int span(PlotSpec spec) {
			return Math.min(spec.sizeX(), spec.sizeZ());
		}
	}

	private final Atlas atlas;
	private final TerrainProbe terrain;
	private final @Nullable Path file;
	private final Supplier<long[]> players;
	private final Map<Long, Region> regions = new ConcurrentHashMap<>();
	private final Set<Long> adopted = ConcurrentHashMap.newKeySet();
	private final Map<String, Typical> typicals = new ConcurrentHashMap<>();
	private final AtomicInteger workers = new AtomicInteger();
	private final AtomicLong seq = new AtomicLong();
	private final AtomicInteger snapshots = new AtomicInteger();
	private volatile boolean loaded;
	private volatile boolean dirty;
	private volatile long savedAt;
	private volatile long[] focus = new long[0];
	private volatile long focusAt;

	/** Counters for the bench and the debug screen. */
	public final AtomicLong built = new AtomicLong();
	public final AtomicLong buildNanos = new AtomicLong();
	public final AtomicLong compiles = new AtomicLong();
	public final AtomicLong dropped = new AtomicLong();

	/** Footprints with nothing saved and no players to work nearest (tests, benches). */
	public LodFootprints(Atlas atlas, TerrainProbe terrain) {
		this(atlas, terrain, null, () -> new long[0]);
	}

	/** A level's footprints: saved under the world's data, worked nearest its players first. */
	public LodFootprints(Atlas atlas, TerrainProbe terrain, ServerLevel level) {
		this(atlas, terrain, cacheFile(level), () -> {
			try {
				var list = List.copyOf(level.players());
				long[] out = new long[list.size()];
				for (int i = 0; i < out.length; i++) {
					out[i] = Atlas.regionKey(Atlas.region(list.get(i).getBlockX()), Atlas.region(list.get(i).getBlockZ()));
				}
				return out;
			} catch (RuntimeException e) {
				// the list changed under us (we are not on the server thread): try again next time
				return null;
			}
		});
	}

	/**
	 * @param file    where the historic lists and typical silhouettes are kept between runs, or null
	 * @param players the region keys of the players, for nearest first (null answers keep the last)
	 */
	public LodFootprints(Atlas atlas, TerrainProbe terrain, @Nullable Path file, Supplier<long @Nullable []> players) {
		this.atlas = atlas;
		this.terrain = terrain;
		this.file = file;
		this.players = players;
	}

	private static Path cacheFile(ServerLevel level) {
		var id = level.dimension().identifier();
		return level.getServer().getWorldPath(LevelResource.DATA).resolve("hearth").resolve("lod")
			.resolve(id.getNamespace() + "_" + id.getPath().replace('/', '_') + ".bin");
	}

	/** The footprints of a level, or null if Hearth does not run there. */
	public static @Nullable LodFootprints of(ServerLevel level) {
		var w = SimServer.world(level);
		return w == null ? null : w.footprints();
	}

	private Region region(long key) {
		return this.regions.computeIfAbsent(key, k -> new Region(ChunkPos.getX(k), ChunkPos.getZ(k)));
	}

	// ---------------------------------------------------------------- updates (server thread)

	/** Per live settlement: which of its plots are drawn, and in which region (so updates never scan every region). */
	private final Map<Long, Map<Integer, Long>> drawn = new ConcurrentHashMap<>();

	/** A plot finished: it now shows at the horizon. Cheap: no blueprint is generated here (see {@link Entry}). */
	public void built(SimSettlement s, SimPlot p) {
		if (PlotColumns.isLine(p.spec())) {
			return;
		}
		Map<Integer, Long> mine = this.drawn.computeIfAbsent(s.id(), k -> new ConcurrentHashMap<>());
		long key = key(p.spec());
		Long was = mine.put(p.id(), key);
		if (was != null) {
			Region old = this.regions.get(was);
			if (old != null) {
				old.live.removeIf(e -> e.settlement == s.id() && e.plot == p.id());
				changed(old);
			}
		}
		Region r = region(key);
		r.live.add(new Entry(s.id(), p.id(), p.spec()));
		changed(r);
	}

	/** Re-reads a settlement's standing plots (after adoption, removals, or loading): O(plots), no blueprints. */
	public void refresh(SimSettlement s) {
		if (s.originRegion() != SimSettlement.NO_REGION) {
			if (this.adopted.add(s.originRegion())) {
				Region r = this.regions.get(s.originRegion());
				if (r != null) {
					changed(r);
				}
			}
		}
		Map<Integer, Long> mine = this.drawn.computeIfAbsent(s.id(), k -> new ConcurrentHashMap<>());
		var it = mine.entrySet().iterator();
		while (it.hasNext()) {
			var en = it.next();
			SimPlot p = s.plot(en.getKey());
			if (p == null || !p.isStanding() || key(p.spec()) != en.getValue()) {
				it.remove();
				Region r = this.regions.get(en.getValue());
				if (r != null) {
					int id = en.getKey();
					r.live.removeIf(e -> e.settlement == s.id() && e.plot == id);
					changed(r);
				}
			}
		}
		for (SimPlot p : s.plots()) {
			if (p.isStanding() && !mine.containsKey(p.id())) {
				built(s, p);
			}
		}
	}

	/** The region draws differently now: rebuild it in the background if LOD has looked at it (the version changes then). */
	private void changed(Region r) {
		r.content++;
		if (r.snapshot != null || r.read != 0) {
			want(r);
		}
	}

	private static long key(PlotSpec spec) {
		int cx = spec.origin().getX() + spec.sizeX() / 2;
		int cz = spec.origin().getZ() + spec.sizeZ() / 2;
		return Atlas.regionKey(Atlas.region(cx), Atlas.region(cz));
	}

	// ---------------------------------------------------------------- queries (any thread, never compute)

	/**
	 * Whether the region's ready snapshot has any finished building in it (false while it is not ready yet). Cheap; LOD may
	 * skip rebuilding a region's tiles when this is false.
	 */
	public boolean any(int regionX, int regionZ) {
		Region r = this.regions.get(Atlas.regionKey(regionX, regionZ));
		Snapshot snap = r == null ? null : r.snapshot;
		return snap != null && !snap.chunks.isEmpty();
	}

	/**
	 * Changes whenever the region's drawing changes (a new snapshot with something in it, or one that empties it). A region
	 * with nothing to draw keeps its version when it becomes ready, so LOD rebuilds only where towns are. 0 for a region
	 * never read.
	 */
	public long version(int regionX, int regionZ) {
		Region r = this.regions.get(Atlas.regionKey(regionX, regionZ));
		if (r == null) {
			return 0;
		}
		// LOD polls the regions it shows: keep them wanted, and catch up any it read before they changed
		r.wanted = System.currentTimeMillis();
		Snapshot snap = r.snapshot;
		if ((snap == null || snap.content != r.content) && r.state == IDLE) {
			want(r);
		}
		return r.version;
	}

	/**
	 * Fills {@code out} with the column's {wall colour, roof colour, eave << 16 | ridge} and returns true if a finished
	 * building stands there. False, at once, while the region is not ready (it is then queued; see the class notes).
	 */
	public boolean sample(int x, int z, int[] out) {
		Snapshot snap = snapshot(Atlas.region(x), Atlas.region(z));
		if (snap == null) {
			return false;
		}
		int[] cols = snap.chunks.get(ChunkPos.pack(x >> 4, z >> 4));
		if (cols == null) {
			return false;
		}
		int i = ((z & 15) << 4 | x & 15) * 3;
		if (cols[i] == 0 && cols[i + 1] == 0 && cols[i + 2] == 0) {
			return false;
		}
		out[0] = cols[i];
		out[1] = cols[i + 1];
		out[2] = cols[i + 2];
		return true;
	}

	/**
	 * Fills {@code out} (768 ints) with the whole chunk's columns, {wall, roof, eave << 16 | ridge} per column at
	 * {@code ((z & 15) << 4 | x & 15) * 3} (all zero where nothing stands), and returns false, leaving {@code out} untouched,
	 * if no finished building stands anywhere in the chunk (or the region is not ready yet). One lookup instead of 256.
	 */
	public boolean chunk(int cx, int cz, int[] out) {
		Snapshot snap = snapshot(Atlas.region(cx << 4), Atlas.region(cz << 4));
		if (snap == null) {
			return false;
		}
		int[] cols = snap.chunks.get(ChunkPos.pack(cx, cz));
		if (cols == null) {
			return false;
		}
		System.arraycopy(cols, 0, out, 0, 256 * 3);
		return true;
	}

	/** Whether the region's drawing is up to date (for tests and benches). */
	public boolean ready(int rx, int rz) {
		Region r = this.regions.get(Atlas.regionKey(rx, rz));
		Snapshot snap = r == null ? null : r.snapshot;
		return snap != null && snap.content == r.content;
	}

	/** Regions queued or being built. */
	public int pending() {
		int n = 0;
		for (Region r : this.regions.values()) {
			if (r.state != IDLE) {
				n++;
			}
		}
		return n;
	}

	/** The snapshot as it stands (possibly stale, possibly none), queuing a rebuild if it is not up to date. */
	private @Nullable Snapshot snapshot(int rx, int rz) {
		Region r = region(Atlas.regionKey(rx, rz));
		long now = System.currentTimeMillis();
		r.read = now;
		r.wanted = now;
		Snapshot snap = r.snapshot;
		if ((snap == null || snap.content != r.content) && r.state == IDLE) {
			want(r);
		}
		return snap;
	}

	// ---------------------------------------------------------------- the background queue

	private void want(Region r) {
		r.wanted = System.currentTimeMillis();
		synchronized (r) {
			if (r.state != IDLE) {
				return;
			}
			r.seq = this.seq.incrementAndGet();
			r.state = QUEUED;
		}
		kick();
	}

	/** Starts a worker if this level has fewer than {@link #THREADS}. */
	private void kick() {
		int w = this.workers.get();
		while (w < THREADS) {
			if (this.workers.compareAndSet(w, w + 1)) {
				try {
					POOL.execute(this::work);
				} catch (RuntimeException e) {
					this.workers.decrementAndGet();
				}
				return;
			}
			w = this.workers.get();
		}
	}

	private void work() {
		try {
			for (Region r = next(); r != null; r = next()) {
				try {
					build(r);
				} catch (Throwable t) {
					Hearth.LOG.warn("hearth: LOD footprints of region {},{} failed", r.rx, r.rz, t);
					synchronized (r) {
						r.state = IDLE;
					}
				}
				saveIfDirty(false);
			}
			saveIfDirty(true);
		} finally {
			this.workers.decrementAndGet();
		}
		// a region queued as we left found every worker busy: take it up
		for (Region r : this.regions.values()) {
			if (r.state == QUEUED) {
				kick();
				break;
			}
		}
	}

	/** The nearest queued region still wanted (dropping stale and far ones), claimed for building; null if none. */
	private @Nullable Region next() {
		while (true) {
			long now = System.currentTimeMillis();
			long[] near = focus(now);
			Region best = null;
			long bestRank = Long.MAX_VALUE;
			for (Region r : this.regions.values()) {
				if (r.state != QUEUED) {
					continue;
				}
				int d = distance(r, near);
				if (now - r.wanted > STALE_MS || d > FAR_REGIONS) {
					synchronized (r) {
						if (r.state == QUEUED) {
							r.state = IDLE;
							this.dropped.incrementAndGet();
						}
					}
					continue;
				}
				long rank = (long) Math.min(d, 1 << 20) << 40 | r.seq & 0xFF_FFFF_FFFFL;
				if (rank < bestRank) {
					bestRank = rank;
					best = r;
				}
			}
			if (best == null) {
				return null;
			}
			synchronized (best) {
				if (best.state == QUEUED) {
					best.state = BUILDING;
					return best;
				}
			}
		}
	}

	private long[] focus(long now) {
		if (now - this.focusAt > 1000) {
			long[] p = this.players.get();
			if (p != null) {
				this.focus = p;
			}
			this.focusAt = now;
		}
		return this.focus;
	}

	private static int distance(Region r, long[] near) {
		if (near.length == 0) {
			return 0;
		}
		int best = Integer.MAX_VALUE;
		for (long k : near) {
			best = Math.min(best, Math.max(Math.abs(ChunkPos.getX(k) - r.rx), Math.abs(ChunkPos.getZ(k) - r.rz)));
		}
		return best;
	}

	/** Builds and publishes one region's snapshot (on a background thread). */
	private void build(Region r) {
		long start = System.nanoTime();
		long content = r.content;
		long key = Atlas.regionKey(r.rx, r.rz);
		Long2ObjectOpenHashMap<int[]> chunks = new Long2ObjectOpenHashMap<>();
		for (Entry e : r.live) {
			Silhouette sil = e.silhouette(this.terrain);
			if (sil != null) {
				paint(chunks, e.spec.origin().getX(), e.spec.origin().getZ(), e.spec.sizeX(), e.spec.sizeZ(), sil.wallColor(), sil.roofColor(),
					sil.eaveY() << 16 | sil.ridgeY() & 0xFFFF);
			}
		}
		if (!this.adopted.contains(key) && this.atlas.enabled()) {
			int[] h = historic(r);
			for (int i = 0; i + REC <= h.length; i += REC) {
				paint(chunks, h[i], h[i + 1], h[i + 2], h[i + 3], h[i + 4], h[i + 5], h[i + 6]);
			}
		}
		chunks.trim();
		Snapshot snap = new Snapshot(content, chunks);
		boolean fresh;
		synchronized (r) {
			if (r.snapshot == null) {
				this.snapshots.incrementAndGet();
			}
			Snapshot was = r.snapshot;
			r.snapshot = snap;
			// a region with nothing to draw before and after keeps its version: LOD rebuilds only where towns appear or change
			if (!(chunks.isEmpty() && (was == null || was.chunks.isEmpty()))) {
				r.version++;
			}
			fresh = r.content == content;
			r.state = IDLE;
		}
		this.built.incrementAndGet();
		this.buildNanos.addAndGet(System.nanoTime() - start);
		if (!fresh) {
			want(r);
		}
		if (this.snapshots.get() > SNAPSHOT_CAP) {
			evict();
		}
	}

	/** Drops the least recently read snapshots (not their lists: rebuilding one is a paint). */
	private void evict() {
		List<Region> held = new ArrayList<>();
		for (Region r : this.regions.values()) {
			if (r.snapshot != null && r.state == IDLE) {
				held.add(r);
			}
		}
		held.sort((a, b) -> Long.compare(a.read, b.read));
		for (int i = 0; i < held.size() - SNAPSHOT_CAP * 3 / 4; i++) {
			Region r = held.get(i);
			synchronized (r) {
				if (r.snapshot != null && r.state == IDLE) {
					r.snapshot = null;
					this.snapshots.decrementAndGet();
				}
			}
		}
	}

	/** The region's historic rectangles: from memory, the file, or (once per world) the atlas plan. */
	private int[] historic(Region r) {
		int[] h = r.historic;
		if (h != null) {
			return h;
		}
		load();
		h = r.historic;
		if (h != null) {
			return h;
		}
		RegionPlan plan = this.atlas.planTransient(r.rx, r.rz);
		IntArrayList out = new IntArrayList();
		for (RegionPlan.HistoricPlot hp : plan.plots()) {
			if (hp.completed() >= hp.stageCount() && !hp.road()) {
				PlotSpec spec = hp.spec();
				Silhouette s = typical(spec);
				if (s != null) {
					out.addAll(IntArrayList.of(spec.origin().getX(), spec.origin().getZ(), spec.sizeX(), spec.sizeZ(), s.wallColor() | 0xFF00_0000,
						s.roofColor() | 0xFF00_0000, s.eaveY() << 16 | s.ridgeY() & 0xFFFF));
				}
			}
		}
		h = out.isEmpty() ? NOTHING : out.toIntArray();
		r.historic = h;
		this.dirty = true;
		return h;
	}

	/** What a historic plot looks like from far away: its kind's typical silhouette, compiling only the first of a kind. */
	private @Nullable Silhouette typical(PlotSpec spec) {
		String kind = kind(spec);
		Typical t = this.typicals.get(kind);
		if (t == null) {
			this.compiles.incrementAndGet();
			Silhouette s = compile(spec, this.terrain);
			this.typicals.putIfAbsent(kind, s == null ? Typical.NONE : Typical.of(spec, s));
			return s;
		}
		return t == Typical.NONE || t.span < 0 ? null : t.at(spec);
	}

	/** What a silhouette depends on besides size and place: archetype, style, tier and the params that shape the body. */
	static String kind(PlotSpec spec) {
		return spec.archetype() + "|" + spec.style() + "|" + spec.tier() + "|" + spec.paramInt("storeys", -1) + "|" + spec.paramString("kind", "")
			+ "|" + spec.paramString("wall", "") + "|" + spec.paramInt("wall_height", -1) + "|" + (spec.paramInt("stacked_on", -1) >= 0);
	}

	private static @Nullable Silhouette compile(PlotSpec spec, TerrainProbe terrain) {
		try {
			return HearthServices.blueprints().blueprint(spec, terrain).silhouette();
		} catch (RuntimeException e) {
			// an archetype that no longer exists draws nothing
			return null;
		}
	}

	private static void paint(Long2ObjectOpenHashMap<int[]> chunks, int ox, int oz, int sx, int sz, int wall, int roof, int packed) {
		for (int x = ox; x < ox + sx; x++) {
			for (int z = oz; z < oz + sz; z++) {
				int[] cols = chunks.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new int[256 * 3]);
				int i = ((z & 15) << 4 | x & 15) * 3;
				cols[i] = wall | 0xFF00_0000;
				cols[i + 1] = roof | 0xFF00_0000;
				cols[i + 2] = packed;
			}
		}
	}

	// ---------------------------------------------------------------- the file

	/** Changes when anything a saved list depends on might: the seed, Hearth's version, the sim config. */
	private long fingerprint() {
		long f = this.fingerprint;
		if (f == 0) {
			f = this.fingerprint = computeFingerprint();
		}
		return f;
	}

	private volatile long fingerprint;

	private long computeFingerprint() {
		long h = FORMAT * 0x9E3779B97F4A7C15L ^ this.terrain.seed();
		String version = "dev";
		try {
			version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(Hearth.MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		} catch (Throwable ignored) {
			// no loader (tests)
		}
		String config = SimConfig.CODEC.encodeStart(JsonOps.INSTANCE, this.atlas.rules().config).result().map(Object::toString).orElse("");
		h = h * 31 + version.hashCode();
		h = h * 31 + config.hashCode();
		h = h * 31 + (this.atlas.enabled() ? 1 : 0);
		return h;
	}

	private void load() {
		if (this.loaded) {
			return;
		}
		synchronized (this) {
			if (this.loaded || this.file == null || !Files.isRegularFile(this.file)) {
				this.loaded = true;
				return;
			}
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(this.file))))) {
				if (in.readInt() != FORMAT || in.readLong() != fingerprint()) {
					Hearth.LOG.info("hearth: LOD footprints in {} are from another world or version; drawing afresh", this.file);
				} else {
					int kinds = in.readInt();
					for (int i = 0; i < kinds; i++) {
						String k = in.readUTF();
						this.typicals.putIfAbsent(k, new Typical(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt()));
					}
					int count = in.readInt();
					for (int i = 0; i < count; i++) {
						long key = in.readLong();
						int[] h = new int[in.readInt()];
						for (int j = 0; j < h.length; j++) {
							h[j] = in.readInt();
						}
						Region r = region(key);
						if (r.historic == null) {
							r.historic = h.length == 0 ? NOTHING : h;
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				Hearth.LOG.warn("hearth: could not read LOD footprints from {}; drawing afresh", this.file, e);
			}
			this.loaded = true;
		}
	}

	/** Saves the lists if they changed, at most every few seconds unless {@code idle}. */
	private void saveIfDirty(boolean idle) {
		long now = System.currentTimeMillis();
		if (!this.dirty || this.file == null || !idle && now - this.savedAt < 5000) {
			return;
		}
		synchronized (this) {
			if (!this.dirty) {
				return;
			}
			this.dirty = false;
			this.savedAt = now;
			Path tmp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
			try {
				Files.createDirectories(this.file.getParent());
				try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
					out.writeInt(FORMAT);
					out.writeLong(fingerprint());
					var kinds = new ArrayList<>(this.typicals.entrySet());
					out.writeInt(kinds.size());
					for (var en : kinds) {
						Typical t = en.getValue();
						out.writeUTF(en.getKey());
						out.writeInt(t.eaveRel);
						out.writeInt(t.ridgeRel);
						out.writeInt(t.span);
						out.writeInt(t.wall);
						out.writeInt(t.roof);
					}
					List<Region> known = new ArrayList<>();
					for (Region r : this.regions.values()) {
						if (r.historic != null) {
							known.add(r);
						}
					}
					out.writeInt(known.size());
					for (Region r : known) {
						int[] h = r.historic;
						out.writeLong(Atlas.regionKey(r.rx, r.rz));
						out.writeInt(h.length);
						for (int v : h) {
							out.writeInt(v);
						}
					}
				}
				Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException | RuntimeException e) {
				this.dirty = true;
				Hearth.LOG.warn("hearth: could not save LOD footprints to {}", this.file, e);
			}
		}
	}
}
