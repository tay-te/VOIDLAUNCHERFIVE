package dev.voidmc.hearth.sim.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.sim.land.LandState;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sim module's settlement: the authoritative record of a town, its plots, people and stores. It is data first —
 * entities and placed blocks are views of it made near players — and it is what gets saved, one file per settlement.
 *
 * <p>Server thread only, except that a settlement still being written as history lives on the one thread computing it
 * and is never seen by anything else until adopted (as a copy).
 */
public final class SimSettlement implements Settlement {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** {@link #originRegion} of a settlement founded at runtime rather than from the atlas. */
	public static final long NO_REGION = Long.MIN_VALUE;

	private final long id;
	private String name;
	private final Culture culture;
	private final ResourceKey<Level> dimension;
	private final BlockPos center;
	private final long foundedDay;
	private final long originRegion;
	SettlementTier tier = SettlementTier.CAMP;

	final Int2ObjectLinkedOpenHashMap<SimPlot> plots = new Int2ObjectLinkedOpenHashMap<>();
	int nextPlotId;
	final Map<UUID, Resident> residents = new LinkedHashMap<>();
	private final Set<UUID> embodiedResidents = new HashSet<>();
	final SimStockpile stockpile;
	long treasury;
	private final Object2IntOpenHashMap<UUID> reputation = new Object2IntOpenHashMap<>();
	private final DataMap data = new DataMap(HearthDataKey.Scope.SETTLEMENT);
	final ClockState clock;

	private boolean embodied;
	/** Inside a call to the standing service: reads and writes go to the raw numbers. */
	private boolean delegating;
	private @Nullable LandState landCache;
	private @Nullable SimContext ctx;
	private boolean dirty;
	private boolean derivedDirty = true;
	private @Nullable BoundingBox bounds;
	private int beds;
	private final int[] jobSlots = new int[JobType.values().length];
	private int storageBuildings;
	private int buildings;
	private final int[] coolCapacity = new int[Goods.VALUES.length];
	private int activeDigs;

	public SimSettlement(long id, String name, Culture culture, ResourceKey<Level> dimension, BlockPos center, long foundedDay, long originRegion,
		long seed) {
		this(id, name, culture, dimension, center, foundedDay, originRegion, new SimStockpile(), new ClockState(foundedDay * SimRules.DAY, 0, seed));
	}

	private SimSettlement(long id, String name, Culture culture, ResourceKey<Level> dimension, BlockPos center, long foundedDay, long originRegion,
		SimStockpile stockpile, ClockState clock) {
		this.id = id;
		this.name = name;
		this.culture = culture;
		this.dimension = dimension;
		this.center = center.immutable();
		this.foundedDay = foundedDay;
		this.originRegion = originRegion;
		this.stockpile = stockpile;
		this.clock = clock;
		this.stockpile.bind(this::markDirty);
		this.reputation.defaultReturnValue(0);
	}

	// ---------------------------------------------------------------- binding

	/** Connects the settlement to the world (or history) that runs it. */
	public void bind(SimContext ctx) {
		this.ctx = ctx;
	}

	public @Nullable SimContext context() {
		return this.ctx;
	}

	SimContext ctx() {
		if (this.ctx == null) {
			throw new IllegalStateException("settlement " + this.id + " is not bound to a world");
		}
		return this.ctx;
	}

	public DataMap dataMap() {
		return this.data;
	}

	// ---------------------------------------------------------------- Settlement

	@Override
	public long id() {
		return this.id;
	}

	@Override
	public String name() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
		markDirty();
	}

	@Override
	public Culture culture() {
		return this.culture;
	}

	@Override
	public SettlementTier tier() {
		return this.tier;
	}

	@Override
	public ResourceKey<Level> dimension() {
		return this.dimension;
	}

	@Override
	public BlockPos center() {
		return this.center;
	}

	@Override
	public BoundingBox bounds() {
		if (this.bounds == null) {
			BoundingBox box = new BoundingBox(this.center);
			for (SimPlot p : this.plots.values()) {
				PlotSpec s = p.spec();
				box.encapsulate(new BlockPos(s.origin().getX(), s.floorY() - 8, s.origin().getZ()));
				box.encapsulate(new BlockPos(s.origin().getX() + s.sizeX() - 1, s.floorY() + s.maxHeight(), s.origin().getZ() + s.sizeZ() - 1));
			}
			this.bounds = box;
		}
		return this.bounds;
	}

	@Override
	public long foundedDay() {
		return this.foundedDay;
	}

	/** The atlas region this settlement was founded in by history, or {@link #NO_REGION}. */
	public long originRegion() {
		return this.originRegion;
	}

	@Override
	public boolean embodied() {
		return this.embodied;
	}

	/** Set by the world's embodiment tracker. */
	public void setEmbodied(boolean embodied) {
		this.embodied = embodied;
	}

	@Override
	public Collection<SimPlot> plots() {
		return Collections.unmodifiableCollection(this.plots.values());
	}

	@Override
	public @Nullable SimPlot plot(int id) {
		return this.plots.get(id);
	}

	@Override
	public Collection<Resident> residents() {
		return Collections.unmodifiableCollection(this.residents.values());
	}

	@Override
	public @Nullable Resident resident(UUID id) {
		return this.residents.get(id);
	}

	@Override
	public int population() {
		return this.residents.size();
	}

	@Override
	public int beds() {
		ensureDerived();
		return this.beds;
	}

	@Override
	public SimStockpile stockpile() {
		ensureDerived();
		return this.stockpile;
	}

	@Override
	public long treasury() {
		return this.treasury;
	}

	@Override
	public void deposit(long bits) {
		if (bits > 0) {
			this.treasury += bits;
			markDirty();
		}
	}

	@Override
	public boolean withdraw(long bits) {
		if (bits < 0 || bits > this.treasury) {
			return false;
		}
		this.treasury -= bits;
		markDirty();
		return true;
	}

	/**
	 * The player's standing here. While a {@link StandingService} is registered it is the authority (its settlement layer
	 * folds in titles, deeds and company); the sim's own number is the fallback, and what is read while the standing
	 * module itself asks (so a standing module built over these raw numbers cannot recurse).
	 */
	@Override
	public int reputation(UUID player) {
		StandingService standing = HearthServices.standing();
		if (standing != null && !this.delegating) {
			this.delegating = true;
			try {
				return standing.standing(this.id, player);
			} finally {
				this.delegating = false;
			}
		}
		return this.reputation.getInt(player);
	}

