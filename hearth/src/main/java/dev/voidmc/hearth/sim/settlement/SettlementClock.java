package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.SettlementPlanner;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The abstract simulation of one settlement: integrates elapsed sim time into production and consumption, population,
 * construction and growth. Costs a few array operations per slice plus a pass over the residents once per sim day, so a
 * town of 150 steps in well under a fifth of a millisecond.
 *
 * <p>Time is cut into slices that never cross a day boundary: goods and labour are integrated over each slice, and the
 * once-a-day business (births, deaths, settlers, homes, jobs, tier, {@code DAY_PASSED}) runs at each boundary.
 */
public final class SettlementClock {
	/** An embodied builder that advanced a plot this recently (ticks) keeps abstract labour off it. */
	public static final long EMBODIED_HOLD = 200;
	/** What a surplus fetches in barter, as a share of its price. */
	private static final double BARTER = 0.5;

	private SettlementClock() {
	}

	/**
	 * Runs the settlement forward to {@code target} (sim ticks), or as far as one step's catch-up cap allows.
	 *
	 * @return the sim ticks actually simulated
	 */
	public static long advance(SimSettlement s, long target) {
		SimContext c = s.ctx();
		SimRules r = c.rules();
		SimSettlement.ClockState k = s.clock;
		if (target <= k.simTick) {
			return 0;
		}
		long start = k.simTick;
		long end = Math.min(target, k.simTick + r.maxCatchUpTicks);
		long t0 = System.nanoTime();
		long[] phase = new long[4];
		Workforce wf = Workforce.of(s, c);
		while (k.simTick < end) {
			long dayEnd = (Math.floorDiv(k.simTick, SimRules.DAY) + 1) * SimRules.DAY;
			long sliceEnd = Math.min(end, dayEnd);
			double days = (sliceEnd - k.simTick) / (double) SimRules.DAY;
			long a = System.nanoTime();
			Production.produce(s, wf, days, r, c);
			long b = System.nanoTime();
			construct(s, wf, days, r, c);
			long d = System.nanoTime();
			phase[0] += b - a;
			phase[1] += d - b;
			k.simTick = sliceEnd;
			if (sliceEnd == dayEnd) {
				daily(s, r, c, Math.floorDiv(dayEnd, SimRules.DAY));
				wf = Workforce.of(s, c);
			}
			long e = System.nanoTime();
			phase[2] += e - d;
			grow(s, wf, r, c);
			phase[3] += System.nanoTime() - e;
		}
		s.markDirty();
		c.dirty(s);
		if (c.eventWorld() != null) {
			WorstStep.offer(s, System.nanoTime() - t0, phase);
		}
		return end - start;
	}

	// ---------------------------------------------------------------- goods

	/**
	 * Labour an embodied resident did as an entity, credited at the abstract rates (the clock left them out of its head
	 * count while they were embodied). {@code workerDays} is a working day's worth of their trade per unit.
	 */
	static void credit(SimSettlement s, Resident res, JobType job, double workerDays) {
		if (workerDays > 0) {
			Production.credit(s, res, job, workerDays);
		}
	}

	// ---------------------------------------------------------------- construction

	/** Builders wanted per plot under way, so labour spreads over a few plots rather than one at a time. */
	static int maxConcurrent(int builders, SimRules r) {
		return Math.max(1, (builders + r.labour.builderShare() - 1) / r.labour.builderShare());
	}

	private static void construct(SimSettlement s, Workforce wf, double days, SimRules r, SimContext c) {
		if (s.embodied() && wf.embodiedBuilders > 0) {
			fundForEntities(s, wf.embodiedBuilders, r);
		}
		int builders = wf.workers[JobType.BUILDER.ordinal()];
		if (builders == 0) {
			s.builderLabourOffer = 0;
			s.builderLabourTaken = 0;
			return;
		}
		double pool = builders * r.labour.workPerBuilderHour() * r.labour.builderHoursPerDay() * days;
		// labour other modules took for repairs since the last step comes out of construction (repairs come first)
		double taken = Math.min(pool, s.builderLabourTaken);
		s.builderLabourTaken = 0;
		s.builderLabourOffer = pool;
		pool -= taken;
		int concurrent = maxConcurrent(builders, r);
		long now = c.gameTime();
		Iterable<SimPlot> order = playerFundedFirst(s);
		for (int round = 0; round < 8 && pool > 0.5; round++) {
			List<SimPlot> working = new ArrayList<>(concurrent);
			for (SimPlot p : order) {
				if (working.size() >= concurrent) {
					break;
				}
				if (!p.isActive()) {
					continue;
				}
				if (Extraction.is(s, p)) {
					// excavations are dug by their own trade (quarriers, miners), not by builders
					continue;
				}
				if (s.embodied() && p.lastEmbodiedAdvance != Long.MIN_VALUE && now - p.lastEmbodiedAdvance < EMBODIED_HOLD) {
					// an embodied builder is on it: abstract labour goes elsewhere
					continue;
				}
				if (!s.factsReady(p)) {
					// its blueprint is being made in the background (live): its builders go to the others meanwhile
					continue;
				}
				PlotFacts f = s.facts(p);
				if (!p.isActive() || p.stage >= f.stageCount()) {
					continue;
				}
				if (!p.funded && !s.startStage(p, f, false)) {
					continue;
				}
				working.add(p);
			}
			if (working.isEmpty()) {
				break;
			}
			double share = pool / working.size();
			double left = 0;
			for (SimPlot p : working) {
				left += labour(s, p, share, c);
			}
			boolean stuck = left >= pool - 1e-6;
			pool = left;
			if (stuck) {
				break;
			}
		}
		if (pool > 0.5) {
			// nothing more they can build today: the builders fetch and dress materials with the rest of their time
			Production.fetch(s, wf, r.labour.fetchShare() * pool / (r.labour.workPerBuilderHour() * r.labour.builderHoursPerDay()), r, c);
		}
	}

