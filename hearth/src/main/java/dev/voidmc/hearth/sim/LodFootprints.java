package dev.voidmc.hearth.sim;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Settlements as VOID LOD sees them at the horizon: for any column, whether a building stands there (or is planned
 * there) and its silhouette (wall colour under roof colour, eave and ridge heights), read from blueprints rather than
 * blocks. Footprints are per building, not per plot: only the columns the building itself covers ({@link FarShape}),
 * coloured with what it is actually built of (its walls' and roof's most common map colours: a Bohemian house reads
 * white plaster under a shingle or red tile roof), with the ridge at the eave for a flat roof.
 * Read-only and thread-safe for LOD's workers; versioned per 1024-block region so LOD rebuilds only tiles whose region
 * changed. Built plots of live settlements come from their records (kept here as they finish), and the finished plots of
 * histories not yet adopted come from the atlas.
 *
 * <pre>
 *   LodFootprints f = LodFootprints.of(level);       // null where Hearth does not run
 *   long v = f.version(x >> 10, z >> 10);            // changed since the tile was built?
 *   int[] out = new int[3];
 *   if (f.sample(x, z, out)) { wall = out[0] & 0xFFFFFF; roof = out[1] & 0xFFFFFF; eave = out[2] >> 16; ridge = (short) out[2];
 *                              planned = out[0] >>> 24 == 0xFE; }
 * </pre>
 *
 * <p>The wall word's high byte is the column's state: 0xFF ({@link #BUILT}) a finished building, 0xFE ({@link #PLANNED})
 * a plot a live settlement has planned or is building but not finished (LOD may draw it as a ghost, or not at all). The
 * finished plots of worldgen histories not yet adopted (they are generated into the world with their chunks) are 0xFF.
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
 * <p>Building a snapshot paints live plots with their exact shapes (compiled once per plot, on the background thread)
 * and historic plots from a per-region list of placed plots kept for the life of the world. That list
 * comes from the atlas plan (the expensive part: it grows the history, 150 to 700 ms per settled region, unless the
 * atlas has it already; taken with {@link Atlas#planTransient} so the far view never pushes world generation's plans out
 * of the atlas cache), and each historic plot's <i>shape</i> is typical: the first plot of a kind (archetype, style,
 * tier and the params that shape it) is compiled exactly, and the rest of that kind take its mask (scaled to their
 * plot, turned to their facing), its eave height above the floor and its roof rise scaled to their span, without a
 * compile. Their <i>colours</i> are their own: the materials their own dress gives the roles the kind's walls and roof
 * turned out to be made of ({@code BlueprintService#dressColours}: the variant, region and tier of each building), so a
 * street of one kind of house still shows its plastered, brick and log fronts, its thatch, shingle and tile. The lists
 * and the typical shapes are saved per world ({@code data/hearth/lod/<dimension>.bin}, fingerprinted by seed, Hearth
 * version and sim config), so after a restart a historic region costs only the paint, never a plan or a compile. Live
 * plots (near the players) stay exact.
 *
 * <p>Measured ({@code LodFootprintsBench}, 256 regions of hills, real catalogue and planner, 2 threads): a read about
 * 0.0002 ms; in the background, the plan per region when cold, a few milliseconds per region with the plans made (the
 * first compile of each kind, each plot's dress), a fraction of one after a restart.
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
	private static final int FORMAT = 2;
	/** The wall word's high byte (the column's state). */
	public static final int STATE = 0xFF00_0000;
	/** State: a finished (standing) building. */
	public static final int BUILT = 0xFF00_0000;
	/** State: planned or being built, not finished. */
	public static final int PLANNED = 0xFE00_0000;

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

	/** A live settlement's building, finished or planned. Its shape is worked out on first use, on the background thread. */
	private static final class Entry {
		final long settlement;
		final int plot;
		final PlotSpec spec;
		final boolean planned;
		private volatile @Nullable FarShape shape;
		private volatile boolean done;

		Entry(long settlement, int plot, PlotSpec spec, boolean planned, @Nullable Entry was) {
			this.settlement = settlement;
			this.plot = plot;
			this.spec = spec;
			this.planned = planned;
			// the same building planned, then finished: its shape is the same
			if (was != null && was.done && was.spec.equals(spec)) {
				this.shape = was.shape;
				this.done = true;
			}
		}

		@Nullable FarShape shape(TerrainProbe terrain) {
			if (!this.done) {
				this.shape = shapeOf(this.spec, terrain);
				this.done = true;
			}
			return this.shape;
		}
	}

	/** A region's columns: per chunk, 256 × {wall, roof, eave << 16 | ridge & 0xFFFF}, as of content version {@code content}. */
	private record Snapshot(long content, Long2ObjectMap<int[]> chunks) {
	}

	/**
	 * Historic plots, {@link #REC} ints each: origin x, z, sizeX, sizeZ, facing (2D data value) << 24 | kind index (into
	 * {@link #kindList}), wall, roof, eave << 16 | ridge.
	 */
	private static final int REC = 8;
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
		/** The historic plots (from the atlas plan, or the file), once known. */
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

	/**
	 * How one kind of building stands: the shape of its first plot (null: it draws nothing), that plot's span (the roof
	 * rise scales with it), and which {@code BlueprintService#DRESS_ROLES} its walls and roof turned out to be made of
	 * (-1: none of them; the shape's own colour is used then).
	 */
	private record Typical(@Nullable FarShape shape, int span, int wallRole, int roofRole) {
		static final Typical NONE = new Typical(null, -1, -1, -1);

		/** Eave << 16 | ridge on another plot of the kind. */
		int packed(PlotSpec spec) {
			FarShape s = this.shape;
			int eave = spec.floorY() + s.eaveRel;
			int rise = Math.max(0, s.ridgeRel - s.eaveRel);
			// a roof (its rise no more than its span) grows with the span; a tower or spire keeps its height
			if (this.span > 0 && rise <= this.span) {
				rise = Math.round(rise * span(spec) / (float) this.span);
			}
			int ridge = Math.max(eave, Math.min(eave + rise, spec.floorY() + Math.max(spec.maxHeight(), s.ridgeRel)));
			return eave << 16 | ridge & 0xFFFF;
		}

		static int span(PlotSpec spec) {
			return Math.min(spec.sizeX(), spec.sizeZ());
		}
	}

	/** Which dress role a colour is, roof roles first for a roof; -1 if none. */
	private static int role(int @Nullable [] dress, int colour, boolean roof) {
		if (dress == null) {
			return -1;
		}
		int n = dress.length;
		int start = roof ? Math.min(5, n - 1) : 0;
		for (int k = 0; k < n; k++) {
			int i = (start + k) % n;
			if (dress[i] == colour) {
				return i;
			}
		}
		return -1;
	}

	private final Atlas atlas;
	private final TerrainProbe terrain;
	private final @Nullable Path file;
	private final Supplier<long[]> players;
	private final Map<Long, Region> regions = new ConcurrentHashMap<>();
	private final Set<Long> adopted = ConcurrentHashMap.newKeySet();
	/** The kinds seen, in the order first seen (historic plots name them by index), and their indexes by key. */
	private final List<Typical> kindList = new CopyOnWriteArrayList<>();
	private final Map<String, Integer> kindIndex = new ConcurrentHashMap<>();
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

	/** Where a live plot is drawn, and whether as planned. */
	private record Drawn(long region, boolean planned) {
	}

	/** Per live settlement: which of its plots are drawn, and in which region (so updates never scan every region). */
	private final Map<Long, Map<Integer, Drawn>> drawn = new ConcurrentHashMap<>();

	/** A plot finished (or changed state): it now shows at the horizon as it stands. Cheap: no blueprint is generated here. */
	public void built(SimSettlement s, SimPlot p) {
		update(s, p);
	}

	/**
	 * Re-reads a settlement's plots (after adoption, plans, removals, or loading): O(plots), no blueprints. Finished (and
	 * abandoned, still standing) buildings are drawn as built; planned and building plots with {@link #PLANNED}.
	 */
	public void refresh(SimSettlement s) {
		if (s.originRegion() != SimSettlement.NO_REGION) {
			if (this.adopted.add(s.originRegion())) {
				// the history there is the settlement's own now: stop drawing it from the atlas
				changed(region(s.originRegion()));
			}
		}
		Map<Integer, Drawn> mine = this.drawn.computeIfAbsent(s.id(), k -> new ConcurrentHashMap<>());
		for (Integer id : List.copyOf(mine.keySet())) {
			if (s.plot(id) == null) {
				Drawn was = mine.remove(id);
				if (was != null) {
					remove(s.id(), id, was);
				}
			}
		}
		for (SimPlot p : s.plots()) {
			update(s, p);
		}
	}

	private void update(SimSettlement s, SimPlot p) {
		if (PlotColumns.isLine(p.spec())) {
			return;
		}
		PlotState st = p.state();
		boolean standing = st == PlotState.BUILT || st == PlotState.ABANDONED;
		boolean planned = st == PlotState.PLANNED || st == PlotState.BUILDING;
		Map<Integer, Drawn> mine = this.drawn.computeIfAbsent(s.id(), k -> new ConcurrentHashMap<>());
		Drawn now = standing || planned ? new Drawn(key(p.spec()), planned) : null;
		Drawn was = now == null ? mine.remove(p.id()) : mine.put(p.id(), now);
		if (java.util.Objects.equals(was, now)) {
			return;
		}
		Entry old = was == null ? null : remove(s.id(), p.id(), was);
		if (now != null) {
			Region r = region(now.region);
			r.live.add(new Entry(s.id(), p.id(), p.spec(), planned, old));
			changed(r);
		}
	}

	private @Nullable Entry remove(long settlement, int plot, Drawn was) {
		Region r = this.regions.get(was.region);
		if (r == null) {
			return null;
		}
		Entry found = null;
		for (Entry e : r.live) {
			if (e.settlement == settlement && e.plot == plot) {
				found = e;
				r.live.remove(e);
			}
		}
		changed(r);
		return found;
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
	 * Fills {@code out} with the column's {wall colour (its high byte the state: {@link #BUILT} or {@link #PLANNED}), roof colour, eave << 16 | ridge}
	 * and returns true if a building stands there or is planned there. False, at once, while the region is not ready (it
	 * is then queued; see the class notes).
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
		if (!this.adopted.contains(key) && this.atlas.enabled()) {
			int[] h = historic(r);
			for (int i = 0; i + REC <= h.length; i += REC) {
				int k = h[i + 4] & 0xFF_FFFF;
				FarShape shape = k < this.kindList.size() ? this.kindList.get(k).shape : null;
				if (shape != null) {
					paint(chunks, shape, h[i], h[i + 1], h[i + 2], h[i + 3], Direction.from2DDataValue(h[i + 4] >>> 24), h[i + 5], h[i + 6], h[i + 7]);
				}
			}
		}
		// live plots over the history; planned ones first, so a finished building over a planned one (a rebuild) shows
		for (int pass = 0; pass < 2; pass++) {
			for (Entry e : r.live) {
				if (e.planned != (pass == 0)) {
					continue;
				}
				FarShape shape = e.shape(this.terrain);
				if (shape != null) {
					PlotSpec sp = e.spec;
					paint(chunks, shape, sp.origin().getX(), sp.origin().getZ(), sp.sizeX(), sp.sizeZ(), sp.facing(), shape.wall & 0xFF_FFFF | (e.planned ? PLANNED : BUILT),
						shape.roof, (sp.floorY() + shape.eaveRel) << 16 | sp.floorY() + shape.ridgeRel & 0xFFFF);
				}
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

	/** The region's historic plots: from memory, the file, or (once per world) the atlas plan. */
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
			if (hp.completed() < hp.stageCount() || hp.road() || PlotColumns.isLine(hp.spec())) {
				continue;
			}
			PlotSpec spec = hp.spec();
			int k = typical(spec);
			boolean first = k < 0;
			k = first ? ~k : k;
			Typical t = this.kindList.get(k);
			FarShape shape = t.shape;
			if (shape == null) {
				continue;
			}
			int wall = shape.wall;
			int roof = shape.roof;
			if (!first && (t.wallRole >= 0 || t.roofRole >= 0)) {
				// the kind's shape, this building's own dress
				int[] dress = dress(spec);
				if (dress != null) {
					if (t.wallRole >= 0 && dress[t.wallRole] != 0) {
						wall = dress[t.wallRole];
					}
					if (t.roofRole >= 0 && dress[t.roofRole] != 0) {
						roof = dress[t.roofRole];
					}
				}
			}
			out.addAll(IntArrayList.of(spec.origin().getX(), spec.origin().getZ(), spec.sizeX(), spec.sizeZ(), spec.facing().get2DDataValue() << 24 | k,
				wall, roof, t.packed(spec)));
		}
		h = out.isEmpty() ? NOTHING : out.toIntArray();
		r.historic = h;
		this.dirty = true;
		return h;
	}

	/**
	 * The index of a historic plot's kind in {@link #kindList}, compiling only the first of a kind; {@code ~index} when this
	 * call compiled it (this plot's own shape and colours, exactly).
	 */
	private int typical(PlotSpec spec) {
		String kind = kind(spec);
		Integer k = this.kindIndex.get(kind);
		if (k != null) {
			return k;
		}
		this.compiles.incrementAndGet();
		FarShape shape = shapeOf(spec, this.terrain);
		Typical t = Typical.NONE;
		if (shape != null) {
			int[] dress = dress(spec);
			t = new Typical(shape, Typical.span(spec), role(dress, shape.wall, false), role(dress, shape.roof, true));
		}
		synchronized (this.kindList) {
			k = this.kindIndex.get(kind);
			if (k != null) {
				return k;
			}
			k = this.kindList.size();
			this.kindList.add(t);
			this.kindIndex.put(kind, k);
		}
		return ~k;
	}

	private int @Nullable [] dress(PlotSpec spec) {
		try {
			return HearthServices.blueprints().dressColours(spec, this.terrain);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** What a silhouette depends on besides size and place: archetype, style, tier and the params that shape the body. */
	static String kind(PlotSpec spec) {
		return spec.archetype() + "|" + spec.style() + "|" + spec.tier() + "|" + spec.paramInt("storeys", -1) + "|" + spec.paramString("kind", "")
			+ "|" + spec.paramString("wall", "") + "|" + spec.paramInt("wall_height", -1) + "|" + (spec.paramInt("stacked_on", -1) >= 0);
	}

	/** A plot's exact shape (a compile), or null where it covers nothing or its archetype no longer exists. */
	static @Nullable FarShape shapeOf(PlotSpec spec, TerrainProbe terrain) {
		try {
			return FarShape.of(HearthServices.blueprints().blueprint(spec, terrain));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static void paint(Long2ObjectOpenHashMap<int[]> chunks, FarShape shape, int ox, int oz, int sx, int sz, Direction facing, int wall, int roof,
		int packed) {
		// the state byte: built unless the caller said planned (historic plots pass a bare colour)
		int w = (wall & STATE) == PLANNED ? wall : wall & 0xFF_FFFF | BUILT;
		int rf = roof | 0xFF00_0000;
		shape.paint(ox, oz, sx, sz, facing, (x, z) -> {
			int[] cols = chunks.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new int[256 * 3]);
			int i = ((z & 15) << 4 | x & 15) * 3;
			cols[i] = w;
			cols[i + 1] = rf;
			cols[i + 2] = packed;
		});
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
					// the file's kind indexes to ours (the same unless kinds were seen before the file was read)
					int[] remap = new int[kinds];
					for (int i = 0; i < kinds; i++) {
						String key = in.readUTF();
						Typical t = Typical.NONE;
						if (in.readBoolean()) {
							int span = in.readInt();
							int wallRole = in.readInt();
							int roofRole = in.readInt();
							int f = in.readInt();
							int d = in.readInt();
							int wall = in.readInt();
							int roof = in.readInt();
							int eave = in.readInt();
							int ridge = in.readInt();
							long[] bits = new long[in.readInt()];
							for (int j = 0; j < bits.length; j++) {
								bits[j] = in.readLong();
							}
							t = new Typical(new FarShape(f, d, bits, wall, roof, eave, ridge), span, wallRole, roofRole);
						}
						synchronized (this.kindList) {
							Integer k = this.kindIndex.get(key);
							if (k == null) {
								k = this.kindList.size();
								this.kindList.add(t);
								this.kindIndex.put(key, k);
							}
							remap[i] = k;
						}
					}
					int count = in.readInt();
					for (int i = 0; i < count; i++) {
						long key = in.readLong();
						int[] h = new int[in.readInt()];
						for (int j = 0; j < h.length; j++) {
							h[j] = in.readInt();
						}
						for (int j = 4; j < h.length; j += REC) {
							int k = h[j] & 0xFF_FFFF;
							h[j] = h[j] & 0xFF00_0000 | (k < remap.length ? remap[k] : 0xFF_FFFF);
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
					List<Typical> kinds;
					String[] keys;
					synchronized (this.kindList) {
						kinds = List.copyOf(this.kindList);
						keys = new String[kinds.size()];
						this.kindIndex.forEach((k, i) -> {
							if (i < keys.length) {
								keys[i] = k;
							}
						});
					}
					out.writeInt(kinds.size());
					for (int i = 0; i < kinds.size(); i++) {
						Typical t = kinds.get(i);
						out.writeUTF(keys[i] == null ? "?" + i : keys[i]);
						FarShape sh = t.shape;
						out.writeBoolean(sh != null);
						if (sh != null) {
							out.writeInt(t.span);
							out.writeInt(t.wallRole);
							out.writeInt(t.roofRole);
							out.writeInt(sh.frontage);
							out.writeInt(sh.depth);
							out.writeInt(sh.wall);
							out.writeInt(sh.roof);
							out.writeInt(sh.eaveRel);
							out.writeInt(sh.ridgeRel);
							out.writeInt(sh.bits.length);
							for (long b : sh.bits) {
								out.writeLong(b);
							}
						}
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
