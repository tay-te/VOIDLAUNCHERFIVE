package dev.voidmc.hearth.sim.atlas;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.Names;
import dev.voidmc.hearth.sim.settlement.PlotFacts;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.api.TerrainProbe;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The world's settlement history, region by region. The world is cut into regions of 1024 × 1024 blocks; for each the
 * atlas decides from the seed whether a settlement was founded there, where, of what culture and how long ago, and then
 * runs its history ({@link History}). Plans are computed on demand by whichever thread first needs one (a world
 * generation worker writing a chunk, the guard keeping a tree off a road, the server adopting a town), cached, and
 * evicted least-recently-used — recomputing gives the same plan, so eviction loses nothing.
 *
 * <p>A site keeps {@code siteMargin} blocks from its region's edges and a history may only build inside its region, so
 * centres are at least twice the margin apart and writing a chunk never needs a neighbour region's plan.
 */
public final class Atlas {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	public static final int REGION_BITS = 10;
	public static final int REGION_SIZE = 1 << REGION_BITS;
	/** Historic settlement ids carry this bit; live ones are counted from 1. */
	public static final long HISTORIC = 1L << 62;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-atlas");
		t.setDaemon(true);
		return t;
	});

	public final LongAdder plansComputed = new LongAdder();
	public final LongAdder plansSettled = new LongAdder();
	public final LongAdder planNanos = new LongAdder();
	public final LongAdder historyNanos = new LongAdder();

	private final ResourceKey<Level> dimension;
	private final TerrainProbe probe;
	/** For the site survey: comparing places needs no foundation-exact ground. */
	private final TerrainProbe survey;
	private final @Nullable BiomeLookup biomes;
	private final SimConfig.Atlas config;
	private final SimRules rules;
	private final @Nullable StructureAvoidance avoidance;
	private final Function<@Nullable Holder<Biome>, Culture> culture;
	private final long seed;
	private final Map<Long, Entry> plans = new ConcurrentHashMap<>();
	private final AtomicLong clock = new AtomicLong();

	private static final class Entry {
		final CompletableFuture<RegionPlan> future = new CompletableFuture<>();
		volatile long used;
	}

	/** The surface biome at a column; absent for synthetic terrain. */
	@FunctionalInterface
	public interface BiomeLookup {
		Holder<Biome> at(int x, int z);
	}

	/**
	 * @param biomes  the surface biome (for cultures and structure biome checks), or null on synthetic terrain
	 * @param culture the culture for a site's biome (given null where there is no biome lookup)
	 */
	public Atlas(ResourceKey<Level> dimension, TerrainProbe probe, @Nullable BiomeLookup biomes, SimRules rules,
		@Nullable StructureAvoidance avoidance, Function<@Nullable Holder<Biome>, Culture> culture) {
		this.dimension = dimension;
		this.probe = probe;
		this.survey = probe instanceof dev.voidmc.hearth.sim.terrain.GeneratorProbe g ? g.rough() : probe;
		this.biomes = biomes;
		this.config = rules.config.atlas();
		this.rules = rules;
		this.avoidance = avoidance;
		this.culture = culture;
		this.seed = probe.seed();
	}

	public TerrainProbe probe() {
		return this.probe;
	}

	public boolean enabled() {
		return this.config.enabled();
	}

	// ---------------------------------------------------------------- regions and ids

	public static int region(int block) {
		return block >> REGION_BITS;
	}

	public static long regionKey(int rx, int rz) {
		return ChunkPos.pack(rx, rz);
	}

	public static long historicId(int rx, int rz) {
		return HISTORIC | (long) (rx + (1 << 20) & 0x1F_FFFF) << 21 | rz + (1 << 20) & 0x1F_FFFF;
	}

	public static boolean isHistoric(long settlementId) {
		return (settlementId & HISTORIC) != 0;
	}

	/** The block rectangle of a region. */
	public static BoundingBox regionBox(int rx, int rz) {
		return new BoundingBox(rx << REGION_BITS, -2048, rz << REGION_BITS, (rx << REGION_BITS) + REGION_SIZE - 1, 4096,
			(rz << REGION_BITS) + REGION_SIZE - 1);
	}

	private long regionSeed(int rx, int rz, long salt) {
		long h = this.seed ^ salt;
		h ^= rx * 0x9E3779B97F4A7C15L;
		h = Long.rotateLeft(h, 29) ^ rz * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		return h ^ (h >>> 29);
	}

	// ---------------------------------------------------------------- plans

	/** The region's plan, computing it on this thread if nobody has yet (blocks while another thread computes it). */
	public RegionPlan plan(int rx, int rz) {
		long key = regionKey(rx, rz);
		Entry e = this.plans.get(key);
		if (e == null) {
			Entry mine = new Entry();
			e = this.plans.putIfAbsent(key, mine);
			if (e == null) {
				e = mine;
				try {
					mine.future.complete(compute(rx, rz));
				} catch (Throwable t) {
					LOG.error("hearth atlas: region {},{} failed; it stays unsettled", rx, rz, t);
					mine.future.complete(empty(rx, rz, 0));
				}
				evict();
			}
		}
		e.used = this.clock.incrementAndGet();
		return e.future.join();
	}

	/** The plan if it is ready, without waiting or computing. */
	public @Nullable RegionPlan planIfReady(int rx, int rz) {
		Entry e = this.plans.get(regionKey(rx, rz));
		if (e == null || !e.future.isDone()) {
			return null;
		}
		e.used = this.clock.incrementAndGet();
		return e.future.getNow(null);
	}

	/** The plan, computed off the server thread (on the atlas worker, never the world generation pool). */
	public CompletableFuture<RegionPlan> planAsync(int rx, int rz) {
		RegionPlan ready = planIfReady(rx, rz);
		if (ready != null) {
			return CompletableFuture.completedFuture(ready);
		}
		return CompletableFuture.supplyAsync(() -> plan(rx, rz), WORKER);
	}

	public int cachedPlans() {
		return this.plans.size();
	}

	private void evict() {
		while (this.plans.size() > this.config.planCache()) {
			Map.Entry<Long, Entry> oldest = null;
			for (Map.Entry<Long, Entry> e : this.plans.entrySet()) {
				if (e.getValue().future.isDone() && (oldest == null || e.getValue().used < oldest.getValue().used)) {
					oldest = e;
				}
			}
			if (oldest == null) {
				return;
			}
			this.plans.remove(oldest.getKey(), oldest.getValue());
		}
	}

	private RegionPlan empty(int rx, int rz, double millis) {
		return new RegionPlan(rx, rz, null, null, List.of(), Long2ObjectMaps.emptyMap(), Long2ObjectMaps.emptyMap(), null, millis, 0);
	}

	private RegionPlan compute(int rx, int rz) {
		long start = System.nanoTime();
		this.plansComputed.increment();
		RandomSource random = RandomSource.create(regionSeed(rx, rz, 0x5173_A71A5L));
		if (!this.config.enabled() || random.nextDouble() >= this.config.density() || !HearthServices.hasBlueprints()) {
			return finish(empty(rx, rz, 0), start);
		}
		int margin = this.config.siteMargin();
		int minX = (rx << REGION_BITS) + margin;
		int minZ = (rz << REGION_BITS) + margin;
		int span = REGION_SIZE - 2 * margin;
		BiomeLookup lookup = this.biomes;
		List<StructureAvoidance.Hazard> hazards = this.avoidance == null || lookup == null ? List.of()
			: this.avoidance.hazards(minX, minZ, minX + span, minZ + span, lookup::at);
		SiteFinder.Spot spot = SiteFinder.find(this.survey, minX, minZ, span, random, (x, z) -> StructureAvoidance.clear(hazards, x, z));
		if (spot == null) {
			return finish(empty(rx, rz, 0), start);
		}
		Holder<Biome> biome = lookup == null ? null : lookup.at(spot.x(), spot.z());
		Culture c = this.culture.apply(biome);
		int age = (int) Math.round(this.config.maxAgeDays() * Math.pow(random.nextDouble(), this.config.ageSkew()));
		String name = Names.settlement(c, random);
		BlockPos center = new BlockPos(spot.x(), this.probe.groundY(spot.x(), spot.z()), spot.z());
		RegionPlan.Site site = new RegionPlan.Site(historicId(rx, rz), name, c, center, age,
			biome == null ? "?" : biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?"), spot.score());
		long historyStart = System.nanoTime();
		SimSettlement history = History.run(site, this.dimension, regionKey(rx, rz), regionSeed(rx, rz, 0x4157_0E1L),
			History.context(this.rules, this.probe, HearthServices.blueprints(), HearthServices.hasPlanner() ? HearthServices.planner() : null,
				regionBox(rx, rz)));
		long historyNanos = System.nanoTime() - historyStart;
		this.historyNanos.add(historyNanos);
		this.plansSettled.increment();
		return finish(index(rx, rz, site, history, historyNanos / 1e6), start);
	}

	private RegionPlan finish(RegionPlan plan, long start) {
		long nanos = System.nanoTime() - start;
		this.planNanos.add(nanos);
		return new RegionPlan(plan.regionX(), plan.regionZ(), plan.site(), plan.history(), plan.plots(), plan.byChunk(), plan.planned(), plan.bounds(),
			nanos / 1e6, plan.historyMillis());
	}

	/** Indexes what world generation needs from a history: completed stages by chunk, and the planned ground. */
	private RegionPlan index(int rx, int rz, RegionPlan.Site site, SimSettlement s, double historyMillis) {
		List<RegionPlan.HistoricPlot> plots = new ArrayList<>();
		Long2ObjectOpenHashMap<IntArrayList> byChunk = new Long2ObjectOpenHashMap<>();
		Long2ObjectMap<long[]> planned = PlotColumns.masks();
		BoundingBox bounds = null;
		for (SimPlot p : s.plots()) {
			PlotColumns.mark(p.spec(), this.config.guardMargin(), planned);
			BoundingBox reach = p.reach();
			bounds = bounds == null ? new BoundingBox(reach.minX(), reach.minY(), reach.minZ(), reach.maxX(), reach.maxY(), reach.maxZ())
				: bounds.encapsulate(reach);
			PlotFacts f = s.facts(p);
			int completed = switch (p.state()) {
				case BUILT, DEMOLISHING -> f.stageCount();
				default -> Math.min(p.stage(), f.stageCount());
			};
			if (completed <= 0) {
				continue;
			}
			int index = plots.size();
			plots.add(new RegionPlan.HistoricPlot(p.id(), p.spec(), completed, f.stageCount(), PlotColumns.isLine(p.spec())));
			for (int cx = reach.minX() >> 4; cx <= reach.maxX() >> 4; cx++) {
				for (int cz = reach.minZ() >> 4; cz <= reach.maxZ() >> 4; cz++) {
					byChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new IntArrayList()).add(index);
				}
			}
		}
		Long2ObjectOpenHashMap<int[]> frozen = new Long2ObjectOpenHashMap<>(byChunk.size());
		byChunk.forEach((k, v) -> frozen.put((long) k, v.toIntArray()));
		return new RegionPlan(rx, rz, site, s, List.copyOf(plots), frozen, planned, bounds, 0, historyMillis);
	}

	// ---------------------------------------------------------------- queries for world generation

	/** The historic plots with completed stages that touch a chunk (computing the region's plan if need be). */
	public List<RegionPlan.HistoricPlot> plotsInChunk(int chunkX, int chunkZ) {
		RegionPlan plan = plan(chunkX >> (REGION_BITS - 4), chunkZ >> (REGION_BITS - 4));
		int[] idx = plan.byChunk().get(ChunkPos.pack(chunkX, chunkZ));
		if (idx == null) {
			return List.of();
		}
		List<RegionPlan.HistoricPlot> out = new ArrayList<>(idx.length);
		for (int i : idx) {
			out.add(plan.plots().get(i));
		}
		return out;
	}

	/** Whether history planned a plot or road on this column (guard margin included). */
	public boolean isPlanned(int x, int z) {
		return this.config.enabled() && plan(region(x), region(z)).isPlanned(x, z);
	}
}
