package dev.voidmc.hearth.sim.roads;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.atlas.StructureAvoidance;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The road network of one level (docs/LIVING_WORLD_2.md §1.2): the historic roads between the atlas's sites, worked out
 * from the seed region by region as world generation (or anyone) needs them, and the live roads begun since, kept in
 * the world's road book. Implements the api's {@link RoadNetwork}.
 *
 * <p>Historic roads need only the sites of the regions round about (cheap, seed-only, {@link Atlas#site}); a road's way
 * is found once by whichever thread first needs it ({@link RoadRouter}) and cached least-recently-used, as the atlas
 * caches its plans. A region's roads — the ways that touch it, their plots by chunk, the planned columns of the built
 * ones — are worked out the same way and cached. Everything here may be read from world generation workers except the
 * live state, which the server thread keeps and publishes as an immutable index.
 */
public final class RoadWorld implements RoadNetwork {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** Ledger ids of road plots carry this bit (the atlas's historic settlements carry bit 62). */
	public static final long ROAD_BIT = 1L << 61;
	/** Regions of sites looked at round a region for the roads that may cross it. */
	private static final int WINDOW = 3;
	/** And for the long roads of places with no neighbour in reach. */
	private static final int FALLBACK_WINDOW = 5;
	private static final int REGION_CACHE = 192;

	/** Where the network's roads are written in a chunk's ledger: the road's id with {@link #ROAD_BIT}. */
	public static long ledgerId(long wayId) {
		return ROAD_BIT | wayId;
	}

	public static boolean isRoad(long ledgerId) {
		return (ledgerId & ROAD_BIT) != 0 && (ledgerId & Atlas.HISTORIC) == 0;
	}

	/** Answers whether a column is planned by a settlement (the atlas's histories, and live plots on the server thread). */
	@FunctionalInterface
	public interface Planned {
		boolean isPlanned(int x, int z);
	}

	private final ResourceKey<Level> dimension;
	private final Atlas atlas;
	private final TerrainProbe probe;
	private final TerrainProbe rough;
	private final Function<Culture, Identifier> styles;
	private final RoadsConfig config;
	private final RoadGraph.Rules rules;
	private final Planned planned;
	private final RoadRouter.Params params;

	private final Map<Long, Entry<Way>> ways = new ConcurrentHashMap<>();
	private final Map<Long, Entry<Region>> regions = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();
	private volatile LiveIndex live = LiveIndex.EMPTY;
	private volatile Danger danger = (id, along) -> 0F;

	public final LongAdder routesComputed = new LongAdder();
	public final LongAdder routeNanos = new LongAdder();
	public final LongAdder routesFailed = new LongAdder();
	public final LongAdder regionsComputed = new LongAdder();
	public final LongAdder regionNanos = new LongAdder();
	/** Route times in ms, the last few hundred, for the p50 and p90 in the stats. */
	private final double[] recent = new double[256];
	private int recentCount;

	/** The roads' worker: live routes and regions the server thread wants without waiting. */
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-roads");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	/** Works out the sites of a window of regions in parallel (only ever site surveys: nothing in it waits). */
	private static final ExecutorService SITES = Executors.newFixedThreadPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)), r -> {
		Thread t = new Thread(r, "hearth-road-sites");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	private static final class Entry<T> {
		final CompletableFuture<T> future = new CompletableFuture<>();
		volatile long used;
	}

	/** How dangerous a road is along its length (the bandits' books), for {@link #danger}. */
	@FunctionalInterface
	public interface Danger {
		float at(long roadId, double along);
	}

	public RoadWorld(ResourceKey<Level> dimension, Atlas atlas, TerrainProbe probe, TerrainProbe rough, Function<Culture, Identifier> styles,
		RoadsConfig config, Planned planned) {
		this.dimension = dimension;
		this.atlas = atlas;
		this.probe = probe;
		this.rough = rough;
		this.styles = styles;
		this.config = config;
		this.rules = new RoadGraph.Rules(config.roads().maxLength(), 4, 200, 30);
		this.planned = planned;
		this.params = RoadRouter.Params.DEFAULTS;
	}

	public ResourceKey<Level> dimension() {
		return this.dimension;
	}

	public RoadsConfig config() {
		return this.config;
	}

	public RoadGraph.Rules rules() {
		return this.rules;
	}

	public TerrainProbe probe() {
		return this.probe;
	}

	public TerrainProbe rough() {
		return this.rough;
	}

	public Atlas atlas() {
		return this.atlas;
	}

	public boolean enabled() {
		return this.config.roads().enabled() && this.atlas.enabled();
	}

	public void setDanger(Danger danger) {
		this.danger = danger;
	}

	/** A fingerprint of everything a historic road's way depends on beyond the seed (a changed one moves the roads). */
	public long fingerprint() {
		long h = 0x5EED_0AD5L;
		h = h * 31 + this.rules.hashCode();
		h = h * 31 + this.params.hashCode();
		h = h * 31 + this.atlas.rules().config.atlas().hashCode();
		h = h * 31 + 3;   // the router's own revision
		return h;
	}

	// ---------------------------------------------------------------- places

	static RoadGraph.Node node(RegionPlan.Site s) {
		return new RoadGraph.Node(s.settlementId(), s.center().getX(), s.center().getZ(), s.age(), s.age() >= 200);
	}

	/** The historic site of a settlement id, or null. */
	public RegionPlan.@Nullable Site site(long settlementId) {
		if (!Atlas.isHistoric(settlementId)) {
			return null;
		}
		RegionPlan.Site s = this.atlas.site(Atlas.historicRegionX(settlementId), Atlas.historicRegionZ(settlementId));
		return s != null && s.settlementId() == settlementId ? s : null;
	}

	/** Sites within {@code radius} of a point. */
	List<RoadGraph.Node> sitesWithin(int x, int z, int radius) {
		List<RoadGraph.Node> out = new ArrayList<>();
		double r2 = (double) radius * radius;
		for (int rx = Atlas.region(x - radius); rx <= Atlas.region(x + radius); rx++) {
			for (int rz = Atlas.region(z - radius); rz <= Atlas.region(z + radius); rz++) {
				RegionPlan.Site s = this.atlas.site(rx, rz);
				if (s != null) {
					double dx = s.center().getX() - x;
					double dz = s.center().getZ() - z;
					if (dx * dx + dz * dz <= r2) {
						out.add(node(s));
					}
				}
			}
		}
		return out;
	}

	/** Works out the sites of a block of regions on the site pool, so a region's first roads do not wait on them one by one. */
	private void warmSites(int rx0, int rz0, int rx1, int rz1) {
		List<CompletableFuture<?>> jobs = new ArrayList<>();
		for (int rx = rx0; rx <= rx1; rx++) {
			for (int rz = rz0; rz <= rz1; rz++) {
				int fx = rx;
				int fz = rz;
				jobs.add(CompletableFuture.runAsync(() -> this.atlas.site(fx, fz), SITES));
			}
		}
		for (CompletableFuture<?> j : jobs) {
			try {
				j.join();
			} catch (RuntimeException e) {
				// the site is worked out again (and its failure logged) when asked for
			}
		}
	}

	// ---------------------------------------------------------------- the historic graph

	/** Whether a place has nobody within a road's length (then it joins its nearest place within half as far again). */
	boolean isolated(RoadGraph.Node n) {
		int len = this.rules.maxLength();
		// rings outward, stopping at the first neighbour in reach: most places find one next door
		int r0x = Atlas.region(n.x());
		int r0z = Atlas.region(n.z());
		int rings = len / Atlas.REGION_SIZE + 1;
		double max2 = (double) len * len;
		for (int ring = 0; ring <= rings; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					RegionPlan.Site s = this.atlas.site(r0x + dx, r0z + dz);
					if (s != null && s.settlementId() != n.id()) {
						double ex = s.center().getX() - n.x();
						double ez = s.center().getZ() - n.z();
						if (ex * ex + ez * ez <= max2) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	/** The historic roads of a place. */
	public List<RoadGraph.Edge> historicEdges(RoadGraph.Node n) {
		return RoadGraph.edgesOf(n, this::sitesWithin, this.rules);
	}

	/** The historic roads that may cross a region: both ends' sites near it, or a fallback road of a lonely place. */
	List<RoadGraph.Edge> edgesNear(int rx, int rz) {
		List<RoadGraph.Node> window = new ArrayList<>();
		for (int x = rx - WINDOW; x <= rx + WINDOW; x++) {
			for (int z = rz - WINDOW; z <= rz + WINDOW; z++) {
				RegionPlan.Site s = this.atlas.site(x, z);
				if (s != null) {
					window.add(node(s));
				}
			}
		}
		int x0 = rx << Atlas.REGION_BITS;
		int z0 = rz << Atlas.REGION_BITS;
		int x1 = x0 + Atlas.REGION_SIZE - 1;
		int z1 = z0 + Atlas.REGION_SIZE - 1;
		Map<Long, RoadGraph.Edge> out = new java.util.TreeMap<>();
		double max2 = (double) this.rules.maxLength() * this.rules.maxLength();
		Map<Long, List<RoadGraph.Node>> cands = new HashMap<>();
		for (int i = 0; i < window.size(); i++) {
			RoadGraph.Node n = window.get(i);
			for (int j = i + 1; j < window.size(); j++) {
				RoadGraph.Node m = window.get(j);
				if (n.distance2(m) > max2 || !reaches(n.x(), n.z(), m.x(), m.z(), x0, z0, x1, z1)) {
					continue;
				}
				List<RoadGraph.Node> cn = cands.computeIfAbsent(n.id(), k -> RoadGraph.candidates(n, sitesWithin(n.x(), n.z(), this.rules.maxLength()), this.rules));
				if (!contains(cn, m.id())) {
					continue;
				}
				List<RoadGraph.Node> cm = cands.computeIfAbsent(m.id(), k -> RoadGraph.candidates(m, sitesWithin(m.x(), m.z(), this.rules.maxLength()), this.rules));
				if (contains(cm, n.id())) {
					RoadGraph.Edge e = RoadGraph.edge(n, m, this.rules);
					out.put(e.id(), e);
				}
			}
		}
		// lonely places: their one long road to the nearest place
		int fallback = this.rules.maxLength() * 3 / 2;
		for (int x = rx - FALLBACK_WINDOW; x <= rx + FALLBACK_WINDOW; x++) {
			for (int z = rz - FALLBACK_WINDOW; z <= rz + FALLBACK_WINDOW; z++) {
				RegionPlan.Site s = this.atlas.site(x, z);
				if (s == null) {
					continue;
				}
				RoadGraph.Node k = node(s);
				if (!isolated(k)) {
					continue;
				}
				RoadGraph.Node m = RoadGraph.nearest(k, sitesWithin(k.x(), k.z(), fallback), fallback);
				if (m != null && reaches(k.x(), k.z(), m.x(), m.z(), x0, z0, x1, z1)) {
					RoadGraph.Edge e = RoadGraph.edge(k, m, this.rules);
					out.put(e.id(), e);
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	private static boolean contains(List<RoadGraph.Node> nodes, long id) {
		for (RoadGraph.Node n : nodes) {
			if (n.id() == id) {
				return true;
			}
		}
		return false;
	}

	/** Whether the ellipse a road between two points may wander in reaches a rectangle. */
	static boolean reaches(int ax, int az, int bx, int bz, int x0, int z0, int x1, int z1) {
		double d = Math.hypot(bx - ax, bz - az);
		int pad = (int) Math.ceil(d * 0.46) + 32;
		return Math.max(ax, bx) + pad >= x0 && Math.min(ax, bx) - pad <= x1 && Math.max(az, bz) + pad >= z0 && Math.min(az, bz) - pad <= z1;
	}

	// ---------------------------------------------------------------- ways

	/** How far from a place's heart its roads end (the town's own arms and lanes take over): by its age at day 0. */
	static int endRadius(int age) {
		return Math.min(220, 72 + Math.max(0, age) / 2);
	}

	/** The point where a road from {@code n} toward {@code m} leaves {@code n}, on dry land. */
	int[] end(int nx, int nz, int radius, int mx, int mz) {
		double d = Math.max(1, Math.hypot(mx - nx, mz - nz));
		double ux = (mx - nx) / d;
		double uz = (mz - nz) / d;
		double r = Math.min(radius, d * 0.4);
		for (int k = 0; k < 24; k++) {
			// out along the bearing, then a little to either side, until the ground is dry
			double off = (k / 2) * 6.0 * (k % 2 == 0 ? 1 : -1);
			int x = (int) Math.round(nx + ux * r - uz * off);
			int z = (int) Math.round(nz + uz * r + ux * off);
			if (!this.probe.isWater(x, z)) {
				return new int[]{x, z};
			}
		}
		return new int[]{(int) Math.round(nx + ux * r), (int) Math.round(nz + uz * r)};
	}

	/** The way of a historic road (worked out on this thread if nobody has yet). */
	public Way way(RoadGraph.Edge e) {
		Entry<Way> entry = this.ways.get(e.id());
		if (entry == null) {
			Entry<Way> mine = new Entry<>();
			entry = this.ways.putIfAbsent(e.id(), mine);
			if (entry == null) {
				entry = mine;
				try {
					mine.future.complete(computeWay(e));
				} catch (Throwable t) {
					LOG.error("hearth roads: the road {} could not be worked out; it stays unbuilt", Long.toHexString(e.id()), t);
					mine.future.complete(Way.of(e, 0, 0, 0, 0, "", "", null, List.of(), false));
				}
				evictWays();
			}
		}
		entry.used = this.clock.incrementAndGet();
		return entry.future.join();
	}

	/** The way if it is worked out already, or null. */
	public @Nullable Way wayIfReady(long id) {
		Entry<Way> e = this.ways.get(id);
		if (e == null || !e.future.isDone()) {
			return null;
		}
		e.used = this.clock.incrementAndGet();
		return e.future.getNow(null);
	}

	private void evictWays() {
		int cap = Math.max(32, this.config.roads().routeCache());
		while (this.ways.size() > cap) {
			Map.Entry<Long, Entry<Way>> oldest = null;
			for (Map.Entry<Long, Entry<Way>> e : this.ways.entrySet()) {
				if (e.getValue().future.isDone() && (oldest == null || e.getValue().used < oldest.getValue().used)) {
					oldest = e;
				}
			}
			if (oldest == null) {
				return;
			}
			this.ways.remove(oldest.getKey(), oldest.getValue());
		}
	}

	private Way computeWay(RoadGraph.Edge e) {
		RegionPlan.Site a = site(e.a());
		RegionPlan.Site b = site(e.b());
		if (a == null || b == null) {
			return Way.of(e, 0, 0, 0, 0, "", "", null, List.of(), false);
		}
		int[] ea = end(a.center().getX(), a.center().getZ(), endRadius(a.age()), b.center().getX(), b.center().getZ());
		int[] eb = end(b.center().getX(), b.center().getZ(), endRadius(b.age()), a.center().getX(), a.center().getZ());
		RoadRouter.Avoid avoid = avoid(ea[0], ea[1], eb[0], eb[1], e.a(), e.b());
		return route(e, ea[0], ea[1], eb[0], eb[1], a.name(), a.culture(), b.name(), b.culture(), avoid, false);
	}

	/** Finds a road's way and cuts it into plots (any thread). */
	Way route(RoadGraph.Edge e, int ax, int az, int bx, int bz, String nameA, Culture cultureA, String nameB, Culture cultureB, RoadRouter.Avoid avoid,
		boolean live) {
		long t0 = System.nanoTime();
		Route r = RoadRouter.route(this.probe, this.rough, ax, az, bx, bz, avoid, this.params);
		long took = System.nanoTime() - t0;
		this.routesComputed.increment();
		this.routeNanos.add(took);
		synchronized (this.recent) {
			this.recent[this.recentCount++ % this.recent.length] = took / 1e6;
		}
		if (r == null) {
			this.routesFailed.increment();
			LOG.debug("hearth roads: no way for the road {} from {} to {}: {}", Long.toHexString(e.id()), nameA, nameB, RoadRouter.lastFailure());
			return Way.of(e, ax, az, bx, bz, nameA, nameB, null, List.of(), live);
		}
		double len = r.length();
		RoadSegments.Dressing dress = new RoadSegments.Dressing(this.styles.apply(cultureA), this.styles.apply(cultureB), sign(nameB, len), sign(nameA, len),
			e.kind() == RoadClass.KINGS_ROAD);
		List<RoadSegments.Segment> segs = RoadSegments.cut(e.id(), e.kind(), r, dress);
		return Way.of(e, ax, az, bx, bz, nameA, nameB, r, segs, live);
	}

	/** What a signpost says of the town a road leads to: its name and how far, in leagues of a thousand blocks. */
	static String sign(String name, double blocks) {
		double leagues = Math.max(0.25, Math.round(blocks / 250.0) / 4.0);
		String whole = leagues >= 1 ? String.valueOf((int) Math.floor(leagues)) : "";
		String frac = switch ((int) Math.round((leagues - Math.floor(leagues)) * 4)) {
			case 1 -> "¼";
			case 2 -> "½";
			case 3 -> "¾";
			default -> "";
		};
		return name + "\n" + whole + frac + (leagues <= 1 ? " league" : " leagues");
	}

	/**
	 * What keeps a road away: the hearts of the other places round about (a road passes a town by, it does not run
	 * through its square) and the structures the atlas keeps settlements clear of (their start chunks and those beside).
	 */
	RoadRouter.Avoid avoid(int ax, int az, int bx, int bz, long a, long b) {
		double d = Math.hypot(bx - ax, bz - az);
		int pad = (int) Math.ceil(d * 0.46) + 64;
		int x0 = Math.min(ax, bx) - pad;
		int z0 = Math.min(az, bz) - pad;
		int x1 = Math.max(ax, bx) + pad;
		int z1 = Math.max(az, bz) + pad;
		List<int[]> cores = new ArrayList<>();
		for (int rx = Atlas.region(x0); rx <= Atlas.region(x1); rx++) {
			for (int rz = Atlas.region(z0); rz <= Atlas.region(z1); rz++) {
				RegionPlan.Site s = this.atlas.site(rx, rz);
				if (s != null && s.settlementId() != a && s.settlementId() != b) {
					cores.add(new int[]{s.center().getX(), s.center().getZ(), endRadius(s.age())});
				}
			}
		}
		LongOpenHashSet structures = new LongOpenHashSet();
		for (StructureAvoidance.Hazard h : this.atlas.hazards(x0, z0, x1, z1)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					structures.add(ChunkPos.pack(h.chunkX() + dx, h.chunkZ() + dz));
				}
			}
		}
		return (x, z) -> {
			if (!structures.isEmpty() && structures.contains(ChunkPos.pack(x >> 4, z >> 4))) {
				return Double.POSITIVE_INFINITY;
			}
			double extra = 0;
			for (int[] c : cores) {
				double dist = Math.hypot(x - c[0], z - c[1]);
				double inner = c[2] * 0.9;
				if (dist < inner) {
					return Double.POSITIVE_INFINITY;
				}
				double outer = c[2] + 220;
				if (dist < outer) {
					double f = 1 - (dist - inner) / (outer - inner);
					extra += 4 * f * f;
				}
			}
			return extra;
		};
	}

	/**
	 * Whether a road plot must not be written because it would run over a settlement's planned ground: a historic town's
	 * plots (asked only where a site is near enough to have them) or a live one's. Deterministic for historic plans.
	 */
	boolean trimmed(Way w, int index) {
		byte t = w.trim()[index];
		if (t != UNKNOWN_TRIM) {
			return t == Way.DROP;
		}
		RoadSegments.Segment s = w.segments().get(index);
		boolean[] drop = {false};
		int[] pts = s.spec().params().getIntArray("points").orElse(new int[0]);
		int width = s.spec().params().getIntOr("width", 3);
		Map<Long, Boolean> nearSite = new HashMap<>();
		RoadSegments.corridor(pts, width, (x, z) -> {
			if (drop[0]) {
				return;
			}
			int rx = Atlas.region(x);
			int rz = Atlas.region(z);
			boolean near = nearSite.computeIfAbsent(Atlas.regionKey(rx, rz), k -> {
				RegionPlan.Site site = this.atlas.site(rx, rz);
				return site != null && site.center().distSqr(new BlockPos(x, site.center().getY(), z)) < 560 * 560;
			});
			if (near && this.atlas.isPlanned(x, z)) {
				drop[0] = true;
			}
		});
		w.trim()[index] = drop[0] ? Way.DROP : Way.KEEP;
		return drop[0];
	}

	private static final byte UNKNOWN_TRIM = Way.UNKNOWN;

	// ---------------------------------------------------------------- regions

	/**
	 * A region's roads.
	 *
	 * @param ways    the ways with plots in the region
	 * @param byChunk chunk to packed {@code way index << 32 | plot index}, the plots kept (not trimmed) touching it
	 * @param mask    chunk to the 256-bit mask of columns planned for built roads (corridor and guard)
	 */
	public record Region(int rx, int rz, List<Way> ways, Long2ObjectMap<long[]> byChunk, Long2ObjectMap<long[]> mask, double millis) {
		public boolean isPlanned(int x, int z) {
			long[] m = this.mask.get(ChunkPos.pack(x >> 4, z >> 4));
			if (m == null) {
				return false;
			}
			int bit = (z & 15) << 4 | x & 15;
			return (m[bit >> 6] >>> (bit & 63) & 1L) != 0;
		}
	}

	/** A region's roads, worked out on this thread if nobody has yet (blocks while another thread works them out). */
	public Region region(int rx, int rz) {
		long key = Atlas.regionKey(rx, rz);
		Entry<Region> e = this.regions.get(key);
		if (e == null) {
			Entry<Region> mine = new Entry<>();
			e = this.regions.putIfAbsent(key, mine);
			if (e == null) {
				e = mine;
				try {
					mine.future.complete(computeRegion(rx, rz));
				} catch (Throwable t) {
					LOG.error("hearth roads: the roads of region {},{} could not be worked out; none are written there", rx, rz, t);
					mine.future.complete(new Region(rx, rz, List.of(), Long2ObjectMaps.emptyMap(), Long2ObjectMaps.emptyMap(), 0));
				}
				evictRegions();
			}
		}
		e.used = this.clock.incrementAndGet();
		return e.future.join();
	}

	/** The region's roads if they are worked out, else null (and, if {@code start}, they are begun on the roads' worker). */
	public @Nullable Region regionIfReady(int rx, int rz, boolean start) {
		Entry<Region> e = this.regions.get(Atlas.regionKey(rx, rz));
		if (e != null && e.future.isDone()) {
			e.used = this.clock.incrementAndGet();
			return e.future.getNow(null);
		}
		if (start && e == null) {
			regionAsync(rx, rz);
		}
		return null;
	}

	public CompletableFuture<Region> regionAsync(int rx, int rz) {
		Region ready = regionIfReady(rx, rz, false);
		if (ready != null) {
			return CompletableFuture.completedFuture(ready);
		}
		return CompletableFuture.supplyAsync(() -> region(rx, rz), WORKER);
	}

	/** Runs work on the roads' worker (live routes). */
	public static CompletableFuture<Void> background(Runnable work) {
		return CompletableFuture.runAsync(work, WORKER);
	}

	private void evictRegions() {
		while (this.regions.size() > REGION_CACHE) {
			Map.Entry<Long, Entry<Region>> oldest = null;
			for (Map.Entry<Long, Entry<Region>> e : this.regions.entrySet()) {
				if (e.getValue().future.isDone() && (oldest == null || e.getValue().used < oldest.getValue().used)) {
					oldest = e;
				}
			}
			if (oldest == null) {
				return;
			}
			this.regions.remove(oldest.getKey(), oldest.getValue());
		}
	}

	private Region computeRegion(int rx, int rz) {
		long t0 = System.nanoTime();
		if (!enabled()) {
			return new Region(rx, rz, List.of(), Long2ObjectMaps.emptyMap(), Long2ObjectMaps.emptyMap(), 0);
		}
		int span = WINDOW + this.rules.maxLength() / Atlas.REGION_SIZE + 1;
		warmSites(rx - span, rz - span, rx + span, rz + span);
		int x0 = rx << Atlas.REGION_BITS;
		int z0 = rz << Atlas.REGION_BITS;
		int x1 = x0 + Atlas.REGION_SIZE - 1;
		int z1 = z0 + Atlas.REGION_SIZE - 1;
		List<Way> touching = new ArrayList<>();
		Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
		Long2ObjectOpenHashMap<long[]> mask = new Long2ObjectOpenHashMap<>();
		for (RoadGraph.Edge e : edgesNear(rx, rz)) {
			Way w = way(e);
			int wi = -1;
			for (int i = 0; i < w.segments().size(); i++) {
				RoadSegments.Segment s = w.segments().get(i);
				if (s.maxX() + RoadSegments.GUARD < x0 || s.minX() - RoadSegments.GUARD > x1 || s.maxZ() + RoadSegments.GUARD < z0
					|| s.minZ() - RoadSegments.GUARD > z1) {
					continue;
				}
				if (trimmed(w, i)) {
					continue;
				}
				if (wi < 0) {
					wi = touching.size();
					touching.add(w);
				}
				index(s, ((long) wi << 32) | i, x0, z0, x1, z1, byChunk);
				if (e.historic()) {
					mark(s, x0, z0, x1, z1, mask);
				}
			}
		}
		Long2ObjectOpenHashMap<long[]> frozen = new Long2ObjectOpenHashMap<>(byChunk.size());
		byChunk.forEach((k, v) -> frozen.put((long) k, v.toLongArray()));
		long took = System.nanoTime() - t0;
		this.regionsComputed.increment();
		this.regionNanos.add(took);
		return new Region(rx, rz, List.copyOf(touching), frozen, mask, took / 1e6);
	}

	/** Lists a plot under every chunk its corridor box touches within the rectangle. */
	static void index(RoadSegments.Segment s, long ref, int x0, int z0, int x1, int z1, Long2ObjectOpenHashMap<LongArrayList> byChunk) {
		for (int cx = Math.max(x0, s.minX()) >> 4; cx <= Math.min(x1, s.maxX()) >> 4; cx++) {
			for (int cz = Math.max(z0, s.minZ()) >> 4; cz <= Math.min(z1, s.maxZ()) >> 4; cz++) {
				byChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new LongArrayList()).add(ref);
			}
		}
	}

	/** Marks a plot's corridor and its guard margin as planned ground in the rectangle. */
	static void mark(RoadSegments.Segment s, int x0, int z0, int x1, int z1, Long2ObjectOpenHashMap<long[]> mask) {
		int[] pts = s.spec().params().getIntArray("points").orElse(new int[0]);
		int width = s.spec().params().getIntOr("width", 3) + 2 * RoadSegments.GUARD;
		RoadSegments.corridor(pts, width, (x, z) -> {
			if (x < x0 || x > x1 || z < z0 || z > z1) {
				return;
			}
			long[] m = mask.computeIfAbsent(ChunkPos.pack(x >> 4, z >> 4), k -> new long[4]);
			int bit = (z & 15) << 4 | x & 15;
			m[bit >> 6] |= 1L << (bit & 63);
		});
	}

	// ---------------------------------------------------------------- planned ground (Claims)

	/**
	 * Whether a road plans this column (its corridor and guard): historic roads built in history, and live roads' plots
	 * built or being built. Any thread; on the server thread ({@code now}) a region not worked out yet is begun in the
	 * background and its columns answer planned meanwhile.
	 */
	public boolean isPlanned(int x, int z, boolean now) {
		if (!enabled()) {
			return false;
		}
		if (this.live.isPlanned(x, z)) {
			return true;
		}
		int rx = Atlas.region(x);
		int rz = Atlas.region(z);
		if (now) {
			Region r = regionIfReady(rx, rz, true);
			return r == null || r.isPlanned(x, z);
		}
		return region(rx, rz).isPlanned(x, z);
	}

	/** As {@link #isPlanned} for the server thread, counting only roads already worked out (nothing is held up waiting). */
	public boolean plannedIfKnown(int x, int z) {
		if (!enabled()) {
			return false;
		}
		if (this.live.isPlanned(x, z)) {
			return true;
		}
		Region r = regionIfReady(Atlas.region(x), Atlas.region(z), true);
		return r != null && r.isPlanned(x, z);
	}

	/** Whether a plot's ground crosses a road's strip (sampled every other column; known roads only). */
	public boolean crosses(dev.voidmc.hearth.api.PlotSpec spec) {
		boolean[] hit = {false};
		dev.voidmc.hearth.sim.atlas.PlotColumns.forEach(spec, 0, (x, z) -> {
			if (!hit[0] && ((x ^ z) & 1) == 0 && plannedIfKnown(x, z)) {
				hit[0] = true;
			}
		});
		return hit[0];
	}

	// ---------------------------------------------------------------- live roads

	/**
	 * The live roads' plots by chunk, as the server thread last published them: what world generation keeps clear and
	 * the applier writes. Immutable.
	 */
	public record LiveIndex(Map<Long, Way> ways, Long2ObjectMap<long[]> byChunk, Long2ObjectMap<long[]> mask) {
		static final LiveIndex EMPTY = new LiveIndex(Map.of(), Long2ObjectMaps.emptyMap(), Long2ObjectMaps.emptyMap());

		boolean isPlanned(int x, int z) {
			if (this.mask.isEmpty()) {
				return false;
			}
			long[] m = this.mask.get(ChunkPos.pack(x >> 4, z >> 4));
			if (m == null) {
				return false;
			}
			int bit = (z & 15) << 4 | x & 15;
			return (m[bit >> 6] >>> (bit & 63) & 1L) != 0;
		}
	}

	public LiveIndex live() {
		return this.live;
	}

	/**
	 * Publishes the live roads (server thread): every live way, and for each the plots that count as planned (built or
	 * being built), so the index and the planned mask follow.
	 */
	public void publishLive(List<Way> ways, java.util.function.BiPredicate<Way, Integer> plannedPlot) {
		Map<Long, Way> byId = new HashMap<>();
		Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
		Long2ObjectOpenHashMap<long[]> mask = new Long2ObjectOpenHashMap<>();
		for (Way w : ways) {
			byId.put(w.id(), w);
			for (int i = 0; i < w.segments().size(); i++) {
				if (plannedPlot.test(w, i)) {
					mark(w.segments().get(i), Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, mask);
				}
			}
		}
		// (live plots are found by way and index: the chunk index lists each way's id once per chunk)
		for (Way w : ways) {
			for (int i = 0; i < w.segments().size(); i++) {
				RoadSegments.Segment s = w.segments().get(i);
				for (int cx = s.minX() >> 4; cx <= s.maxX() >> 4; cx++) {
					for (int cz = s.minZ() >> 4; cz <= s.maxZ() >> 4; cz++) {
						LongArrayList l = byChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new LongArrayList());
						if (!l.contains(w.id())) {
							l.add(w.id());
						}
					}
				}
			}
		}
		Long2ObjectOpenHashMap<long[]> frozen = new Long2ObjectOpenHashMap<>(byChunk.size());
		byChunk.forEach((k, v) -> frozen.put((long) k, v.toLongArray()));
		this.live = new LiveIndex(Map.copyOf(byId), frozen, mask);
	}

	// ---------------------------------------------------------------- RoadNetwork (server thread)

	/** The node a settlement id stands for in the historic graph, or null if it is not a historic site. */
	public RoadGraph.@Nullable Node historicNode(long id) {
		RegionPlan.Site s = site(id);
		return s == null ? null : node(s);
	}

	/** Every way known near a point: historic ones of regions worked out, and live ones. */
	List<Way> waysNear(int x, int z, int radius) {
		Map<Long, Way> out = new java.util.LinkedHashMap<>();
		for (int rx = Atlas.region(x - radius); rx <= Atlas.region(x + radius); rx++) {
			for (int rz = Atlas.region(z - radius); rz <= Atlas.region(z + radius); rz++) {
				Region r = regionIfReady(rx, rz, true);
				if (r != null) {
					for (Way w : r.ways()) {
						out.putIfAbsent(w.id(), w);
					}
				}
			}
		}
		for (Way w : this.live.ways().values()) {
			out.putIfAbsent(w.id(), w);
		}
		return new ArrayList<>(out.values());
	}

	/** The nearest point of a way's route to (x, z): {@code {distance², along}}, or null without a route. */
	static double @Nullable [] nearestOn(Way w, int x, int z) {
		Route r = w.route();
		if (r == null) {
			return null;
		}
		double best = Double.MAX_VALUE;
		double bestAlong = 0;
		double along = 0;
		for (int i = 0; i < r.size(); i++) {
			if (i > 0) {
				along += Math.hypot(r.xs()[i] - r.xs()[i - 1], r.zs()[i] - r.zs()[i - 1]);
			}
			if ((i & 3) != 0 && i != r.size() - 1) {
				continue;
			}
			double dx = r.xs()[i] - x;
			double dz = r.zs()[i] - z;
			double d = dx * dx + dz * dz;
			if (d < best) {
				best = d;
				bestAlong = along;
			}
		}
		return new double[]{best, bestAlong};
	}

	/** How built a way is (0–1): 1 for historic roads, the road book's count for live ones. */
	private volatile Function<Way, Float> built = w -> w.edge().historic() ? 1F : 0F;

	public void setBuilt(Function<Way, Float> built) {
		this.built = built;
	}

	@Override
	public List<RoadView> near(ResourceKey<Level> dimension, BlockPos at, int radius) {
		if (dimension != this.dimension || !enabled()) {
			return List.of();
		}
		List<double[]> found = new ArrayList<>();
		List<Way> ws = waysNear(at.getX(), at.getZ(), radius + 64);
		for (int i = 0; i < ws.size(); i++) {
			double[] n = nearestOn(ws.get(i), at.getX(), at.getZ());
			if (n != null && n[0] <= (double) radius * radius) {
				found.add(new double[]{n[0], i});
			}
		}
		found.sort(Comparator.comparingDouble(f -> f[0]));
		List<RoadView> out = new ArrayList<>(found.size());
		for (double[] f : found) {
			Way w = ws.get((int) f[1]);
			out.add(w.view(this.built.apply(w)));
		}
		return out;
	}

	/** The ways out of a place: its historic roads (once the network round it is worked out) and live ones. */
	public List<Way> waysFrom(long settlementId) {
		List<Way> out = new ArrayList<>();
		for (Way w : this.live.ways().values()) {
			if (!w.spur() && (w.a() == settlementId || w.b() == settlementId)) {
				out.add(w);
			}
		}
		List<RoadGraph.Edge> edges = adjacencyIfReady(settlementId);
		if (edges != null) {
			for (RoadGraph.Edge e : edges) {
				Way w = wayIfReady(e.id());
				if (w == null) {
					wayAsync(e);
				} else if (!containsWay(out, w.id())) {
					out.add(w);
				}
			}
		}
		return out;
	}

	private static boolean containsWay(List<Way> ways, long id) {
		for (Way w : ways) {
			if (w.id() == id) {
				return true;
			}
		}
		return false;
	}

	private final Map<Long, CompletableFuture<List<RoadGraph.Edge>>> adjacency = new ConcurrentHashMap<>();
	private final java.util.Set<Long> wayRequests = ConcurrentHashMap.newKeySet();

	/** A historic place's roads if they are worked out (else begun in the background, and null). */
	public @Nullable List<RoadGraph.Edge> adjacencyIfReady(long settlementId) {
		if (!Atlas.isHistoric(settlementId)) {
			return List.of();
		}
		CompletableFuture<List<RoadGraph.Edge>> f = this.adjacency.computeIfAbsent(settlementId, id -> CompletableFuture.supplyAsync(() -> {
			RoadGraph.Node n = historicNode(id);
			return n == null ? List.<RoadGraph.Edge>of() : historicEdges(n);
		}, WORKER));
		if (this.adjacency.size() > 4096) {
			this.adjacency.clear();
		}
		return f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
	}

	/** Works out a way in the background (once). */
	public void wayAsync(RoadGraph.Edge e) {
		if (wayIfReady(e.id()) == null && this.wayRequests.add(e.id())) {
			CompletableFuture.runAsync(() -> {
				try {
					way(e);
				} finally {
					this.wayRequests.remove(e.id());
				}
			}, WORKER);
		}
	}

	@Override
	public List<RoadView> from(long settlementId) {
		List<RoadView> out = new ArrayList<>();
		for (Way w : waysFrom(settlementId)) {
			out.add(w.view(this.built.apply(w)));
		}
		return out;
	}

	@Override
	public @Nullable RoadView between(long a, long b) {
		for (Way w : waysFrom(a)) {
			if (w.other(a) == b) {
				return w.view(this.built.apply(w));
			}
		}
		return null;
	}

	/** Shortest road path between two places over the roads known now: the ways in order, or null. */
	public @Nullable List<Way> path(long from, long to, int maxHops) {
		if (from == to) {
			return List.of();
		}
		Map<Long, Double> dist = new HashMap<>();
		Map<Long, Way> via = new HashMap<>();
		Map<Long, Integer> hops = new HashMap<>();
		PriorityQueue<double[]> q = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
		Map<Integer, Long> ids = new HashMap<>();
		int[] seq = {0};
		dist.put(from, 0.0);
		hops.put(from, 0);
		ids.put(0, from);
		q.add(new double[]{0, 0});
		int expanded = 0;
		while (!q.isEmpty() && expanded++ < 256) {
			double[] top = q.poll();
			long u = ids.get((int) top[1]);
			if (top[0] > dist.getOrDefault(u, Double.MAX_VALUE)) {
				continue;
			}
			if (u == to) {
				break;
			}
			if (hops.get(u) >= maxHops) {
				continue;
			}
			for (Way w : waysFrom(u)) {
				long v = w.other(u);
				double nd = top[0] + w.length();
				if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
					dist.put(v, nd);
					via.put(v, w);
					hops.put(v, hops.get(u) + 1);
					int id = ++seq[0];
					ids.put(id, v);
					q.add(new double[]{nd, id});
				}
			}
		}
		if (!via.containsKey(to)) {
			return null;
		}
		List<Way> out = new ArrayList<>();
		for (long k = to; k != from; ) {
			Way w = via.get(k);
			out.add(0, w);
			k = w.other(k);
		}
		return out;
	}

	@Override
	public double roadDistance(long a, long b) {
		List<Way> p = path(a, b, 8);
		if (p == null) {
			return -1;
		}
		double len = 0;
		for (Way w : p) {
			len += w.length();
		}
		return len;
	}

	@Override
	public float danger(long roadId, double along) {
		return this.danger.at(roadId, along);
	}

	/** A way known by id now (historic in a worked-out region or cache, or live), or null. */
	public @Nullable Way known(long id) {
		Way w = this.live.ways().get(id);
		return w != null ? w : wayIfReady(id);
	}

	// ---------------------------------------------------------------- stats

	public String describe() {
		double[] times;
		synchronized (this.recent) {
			int n = Math.min(this.recentCount, this.recent.length);
			times = java.util.Arrays.copyOf(this.recent, n);
		}
		java.util.Arrays.sort(times);
		double p50 = times.length == 0 ? 0 : times[times.length / 2];
		double p90 = times.length == 0 ? 0 : times[Math.min(times.length - 1, (int) (times.length * 0.9))];
		long routes = this.routesComputed.sum();
		long regs = this.regionsComputed.sum();
		return String.format(Locale.ROOT, "%d routes (%d failed; p50 %.1f ms, p90 %.1f ms), %d road regions (%.0f ms avg), %d sites (%.1f ms avg), %d live roads",
			routes, this.routesFailed.sum(), p50, p90, regs, regs == 0 ? 0 : this.regionNanos.sum() / 1e6 / regs, this.atlas.sitesComputed.sum(),
			this.atlas.sitesComputed.sum() == 0 ? 0 : this.atlas.siteNanos.sum() / 1e6 / this.atlas.sitesComputed.sum(), this.live.ways().size());
	}
}