	/**
	 * The plots in construction order: those players paid toward (the economy's funded projects, LIVING_WORLD.md §5
	 * "fund a specific project and the planner puts it first") before the rest.
	 */
	static Iterable<SimPlot> playerFundedFirst(SimSettlement s) {
		dev.voidmc.hearth.api.EconomyService eco = dev.voidmc.hearth.api.HearthServices.economy();
		java.util.Set<Integer> funded = eco == null ? java.util.Set.of() : eco.fundedPlots(s);
		if (funded.isEmpty()) {
			return s.plots.values();
		}
		List<SimPlot> out = new ArrayList<>(s.plots.size());
		for (int id : funded) {
			SimPlot p = s.plots.get(id);
			if (p != null) {
				out.add(p);
			}
		}
		for (SimPlot p : s.plots.values()) {
			if (!funded.contains(p.id())) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * Builders walking about as entities only build plots whose current stage has its goods in hand ({@code BUILDING},
	 * not waiting): start the stages they will need, as many plots at once as there are pairs of them, or mark the plots
	 * waiting (which fires the stall, and the supply quest) so they leave them be.
	 */
	private static void fundForEntities(SimSettlement s, int builders, SimRules r) {
		int want = maxConcurrent(builders, r);
		int working = 0;
		for (SimPlot p : playerFundedFirst(s)) {
			if (working >= want) {
				return;
			}
			if (!p.isActive() || Extraction.is(s, p) || !s.factsReady(p)) {
				continue;
			}
			PlotFacts f = s.facts(p);
			if (!p.isActive() || p.stage >= f.stageCount()) {
				continue;
			}
			if (p.funded || s.startStage(p, f, false)) {
				working++;
			}
		}
	}

	/** Puts {@code units} of labour into a plot; returns what was left over when it ran out of work it could do. */
	static double labour(SimSettlement s, SimPlot p, double units, SimContext c) {
		PlotFacts f = s.facts(p);
		while (units > 0 && p.isActive() && p.stage < f.stageCount()) {
			if (!p.funded && !s.startStage(p, f, false)) {
				break;
			}
			int work = f.work()[p.stage];
			int steps = f.stepCount()[p.stage];
			double need = work - p.labour;
			if (units >= need) {
				units -= Math.max(0, need);
				s.finishStage(p, f, true);
				continue;
			}
			p.labour += units;
			units = 0;
			int step = work <= 0 ? steps : (int) Math.min(steps, Math.floor(steps * p.labour / work));
			if (step > p.step) {
				p.step = step;
				if (s.embodied()) {
					c.progressed(s, p);
				}
			}
		}
		return units;
	}

	// ---------------------------------------------------------------- growth

	private static void grow(SimSettlement s, Workforce wf, SimRules r, SimContext c) {
		SettlementPlanner planner = c.planner();
		SimSettlement.ClockState k = s.clock;
		// builders walking about as entities want work too
		int builders = wf.workers[JobType.BUILDER.ordinal()] + wf.embodiedBuilders;
		if (planner == null || builders == 0 || k.simTick < k.noGrowthUntil) {
			return;
		}
		int active = 0;
		int stalled = 0;
		for (SimPlot p : s.plots.values()) {
			if (p.isActive() && !Extraction.is(s, p)) {
				if (p.stalledOn != null) {
					stalled++;
				} else {
					active++;
				}
			}
		}
		if (stalled >= 2) {
			// short of goods twice over already: planning more would only queue more stalls
			return;
		}
		// keep every pair of builders busy, a few proposals at a time at most. Roads come first: planning one need often
		// queues a lane and then the houses along it, so a road is followed by another call in the same step (the
		// planner keeps its queue of roads short; this caps it anyway)
		int roadsInARow = 0;
		for (int tries = 0; tries < 4 && active < maxConcurrent(builders, r); tries++) {
			boolean afterRoad = roadsInARow > 0 && roadsInARow <= MAX_ROADS_IN_A_ROW;
			if ((!afterRoad || c.budgetsEveryPlannerCall()) && !c.tryPlannerCall()) {
				return;
			}
			SettlementPlanner.GrowthProject project;
			long t0 = System.nanoTime();
			try {
				project = planner.next(s, c.terrain(), s.random());
			} catch (RuntimeException e) {
				org.slf4j.LoggerFactory.getLogger("hearth").warn("planner failed for settlement {}", s.id(), e);
				project = null;
			}
			PlannerTimings.record(c.eventWorld() != null, System.nanoTime() - t0);
			if (project == null && k.outOfTimeRuns < MAX_OUT_OF_TIME_RETRIES && planner.ranOutOfTime(s)) {
				// nothing only because the call ran out of time (a live call's limit, a city's long searches): the needs it did not
				// reach are asked at the next step, not half a day later (requests/w6-plan.md §4, w7-tour.md §1); the planner rests
				// the needs that ran out of time, so the next call tries the others
				k.outOfTimeRuns++;
				k.noGrowthUntil = k.simTick + 1;
				return;
			}
			k.outOfTimeRuns = 0;
			if (project == null || !c.allows(s, project.spec())) {
				k.noGrowthUntil = k.simTick + SimRules.DAY / 2;
				return;
			}
			s.addPlot(Prosperity.finish(s, project.spec(), r), project.replaces(), project.reason());
			if (isRoad(project.spec()) && roadsInARow < MAX_ROADS_IN_A_ROW) {
				roadsInARow++;
				tries--;
				continue;
			}
			roadsInARow = 0;
			active++;
		}
	}

	/** Out-of-time answers in a row asked again at the next step before the clock waits its half day as for any other. */
	static final int MAX_OUT_OF_TIME_RETRIES = 4;

	/** Roads handed out in a row before the clock stops asking within one step. */
	static final int MAX_ROADS_IN_A_ROW = 4;

	/** Infrastructure ({@code hearth:infra/*}): roads, stairs, bridges, terraces, walkways, docks. */
	static boolean isRoad(PlotSpec spec) {
		return spec.archetype().getPath().startsWith("infra/");
	}

	/** The slowest live step since the last reset, and where its time went (for the dev harness and the tours). */
	public static final class WorstStep {
		private static long worst;
		private static String line = "none";

		private WorstStep() {
		}

		static synchronized void offer(SimSettlement s, long nanos, long[] phase) {
			if (nanos > worst) {
				worst = nanos;
				line = String.format(java.util.Locale.ROOT, "%s (%d souls, %d plots): %.2f ms = production %.2f, construction %.2f, the day %.2f, growth %.2f",
					s.name(), s.population(), s.plots().size(), nanos / 1e6, phase[0] / 1e6, phase[1] / 1e6, phase[2] / 1e6, phase[3] / 1e6);
			}
		}

		public static synchronized String line() {
			return line;
		}

		public static synchronized void reset() {
			worst = 0;
			line = "none";
		}
	}

	/** How long {@code planner.next} takes: live calls (the server thread) and history calls (atlas workers) apart. */
	public static final class PlannerTimings {
		public static final java.util.concurrent.atomic.LongAdder LIVE_CALLS = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.LongAdder LIVE_NANOS = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.AtomicLong LIVE_MAX = new java.util.concurrent.atomic.AtomicLong();
		/** Live calls under 1, 5, 20 ms and the rest. */
		public static final java.util.concurrent.atomic.LongAdder[] LIVE_BUCKETS = {new java.util.concurrent.atomic.LongAdder(),
			new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder()};
		public static final java.util.concurrent.atomic.LongAdder HISTORY_CALLS = new java.util.concurrent.atomic.LongAdder();
		public static final java.util.concurrent.atomic.LongAdder HISTORY_NANOS = new java.util.concurrent.atomic.LongAdder();
		/** The last live calls' times (a ring), for the median and the 90th percentile. */
		private static final long[] RECENT = new long[4096];
		private static int recentCount;

		private PlannerTimings() {
		}

		/** Median, 90th percentile and maximum of the recent live calls, in ms (zeros if none). */
		public static synchronized double[] percentiles() {
			int n = Math.min(recentCount, RECENT.length);
			if (n == 0) {
				return new double[3];
			}
			long[] copy = java.util.Arrays.copyOf(RECENT, n);
			java.util.Arrays.sort(copy);
			return new double[]{copy[n / 2] / 1e6, copy[Math.min(n - 1, (int) (n * 0.9))] / 1e6, copy[n - 1] / 1e6};
		}

		public static synchronized void reset() {
			recentCount = 0;
			LIVE_CALLS.reset();
			LIVE_NANOS.reset();
			LIVE_MAX.set(0);
			for (var b : LIVE_BUCKETS) {
				b.reset();
			}
		}

		private static synchronized void remember(long nanos) {
			RECENT[recentCount++ % RECENT.length] = nanos;
			if (recentCount >= 2 * RECENT.length) {
				recentCount -= RECENT.length;
			}
		}

		static void record(boolean live, long nanos) {
			if (live) {
				LIVE_CALLS.increment();
				LIVE_NANOS.add(nanos);
				LIVE_MAX.accumulateAndGet(nanos, Math::max);
				LIVE_BUCKETS[nanos < 1_000_000 ? 0 : nanos < 5_000_000 ? 1 : nanos < 20_000_000 ? 2 : 3].increment();
				remember(nanos);
			} else {
				HISTORY_CALLS.increment();
				HISTORY_NANOS.add(nanos);
			}
		}
	}

	// ---------------------------------------------------------------- the day's business

	private static void daily(SimSettlement s, SimRules r, SimContext c, long day) {
		SimSettlement.ClockState k = s.clock;
		RandomSource random = s.random();
		k.daysLived++;
		age(s, r, day);
		var data = c.data();
		Lands.daily(s, day, c);
		Spoilage.daily(s, r, data);
		k.hungerDays = s.population() > 0 && Production.edible(s, data) < 1 ? k.hungerDays + 1 : 0;
		deaths(s, r, random);
		int housing = s.beds() + r.population.tentSlots();
		k.overcrowdDays = s.population() > housing ? k.overcrowdDays + 1 : 0;
		if ((k.overcrowdDays > r.population.overcrowdDays() || k.hungerDays > r.population.hungerDays()) && random.nextFloat() < 0.5F) {
			emigrate(s, random);
		}
		Prosperity.daily(s, r, c, day, random);
		crowding(s, r, c, day);
		imports(s, r);
		necessities(s, r, c);
		if (k.hungerDays == 0) {
			births(s, r, c, random, day);
			settlers(s, r, c, random, day);
		}
		assignHomes(s);
		assignJobs(s, r);
		Stations.daily(s, r, day);
		c.trade(s, day);
		SettlementTier from = s.tier;
		SettlementTier to = tierFor(from, s.population());
		if (to != from) {
			s.tier = to;
			HearthWorld w = c.eventWorld();
			if (w != null) {
				SettlementEvents.TIER_CHANGED.invoker().onTierChanged(w, s, from, to);
			}
		}
		HearthWorld w = c.eventWorld();
		if (w != null) {
			SettlementEvents.DAY_PASSED.invoker().onDayPassed(w, s, day);
		}
	}

	/**
	 * A town that has had no free bed for days on end sends a party of settlers out to found a daughter hamlet (§5.1).
	 * The live world searches for the site off the hot path and founds it; histories never do (they keep to their region).
	 */
	private static void crowding(SimSettlement s, SimRules r, SimContext c, long day) {
		SimSettlement.ClockState k = s.clock;
		var d = r.config.daughters();
		// out of beds, or past what the land can feed: settlers set out (to land that has what this town lacks)
		boolean crowded = s.population() >= d.minPopulation() && (s.population() >= s.beds() || s.population() > s.land().capacity());
		k.crowdedDays = crowded ? k.crowdedDays + 1 : 0;
		if (!d.enabled() || k.crowdedDays < d.crowdedDays() || k.daughters >= d.maxPerTown()
			|| k.lastDaughterDay != Long.MIN_VALUE && day - k.lastDaughterDay < d.cooldownDays()) {
			return;
		}
		if (c.sendSettlers(s)) {
			// the search is under way; if it finds nowhere, the town tries again after as many crowded days
			k.crowdedDays = 0;
		}
	}

	/**
	 * A stage waiting for goods the town cannot make buys them from passing traders, a cartload a day at a markup: with
	 * coin while the treasury allows, then in barter for what the town has in plenty. Slow and dear on purpose: a player
	 * bringing the goods (a supply quest) is faster.
	 *
	 * <p>Barter is how a young town pays for what it cannot make (requests/w5-tour.md §4): its coin is a few bits a
	 * worker a day, but its land gives grain, logs and stone by the thousand. What it has in plenty is what it holds
	 * beyond its builds' wants (every stage waiting or under way) and a fortnight's eating, not a share of its store
	 * room: a village with a storehouse has room for ten thousand of everything, and keeping a fifth of that back left a
	 * granary full of grain unbartered while a house waited a month for a dozen bolts of cloth.
	 */
	private static void imports(SimSettlement s, SimRules r) {
		int[] keep = null;
		for (SimPlot p : s.plots.values()) {
			if (!p.isActive() || p.stalledOn == null) {
				continue;
			}
			PlotFacts f = s.facts(p);
			if (p.stage >= f.stageCount()) {
				continue;
			}
			if (keep == null) {
				keep = barterReserve(s, r);
			}
			int[] need = f.goods()[p.stage];
			for (int g = 0; g < need.length; g++) {
				int missing = Math.min(s.stockpile.shortfall(need, g), r.importPerDay);
				if (missing <= 0) {
					continue;
				}
				double unit = r.prices[g] * r.importMarkup;
				// coin first
				long price = (long) Math.ceil(unit);
				int bought = price <= 0 ? missing : (int) Math.min(missing, s.treasury / price);
				s.treasury -= bought * price;
				// then barter what the town has in plenty, at a loss
				for (int o = 0; o < need.length && bought < missing; o++) {
					int spare = s.stockpile.get(Goods.VALUES[o]) - Math.max(need[o], keep[o]);
					if (o == g || spare <= 0 || r.prices[o] <= 0) {
						continue;
					}
					double worth = r.prices[o] * BARTER;
					int units = (int) Math.min(missing - bought, Math.floor(spare * worth / unit));
					if (units > 0) {
						int paid = (int) Math.ceil(units * unit / worth);
						s.stockpile.takeQuiet(o, paid);
						bought += units;
					}
				}
				if (bought > 0) {
					s.stockpile.addQuiet(g, bought);
					s.land().traded(0, bought * unit);
				}
			}
		}
	}

	/** Days of eating a town keeps back from barter, of each thing it eats. */
	private static final int BARTER_FOOD_DAYS = 15;

	/**
	 * What a town keeps of each goods when it barters: what every active plot's current stage still wants (waiting or
	 * about to start), and a fortnight's eating of what it eats.
	 */
	private static int[] barterReserve(SimSettlement s, SimRules r) {
		int n = Goods.VALUES.length;
		int[] keep = new int[n];
		for (SimPlot p : s.plots.values()) {
			if (!p.isActive() || p.funded || !s.factsReady(p)) {
				continue;
			}
			PlotFacts f = s.facts(p);
			if (p.stage < f.stageCount()) {
				int[] g = f.goods()[p.stage];
				for (int i = 0; i < n && i < g.length; i++) {
					keep[i] += g[i];
				}
			}
		}
		double eat = s.population() * r.foodAdult * BARTER_FOOD_DAYS;
		double[] edible = s.ctx().data().edible();
		for (int i = 0; i < n && i < edible.length; i++) {
			if (edible[i] > 0) {
				keep[i] += (int) Math.ceil(eat / edible[i]);
			}
		}
		return keep;
	}

	/**
	 * Passing traders keep a town in tools and, while its coin lasts, in food: a cartload of tools when it is down to a
	 * few days' wear, and a few days' bread when it is starving. Dear (the import markup) and with coin only.
	 */
	private static void necessities(SimSettlement s, SimRules r, SimContext c) {
		var data = c.data();
		int tools = Goods.TOOLS.ordinal();
		int adults = 0;
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT && res.job != JobType.NONE) {
				adults++;
			}
		}
		int wantTools = (int) Math.ceil(adults * r.resources.chains().toolWear() * 10);
		buy(s, r, tools, wantTools - s.stockpile.get(Goods.TOOLS));
		double edible = Production.edible(s, data);
		double need = s.population() * r.foodAdult;
		if (edible < need * 2) {
			buy(s, r, Goods.FOOD.ordinal(), (int) Math.ceil(need * 4 - edible));
		}
	}

