package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.land.LandState;
import dev.voidmc.hearth.sim.land.SimSite;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.model.SimRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.util.RandomSource;

/**
 * A town's fortunes (docs/LIVING_WORLD.md §7.2): carrying capacity — the people its land and its trade can feed — and
 * prosperity, a slow average of how its sites, food, work, trade, coin and housing are doing. Prosperity raises the
 * finish of what it builds; a boom draws settlers; a bust (sites worked out, trade gone) sends households away and leaves
 * their houses standing empty, to decay (the build module's decay look) or to be moved back into when times turn.
 * {@code PROSPERITY_CHANGED} fires when the fortunes turn, with a short why.
 */
public final class Prosperity {
	/** A camp or a young hamlet does not lose households to decline (hunger still drives folk away). */
	static final int EMIGRATION_FROM = 12;

	private Prosperity() {
	}

	/** The day's reckoning: capacity, prosperity, the turn of fortunes, and decline's departures and empty houses. */
	static void daily(SimSettlement s, SimRules r, SimContext c, long day, RandomSource random) {
		SimConfig.Prosperity cfg = r.resources.prosperity();
		LandState land = s.land();
		SimData data = c.data();
		land.setCapacity(capacity(s, r, data));
		if (!cfg.enabled()) {
			return;
		}
		int pop = s.population();
		Factors f = factors(s, r, data);
		double p = land.prosperity() + cfg.smoothing() * (f.score() - land.prosperity());
		land.setProsperity(p);
		land.decayTrade(0.9);
		HearthWorld w = c.eventWorld();
		if (p > cfg.riseAbove() && land.mood() <= 0) {
			land.setMood(1, f.best());
			if (w != null) {
				SettlementEvents.PROSPERITY_CHANGED.invoker().onProsperityChanged(w, s, true, f.best());
			}
		} else if (p < cfg.declineBelow() && land.mood() >= 0 && pop > 0) {
			land.setMood(-1, f.worst());
			if (w != null) {
				SettlementEvents.PROSPERITY_CHANGED.invoker().onProsperityChanged(w, s, false, f.worst());
			}
		} else if (land.mood() > 0 && p < cfg.riseAbove() - 0.08 || land.mood() < 0 && p > cfg.declineBelow() + 0.08) {
			// back to ordinary times: quietly
			land.setMood(0, land.why());
		}
		if (land.mood() < 0 && pop > EMIGRATION_FROM && s.clock.daysLived() > 30) {
			double depth = Math.max(0, (cfg.declineBelow() - p) / Math.max(0.01, cfg.declineBelow()));
			if (random.nextDouble() < cfg.emigration() * (0.3 + depth)) {
				leave(s, random);
			}
		}
		abandonEmpty(s, cfg, c, land);
		resettle(s, land, c);
		weather(s, cfg, land, day);
	}

	/** The decay of a house the day it is left (the build module's {@code Decay.ABANDONED}), and of each step after. */
	static final double LEFT = 0.25;

	/**
	 * Time's work on the houses a decline left empty (docs/buildings/age.md): a quarter of the way to a ruin the day a house
	 * is left (lamps and bedding gone), then a quarter more every {@code decay_step_days} (holes in the roof, the roof
	 * gone, a roofless shell). A house folk move back into is rebuilt whole ({@link #resettle}).
	 */
	static void weather(SimSettlement s, SimConfig.Prosperity cfg, LandState land, long day) {
		if (cfg.decayStepDays() <= 0 || land.deserted().isEmpty()) {
			return;
		}
		for (int id : List.copyOf(land.deserted())) {
			SimPlot p = s.plots.get(id);
			if (p == null || p.state != PlotState.ABANDONED || p.owner != null) {
				land.resettle(id);
				continue;
			}
			double decay = SimSettlement.decay(p.spec());
			double next;
			if (decay < LEFT) {
				next = LEFT;
			} else if (decay < 1 && day - p.completedDay >= cfg.decayStepDays()) {
				next = Math.min(1, decay + LEFT);
			} else {
				continue;
			}
			SimPlot w = s.weather(p, next);
			if (w != p) {
				land.resettle(id);
				land.desert(w.id());
			}
		}
	}

	/**
	 * What a day's fortunes are made of, each 0–1, and the score (their weighted sum).
	 */
	record Factors(double sites, double food, double work, double trade, double coin, double housing, String siteWhy) {
		double score() {
			// a town that merely feeds itself sits near the middle; trade and coin make a boom, worked-out land a bust
			return 0.2 * this.sites + 0.15 * this.food + 0.1 * this.work + 0.25 * this.trade + 0.2 * this.coin + 0.1 * this.housing;
		}

		String best() {
			double m = Math.max(Math.max(this.sites, this.food), Math.max(this.trade, this.coin));
			if (m == this.trade) {
				return "trade is good";
			}
			if (m == this.sites) {
				return "the land round about is rich";
			}
			if (m == this.food) {
				return "the stores are full";
			}
			return "the treasury is full";
		}

