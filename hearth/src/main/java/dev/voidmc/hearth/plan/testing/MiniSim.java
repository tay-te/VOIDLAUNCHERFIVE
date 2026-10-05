package dev.voidmc.hearth.plan.testing;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Needs;
import dev.voidmc.hearth.plan.society.Stations;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * A minimal stand-in for the sim module, enough to grow a settlement offline for the planner's tests and previews:
 * builders work through planned plots in order (work estimated from the archetype, one builder doing about 330 units
 * a day as in ARCHITECTURE.md §4), finished houses give beds, newcomers arrive while beds are free, residents take the
 * jobs workplaces offer, and the planner is asked for a project whenever builders are idle. Rebuilds replace their old
 * plot when they start. Deterministic for a seed.
 */
public final class MiniSim {
	private static final int UNITS_PER_BUILDER_DAY = 330;
	private static final double GROWTH_RATE = 0.028;
	/** The population growth levels off toward (a city of about 220); {@link #capacity(int)} raises it to grow a big city. */
	private int capacity = 220;
	private static final String[] NAMES = {"Ada", "Bram", "Cora", "Dain", "Edda", "Finn", "Gwen", "Hal", "Ivy", "Jory", "Kit", "Lene"};

	public final SurveyedSettlement settlement;
	public final TerrainProbe terrain;
	private final SettlementPlanner planner;
	private final RandomSource random;
	private final Catalogue catalogue;
	private final Map<Integer, Integer> progress = new java.util.HashMap<>();
	private final List<String> log = new ArrayList<>();
	private int day;
	private double growth;
	/** Total time spent in planner calls, nanoseconds, and the slowest call. */
	public long plannerNanos;
	public long slowestNext;
	public int calls;

	public MiniSim(Culture culture, TerrainProbe terrain, long seed, SettlementPlanner planner) {
		this(culture, terrain, seed, planner, new BlockPos(0, terrain.groundY(0, 0), 0));
	}

	public MiniSim(Culture culture, TerrainProbe terrain, long seed, SettlementPlanner planner, BlockPos center) {
		this(culture, terrain, seed, planner, center, (heart, radius) -> MiniSurvey.survey(terrain, heart, radius));
	}

	/**
	 * With a survey of its own: {@code surveyor} gives the sites round a heart within a radius (the sim's own survey, so the
	 * planner's tests see the sites the game gives a town).
	 */
	public MiniSim(Culture culture, TerrainProbe terrain, long seed, SettlementPlanner planner, BlockPos center,
		java.util.function.BiFunction<BlockPos, Integer, List<dev.voidmc.hearth.api.ResourceSite>> surveyor) {
		this.surveyor = surveyor;
		this.terrain = terrain;
		this.planner = planner;
		this.random = RandomSource.create(seed);
		this.catalogue = Catalogue.current();
		this.settlement = new SurveyedSettlement(seed, "Preview " + seed, culture, center, 0);
		survey();
		for (int i = 0; i < 4; i++) {
			addResident(i == 0 ? JobType.BUILDER : i == 1 ? JobType.FARMER : JobType.NONE);
		}
		long t0 = System.nanoTime();
		List<PlotSpec> first = planner.found(new SettlementPlanner.FoundingContext(this.settlement, center, terrain, this.random));
		this.plannerNanos += System.nanoTime() - t0;
		for (PlotSpec spec : first) {
			this.settlement.addPlot(spec);
		}
		this.log.add("day 0: founded with " + first.size() + " plots");
	}

	/** Grows toward this many souls instead of about 220 (to see what a city of four or five hundred plans). */
	public MiniSim capacity(int souls) {
		this.capacity = souls;
		return this;
	}

	public int day() {
		return this.day;
	}

	public List<String> log() {
		return this.log;
	}

	public void run(int days) {
		for (int i = 0; i < days; i++) {
			step();
		}
	}