	private static void buy(SimSettlement s, SimRules r, int g, int want) {
		if (want <= 0) {
			return;
		}
		double unit = r.prices[g] * r.importMarkup;
		long price = Math.max(1, (long) Math.ceil(unit));
		int n = (int) Math.min(Math.min(want, r.importPerDay), s.treasury / price);
		if (n > 0) {
			s.treasury -= n * price;
			s.stockpile.addQuiet(g, n);
			s.land().traded(0, n * price);
		}
	}

	/** The tier for a population, with a margin before falling back so a town at the line does not flicker. */
	static SettlementTier tierFor(SettlementTier current, int population) {
		SettlementTier up = SettlementTier.forPopulation(population);
		if (up.ordinal() >= current.ordinal()) {
			return up;
		}
		return population < current.minPopulation * 0.8 ? up : current;
	}

	private static void age(SimSettlement s, SimRules r, long day) {
		int child = r.population.childhoodDays();
		int elder = child + r.population.adulthoodDays();
		for (Resident res : s.residents.values()) {
			long age = day - res.bornDay;
			AgeStage stage = age < child ? AgeStage.CHILD : age < elder ? AgeStage.ADULT : AgeStage.ELDER;
			if (stage != res.age) {
				res.age = stage;
				if (stage == AgeStage.ELDER) {
					// elders retire
					res.job = JobType.NONE;
					res.work = Resident.NO_PLOT;
				}
			}
		}
	}