		String worst() {
			double m = Math.min(Math.min(Math.min(this.sites, this.food), Math.min(this.work, this.trade)), Math.min(this.coin, this.housing));
			if (m == this.sites) {
				return this.siteWhy;
			}
			if (m == this.food) {
				return "food is short";
			}
			if (m == this.work) {
				return "there is no work";
			}
			if (m == this.trade) {
				return "the trade has gone";
			}
			if (m == this.housing) {
				return "there is no room";
			}
			return "the treasury is empty";
		}
	}

	static Factors factors(SimSettlement s, SimRules r, SimData data) {
		LandState land = s.land();
		int pop = Math.max(1, s.population());
		// the sites in use: how much is left of what is being worked (a worked-out mine drags a mining town down)
		double fill = 0;
		double weight = 0;
		SimSite worst = null;
		for (SimSite site : land.sites()) {
			double w = site.workedYesterday();
			if (w <= 0) {
				continue;
			}
			fill += site.fill() * w;
			weight += w;
			if (worst == null || site.fill() < worst.fill()) {
				worst = site;
			}
		}
		double sites = weight > 0 ? fill / weight : land.sites().isEmpty() ? 0.6 : 0.5;
		String siteWhy = worst == null ? "the land gives little" : worked(worst);
		double days = Production.edible(s, data) / Math.max(1, pop * r.foodAdult);
		double food = Math.min(1, days / 30);
		int adults = 0;
		int working = 0;
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT) {
				adults++;
				if (res.job != JobType.NONE) {
					working++;
				}
			}
		}
		double work = adults == 0 ? 0.5 : working / (double) adults;
		double perHead = (land.exports() - land.imports()) / 10.0 / pop;
		double trade = 0.5 + 0.5 * Math.tanh(perHead / 1.5);
		double coin = Math.tanh(s.treasury / (pop * 120.0));
		double housing = s.population() == 0 ? 1 : Math.min(1, s.beds() / (double) s.population());
		return new Factors(sites, food, work, trade, coin, housing, siteWhy);
	}

	private static String worked(SimSite site) {
		String what = site.material() == null ? site.kind().getSerializedName() : site.material().getPath().replace('_', ' ');
		if (site.fill() <= 0.02 && !site.renewable()) {
			return String.format(Locale.ROOT, "the %s at %d, %d is worked out", what, site.center().getX(), site.center().getZ());
		}
		return String.format(Locale.ROOT, "the %s at %d, %d is running out", what, site.center().getX(), site.center().getZ());
	}

	/**
	 * People the land and the trade can feed: what the food sites regrow a day (in food, through the trades that work them
	 * at full richness), plus what the town's trade earns in food bought in, over what a grown person eats.
	 */
	static int capacity(SimSettlement s, SimRules r, SimData data) {
		LandState land = s.land();
		if (land.surveyed() == 0) {
			return Integer.MAX_VALUE;
		}
		double[] value = data.edible();
		double perDay = 0;
		for (SimSite site : land.sites()) {
			ResourceSite.Kind kind = site.kind();
			double perUnit = foodPerWear(data, kind, site);
			if (perUnit > 0) {
				perDay += site.regen() * perUnit;
			}
		}
		double tradeFood = Math.max(0, land.exports() / 10.0) / Math.max(0.01, r.prices[Goods.FOOD.ordinal()] * r.importMarkup);
		// the founders' stores and the odd job labourers' foraging keep a camp going whatever its land
		double food = perDay + tradeFood + 2.5 * 5;
		if (value[Goods.FOOD.ordinal()] <= 0) {
			return Integer.MAX_VALUE;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.floor(food / Math.max(0.1, r.foodAdult) * 1.15));
	}

	/** Food a unit of a site's wear gives through the trade the site is for (a farm's grain and fruit, eaten). */
	private static double foodPerWear(SimData data, ResourceSite.Kind kind, SimSite site) {
		double[] value = data.edible();
		if (kind == ResourceSite.Kind.SPECIALTY) {
			var sp = data.specialty(site.material());
			return sp != null && value[sp.goods().ordinal()] > 0 ? value[sp.goods().ordinal()] : 0;
		}
		SimData.Gathering g = data.gathering(data.site(kind).primary());
		SimData.Gathering.Entry e = g == null ? null : g.entry(kind);
		if (e == null || e.wearPerDay() <= 0) {
			return 0;
		}
		double food = 0;
		for (int i = 0; i < e.yields().length; i++) {
			food += e.yields()[i] * value[i];
		}
		return food / e.wearPerDay();
	}

	/** A household leaves a failing town (all who share a home; a homeless adult alone). */
	private static void leave(SimSettlement s, RandomSource random) {
		List<Resident> adults = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res.age == AgeStage.ADULT && !s.isResidentOut(res.id)) {
				adults.add(res);
			}
		}
		if (adults.isEmpty()) {
			return;
		}
		Resident first = adults.get(random.nextInt(adults.size()));
		List<UUID> going = new ArrayList<>();
		for (Resident res : s.residents.values()) {
			if (res == first || first.home != Resident.NO_PLOT && res.home == first.home && !s.isResidentOut(res.id)) {
				going.add(res.id);
			}
		}
		for (UUID id : going) {
			Resident res = s.residents.get(id);
			if (res != null) {
				// listeners can tell a departure from a death
				res.extra("sim").putString("left_for", "elsewhere");
			}
			s.removeResident(id);
		}
	}

	/**
	 * A declining town with more empty beds than it needs leaves a house standing empty: {@code ABANDONED}, its blocks as
	 * they are (decay is the build module's look over abandoned plots), its beds no longer counted. Never a player's
	 * house.
	 */
	private static void abandonEmpty(SimSettlement s, SimConfig.Prosperity cfg, SimContext c, LandState land) {
		if (land.mood() >= 0 || s.beds() - s.population() <= cfg.abandonSlack()) {
			return;
		}
		it.unimi.dsi.fastutil.ints.IntOpenHashSet lived = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
		for (Resident res : s.residents.values()) {
			if (res.home != Resident.NO_PLOT) {
				lived.add(res.home);
			}
		}
		SimPlot pick = null;
		for (SimPlot p : s.plots.values()) {
			if (p.state != PlotState.BUILT || p.beds <= 0 || p.owner != null || lived.contains(p.id())) {
				continue;
			}
			ArchetypeInfo info = c.blueprints().archetype(p.spec().archetype());
			if (info != null && info.category() != ArchetypeCategory.HOUSING) {
				continue;
			}
			// the furthest out goes first: the edge of town empties before the heart
			if (pick == null || p.spec().origin().distSqr(s.center()) > pick.spec().origin().distSqr(s.center())) {
				pick = p;
			}
		}
		if (pick != null && s.beds() - pick.beds >= s.population()) {
			pick.state = PlotState.ABANDONED;
			land.desert(pick.id());
			s.invalidateDerived();
			s.markDirty();
			HearthWorld w = c.eventWorld();
			if (w != null) {
				SettlementEvents.PLOT_ABANDONED.invoker().onPlotAbandoned(w, s, pick, "left empty: " + land.why());
			}
		}
	}

	/** When folk need beds again, they move back into a house left empty before anything new is built. */
	private static void resettle(SimSettlement s, LandState land, SimContext c) {
		if (land.deserted().isEmpty() || land.mood() < 0) {
			return;
		}
		int homeless = 0;
		for (Resident res : s.residents.values()) {
			if (res.home == Resident.NO_PLOT) {
				homeless++;
			}
		}
		if (homeless == 0) {
			return;
		}
		for (int id : List.copyOf(land.deserted())) {
			SimPlot p = s.plots.get(id);
			land.resettle(id);
			if (p != null && p.state == PlotState.ABANDONED && SimSettlement.decay(p.spec()) > 0) {
				// time has worked on it: lowering the decay is a rebuild, the house comes back whole (docs/buildings/age.md)
				net.minecraft.nbt.CompoundTag params = p.spec().params().copy();
				params.remove(SimSettlement.DECAY);
				PlotSpec o = p.spec();
				s.addPlot(new PlotSpec(o.archetype(), o.style(), o.seed(), o.origin(), o.sizeX(), o.sizeZ(), o.facing(), o.maxHeight(), o.tier(), params),
					p.id(), "repaired: folk came back");
				return;
			}
			if (p != null && p.state == PlotState.ABANDONED && p.stage >= s.facts(p).stageCount()) {
				p.state = PlotState.BUILT;
				s.invalidateDerived();
				s.markDirty();
				c.progressed(s, p);
				return;
			}
		}
	}

	/**
	 * The finish a new building gets: one tier finer in a boom, one rougher in decline (dressed stone and glass, or rough
	 * timber), within 0–4. Roads and the like are left as planned.
	 */
	static PlotSpec finish(SimSettlement s, PlotSpec spec, SimRules r) {
		SimConfig.Prosperity cfg = r.resources.prosperity();
		if (!cfg.enabled() || !cfg.finishTier() || SettlementClock.isRoad(spec)) {
			return spec;
		}
		int replaces = spec.params().getIntOr("replaces", -1);
		SimPlot old = replaces >= 0 ? s.plots.get(replaces) : null;
		if (old != null && SimSettlement.inPlace(old.spec(), spec)) {
			// an addition to a standing house keeps the house's finish
			return spec;
		}
		double p = s.land().prosperity();
		int tier = spec.tier();
		if (p >= cfg.riseAbove()) {
			tier = Math.min(4, tier + 1);
		} else if (p < cfg.declineBelow()) {
			tier = Math.max(0, tier - 1);
		}
		if (tier == spec.tier()) {
			return spec;
		}
		return new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(), spec.maxHeight(),
			tier, spec.params());
	}
}
