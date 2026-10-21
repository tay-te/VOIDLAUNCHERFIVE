package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.society.StationLedger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * What a settlement has and lacks, read from its plots, residents and the planner's queue: beds against people, each
 * trade's workers against its workplaces, food against mouths, and how many of each archetype stand or are planned.
 */
public final class Needs {
	public final int population;
	public final int adults;
	/** Beds in finished buildings (the sim's count). */
	public final int beds;
	/** Beds expected from housing planned, under way or queued. */
	public int plannedBeds;
	/** Residents with no home (no bed free for them: an old save's inn folk, newcomers past the beds). */
	public final int homeless;
	public final Map<Identifier, Integer> counts = new HashMap<>();
	public final Map<ArchetypeCategory, Integer> categories = new EnumMap<>(ArchetypeCategory.class);
	public final Map<JobType, Integer> workers = new EnumMap<>(JobType.class);
	public final Map<JobType, Integer> slots = new EnumMap<>(JobType.class);
	/**
	 * Workers of a trade with nowhere to work: no workplace, or one that is not standing. The sim gives a trade only as
	 * many workers as its standing buildings have job sites, and a townhouse's workshop is a carpenter's place as much as
	 * a carpentry is, so counting every worker against the planner's own guess of the slots asked for a carpentry per
	 * townhouse (69 in a city of 441, requests/w5-tour.md §2).
	 */
	public final Map<JobType, Integer> unplaced = new EnumMap<>(JobType.class);
	/** Job slots in workplaces planned or under way (not yet standing): where the unplaced will work. */
	public final Map<JobType, Integer> plannedSlots = new EnumMap<>(JobType.class);
	/** Food production in mouths fed (built, under way and queued). */
	public int food;
	public int unbuilt;
	/** Plots whose builders wait for goods the stores lack ({@code PlotView.waitingForGoods}). */
	public int waiting;

	private Needs(PlanContext ctx) {
		this.population = ctx.population;
		this.beds = ctx.settlement.beds();
		int a = 0;
		int unhoused = 0;
		for (Resident r : ctx.settlement.residents()) {
			if (r.age != AgeStage.CHILD) {
				a++;
			}
			if (r.home == Resident.NO_PLOT) {
				unhoused++;
			}
			if (r.job != JobType.NONE) {
				this.workers.merge(r.job, 1, Integer::sum);
				PlotView work = r.work >= 0 ? ctx.settlement.plot(r.work) : null;
				// a level's view does not show the other level's plots: a miner working down in the deep town has a place
				boolean elsewhere = work == null && r.work >= 0 && ctx.state.deep != null;
				if (!elsewhere && (work == null || work.state() != PlotState.BUILT)) {
					this.unplaced.merge(r.job, 1, Integer::sum);
				}
			}
		}
		this.adults = a;
		this.homeless = unhoused;
		masters(ctx);
		for (PlotView plot : ctx.settlement.plots()) {
			boolean built = plot.state() == PlotState.BUILT || plot.state() == PlotState.ABANDONED;
			if (plot.state() == PlotState.DEMOLISHING) {
				continue;
			}
			if (plot.waitingForGoods()) {
				this.waiting++;
			}
			count(ctx, plot.spec(), built);
		}
		for (QueuedProject q : ctx.state.queue) {
			count(ctx, q.spec(), false);
		}
	}

	/**
	 * A master craftsman sharing a workshop with another master of the trade wants one of their own (the lives,
	 * docs/LIVING_WORLD_2.md §4.1: "he'll have his own forge by spring"): one more of the trade counted unplaced, at most
	 * one a trade at a time, so the trade's workplace need reads it. Masters are read from the residents' documented
	 * {@code extra("lives").mt}.
	 */
	private void masters(PlanContext ctx) {
		Map<JobType, java.util.Set<Integer>> shared = null;
		Map<Long, Integer> at = null;
		for (Resident r : ctx.settlement.residents()) {
			if (r.age != AgeStage.ADULT || r.work < 0 || !(r.extra.get("lives") instanceof net.minecraft.nbt.CompoundTag lives)) {
				continue;
			}
			String mt = lives.getStringOr("mt", "");
			if (mt.isEmpty() || !mt.equals(r.job.getSerializedName())) {
				continue;
			}
			if (at == null) {
				at = new HashMap<>();
			}
			long key = (long) r.work << 8 | r.job.ordinal();
			if (at.merge(key, 1, Integer::sum) == 2) {
				if (shared == null) {
					shared = new EnumMap<>(JobType.class);
				}
				shared.computeIfAbsent(r.job, k -> new java.util.HashSet<>()).add(r.work);
			}
		}
		if (shared != null) {
			for (JobType job : shared.keySet()) {
				this.unplaced.merge(job, 1, Integer::sum);
			}
		}
	}