	/**
	 * Moves the player's standing. With a {@link StandingService} it is reported to it as a deed of kind
	 * {@code hearth:reputation} (witnessed, at the heart, weighed by {@code delta}); without one, or from inside the
	 * standing module's own call, the sim's raw number moves.
	 */
	@Override
	public void addReputation(UUID player, int delta) {
		StandingService standing = HearthServices.standing();
		if (standing != null && !this.delegating) {
			this.delegating = true;
			try {
				standing.deed(new StandingService.Deed(player, this.id, REPUTATION_DEED, delta, null, this.center, true));
			} finally {
				this.delegating = false;
			}
			return;
		}
		rawAddReputation(player, delta);
	}

	/** The deed kind a plain {@link #addReputation} becomes when a standing module is the authority. */
	public static final String REPUTATION_DEED = "hearth:reputation";

	/** The sim's own stored standing, whatever module is the authority (for the standing module and old saves). */
	public int rawReputation(UUID player) {
		return this.reputation.getInt(player);
	}

	public void rawAddReputation(UUID player, int delta) {
		int v = Math.max(-100, Math.min(100, this.reputation.getInt(player) + delta));
		this.reputation.put(player, v);
		markDirty();
	}

	/** Players with a stored standing here. */
	public java.util.Set<UUID> reputationHolders() {
		return java.util.Collections.unmodifiableSet(this.reputation.keySet());
	}

	// ---------------------------------------------------------------- land (docs/SIM.md §16)

	/** The settlement's territory, resource sites, prosperity and trade. */
	public LandState land() {
		LandState l = this.landCache;
		if (l == null) {
			l = this.landCache = this.data.get(LandState.KEY);
		}
		return l;
	}

	@Override
	public int territoryRadius() {
		return land().territory();
	}

	@Override
	public List<ResourceSite> sites() {
		return land().views();
	}

	@Override
	public double prosperity() {
		return land().prosperity();
	}

	@Override
	public int fortunes() {
		return land().mood();
	}

	@Override
	public String fortunesWhy() {
		return land().why();
	}

	@Override
	public int carryingCapacity() {
		return land().capacity();
	}

	@Override
	public boolean deserted(int plotId) {
		return land().isDeserted(plotId);
	}

	@Override
	public @Nullable ResourceSite reportSite(ResourceSite.Kind kind, BlockPos pos, String reason) {
		dev.voidmc.hearth.sim.land.SimSite site = dev.voidmc.hearth.sim.land.Lands.prospect(this, kind, pos, ctx());
		if (site != null) {
			dev.voidmc.hearth.Hearth.LOG.info("[hearth sim] {}: {} site {} added at {} ({})", this.name, kind.getSerializedName(), site.id(),
				site.center().toShortString(), reason);
		}
		return site == null ? null : site.view();
	}

	/** Builder labour (work units) other modules took today before construction (repairs); spent by the clock. */
	double builderLabourTaken;
	/** What the clock offers to other modules a day before construction, and what is left of it today. */
	double builderLabourOffer;

	@Override
	public int takeBuilderLabour(int units) {
		if (units <= 0) {
			return 0;
		}
		int granted = (int) Math.min(units, Math.max(0, this.builderLabourOffer - this.builderLabourTaken));
		this.builderLabourTaken += granted;
		return granted;
	}

	/** Real goods embodied gatherers brought in, per resident, not yet set against their labour credit (transient). */
	private final java.util.Map<UUID, int[]> yieldCredits = new java.util.HashMap<>();

	@Override
	public void creditYield(UUID resident, Goods goods, int units) {
		if (units <= 0 || this.residents.get(resident) == null) {
			return;
		}
		int added = stockpile().add(goods, units);
		int[] owed = this.yieldCredits.computeIfAbsent(resident, k -> new int[Goods.VALUES.length]);
		owed[goods.ordinal()] += units;
		// the land gives it up: the nearest site that yields this goods
		dev.voidmc.hearth.sim.land.SimSite best = null;
		double bestD = Double.MAX_VALUE;
		for (dev.voidmc.hearth.sim.land.SimSite site : land().sites()) {
			if (site.yieldsView()[goods.ordinal()] > 0 && site.remaining() > 0) {
				double d = site.distanceTo(this.center);
				if (d < bestD) {
					bestD = d;
					best = site;
				}
			}
		}
		if (best != null) {
			dev.voidmc.hearth.sim.land.Lands.take(this, best, units, 0, false, ctx());
		}
		if (added > 0) {
			markDirty();
		}
	}

	@Override
	public void addResident(Resident resident) {
		if (this.residents.putIfAbsent(resident.id, resident) == null) {
			markDirty();
			HearthWorld w = this.ctx == null ? null : this.ctx.eventWorld();
			if (w != null) {
				SettlementEvents.RESIDENT_ADDED.invoker().onResident(w, this, resident);
			}
		}
	}

	@Override
	public boolean removeResident(UUID id) {
		Resident r = this.residents.remove(id);
		this.embodiedResidents.remove(id);
		this.yieldCredits.remove(id);
		if (r == null) {
			return false;
		}
		markDirty();
		HearthWorld w = this.ctx == null ? null : this.ctx.eventWorld();
		if (w != null) {
			SettlementEvents.RESIDENT_REMOVED.invoker().onResident(w, this, r);
		}
		return true;
	}

	@Override
	public void setResidentEmbodied(UUID id, boolean embodied) {
		if (embodied && this.residents.containsKey(id)) {
			this.embodiedResidents.add(id);
		} else {
			this.embodiedResidents.remove(id);
		}
	}

	@Override
	public boolean isResidentEmbodied(UUID id) {
		return this.embodiedResidents.contains(id);
	}

	public int embodiedResidentCount() {
		return this.embodiedResidents.size();
	}

