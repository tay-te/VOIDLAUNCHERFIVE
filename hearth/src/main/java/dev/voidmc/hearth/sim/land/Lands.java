package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.chains.SimData;
import dev.voidmc.hearth.sim.model.SimContext;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's land through time (docs/LIVING_WORLD.md §7.1–7.2): its territory surveyed into sites at founding and
 * again as it grows, sites taken from as they are worked and regrowing at their rates, depletion announced, and the link
 * between a site and the excavation working it. Runs in histories and live worlds alike (a live world surveys off the
 * server thread and merges here).
 */
public final class Lands {
	private Lands() {
	}

	/** What a survey needs, taken on the thread that owns the settlement. */
	public record Request(long settlement, BlockPos heart, int radius, Survey.Params params, List<Territory.Core> cores) {
	}

	/** The territory this settlement should work now, and the survey that would cover it. */
	public static Request request(SimSettlement s, SimContext c) {
		SimConfig.Lands cfg = c.rules().resources.land();
		List<Territory.Core> cores = c.cores(s);
		int wanted = c.territoryLimit(s, Territory.byTier(s.tier(), cfg.territory()));
		int radius = Territory.radius(s.center(), wanted, coreRadius(s, cfg.coreMargin()), cores);
		return new Request(s.id(), s.center(), radius, new Survey.Params(cfg.surveyCells(), cfg.surveyStep(), cfg.maxSites()), cores);
	}

	/** How far a settlement's built-up core reaches from its heart (its bounds, plus a margin). */
	public static int coreRadius(SimSettlement s, int margin) {
		BoundingBox b = s.bounds();
		BlockPos h = s.center();
		int r = Math.max(Math.max(h.getX() - b.minX(), b.maxX() - h.getX()), Math.max(h.getZ() - b.minZ(), b.maxZ() - h.getZ()));
		return Math.max(16, r) + margin;
	}

	/** Runs a survey (any thread: touches only the land and the data). */
	public static List<Survey.Found> run(Request q, Land land, SimData data) {
		return Survey.run(land, q.heart(), q.radius(), data, q.params(), (x, z) -> Territory.inCore(x, z, q.cores()));
	}

	/** Surveys now, on this thread, and merges (histories, tests, commands). */
	public static void surveyNow(SimSettlement s, SimContext c) {
		Request q = request(s, c);
		List<Survey.Found> found = run(q, Land.of(c.terrain(), c.underground()), c.data());
		merge(s, q.radius(), Survey.step(q.radius(), q.params()), found, day(s), c);
	}

	private static long day(SimSettlement s) {
		return Math.floorDiv(s.simTick(), 24000L);
	}

	/**
	 * Folds a survey into the settlement's sites: a site found again keeps its id, what was taken from it and the
	 * excavation working it (a bigger survey grows its capacity in proportion); a new one fires {@code SITE_FOUND}; one
	 * no longer in the territory (a neighbour's core grew over it) is dropped unless it is being worked.
	 */
	public static void merge(SimSettlement s, int radius, int step, List<Survey.Found> found, long day, SimContext c) {
		LandState land = s.land();
		List<SimSite> before = new ArrayList<>(land.sites);
		boolean[] kept = new boolean[before.size()];
		List<SimSite> added = new ArrayList<>();
		int near = Math.max(48, step * 2);
		for (Survey.Found f : found) {
			int match = -1;
			double bestD = Double.MAX_VALUE;
			for (int i = 0; i < before.size(); i++) {
				SimSite e = before.get(i);
				if (kept[i] || e.kind != f.kind() || !java.util.Objects.equals(e.material, f.material())) {
					continue;
				}
				double dx = e.center.getX() - f.center().getX();
				double dz = e.center.getZ() - f.center().getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				boolean inside = e.area.isInside(f.center().getX(), e.area.minY(), f.center().getZ());
				if ((d < near || inside) && d < bestD) {
					bestD = d;
					match = i;
				}
			}
			if (match >= 0) {
				SimSite e = before.get(match);
				kept[match] = true;
				if (f.capacity() > e.capacity) {
					// more of it surveyed: the new ground comes as full as the old is now
					e.remaining += (f.capacity() - e.capacity) * (e.capacity <= 0 ? 1 : e.fill());
					e.capacity = f.capacity();
				}
				e.area = f.area();
				e.regen = f.regen();
				e.density = f.density();
				e.yields = f.yields().clone();
				e.ore = f.ore();
				e.fuel = f.fuel();
				e.cells = f.cells();
			} else {
				added.add(new SimSite(land.nextSite++, f.kind(), f.center(), f.area(), f.capacity(), f.capacity(), f.regen(), f.density(),
					f.yields().clone(), f.material(), PlotView.NONE, f.cells(), f.ore(), f.fuel(), 0, 0));
			}
		}
		land.sites.clear();
		for (int i = 0; i < before.size(); i++) {
			SimSite e = before.get(i);
			boolean ours = e.distanceTo(s.center()) <= radius + step;
			if (kept[i] || e.plot != PlotView.NONE || ours && (e.remaining > 0 || e.regen > 0)) {
				land.sites.add(e);
			}
		}
		land.sites.addAll(added);
		land.territory = radius;
		land.surveyed = radius;
		land.lastSurveyDay = day;
		land.touched();
		s.markDirty();
		HearthWorld w = c.eventWorld();
		if (w != null) {
			for (SimSite site : added) {
				SettlementEvents.SITE_FOUND.invoker().onSite(w, s, site.view());
			}
		}
	}

