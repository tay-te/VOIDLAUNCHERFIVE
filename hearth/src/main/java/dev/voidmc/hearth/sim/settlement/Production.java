package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.chains.BiomeTable;
import dev.voidmc.hearth.sim.chains.Recipe;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.land.LandState;
import dev.voidmc.hearth.sim.land.Lands;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The goods side of a step (docs/LIVING_WORLD.md §7.4–7.5): gatherers yield from their territory's sites, workshops turn
 * raw goods into processed and finished ones by the data's recipes, quarriers and miners advance their excavations,
 * tools wear out, and folk eat — bread when there is bread, what the land gives when there is not. Embodied workers'
 * days come back through {@link #credit} and go through the very same arithmetic, so a town a player stands in produces
 * what one nobody is near would.
 *
 * <p>Costs a pass over the settlement's crews (a few dozen at most: one per trade and workplace) and their recipes and
 * sites per slice; no allocation proportional to the population.
 */
public final class Production {
	/** Ordinals eaten as they are when there is no bread, most perishable first. */
	private static final int[] EAT_ORDER = {Goods.FISH.ordinal(), Goods.MEAT.ordinal(), Goods.PRODUCE.ordinal(), Goods.FLOUR.ordinal(),
		Goods.GRAIN.ordinal()};

	private Production() {
	}

	/**
	 * Workers of one trade at one kind of workplace: they follow the same recipes and gather from the same sites.
	 *
	 * @param workplace the archetype of their workplace, or null (no workplace: builders without a yard, odd-job labourers)
	 */
	record Crew(JobType job, @Nullable Identifier workplace, List<Recipe> recipes, SimData.@Nullable Gathering gathering, double toolWear,
		SimData.@Nullable Extraction digs) {
	}

	/** The crews of a settlement's working adults (not embodied), with their head counts. */
	static final class Crews {
		final List<Crew> crews = new ArrayList<>();
		int[] counts = new int[8];
		final Map<Long, Integer> index = new HashMap<>();

		void add(Crew c) {
			if (this.crews.size() == this.counts.length) {
				this.counts = java.util.Arrays.copyOf(this.counts, this.counts.length * 2);
			}
			this.crews.add(c);
		}
	}

	/** Builds the crew a resident works in (recipes and gathering resolved once per crew, not per person). */
	static int crewOf(Crews crews, SimSettlement s, JobType job, int work, SimData data, SimRules r) {
		Identifier workplace = null;
		if (work != dev.voidmc.hearth.api.Resident.NO_PLOT) {
			SimPlot p = s.plots.get(work);
			if (p != null) {
				workplace = p.spec().archetype();
			}
		}
		long key = (long) job.ordinal() << 32 | (workplace == null ? 0 : workplace.hashCode() & 0xFFFF_FFFFL);
		Integer at = crews.index.get(key);
		if (at != null && java.util.Objects.equals(crews.crews.get(at).workplace(), workplace)) {
			return at;
		}
		SimData.Gathering g = data.gathering(job);
		double wear = g != null && g.tools() >= 0 ? g.tools() : job == JobType.NONE ? 0 : r.resources.chains().toolWear();
		SimData.Extraction digs = null;
		for (SimData.Extraction e : data.model().extraction().values()) {
			if (e.job() == job) {
				digs = e;
				break;
			}
		}
		crews.add(new Crew(job, workplace, data.recipesFor(job, workplace), g, wear, digs));
		int idx = crews.crews.size() - 1;
		crews.index.put(key, idx);
		return idx;
	}

	/**
	 * What construction is about to need: the goods of every plot's stage that has not started yet (capped at the store
	 * room). Workshops do not turn these into something else — the mason leaves the rough stone the next foundation needs.
	 */
	static int[] reserved(SimSettlement s) {
		int n = Goods.VALUES.length;
		int[] keep = new int[n];
		for (SimPlot p : s.plots.values()) {
			if (!p.isActive() || p.funded || p.facts == null || p.stage >= p.facts.stageCount()) {
				continue;
			}
			int[] need = p.facts.goods()[p.stage];
			for (int g = 0; g < n && g < need.length; g++) {
				keep[g] += need[g];
			}
		}
		return keep;
	}

	/** One slice of production and eating. */
	/** The share of the trades' income a town keeps when the economy module pays wages from it. */
	static final double ECONOMY_REVENUE = 1.0;

	static void produce(SimSettlement s, SettlementClock.Workforce wf, double days, SimRules r, SimContext c) {
		SimData data = c.data();
		double[] frac = s.clock.fractions;
		SimStockpile stock = s.stockpile();
		int[] reserved = reserved(s);
		int[] amounts = stock.amounts();
		int[] caps = stock.capacities();
		SimConfig.Chains chains = r.resources.chains();
		boolean economyWages = c.economyHandlesWages();
		double coin = 0;

		// tools: every trade wears them; short of tools, everyone manages less
		double toolDemand = 0;
		int gatherers = 0;
		for (int k = 0; k < wf.crews.crews.size(); k++) {
			int n = wf.crews.counts[k];
			Crew crew = wf.crews.crews.get(k);
			toolDemand += n * days * crew.toolWear();
			if (crew.gathering() != null && crew.job() != JobType.NONE) {
				gatherers += n;
			}
		}
		double toolHave = amounts[Goods.TOOLS.ordinal()] + Math.max(0, frac[Goods.TOOLS.ordinal()]);
		double toolShare = toolDemand <= 0 ? 1 : Math.min(1, toolHave / toolDemand);
		frac[Goods.TOOLS.ordinal()] -= toolDemand * toolShare;
		double toolFactor = chains.noToolsFactor() + (1 - chains.noToolsFactor()) * toolShare;
		int haulers = wf.workers[JobType.HAULER.ordinal()];
		double haulCover = gatherers == 0 ? 1 : Math.min(1, haulers * chains.haulPerHauler() / (double) gatherers);

		for (int k = 0; k < wf.crews.crews.size(); k++) {
			int n = wf.crews.counts[k];
			if (n == 0) {
				continue;
			}
			Crew crew = wf.crews.crews.get(k);
			double workerDays = n * days * (crew.toolWear() > 0 ? toolFactor : 1);
			work(s, crew, workerDays, frac, amounts, caps, data, r, c, haulCover, true, reserved);
			// the town's own income from its trades. With the economy module it is what the town pays its wages out of
			// (into the residents' purses): without it the treasuries ran dry, wages stopped, every purse emptied and every
			// household fell to poor by the purse rule (integration 3)
			coin += r.coin[crew.job().ordinal()] * n * days * (economyWages ? ECONOMY_REVENUE : 1);
		}
		eat(s, wf, days, r, data, frac, amounts, reserved);
		settle(s, r, frac, coin);
	}

	/**
	 * A crew's {@code workerDays}: recipes first (as far as their inputs and the stores' need for their outputs allow),
	 * then the trade's excavations, then gathering from the sites; a trade the data does not cover uses the config's
	 * per-day row.
	 *
	 * @param owe whether logs taken from natural woods are owed to the world (not for an embodied forester's credited
	 *            days: the entity felled real trees)
	 */
	static void work(SimSettlement s, Crew crew, double workerDays, double[] frac, int[] amounts, int[] caps, SimData data, SimRules r,
		SimContext c, double haulCover, boolean owe, int[] reserved) {
		JobType job = crew.job();
		double left = workerDays;
		if (job == JobType.BUILDER) {
			// a builder's own hewing and dressing, between placing blocks (their labour goes to the plots)
			left = workerDays * r.resources.chains().builderShare();
		}
		labour(s, crew, left, frac, amounts, caps, data, r, c, haulCover, owe, reserved);
	}

	/** {@code left} worker-days of a crew's trade: recipes, then excavations, then gathering. */
	private static void labour(SimSettlement s, Crew crew, double left, double[] frac, int[] amounts, int[] caps, SimData data, SimRules r,
		SimContext c, double haulCover, boolean owe, int[] reserved) {
		JobType job = crew.job();
		double total = left;
		if (!crew.recipes().isEmpty()) {
			left = recipes(crew.recipes(), left, total, frac, amounts, caps, crew.toolWear(), reserved);
		}
		if (crew.digs() != null && left > 1e-6) {
			left = Extraction.dig(s, crew.digs(), left, r, c);
		}
		if (crew.gathering() != null) {
			if (left > 1e-6) {
				gather(s, crew.gathering(), left, frac, r, c, haulCover, owe);
			}
		} else if (!data.covers(job)) {
			// what the stages waiting for goods want is kept back here too, as for the recipes: the mint struck the metal a
			// waiting house was bought in for into coin overnight, every night, and the house never started (a town caught
			// up a day at a time sat at 70 souls for good, its treasury in the thousands)
			double[] row = r.production[job.ordinal()];
			double factor = 1;
			for (int g = 0; g < row.length; g++) {
				if (row[g] < 0) {
					double need = -row[g] * left;
					double have = Math.max(0, amounts[g] + Math.max(0, frac[g]) - reserved[g]);
					factor = Math.min(factor, need <= 0 ? 1 : have / need);
				}
			}
			factor = Math.max(0, factor);
			for (int g = 0; g < row.length; g++) {
				if (row[g] != 0) {
					frac[g] += row[g] * left * factor;
				}
			}
		}
	}

	/**
	 * Builders with no stage they can work (every one waits for goods, or none is planned) fetch and dress materials for
	 * the rest of their day instead of standing about (requests/w5-tour.md §4): they hew the logs in store into timber
	 * and dress stone, then cut wood, break stone and dig clay and sand on the town's own sites (the builders'
	 * {@code gathering/builder.json}), which lose what was taken. A young town's builders thus win the raw goods of its
	 * next stages from the land round it, and what it gathers beyond them is what it barters for the rest.
	 *
	 * @param builderDays the idle builders' time, in builder-days
	 */
	static void fetch(SimSettlement s, SettlementClock.Workforce wf, double builderDays, SimRules r, SimContext c) {
		if (builderDays <= 1e-6) {
			return;
		}
		SimData data = c.data();
		Crew crew = null;
		for (int k = 0; k < wf.crews.crews.size(); k++) {
			if (wf.crews.crews.get(k).job() == JobType.BUILDER && wf.crews.counts[k] > 0) {
				crew = wf.crews.crews.get(k);
				break;
			}
		}
		if (crew == null) {
			return;
		}
		double[] frac = s.clock.fractions;
		SimStockpile stock = s.stockpile();
		int gatherers = 0;
		for (int k = 0; k < wf.crews.crews.size(); k++) {
			if (wf.crews.crews.get(k).gathering() != null && wf.crews.crews.get(k).job() != JobType.NONE) {
				gatherers += wf.crews.counts[k];
			}
		}
		int haulers = wf.workers[JobType.HAULER.ordinal()];
		double haulCover = gatherers == 0 ? 1 : Math.min(1, haulers * r.resources.chains().haulPerHauler() / (double) gatherers);
		labour(s, crew, builderDays, frac, stock.amounts(), stock.capacities(), data, r, c, haulCover, true, reserved(s));
		settle(s, r, frac, 0);
	}

	/** Runs a crew's recipes, the most wanted first; returns the worker-days left over. */
	static double recipes(List<Recipe> list, double left, double total, double[] frac, int[] amounts, int[] caps) {
		return recipes(list, left, total, frac, amounts, caps, 0, new int[amounts.length]);
	}

	/**
	 * As above; {@code crewWear} is the tools a worker-day of this crew was already charged, which a recipe with its own
	 * rate ({@code tools}, per worker-day) corrects for the time spent on it (hand work wears no tools).
	 */
	static double recipes(List<Recipe> list, double left, double total, double[] frac, int[] amounts, int[] caps, double crewWear,
		int[] reserved) {
		Recipe[] order = list.toArray(Recipe[]::new);
		if (order.length > 1) {
			// the recipe whose outputs are scarcest first; priority breaks ties
			java.util.Arrays.sort(order, (a, b) -> {
				int cmp = Double.compare(fill(a, amounts, frac, caps), fill(b, amounts, frac, caps));
				return cmp != 0 ? cmp : Integer.compare(b.priority(), a.priority());
			});
		}
		for (Recipe rec : order) {
			if (left <= 1e-9) {
				break;
			}
			if (satisfied(rec, amounts, frac, caps)) {
				continue;
			}
			double labour = Math.min(left, total * rec.share());
			double batches = labour / rec.labour();
			double[] in = rec.inputs();
			double factor = 1;
			for (int g = 0; g < in.length; g++) {
				if (in[g] > 0) {
					double have = amounts[g] + frac[g] - reserved[g];
					factor = Math.min(factor, Math.max(0, have) / (in[g] * batches));
				}
			}
			if (factor <= 0) {
				continue;
			}
			batches *= Math.min(1, factor);
			double[] out = rec.outputs();
			for (int g = 0; g < in.length; g++) {
				frac[g] += out[g] * batches - in[g] * batches;
			}
			double used = batches * rec.labour();
			if (rec.tools() >= 0) {
				frac[Goods.TOOLS.ordinal()] -= (rec.tools() - crewWear) * used;
			}
			left -= used;
		}
		return Math.max(0, left);
	}

	private static double fill(Recipe rec, int[] amounts, double[] frac, int[] caps) {
		double f = 1;
		double[] out = rec.outputs();
		for (int g = 0; g < out.length; g++) {
			if (out[g] > 0 && caps[g] > 0) {
				f = Math.min(f, (amounts[g] + frac[g]) / caps[g]);
			}
		}
		return f;
	}

	private static boolean satisfied(Recipe rec, int[] amounts, double[] frac, int[] caps) {
		double[] out = rec.outputs();
		for (int g = 0; g < out.length; g++) {
			if (out[g] > 0 && amounts[g] + frac[g] < caps[g] * rec.target()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Gathering: the day is shared over the kinds of site the trade works (or, for odd-job labourers, every kind gives at
	 * once), and each kind's share over its richest, nearest sites. Yields scale with the site's density and how much is
	 * left; far sites yield less unless haulers carry for the gatherers.
	 */
	static void gather(SimSettlement s, SimData.Gathering g, double workerDays, double[] frac, SimRules r, SimContext c, double haulCover,
		boolean owe) {
		LandState land = s.land();
		SimData data = c.data();
		List<SimSite> sites = land.sites();
		SimConfig.Chains chains = r.resources.chains();
		double shares = 0;
		boolean[] present = new boolean[g.sites().size()];
		for (int e = 0; e < g.sites().size(); e++) {
			SimData.Gathering.Entry entry = g.sites().get(e);
			for (SimSite site : sites) {
				if (site.kind() == entry.kind() && (site.remaining() > 0 || site.renewable())) {
					present[e] = true;
					break;
				}
			}
			if (present[e]) {
				shares += entry.share();
			}
		}
		// a land's rarities: the sites whose rarity this trade gathers
		List<SimSite> special = null;
		for (SimSite site : sites) {
			if (site.kind() == ResourceSite.Kind.SPECIALTY && site.remaining() > 0) {
				BiomeTable.Specialty sp = data.specialty(site.material());
				if (sp != null && sp.job() == g.job()) {
					if (special == null) {
						special = new ArrayList<>(2);
					}
					special.add(site);
				}
			}
		}
		double specialShare = special == null ? 0 : 0.25 * Math.max(1, shares);
		if (!g.split()) {
			// an odd-job labourer's day: every kind of land gives at once, and where the territory has none of a kind,
			// a little of it is scraped together anyway (deadwood, a few stones, berries by the path)
			double k = r.resources.chains().noSiteFactor() * SCROUNGE;
			for (int e = 0; e < g.sites().size(); e++) {
				SimData.Gathering.Entry entry = g.sites().get(e);
				if (present[e]) {
					work(s, entry.kind(), null, entry.yields(), entry.wearPerDay(), workerDays, frac, r, c, haulCover, sites, owe);
				} else {
					for (int i = 0; i < frac.length; i++) {
						frac[i] += entry.yields()[i] * workerDays * k;
					}
				}
			}
			if (special != null) {
				for (SimSite site : special) {
					work(s, site.kind(), site, site.yieldsView(), -1, workerDays * 0.25 / special.size(), frac, r, c, haulCover, sites, owe);
				}
			}
			return;
		}
		if (shares <= 0 && special == null) {
			double k = chains.noSiteFactor();
			for (int i = 0; i < frac.length; i++) {
				frac[i] += g.noSite()[i] * workerDays * k;
			}
			return;
		}
		double whole = shares + specialShare;
		for (int e = 0; e < g.sites().size(); e++) {
			if (!present[e]) {
				continue;
			}
			SimData.Gathering.Entry entry = g.sites().get(e);
			double days = workerDays * entry.share() / whole;
			work(s, entry.kind(), null, entry.yields(), entry.wearPerDay(), days, frac, r, c, haulCover, sites, owe);
		}
		if (special != null) {
			double days = workerDays * specialShare / whole;
			double each = days / special.size();
			for (SimSite site : special) {
				work(s, site.kind(), site, site.yieldsView(), -1, each, frac, r, c, haulCover, sites, owe);
			}
		}
	}

	/**
	 * Puts {@code days} worker-days into the sites of a kind (or one site): the best three by richness and nearness share
	 * them, so a town thins its woods rather than clear-cutting the nearest one.
	 */
	private static void work(SimSettlement s, ResourceSite.Kind kind, @Nullable SimSite only, double[] yields, double wearPerDay, double days,
		double[] frac, SimRules r, SimContext c, double haulCover, List<SimSite> sites, boolean owe) {
		SimSite[] best = new SimSite[3];
		double[] weight = new double[3];
		if (only != null) {
			best[0] = only;
			weight[0] = 1;
		} else {
			for (SimSite site : sites) {
				if (site.kind() != kind || site.remaining() <= 0 && !site.renewable()) {
					continue;
				}
				double w = site.density() * yieldFactor(site) / (1 + site.distanceTo(s.center()) / Math.max(1, r.resources.chains().haulRange()));
				for (int i = 0; i < 3; i++) {
					if (best[i] == null || w > weight[i]) {
						for (int j = 2; j > i; j--) {
							best[j] = best[j - 1];
							weight[j] = weight[j - 1];
						}
						best[i] = site;
						weight[i] = w;
						break;
					}
				}
			}
		}
		double sum = weight[0] + weight[1] + weight[2];
		if (sum <= 0) {
			return;
		}
		double sumYield = 0;
		for (double y : yields) {
			sumYield += y;
		}
		double wear = wearPerDay >= 0 ? wearPerDay : sumYield;
		int range = r.resources.chains().haulRange();
		LandState land = s.land();
		for (int i = 0; i < 3; i++) {
			SimSite site = best[i];
			if (site == null) {
				continue;
			}
			double d = days * weight[i] / sum;
			double dist = site.distanceTo(s.center());
			double haul = dist <= range / 2.0 ? 1 : Math.max(0.5, 1 - (dist - range / 2.0) / range * (1 - haulCover));
			// a rarity's yields are already scaled to its density; a kind's are per full-density site
			double factor = (only != null ? 1 : densityFactor(site)) * yieldFactor(site) * haul;
			double logs = 0;
			for (int g = 0; g < yields.length; g++) {
				if (yields[g] != 0) {
					double y = yields[g] * d * factor;
					frac[g] += y;
					if (g == Goods.LOGS.ordinal()) {
						logs += y;
					}
				}
			}
			double used = wear * d * factor;
			Lands.take(s, site, used, logs, owe, c);
			site.worked(d);
		}
		land.touched();
	}

	/**
	 * How much of a full site's yield a worker-day gets here: the richness (falling slower than the stock), but land that
	 * regrows never gives nothing — worn-out fields and overgrazed pasture still yield a third, while the stock rests
	 * at nothing until the work eases. Stone and ore worked out give nothing.
	 */
	static double yieldFactor(SimSite site) {
		return site.renewable() ? Math.max(RENEWABLE_FLOOR, site.richness()) : site.richness();
	}

	/**
	 * How a site's density scales a worker-day: ore and stone by their grade outright; land that is worked (fields,
	 * pasture, woods, waters) much less — poor soil is mostly less of it (the site's capacity), and a farmer on it still
	 * brings in a harvest.
	 */
	static double densityFactor(SimSite site) {
		double d = Math.min(1.5, site.density());
		return site.renewable() ? 0.4 + 0.6 * Math.min(1, d) : d;
	}

	/** What an odd-job labourer scrapes together of a kind of land the territory has no site of, as a share of its yield. */
	static final double SCROUNGE = 0.4;

	/** What a worn-out renewable site still yields, as a share of a full one. */
	static final double RENEWABLE_FLOOR = 0.35;

	/**
	 * Folk eat: prepared food first; when there is none, what the land gives eaten as it is (fish, meat, fruit, flour,
	 * grain — worth less than bread). What a stage waiting for goods wants is eaten last, only when nothing else is left
	 * (a farmhouse's last stage waited half a year for the one basket of fruit its builders were bought in and that was
	 * eaten every night, with 17 000 grain in the granary).
	 */
	private static void eat(SimSettlement s, SettlementClock.Workforce wf, double days, SimRules r, SimData data, double[] frac, int[] amounts,
		int[] reserved) {
		int food = Goods.FOOD.ordinal();
		double need = (wf.adults * r.foodAdult + wf.children * r.foodChild + wf.elders * r.foodElder) * days;
		frac[food] -= need;
		double deficit = -(amounts[food] + frac[food]);
		if (deficit <= 0) {
			return;
		}
		double[] value = data.edible();
		for (int pass = 0; pass < 2 && deficit > 0; pass++) {
			for (int g : EAT_ORDER) {
				if (deficit <= 0) {
					break;
				}
				if (value[g] <= 0) {
					continue;
				}
				double have = amounts[g] + frac[g] - (pass == 0 ? reserved[g] : 0);
				if (have <= 0) {
					continue;
				}
				double take = Math.min(have, deficit / value[g]);
				frac[g] -= take;
				frac[food] += take * value[g];
				deficit -= take * value[g];
			}
		}
	}

	/** Whole units into and out of the stores; what will not fit is sold to passing traders, cheaply. */
	static void settle(SimSettlement s, SimRules r, double[] frac, double coin) {
		SimStockpile stock = s.stockpile();
		SimSettlement.ClockState k = s.clock;
		for (int g = 0; g < frac.length; g++) {
			if (frac[g] >= 1) {
				int whole = (int) Math.floor(frac[g]);
				int added = stock.addQuiet(g, whole);
				double sold = (whole - added) * r.prices[g] * r.exportShare;
				coin += sold;
				if (sold > 0) {
					s.land().traded(sold, 0);
				}
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

	/** Prepared food and everything edible in store, in food units. */
	public static double edible(SimSettlement s, SimData data) {
		double[] value = data.edible();
		int[] amounts = s.stockpile().amounts();
		double sum = 0;
		for (int g = 0; g < value.length; g++) {
			if (value[g] > 0) {
				sum += amounts[g] * value[g];
			}
		}
		return sum;
	}

	/**
	 * An embodied resident's work, credited at the abstract rates when the entity folds back into its record (the clock
	 * left them out of its head count meanwhile). Foresters' felling is not owed to the world again: their entity felled
	 * real trees.
	 */
	static void credit(SimSettlement s, dev.voidmc.hearth.api.Resident res, JobType job, double workerDays) {
		SimContext c = s.ctx();
		SimRules r = c.rules();
		SimData data = c.data();
		Crews crews = new Crews();
		int k = crewOf(crews, s, job, res.work, data, r);
		Crew crew = crews.crews.get(k);
		double[] frac = s.clock.fractions;
		SimStockpile stock = s.stockpile();
		work(s, crew, workerDays, frac, stock.amounts(), stock.capacities(), data, r, c, 1, false, reserved(s));
		double coin = r.coin[job.ordinal()] * workerDays * (c.economyHandlesWages() ? ECONOMY_REVENUE : 1);
		settle(s, r, frac, coin);
		s.markDirty();
	}

}