	/** One sim day: build, house, grow, employ, plan. */
	public void step() {
		this.day++;
		int builders = builders();
		int labour = builders * UNITS_PER_BUILDER_DAY;
		List<SimpleSettlement.SimplePlot> active = new ArrayList<>();
		for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
			if (p.state == PlotState.PLANNED || p.state == PlotState.BUILDING) {
				active.add(p);
			}
		}
		// builders spread over the first few sites, roads first since they were planned first
		int sites = Math.min(active.size(), 1 + builders / 2);
		for (int i = 0; i < sites && labour > 0; i++) {
			SimpleSettlement.SimplePlot p = active.get(i);
			int need = work(p.spec()) - this.progress.getOrDefault(p.id(), 0);
			int give = Math.min(need, Math.max(labour / (sites - i), 1));
			labour -= give;
			this.progress.merge(p.id(), give, Integer::sum);
			if (p.state == PlotState.PLANNED) {
				p.state = PlotState.BUILDING;
				int replaces = p.spec().params().getIntOr("replaces", -1);
				if (replaces >= 0 && this.settlement.plot(replaces) != null) {
					this.settlement.removePlot(replaces);
				}
			}
			if (this.progress.get(p.id()) >= work(p.spec())) {
				p.state = PlotState.BUILT;
				p.stage = 3;
			}
		}
		this.settlement.setBeds(beds());
		grow();
		employ();
		house();
		if (this.settlement.tier() != this.surveyedTier) {
			survey();
		}
		plan();
	}

	/**
	 * The territory and its sites (LIVING_WORLD.md §7.1), as the sim's survey would give them: the radius grows with the
	 * tier, and the stand-in survey reads the probe again.
	 */
	private void survey() {
		SettlementTier tier = this.settlement.tier();
		int radius = switch (tier) {
			case CAMP -> 112;
			case HAMLET -> 144;
			case VILLAGE -> 176;
			case TOWN -> 224;
			case CITY -> 288;
		};
		this.settlement.setTerritoryRadius(radius);
		this.settlement.setSites(this.surveyor.apply(this.settlement.center(), radius));
		this.surveyedTier = tier;
	}

	private SettlementTier surveyedTier = SettlementTier.CAMP;
	private final java.util.function.BiFunction<BlockPos, Integer, List<dev.voidmc.hearth.api.ResourceSite>> surveyor;

	/**
	 * Moves households into homes as the sim is asked to (docs/requests/p3-plan.md): the household a home was planned for
	 * first, then those whose station the home serves, then anyone into any free bed.
	 */
	private void house() {
		Map<Integer, Integer> free = new java.util.HashMap<>();
		Map<Integer, Station> serves = new java.util.HashMap<>();
		Map<String, Integer> named = new java.util.HashMap<>();
		List<SimpleSettlement.SimplePlot> homes = new ArrayList<>();
		for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
			ArchetypeInfo info = this.catalogue.info(p.spec().archetype());
			if (p.state != PlotState.BUILT || info == null || info.beds() <= 0 || !Stations.isHome(info)) {
				continue;
			}
			homes.add(p);
			free.put(p.id(), Needs.estimateBeds(info, p.spec()));
			Station st = Stations.home(p.spec(), info);
			if (st != null) {
				serves.put(p.id(), st);
			}
			String head = p.spec().params().getCompoundOrEmpty("household").getStringOr("head", "");
			if (!head.isEmpty()) {
				named.put(head, p.id());
			}
		}
		for (Resident r : this.settlement.residents()) {
			if (r.home >= 0 && free.containsKey(r.home)) {
				free.merge(r.home, -1, Integer::sum);
			} else {
				r.home = Resident.NO_PLOT;
			}
		}
		List<Resident> residents = new ArrayList<>(this.settlement.residents());
		residents.sort(java.util.Comparator.comparing(r -> r.id));
		for (Resident r : residents) {
			Station want = Stations.of(r, this.settlement.tier());
			if (r.home >= 0 && serves.get(r.home) == want) {
				continue;
			}
			// the home planned for this household, else one that serves its station, else (homeless) any free bed
			Integer target = named.get(r.id.toString());
			if (target != null && free.getOrDefault(target, 0) <= 0) {
				target = null;
			}
			for (int pass = 0; pass < 2 && target == null; pass++) {
				if (pass == 1 && r.home >= 0) {
					break;
				}
				for (SimpleSettlement.SimplePlot h : homes) {
					if (free.getOrDefault(h.id(), 0) > 0 && (pass == 1 || serves.get(h.id()) == want)) {
						target = h.id();
						break;
					}
				}
			}
			if (target != null) {
				if (r.home >= 0) {
					free.merge(r.home, 1, Integer::sum);
				}
				r.home = target;
				free.merge(target, -1, Integer::sum);
			}
		}
	}

	private int builders() {
		int n = 0;
		for (Resident r : this.settlement.residents()) {
			if (r.job == JobType.BUILDER) {
				n++;
			}
		}
		return Math.max(1, n);
	}

	private void plan() {
		int active = 0;
		for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
			if (p.state == PlotState.PLANNED || p.state == PlotState.BUILDING) {
				active++;
			}
		}
		int want = 2 + builders();
		for (int i = 0; i < 8 && active < want; i++) {
			long t0 = System.nanoTime();
			SettlementPlanner.GrowthProject project = this.planner.next(this.settlement, this.terrain, this.random);
			long dt = System.nanoTime() - t0;
			this.plannerNanos += dt;
			this.slowestNext = Math.max(this.slowestNext, dt);
			this.calls++;
			if (project == null) {
				break;
			}
			var plot = this.settlement.addPlot(project.spec());
			if (!project.spec().archetype().getPath().startsWith("infra/")) {
				active++;
			}
			this.log.add("day " + this.day + ": #" + plot.id() + " " + project.reason() + " [" + project.spec().archetype().getPath() + "]"
				+ (project.replaces() >= 0 ? " replaces #" + project.replaces() : ""));
		}
	}

	private int beds() {
		int beds = 0;
		for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
			ArchetypeInfo info = this.catalogue.info(p.spec().archetype());
			if (p.state == PlotState.BUILT && info != null && info.beds() > 0 && Stations.isHome(info)) {
				beds += Needs.estimateBeds(info, p.spec());
			}
		}
		return beds;
	}

	/**
	 * Logistic growth toward a city of about 220, limited by free beds: a hamlet at a month, a town by the fourth month,
	 * a city by the sixth, which matches the ages the atlas gives settlements (0–400 days).
	 */
	private void grow() {
		int pop = this.settlement.population();
		int free = this.settlement.beds() - pop;
		this.growth += Math.max(0.15, GROWTH_RATE * pop * (1 - pop / (double) this.capacity));
		int arrivals = Math.min(free, (int) this.growth);
		if (arrivals <= 0) {
			this.growth = Math.min(this.growth, 3);
			return;
		}
		this.growth -= arrivals;
		for (int i = 0; i < arrivals; i++) {
			addResident(JobType.NONE);
		}
	}

	/** Residents without work take jobs the built workplaces offer; a share always build. */
	private void employ() {
		Map<JobType, Integer> slots = new EnumMap<>(JobType.class);
		for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
			ArchetypeInfo info = this.catalogue.info(p.spec().archetype());
			if (p.state != PlotState.BUILT || info == null) {
				continue;
			}
			int s = Needs.jobSlots(info, p.spec());
			for (JobType j : info.jobs()) {
				slots.merge(j, s, Integer::sum);
			}
		}
		Map<JobType, Integer> held = new EnumMap<>(JobType.class);
		for (Resident r : this.settlement.residents()) {
			held.merge(r.job, 1, Integer::sum);
		}
		int pop = this.settlement.population();
		boolean village = this.settlement.tier().ordinal() >= SettlementTier.VILLAGE.ordinal();
		for (Resident r : this.settlement.residents()) {
			if (r.job != JobType.NONE) {
				continue;
			}
			// as in the sim, some find no trade and live by gathering and day labour: the poor of a village and up
			if (village && ((r.appearance >>> 16) & 0xFF) < 56) {
				r.station = Station.POOR;
				continue;
			}
			if (held.getOrDefault(JobType.BUILDER, 0) < Math.max(1, pop / 6)) {
				r.job = JobType.BUILDER;
				held.merge(JobType.BUILDER, 1, Integer::sum);
				r.station = Station.of(r.job);
				continue;
			}
			// as the sim does: the workshops' and the town's trades first, then the land's (fields always want hands)
			for (int pass = 0; pass < 2 && r.job == JobType.NONE; pass++) {
				for (JobType j : JobType.values()) {
					boolean land = j == JobType.FARMER || j == JobType.HERDER || j == JobType.FISHER || j == JobType.HUNTER;
					if (j == JobType.NONE || j == JobType.BUILDER || land != (pass == 1)) {
						continue;
					}
					if (slots.getOrDefault(j, 0) > held.getOrDefault(j, 0)) {
						r.job = j;
						held.merge(j, 1, Integer::sum);
						r.station = Station.of(j);
						break;
					}
				}
			}
		}
		place();
		// the purse moves some households up, as the npc module's stations follow wealth: a few craftsmen prosper into
		// merchants, and in a town a few old merchant families count as gentry
		SettlementTier tier = this.settlement.tier();
		for (Resident r : this.settlement.residents()) {
			double luck = (r.appearance & 0xFFFF) / 65536.0;
			if (r.station == Station.ARTISAN && tier.ordinal() >= SettlementTier.VILLAGE.ordinal() && luck < 0.12) {
				r.station = Station.MERCHANT;
			} else if (r.station == Station.MERCHANT && tier.ordinal() >= SettlementTier.TOWN.ordinal() && luck > 0.86) {
				r.station = Station.GENTRY;
			}
		}
	}

	/**
	 * Gives each worker a workplace with a free place for the trade, as the sim does (keeping the one they have while it
	 * stands); builders past the yards' places, and anyone whose trade has no place left, work from home (none).
	 */
	private void place() {
		Map<Long, Integer> used = new java.util.HashMap<>();
		List<Resident> residents = new ArrayList<>(this.settlement.residents());
		residents.sort(java.util.Comparator.comparing(r -> r.id));
		for (int pass = 0; pass < 2; pass++) {
			for (Resident r : residents) {
				if (r.job == JobType.NONE) {
					r.work = Resident.NO_PLOT;
					continue;
				}
				if (pass == 0) {
					// keep a place that still stands and still has room
					if (r.work >= 0 && !take(r.work, r.job, used)) {
						r.work = Resident.NO_PLOT;
					}
					continue;
				}
				if (r.work >= 0) {
					continue;
				}
				for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
					if (take(p.id(), r.job, used)) {
						r.work = p.id();
						break;
					}
				}
			}
		}
	}

	/** Takes a place for a trade at a plot if it stands, employs the trade and has one free. */
	private boolean take(int plot, JobType job, Map<Long, Integer> used) {
		SimpleSettlement.SimplePlot p = this.settlement.plot(plot);
		ArchetypeInfo info = p == null ? null : this.catalogue.info(p.spec().archetype());
		if (p == null || p.state != PlotState.BUILT || info == null || !info.jobs().contains(job)) {
			return false;
		}
		long key = (long) plot << 8 | job.ordinal();
		int n = used.getOrDefault(key, 0);
		if (n >= Needs.jobSlots(info, p.spec())) {
			return false;
		}
		used.put(key, n + 1);
		return true;
	}

	private void addResident(JobType job) {
		UUID id = new UUID(this.random.nextLong(), this.random.nextLong());
		String name = NAMES[this.random.nextInt(NAMES.length)];
		this.settlement.addResident(new Resident(id, name, "of " + this.settlement.name(), this.random.nextBoolean(), AgeStage.ADULT,
			this.day - 20 * 365, job, Resident.NO_PLOT, Resident.NO_PLOT, this.random.nextInt(), new CompoundTag()));
	}

	/** Work units to build a plot, estimated from its size and kind (about a third of its volume, as blocks). */
	public int work(PlotSpec spec) {
		var id = spec.archetype();
		if (Archetypes.isLinear(id) || id.equals(Archetypes.WALKWAY)) {
			int[] pts = spec.params().getIntArray("points").orElse(new int[6]);
			int len = 0;
			for (int i = 3; i < pts.length; i += 3) {
				len += Math.max(Math.abs(pts[i] - pts[i - 3]), Math.abs(pts[i + 2] - pts[i - 1]));
			}
			return Math.max(10, len * spec.params().getIntOr("width", 3) * 2);
		}
		ArchetypeInfo info = this.catalogue.info(id);
		int area = spec.sizeX() * spec.sizeZ();
		if (info != null && (info.category() == ArchetypeCategory.FARM || info.category() == ArchetypeCategory.LANDSCAPE
			|| info.category() == ArchetypeCategory.INFRASTRUCTURE)) {
			return Math.max(20, area);
		}
		int h = info == null ? spec.maxHeight() : Math.min(spec.maxHeight(), (info.minHeight() + info.maxHeight()) / 2);
		return Math.max(40, (int) (area * h * 0.32));
	}
}