	@Override
	public void advanceConstruction(int plotId, int steps) {
		SimPlot p = this.plots.get(plotId);
		if (p == null || !p.isActive() || steps < 0) {
			return;
		}
		SimContext c = ctx();
		if (steps == 0) {
			// builders at work on it with no step finished just now (putting up scaffolding): abstract labour stays off it
			p.lastEmbodiedAdvance = c.gameTime();
			return;
		}
		PlotFacts f = facts(p);
		long from = p.cursor();
		int remaining = steps;
		while (remaining > 0 && p.stage < f.stageCount()) {
			if (!p.funded && !startStage(p, f, false)) {
				// the stage's goods are not in store: it waits (stalled, waitingForGoods), and so do the builders
				break;
			}
			int n = Math.min(remaining, f.stepCount()[p.stage] - p.step);
			p.step += n;
			remaining -= n;
			int stepsInStage = Math.max(1, f.stepCount()[p.stage]);
			p.labour = Math.max(p.labour, f.work()[p.stage] * (double) p.step / stepsInStage);
			if (p.step >= f.stepCount()[p.stage]) {
				finishStage(p, f, false);
			}
		}
		if (p.cursor() != from) {
			p.lastEmbodiedAdvance = c.gameTime();
			c.placedByEntity(this, p, from, p.cursor());
			markDirty();
		}
	}

	@Override
	public void setPlotOwner(int plotId, @Nullable UUID player) {
		SimPlot p = this.plots.get(plotId);
		if (p != null) {
			p.owner = player;
			markDirty();
		}
	}

	@Override
	public <T> T data(HearthDataKey<T> key) {
		return this.data.get(key);
	}

