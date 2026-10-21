package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.atlas.StructureAvoidance;
import dev.voidmc.hearth.sim.roads.RoadWorld;
import dev.voidmc.hearth.sim.roads.Way;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * One level's posts (docs/LIVING_WORLD_2.md §7): the site overlay region by region ({@link PostSites}, worked out on
 * demand by whichever thread first asks and cached), their squares as planned ground ({@code Claims}: trees, Expanse's
 * sights and towns keep off them), and their founding: when a chunk near a post's site loads, its history is worked out
 * on the wild worker ({@link PostHistory}) and the world adopts it, so it is written into the land as its chunks load,
 * whether the region was generated before or after.
 */
public final class PostWorld {
	private static final int REGION_CACHE = 256;
	/** A post is founded when a chunk this far (blocks) from its square loads. */
	static final int FOUND_REACH = 96;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-wild");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** How the siting reads one region's world. */
	@FunctionalInterface
	public interface Lands {
		PostSites.Land land(int rx, int rz);
	}

	private static final class Entry {
		final CompletableFuture<List<PostSite>> future = new CompletableFuture<>();
		volatile long used;
	}

	private final Lands lands;
	private final Supplier<WildConfig.Wild> config;
	private final @Nullable SimWorld world;
	private final Map<Long, Entry> regions = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();
	public final LongAdder regionsComputed = new LongAdder();
	public final LongAdder regionNanos = new LongAdder();
	private volatile long maxRegionNanos;

	// the server thread's founding state
	private final it.unimi.dsi.fastutil.longs.LongSet inFlight = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
	private final it.unimi.dsi.fastutil.longs.LongSet regionsDone = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
	int founded;
	long foundNanos;
	long historyNanos;

	public PostWorld(Lands lands, Supplier<WildConfig.Wild> config, @Nullable SimWorld world) {
		this.lands = lands;
		this.config = config;
		this.world = world;
	}

	/** The posts of a live level, reading its probe, biomes, atlas and roads. */
	public static PostWorld of(SimWorld w) {
		return new PostWorld((rx, rz) -> new WorldLand(w, rx, rz), () -> Wild.config().wild(), w);
	}

	public boolean enabled() {
		return this.config.get().enabled() && this.config.get().perRegion() > 0;
	}

	// ---------------------------------------------------------------- the overlay

	/** The region's posts, worked out on this thread if nobody has (blocks while another thread works them out). */
	public List<PostSite> region(int rx, int rz) {
		long key = Atlas.regionKey(rx, rz);
		Entry e = this.regions.get(key);
		if (e == null) {
			Entry mine = new Entry();
			e = this.regions.putIfAbsent(key, mine);
			if (e == null) {
				e = mine;
				compute(mine, rx, rz);
			}
		}
		e.used = this.clock.incrementAndGet();
		return e.future.join();
	}

	/** The region's posts if worked out, else null (and, if {@code start}, they are begun on the wild worker). */
	public @Nullable List<PostSite> regionIfReady(int rx, int rz, boolean start) {
		long key = Atlas.regionKey(rx, rz);
		Entry e = this.regions.get(key);
		if (e != null && e.future.isDone()) {
			e.used = this.clock.incrementAndGet();
			return e.future.getNow(List.of());
		}
		if (start && e == null) {
			Entry mine = new Entry();
			if (this.regions.putIfAbsent(key, mine) == null) {
				WORKER.execute(() -> compute(mine, rx, rz));
			}
		}
		return null;
	}

	private void compute(Entry e, int rx, int rz) {
		long t0 = System.nanoTime();
		try {
			e.future.complete(enabled() ? PostSites.region(this.lands.land(rx, rz), this.config.get(), rx, rz) : List.of());
		} catch (Throwable t) {
			Hearth.LOG.error("hearth wild: the posts of region {},{} could not be worked out; none stand there", rx, rz, t);
			e.future.complete(List.of());
		}
		long took = System.nanoTime() - t0;
		this.regionsComputed.increment();
		this.regionNanos.add(took);
		if (took > this.maxRegionNanos) {
			this.maxRegionNanos = took;
		}
		evict();
	}

	private void evict() {
		while (this.regions.size() > REGION_CACHE) {
			Map.Entry<Long, Entry> oldest = null;
			for (Map.Entry<Long, Entry> en : this.regions.entrySet()) {
				if (en.getValue().future.isDone() && (oldest == null || en.getValue().used < oldest.getValue().used)) {
					oldest = en;
				}
			}
			if (oldest == null) {
				return;
			}
			this.regions.remove(oldest.getKey(), oldest.getValue());
		}
	}