	public static Needs of(PlanContext ctx) {
		return new Needs(ctx);
	}

	private void count(PlanContext ctx, PlotSpec spec, boolean built) {
		Identifier id = spec.archetype();
		this.counts.merge(id, 1, Integer::sum);
		ArchetypeInfo info = ctx.info(id);
		if (info == null) {
			return;
		}
		this.categories.merge(info.category(), 1, Integer::sum);
		if (!built && info.category() != ArchetypeCategory.INFRASTRUCTURE) {
			this.unbuilt++;
		}
		if (info.homeBeds() > 0 && !built) {
			this.plannedBeds += estimateBeds(info, spec);
		}
		int s = jobSlots(info, spec);
		for (JobType j : info.jobs()) {
			this.slots.merge(j, s, Integer::sum);
			if (!built) {
				this.plannedSlots.merge(j, s, Integer::sum);
			}
		}
		this.food += foodOf(id, info, spec);
	}

	public int count(Identifier id) {
		return this.counts.getOrDefault(id, 0);
	}

	public int category(ArchetypeCategory c) {
		return this.categories.getOrDefault(c, 0);
	}

	/** Beds a planned house will have: its archetype's minimum, more for a bigger plot, a storey's worth more if tall. */
	public static int estimateBeds(ArchetypeInfo info, PlotSpec spec) {
		double area = spec.sizeX() * spec.sizeZ();
		double min = Math.max(1, info.minFrontage() * info.minDepth());
		double scale = Math.max(1, Math.min(1.8, area / min));
		return (int) Math.round(info.beds() * scale);
	}

	/** Workers a workplace employs: two, or more on a big plot. */
	public static int jobSlots(ArchetypeInfo info, PlotSpec spec) {
		if (info.jobs().isEmpty()) {
			return 0;
		}
		if (info.category() == ArchetypeCategory.HOUSING) {
			return 1;
		}
		if (info.category() == ArchetypeCategory.FARM) {
			return Math.max(1, spec.sizeX() * spec.sizeZ() / 120);
		}
		int area = spec.sizeX() * spec.sizeZ();
		return area >= 200 ? 4 : area >= 100 ? 3 : 2;
	}

	/** Mouths a food plot feeds. */
	public static int foodOf(Identifier id, ArchetypeInfo info, PlotSpec spec) {
		int area = spec.sizeX() * spec.sizeZ();
		if (id.equals(Archetypes.FISHERY)) {
			return 9;
		}
		if (id.equals(Archetypes.HUNTERS_LODGE)) {
			return 4;
		}
		if (id.equals(Archetypes.DEEP_GLOW_GARDEN)) {
			// glowcaps and cave fruit under the vault
			return Math.max(4, area / 40);
		}
		if (info.category() != ArchetypeCategory.FARM) {
			return 0;
		}
		if (id.equals(Archetypes.PASTURE)) {
			return Math.max(3, area / 70);
		}
		if (id.equals(Archetypes.ORCHARD)) {
			return Math.max(3, area / 60);
		}
		return Math.max(3, area / 45);
	}

	/** Beds still wanted: the population plus a margin for growth, minus beds built and planned. */
	public int housingDeficit(double margin) {
		return housingDeficit(margin, 0);
	}

	/**
	 * Beds still wanted with the territory's carrying capacity as a ceiling (LIVING_WORLD.md §7.2): past what its land
	 * and trade can feed a town plans no spare beds, so newcomers stop coming (the sim settles folk only while beds are
	 * free) and it must specialise, import or send settlers away. {@code capacity} 0 means unbounded.
	 */
	public int housingDeficit(double margin, int capacity) {
		int want = this.population + Math.max(2, (int) Math.ceil(this.population * margin));
		if (capacity > 0) {
			want = Math.min(want, Math.max(this.population, capacity));
		}
		return want - this.beds - this.plannedBeds;
	}

	/** Points a call at what these needs have already worked out (the station ledger), when a kept list is reused. */
	void attach(PlanContext ctx) {
		if (this.stations != null) {
			ctx.ledger = this.stations;
		}
	}

	/** Housing by station (built on first use; see {@link StationLedger}). */
	public StationLedger stations(PlanContext ctx) {
		if (this.stations == null) {
			this.stations = StationLedger.of(ctx);
			ctx.ledger = this.stations;
		}
		return this.stations;
	}

	private @org.jspecify.annotations.Nullable StationLedger stations;
}