	@Override
	public void markDirty() {
		this.dirty = true;
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public void clearDirty() {
		this.dirty = false;
	}

	// ---------------------------------------------------------------- plots

	/** Adds a planned plot. {@code replaces} is the id of a plot it will rebuild once construction starts, or -1. */
	public SimPlot addPlot(PlotSpec spec, int replaces, String reason) {
		SimPlot p = new SimPlot(this.nextPlotId++, spec);
		p.replaces = replaces >= 0 && this.plots.containsKey(replaces) ? replaces : SimPlot.NONE;
		p.reason = reason == null ? "" : reason;
		this.plots.put(p.id(), p);
		this.bounds = null;
		if (this.ctx != null && this.ctx.deferBlueprints()) {
			// its blueprint made in the background from now, so it is ready by the time its builders need it
			blueprintsReady(p);
		}
		// an excavation employs its diggers from the day it is staked out; any other planned plot changes none of the derived
		// figures (beds, job places and store room count standing buildings), so the planner's next call, asked in the same
		// step, does not recompute them (7.6 % of planner.next in a city, requests/w6-plan.md §5)
		if (this.ctx == null || this.ctx.data().extraction(spec.archetype()) != null) {
			this.derivedDirty = true;
		}
		markDirty();
		if (this.ctx != null) {
			this.ctx.plotsChanged(this);
		}
		return p;
	}

	/** Takes a plot out of the record (not the world). */
	public void removePlot(int id) {
		if (this.plots.remove(id) != null) {
			this.bounds = null;
			this.derivedDirty = true;
			markDirty();
			if (this.ctx != null) {
				this.ctx.plotsChanged(this);
			}
		}
	}

	/** The plot param the build module reads time's work from (docs/buildings/age.md, {@code build.age.Decay}). */
	public static final String DECAY = "decay";
	/** The plot param a growing household's additions are counted in (docs/buildings/age.md, {@code build.age.Accretion}). */
	public static final String ADDITIONS = "additions";

	/**
	 * Whether a plot replacing another changes it in place rather than rebuilding it: the same building on the same ground
	 * (archetype, style, seed, footprint, facing, height, finish and every other param), differing only in what time does
	 * to it: the additions a growing household builds on, the decay of a house left empty (docs/buildings/age.md). Such a
	 * building is never taken down: the new drawing is written over the old one, which stands (and is lived in) until it
	 * is done.
	 */
	public static boolean inPlace(@Nullable PlotSpec old, PlotSpec now) {
		if (old == null || !old.archetype().equals(now.archetype()) || !old.style().equals(now.style()) || old.seed() != now.seed()
			|| !old.origin().equals(now.origin()) || old.sizeX() != now.sizeX() || old.sizeZ() != now.sizeZ() || old.facing() != now.facing()
			|| old.maxHeight() != now.maxHeight() || old.tier() != now.tier()) {
			return false;
		}
		CompoundTag a = old.params().copy();
		CompoundTag b = now.params().copy();
		for (String k : new String[]{DECAY, ADDITIONS, "replaces"}) {
			a.remove(k);
			b.remove(k);
		}
		return a.equals(b);
	}

	/**
	 * What a building changed in place may have stood as before (for the applier: blocks any of them put in the world are
	 * the plot's own): as first built and with each addition before its current count (a house grows while lived in), then
	 * at each step of decay below its current one (it weathers with the additions it had).
	 */
	public static List<PlotSpec> earlierSelves(PlotSpec now) {
		List<PlotSpec> out = new ArrayList<>();
		CompoundTag params = now.params();
		int additions = params.contains(ADDITIONS) ? params.getIntOr(ADDITIONS, -1) : -1;
		for (int k = 0; k < additions; k++) {
			CompoundTag p = params.copy();
			p.putInt(ADDITIONS, k);
			p.remove(DECAY);
			out.add(withParams(now, p));
		}
		double decay = decay(now);
		if (decay > 0) {
			CompoundTag whole = params.copy();
			whole.remove(DECAY);
			out.add(withParams(now, whole));
			for (double d = 0.25; d < decay - 1e-6; d += 0.25) {
				CompoundTag p = params.copy();
				p.putDouble(DECAY, d);
				out.add(withParams(now, p));
			}
		}
		return out;
	}

	private static PlotSpec withParams(PlotSpec s, CompoundTag params) {
		return new PlotSpec(s.archetype(), s.style(), s.seed(), s.origin(), s.sizeX(), s.sizeZ(), s.facing(), s.maxHeight(), s.tier(), params);
	}

	/** The spec of the building a plot replaces (standing, or remembered once it is gone), or null. */
	public @Nullable PlotSpec replacedSpec(SimPlot p) {
		if (p.replaces == SimPlot.NONE) {
			return p.replacedSpec;
		}
		SimPlot old = this.plots.get(p.replaces);
		return old != null ? old.spec() : p.replacedSpec;
	}

	/** How far time has worked on a plot: its {@code decay} param, 0 to 1. */
	public static double decay(PlotSpec spec) {
		CompoundTag params = spec.params();
		if (!params.contains(DECAY)) {
			return 0;
		}
		double v = params.getDoubleOr(DECAY, params.getFloatOr(DECAY, 0));
		return Math.clamp(v, 0, 1);
	}

	/**
	 * Time works on a building left standing empty (docs/buildings/age.md, LIVING_WORLD.md §7.2): it is drawn again with
	 * {@code decay} (0.25 the day it is left, then holes in the roof, then a ruin). The weathered building takes the old
	 * one's place in the record as a new plot ({@code ABANDONED}, finished, remembering the spec it replaced), and in the
	 * world as its chunks are next written: what still stands as built comes down and the weathered drawing goes in
	 * (identical blocks come back; nothing a player changed is touched). Returns the new plot, or the old one when the
	 * decay would not rise.
	 */
	public SimPlot weather(SimPlot old, double decay) {
		double level = Math.clamp(decay, 0, 1);
		if (level <= decay(old.spec()) || old.state != PlotState.ABANDONED || !this.plots.containsKey(old.id())) {
			return old;
		}
		PlotSpec o = old.spec();
		CompoundTag params = o.params().copy();
		params.putDouble(DECAY, Math.round(level * 100) / 100.0);
		PlotSpec spec = new PlotSpec(o.archetype(), o.style(), o.seed(), o.origin(), o.sizeX(), o.sizeZ(), o.facing(), o.maxHeight(), o.tier(), params);
		SimPlot p = new SimPlot(this.nextPlotId++, spec);
		p.state = PlotState.ABANDONED;
		p.replaces = old.id();
		p.replacedSpec = o;
		p.reason = old.reason;
		p.owner = old.owner;
		this.plots.remove(old.id());
		this.plots.put(p.id(), p);
		p.stage = facts(p).stageCount();
		p.completedDay = Math.floorDiv(this.clock.simTick, SimRules.DAY);
		this.bounds = null;
		this.derivedDirty = true;
		markDirty();
		if (this.ctx != null) {
			this.ctx.plotsChanged(this);
			this.ctx.progressed(this, p);
		}
		return p;
	}

	/** Gives up on a plot (its ground was built over); its finished stages stay as they are. */
	public void abandonPlot(SimPlot p) {
		abandonPlot(p, "built over");
	}

	/** Gives up on a plot; {@code why} is short and readable ("built over", "its archetype is gone"). */
	public void abandonPlot(SimPlot p, String why) {
		if (p.state == PlotState.ABANDONED) {
			return;
		}
		p.state = PlotState.ABANDONED;
		p.funded = false;
		p.stalledOn = null;
		SimPlot old = this.plots.get(p.replaces);
		if (old != null && old.state == PlotState.DEMOLISHING) {
			// the rebuild is off: the old building stands (and is written back where it was already cleared)
			old.state = PlotState.BUILT;
			if (this.ctx != null) {
				this.ctx.progressed(this, old);
			}
		}
		this.derivedDirty = true;
		markDirty();
		firePlotAbandoned(p, why);
	}

	/** Ticks of an entity's WORK routine that make one abstract worker-day (a folk works about nine hours a day). */
	private static final double WORK_TICKS_PER_DAY = 9000;
	private int poiCacheKey = -1;

	@Override
	public void creditLabour(UUID resident, JobType job, int ticks) {
		Resident res = this.residents.get(resident);
		if (ticks > 0 && res != null) {
			int[] owed = this.yieldCredits.remove(resident);
			int[] before = owed == null ? null : stockpile().amounts().clone();
			SettlementClock.credit(this, res, job, ticks / WORK_TICKS_PER_DAY);
			if (owed != null) {
				// the goods their entity really gathered were credited already (creditYield): not again from the time
				int[] after = stockpile().amounts();
				for (int g = 0; g < owed.length && g < after.length; g++) {
					int twice = Math.min(owed[g], after[g] - before[g]);
					if (twice > 0) {
						stockpile().take(Goods.VALUES[g], twice);
					}
				}
			}
		}
	}

	/** POIs of the built plots by type, rebuilt when the set of built plots changes (checked at most once a game tick). */
	private java.util.@Nullable EnumMap<PoiType, List<Poi>> poiByType;
	private long poiCheckedAt = Long.MIN_VALUE;

	@Override
	public List<Poi> pois(PoiType type) {
		// other modules ask often (locks, the law's cells, the notice board): the plots are walked at most once a tick, and
		// the lists are rebuilt only when the set of built plots changes; blueprints are cached by the build module
		long now = this.ctx == null ? Long.MIN_VALUE : this.ctx.gameTime();
		if (this.poiByType == null || now != this.poiCheckedAt || now == Long.MIN_VALUE) {
			this.poiCheckedAt = now;
			int key = 0;
			for (SimPlot p : this.plots.values()) {
				if (p.state == PlotState.BUILT) {
					key = key * 31 + p.id() + 1;
				}
			}
			if (this.poiByType == null || key != this.poiCacheKey) {
				// each plot's points are read from its blueprint once; on the live server a blueprint not yet made is made in
				// the background and the lists are rebuilt (without it until then) on a later tick
				java.util.EnumMap<PoiType, List<Poi>> byType = new java.util.EnumMap<>(PoiType.class);
				boolean complete = true;
				boolean defer = this.ctx.deferBlueprints();
				for (SimPlot p : this.plots.values()) {
					if (p.state == PlotState.BUILT) {
						List<Poi> own = p.pois;
						if (own == null) {
							if (defer && !ctx().blueprints().prepare(p.spec(), ctx().terrain())) {
								complete = false;
								continue;
							}
							try {
								own = List.copyOf(ctx().blueprints().blueprint(p.spec(), ctx().terrain()).pois());
							} catch (RuntimeException e) {
								// an archetype that no longer exists has no points of interest
								own = List.of();
							}
							p.pois = own;
						}
						for (Poi poi : own) {
							byType.computeIfAbsent(poi.type(), k -> new ArrayList<>()).add(poi);
						}
					}
				}
				this.poiByType = byType;
				this.poiCacheKey = complete ? key : key + 1;
			}
		}
		List<Poi> found = this.poiByType.get(type);
		return found == null ? new ArrayList<>() : new ArrayList<>(found);
	}

	private void firePlotAbandoned(SimPlot p, String why) {
		HearthWorld w = this.ctx == null ? null : this.ctx.eventWorld();
		if (w != null) {
			SettlementEvents.PLOT_ABANDONED.invoker().onPlotAbandoned(w, this, p, why);
		}
	}

	/**
	 * Whether the plot's facts can be read now without making a blueprint on the server thread: they are known, the
	 * blueprints they need are ready, or this context makes blueprints inline ({@link SimContext#deferBlueprints}). If
	 * not, the blueprints are being made in the background and the plot waits for them.
	 */
	public boolean factsReady(SimPlot p) {
		return p.facts != null || this.ctx == null || !this.ctx.deferBlueprints() || blueprintsReady(p);
	}

	/** Whether the blueprints a plot needs (its own, and the one it changes or replaces) are ready, starting any that are not. */
	public boolean blueprintsReady(SimPlot p) {
		var blueprints = ctx().blueprints();
		boolean ready = blueprints.prepare(p.spec(), ctx().terrain());
		if (p.replaces != SimPlot.NONE || p.replacedSpec() != null) {
			PlotSpec old = replacedSpec(p);
			if (old != null) {
				ready &= blueprints.prepare(old, ctx().terrain());
			}
		}
		return ready;
	}

	/** The plot's blueprint facts, read once. A plot whose archetype no longer exists gets none and is abandoned. */
	public PlotFacts facts(SimPlot p) {
		PlotFacts f = p.facts;
		if (f == null) {
			try {
				// a building changed in place (an addition) costs what changes, not the whole house again
				PlotSpec over = p.isActive() && p.replaces != SimPlot.NONE && inPlace(replacedSpec(p), p.spec()) ? replacedSpec(p) : null;
				f = PlotFacts.of(p.spec(), ctx().blueprints(), ctx().terrain(), ctx().data().extraction(p.spec().archetype()), over);
			} catch (RuntimeException e) {
				LOG.warn("settlement {} plot {} ({}): no blueprint ({}); abandoning it", this.id, p.id(), p.spec().archetype(), e.toString());
				f = new PlotFacts(0, new int[0], new int[0], new int[0][], 0, new int[JobType.values().length], false, null);
				if (p.isActive()) {
					p.state = PlotState.ABANDONED;
					firePlotAbandoned(p, "no blueprint for " + p.spec().archetype());
				}
			}
			p.facts = f;
		}
		return f;
	}

	/**
	 * How far a plot may be written into the world: whole stages while the settlement is abstract (completion is
	 * recorded per stage), every step while it is embodied (so nothing pops in in front of a player).
	 */
	public long applicable(SimPlot p) {
		PlotFacts f = facts(p);
		if (f.cuts() != null && this.ctx != null && this.ctx.rules().config.resources().extraction().abstractOnly()) {
			// server owners may keep extraction as numbers only: an excavation changes no terrain
			return Cursor.ZERO;
		}
		return switch (p.state) {
			case BUILT -> f.complete();
			// its rebuild has begun: it comes down (the applier clears what is left of it as its chunks load)
			case DEMOLISHING -> Cursor.ZERO;
			case PLANNED, BUILDING -> this.embodied ? p.cursor() : Cursor.pack(p.stage, 0);
			case ABANDONED -> Cursor.pack(Math.min(p.stage, f.stageCount()), 0);
		};
	}

	/**
	 * Tries to start a plot's current stage now: takes its goods if they are in store, otherwise marks it waiting for
	 * them (firing {@code CONSTRUCTION_STALLED} once). For commands and tests; the clock does this as it goes.
	 */
	public boolean tryStartStage(int plotId) {
		SimPlot p = this.plots.get(plotId);
		if (p == null || !p.isActive()) {
			return false;
		}
		PlotFacts f = facts(p);
		return p.stage < f.stageCount() && (p.funded || startStage(p, f, false));
	}

	/**
	 * Takes the stage's goods if they are in store (or anyway, if forced: an embodied builder already started). The
	 * founders' own buildings take nothing from the stores: they came with what those cost (requests/w5-tour.md §4: the
	 * camp's first houses ask for thousands of goods of ten kinds, which no handful of settlers makes in its first month).
	 */
	boolean startStage(SimPlot p, PlotFacts f, boolean force) {
		if (endowed(p)) {
			fund(p);
			return true;
		}
		int[] need = f.goods()[p.stage];
		provision(need);
		if (!force && !this.stockpile.has(need)) {
			int worst = this.stockpile.worstShortfall(need);
			if (worst >= 0 && (p.stalledOn == null || p.heldBack)) {
				p.heldBack = false;
				p.stalledOn = Goods.VALUES[worst];
				HearthWorld w = ctx().eventWorld();
				if (w != null) {
					SettlementEvents.CONSTRUCTION_STALLED.invoker().onStalled(w, this, p, p.stalledOn, this.stockpile.shortfall(need, worst));
				}
			}
			return false;
		}
		if (!force) {
			Goods held = heldFor(p, need);
			if (held != null) {
				// an earlier plot is waiting for these goods: what comes in goes to it first (or a supply quest that covers
				// its shortfall would be eaten by smaller jobs before it could start). This one waits its turn, quietly.
				p.stalledOn = held;
				p.heldBack = true;
				return false;
			}
		}
		this.stockpile.takeAll(need);
		fund(p);
		return true;
	}

	/** The founding's own plots: the founders brought what they cost (an endowment the size of the first buildings). */
	static boolean endowed(SimPlot p) {
		return FOUNDING.equals(p.reason);
	}

	/** The reason the founding plots carry. */
	public static final String FOUNDING = "founding";

	/** The stage's goods are in hand: under way, and an old building it replaces comes down. */
	private void fund(SimPlot p) {
		p.funded = true;
		p.stalledOn = null;
		p.heldBack = false;
		if (p.state == PlotState.PLANNED) {
			p.state = PlotState.BUILDING;
			SimPlot old = this.plots.get(p.replaces);
			if (old != null && old.state == PlotState.BUILT && !inPlace(old.spec(), p.spec())) {
				// the old building comes down as the new one goes up: its beds and workplaces stop counting
				// (not a house growing in place: it stands, lived in, while its addition goes up over it)
				old.state = PlotState.DEMOLISHING;
				this.derivedDirty = true;
				// the applier clears it from loaded chunks before the new one's first blocks go in
				ctx().progressed(this, old);
				ctx().plotsChanged(this);
				firePlotAbandoned(old, "rebuilt as " + p.spec().archetype().getPath());
			}
		}
	}

	/**
	 * A stage's provisions (food in a bill: sacks and crates, a larder, a feast laid out) come from whatever is edible in
	 * store when there is no prepared food: grain, fruit, fish and meat count at what they are worth eaten.
	 */
	private void provision(int[] need) {
		int food = Goods.FOOD.ordinal();
		if (need.length <= food || need[food] <= 0 || this.ctx == null) {
			return;
		}
		int want = Math.min(need[food], this.stockpile.capacity(Goods.FOOD)) - this.stockpile.get(Goods.FOOD);
		if (want <= 0) {
			return;
		}
		double[] value = this.ctx.data().edible();
		for (int g : PROVISIONS) {
			if (want <= 0) {
				break;
			}
			if (value[g] <= 0) {
				continue;
			}
			int units = Math.min(this.stockpile.get(Goods.VALUES[g]), (int) Math.ceil(want / value[g]));
			if (units > 0) {
				this.stockpile.takeQuiet(g, units);
				int made = (int) Math.floor(units * value[g]);
				this.stockpile.addQuiet(food, made);
				want -= made;
			}
		}
	}

	/** What a bill's food may be made up from, the most plentiful kinds first. */
	private static final int[] PROVISIONS = {Goods.GRAIN.ordinal(), Goods.PRODUCE.ordinal(), Goods.FLOUR.ordinal(), Goods.FISH.ordinal(),
		Goods.MEAT.ordinal()};

	/**
	 * The goods {@code p} would take from the first plot ahead of it (in plot order, the order labour goes round) already
	 * waiting for them, or null if starting {@code p} leaves that plot no worse off. The first plot in line is never held.
	 */
	private @Nullable Goods heldFor(SimPlot p, int[] need) {
		for (SimPlot q : this.plots.values()) {
			if (q == p) {
				// nobody waiting before it: it is first in line
				return null;
			}
			if (!q.isActive() || q.funded || q.stalledOn == null || q.facts == null || q.stage >= q.facts.stageCount()) {
				continue;
			}
			int[] theirs = q.facts.goods()[q.stage];
			for (int g = 0; g < need.length && g < theirs.length; g++) {
				int cap = this.stockpile.capacity(Goods.VALUES[g]);
				if (need[g] > 0 && theirs[g] > 0 && this.stockpile.get(Goods.VALUES[g]) - Math.min(theirs[g], cap) < Math.min(need[g], cap)) {
					return Goods.VALUES[g];
				}
			}
			// only the first waiting plot holds goods back
			return null;
		}
		return null;
	}

	/** The current stage is done: on to the next, or the whole plot. */
	void finishStage(SimPlot p, PlotFacts f, boolean notify) {
		if (f.cuts() != null && p.stage < f.cuts().length) {
			// an excavation: what the stage cut out of the land is the yield (stone by its rock, ore by the survey's estimate)
			Extraction.credit(this, p, f, p.stage);
		}
		p.stage++;
		p.step = 0;
		p.labour = 0;
		p.funded = false;
		if (p.stage >= f.stageCount()) {
			finishPlot(p, f);
			return;
		}
		if (this.embodied) {
			// builders are on site: take the next stage's goods now, or let them see at once that it waits for them
			startStage(p, f, false);
		}
		if (notify) {
			ctx().progressed(this, p);
		}
	}

	private void finishPlot(SimPlot p, PlotFacts f) {
		SimContext c = ctx();
		p.state = PlotState.BUILT;
		p.stage = f.stageCount();
		p.step = 0;
		p.beds = f.beds();
		p.jobs = f.jobs().clone();
		p.storage = f.storage();
		p.completedDay = Math.floorDiv(this.clock.simTick, SimRules.DAY);
		SimPlot old = this.plots.get(p.replaces);
		if (old != null) {
			p.replacedSpec = old.spec();
			if (inPlace(old.spec(), p.spec())) {
				// the same house with its addition: the household stays at home
				for (Resident res : this.residents.values()) {
					if (res.home == old.id()) {
						res.home = p.id();
					}
				}
				p.owner = old.owner;
			}
			this.plots.remove(old.id());
			this.bounds = null;
			c.plotsChanged(this);
		}
		this.derivedDirty = true;
		markDirty();
		c.progressed(this, p);
		HearthWorld w = c.eventWorld();
		if (w != null) {
			SettlementEvents.BUILDING_COMPLETED.invoker().onBuildingCompleted(w, this, p);
		}
	}

	// ---------------------------------------------------------------- derived numbers

	public void invalidateDerived() {
		this.derivedDirty = true;
	}

	private void ensureDerived() {
		if (!this.derivedDirty && this.waitingDigs != null) {
			// an excavation's blueprint was being made at the last count: count again once one of them is ready, not on
			// every read meanwhile (each read of beds() or the stockpile recomputed the whole town)
			for (int id : this.waitingDigs) {
				SimPlot p = this.plots.get(id);
				if (p == null || !p.isActive() || factsReady(p)) {
					this.derivedDirty = true;
					break;
				}
			}
		}
		if (this.derivedDirty) {
			recomputeDerived();
		}
	}

	/** Whether a standing plot is a working dug out (a quarry or clay pit, a mine; not a woodlot, which works on). */
	private boolean workedOut(SimPlot p) {
		var x = this.ctx.data().extraction(p.spec().archetype());
		return x != null && x.kind() != dev.voidmc.hearth.api.ResourceSite.Kind.WOODS && !x.yieldsByCampaign();
	}

	/** Excavations whose blueprints were not ready at the last count of the derived figures, or null. */
	private int @Nullable [] waitingDigs;

	/** Beds, job slots and storage from the standing buildings. */
	public void recomputeDerived() {
		this.derivedDirty = false;
		this.waitingDigs = null;
		it.unimi.dsi.fastutil.ints.IntArrayList waiting = null;
		this.beds = 0;
		this.storageBuildings = 0;
		this.buildings = 0;
		java.util.Arrays.fill(this.jobSlots, 0);
		this.activeDigs = 0;
		// the job sites of workings dug out count once a trade, as the most any one of them has: their crew stays (to cut
		// the next face, and to gather at the surface meanwhile), but a town that has worked out ten pits does not keep ten
		// pits' crews (the next opens as the last is cut out, requests/w7-tour.md §3)
		int[] dugOut = new int[this.jobSlots.length];
		for (SimPlot p : this.plots.values()) {
			if (p.isActive() && this.ctx != null && this.ctx.data().extraction(p.spec().archetype()) != null) {
				// an excavation employs its diggers while it is being dug
				p.digs = true;
				this.activeDigs++;
				if (!factsReady(p)) {
					// its blueprint is being made: counted again (with its diggers' places) once it is ready
					if (waiting == null) {
						waiting = new it.unimi.dsi.fastutil.ints.IntArrayList();
					}
					waiting.add(p.id());
					continue;
				}
				int[] jobs = facts(p).jobs();
				p.jobs = jobs.clone();
				for (int j = 0; j < jobs.length && j < this.jobSlots.length; j++) {
					this.jobSlots[j] += jobs[j];
				}
				continue;
			}
			if (!p.isStanding()) {
				continue;
			}
			this.beds += p.beds;
			if (this.ctx != null) {
				// (a save from before job sites were kept to the archetype's trades)
				p.jobs = PlotFacts.listed(p.jobs, this.ctx.blueprints().archetype(p.spec().archetype()));
			}
			boolean worked = this.ctx != null && workedOut(p);
			for (int j = 0; j < p.jobs.length && j < this.jobSlots.length; j++) {
				if (worked) {
					dugOut[j] = Math.max(dugOut[j], p.jobs[j]);
				} else {
					this.jobSlots[j] += p.jobs[j];
				}
			}
			if (p.storage) {
				this.storageBuildings++;
			} else {
				this.buildings++;
			}
		}
		for (int j = 0; j < dugOut.length; j++) {
			this.jobSlots[j] += dugOut[j];
		}
		SimRules rules = this.ctx == null ? null : this.ctx.rules();
		if (rules != null) {
			// store room per goods: every building's lofts and cellars, storehouses, and each building type's own goods
			int n = Goods.VALUES.length;
			int[] cap = new int[n];
			java.util.Arrays.fill(cap, rules.storage.base());
			java.util.Arrays.fill(this.coolCapacity, 0);
			var data = this.ctx.data();
			for (SimPlot p : this.plots.values()) {
				if (!p.isStanding()) {
					continue;
				}
				int[] add = rules.storageFor(p.spec().archetype());
				if (add != null) {
					for (int g = 0; g < n; g++) {
						cap[g] += add[g];
					}
				} else {
					int all = p.storage ? rules.storage.perStorage() : rules.storage.perBuilding();
					for (int g = 0; g < n; g++) {
						cap[g] += all;
					}
				}
				if (data.cool(p.spec().archetype())) {
					int[] room = add != null ? add : null;
					for (int g = 0; g < n; g++) {
						this.coolCapacity[g] += room != null ? room[g] : rules.storage.perStorage();
					}
				}
			}
			this.stockpile.setCapacity(cap);
		}
		if (waiting != null) {
			this.waitingDigs = waiting.toIntArray();
		}
	}

	/** Excavations being dug (so a step with none skips looking for them). */
	public int activeDigs() {
		ensureDerived();
		return this.activeDigs;
	}

	/** Store room kept cool (granaries, cellars) by goods ordinal: food there barely spoils. */
	public int coolCapacity(int goods) {
		ensureDerived();
		return this.coolCapacity[goods];
	}

	/** Job sites in standing buildings, by job. */
	public int jobSlots(JobType job) {
		ensureDerived();
		return this.jobSlots[job.ordinal()];
	}

	public int storageBuildings() {
		ensureDerived();
		return this.storageBuildings;
	}

	// ---------------------------------------------------------------- clock state

	/** The sim time (overworld clock ticks, plus any {@code /hearth settlement simulate} offset) this record has reached. */
	public long simTick() {
		return this.clock.simTick;
	}

	public long timeOffset() {
		return this.clock.timeOffset;
	}

	/** Sets where the record's clock stands (a settlement founded now starts at now). */
	public void setSimTick(long tick) {
		this.clock.simTick = tick;
		markDirty();
	}

	/** Swaps every resident at once without events (adoption re-peoples a history this way, before FOUNDED). */
	public void replaceResidents(List<Resident> people) {
		this.residents.clear();
		this.embodiedResidents.clear();
		for (Resident r : people) {
			this.residents.put(r.id, r);
		}
		markDirty();
	}

	public void addTimeOffset(long ticks) {
		this.clock.timeOffset += ticks;
		markDirty();
	}

	public ClockState clockState() {
		return this.clock;
	}

	/** A fresh random for one step, from the settlement's own seed sequence (so a history replays exactly). */
	public RandomSource random() {
		long z = this.clock.rng += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return RandomSource.create(z ^ (z >>> 31));
	}

	public void setTier(SettlementTier tier) {
		this.tier = tier;
	}

	public List<Resident> residentList() {
		return new ArrayList<>(this.residents.values());
	}

	@Override
	public String toString() {
		return "Settlement[" + this.id + " " + this.name + ", " + this.tier.getSerializedName() + ", " + population() + " souls, " + this.plots.size()
			+ " plots]";
	}

	// ---------------------------------------------------------------- persistence

	/**
	 * The clock's own state: where the record has got to and the slow counters the model keeps between steps.
	 */
	public static final class ClockState {
		public static final Codec<ClockState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("sim_tick").forGetter(c -> c.simTick),
			Codec.LONG.optionalFieldOf("offset", 0L).forGetter(c -> c.timeOffset),
			Codec.LONG.fieldOf("rng").forGetter(c -> c.rng),
			Codec.INT.optionalFieldOf("hunger_days", 0).forGetter(c -> c.hungerDays),
			Codec.INT.optionalFieldOf("overcrowd_days", 0).forGetter(c -> c.overcrowdDays),
			Codec.DOUBLE.listOf().optionalFieldOf("fractions", List.of()).forGetter(c -> {
				List<Double> l = new ArrayList<>();
				for (double d : c.fractions) {
					l.add(d);
				}
				return l;
			}),
			Codec.DOUBLE.optionalFieldOf("coin_fraction", 0.0).forGetter(c -> c.coinFraction),
			Codec.LONG.optionalFieldOf("no_growth_until", Long.MIN_VALUE).forGetter(c -> c.noGrowthUntil),
			Codec.LONG.optionalFieldOf("days_lived", 0L).forGetter(c -> c.daysLived),
			Codec.INT.optionalFieldOf("crowded_days", 0).forGetter(c -> c.crowdedDays),
			Codec.LONG.optionalFieldOf("last_daughter_day", Long.MIN_VALUE).forGetter(c -> c.lastDaughterDay),
			Codec.INT.optionalFieldOf("daughters", 0).forGetter(c -> c.daughters),
			Codec.LONG.optionalFieldOf("parent", 0L).forGetter(c -> c.parent)
		).apply(i, (tick, offset, rng, hunger, crowd, frac, coin, noGrowth, days, crowded, lastDaughter, daughters, parent) -> {
			ClockState c = new ClockState(tick, offset, rng);
			c.hungerDays = hunger;
			c.overcrowdDays = crowd;
			for (int g = 0; g < frac.size() && g < c.fractions.length; g++) {
				c.fractions[g] = frac.get(g);
			}
			c.coinFraction = coin;
			c.noGrowthUntil = noGrowth;
			c.daysLived = days;
			c.crowdedDays = crowded;
			c.lastDaughterDay = lastDaughter;
			c.daughters = daughters;
			c.parent = parent;
			return c;
		}));

