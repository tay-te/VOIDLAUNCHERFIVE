package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Occupancy;
import dev.voidmc.hearth.plan.grid.PlotGeometry;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.layout.Districts;
import dev.voidmc.hearth.plan.layout.Founder;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.grid.Cell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The settlement planner (ARCHITECTURE.md §5–7): {@link #found} lays a new settlement out; {@link #next} decides what
 * it builds next. Stateless itself (one instance serves every settlement on every thread); everything it remembers is
 * in the settlement's {@link PlanState}. Deterministic for a given settlement state and random: choices come from the
 * random or from stable hashes, collections are walked in id order, and nothing reads the world, only the probe.
 */
public final class HearthPlanner implements SettlementPlanner {
	/** {@code -Dhearth.dev.plan.deadlineMs}: a live call's time limit for every call (offline previews and tests). */
	public static double DEV_DEADLINE_MS = Double.parseDouble(System.getProperty("hearth.dev.plan.deadlineMs", "0"));

	private final HearthDataKey<PlanState> key;
	private final PlanConfig config;

	public HearthPlanner(HearthDataKey<PlanState> key, PlanConfig config) {
		this.key = key;
		this.config = config;
	}

	public HearthDataKey<PlanState> key() {
		return this.key;
	}

	public PlanConfig config() {
		return this.config;
	}

	@Override
	public List<PlotSpec> found(FoundingContext fc) {
		Settlement s = fc.settlement();
		PlanState state = s.data(this.key);
		resetForFounding(state);
		PlanContext ctx = new PlanContext(s, state, fc.terrain(), fc.random(), Catalogue.current(), this.config);
		reconcile(ctx);
		List<PlotSpec> out = layOut(ctx, fc.center(), fc.terrain());
		state.lastTier = s.tier();
		state.issued.clear();
		state.issued.addAll(out);
		state.serial += out.size();
		state.occupancy = ctx.occ;
		s.markDirty();
		return out;
	}

	private static void resetForFounding(PlanState state) {
		state.founded = false;
		state.graph = new dev.voidmc.hearth.plan.model.RoadGraph();
		state.districts.clear();
		state.reservations.clear();
		state.queue.clear();
		state.issued.clear();
		state.cooldowns.clear();
		state.failures.clear();
		state.wallStage = 0;
		state.wallRadius = 0;
		state.squareEdge = -1;
		state.castleHill = null;
		state.deep = null;
		state.deepRealm = 0;
		state.invalidate();
	}

	@Override
	public @Nullable GrowthProject next(Settlement settlement, TerrainProbe terrain, RandomSource random) {
		PlanState state = settlement.data(this.key);
		state.calls++;
		// a settlement with a deep town is planned level by level: the surface sees only its own plots (and the lift)
		Settlement view = state.deep != null ? dev.voidmc.hearth.plan.deep.LevelView.surface(settlement, state) : settlement;
		PlanContext ctx = new PlanContext(view, state, terrain, random, Catalogue.current(), this.config);
		if (PlanModule.isServerThread() && this.config.liveTimeLimitMs() > 0) {
			ctx.deadline = System.nanoTime() + this.config.liveTimeLimitMs() * 1_000_000L;
		} else if (DEV_DEADLINE_MS > 0) {
			// a live call's time limit off the server thread, to see offline what a city's 20 ms do to its plan
			ctx.deadline = System.nanoTime() + (long) (DEV_DEADLINE_MS * 1_000_000L);
		}
		if (!state.founded) {
			// a settlement the planner never laid out (an old save, a record made by hand): lay it out now
			reconcile(ctx);
			List<PlotSpec> first = layOut(ctx, settlement.center(), terrain);
			for (PlotSpec spec : first) {
				ctx.queue(spec, -1, "founding: " + spec.archetype().getPath());
			}
			state.lastTier = settlement.tier();
		} else {
			reconcile(ctx);
		}
		SettlementTier tier = settlement.tier();
		if (tier.ordinal() > state.lastTier.ordinal()) {
			onTier(ctx, state.lastTier, tier);
			state.lastTier = tier;
		}
		String why = null;
		if (state.queue.isEmpty() && (state.deep == null || state.deep.queue.isEmpty())) {
			why = Growth.plan(ctx);
		}
		boolean queued = !state.queue.isEmpty();
		QueuedProject project = poll(ctx);
		state.issued.clear();
		state.occupancy = ctx.occ;
		boolean deep = false;
		if (project == null && state.deep != null && !state.deep.queue.isEmpty()) {
			// the deep town's projects, when the surface has none waiting
			project = state.deep.queue.poll();
			deep = true;
		}
		boolean live = ctx.deadline != Long.MAX_VALUE || PlanStats.everyCall;
		if (project == null) {
			if (live) {
				PlanStats.nothing(settlement.id(), settlement.name(), settlement.population(), why != null ? why
					: queued ? "the queued rebuilds' old plots were gone" : "a need was met with nothing to build");
			}
			settlement.markDirty();
			return null;
		}
		if (live) {
			PlanStats.planned(settlement.id());
		}
		if (deep) {
			state.deep.issued.add(project.spec());
			state.deep.serial++;
		} else {
			state.issued.add(project.spec());
		}
		state.serial++;
		settlement.markDirty();
		return new GrowthProject(project.spec(), project.replaces(), project.reason());
	}

	/** Founds the layout, forcing the dev layout ({@code -Dhearth.dev.plan.layout}) on whatever thread this runs. */
	private static List<PlotSpec> layOut(PlanContext ctx, net.minecraft.core.BlockPos center, TerrainProbe terrain) {
		LayoutType forced = PlanModule.devLayout();
		boolean force = forced != null && LayoutChooser.FORCED.get() == null;
		if (force) {
			LayoutChooser.FORCED.set(forced);
		}
		try {
			return Founder.found(ctx, center, terrain.biome(center.getX(), center.getZ()));
		} finally {
			if (force) {
				LayoutChooser.FORCED.remove();
			}
		}
	}

	/** The next queued project still worth building (an upgrade whose old plot is gone is dropped). */
	private static @Nullable QueuedProject poll(PlanContext ctx) {
		while (!ctx.state.queue.isEmpty()) {
			QueuedProject q = ctx.state.queue.poll();
			if (q.replaces() >= 0) {
				PlotView old = ctx.settlement.plot(q.replaces());
				if (old == null || old.state() != PlotState.BUILT && old.state() != PlotState.ABANDONED) {
					ctx.state.invalidate();
					ctx.occ = rebuild(ctx);
					continue;
				}
			}
			return q;
		}
		return null;
	}

	private static void onTier(PlanContext ctx, SettlementTier from, SettlementTier to) {
		// with housing by station the quarters are drawn at village tier, so a town's social map forms as it grows
		SettlementTier at = ctx.config.living().stations() ? SettlementTier.VILLAGE : SettlementTier.TOWN;
		if (to.ordinal() >= at.ordinal() && ctx.state.districts.isEmpty()) {
			Districts.plan(ctx);
		}
	}

	/** {@link #reconcile} for a level planned on its own (the deep town). */
	public static void reconcileLevel(PlanContext ctx) {
		reconcile(ctx);
	}

	/**
	 * Brings the occupancy grid up to date with the settlement's plots: marks plots it has not seen, and rebuilds from
	 * scratch if a plot it knew is gone or a spec it handed out was not taken (the sim refused or re-planned it).
	 */
	static void reconcile(PlanContext ctx) {
		PlanState state = ctx.state;
		List<PlotView> plots = new ArrayList<>(ctx.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		boolean rebuild = state.occupancy == null || state.knownPlots == null;
		if (!rebuild && !state.issued.isEmpty()) {
			for (PlotSpec spec : state.issued) {
				boolean taken = false;
				for (int i = plots.size() - 1; i >= 0 && !taken; i--) {
					taken = taken(plots.get(i).spec(), spec);
				}
				if (!taken) {
					rebuild = true;
					break;
				}
			}
		}
		int[] known = state.knownPlots;
		if (!rebuild) {
			int seen = 0;
			java.util.Set<Integer> rebuilt = new java.util.HashSet<>();
			for (PlotView p : plots) {
				if (Arrays.binarySearch(known, p.id()) >= 0) {
					seen++;
				}
				if (p.replaces() >= 0) {
					rebuilt.add(p.replaces());
				}
			}
			if (seen != known.length) {
				// a plot that is gone because its rebuild finished stands under the rebuild's own footprint (and its kept
				// gaps only keep ground open): no need to rebuild a city's whole grid for it
				int replacedAway = 0;
				for (int id : known) {
					if (rebuilt.contains(id) && ctx.settlement.plot(id) == null) {
						replacedAway++;
					}
				}
				rebuild = seen + replacedAway != known.length;
			}
		}
		if (rebuild) {
			ctx.occ = rebuild(ctx);
		} else {
			ctx.occ = state.occupancy;
			for (PlotView p : plots) {
				if (Arrays.binarySearch(known, p.id()) < 0) {
					PlotGeometry.mark(ctx.occ, p.spec(), ctx.catalogue);
				}
			}
		}
		int[] ids = new int[plots.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = plots.get(i).id();
		}
		Arrays.sort(ids);
		state.knownPlots = ids;
		state.occupancy = ctx.occ;
		state.issued.clear();
	}

	/**
	 * Whether a plot is the spec handed out: the same, though the sim may have finished it at another tier (a prosperous
	 * town builds a grade up, {@code Prosperity.finish}); the tier is not where a plot stands. Read as not taken, every
	 * call of a prosperous city rebuilt its whole occupancy grid (requests/w5-tour.md §6).
	 */
	static boolean taken(PlotSpec plot, PlotSpec spec) {
		if (plot.tier() == spec.tier()) {
			return plot.equals(spec);
		}
		return plot.archetype().equals(spec.archetype()) && plot.style().equals(spec.style()) && plot.seed() == spec.seed()
			&& plot.origin().equals(spec.origin()) && plot.sizeX() == spec.sizeX() && plot.sizeZ() == spec.sizeZ() && plot.facing() == spec.facing()
			&& plot.maxHeight() == spec.maxHeight() && plot.params().equals(spec.params());
	}

	/** The occupancy grid from scratch: reservations, planned roads, plots in id order, then the queue. */
	static Occupancy rebuild(PlanContext ctx) {
		PlanState state = ctx.state;
		Occupancy occ = new Occupancy();
		for (Reservation r : state.reservations) {
			occ.markRect(r.x0(), r.z0(), r.x1(), r.z1(), r.code());
		}
		for (RoadEdge e : state.graph.edges()) {
			if (e.emits()) {
				Polyline.corridor(e.line().points(), e.width, (x, z) -> occ.mark(x, z, Cell.PLANNED_ROAD));
			}
		}
		for (RoadEdge e : state.graph.edges()) {
			if (e.role == RoadEdge.Role.WALL_LINE) {
				dev.voidmc.hearth.plan.growth.Walls.markLine(occ, e);
			}
		}
		List<PlotView> plots = new ArrayList<>(ctx.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		for (PlotView p : plots) {
			PlotGeometry.mark(occ, p.spec(), ctx.catalogue);
		}
		for (QueuedProject q : state.queue) {
			PlotGeometry.mark(occ, q.spec(), ctx.catalogue);
		}
		int[] ids = new int[plots.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = plots.get(i).id();
		}
		Arrays.sort(ids);
		state.knownPlots = ids;
		state.occupancy = occ;
		return occ;
	}
}
