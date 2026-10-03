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
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
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
		Workforce wf = Workforce.of(s);
		while (k.simTick < end) {
			long dayEnd = (Math.floorDiv(k.simTick, SimRules.DAY) + 1) * SimRules.DAY;
			long sliceEnd = Math.min(end, dayEnd);
			double days = (sliceEnd - k.simTick) / (double) SimRules.DAY;
			produce(s, wf, days, r);
			construct(s, wf, days, r, c);
			k.simTick = sliceEnd;
			if (sliceEnd == dayEnd) {
				daily(s, r, c, Math.floorDiv(dayEnd, SimRules.DAY));
				wf = Workforce.of(s);
			}
			grow(s, wf, r, c);
		}
		s.markDirty();
		c.dirty(s);
		return end - start;
	}

	// ---------------------------------------------------------------- goods

	private static void produce(SimSettlement s, Workforce wf, double days, SimRules r) {
		SimSettlement.ClockState k = s.clock;
		double[] frac = k.fractions;
		SimStockpile stock = s.stockpile();
		double coin = 0;
		for (int j = 0; j < wf.workers.length; j++) {
			int n = wf.workers[j];
			if (n == 0) {
				continue;
			}
			double[] row = r.production[j];
			// inputs short? then the output shrinks with them
			double factor = 1;
			for (int g = 0; g < row.length; g++) {
				if (row[g] < 0) {
					double need = -row[g] * n * days;
					double have = stock.get(Goods.VALUES[g]) + Math.max(0, frac[g]);
					factor = Math.min(factor, need <= 0 ? 1 : have / need);
				}
			}
			factor = Math.max(0, factor);
			for (int g = 0; g < row.length; g++) {
				if (row[g] != 0) {
					frac[g] += row[g] * n * days * factor;
				}
			}
			coin += r.coin[j] * n * days;
		}
		frac[Goods.FOOD.ordinal()] -= (wf.adults * r.foodAdult + wf.children * r.foodChild + wf.elders * r.foodElder) * days;
		for (int g = 0; g < frac.length; g++) {
			if (frac[g] >= 1) {
				int whole = (int) Math.floor(frac[g]);
				int added = stock.addQuiet(g, whole);
				// what the stores cannot hold goes to passing traders, cheaply
				coin += (whole - added) * r.prices[g] * r.exportShare;
				frac[g] -= whole;
			} else if (frac[g] <= -1) {
				int whole = (int) Math.ceil(-frac[g]);
				int taken = stock.takeQuiet(g, whole);
				frac[g] += taken;
				if (taken < whole) {
					// nothing left to take: hunger (or idle workshops), not a debt carried forward
					frac[g] = Math.max(frac[g], -1);
				}
			}
		}
		k.coinFraction += coin;
		if (k.coinFraction >= 1) {
			long bits = (long) Math.floor(k.coinFraction);
			s.treasury += bits;
			k.coinFraction -= bits;
		}
	}

	// ---------------------------------------------------------------- construction

	/** Builders wanted per plot under way, so labour spreads over a few plots rather than one at a time. */
	static int maxConcurrent(int builders, SimRules r) {
		return Math.max(1, (builders + r.labour.builderShare() - 1) / r.labour.builderShare());
	}

	private static void construct(SimSettlement s, Workforce wf, double days, SimRules r, SimContext c) {
		int builders = wf.workers[JobType.BUILDER.ordinal()];
		if (builders == 0) {
			return;
		}
		double pool = builders * r.labour.workPerBuilderHour() * r.labour.builderHoursPerDay() * days;
		int concurrent = maxConcurrent(builders, r);
		long now = c.gameTime();
		for (int round = 0; round < 8 && pool > 0.5; round++) {
			List<SimPlot> working = new ArrayList<>(concurrent);
			for (SimPlot p : s.plots.values()) {
				if (working.size() >= concurrent) {
					break;
				}
				if (!p.isActive()) {
					continue;
				}
				if (s.embodied() && p.lastEmbodiedAdvance != Long.MIN_VALUE && now - p.lastEmbodiedAdvance < EMBODIED_HOLD) {
					// an embodied builder is on it: abstract labour goes elsewhere
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
				return;
			}
			double share = pool / working.size();
			double left = 0;
			for (SimPlot p : working) {
				left += labour(s, p, share, c);
			}
			if (left >= pool - 1e-6) {
				return;
			}
			pool = left;
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
		int builders = wf.workers[JobType.BUILDER.ordinal()];
		if (planner == null || builders == 0 || k.simTick < k.noGrowthUntil) {
			return;
		}
		int active = 0;
		int stalled = 0;
		for (SimPlot p : s.plots.values()) {
			if (p.isActive()) {
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
		// keep every pair of builders busy, a few proposals at a time at most
		for (int tries = 0; tries < 4 && active < maxConcurrent(builders, r); tries++) {
			if (!c.tryPlannerCall()) {
				return;
			}
			SettlementPlanner.GrowthProject project;
			try {
				project = planner.next(s, c.terrain(), s.random());
			} catch (RuntimeException e) {
				org.slf4j.LoggerFactory.getLogger("hearth").warn("planner failed for settlement {}", s.id(), e);
				project = null;
			}
			if (project == null || !c.allows(s, project.spec())) {
				k.noGrowthUntil = k.simTick + SimRules.DAY / 2;
				return;
			}
			s.addPlot(project.spec(), project.replaces(), project.reason());
			active++;
		}
	}

	// ---------------------------------------------------------------- the day's business

	private static void daily(SimSettlement s, SimRules r, SimContext c, long day) {
		SimSettlement.ClockState k = s.clock;
		RandomSource random = s.random();
		k.daysLived++;
		age(s, r, day);
		k.hungerDays = s.population() > 0 && s.stockpile.get(Goods.FOOD) <= 0 ? k.hungerDays + 1 : 0;
		deaths(s, r, random);
		int housing = s.beds() + r.population.tentSlots();
		k.overcrowdDays = s.population() > housing ? k.overcrowdDays + 1 : 0;
		if ((k.overcrowdDays > r.population.overcrowdDays() || k.hungerDays > r.population.hungerDays()) && random.nextFloat() < 0.5F) {
			emigrate(s, random);
		}
		imports(s, r);
		if (k.hungerDays == 0) {
			births(s, r, c, random, day);
			settlers(s, r, c, random, day);
		}
		assignHomes(s);
		assignJobs(s, r);
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
	 * A stage waiting for goods the town cannot make buys them from passing traders, a cartload a day at a markup: with
	 * coin while the treasury allows, then in barter for whatever overflows its stores. Slow and dear on purpose: a player
	 * bringing the goods (a supply quest) is faster.
	 */
	private static void imports(SimSettlement s, SimRules r) {
		for (SimPlot p : s.plots.values()) {
			if (!p.isActive() || p.stalledOn == null) {
				continue;
			}
			PlotFacts f = s.facts(p);
			if (p.stage >= f.stageCount()) {
				continue;
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
				// then barter what overflows the stores, at a loss
				for (int o = 0; o < need.length && bought < missing; o++) {
					int spare = s.stockpile.get(Goods.VALUES[o]) - Math.max(need[o], s.stockpile.capacity(Goods.FOOD) / 2);
					if (o == g || spare <= 0 || r.prices[o] <= 0) {
						continue;
					}
					double worth = r.prices[o] * BARTER;
					int units = (int) Math.min(missing - bought, Math.floor(spare * worth / unit));
					if (units > 0) {
						s.stockpile.takeQuiet(o, (int) Math.ceil(units * unit / worth));
						bought += units;
					}
				}
				if (bought > 0) {
					s.stockpile.addQuiet(g, bought);
				}
			}
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
		List<Resident> mothers = new ArrayList<>();
		List<Resident> fathers = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT) {
				(res.female ? mothers : fathers).add(res);
			}
		}
		int couples = Math.min(mothers.size(), fathers.size());
		for (int i = 0; i < couples && free > 0; i++) {
			if (random.nextDouble() >= r.population.birthPerCoupleDay()) {
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
		if (free <= 0 || random.nextDouble() >= r.population.settlerPerDay()) {
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

	/** Gives the homeless beds in standing houses with room, and turns out anyone whose house is gone. */
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
		var it = s.plots.values().iterator();
		SimPlot house = null;
		for (Resident res : s.residents.values()) {
			if (res.home != Resident.NO_PLOT) {
				continue;
			}
			while (house == null || used.get(house.id()) >= house.beds) {
				if (!it.hasNext()) {
					return;
				}
				SimPlot p = it.next();
				house = p.isStanding() && p.beds > 0 ? p : null;
			}
			res.home = house.id();
			used.addTo(house.id(), 1);
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
		boolean starving = s.stockpile.get(Goods.FOOD) < s.population() * r.foodAdult;
		// never so many builders that nobody feeds them: two in five at most, one while food is short
		slots[JobType.BUILDER.ordinal()] = Math.min(slots[JobType.BUILDER.ordinal()], starving ? 1 : Math.max(1, adults * 2 / 5));
		int[] filled = new int[jobs];
		it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap usedAt = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
		List<Resident> idle = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res.age != AgeStage.ADULT) {
				res.job = JobType.NONE;
				res.work = Resident.NO_PLOT;
				continue;
			}
			int j = res.job.ordinal();
			boolean keep = res.job != JobType.NONE && filled[j] < slots[j];
			if (keep && res.work != Resident.NO_PLOT) {
				SimPlot w = s.plots.get(res.work);
				keep = w != null && w.isStanding() && usedAt.get(res.work * jobs + j) < w.jobSlots(res.job);
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
		boolean hungry = s.stockpile.get(Goods.FOOD) < s.population() * r.foodAdult * 3;
		if (hungry) {
			order.add(JobType.FARMER);
			order.add(JobType.FISHER);
			order.add(JobType.HUNTER);
		}
		for (JobType j : JobType.values()) {
			if (j != JobType.NONE && !order.contains(j)) {
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

	/** A standing plot with a free job site for this job, or none (builders without a yard work from home). */
	private static int workplaceFor(SimSettlement s, JobType job, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap usedAt, int jobs) {
		for (SimPlot p : s.plots.values()) {
			if (p.isStanding() && usedAt.get(p.id() * jobs + job.ordinal()) < p.jobSlots(job)) {
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
		SettlementPlanner planner = c.planner();
		RandomSource random = s.random();
		if (planner != null) {
			List<PlotSpec> plots = planner.found(new SettlementPlanner.FoundingContext(s, s.center(), c.terrain(), random));
			for (PlotSpec spec : plots) {
				if (c.allows(s, spec)) {
					s.addPlot(spec, -1, "founding");
				}
			}
		}
		long day = Math.floorDiv(s.clock.simTick, SimRules.DAY);
		Resident last = null;
		for (int i = 0; i < founders; i++) {
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
		assignJobs(s, c.rules());
		s.markDirty();
	}

	/** Head counts the clock integrates with, refreshed once per sim day (and whenever a step begins). */
	static final class Workforce {
		final int[] workers = new int[JobType.values().length];
		int adults;
		int children;
		int elders;

		static Workforce of(SimSettlement s) {
			Workforce w = new Workforce();
			for (Resident res : s.residents.values()) {
				switch (res.age) {
					case CHILD -> w.children++;
					case ADULT -> w.adults++;
					case ELDER -> w.elders++;
				}
				// embodied folk do their work as entities, not as numbers
				if (res.age == AgeStage.ADULT && !s.isResidentEmbodied(res.id)) {
					w.workers[res.job.ordinal()]++;
				}
			}
			return w;
		}
	}
}
