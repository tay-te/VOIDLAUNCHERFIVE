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
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Needs;
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
	private static final String[] NAMES = {"Ada", "Bram", "Cora", "Dain", "Edda", "Finn", "Gwen", "Hal", "Ivy", "Jory", "Kit", "Lene"};

	public final SimpleSettlement settlement;
	public final TerrainProbe terrain;
	private final SettlementPlanner planner;
	private final RandomSource random;
	private final Catalogue catalogue;
	private final Map<Integer, Integer> progress = new java.util.HashMap<>();
	private final List<String> log = new ArrayList<>();
	private int day;
	/** Total time spent in planner calls, nanoseconds, and the slowest call. */
	public long plannerNanos;
	public long slowestNext;
	public int calls;

	public MiniSim(Culture culture, TerrainProbe terrain, long seed, SettlementPlanner planner) {
		this(culture, terrain, seed, planner, new BlockPos(0, terrain.groundY(0, 0), 0));
	}

	public MiniSim(Culture culture, TerrainProbe terrain, long seed, SettlementPlanner planner, BlockPos center) {
		this.terrain = terrain;
		this.planner = planner;
		this.random = RandomSource.create(seed);
		this.catalogue = Catalogue.current();
		this.settlement = new SimpleSettlement(seed, "Preview " + seed, culture, center, 0);
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
		plan();
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
			this.settlement.addPlot(project.spec());
			if (!project.spec().archetype().getPath().startsWith("infra/")) {
				active++;
			}
			this.log.add("day " + this.day + ": " + project.reason() + " [" + project.spec().archetype().getPath() + "]");
		}
	}

	private int beds() {
		int beds = 0;
		for (SimpleSettlement.SimplePlot p : this.settlement.plots()) {
			ArchetypeInfo info = this.catalogue.info(p.spec().archetype());
			if (p.state == PlotState.BUILT && info != null && info.category() == ArchetypeCategory.HOUSING) {
				beds += Needs.estimateBeds(info, p.spec());
			}
		}
		return beds;
	}

	private void grow() {
		int pop = this.settlement.population();
		int free = this.settlement.beds() - pop;
		if (free <= 0) {
			return;
		}
		int arrivals = Math.min(free, 1 + pop / 30);
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
		for (Resident r : this.settlement.residents()) {
			if (r.job != JobType.NONE) {
				continue;
			}
			if (held.getOrDefault(JobType.BUILDER, 0) < Math.max(1, pop / 6)) {
				r.job = JobType.BUILDER;
				held.merge(JobType.BUILDER, 1, Integer::sum);
				continue;
			}
			for (JobType j : JobType.values()) {
				if (j != JobType.NONE && j != JobType.BUILDER && slots.getOrDefault(j, 0) > held.getOrDefault(j, 0)) {
					r.job = j;
					held.merge(j, 1, Integer::sum);
					break;
				}
			}
		}
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
