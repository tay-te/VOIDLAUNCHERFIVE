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
	/** Homes as {x, z, station ordinal}: what a new home's neighbours would be. */
	private final List<int[]> homes = new ArrayList<>();
	/** Householders' trades by station. */
	private final List<java.util.Map<dev.voidmc.hearth.api.JobType, Integer>> jobs = new ArrayList<>();

	private StationLedger() {
		for (int i = 0; i < STATIONS.length; i++) {
			this.waiting.add(new ArrayList<>());
			this.jobs.add(new java.util.EnumMap<>(dev.voidmc.hearth.api.JobType.class));
		}
	}

	public static StationLedger of(PlanContext ctx) {
		StationLedger l = new StationLedger();
		Map<Integer, HomeFacts> cache = ctx.state.homeFacts;
		// every level's homes count (a deep town's dwellings house the town's miners)
		List<PlotView> plots = new ArrayList<>(dev.voidmc.hearth.plan.deep.LevelView.unwrap(ctx.settlement).plots());
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
				l.homes.add(new int[]{p.spec().origin().getX() + p.spec().sizeX() / 2, p.spec().origin().getZ() + p.spec().sizeZ() / 2, f.station.ordinal()});
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
			l.jobs.get(s.ordinal()).merge(r.job, 1, Integer::sum);
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

	/**
	 * How a spot suits a new home of a station by its neighbours (within 22 blocks): like draws like, so quarters form;
	 * a few points for each home of the same station, some taken off for each of a very different one (no hovel beside
	 * a manor). Capped, so a quarter is a tendency, not a wall.
	 */
	public double neighbourhood(Station s, int x, int z) {
		if (this.buckets == null) {
			// homes by 32-block cell, so a frontage search over a city's every street does not read every home for every place
			this.buckets = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
			for (int[] h : this.homes) {
				this.buckets.computeIfAbsent(cell(h[0] >> 5, h[1] >> 5), k -> new ArrayList<>()).add(h);
			}
		}
		// whole points summed, so the order homes are read in cannot change the score
		int score = 0;
		int rank = rank(s);
		for (int cz = (z - 22) >> 5; cz <= (z + 22) >> 5; cz++) {
			for (int cx = (x - 22) >> 5; cx <= (x + 22) >> 5; cx++) {
				List<int[]> in = this.buckets.get(cell(cx, cz));
				if (in == null) {
					continue;
				}
				for (int[] h : in) {
					int dx = h[0] - x;
					int dz = h[1] - z;
					if (dx * dx + dz * dz > 22 * 22) {
						continue;
					}
					Station o = STATIONS[h[2]];
					if (o == s) {
						score += 5;
					} else if (Math.abs(rank(o) - rank) >= 2) {
						score -= 4;
					}
				}
			}
		}
		return Math.max(-24, Math.min(30, score));
	}

	private static long cell(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private it.unimi.dsi.fastutil.longs.@Nullable Long2ObjectOpenHashMap<List<int[]>> buckets;

	/** Rough standing for neighbourliness: 0 the poor, 1 peasants, 2 artisans and the like, 3 merchants, 4 gentry and lord. */
	private static int rank(Station s) {
		return switch (s) {
			case POOR -> 0;
			case PEASANT -> 1;
			case ARTISAN, CLERGY, SCHOLAR, OFFICIAL -> 2;
			case MERCHANT -> 3;
			case GENTRY, LORD -> 4;
		};
	}

	/** Beds short over all stations: each station's shortfall that is above nothing, summed. */
	public int totalShortfall(double margin) {
		double sum = 0;
		for (Station s : STATIONS) {
			sum += Math.max(0, shortfall(s, margin));
		}
		return (int) Math.round(sum);
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

	/** The commonest trade among a station's householders (the household a new home of theirs most likely takes), or null. */
	public dev.voidmc.hearth.api.@Nullable JobType commonJob(Station s) {
		dev.voidmc.hearth.api.JobType best = null;
		int n = 0;
		for (var e : this.jobs.get(s.ordinal()).entrySet()) {
			if (e.getValue() > n) {
				n = e.getValue();
				best = e.getKey();
			}
		}
		return best;
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