	/**
	 * Whether a post's square plans this column (Claims). Any thread; on the server thread ({@code now}) a region not yet
	 * worked out is begun in the background and its columns answer planned meanwhile, as the roads' do.
	 */
	public boolean isPlanned(int x, int z, boolean now) {
		if (!enabled()) {
			return false;
		}
		int rx = Atlas.region(x);
		int rz = Atlas.region(z);
		List<PostSite> sites = now ? regionIfReady(rx, rz, true) : region(rx, rz);
		if (sites == null) {
			return true;
		}
		for (PostSite p : sites) {
			if (p.claims(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** The site with this key, if its region is worked out (or work it out now when {@code compute}). */
	public @Nullable PostSite site(long key, boolean compute) {
		int rx = PostSite.regionX(key);
		int rz = PostSite.regionZ(key);
		List<PostSite> sites = compute ? region(rx, rz) : regionIfReady(rx, rz, true);
		if (sites != null) {
			for (PostSite p : sites) {
				if (p.key() == key) {
					return p;
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- founding (server thread)

	/** A chunk loaded: found the posts whose squares it is near. */
	void onChunkLoad(LevelChunk chunk) {
		SimWorld w = this.world;
		if (w == null || !enabled()) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		int rx = pos.x() >> (Atlas.REGION_BITS - 4);
		int rz = pos.z() >> (Atlas.REGION_BITS - 4);
		long region = Atlas.regionKey(rx, rz);
		if (this.regionsDone.contains(region)) {
			return;
		}
		List<PostSite> sites = regionIfReady(rx, rz, true);
		if (sites == null) {
			return;
		}
		PostBook book = w.data(PostBook.KEY);
		boolean all = true;
		for (PostSite site : sites) {
			if (book.known(site.key())) {
				continue;
			}
			all = false;
			if (this.inFlight.contains(site.key())) {
				continue;
			}
			int bx0 = pos.getMinBlockX();
			int bz0 = pos.getMinBlockZ();
			if (bx0 + 15 >= site.minX() - FOUND_REACH && bx0 <= site.maxX() + FOUND_REACH && bz0 + 15 >= site.minZ() - FOUND_REACH
				&& bz0 <= site.maxZ() + FOUND_REACH) {
				found(site);
			}
		}
		if (all) {
			this.regionsDone.add(region);
		}
	}

	/** Why a site cannot be founded now (a live town grew over it), or null. */
	@Nullable String whyNot(PostSite site) {
		SimWorld w = this.world;
		if (w == null) {
			return "no world";
		}
		SimSettlement near = w.nearest(site.center(), 160);
		if (near != null) {
			return near.name() + " stands there";
		}
		near = w.settlementAt(site.x(), site.z(), site.type().radius + 16);
		return near != null ? near.name() + " has built there" : null;
	}

	/** Founds a post: its history off the server thread, then adopted here. Returns false if it cannot be. */
	public boolean found(PostSite site) {
		SimWorld w = this.world;
		if (w == null || this.inFlight.contains(site.key())) {
			return false;
		}
		PostBook book = w.data(PostBook.KEY);
		if (book.known(site.key())) {
			return false;
		}
		String why = whyNot(site);
		if (why != null) {
			book.skip(site.key());
			w.markDirty();
			Hearth.LOG.info("hearth wild: no {} at {}: {}", site.type().getSerializedName(), site.center().toShortString(), why);
			return false;
		}
		long t0 = System.nanoTime();
		long id = w.allocateId();
		TerrainProbe probe = w.probe();
		Culture culture = dev.voidmc.hearth.sim.terrain.CultureVote.at(site.x(), site.z(), w.probe()::biomeHolder, HearthServices::culture);
		String name = PostNames.name(site.type(), culture, PostHistory.random(site));
		var dimension = w.level().dimension();
		var rules = w.rules();
		var blueprints = w.blueprints();
		this.inFlight.add(site.key());
		this.foundNanos += System.nanoTime() - t0;
		CompletableFuture.supplyAsync(() -> {
			long h0 = System.nanoTime();
			SimSettlement s = PostHistory.run(site, id, name, culture, dimension, rules, probe, blueprints);
			this.historyNanos += System.nanoTime() - h0;
			return s;
		}, WORKER).whenComplete((s, t) -> w.level().getServer().execute(() -> {
			this.inFlight.remove(site.key());
			if (dev.voidmc.hearth.sim.SimServer.world(w.level()) != w) {
				return;   // the world unloaded meanwhile: found it again next session
			}
			if (t != null) {
				Hearth.LOG.error("hearth wild: the {} at {} could not be worked out", site.type().getSerializedName(), site.center().toShortString(), t);
				book.skip(site.key());
				w.markDirty();
				return;
			}
			long a0 = System.nanoTime();
			try {
				w.adoptRecord(s);
				book.found(site.key(), s.id());
				this.founded++;
				Hearth.LOG.info("hearth wild: {} ({} {}, {} souls, {} plots, {} days old) at {}", s.name(), s.culture().getSerializedName(),
					site.type().getSerializedName(), s.population(), s.plots().size(), site.age(), site.center().toShortString());
			} catch (RuntimeException e) {
				Hearth.LOG.error("hearth wild: adopting {} failed", s.name(), e);
				book.skip(site.key());
			}
			this.foundNanos += System.nanoTime() - a0;
			w.markDirty();
		}));
		return true;
	}

	/** For {@code /hearth wild stats}. */
	public String describe() {
		long n = this.regionsComputed.sum();
		return String.format(java.util.Locale.ROOT, "%d regions sited (%.1f ms avg, %.1f ms worst), %d cached; %d posts founded this session "
				+ "(%.2f ms avg on the server thread, %.1f ms history off it), %d being worked out",
			n, n == 0 ? 0 : this.regionNanos.sum() / 1e6 / n, this.maxRegionNanos / 1e6, this.regions.size(), this.founded,
			this.founded == 0 ? 0 : this.foundNanos / 1e6 / this.founded, this.founded == 0 ? 0 : this.historyNanos / 1e6 / this.founded,
			this.inFlight.size());
	}

	// ---------------------------------------------------------------- the live world's land

	/** What the siting reads of a live level for one region (probe, biomes, atlas sites, historic roads, structures). */
	static final class WorldLand implements PostSites.Land {
		private final SimWorld w;
		private final int rx;
		private final int rz;
		private @Nullable List<StructureAvoidance.Hazard> hazards;

		WorldLand(SimWorld w, int rx, int rz) {
			this.w = w;
			this.rx = rx;
			this.rz = rz;
		}

		@Override
		public TerrainProbe probe() {
			return this.w.probe().rough();
		}

		@Override
		public String biome(int x, int z) {
			var key = this.w.probe().rough().biome(x, z);
			return key == null ? "" : key.identifier().getPath();
		}

		@Override
		public Culture culture(int x, int z) {
			return HearthServices.culture(this.w.probe().biomeHolder(x, z));
		}

		@Override
		public List<int[]> towns(int rx, int rz) {
			List<int[]> out = new ArrayList<>();
			Atlas atlas = this.w.atlas();
			if (!atlas.enabled()) {
				return out;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					RegionPlan.Site s = atlas.site(rx + dx, rz + dz);
					if (s != null) {
						out.add(new int[]{s.center().getX(), s.center().getZ(), townReach(s.age())});
					}
				}
			}
			return out;
		}

		@Override
		public List<PostSites.RoadLine> roads(int rx, int rz) {
			RoadWorld net = this.w.roadWorld();
			List<PostSites.RoadLine> out = new ArrayList<>();
			if (net == null || !net.enabled()) {
				return out;
			}
			for (Way way : net.region(rx, rz).ways()) {
				if (!way.edge().historic() || way.route() == null || way.spur()) {
					continue;
				}
				var r = way.route();
				out.add(new PostSites.RoadLine(way.id(), way.kind() == RoadNetwork.RoadClass.KINGS_ROAD, r.xs(), r.ys(), r.zs(), r.types()));
			}
			return out;
		}

		@Override
		public boolean clear(int x, int z) {
			List<StructureAvoidance.Hazard> h = this.hazards;
			if (h == null) {
				int x0 = (this.rx << Atlas.REGION_BITS) - 64;
				int z0 = (this.rz << Atlas.REGION_BITS) - 64;
				h = this.w.atlas().hazards(x0, z0, x0 + Atlas.REGION_SIZE + 128, z0 + Atlas.REGION_SIZE + 128);
				this.hazards = h;
			}
			return StructureAvoidance.clear(h, x, z);
		}
	}

	/** How far a town of this age reaches from its heart (the roads' end radius: sim/roads/RoadWorld.endRadius). */
	public static int townReach(int age) {
		return Math.min(220, 72 + Math.max(0, age) / 2);
	}
}