	/**
	 * Takes {@code used} units from a site (a worker-day's wear, or an excavation's cut rock). An exhausted site fires
	 * {@code SITE_DEPLETED} once; logs taken from a natural wood (no woodlot working it) are owed to the world, felled
	 * where its chunks load, when {@code owe}.
	 */
	public static void take(SimSettlement s, SimSite site, double used, double logs, boolean owe, SimContext c) {
		if (used > 0) {
			site.remaining -= used;
		}
		if (site.remaining <= 0) {
			site.remaining = 0;
			if ((site.flags & SimSite.EXHAUSTED) == 0) {
				site.flags |= SimSite.EXHAUSTED;
				HearthWorld w = c.eventWorld();
				if (w != null) {
					SettlementEvents.SITE_DEPLETED.invoker().onSite(w, s, site.view());
				}
			}
		}
		SimConfig.Extraction x = c.rules().resources.extraction();
		if (owe && logs > 0 && site.kind == ResourceSite.Kind.WOODS && site.plot == PlotView.NONE && c.inWorld() && x.fellWoods() && !x.abstractOnly()) {
			site.fellingDebt = Math.min(x.maxFellingDebt(), site.fellingDebt + logs);
		}
	}

	/**
	 * The day's business of the land: sites regrow (forests, pasture, fish, clay banks; stone and ore never), a renewable
	 * site regrown past half re-arms its depletion event, excavations are linked to the sites they work, and the
	 * territory is surveyed again when the tier has grown it (or every {@code resurvey_days}).
	 */
	public static void daily(SimSettlement s, long day, SimContext c) {
		LandState land = s.land();
		for (SimSite site : land.sites) {
			site.workedYesterday = site.worked;
			site.worked = 0;
			if (site.regen > 0 && site.remaining < site.capacity) {
				site.remaining = Math.min(site.capacity, site.remaining + site.regen);
				if ((site.flags & SimSite.EXHAUSTED) != 0 && site.fill() > 0.5) {
					site.flags &= ~SimSite.EXHAUSTED;
				}
			}
		}
		link(s, c.data());
		land.touched();
		SimConfig.Lands cfg = c.rules().resources.land();
		int wanted = c.territoryLimit(s, Territory.byTier(s.tier(), cfg.territory()));
		boolean grown = land.surveyed == 0 || wanted > land.surveyed + 8;
		boolean stale = land.lastSurveyDay == Long.MIN_VALUE || day - land.lastSurveyDay >= cfg.resurveyDays();
		if ((grown || stale) && !land.surveying) {
			c.survey(s);
		}
	}

	/** Each site's excavation: the plot whose {@code site} param names it and is still worked (or standing). */
	public static void link(SimSettlement s, SimData data) {
		LandState land = s.land();
		for (SimSite site : land.sites) {
			SimPlot p = site.plot == PlotView.NONE ? null : s.plot(site.plot);
			if (p == null || p.state() == PlotState.ABANDONED || p.state() == PlotState.DEMOLISHING) {
				site.plot = PlotView.NONE;
			}
		}
		for (SimPlot p : s.plots()) {
			if (!p.isActive() || data.extraction(p.spec().archetype()) == null) {
				continue;
			}
			SimSite site = siteFor(s, p, data);
			if (site != null) {
				site.plot = p.id();
			}
		}
	}

	/**
	 * The site an excavation works: the one its {@code site} param names, else the nearest of its kind (a planner that
	 * did not say), or null if the territory has none.
	 */
	public static @Nullable SimSite siteFor(SimSettlement s, PlotView p, SimData data) {
		LandState land = s.land();
		var params = p.spec().params();
		if (params.contains("site")) {
			SimSite named = land.site(params.getIntOr("site", -1));
			if (named != null) {
				return named;
			}
		}
		SimData.Extraction x = data.extraction(p.spec().archetype());
		if (x == null) {
			return null;
		}
		SimSite best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos at = p.spec().origin();
		for (SimSite site : land.sites) {
			if (site.kind != x.kind()) {
				continue;
			}
			double d = site.center.distSqr(at);
			if (d < bestD) {
				bestD = d;
				best = site;
			}
		}
		return best;
	}

	/** Units of goods a site holds now, worth noting when telling what a town lacks (for daughter hamlets). */
	public static double stock(SimSettlement s, ResourceSite.Kind kind) {
		double sum = 0;
		for (SimSite site : s.land().sites) {
			if (site.kind == kind) {
				sum += site.remaining;
			}
		}
		return sum;
	}

	/** The goods ordinal a site kind mainly gives (for commands and trade). */
	public static int mainGoods(ResourceSite.Kind kind) {
		return switch (kind) {
			case WOODS -> Goods.LOGS.ordinal();
			case STONE -> Goods.STONE.ordinal();
			case ORE -> Goods.ORE.ordinal();
			case CLAY -> Goods.CLAY.ordinal();
			case SAND -> Goods.SAND.ordinal();
			case SALT -> Goods.SALT.ordinal();
			case FERTILE -> Goods.GRAIN.ordinal();
			case PASTURE -> Goods.WOOL.ordinal();
			case FISH -> Goods.FISH.ordinal();
			case SPECIALTY -> Goods.SPECIALTY.ordinal();
		};
	}
}
