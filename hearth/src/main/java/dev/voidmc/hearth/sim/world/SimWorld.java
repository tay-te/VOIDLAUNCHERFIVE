package dev.voidmc.hearth.sim.world;

import com.mojang.serialization.DynamicOps;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
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
import dev.voidmc.hearth.api.UndergroundProbe;
import dev.voidmc.hearth.sim.land.Land;
import dev.voidmc.hearth.sim.land.LandState;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.land.Survey;
import dev.voidmc.hearth.sim.land.Territory;
import dev.voidmc.hearth.sim.terrain.Undergrounds;
import dev.voidmc.hearth.sim.trade.Caravans;
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
	/** Where live settlement ids are handed out: the overworld's index. */
	private final SimIndex ids;
	private final SpatialIndex spatial;
	private final ChunkApplier applier;
	private final LodFootprints footprints;
	private final Long2ObjectLinkedOpenHashMap<SimSettlement> settlements = new Long2ObjectLinkedOpenHashMap<>();
	private final DynamicOps<Tag> ops;
	/** Treasury an adopted settlement keeps per head when the economy module runs its books (it seeds new ones at 40). */
	static final long ADOPTED_TREASURY_PER_HEAD = 60;
	/** Regions whose plan is being computed for adoption, and regions known to hold nobody (this session). */
	private final LongSet adopting = new LongOpenHashSet();
	private final LongSet unsettled = new LongOpenHashSet();
	private final java.util.ArrayDeque<SimSettlement> saveQueue = new java.util.ArrayDeque<>();
	private boolean quiet;
	private boolean fastForward;
	private final Hamlets hamlets = new Hamlets(this);
	private final @Nullable UndergroundProbe underground;
	private final Caravans caravans = new Caravans(this);
	private final Woodcutter woodcutter = new Woodcutter(this);
	private int surveys;
	private final java.util.concurrent.atomic.LongAdder surveyNanos = new java.util.concurrent.atomic.LongAdder();

	public SimWorld(ServerLevel level, SimServer server) {
		this.level = level;
		this.server = server;
		SimRules rules = server.rules();
		this.probe = Probes.create(level, rules.config.probeCorrection());
		this.underground = Undergrounds.create(level);
		this.probe.setUnderground(this.underground);
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
		// live ids come from the overworld's index whatever level a settlement is in, so they are unique on the server
		// (other modules key per-player state by settlement id alone)
		this.ids = level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? this.index
			: level.getServer().overworld().getDataStorage().computeIfAbsent(SimIndex.TYPE);
		var embody = rules.config.embody();
		this.spatial = new SpatialIndex(embody.radius() + embody.hysteresis(), rules.config.atlas().guardMargin());
		this.applier = new ChunkApplier(level, this.spatial, blueprints(), this.probe, () -> !INLINE_BLUEPRINTS);
		this.footprints = new LodFootprints(this.atlas, this.probe, level);
		LOG.info("hearth: {} uses the {} probe; {} structure sets avoided", level.dimension().identifier(), this.probe.describe(),
			avoidance == null ? 0 : avoidance.setCount());
	}

	/** Reads the level's settlements from disk and puts them on the clock. */
	public void load() {
		List<SimSettlement> loaded = this.store.loadAll(this.ops);
		int bad = 0;
		for (SimSettlement s : loaded) {
			if (!Atlas.isHistoric(s.id())) {
				// ids are never handed out twice, even if the index was saved before a settlement it counted (a crash)
				this.ids.reserveId(s.id());
			}
			try {
				register(s, false);
			} catch (RuntimeException e) {
				// one settlement that will not come back must not keep the world from loading: its file stays for a fix
				LOG.error("hearth: settlement {} ({}) could not be loaded; left out of this session, its file kept", s.id(), s.name(), e);
				this.settlements.remove(s.id());
				this.spatial.remove(s.id());
				bad++;
			}
		}
		// the files are the truth: drop index entries whose file is gone, add those it missed
		for (SimIndex.Entry e : this.index.entries()) {
			if (!this.settlements.containsKey(e.id())) {
				this.index.remove(e.id());
			}
		}
		LOG.info("hearth: {} settlements loaded in {}", loaded.size() - bad, this.level.dimension().identifier());
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
		if (s.land().surveyed() == 0) {
			// a phase 2 save, or a settlement whose survey never finished: survey its land now (off the server thread)
			survey(s);
		}
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
	@Override
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
		return this.spatial.isPlanned(x, z) || this.atlas.isPlanned(x, z) || Claims.isPlanned(this, x, z, false);
	}

	/**
	 * As {@link #isPlanned}, for the server thread: it never works out a region's history here (that takes most of a
	 * second), so a column in a region not yet planned counts as planned until the atlas worker has done it.
	 */
	public boolean isPlannedNow(int x, int z) {
		if (this.spatial.isPlanned(x, z) || Claims.isPlanned(this, x, z, true)) {
			return true;
		}
		if (!this.atlas.enabled()) {
			return false;
		}
		int rx = Atlas.region(x);
		int rz = Atlas.region(z);
		RegionPlan plan = this.atlas.planIfReady(rx, rz);
		if (plan == null) {
			long key = Atlas.regionKey(rx, rz);
			if (this.planning.add(key)) {
				this.atlas.planAsync(rx, rz).whenComplete((p, t) -> this.level.getServer().execute(() -> this.planning.remove(key)));
			}
			return true;
		}
		return plan.isPlanned(x, z);
	}

	/** Regions whose plan was asked of the atlas worker from the server thread and is not back yet. */
	private final LongSet planning = new LongOpenHashSet();

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

	/** A loaded chunk became reachable from the server thread: write what is owed to it. */
	public void onChunkReachable(LevelChunk chunk) {
		long key = chunk.getPos().pack();
		if (this.spatial.hasPlots(key)) {
			this.applier.enqueue(key);
		}
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
			if (t != null) {
				LOG.error("hearth: the history of region {},{} in {} could not be worked out; it stays unsettled this session", rx, rz,
					this.level.dimension().identifier(), t);
				this.unsettled.add(region);
			} else if (p != null && SimServer.world(this.level) == this) {
				// (not once this world has been unloaded: a server stopping while the plan was made)
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
			try {
				adopt(plan);
			} catch (RuntimeException e) {
				// a bug in the resident factory or a FOUNDED listener: the town is not adopted this session (its region is not
				// marked adopted, so a later session tries again), and the chunk loading goes on
				LOG.error("hearth: adopting the settlement of region {},{} failed; trying again next session", plan.regionX(), plan.regionZ(), e);
				this.unsettled.add(region);
				RegionPlan.Site site = plan.site();
				if (site != null && this.settlements.remove(site.settlementId()) != null) {
					this.spatial.remove(site.settlementId());
					this.index.remove(site.settlementId());
					this.index.unmarkAdopted(region);
				}
			}
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
		if (economyHandlesWages()) {
			// a history piles up coin with nobody to pay (no upkeep before the world began): keep what the economy would
			// expect of a town this size, the economy's daily books take it from there
			s.withdraw(Math.max(0, s.treasury() - ADOPTED_TREASURY_PER_HEAD * Math.max(4, s.population())));
		}
		s.markDirty();
		register(s, true);
		this.index.markAdopted(region);
		this.server.stats().adoptions.increment();
		this.server.stats().adoptNanos.add(System.nanoTime() - start);
		LOG.info("hearth: adopted {} ({} {}, {} souls, {} plots, founded {} days before the world) at {}", s.name(), s.culture().getSerializedName(),
			s.tier().getSerializedName(), s.population(), s.plots().size(), site.age(), s.center().toShortString());
		fireFounded(s);
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

	/** What {@link #whyNotFound(BlockPos)} says while the land's history is still being worked out off the server thread. */
	public static final String SURVEYING = "the land there is still being surveyed";

	/**
	 * Why a settlement cannot be founded here, or null if it can: not on water, not inside or within {@code spacing}
	 * blocks of a live settlement's heart, and not by a historic one's (its heart or its planned ground). Computes the
	 * region's history if need be.
	 */
	public @Nullable String whyNotFound(BlockPos at, int spacing) {
		return whyNotFound(at, spacing, true);
	}

	/** As above; with {@code wait} false it never computes a region on this thread and answers {@link #SURVEYING} instead. */
	public @Nullable String whyNotFound(BlockPos at, int spacing, boolean wait) {
		if (this.probe.isWater(at.getX(), at.getZ())) {
			return "that is water";
		}
		SimSettlement near = nearest(at, spacing);
		if (near == null) {
			near = settlementAt(at.getX(), at.getZ(), 48);
		}
		if (near != null) {
			return near.name() + " is too close";
		}
		if (this.atlas.enabled()) {
			int rx = Atlas.region(at.getX());
			int rz = Atlas.region(at.getZ());
			RegionPlan plan = wait ? this.atlas.plan(rx, rz) : this.atlas.planIfReady(rx, rz);
			if (plan == null) {
				this.atlas.planAsync(rx, rz);
				return SURVEYING;
			}
			if (plan.site() != null && !this.settlements.containsKey(plan.site().settlementId())) {
				BoundingBox b = plan.bounds();
				boolean onIt = b != null && at.getX() >= b.minX() - 48 && at.getX() <= b.maxX() + 48 && at.getZ() >= b.minZ() - 48
					&& at.getZ() <= b.maxZ() + 48;
				if (onIt || plan.site().center().distManhattan(at) < spacing * 1.4) {
					return plan.site().name() + " (not yet visited) stands near here";
				}
			}
		}
		return null;
	}

	/** Spacing kept between a newly founded settlement and any other, live or historic. */
	public static final int FOUND_SPACING = 300;

	/** Never computes a region on the server thread: {@link #SURVEYING} until the land's history is known (ask again). */
	@Override
	public @Nullable String whyNotFound(BlockPos at) {
		return whyNotFound(at, FOUND_SPACING, false);
	}

	@Override
	public @Nullable Settlement found(BlockPos at, @Nullable Culture culture, @Nullable Settlement parent, String reason) {
		if (whyNotFound(at, FOUND_SPACING, true) != null) {
			return null;
		}
		SimSettlement from = parent == null ? null : this.settlements.get(parent.id());
		// a party from the parent town when it can spare them; otherwise newcomers from the land round about
		int party = from == null ? 0 : Math.min(3, from.population() / 8);
		return foundDaughter(at, culture, from, party, reason);
	}

	/**
	 * Founds a hamlet at {@code at} with {@code party} adults from {@code parent} (newcomers if there is no parent or
	 * nobody to spare), who bring the founding goods; the parent gives up a little of its stores and coin for them. Lays
	 * it out on this thread (the planner's founding survey and skeleton roads: 50–150 ms); {@link #foundDaughterAsync}
	 * does that off the server thread.
	 */
	public SimSettlement foundDaughter(BlockPos at, @Nullable Culture culture, @Nullable SimSettlement parent, int party, String reason) {
		long start = System.nanoTime();
		Founding f = prepare(at, culture, null);
		List<Resident> settlers = depart(parent, party, f);
		SimSettlement s = create(f, settlers, parent);
		this.daughterNanos += System.nanoTime() - start;
		this.daughtersFounded++;
		LOG.info("hearth: {} founded{} at {} ({} ms): {}", s.name(), parent == null ? "" : " by " + settlers.size() + " settlers from " + parent.name(),
			s.center().toShortString(), String.format(java.util.Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e6), reason);
		return s;
	}

	/**
	 * As {@link #foundDaughter}, but the layout is worked out on a worker thread with a context that touches nothing but
	 * the probe (the new record is nobody else's until it is registered, back on the server thread). The settlers leave
	 * at once; their hamlet appears a moment later. {@code done} runs on the server thread with the hamlet.
	 */
	public void foundDaughterAsync(BlockPos at, SimSettlement parent, int party, String reason, java.util.function.Consumer<SimSettlement> done) {
		long start = System.nanoTime();
		Founding f = prepare(at, null, null);
		List<Resident> settlers = depart(parent, party, f);
		SimSettlement s = newRecord(f, parent);
		var founding = rules().config.founding();
		dev.voidmc.hearth.sim.atlas.History.Context off = new dev.voidmc.hearth.sim.atlas.History.Context(rules(), this.probe, blueprints(), planner(),
			residents(), new BoundingBox(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000));
		s.bind(off);
		long serverStart = System.nanoTime() - start;
		// until the hamlet is registered its settlers belong to no saved record: if the world unloads first they go home
		java.util.concurrent.CompletableFuture<Void> layout = java.util.concurrent.CompletableFuture.runAsync(() -> SettlementClock.found(s, off,
			settlers.isEmpty() ? founding.residents() : settlers.size(), settlers, founding.goodsMap(), founding.treasury()), FOUNDING);
		PendingFounding pending = new PendingFounding(parent, settlers, layout);
		this.foundingsInFlight.add(pending);
		layout.whenComplete((v, t) -> this.level.getServer().execute(() -> {
				if (!this.foundingsInFlight.remove(pending)) {
					// the world was unloaded meanwhile and the settlers went home
					return;
				}
				if (t != null) {
					LOG.error("hearth: laying out {} failed; its settlers go home to {}", f.name, parent.name(), t);
					for (Resident r : settlers) {
						parent.addResident(r);
					}
					return;
				}
				long t1 = System.nanoTime();
				s.bind(this);
				register(s, true, f);
				this.daughterNanos += serverStart + System.nanoTime() - t1;
				this.daughtersFounded++;
				LOG.info("hearth: {} founded by {} settlers from {} at {} (laid out off-thread): {}", s.name(), settlers.size(), parent.name(),
					s.center().toShortString(), reason);
				done.accept(s);
			}));
	}

	/** A hamlet being laid out off the server thread: who left for it, from where. */
	private record PendingFounding(SimSettlement parent, List<Resident> settlers, java.util.concurrent.CompletableFuture<Void> layout) {
	}

	private final List<PendingFounding> foundingsInFlight = new ArrayList<>();

	/**
	 * The level is unloading (the server stops): settlers whose hamlet is still being laid out go back to the town they
	 * left, so the save that follows does not lose them.
	 */
	public void unload() {
		for (PendingFounding f : this.foundingsInFlight) {
			try {
				// the layout touches the settlers' records: let it finish first (it takes 50-150 ms)
				f.layout().get(10, java.util.concurrent.TimeUnit.SECONDS);
			} catch (Exception e) {
				// failed or slow: the settlers go home all the same
			}
			for (Resident r : f.settlers()) {
				r.extra("sim").remove("came_from");
				r.home = Resident.NO_PLOT;
				r.work = Resident.NO_PLOT;
				f.parent().addResident(r);
			}
		}
		this.foundingsInFlight.clear();
	}

	/** One worker for laying out hamlets founded by settlers. */
	private static final java.util.concurrent.ExecutorService FOUNDING = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-founding");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** The party leaves {@code parent} for the new place, with a share of its stores and coin. */
	private List<Resident> depart(@Nullable SimSettlement parent, int party, Founding f) {
		List<Resident> settlers = new ArrayList<>();
		if (parent == null) {
			return settlers;
		}
		if (party > 0) {
			List<Resident> adults = new ArrayList<>();
			for (Resident r : parent.residentList()) {
				if (r.age == AgeStage.ADULT && !parent.isResidentOut(r.id) && r.job != dev.voidmc.hearth.api.JobType.MINTER) {
					adults.add(r);
				}
			}
			// the homeless first: they are why the town is crowded
			adults.sort(java.util.Comparator.comparingInt(r -> r.home == Resident.NO_PLOT ? 0 : 1));
			for (int i = 0; i < Math.min(party, adults.size()); i++) {
				Resident r = adults.get(i);
				// listeners of RESIDENT_REMOVED can tell a departure from a death
				r.extra("sim").putString("left_for", f.name);
				parent.removeResident(r.id);
				r.extra("sim").remove("left_for");
				r.extra("sim").putLong("came_from", parent.id());
				settlers.add(r);
			}
			var goods = rules().config.founding().goodsMap();
			goods.forEach((g, n) -> parent.stockpile().take(g, Math.min(parent.stockpile().get(g) / 4, n / 2)));
			parent.withdraw(Math.min(parent.treasury() / 5, rules().config.founding().treasury()));
		}
		parent.clockState().daughterFounded(day());
		parent.markDirty();
		return settlers;
	}

	private long daughterNanos;
	private int daughtersFounded;

	/** Hamlets founded by settlers (and scouted sites) this session, and the time founding them took. */
	public String daughterStats() {
		return String.format(java.util.Locale.ROOT, "%d hamlets founded by settlers or scouts, %.1f ms avg; %s", this.daughtersFounded,
			this.daughtersFounded == 0 ? 0 : this.daughterNanos / 1e6 / this.daughtersFounded, this.hamlets.describe());
	}

	@Override
	public boolean economyHandlesWages() {
		return HearthServices.economy() != null;
	}

	// ---------------------------------------------------------------- the land (docs/SIM.md §16)

	@Override
	public @Nullable UndergroundProbe underground() {
		return this.underground;
	}

	@Override
	public boolean inWorld() {
		return true;
	}

	/**
	 * {@code -Dhearth.sim.inlineBlueprints=true} makes every blueprint on the server thread as it is needed (as before the
	 * background makers), for comparing the two.
	 */
	public static final boolean INLINE_BLUEPRINTS = Boolean.getBoolean("hearth.sim.inlineBlueprints");

	@Override
	public boolean budgetsEveryPlannerCall() {
		return !this.fastForward;
	}

	@Override
	public boolean deferBlueprints() {
		return !INLINE_BLUEPRINTS && !this.fastForward && this.server.server().isSameThread();
	}

	/** Caravans on the roads of this world. */
	public Caravans caravanTrade() {
		return this.caravans;
	}

	@Override
	public List<HearthWorld.CaravanView> caravans() {
		List<Caravans.Caravan> active = this.caravans.active();
		List<HearthWorld.CaravanView> out = new ArrayList<>(active.size());
		for (Caravans.Caravan c : active) {
			out.add(Caravans.view(c));
		}
		return out;
	}

	@Override
	public HearthWorld.@Nullable CaravanView caravan(long id) {
		Caravans.Caravan c = this.caravans.caravan(id);
		return c == null ? null : Caravans.view(c);
	}

	@Override
	public boolean escortCaravan(long id) {
		return this.caravans.escort(id);
	}

	@Override
	public int @Nullable [] robCaravan(long id) {
		return this.caravans.rob(id);
	}

	@Override
	public void caravanLost(long id, java.util.@Nullable UUID robbedBy) {
		this.caravans.lose(id, robbedBy != null);
	}

	@Override
	public boolean written(Settlement settlement, int plotId, int chunkX, int chunkZ) {
		if (!(settlement instanceof SimSettlement s)) {
			return true;
		}
		SimPlot p = s.plot(plotId);
		if (p == null) {
			return true;
		}
		var reach = p.reach();
		if (chunkX < reach.minX() >> 4 || chunkX > reach.maxX() >> 4 || chunkZ < reach.minZ() >> 4 || chunkZ > reach.maxZ() >> 4) {
			return true;
		}
		net.minecraft.world.level.chunk.LevelChunk chunk = this.level.getChunkSource().getChunkNow(chunkX, chunkZ);
		return chunk != null && dev.voidmc.hearth.sim.apply.ChunkLedgers.of(chunk).get(s.id(), plotId) >= s.applicable(p);
	}

	@Override
	public void trade(SimSettlement s, long day) {
		if (this.settlements.get(s.id()) == s) {
			this.caravans.consider(s, day);
		}
	}

	/**
	 * Surveys a settlement's territory off the server thread (the probe and the biome reading are thread-safe; the
	 * request is a snapshot) and merges the result back here, firing {@code SITE_FOUND} for what is new.
	 */
	@Override
	public void survey(SimSettlement s) {
		LandState land = s.land();
		if (this.fastForward) {
			// a fast-forward holds the server thread, so a survey made off it could not merge before the spell ends and the
			// town would grow all of it without its land: no workings, no carrying capacity (requests/w5-tour.md §3)
			long t0 = System.nanoTime();
			Lands.surveyNow(s, this);
			land.setSurveying(false);
			// (counted with the others: the stats line's average is every survey's)
			this.surveyNanos.add(System.nanoTime() - t0);
			this.surveys++;
			return;
		}
		if (land.isSurveying()) {
			return;
		}
		land.setSurveying(true);
		Lands.Request q = Lands.request(s, this);
		Land ground = Land.of(this.probe, this.underground);
		var data = data();
		java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			long t0 = System.nanoTime();
			List<Survey.Found> found = Lands.run(q, ground, data);
			this.surveyNanos.add(System.nanoTime() - t0);
			return found;
		}, SURVEYS).whenComplete((found, t) -> this.level.getServer().execute(() -> {
			land.setSurveying(false);
			if (t != null) {
				LOG.warn("hearth: surveying the land of {} failed", s.name(), t);
				return;
			}
			if (s.context() == this && land.surveyed() <= q.radius()) {
				// (not when the land was surveyed wider meanwhile, at once in a fast-forward)
				this.surveys++;
				Lands.merge(s, q.radius(), Survey.step(q.radius(), q.params()), found, day(), this);
			}
		}));
	}

	/** One low-priority worker for surveys of live settlements' land. */
	private static final java.util.concurrent.ExecutorService SURVEYS = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "hearth-survey");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	@Override
	public List<Territory.Core> cores(SimSettlement s) {
		List<Territory.Core> out = new ArrayList<>();
		int margin = rules().resources.land().coreMargin();
		int reach = 2 * Territory.byTier(dev.voidmc.hearth.api.SettlementTier.CITY, rules().resources.land().territory()) + 256;
		long r2 = (long) reach * reach;
		for (SimSettlement o : this.settlements.values()) {
			if (o == s || o.center().distSqr(s.center()) > r2) {
				continue;
			}
			out.add(new Territory.Core(o.center(), Lands.coreRadius(o, margin)));
		}
		return out;
	}

	private boolean reputationHanded;

	/**
	 * Once a session, on the first tick: the sim's stored reputation numbers go to the standing module as seeds
	 * ({@code hearth:seed}, ignored for a player it already knows), so worlds from before it keep their standing
	 * (requests/p3-standing.md §2). Harmless to repeat.
	 */
	private void handReputationOver() {
		this.reputationHanded = true;
		dev.voidmc.hearth.api.StandingService standing = HearthServices.standing();
		if (standing == null) {
			return;
		}
		int seeds = 0;
		for (SimSettlement s : this.settlements.values()) {
			for (java.util.UUID player : List.copyOf(s.reputationHolders())) {
				int v = s.rawReputation(player);
				if (v != 0) {
					standing.deed(new dev.voidmc.hearth.api.StandingService.Deed(player, s.id(), "hearth:seed", v, null, s.center(), false));
					seeds++;
				}
			}
		}
		if (seeds > 0) {
			LOG.info("hearth: handed {} reputation numbers to the standing module", seeds);
		}
	}

	/** Caravans that are due, and trees owed to the world felled in loaded woods (from the server tick). */
	public void tickLand(long fellBudgetNanos) {
		tickCaravans();
		tickFelling(fellBudgetNanos);
	}

	/** Caravans that are due (and, once a session, the old reputation numbers handed to the standing module). */
	public void tickCaravans() {
		if (!this.reputationHanded) {
			handReputationOver();
		}
		this.caravans.tick(clock());
	}

	/** Trees owed to the world felled in loaded woods, under the budget. */
	public void tickFelling(long fellBudgetNanos) {
		this.woodcutter.tick(fellBudgetNanos);
	}

	/** Surveys run, felling and caravans this session, for {@code /hearth sim stats} and the harness. */
	public String landStats() {
		return String.format(java.util.Locale.ROOT, "%d surveys (%.1f ms avg, off-thread but in a fast-forward); %s; %s", this.surveys,
			this.surveys == 0 ? 0 : this.surveyNanos.sum() / 1e6 / this.surveys, this.woodcutter.describe(), this.caravans.describe());
	}

	@Override
	public boolean sendSettlers(SimSettlement s) {
		return this.settlements.get(s.id()) == s && this.hamlets.request(s);
	}

	/** Searches for daughter hamlets' sites a little at a time (from the server tick). */
	public void tickHamlets(long budgetNanos) {
		this.hamlets.tick(budgetNanos);
	}

	@Override
	public List<SiteInfo> knownSites(BlockPos near, int radius) {
		List<SiteInfo> out = new ArrayList<>();
		long r2 = (long) radius * radius;
		for (SimSettlement s : this.settlements.values()) {
			if (s.center().distSqr(near) <= r2) {
				out.add(new SiteInfo(s.id(), s.name(), s.culture(), s.center(), s.tier(), true));
			}
		}
		if (this.atlas.enabled()) {
			int r0x = Atlas.region(near.getX() - radius), r1x = Atlas.region(near.getX() + radius);
			int r0z = Atlas.region(near.getZ() - radius), r1z = Atlas.region(near.getZ() + radius);
			for (int rx = r0x; rx <= r1x; rx++) {
				for (int rz = r0z; rz <= r1z; rz++) {
					RegionPlan plan = this.atlas.planIfReady(rx, rz);
					if (plan == null) {
						this.atlas.planAsync(rx, rz);   // known next time
						continue;
					}
					RegionPlan.Site site = plan.site();
					if (site == null || this.settlements.containsKey(site.settlementId()) || site.center().distSqr(near) > r2) {
						continue;
					}
					SettlementTier tier = plan.history() != null ? plan.history().tier() : SettlementTier.CAMP;
					out.add(new SiteInfo(site.settlementId(), site.name(), site.culture(), site.center(), tier, false));
				}
			}
		}
		out.sort(java.util.Comparator.comparingDouble(i -> i.center().distSqr(near)));
		return out;
	}

	/** Founds a live settlement at a spot (a command, a quest, settlers from a town that outgrew its land). */
	public SimSettlement found(BlockPos at, @Nullable Culture culture, @Nullable String name) {
		return create(prepare(at, culture, name), List.of(), null);
	}

	/** Who and where a settlement about to be founded is: its id and name are fixed before anyone moves. */
	private record Founding(long id, String name, Culture culture, BlockPos center, RandomSource random) {
	}

	private Founding prepare(BlockPos at, @Nullable Culture culture, @Nullable String name) {
		int y = this.probe.groundY(at.getX(), at.getZ());
		BlockPos center = new BlockPos(at.getX(), y, at.getZ());
		Culture c = culture != null ? culture
			: dev.voidmc.hearth.sim.terrain.CultureVote.at(at.getX(), at.getZ(), this.probe::biomeHolder, b -> HearthServices.culture(b));
		long id = this.ids.allocateId();
		RandomSource random = RandomSource.create(id * 0x9E3779B97F4A7C15L ^ this.level.getSeed());
		String n = name != null ? name : Names.settlement(c, random);
		return new Founding(id, n, c, center, random);
	}

	private SimSettlement newRecord(Founding f, @Nullable SimSettlement parent) {
		SimSettlement s = new SimSettlement(f.id, f.name, f.culture, this.level.dimension(), f.center, day(), SimSettlement.NO_REGION, f.random.nextLong());
		if (parent != null) {
			s.clockState().setParent(parent.id());
		}
		return s;
	}

	private SimSettlement create(Founding f, List<Resident> settlers, @Nullable SimSettlement parent) {
		SimSettlement s = newRecord(f, parent);
		s.bind(this);
		this.quiet = true;
		long t0 = System.nanoTime();
		try {
			var founding = rules().config.founding();
			SettlementClock.found(s, this, settlers.isEmpty() ? founding.residents() : settlers.size(), settlers, founding.goodsMap(),
				founding.treasury());
		} finally {
			this.quiet = false;
		}
		this.foundings++;
		this.foundingNanos += System.nanoTime() - t0;
		register(s, true, f);
		return s;
	}

	/** Puts a freshly founded record on the clock and tells everyone. */
	private void register(SimSettlement s, boolean isNew, Founding f) {
		s.setSimTick(simNow(s));
		s.markDirty();
		register(s, isNew);
		LOG.info("hearth: founded {} ({}) at {}", s.name(), f.culture.getSerializedName(), f.center.toShortString());
		fireFounded(s);
	}

	/** {@code FOUNDED}; a listener that throws is logged, and the settlement stays founded for everyone else. */
	private void fireFounded(SimSettlement s) {
		try {
			SettlementEvents.FOUNDED.invoker().onFounded(this, s);
		} catch (RuntimeException e) {
			LOG.error("hearth: a FOUNDED listener failed for {} (#{})", s.name(), s.id(), e);
		}
	}

	private int foundings;
	private long foundingNanos;

	/** Live settlements laid out this session and the planner's founding time (survey and skeleton roads) per town. */
	public String foundingStats() {
		return String.format(java.util.Locale.ROOT, "%d founded, %.1f ms avg to lay out", this.foundings,
			this.foundings == 0 ? 0 : this.foundingNanos / 1e6 / this.foundings);
	}

	/** Runs a settlement's clock forward by {@code days} at once (a debug command and the dev harness). */
	public long simulate(SimSettlement s, int days) {
		long start = System.nanoTime();
		s.addTimeOffset(days * SimRules.DAY);
		long target = simNow(s);
		this.fastForward = true;
		try {
			if (s.land().isSurveying()) {
				// the founders' survey is still out: make it now (see survey)
				survey(s);
			}
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

	/** Ages/residents helper for commands: counts by age stage. */
	public static int[] ages(SimSettlement s) {
		int[] n = new int[AgeStage.values().length];
		for (Resident r : s.residents()) {
			n[r.age.ordinal()]++;
		}
		return n;
	}
}
