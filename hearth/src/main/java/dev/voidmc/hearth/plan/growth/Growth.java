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
	record Need(String key, double priority, Predicate<PlanContext> plan) {
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
				if (ctx.buildings > 0) {
					ctx.state.cooldowns.remove(need.key);
				} else if (ctx.state.queue.size() > before) {
					// only a road came of it: the need rests a little so roads do not run on without anything to build
					ctx.state.cooldowns.put(need.key, ctx.state.calls + (need.key.startsWith("landmark") ? 12 : 4));
				}
				return true;
			}
			ctx.state.cooldowns.put(need.key, ctx.state.calls + 4 + ctx.random.nextInt(5));
			if (++attempts >= MAX_ATTEMPTS) {
				break;
			}
		}
		return false;
	}

	static List<Need> needs(PlanContext ctx, Needs needs) {
		List<Need> list = new ArrayList<>();
		int pop = ctx.population;
		SettlementTier tier = ctx.tier;
		// cap what is planned but not yet built, so the plan does not run far ahead of the builders
		boolean busy = needs.unbuilt > 4 + pop / 6;

		int deficit = needs.housingDeficit(ctx.config.housingMargin());
		if (deficit > 0) {
			list.add(new Need("housing", 600 + Math.min(300, deficit * 12), c -> Housing.plan(c, needs, deficit)));
		}
		int mouths = Math.max(pop, 3);
		if (needs.food < mouths) {
			list.add(new Need("food", 420 + Math.min(200, (mouths - needs.food) * 6), c -> Farms.plan(c, needs)));
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
			ArchetypeInfo info = ctx.catalogue.resolve(e.id(), ctx.culture, tier);
			if (info == null) {
				continue;
			}
			int have = needs.count(info.id());
			if (info.maxPerSettlement() > 0) {
				want = Math.min(want, info.maxPerSettlement());
			}
			if (have < want) {
				Identifier id = info.id();
				list.add(new Need("program/" + id.getPath(), e.priority() - have * 15, c -> {
					Lots.release(c, e.id());
					if (id.equals(Archetypes.CHAPEL)) {
						return Landmarks.chapel(c, info, "faith: the village raises its chapel on the rise");
					}
					if (id.equals(Archetypes.MARKET_STALL) || Archetypes.isPlazaFurniture(id) && c.state.squareEdge >= 0) {
						if (Square.furniture(c, info, "market: a stall on the square")) {
							return true;
						}
					}
					return placeBuilding(c, info, e.placement(), e.district(), reasonFor(info, e.placement()), null);
				}));
			}
		}
		if (tier.ordinal() >= SettlementTier.VILLAGE.ordinal() && !Square.paved(ctx) && ctx.catalogue.has(Archetypes.PLAZA)) {
			list.add(new Need("plaza", 280, Square::pave));
		}
		Landmarks.needs(ctx, needs, list);
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
		if (deficit <= 0 && needs.food >= mouths && tier.ordinal() >= SettlementTier.HAMLET.ordinal()) {
			int beauty = needs.category(ArchetypeCategory.LANDSCAPE);
			if (beauty < 2 + pop / 15) {
				list.add(new Need("beauty", 60, c -> Beauty.plan(c, needs)));
			}
		}
		return list;
	}

	/** A workplace for a trade whose workers have nowhere to work. */
	static boolean workplace(PlanContext ctx, JobType job) {
		for (ArchetypeInfo info : ctx.catalogue.forJob(job, ctx.culture, ctx.tier)) {
			if (info.category() == ArchetypeCategory.FARM) {
				return Farms.place(ctx, info, null);
			}
			if (info.maxPerSettlement() > 0 && Needs.of(ctx).count(info.id()) >= info.maxPerSettlement()) {
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
		Parcels.Request req = new Parcels.Request(info, placement, district);
		Parcels.Parcel p = Parcels.best(ctx, req, null);
		if (p != null && !acceptable(ctx, placement, p)) {
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
				if (!Parcels.stillClear(ctx, p) || q != null && acceptable(ctx, placement, q)
					&& ctx.distanceToHeart(q.x0 + q.sizeX / 2, q.z0 + q.sizeZ / 2) < ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2)) {
					p = q != null && acceptable(ctx, placement, q) ? q : null;
				}
			}
		}
		if (p == null) {
			grew = Expand.toward(ctx, placement);
			if (grew) {
				p = Parcels.best(ctx, req, null);
				if (p != null && !acceptable(ctx, placement, p)) {
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
	private static boolean acceptable(PlanContext ctx, Placement placement, Parcels.Parcel p) {
		double d = ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2);
		double core = ctx.coreRadius();
		return switch (placement) {
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