		long simTick;
		long timeOffset;
		long rng;
		int hungerDays;
		int overcrowdDays;
		/** Goods produced but not yet a whole unit. */
		final double[] fractions = new double[Goods.VALUES.length];
		double coinFraction;
		/** The planner had nothing to propose; do not ask again before this sim tick. */
		long noGrowthUntil = Long.MIN_VALUE;
		/** Answers in a row that were nothing only because the call ran out of time (asked again at the next step; never saved). */
		int outOfTimeRuns;
		long daysLived;
		/** Days in a row with no free bed (settlers set out for a daughter hamlet after a few). */
		int crowdedDays;
		long lastDaughterDay = Long.MIN_VALUE;
		/** Hamlets founded by settlers from here. */
		int daughters;
		/** The settlement whose settlers founded this one, or 0. */
		long parent;

		ClockState(long simTick, long timeOffset, long rng) {
			this.simTick = simTick;
			this.timeOffset = timeOffset;
			this.rng = rng;
		}

		public int hungerDays() {
			return this.hungerDays;
		}

		public int overcrowdDays() {
			return this.overcrowdDays;
		}

		public long daysLived() {
			return this.daysLived;
		}

		public int daughters() {
			return this.daughters;
		}

		/** The planner had nothing to propose: the clock does not ask again before this sim tick. */
		public long noGrowthUntil() {
			return this.noGrowthUntil;
		}

