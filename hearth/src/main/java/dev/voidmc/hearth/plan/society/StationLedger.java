package dev.voidmc.hearth.plan.society;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.growth.Needs;
import dev.voidmc.hearth.plan.model.QueuedProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Housing by station (LIVING_WORLD.md §8.1): how many beds each station needs (its households, with the children and
 * elders who live with them spread over the stations as the adults are), how many beds the homes built, under way and
 * queued for it hold, and which households still live in a home not of their station. The planner builds next for the
 * station shortest of homes, in its own quarter, and names the household it is for ({@code household} param).
 */
public final class StationLedger {
	private static final Station[] STATIONS = Station.values();

	/** Beds each station needs. */
	public final double[] demand = new double[STATIONS.length];
	/** Beds in homes serving each station: built, under way and queued. */
	public final int[] supply = new int[STATIONS.length];
	/** Householders by station whose home does not serve their station (or who have none), in id order. */
	private final List<List<Resident>> waiting = new ArrayList<>();
	/** Household heads named on homes not built yet. */
	private final Set<String> named = new HashSet<>();

	private StationLedger() {
		for (int i = 0; i < STATIONS.length; i++) {
			this.waiting.add(new ArrayList<>());
		}
	}

	public static StationLedger of(PlanContext ctx) {
		StationLedger l = new StationLedger();
		Map<Integer, HomeFacts> cache = ctx.state.homeFacts;
		List<PlotView> plots = new ArrayList<>(ctx.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		java.util.HashMap<Integer, Station> byPlot = new java.util.HashMap<>();
		for (PlotView p : plots) {
			if (p.state() == PlotState.DEMOLISHING) {
				continue;
			}
			HomeFacts f = cache.get(p.id());
			if (f == null || f.seed != p.spec().seed()) {
				f = HomeFacts.of(ctx, p.spec());
				cache.put(p.id(), f);
			}
			if (f.station != null) {
				l.supply[f.station.ordinal()] += f.beds;
				byPlot.put(p.id(), f.station);
				if (f.head != null && p.state() != PlotState.BUILT) {
					l.named.add(f.head);
				}
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			HomeFacts f = HomeFacts.of(ctx, q.spec());
			if (f.station != null) {
				l.supply[f.station.ordinal()] += f.beds;
				if (f.head != null) {
					l.named.add(f.head);
				}
			}
		}
		// adults by station; the rest of the household spread over the stations as the adults are
		int[] adults = new int[STATIONS.length];
		int dependants = 0;
		List<Resident> residents = new ArrayList<>(ctx.settlement.residents());
		residents.sort(Comparator.comparing(r -> r.id));
		for (Resident r : residents) {
			if (!Stations.householder(r)) {
				dependants++;
				continue;
			}
			Station s = Stations.of(r, ctx.tier);
			adults[s.ordinal()]++;
			Station home = r.home >= 0 ? byPlot.get(r.home) : null;
			if (home != s) {
				l.waiting.get(s.ordinal()).add(r);
			}
		}
		int totalAdults = 0;
		for (int a : adults) {
			totalAdults += a;
		}
		for (int i = 0; i < STATIONS.length; i++) {
			l.demand[i] = adults[i] + (totalAdults == 0 ? 0 : dependants * adults[i] / (double) totalAdults);
		}
		if (totalAdults == 0 && dependants > 0) {
			l.demand[Station.PEASANT.ordinal()] = dependants;
		}
		return l;
	}

	/** Beds a station is short of, with the housing margin on its need. */
	public double shortfall(Station s, double margin) {
		return this.demand[s.ordinal()] * (1 + margin) - this.supply[s.ordinal()];
	}

	/**
	 * The station to build for next: drawn among those short of beds, weighted by how short (the lord and the clergy,
	 * whose homes are few and particular, count a little more); the commonest station if none is short.
	 */
	public Station pick(PlanContext ctx, double margin) {
		double total = 0;
		double[] w = new double[STATIONS.length];
		for (Station s : STATIONS) {
			double sf = shortfall(s, margin);
			if (sf > 0.5) {
				w[s.ordinal()] = sf * (s == Station.LORD || s == Station.CLERGY || s == Station.OFFICIAL ? 2.5 : 1);
				total += w[s.ordinal()];
			}
		}
		if (total <= 0) {
			Station best = Station.PEASANT;
			for (Station s : STATIONS) {
				if (this.demand[s.ordinal()] > this.demand[best.ordinal()]) {
					best = s;
				}
			}
			return best;
		}
		double r = ctx.random.nextDouble() * total;
		for (Station s : STATIONS) {
			r -= w[s.ordinal()];
			if (w[s.ordinal()] > 0 && r <= 0) {
				return s;
			}
		}
		return Station.PEASANT;
	}

	/** The household a new home of a station is for: the first of its householders without a home of their station. */
	public @Nullable Resident head(Station s) {
		for (Resident r : this.waiting.get(s.ordinal())) {
			if (!this.named.contains(r.id.toString())) {
				return r;
			}
		}
		return null;
	}

	/** Householders of a station still waiting for a home of their station. */
	public int waiting(Station s) {
		return this.waiting.get(s.ordinal()).size();
	}

	/** Marks a household as named on a home just planned, so the next home goes to another. */
	public void name(Resident r) {
		this.named.add(r.id.toString());
	}

	/**
	 * What the planner keeps about a plot for housing by station, cached by plot id and checked against the spec's seed
	 * (specs never change; a rebuild is a new plot).
	 */
	public static final class HomeFacts {
		final long seed;
		final @Nullable Station station;
		final int beds;
		final @Nullable String head;

		private HomeFacts(long seed, @Nullable Station station, int beds, @Nullable String head) {
			this.seed = seed;
			this.station = station;
			this.beds = beds;
			this.head = head;
		}

		static HomeFacts of(PlanContext ctx, PlotSpec spec) {
			ArchetypeInfo info = ctx.info(spec.archetype());
			Station st = Stations.home(spec, info);
			if (st == null) {
				return new HomeFacts(spec.seed(), null, 0, null);
			}
			String head = spec.params().getCompoundOrEmpty("household").getStringOr("head", "");
			return new HomeFacts(spec.seed(), st, Needs.estimateBeds(info, spec), head.isEmpty() ? null : head);
		}
	}

	/** Whether a resident is a child or elder (lives in a household's home). */
	static boolean dependant(Resident r) {
		return r.age != AgeStage.ADULT;
	}
}