	private static void deaths(SimSettlement s, SimRules r, RandomSource random) {
		List<UUID> dead = new ArrayList<>();
		boolean starving = s.clock.hungerDays > 2 * r.population.hungerDays();
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ELDER && !s.isResidentEmbodied(res.id)
				&& random.nextDouble() < r.population.elderDeathPerDay() * (starving ? 4 : 1)) {
				dead.add(res.id);
			}
		}
		for (UUID id : dead) {
			s.removeResident(id);
		}
	}

	private static void emigrate(SimSettlement s, RandomSource random) {
		List<Resident> adults = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT && !s.isResidentEmbodied(res.id)) {
				adults.add(res);
			}
		}
		if (!adults.isEmpty()) {
			s.removeResident(adults.get(random.nextInt(adults.size())).id);
		}
	}

	private static void births(SimSettlement s, SimRules r, SimContext c, RandomSource random, long day) {
		int free = s.beds() - s.population();
		if (free <= 0) {
			return;
		}
		// past what the land feeds, or in decline, fewer children are born
		double odds = r.population.birthPerCoupleDay() * growthFactor(s, r);
		List<Resident> mothers = new ArrayList<>();
		List<Resident> fathers = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT) {
				(res.female ? mothers : fathers).add(res);
			}
		}
		int couples = Math.min(mothers.size(), fathers.size());
		for (int i = 0; i < couples && free > 0; i++) {
			if (random.nextDouble() >= odds) {
				continue;
			}
			Resident mother = mothers.get(random.nextInt(mothers.size()));
			Resident father = null;
			for (Resident f : fathers) {
				if (f.home == mother.home && mother.home != Resident.NO_PLOT) {
					father = f;
					break;
				}
			}
			if (father == null) {
				father = fathers.get(random.nextInt(fathers.size()));
			}
			Resident child = newResident(s, c, AgeStage.CHILD, random, mother, father, day);
			child.home = mother.home;
			s.addResident(child);
			free--;
		}
	}

	private static void settlers(SimSettlement s, SimRules r, SimContext c, RandomSource random, long day) {
		int free = s.beds() - s.population();
		if (free <= 0 || random.nextDouble() >= r.population.settlerPerDay() * settlerFactor(s, r)) {
			return;
		}
		int group = Math.min(free, 1 + random.nextInt(3));
		Resident first = null;
		Resident second = null;
		for (int i = 0; i < group; i++) {
			Resident res;
			if (i < 2) {
				res = newResident(s, c, AgeStage.ADULT, random, null, null, day);
				res.bornDay = day - r.population.childhoodDays() - random.nextInt(Math.max(1, (int) (r.population.adulthoodDays() * 0.6)));
				if (i == 1 && first != null && first.female == res.female && random.nextBoolean()) {
					// often a couple
					res.female = !first.female;
				}
				if (i == 0) {
					first = res;
				} else {
					second = res;
				}
			} else {
				res = newResident(s, c, AgeStage.CHILD, random, first, second, day);
				res.bornDay = day - random.nextInt(Math.max(1, r.population.childhoodDays()));
			}
			s.addResident(res);
			// settlers bring a few days' food of their own
			s.stockpile.addQuiet(Goods.FOOD.ordinal(), (int) (r.foodAdult * 10));
		}
	}

	/** Births slow past the carrying capacity (to a quarter) and in decline (to half). */
	static double growthFactor(SimSettlement s, SimRules r) {
		var cfg = r.resources.prosperity();
		if (!cfg.enabled()) {
			return 1;
		}
		double f = 1;
		if (cfg.capacityLimits() && s.population() >= s.land().capacity()) {
			f *= 0.25;
		}
		if (s.land().mood() < 0) {
			f *= 0.5;
		}
		return f;
	}

	/** Settlers come to a booming town, stay away from a failing one, and not at all past what the land can feed. */
	static double settlerFactor(SimSettlement s, SimRules r) {
		var cfg = r.resources.prosperity();
		if (!cfg.enabled()) {
			return 1;
		}
		if (cfg.capacityLimits() && s.population() >= s.land().capacity()) {
			return 0;
		}
		return s.land().mood() < 0 ? 0.2 : s.land().mood() > 0 ? 1.4 : 1;
	}

	/** A new person from the resident factory, with the sim's own fields (age, birth day) set. */
	public static Resident newResident(SimSettlement s, SimContext c, AgeStage age, RandomSource random, @Nullable Resident a, @Nullable Resident b,
		long day) {
		Resident res = c.residents().create(s, age, random, a, b);
		res.age = age;
		res.bornDay = day;
		res.job = JobType.NONE;
		res.work = Resident.NO_PLOT;
		res.home = Resident.NO_PLOT;
		if (a != null || b != null) {
			var sim = res.extra("sim");
			if (a != null) {
				sim.putString("parent_a", a.id.toString());
			}
			if (b != null) {
				sim.putString("parent_b", b.id.toString());
			}
		}
		return res;
	}

	/**
	 * Gives the homeless beds in standing houses with room, and turns out anyone whose house is gone. Housing follows the
	 * planner's class-aware homes (requests/p3-plan.md §5): a family a home was built for (its {@code household.head})
	 * moves in first; then families into free homes of their station ({@code station} param); then anyone into any free
	 * bed. A household living in another station's home moves into an empty home of its own station when one stands.
	 */
	static void assignHomes(SimSettlement s) {
		it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap used = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
		for (Resident res : s.residents.values()) {
			if (res.home != Resident.NO_PLOT) {
				SimPlot home = s.plots.get(res.home);
				if (home == null || !home.isStanding() || used.get(res.home) >= home.beds) {
					res.home = Resident.NO_PLOT;
				} else {
					used.addTo(res.home, 1);
				}
			}
		}
		List<SimPlot> homes = new ArrayList<>();
		for (SimPlot p : s.plots.values()) {
			if (p.isStanding() && p.beds > 0) {
				homes.add(p);
			}
		}
		if (homes.isEmpty()) {
			return;
		}
		// families: the homeless by family name (a head's spouse and children share it)
		Map<String, List<Resident>> homeless = new LinkedHashMap<>();
		for (Resident res : s.residents.values()) {
			if (res.home == Resident.NO_PLOT) {
				homeless.computeIfAbsent(res.familyName, k -> new ArrayList<>()).add(res);
			}
		}
		if (!homeless.isEmpty()) {
			// 1. the family a home was built for
			for (SimPlot p : homes) {
				String head = headOf(p);
				if (head == null) {
					continue;
				}
				for (List<Resident> fam : homeless.values()) {
					if (!fam.isEmpty() && containsId(fam, head)) {
						moveIn(fam, p, used);
						break;
					}
				}
			}
			// 2. families into homes of their station, 3. anyone anywhere
			for (int pass = 0; pass < 2; pass++) {
				for (List<Resident> fam : homeless.values()) {
					for (java.util.Iterator<Resident> it = fam.iterator(); it.hasNext(); ) {
						Resident res = it.next();
						SimPlot best = null;
						for (SimPlot p : homes) {
							if (used.get(p.id()) >= p.beds) {
								continue;
							}
							if (pass == 0 && !res.station.getSerializedName().equals(stationOf(p))) {
								continue;
							}
							best = p;
							break;
						}
						if (best != null) {
							res.home = best.id();
							used.addTo(best.id(), 1);
							it.remove();
						}
					}
				}
			}
		}
		// a household in another station's home moves to an empty one of its own (a few a day: folk move slowly)
		Map<Integer, List<Resident>> households = new LinkedHashMap<>();
		for (Resident res : s.residents.values()) {
			if (res.home != Resident.NO_PLOT && res.age != AgeStage.CHILD) {
				households.computeIfAbsent(res.home, k -> new ArrayList<>()).add(res);
			}
		}
		int moves = 0;
		for (Map.Entry<Integer, List<Resident>> e : households.entrySet()) {
			if (moves >= 2) {
				break;
			}
			SimPlot home = s.plots.get(e.getKey());
			String st = home == null ? null : stationOf(home);
			Station theirs = e.getValue().getFirst().station;
			if (st == null || st.equals(theirs.getSerializedName())) {
				continue;
			}
			int size = used.get(home.id());
			for (SimPlot p : homes) {
				if (p != home && used.get(p.id()) == 0 && p.beds >= size && theirs.getSerializedName().equals(stationOf(p))) {
					for (Resident res : s.residents.values()) {
						if (res.home == home.id()) {
							res.home = p.id();
						}
					}
					used.put(p.id(), size);
					used.put(home.id(), 0);
					moves++;
					break;
				}
			}
		}
	}

	private static @Nullable String stationOf(SimPlot p) {
		return p.spec().hasParam("station") ? p.spec().paramString("station", "") : null;
	}

	private static @Nullable String headOf(SimPlot p) {
		String head = p.spec().params().getCompoundOrEmpty("household").getStringOr("head", "");
		return head.isEmpty() ? null : head;
	}

	private static boolean containsId(List<Resident> fam, String id) {
		for (Resident r : fam) {
			if (r.id.toString().equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static void moveIn(List<Resident> fam, SimPlot p, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap used) {
		for (java.util.Iterator<Resident> it = fam.iterator(); it.hasNext(); ) {
			Resident res = it.next();
			if (used.get(p.id()) >= p.beds) {
				return;
			}
			res.home = p.id();
			used.addTo(p.id(), 1);
			it.remove();
		}
	}

	/** Builders first (a settlement that cannot build cannot grow), then every workplace's trade; the rest gather. */
	static void assignJobs(SimSettlement s, SimRules r) {
		int jobs = JobType.values().length;
		int[] slots = new int[jobs];
		for (JobType j : JobType.values()) {
			slots[j.ordinal()] = s.jobSlots(j);
		}
		slots[JobType.BUILDER.ordinal()] += r.labour.baseBuilders() + r.labour.buildersPerTen() * s.population() / 10;
		int adults = 0;
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT) {
				adults++;
			}
		}
		double edible = Production.edible(s, s.ctx().data());
		boolean starving = edible < s.population() * r.foodAdult;
		// never so many builders that nobody feeds them: two in five at most, one while food is short
		slots[JobType.BUILDER.ordinal()] = Math.min(slots[JobType.BUILDER.ordinal()], starving ? 1 : Math.max(1, adults * 2 / 5));
		int[] filled = new int[jobs];
		it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap usedAt = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
		List<Resident> idle = new ArrayList<>();
		dev.voidmc.hearth.api.EconomyService eco = dev.voidmc.hearth.api.HearthServices.economy();
		for (Resident res : s.residents.values()) {
			if (res.age != AgeStage.ADULT) {
				res.job = JobType.NONE;
				res.work = Resident.NO_PLOT;
				continue;
			}
			int j = res.job.ordinal();
			if (eco != null && res.job != JobType.NONE && res.work != Resident.NO_PLOT && eco.businessOwner(s, res.work) != null) {
				// hired into a player's business (requests/p3-economy.md §6): theirs to keep while it stands
				SimPlot w = s.plots.get(res.work);
				if (w != null && w.isStanding()) {
					filled[j]++;
					usedAt.addTo(res.work * jobs + j, 1);
					continue;
				}
			}
			boolean keep = res.job != JobType.NONE && filled[j] < slots[j];
			if (keep && res.work != Resident.NO_PLOT) {
				SimPlot w = s.plots.get(res.work);
				keep = w != null && w.employs() && usedAt.get(res.work * jobs + j) < w.jobSlots(res.job);
			}
			if (keep) {
				filled[j]++;
				if (res.work != Resident.NO_PLOT) {
					usedAt.addTo(res.work * jobs + j, 1);
				}
			} else {
				res.job = JobType.NONE;
				res.work = Resident.NO_PLOT;
				idle.add(res);
			}
		}
		if (idle.isEmpty()) {
			return;
		}
		// order of preference: builders, food when short, then the rest as the workplaces come
		List<JobType> order = new ArrayList<>();
		order.add(JobType.BUILDER);
		boolean hungry = edible < s.population() * r.foodAdult * 3;
		if (hungry) {
			order.add(JobType.FARMER);
			order.add(JobType.FISHER);
			order.add(JobType.HUNTER);
		}
		// with a month's food in store, the food trades take what workers are left after the other workplaces
		boolean plenty = edible > s.population() * r.foodAdult * 30;
		for (JobType j : JobType.values()) {
			if (j != JobType.NONE && !order.contains(j) && !(plenty && foodTrade(j))) {
				order.add(j);
			}
		}
		for (JobType j : JobType.values()) {
			if (!order.contains(j) && j != JobType.NONE) {
				order.add(j);
			}
		}
		int next = 0;
		for (JobType job : order) {
			int j = job.ordinal();
			while (filled[j] < slots[j] && next < idle.size()) {
				Resident res = idle.get(next++);
				res.job = job;
				res.work = workplaceFor(s, job, usedAt, jobs);
				if (res.work != Resident.NO_PLOT) {
					usedAt.addTo(res.work * jobs + j, 1);
				}
				filled[j]++;
			}
		}
	}

	private static boolean foodTrade(JobType j) {
		return j == JobType.FARMER || j == JobType.FISHER || j == JobType.HUNTER || j == JobType.HERDER;
	}

	/** A standing plot with a free job site for this job, or none (builders without a yard work from home). */
	private static int workplaceFor(SimSettlement s, JobType job, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap usedAt, int jobs) {
		for (SimPlot p : s.plots.values()) {
			if (p.employs() && usedAt.get(p.id() * jobs + job.ordinal()) < p.jobSlots(job)) {
				return p.id();
			}
		}
		return Resident.NO_PLOT;
	}

	/**
	 * Lays a new settlement out with the planner and moves its founders in: adults from the resident factory, the goods
	 * and coin they bring. Nothing fires here; the caller fires {@code FOUNDED} once the record is registered.
	 */
	public static void found(SimSettlement s, SimContext c, int founders, java.util.Map<Goods, Integer> goods, long treasury) {
		found(s, c, founders, List.of(), goods, treasury);
	}

	/**
	 * As above, with {@code settlers} (people who left another settlement to found this one) among the founders; the rest
	 * of the {@code founders} are newcomers from the resident factory.
	 */
	public static void found(SimSettlement s, SimContext c, int founders, List<Resident> settlers, java.util.Map<Goods, Integer> goods,
		long treasury) {
		SettlementPlanner planner = c.planner();
		RandomSource random = s.random();
		if (planner != null) {
			List<PlotSpec> plots = planner.found(new SettlementPlanner.FoundingContext(s, s.center(), c.terrain(), random));
			for (PlotSpec spec : plots) {
				if (c.allows(s, spec)) {
					s.addPlot(spec, -1, SimSettlement.FOUNDING);
				}
			}
		}
		long day = Math.floorDiv(s.clock.simTick, SimRules.DAY);
		Resident last = null;
		for (Resident res : settlers) {
			res.home = Resident.NO_PLOT;
			res.work = Resident.NO_PLOT;
			res.job = JobType.NONE;
			s.addResident(res);
			last = res;
		}
		for (int i = settlers.size(); i < founders; i++) {
			Resident res = newResident(s, c, AgeStage.ADULT, random, null, null, day);
			res.bornDay = day - c.rules().population.childhoodDays() - random.nextInt(Math.max(1, c.rules().population.adulthoodDays() / 2));
			if (last != null && i % 2 == 1) {
				res.female = !last.female;
			}
			last = res;
			s.addResident(res);
		}
		s.recomputeDerived();
		goods.forEach((g, n) -> s.stockpile.addQuiet(g.ordinal(), n));
		s.treasury += treasury;
		// the land is surveyed as the founders arrive (a history at once; the live world off the server thread)
		c.survey(s);
		assignJobs(s, c.rules());
		s.markDirty();
	}

	/** Head counts the clock integrates with, refreshed once per sim day (and whenever a step begins). */
	static final class Workforce {
		final int[] workers = new int[JobType.values().length];
		/** Builders who are entities just now: their placements arrive through {@code advanceConstruction}. */
		int embodiedBuilders;
		int adults;
		int children;
		int elders;

		/** Working adults by trade and workplace, with their recipes and sites resolved (docs/SIM.md §16). */
		final Production.Crews crews = new Production.Crews();

		static Workforce of(SimSettlement s, SimContext c) {
			Workforce w = new Workforce();
			var data = c.data();
			SimRules r = c.rules();
			for (Resident res : s.residents.values()) {
				switch (res.age) {
					case CHILD -> w.children++;
					case ADULT -> w.adults++;
					case ELDER -> w.elders++;
				}
				// embodied folk do their work as entities, not as numbers
				if (res.age == AgeStage.ADULT && !s.isResidentEmbodied(res.id)) {
					w.workers[res.job.ordinal()]++;
					// (the crew is found first: finding a new one may grow the counts array)
					int crew = Production.crewOf(w.crews, s, res.job, res.work, data, r);
					w.crews.counts[crew]++;
				} else if (res.age == AgeStage.ADULT && res.job == JobType.BUILDER) {
					w.embodiedBuilders++;
				}
			}
			return w;
		}
	}
}