		public long parent() {
			return this.parent;
		}

		public void setParent(long id) {
			this.parent = id;
		}

		/** A group of settlers set out from here to found a hamlet on this day. */
		public void daughterFounded(long day) {
			this.daughters++;
			this.lastDaughterDay = day;
			this.crowdedDays = 0;
		}
	}

	private record Rep(UUID player, int value) {
		static final Codec<Rep> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Rep::player),
			Codec.INT.fieldOf("value").forGetter(Rep::value)
		).apply(i, Rep::new));
	}

	public static final Codec<SimSettlement> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("id").forGetter(SimSettlement::id),
		Codec.STRING.fieldOf("name").forGetter(SimSettlement::name),
		Culture.CODEC.fieldOf("culture").forGetter(SimSettlement::culture),
		ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SimSettlement::dimension),
		BlockPos.CODEC.fieldOf("center").forGetter(SimSettlement::center),
		Codec.LONG.fieldOf("founded_day").forGetter(SimSettlement::foundedDay),
		Codec.LONG.optionalFieldOf("origin_region", NO_REGION).forGetter(SimSettlement::originRegion),
		SettlementTier.CODEC.optionalFieldOf("tier", SettlementTier.CAMP).forGetter(s -> s.tier),
		ClockState.CODEC.fieldOf("clock").forGetter(s -> s.clock),
		Codec.INT.optionalFieldOf("next_plot_id", 0).forGetter(s -> s.nextPlotId),
		SimPlot.CODEC.listOf().optionalFieldOf("plots", List.of()).forGetter(s -> List.copyOf(s.plots.values())),
		Resident.CODEC.listOf().optionalFieldOf("residents", List.of()).forGetter(s -> List.copyOf(s.residents.values())),
		SimStockpile.CODEC.optionalFieldOf("stockpile", new SimStockpile()).forGetter(s -> s.stockpile),
		Codec.LONG.optionalFieldOf("treasury", 0L).forGetter(s -> s.treasury),
		Rep.CODEC.listOf().optionalFieldOf("reputation", List.of()).forGetter(s -> {
			List<Rep> l = new ArrayList<>();
			s.reputation.object2IntEntrySet().forEach(e -> l.add(new Rep(e.getKey(), e.getIntValue())));
			l.sort((a, b) -> a.player.compareTo(b.player));
			return l;
		}),
		CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(s -> s.data.save())
	).apply(i, (id, name, culture, dim, center, founded, origin, tier, clock, nextPlot, plots, residents, stock, treasury, rep, data) -> {
		SimSettlement s = new SimSettlement(id, name, culture, dim, center, founded, origin, stock, clock);
		s.tier = tier;
		int next = nextPlot;
		for (SimPlot p : plots) {
			s.plots.put(p.id(), p);
			next = Math.max(next, p.id() + 1);
		}
		s.nextPlotId = next;
		for (Resident r : residents) {
			s.residents.put(r.id, r);
		}
		s.treasury = treasury;
		for (Rep r : rep) {
			s.reputation.put(r.player, r.value);
		}
		s.data.load(data);
		return s;
	}));

	/** A deep copy through the codec (how an adopted settlement leaves the atlas's cached history behind). */
	public SimSettlement copy() {
		var ops = net.minecraft.nbt.NbtOps.INSTANCE;
		return CODEC.parse(ops, CODEC.encodeStart(ops, this).getOrThrow()).getOrThrow();
	}

	/** For tests and commands: plot states counted. */
	public Map<PlotState, Integer> plotCounts() {
		Map<PlotState, Integer> m = new java.util.EnumMap<>(PlotState.class);
		for (SimPlot p : this.plots.values()) {
			m.merge(p.state, 1, Integer::sum);
		}
		return m;
	}
}
