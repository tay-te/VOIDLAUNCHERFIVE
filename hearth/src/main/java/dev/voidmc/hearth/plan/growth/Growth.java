package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.Signature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Decides what a settlement builds next (PLANNING.md, "Needs to projects"): lists what it lacks — beds, food, a
 * trade's workplace, the civic buildings of its tier, a landmark, docks, beauty once needs are met, taller houses in a
 * crowded core — ranks them by urgency with a little chance in the order, and plans the first that can be placed,
 * growing the road network where there is no room. A need that could not be placed rests for a while so the planner
 * does not try it on every call.
 */
public final class Growth {
	private static final int MAX_ATTEMPTS = 7;
	/** Logs every need tried and whether it could be met ({@code -Dhearth.dev.plan.trace=true}). */
	public static boolean trace = Boolean.getBoolean("hearth.dev.plan.trace");

	private Growth() {
	}

	/** A need, with how urgent it is and how to meet it. */
	public record Need(String key, double priority, Predicate<PlanContext> plan) {
	}

	/** A need other packages add to the list (the nobility, the chains, the law). */
	public static Need need(String key, double priority, Predicate<PlanContext> plan) {
		return new Need(key, priority, plan);
	}

	/** Plans the most urgent need that can be met; true if anything was queued. */
	public static boolean step(PlanContext ctx) {
		Needs needs = Needs.of(ctx);
		List<Need> list = needs(ctx, needs);
		// a little chance in the order, so towns differ even with the same needs
		List<double[]> order = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			order.add(new double[]{list.get(i).priority * (0.9 + 0.2 * ctx.random.nextDouble()), i});
		}
		order.sort(Comparator.comparingDouble((double[] o) -> -o[0]).thenComparingDouble(o -> o[1]));
		int attempts = 0;
		for (double[] o : order) {
			if (ctx.pastDeadline()) {
				// a live call out of time: the other needs wait for the next call
				break;
			}
			Need need = list.get((int) o[1]);
			Long until = ctx.state.cooldowns.get(need.key);
			if (until != null && until > ctx.state.calls) {
				continue;
			}
			long t0 = System.nanoTime();
			int before = ctx.state.queue.size();
			ctx.buildings = 0;
			boolean met = need.plan.test(ctx);
			if (trace) {
				dev.voidmc.hearth.Hearth.LOG.info("[plan] {} pop {} {}: {} ({}) {} us", ctx.settlement.name(), ctx.population, need.key,
					met ? "planned" : "no room", (int) o[0], (System.nanoTime() - t0) / 1000);
			}
			if (met) {
				ctx.state.failures.remove(need.key);
				if (ctx.buildings > 0) {
					ctx.state.cooldowns.remove(need.key);
				} else if (ctx.state.queue.size() > before) {
					// only a road came of it: the need rests a little so roads do not run on without anything to build
					ctx.state.cooldowns.put(need.key, ctx.state.calls + (farOff(need.key) ? 12 : 4));
				}
				return true;
			}
			// a need that keeps finding no room rests longer each time (up to eight times as long), so a town that has no
			// place for its observatory does not search for one on every other call
			int failed = ctx.state.failures.merge(need.key, 1, Integer::sum);
			boolean vital = need.key.equals("housing") || need.key.equals("food") || need.key.startsWith("short/");
			ctx.state.cooldowns.put(need.key, ctx.state.calls + (4 + ctx.random.nextInt(5)) * (vital ? 1 : Math.min(8, failed)));
			if (++attempts >= MAX_ATTEMPTS) {
				break;
			}
		}
		return false;
	}

	/** Needs whose buildings stand apart (a landmark on the summit, a monastery on quiet ground): their roads are long. */
	private static boolean farOff(String key) {
		return key.startsWith("landmark") || key.endsWith("observatory") || key.endsWith("monastery") || key.endsWith("convent")
			|| key.endsWith("hermit_hut") || key.startsWith("noble/");
	}

	static List<Need> needs(PlanContext ctx, Needs needs) {
		List<Need> list = new ArrayList<>();
		int pop = ctx.population;
		SettlementTier tier = ctx.tier;
		// cap what is planned but not yet built, so the plan does not run far ahead of the builders
		boolean busy = needs.unbuilt > 4 + pop / 6;

		int capacity = ctx.territory().capacity();
		int short_ = needs.housingDeficit(ctx.config.housingMargin(), capacity);
		int social = 0;
		if (ctx.config.living().stations()) {
			// households in a home not of their station (a peasant family in a townhouse) want one of their own, within what
			// the land can carry
			// homes for households living in another station's home use a little slack over the town's beds (a fifth), never
			// past what the land can carry: the sim moves families into homes of their station, and the homes they leave
			// take newcomers
			int roof = (int) Math.ceil(pop * (1 + ctx.config.housingMargin()) + Math.max(4, pop * 0.2));
			if (capacity > 0) {
				roof = Math.min(roof, Math.max(pop, capacity) + 4);
			}
			int ceiling = roof - needs.beds - needs.plannedBeds;
			social = Math.max(0, Math.min(needs.stations(ctx).totalShortfall(ctx.config.housingMargin()), ceiling));
		}
		int deficit = Math.max(short_, social);
		if (deficit > 0) {
			double priority = short_ > 0 ? 600 + Math.min(300, short_ * 12) : 240 + Math.min(160, social * 4);
			list.add(new Need("housing", priority, c -> Housing.plan(c, needs, deficit)));
		}
		int mouths = Math.max(pop, 3);
		if (needs.food < mouths) {
			list.add(new Need("food", 420 + Math.min(200, (mouths - needs.food) * 6), c -> Farms.plan(c, needs)));
		}
		// builders waiting on goods the town hardly makes: the workplace that makes them, even while busy
		if (needs.waiting > 0) {
			for (Shortage sh : SHORTAGES) {
				if (tier.ordinal() >= sh.tier.ordinal()) {
					shortage(ctx, needs, list, sh);
				}
			}
		}
		if (busy) {
			return list;
		}
		// jobs held by residents with nowhere to work
		for (JobType job : JobType.values()) {
			int w = needs.workers.getOrDefault(job, 0);
			int s = needs.slots.getOrDefault(job, 0);
			if (job != JobType.NONE && job != JobType.COURIER && w > s) {
				list.add(new Need("job/" + job.getSerializedName(), 300 + 12 * (w - s), c -> workplace(c, job)));
			}
		}
		for (Program.Entry e : Program.ENTRIES) {
			if (pop < e.minPop() || !e.when().test(ctx)) {
				continue;
			}
			int want = e.count().applyAsInt(pop);
			// the living world's buildings are what they are: a monastery never falls back to a library
			ArchetypeInfo info = dev.voidmc.hearth.plan.catalogue.DefaultCatalogue.isPhase3(e.id()) ? exact(ctx, e.id())
				: ctx.catalogue.resolve(e.id(), ctx.culture, tier);
			if (info == null) {
				continue;
			}
			int have = needs.count(info.id());
			if (info.maxPerSettlement() > 0) {
				want = Math.min(want, info.maxPerSettlement());
			}
			if (have < want) {
				Identifier id = info.id();
				list.add(new Need("program/" + id.getPath(), e.priority() - have * 15, c -> program(c, e, info)));
			}
		}
		if (Lots.missing(ctx)) {
			// a lot on the square for the hall or the court, kept before the houses take every frontage
			list.add(new Need("lots", 335, Lots::reserve));
		}
		if (tier.ordinal() >= SettlementTier.VILLAGE.ordinal() && !Square.paved(ctx) && ctx.catalogue.has(Archetypes.PLAZA)) {
			list.add(new Need("plaza", 280, Square::pave));
		}
		Landmarks.needs(ctx, needs, list);
		dev.voidmc.hearth.plan.society.Nobility.needs(ctx, needs, list);
		Gates.needs(ctx, needs, list);
		dev.voidmc.hearth.plan.deep.DeepTown.needs(ctx, needs, list);
		if (Docks.wanted(ctx, needs)) {
			list.add(new Need("docks", 150, Docks::plan));
		}
		if (Walls.wanted(ctx)) {
			list.add(new Need("walls", 310, Walls::plan));
		}
		if (tier.ordinal() >= SettlementTier.TOWN.ordinal() || tier == SettlementTier.VILLAGE && ctx.state.layout.dense && pop >= 40) {
			double p = 110 + 90 * ctx.state.layout.verticalBias * ctx.config.verticalBias();
			list.add(new Need("densify", p, c -> Upgrades.plan(c, needs, false)));
		}
		if (ctx.state.signature.stacks && tier.ordinal() >= SettlementTier.HAMLET.ordinal()) {
			list.add(new Need("stack", 120, Stacks::plan));
		}
		// households that outgrew their houses build on (docs/buildings/age.md)
		Need additions = Additions.need(ctx);
		if (additions != null) {
			list.add(additions);
		}
		if (deficit <= 0 && needs.food >= mouths && tier.ordinal() >= SettlementTier.HAMLET.ordinal()) {
			int beauty = needs.category(ArchetypeCategory.LANDSCAPE);
			if (beauty < 2 + pop / 15) {
				list.add(new Need("beauty", 60, c -> Beauty.plan(c, needs)));
			}
		}
		return list;
	}

	/**
	 * A good builders may wait for, the trades that make it (the last step of its chain first, then what feeds it), how
	 * little in store counts as short, and from which tier the town makes it itself.
	 */
	private record Shortage(dev.voidmc.hearth.api.Goods goods, int below, SettlementTier tier, JobType... jobs) {
	}

	/** Below this much in store, with builders waiting, a town plans the workplace that makes the goods. */
	private static final List<Shortage> SHORTAGES = List.of(
		new Shortage(dev.voidmc.hearth.api.Goods.STONE, 200, SettlementTier.CAMP, JobType.QUARRIER),
		new Shortage(dev.voidmc.hearth.api.Goods.WOOD, 200, SettlementTier.CAMP, JobType.FORESTER),
		new Shortage(dev.voidmc.hearth.api.Goods.LOGS, 120, SettlementTier.HAMLET, JobType.FORESTER),
		new Shortage(dev.voidmc.hearth.api.Goods.DRESSED_STONE, 96, SettlementTier.VILLAGE, JobType.MASON, JobType.QUARRIER),
		new Shortage(dev.voidmc.hearth.api.Goods.BRICK, 64, SettlementTier.VILLAGE, JobType.KILN_WORKER, JobType.CLAY_DIGGER),
		new Shortage(dev.voidmc.hearth.api.Goods.GLASS, 32, SettlementTier.VILLAGE, JobType.GLASSMAKER, JobType.CLAY_DIGGER),
		new Shortage(dev.voidmc.hearth.api.Goods.METAL, 32, SettlementTier.VILLAGE, JobType.SMELTER, JobType.MINER, JobType.SMITH),
		new Shortage(dev.voidmc.hearth.api.Goods.FUEL, 24, SettlementTier.VILLAGE, JobType.CHARCOAL_BURNER),
		new Shortage(dev.voidmc.hearth.api.Goods.CANDLES, 16, SettlementTier.VILLAGE, JobType.CHANDLER, JobType.BEEKEEPER));

	private static void shortage(PlanContext ctx, Needs needs, List<Need> list, Shortage sh) {
		if (ctx.settlement.stockpile().get(sh.goods) >= sh.below) {
			return;
		}
		for (JobType job : sh.jobs) {
			// one such workplace per forty souls is plenty
			if (needs.slots.getOrDefault(job, 0) >= 2 * (1 + ctx.population / 40)) {
				continue;
			}
			list.add(new Need("short/" + sh.goods.getSerializedName(), 380, c -> workplace(c, job)));
			return;
		}
	}

	/** {@link #exact} for other packages (the deep town, the nobility). */
	public static @Nullable ArchetypeInfo exactInfo(PlanContext ctx, Identifier id) {
		return exact(ctx, id);
	}

	/** An archetype exactly as registered and allowed here, or null (no fallback). */
	static @Nullable ArchetypeInfo exact(PlanContext ctx, Identifier id) {
		ArchetypeInfo info = ctx.catalogue.info(id);
		return info != null && Catalogue.allowed(info, ctx.culture, ctx.tier) ? info : null;
	}

	/** Plans one of the program's buildings, as its entry says. */
	static boolean program(PlanContext c, Program.Entry e, ArchetypeInfo info) {
		Identifier id = info.id();
		Lots.release(c, e.id());
		if (id.equals(Archetypes.CHAPEL)) {
			return Landmarks.chapel(c, info, "faith: the village raises its chapel on the rise");
		}
		if (e.custom() != null) {
			return e.custom().test(c, info);
		}
		if (id.equals(Archetypes.MARKET_STALL) || Archetypes.isPlazaFurniture(id) && c.state.squareEdge >= 0) {
			String what = id.equals(Archetypes.MARKET_STALL) ? "market: a stall on the square" : reasonFor(info, e.placement()) + " on the square";
			if (Square.furniture(c, info, what)) {
				return true;
			}
		}
		Siting siting = e.siting() == null ? null : e.siting().apply(c);
		Parcels.Request req = new Parcels.Request(info, e.placement(), e.district());
		if (siting != null) {
			req = req.sited(siting);
		}
		CompoundTag params = null;
		if (c.config.living().stations() && dev.voidmc.hearth.plan.society.Stations.isHome(info) && info.beds() > 0) {
			// a home the program raises (a manor on the hill, a hermit's hut) is for its station too
			params = new CompoundTag();
			params.putString("station", dev.voidmc.hearth.plan.society.Stations.inferred(id).getSerializedName());
		}
		return placeBuilding(c, req, reasonFor(info, req.placement()), params);
	}

	/** A workplace for a trade whose workers have nowhere to work. */
	static boolean workplace(PlanContext ctx, JobType job) {
		for (ArchetypeInfo info : ctx.catalogue.forJob(job, ctx.culture, ctx.tier)) {
			if (info.category() == ArchetypeCategory.FARM) {
				return Farms.place(ctx, info, null);
			}
			// the deep town builds its own; the lord's seat is the nobility's to plan
			if (info.category() == ArchetypeCategory.UNDERGROUND) {
				continue;
			}
			if (info.maxPerSettlement() > 0 && Needs.of(ctx).count(info.id()) >= info.maxPerSettlement()) {
				continue;
			}
			if (info.category() == ArchetypeCategory.NOBLE && dev.voidmc.hearth.plan.society.Nobility.isSeat(info.id())) {
				if (dev.voidmc.hearth.plan.society.Nobility.seat(ctx)) {
					return true;
				}
				continue;
			}
			if (info.category() == ArchetypeCategory.EXTRACTION || dev.voidmc.hearth.plan.resource.Extraction.kind(info.id()) != null) {
				if (dev.voidmc.hearth.plan.resource.Extraction.place(ctx, info)) {
					return true;
				}
				continue;
			}
			Program.Entry entry = Program.entry(info.id());
			if (entry != null && entry.when().test(ctx) && (entry.siting() != null || entry.custom() != null)) {
				if (program(ctx, entry, info)) {
					return true;
				}
				continue;
			}
			Placement placement = info.flags().contains(SiteFlag.WATERFRONT) ? Placement.WATER
				: info.flags().contains(SiteFlag.OUTSKIRTS) ? Placement.EDGE
				: info.flags().contains(SiteFlag.CENTRAL) ? Placement.CENTRAL
				: info.flags().contains(SiteFlag.ATTACHABLE) ? Placement.MAIN : Placement.CRAFTS;
			if (placeBuilding(ctx, info, placement, District.Kind.CRAFTS, "work: the " + job.getSerializedName() + "s need a "
				+ Build.shortName(info), null)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Places a building where it wants to be, growing roads toward that place if no frontage is free. True if
	 * anything was queued (the building, or at least the road it will stand on).
	 */
	public static boolean placeBuilding(PlanContext ctx, ArchetypeInfo info, Placement placement, District.@Nullable Kind district,
		String reason, @Nullable CompoundTag params) {
		return placeBuilding(ctx, new Parcels.Request(info, placement, district), reason, params);
	}

	/** {@link #placeBuilding(PlanContext, ArchetypeInfo, Placement, District.Kind, String, CompoundTag)} for a full request. */
	public static boolean placeBuilding(PlanContext ctx, Parcels.Request req, String reason, @Nullable CompoundTag params) {
		ArchetypeInfo info = req.info();
		Placement placement = req.placement();
		Parcels.Parcel p = Parcels.best(ctx, req, null);
		if (p != null && !acceptable(ctx, req, p)) {
			p = null;
		}
		boolean grew = false;
		// in town, make room near the heart (a new lane) rather than settle far out along a main road
		if (p != null && wantsCentre(placement) && ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) > ctx.coreRadius() * reach(placement)
			&& !ctx.state.layout.expansive) {
			if (Expand.town(ctx)) {
				grew = true;
				Parcels.Parcel q = Parcels.best(ctx, req, null);
				// the new road may run through the old site, so it only stands if it is still clear
				if (!Parcels.stillClear(ctx, p) || q != null && acceptable(ctx, req, q)
					&& ctx.distanceToHeart(q.x0 + q.sizeX / 2, q.z0 + q.sizeZ / 2) < ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2)) {
					p = q != null && acceptable(ctx, req, q) ? q : null;
				}
			}
		}
		if (p == null) {
			grew = placement == Placement.SITED && req.siting() != null ? Expand.toward(ctx, req.siting()) : Expand.toward(ctx, placement);
			if (grew) {
				p = Parcels.best(ctx, req, null);
				if (p != null && !acceptable(ctx, req, p)) {
					p = null;
				}
			}
		}
		if (p == null) {
			return grew;
		}
		if (trace) {
			dev.voidmc.hearth.Hearth.LOG.info("[plan]   {} at {},{} {}x{} facing {} edge {} ({}) dist {}", info.id().getPath(), p.x0, p.z0, p.sizeX, p.sizeZ,
				p.facing, p.edge, ctx.state.graph.edge(p.edge).role, (int) ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2));
		}
		Build.building(ctx, info, p, reason, params);
		return true;
	}

	private static boolean wantsCentre(Placement placement) {
		return switch (placement) {
			case HOME, CENTRAL, CIVIC, MAIN, CRAFTS -> true;
			default -> false;
		};
	}

	/** How far out (in core radii) a placement accepts a site before the planner makes room nearer the heart. */
	static double reach(Placement placement) {
		return switch (placement) {
			case CENTRAL -> 0.6;
			case CIVIC, MAIN -> 0.8;
			case HOME -> 0.95;
			default -> 1.2;
		};
	}

	/** Rejects a place that misses the point of its placement (a rise that is not raised, an edge in the core). */
	private static boolean acceptable(PlanContext ctx, Parcels.Request req, Parcels.Parcel p) {
		if (Landmarks.big(req.info()) && (p.terrace || p.hi - p.lo > ctx.config.slopeTolerance() + 1)) {
			// a cathedral, a temple court, a great hall: on level ground or not at all, never on a terrace up a slope
			return false;
		}
		if (req.placement() == Placement.SITED && req.siting() != null) {
			Siting s = req.siting();
			double d = ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2);
			// a workshop of the outskirts (a tannery, a smelter, a kiln) stands at the edge of the town it is becoming, wherever
			// its inputs are, so the streets do not grow round it
			if (req.info().flags().contains(SiteFlag.OUTSKIRTS) && d < futureEdge(ctx)) {
				return false;
			}
			return Math.hypot(p.x0 + p.sizeX / 2.0 - s.x(), p.z0 + p.sizeZ / 2.0 - s.z()) <= s.maxDistance() + Math.max(p.sizeX, p.sizeZ) / 2.0 + 8;
		}
		return acceptable(ctx, req.placement(), p);
	}

	/** Where the edge of the town will be once it has grown (at least a town of a hundred): the industrial edge. */
	public static double futureEdge(PlanContext ctx) {
		return Math.max(Parcels.edgeRing(ctx) * 0.85, ctx.coreRadiusAt(Math.max(100, (int) (ctx.population * 1.6))) * 1.25);
	}

	private static boolean acceptable(PlanContext ctx, Placement placement, Parcels.Parcel p) {
		double d = ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2);
		double core = ctx.coreRadius();
		return switch (placement) {
			case GATEWAY -> d > core * 0.6;
			case QUIET -> d > Parcels.edgeRing(ctx) + 12;
			case HOVELS, CROFTS -> d > core * 0.45;
			case RISE, SUMMIT -> ctx.state.site == null || p.floorY >= ctx.state.heartY + Math.min(4, (ctx.state.site.riseY() - ctx.state.heartY) / 2)
				|| d < core;
			case EDGE -> d > core * 0.8 && (ctx.wallLine() <= 0 || d > ctx.wallLine());
			case FARMSTEAD -> d > core * 1.5;
			case WATER -> Parcels.waterDistance(ctx.ground, p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) <= 9
				|| p.sizeX * p.sizeZ > 0 && d < core * 0.5;
			default -> true;
		};
	}

	static String reasonFor(ArchetypeInfo info, Placement placement) {
		String name = Build.shortName(info);
		return switch (info.category()) {
			case CIVIC -> "civic: the town needs a " + name;
			case COMMERCE -> "trade: a " + name;
			case STORAGE -> "storage: a " + name + " for the town's goods";
			case WORKPLACE -> "work: a " + name;
			case DEFENSE -> "defence: a " + name;
			case LANDSCAPE -> "beauty: a " + name;
			case LANDMARK -> "landmark: the town raises its " + name;
			case LAW -> "law: the town raises its " + name;
			case NOBLE -> "nobility: the lord's " + name;
			case EXTRACTION -> "resources: a " + name;
			case UNDERGROUND -> "the deep: a " + name;
			default -> name;
		};
	}

	/** Allowed archetypes of a category for this settlement, sorted by id. */
	static List<ArchetypeInfo> allowed(PlanContext ctx, ArchetypeCategory category) {
		List<ArchetypeInfo> out = new ArrayList<>();
		for (ArchetypeInfo info : ctx.catalogue.category(category)) {
			if (Catalogue.allowed(info, ctx.culture, ctx.tier)) {
				out.add(info);
			}
		}
		return out;
	}

	/** Weighted draw from a list with weights; null if all weights are zero. */
	static <T> @Nullable T draw(PlanContext ctx, List<T> items, List<Double> weights) {
		double total = 0;
		for (double w : weights) {
			total += Math.max(0, w);
		}
		if (total <= 0) {
			return null;
		}
		double r = ctx.random.nextDouble() * total;
		for (int i = 0; i < items.size(); i++) {
			r -= Math.max(0, weights.get(i));
			if (r <= 0 && weights.get(i) > 0) {
				return items.get(i);
			}
		}
		return items.get(items.size() - 1);
	}

	static boolean isCulture(PlanContext ctx, Culture... cultures) {
		for (Culture c : cultures) {
			if (ctx.culture == c) {
				return true;
			}
		}
		return false;
	}

	static boolean layoutIs(PlanContext ctx, LayoutType... types) {
		for (LayoutType t : types) {
			if (ctx.state.layout == t) {
				return true;
			}
		}
		return false;
	}

	static boolean signatureIs(PlanContext ctx, Signature... sigs) {
		for (Signature s : sigs) {
			if (ctx.state.signature == s) {
				return true;
			}
		}
		return false;
	}

	/** Whether a spec stands on an edge of a given role (by its entrance). */
	static boolean frontsRole(PlanContext ctx, Parcels.Parcel p, RoadEdge.Role role) {
		return ctx.state.graph.edge(p.edge).role == role;
	}

	static @Nullable ArchetypeInfo info(PlanContext ctx, PlotSpec spec) {
		return ctx.info(spec.archetype());
	}
}
