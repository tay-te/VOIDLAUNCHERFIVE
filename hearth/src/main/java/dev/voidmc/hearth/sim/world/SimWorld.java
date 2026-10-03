package dev.voidmc.hearth.sim.world;

import com.mojang.serialization.DynamicOps;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResidentFactory;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.LodFootprints;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.apply.ChunkApplier;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.atlas.StructureAvoidance;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.Names;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.GeneratorProbe;
import dev.voidmc.hearth.sim.terrain.Probes;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hearth in one level: its live settlements, the atlas of its history, persistence, the chunk applier, adoption and the
 * spatial index. It is also the {@link SimContext} its settlements run in, so the model reaches the world (events, the
 * applier, the planner budget) only through here. Server thread only, except {@link #isPlanned} and the atlas, which
 * world generation reads.
 */
public final class SimWorld implements HearthWorld, SimContext {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");

	private final ServerLevel level;
	private final SimServer server;
	private final GeneratorProbe probe;
	private final Atlas atlas;
	private final SettlementStore store;
	private final SimIndex index;
	private final SpatialIndex spatial;
	private final ChunkApplier applier;
	private final LodFootprints footprints;
	private final Long2ObjectLinkedOpenHashMap<SimSettlement> settlements = new Long2ObjectLinkedOpenHashMap<>();
	private final DynamicOps<Tag> ops;
	/** Regions whose plan is being computed for adoption, and regions known to hold nobody (this session). */
	private final LongSet adopting = new LongOpenHashSet();
	private final LongSet unsettled = new LongOpenHashSet();
	private final Map<UUID, Long> playerIn = new HashMap<>();
	private final java.util.ArrayDeque<SimSettlement> saveQueue = new java.util.ArrayDeque<>();
	private boolean quiet;
	private boolean fastForward;

	public SimWorld(ServerLevel level, SimServer server) {
		this.level = level;
		this.server = server;
		SimRules rules = server.rules();
		this.probe = Probes.create(level, rules.config.probeCorrection());
		StructureAvoidance avoidance = null;
		try {
			avoidance = new StructureAvoidance(level, rules.config.atlas());
		} catch (RuntimeException e) {
			LOG.warn("hearth: structure avoidance unavailable in {}", level.dimension().identifier(), e);
		}
		this.atlas = new Atlas(level.dimension(), this.probe, this.probe::biomeHolder, rules, avoidance, HearthServices::culture);
		this.ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
		Path data = level.getServer().getWorldPath(LevelResource.DATA).resolve("hearth").resolve("settlements");
		if (!level.dimension().identifier().getPath().equals("overworld") || !level.dimension().identifier().getNamespace().equals("minecraft")) {
			data = data.resolve(level.dimension().identifier().getNamespace() + "_" + level.dimension().identifier().getPath().replace('/', '_'));
		}
		this.store = new SettlementStore(data);
		this.index = level.getDataStorage().computeIfAbsent(SimIndex.TYPE);
		this.index.data().setOps(this.ops);
		var embody = rules.config.embody();
		this.spatial = new SpatialIndex(embody.radius() + embody.hysteresis(), rules.config.atlas().guardMargin());
		this.applier = new ChunkApplier(level, this.spatial, blueprints(), this.probe);
		this.footprints = new LodFootprints(this.atlas, this.probe);
		LOG.info("hearth: {} uses the {} probe; {} structure sets avoided", level.dimension().identifier(), this.probe.describe(),
			avoidance == null ? 0 : avoidance.setCount());
	}

	/** Reads the level's settlements from disk and puts them on the clock. */
	public void load() {
		List<SimSettlement> loaded = this.store.loadAll(this.ops);
		for (SimSettlement s : loaded) {
			register(s, false);
		}
		// the files are the truth: drop index entries whose file is gone, add those it missed
		for (SimIndex.Entry e : this.index.entries()) {
			if (!this.settlements.containsKey(e.id())) {
				this.index.remove(e.id());
			}
		}
		LOG.info("hearth: {} settlements loaded in {}", loaded.size(), this.level.dimension().identifier());
	}

	private void register(SimSettlement s, boolean isNew) {
		s.bind(this);
		s.dataMap().setOps(this.ops);
		s.recomputeDerived();
		this.settlements.put(s.id(), s);
		this.spatial.update(s);
		this.index.put(entry(s));
		if (s.originRegion() != SimSettlement.NO_REGION) {
			this.index.markAdopted(s.originRegion());
		}
		this.footprints.refresh(s);
		this.server.schedule(this, s, isNew);
	}

	private static SimIndex.Entry entry(SimSettlement s) {
		BoundingBox b = s.bounds();
		return new SimIndex.Entry(s.id(), s.name(), s.center(), new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
	}

	// ---------------------------------------------------------------- HearthWorld

	@Override
	public ServerLevel level() {
		return this.level;
	}

	@Override
	public TerrainProbe terrain() {
		return this.probe;
	}

	public GeneratorProbe probe() {
		return this.probe;
	}

	@Override
	public long day() {
		return Math.floorDiv(clock(), SimRules.DAY);
	}

	/** The overworld clock: what sim time follows (sleeping through the night moves it, and the towns, forward). */
	public long clock() {
		return this.level.getServer().overworld().getOverworldClockTime();
	}

	/** Where a settlement's own clock should be: the world clock plus any time it was fast-forwarded. */
	public long simNow(SimSettlement s) {
		return clock() + s.timeOffset();
	}

	@Override
	public Collection<SimSettlement> settlements() {
		return Collections.unmodifiableCollection(this.settlements.values());
	}

	@Override
	public @Nullable SimSettlement settlement(long id) {
		return this.settlements.get(id);
	}

	@Override
	public @Nullable SimSettlement settlementAt(BlockPos pos) {
		return settlementAt(pos.getX(), pos.getZ(), 8);
	}

	/** The settlement whose bounds, grown by {@code margin}, contain the column. */
	public @Nullable SimSettlement settlementAt(int x, int z, int margin) {
		SimSettlement best = null;
		for (long id : this.spatial.near(x, z)) {
			SimSettlement s = this.settlements.get(id);
			if (s == null) {
				continue;
			}
			BoundingBox b = s.bounds();
			if (x >= b.minX() - margin && x <= b.maxX() + margin && z >= b.minZ() - margin && z <= b.maxZ() + margin) {
				if (best == null || s.center().distSqr(new BlockPos(x, s.center().getY(), z)) < best.center().distSqr(new BlockPos(x, best.center().getY(), z))) {
					best = s;
				}
			}
		}
		return best;
	}

	@Override
	public @Nullable SimSettlement nearest(BlockPos pos, int maxDistance) {
		SimSettlement best = null;
		double bestD = (double) maxDistance * maxDistance;
		for (SimSettlement s : this.settlements.values()) {
			double dx = s.center().getX() - pos.getX();
			double dz = s.center().getZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}

	@Override
	public <T> T data(HearthDataKey<T> key) {
		return this.index.data().get(key);
	}

	@Override
	public void markDirty() {
		this.index.setDirty();
	}

	public Atlas atlas() {
		return this.atlas;
	}

	public ChunkApplier applier() {
		return this.applier;
	}

	public SpatialIndex spatial() {
		return this.spatial;
	}

	public SimIndex index() {
		return this.index;
	}

	/** Built plots' silhouettes by column, for VOID LOD. */
	public LodFootprints footprints() {
		return this.footprints;
	}

	public SettlementStore store() {
		return this.store;
	}

	/** Whether world generation should keep features off this column: history or a live settlement plans to build there. */
	public boolean isPlanned(int x, int z) {
		return this.spatial.isPlanned(x, z) || this.atlas.isPlanned(x, z);
	}

	// ---------------------------------------------------------------- SimContext

	@Override
	public SimRules rules() {
		return this.server.rules();
	}

	@Override
	public BlueprintService blueprints() {
		return HearthServices.blueprints();
	}

	@Override
	public @Nullable SettlementPlanner planner() {
		return HearthServices.hasPlanner() ? HearthServices.planner() : null;
	}

	@Override
	public ResidentFactory residents() {
		return HearthServices.residents();
	}

	@Override
	public @Nullable HearthWorld eventWorld() {
		return this.quiet ? null : this;
	}

	@Override
	public long gameTime() {
		return this.level.getGameTime();
	}

	@Override
	public boolean tryPlannerCall() {
		// an explicit fast-forward plans as much as it needs; the clock shares one call per tick
		return this.fastForward || this.server.tryPlannerCall();
	}

	@Override
	public void plotsChanged(SimSettlement s) {
		if (this.settlements.get(s.id()) == s) {
			this.spatial.update(s);
			this.index.put(entry(s));
			this.footprints.refresh(s);
		}
	}

	@Override
	public void progressed(SimSettlement s, SimPlot p) {
		this.applier.enqueuePlot(p);
		if (p.isStanding() && this.settlements.get(s.id()) == s) {
			this.footprints.built(s, p);
		}
	}

	@Override
	public void placedByEntity(SimSettlement s, SimPlot p, long from, long to) {
		this.applier.placedByEntity(s, p, from, to);
	}

	@Override
	public void dirty(SimSettlement s) {
		// the settlement carries its own dirty flag; saving collects dirty ones
	}

	// ---------------------------------------------------------------- life cycle

	/** Writes every changed settlement (on the game's save). */
	/**
	 * The game is saving. A flushing save (shutdown, {@code /save-all flush}) writes every changed settlement now; an
	 * autosave only queues them, and {@link #saveSome} writes the queue a slice per tick, so a server with hundreds of
	 * towns never stalls a tick encoding them all at once.
	 */
	public int save(boolean flush) {
		if (!flush) {
			for (SimSettlement s : this.settlements.values()) {
				if (s.isDirty() && !this.saveQueue.contains(s)) {
					this.saveQueue.add(s);
				}
			}
			return 0;
		}
		this.saveQueue.clear();
		int n = 0;
		for (SimSettlement s : this.settlements.values()) {
			if (s.isDirty()) {
				saveNow(s);
				n++;
			}
		}
		this.store.flush();
		return n;
	}

	/** Writes queued settlements until the budget is spent; returns how many. */
	public int saveSome(long budgetNanos) {
		long start = System.nanoTime();
		int n = 0;
		while (!this.saveQueue.isEmpty() && System.nanoTime() - start < budgetNanos) {
			SimSettlement s = this.saveQueue.poll();
			if (s.isDirty() && this.settlements.get(s.id()) == s) {
				saveNow(s);
				n++;
			}
		}
		return n;
	}

	private void saveNow(SimSettlement s) {
		this.store.save(s, this.ops);
		this.index.put(entry(s));
	}

	/** A chunk finished loading: write what is owed to it, and adopt the history it belongs to. */
	public void onChunkLoad(LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		if (this.spatial.hasPlots(pos.pack())) {
			this.applier.enqueue(pos.pack());
		}
		if (!this.atlas.enabled()) {
			return;
		}
		int rx = pos.x() >> (Atlas.REGION_BITS - 4);
		int rz = pos.z() >> (Atlas.REGION_BITS - 4);
		long region = Atlas.regionKey(rx, rz);
		if (this.index.isAdopted(region) || this.unsettled.contains(region) || this.adopting.contains(region)) {
			return;
		}
		RegionPlan plan = this.atlas.planIfReady(rx, rz);
		if (plan != null) {
			considerAdoption(plan, pos);
			return;
		}
		this.adopting.add(region);
		this.atlas.planAsync(rx, rz).whenComplete((p, t) -> this.level.getServer().execute(() -> {
			this.adopting.remove(region);
			if (p != null) {
				considerAdoption(p, pos);
			}
		}));
	}

	private void considerAdoption(RegionPlan plan, ChunkPos pos) {
		long region = Atlas.regionKey(plan.regionX(), plan.regionZ());
		if (!plan.settled() || plan.bounds() == null) {
			this.unsettled.add(region);
			return;
		}
		int m = rules().config.atlas().adoptMargin();
		BoundingBox b = plan.bounds();
		if (pos.getMaxBlockX() >= b.minX() - m && pos.getMinBlockX() <= b.maxX() + m && pos.getMaxBlockZ() >= b.minZ() - m
			&& pos.getMinBlockZ() <= b.maxZ() + m) {
			adopt(plan);
		}
	}

	/**
	 * Makes a historic settlement live: a copy of its history, peopled by the resident factory (one new person for each of
	 * the history's, keeping their age, trade, home and family), then {@code FOUNDED}, and the years since day 0 caught
	 * up as ordinary simulation.
	 */
	public @Nullable SimSettlement adopt(RegionPlan plan) {
		RegionPlan.Site site = plan.site();
		long region = Atlas.regionKey(plan.regionX(), plan.regionZ());
		if (site == null || plan.history() == null) {
			return null;
		}
		SimSettlement existing = this.settlements.get(site.settlementId());
		if (existing != null) {
			this.index.markAdopted(region);
			return existing;
		}
		long start = System.nanoTime();
		SimSettlement s = plan.history().copy();
		s.bind(this);
		this.quiet = true;
		try {
			repopulate(s);
		} finally {
			this.quiet = false;
		}
		s.markDirty();
		register(s, true);
		this.index.markAdopted(region);
		this.server.stats().adoptions.increment();
		this.server.stats().adoptNanos.add(System.nanoTime() - start);
		LOG.info("hearth: adopted {} ({} {}, {} souls, {} plots, founded {} days before the world) at {}", s.name(), s.culture().getSerializedName(),
			s.tier().getSerializedName(), s.population(), s.plots().size(), site.age(), s.center().toShortString());
		SettlementEvents.FOUNDED.invoker().onFounded(this, s);
		for (SimPlot p : s.plots()) {
			this.applier.enqueuePlot(p);
		}
		return s;
	}

	/** Replaces the history's plain people with the resident factory's, keeping who they are to each other. */
	private void repopulate(SimSettlement s) {
		List<Resident> old = s.residentList();
		Map<UUID, Resident> made = new HashMap<>();
		RandomSource random = s.random();
		List<Resident> fresh = new ArrayList<>(old.size());
		for (Resident o : old) {
			Resident a = parent(o, "parent_a", made);
			Resident b = parent(o, "parent_b", made);
			Resident r = residents().create(s, o.age, random, a, b);
			r.age = o.age;
			r.bornDay = o.bornDay;
			r.job = o.job;
			r.home = o.home;
			r.work = o.work;
			if (a != null) {
				r.extra("sim").putString("parent_a", a.id.toString());
			}
			if (b != null) {
				r.extra("sim").putString("parent_b", b.id.toString());
			}
			made.put(o.id, r);
			fresh.add(r);
		}
		s.replaceResidents(fresh);
	}

	private static @Nullable Resident parent(Resident child, String key, Map<UUID, Resident> made) {
		String id = child.extra.getCompoundOrEmpty("sim").getStringOr(key, "");
		if (id.isEmpty()) {
			return null;
		}
		try {
			return made.get(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Why a settlement cannot be founded here, or null if it can: not on water, and not within {@code spacing} blocks of
	 * a live settlement's centre or a historic one's.
	 */
	public @Nullable String whyNotFound(BlockPos at, int spacing) {
		if (this.probe.isWater(at.getX(), at.getZ())) {
			return "that is water";
		}
		SimSettlement near = nearest(at, spacing);
		if (near != null) {
			return near.name() + " is too close";
		}
		if (this.atlas.enabled()) {
			RegionPlan plan = this.atlas.plan(Atlas.region(at.getX()), Atlas.region(at.getZ()));
			if (plan.site() != null && plan.site().center().distManhattan(at) < spacing * 1.4) {
				return plan.site().name() + " (not yet visited) stands near here";
			}
		}
		return null;
	}

	/** Founds a live settlement at a spot (a command, a quest, settlers from a town that outgrew its land). */
	public SimSettlement found(BlockPos at, @Nullable Culture culture, @Nullable String name) {
		int y = this.probe.groundY(at.getX(), at.getZ());
		BlockPos center = new BlockPos(at.getX(), y, at.getZ());
		Culture c = culture != null ? culture
			: dev.voidmc.hearth.sim.terrain.CultureVote.at(at.getX(), at.getZ(), this.probe::biomeHolder, b -> HearthServices.culture(b));
		long id = this.index.allocateId();
		RandomSource random = RandomSource.create(id * 0x9E3779B97F4A7C15L ^ this.level.getSeed());
		String n = name != null ? name : Names.settlement(c, random);
		long day = day();
		SimSettlement s = new SimSettlement(id, n, c, this.level.dimension(), center, day, SimSettlement.NO_REGION, random.nextLong());
		s.bind(this);
		this.quiet = true;
		try {
			var founding = rules().config.founding();
			SettlementClock.found(s, this, founding.residents(), founding.goodsMap(), founding.treasury());
		} finally {
			this.quiet = false;
		}
		s.setSimTick(simNow(s));
		s.markDirty();
		register(s, true);
		LOG.info("hearth: founded {} ({}) at {}", s.name(), c.getSerializedName(), center.toShortString());
		SettlementEvents.FOUNDED.invoker().onFounded(this, s);
		return s;
	}

	/** Runs a settlement's clock forward by {@code days} at once (a debug command and the dev harness). */
	public long simulate(SimSettlement s, int days) {
		long start = System.nanoTime();
		s.addTimeOffset(days * SimRules.DAY);
		long target = simNow(s);
		this.fastForward = true;
		try {
			while (s.simTick() < target) {
				if (SettlementClock.advance(s, target) <= 0) {
					break;
				}
			}
		} finally {
			this.fastForward = false;
		}
		for (SimPlot p : s.plots()) {
			this.applier.enqueuePlot(p);
		}
		return System.nanoTime() - start;
	}

	/** Players near settlements: embodiment and arrival banners. Called every 20 ticks. */
	public Map<UUID, Long> playerPresence() {
		return this.playerIn;
	}

	/** Ages/residents helper for commands: counts by age stage. */
	public static int[] ages(SimSettlement s) {
		int[] n = new int[AgeStage.values().length];
		for (Resident r : s.residents()) {
			n[r.age.ordinal()]++;
		}
		return n;
	}
}
