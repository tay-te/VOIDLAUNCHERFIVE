package dev.voidmc.hearth.sim.settlement;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
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
	private @Nullable SimContext ctx;
	private boolean dirty;
	private boolean derivedDirty = true;
	private @Nullable BoundingBox bounds;
	private int beds;
	private final int[] jobSlots = new int[JobType.values().length];
	private int storageBuildings;
	private int buildings;

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

	@Override
	public int reputation(UUID player) {
		return this.reputation.getInt(player);
	}

	@Override
	public void addReputation(UUID player, int delta) {
		int v = Math.max(-100, Math.min(100, this.reputation.getInt(player) + delta));
		this.reputation.put(player, v);
		markDirty();
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
		if (p == null || !p.isActive() || steps <= 0) {
			return;
		}
		SimContext c = ctx();
		PlotFacts f = facts(p);
		long from = p.cursor();
		int remaining = steps;
		while (remaining > 0 && p.stage < f.stageCount()) {
			if (!p.funded) {
				// The builder has already started: take what the stage needs, or what there is of it.
				startStage(p, f, true);
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
		p.lastEmbodiedAdvance = c.gameTime();
		c.placedByEntity(this, p, from, p.cursor());
		markDirty();
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

	/** Gives up on a plot (its ground was built over); its finished stages stay as they are. */
	public void abandonPlot(SimPlot p) {
		if (p.state == PlotState.ABANDONED) {
			return;
		}
		p.state = PlotState.ABANDONED;
		p.funded = false;
		p.stalledOn = null;
		SimPlot old = this.plots.get(p.replaces);
		if (old != null && old.state == PlotState.DEMOLISHING) {
			old.state = PlotState.BUILT;
		}
		this.derivedDirty = true;
		markDirty();
	}

	/** The plot's blueprint facts, read once. A plot whose archetype no longer exists gets none and is abandoned. */
	public PlotFacts facts(SimPlot p) {
		PlotFacts f = p.facts;
		if (f == null) {
			try {
				f = PlotFacts.of(p.spec(), ctx().blueprints(), ctx().terrain());
			} catch (IllegalArgumentException e) {
				LOG.warn("settlement {} plot {}: {}; abandoning it", this.id, p.id(), e.getMessage());
				f = new PlotFacts(0, new int[0], new int[0], new int[0][], 0, new int[JobType.values().length], false);
				if (p.isActive()) {
					p.state = PlotState.ABANDONED;
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
		return switch (p.state) {
			case BUILT, DEMOLISHING -> f.complete();
			case PLANNED, BUILDING -> this.embodied ? p.cursor() : Cursor.pack(p.stage, 0);
			case ABANDONED -> Cursor.pack(Math.min(p.stage, f.stageCount()), 0);
		};
	}

	/** Takes the stage's goods if they are in store (or anyway, if forced: an embodied builder already started). */
	boolean startStage(SimPlot p, PlotFacts f, boolean force) {
		int[] need = f.goods()[p.stage];
		if (!force && !this.stockpile.has(need)) {
			int worst = this.stockpile.worstShortfall(need);
			if (worst >= 0 && p.stalledOn == null) {
				p.stalledOn = Goods.VALUES[worst];
				HearthWorld w = ctx().eventWorld();
				if (w != null) {
					SettlementEvents.CONSTRUCTION_STALLED.invoker().onStalled(w, this, p, p.stalledOn, this.stockpile.shortfall(need, worst));
				}
			}
			return false;
		}
		this.stockpile.takeAll(need);
		p.funded = true;
		p.stalledOn = null;
		if (p.state == PlotState.PLANNED) {
			p.state = PlotState.BUILDING;
			SimPlot old = this.plots.get(p.replaces);
			if (old != null && old.state == PlotState.BUILT) {
				// the old building comes down as the new one goes up: its beds and workplaces stop counting
				old.state = PlotState.DEMOLISHING;
				this.derivedDirty = true;
			}
		}
		return true;
	}

	/** The current stage is done: on to the next, or the whole plot. */
	void finishStage(SimPlot p, PlotFacts f, boolean notify) {
		p.stage++;
		p.step = 0;
		p.labour = 0;
		p.funded = false;
		if (p.stage >= f.stageCount()) {
			finishPlot(p, f);
		} else if (notify) {
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
		if (this.derivedDirty) {
			recomputeDerived();
		}
	}

	/** Beds, job slots and storage from the standing buildings. */
	public void recomputeDerived() {
		this.derivedDirty = false;
		this.beds = 0;
		this.storageBuildings = 0;
		this.buildings = 0;
		java.util.Arrays.fill(this.jobSlots, 0);
		for (SimPlot p : this.plots.values()) {
			if (!p.isStanding()) {
				continue;
			}
			this.beds += p.beds;
			for (int j = 0; j < p.jobs.length && j < this.jobSlots.length; j++) {
				this.jobSlots[j] += p.jobs[j];
			}
			if (p.storage) {
				this.storageBuildings++;
			} else {
				this.buildings++;
			}
		}
		SimRules rules = this.ctx == null ? null : this.ctx.rules();
		if (rules != null) {
			this.stockpile.setCapacity(rules.storage.base() + rules.storage.perStorage() * this.storageBuildings
				+ rules.storage.perBuilding() * this.buildings);
		}
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
			Codec.LONG.optionalFieldOf("days_lived", 0L).forGetter(c -> c.daysLived)
		).apply(i, (tick, offset, rng, hunger, crowd, frac, coin, noGrowth, days) -> {
			ClockState c = new ClockState(tick, offset, rng);
			c.hungerDays = hunger;
			c.overcrowdDays = crowd;
			for (int g = 0; g < frac.size() && g < c.fractions.length; g++) {
				c.fractions[g] = frac.get(g);
			}
			c.coinFraction = coin;
			c.noGrowthUntil = noGrowth;
			c.daysLived = days;
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
		long daysLived;

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

	/** For the planner's sake, pairs of (plot, facts) of everything standing. */
	public List<Pair<SimPlot, PlotFacts>> standing() {
		List<Pair<SimPlot, PlotFacts>> l = new ArrayList<>();
		for (SimPlot p : this.plots.values()) {
			if (p.isStanding()) {
				l.add(Pair.of(p, facts(p)));
			}
		}
		return l;
	}
}
