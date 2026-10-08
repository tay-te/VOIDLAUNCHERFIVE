package dev.voidmc.hearth.plan.society;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.growth.Build;
import dev.voidmc.hearth.plan.growth.Growth;
import dev.voidmc.hearth.plan.growth.Parcels;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.growth.Siting;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The lord and his seat (LIVING_WORLD.md §8.1): at town tier the lord's keep on the most commanding ground within reach
 * — the castle hill, chosen once and kept — with a close round it big enough for the castle (or, in the cultures that
 * build them, the palace) a city raises in its place; a cart road up to it; its stables and gardens beside it; a hunting
 * lodge out in the woods. The castle and palace replace the keep ({@code replaces}) over the keep and its close, as a
 * cathedral replaces the chapel.
 */
public final class Nobility {
	/** The reservation kept round the keep for the castle (its bailey until then). */
	public static final String CLOSE = "castle";
	/** The castle hill held from town tier until the keep is raised on it. */
	public static final String HILL = "castle_hill";

	private Nobility() {
	}

	/** The lord's seat standing or planned: keep, castle or palace, as a plot or a queued project. */
	public static @Nullable PlotSpec seatSpec(PlanContext ctx) {
		for (PlotView p : ctx.settlement.plots()) {
			if (isSeat(p.spec().archetype()) && p.state() != PlotState.DEMOLISHING) {
				return p.spec();
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (isSeat(q.spec().archetype())) {
				return q.spec();
			}
		}
		return null;
	}

	public static boolean isSeat(Identifier id) {
		return id.equals(Archetypes.KEEP) || id.equals(Archetypes.CASTLE) || id.equals(Archetypes.PALACE);
	}

	/**
	 * Plans the lord's seat: the keep if the town has none, the castle or palace in its place once it is a city. True if
	 * anything was queued (the seat, or the road up to it).
	 */
	public static boolean seat(PlanContext ctx) {
		if (ctx.tier.ordinal() < SettlementTier.TOWN.ordinal() || !ctx.config.living().nobility()) {
			return false;
		}
		PlotSpec seat = seatSpec(ctx);
		if (seat == null) {
			return keep(ctx);
		}
		if (ctx.tier == SettlementTier.CITY && seat.archetype().equals(Archetypes.KEEP)) {
			return castle(ctx);
		}
		return false;
	}

	/** The needs of a town's nobility besides the seat itself: stables and gardens by it, a hunting lodge in the woods. */
	public static void needs(PlanContext ctx, dev.voidmc.hearth.plan.growth.Needs needs, List<Growth.Need> out) {
		if (ctx.tier.ordinal() < SettlementTier.TOWN.ordinal() || !ctx.config.living().nobility()) {
			return;
		}
		PlotSpec seat = seatSpec(ctx);
		if (seat == null) {
			if (ctx.population >= SettlementTier.TOWN.minPopulation && ctx.catalogue.has(Archetypes.KEEP)) {
				out.add(Growth.need("noble/keep", 265, Nobility::seat));
			}
			return;
		}
		if (ctx.tier == SettlementTier.CITY && seat.archetype().equals(Archetypes.KEEP) && ctx.population >= 170) {
			out.add(Growth.need("noble/castle", 240, Nobility::seat));
		}
		int[] at = {seat.origin().getX() + seat.sizeX() / 2, seat.origin().getZ() + seat.sizeZ() / 2};
		ArchetypeInfo stables = ctx.catalogue.resolve(Archetypes.STABLES, ctx.culture, ctx.tier);
		if (stables != null && stables.id().equals(Archetypes.STABLES) && needs.count(Archetypes.STABLES) < 1) {
			out.add(Growth.need("noble/stables", 170, c -> beside(c, stables, at, "the lord's stables beside his " + Build.shortName(c.info(seat.archetype())))));
		}
		ArchetypeInfo garden = ctx.catalogue.info(Archetypes.GARDEN);
		if (garden != null && Catalogue.allowed(garden, ctx.culture, ctx.tier) && gardensNear(ctx, at) < (ctx.tier == SettlementTier.CITY ? 2 : 1)) {
			out.add(Growth.need("noble/garden", 110, c -> beside(c, garden, at, "the lord's gardens")));
		}
		ArchetypeInfo lodge = ctx.catalogue.info(Archetypes.HUNTING_LODGE);
		if (lodge != null && Catalogue.allowed(lodge, ctx.culture, ctx.tier) && needs.count(Archetypes.HUNTING_LODGE) < 1 && ctx.population >= 90) {
			out.add(Growth.need("noble/hunting_lodge", 90, c -> lodge(c, lodge)));
		}
	}

	private static int gardensNear(PlanContext ctx, int[] at) {
		int n = 0;
		for (int[] g : ctx.territory().positions(Archetypes.GARDEN)) {
			if (Math.hypot(g[0] - at[0], g[1] - at[1]) < 48) {
				n++;
			}
		}
		return n;
	}

	/** Something of the seat's beside it: on the roads near the keep, as close as can be. */
	private static boolean beside(PlanContext ctx, ArchetypeInfo info, int[] at, String reason) {
		Siting siting = Siting.near(at[0], at[1], 44).withHeight(0.3);
		Parcels.Request req = new Parcels.Request(info, Placement.SITED, District.Kind.CASTLE).sited(siting);
		Parcels.Parcel p = Parcels.best(ctx, req, null);
		if (p == null || Math.hypot(p.x0 + p.sizeX / 2.0 - at[0], p.z0 + p.sizeZ / 2.0 - at[1]) > 56) {
			return false;
		}
		Build.building(ctx, info, p, "nobility: " + reason, null);
		return true;
	}

	/** The hunting lodge: out at the edge of the nearest wood, else far out along a road. */
	private static boolean lodge(PlanContext ctx, ArchetypeInfo info) {
		List<ResourceSite> woods = ctx.territory().workable(ResourceSite.Kind.WOODS);
		if (!woods.isEmpty()) {
			ResourceSite w = woods.get(woods.size() > 1 ? 1 : 0);
			Parcels.Request req = new Parcels.Request(info, Placement.SITED, null).sited(Siting.near(w.center().getX(), w.center().getZ(), 70));
			Parcels.Parcel p = Parcels.best(ctx, req, null);
			if (p != null && Math.hypot(p.x0 - w.center().getX(), p.z0 - w.center().getZ()) < 90) {
				Build.building(ctx, info, p, "nobility: the lord's hunting lodge by the woods", null);
				return true;
			}
			if (ctx.distanceToHeart(w.center().getX(), w.center().getZ()) < ctx.config.living().haulMaxDistance()
				&& Roads.haul(ctx, w.center().getX(), w.center().getZ(), RoadEdge.Role.HAUL, RoadKind.PATH, 3, false,
				"road: a track out to the lord's hunting woods") != null) {
				return true;
			}
		}
		return Growth.placeBuilding(ctx, info, Placement.QUIET, null, "nobility: the lord's hunting lodge", null);
	}

	/**
	 * The castle hill: the most commanding ground within reach of the town — high, standing proud of the land round it,
	 * dry and wide enough for a castle — chosen once (at town tier, with the districts) and kept in the plan state.
	 */
	public static int[] hill(PlanContext ctx) {
		if (ctx.state.castleHill != null) {
			return ctx.state.castleHill;
		}
		Ground g = ctx.ground;
		double core = ctx.coreRadius();
		// within reach of the city the town is becoming, and out of the streets it already has
		double reach = Math.max(80, ctx.coreRadiusAt(150) * 2.2);
		double outOf = Math.max(ctx.state.plazaHalf + 14, core * 0.7);
		Site site = ctx.state.site;
		List<int[]> candidates = new ArrayList<>();
		if (site != null) {
			candidates.add(new int[]{site.summitX(), site.summitZ()});
			candidates.add(new int[]{site.riseX(), site.riseZ()});
		}
		for (int r = 24; r <= reach; r += 12) {
			int n = Math.max(12, (int) (r / 6));
			for (int k = 0; k < n; k++) {
				double a = k * 2 * Math.PI / n;
				candidates.add(new int[]{ctx.heartX() + (int) Math.round(StrictMath.cos(a) * r), ctx.heartZ() + (int) Math.round(StrictMath.sin(a) * r)});
			}
		}
		sited(ctx, reach, outOf, candidates);
		int[] best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int[] c : candidates) {
			double d = ctx.distanceToHeart(c[0], c[1]);
			if (d > reach + 30 || d < outOf) {
				continue;
			}
			// a hill or rim the terrain marks as a castle site is preferred to merely high ground
			double score = commanding(ctx, c[0], c[1]) - d * 0.06 + (c.length > 2 ? c[2] : 0);
			if (score > bestScore) {
				bestScore = score;
				best = c;
			}
		}
		if (best == null) {
			best = new int[]{ctx.heartX() + (int) core, ctx.heartZ()};
		}
		ctx.state.castleHill = new int[]{best[0], best[1]};
		return ctx.state.castleHill;
	}

	/** Score added to a candidate on the level top of a lone hill, and on (or just behind) an escarpment's rim. */
	static final int HILLTOP_BONUS = 14;
	static final int RIM_BONUS = 10;
	/** Castle sites the terrain marks that are looked at, nearest the heart first. */
	private static final int SITED_MAX = 24;

	/**
	 * The terrain's own castle sites within reach ({@code TerrainProbe#site}: Expanse marks the level tops of lone hills and
	 * the tops of escarpments' edges), added to the candidates with their bonus. A rim column stands on the edge itself, so
	 * the points ten blocks to each side of it are tried too: the one on the upland behind the edge is the castle's.
	 * Looked for on a 12-block grid, once per town (the hill is kept); a probe without sites answers none at once.
	 */
	static void sited(PlanContext ctx, double reach, double outOf, List<int[]> candidates) {
		var probe = ctx.ground.probe();
		List<int[]> found = new ArrayList<>();
		int r = (int) reach;
		for (int dz = -r; dz <= r; dz += 12) {
			for (int dx = -r; dx <= r; dx += 12) {
				double d = Math.hypot(dx, dz);
				if (d > reach || d < outOf) {
					continue;
				}
				int x = ctx.heartX() + dx;
				int z = ctx.heartZ() + dz;
				int site = probe.site(x, z);
				if (site != dev.voidmc.hearth.api.TerrainProbe.SITE_NONE) {
					found.add(new int[]{x, z, site, (int) d});
				}
			}
		}
		found.sort(java.util.Comparator.comparingInt((int[] f) -> f[3]));
		for (int i = 0; i < found.size() && i < SITED_MAX; i++) {
			int[] f = found.get(i);
			if (f[2] == dev.voidmc.hearth.api.TerrainProbe.SITE_HILLTOP) {
				candidates.add(new int[]{f[0], f[1], HILLTOP_BONUS});
				continue;
			}
			candidates.add(new int[]{f[0], f[1], RIM_BONUS});
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				candidates.add(new int[]{f[0] + dir.getStepX() * 10, f[1] + dir.getStepZ() * 10, RIM_BONUS});
			}
		}
	}

	/** How commanding a spot is: its height over the heart and over the land round it, if a castle can stand there. */
	private static double commanding(PlanContext ctx, int x, int z) {
		Ground g = ctx.ground;
		int h = g.ground(x, z);
		int half = 11;
		int[] range = g.range(x - half, z - half, x + half, z + half, 3);
		if (range[1] - range[0] > 9 || g.isWater(x, z) || g.isWater(x - half, z - half) || g.isWater(x + half, z + half)
			|| g.isWater(x - half, z + half) || g.isWater(x + half, z - half)) {
			return Double.NEGATIVE_INFINITY;
		}
		// the castle's ground must be mostly open still
		int open = 0;
		int all = 0;
		for (int dz = -half; dz <= half; dz += 3) {
			for (int dx = -half; dx <= half; dx += 3) {
				byte c = ctx.occ.get(x + dx, z + dz);
				all++;
				if (c == Cell.FREE || c == Cell.SETBACK || c == Cell.PLANNED_ROAD) {
					open++;
				}
			}
		}
		if (open * 10 < all * 8) {
			return Double.NEGATIVE_INFINITY;
		}
		double around = 0;
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			around += g.ground(x + (int) Math.round(StrictMath.cos(a) * 28), z + (int) Math.round(StrictMath.sin(a) * 28));
		}
		double prominence = h - around / 8;
		return (h - ctx.state.heartY) * 1.0 + prominence * 1.6 - (range[1] - range[0]) * 0.8;
	}

	/**
	 * Holds the castle hill (open ground only) from town tier, so the lord's keep finds it free when he raises it. Called
	 * when the districts are laid out.
	 */
	public static void reserveHill(PlanContext ctx) {
		if (ctx.state.reservation(HILL) != null || seatSpec(ctx) != null) {
			return;
		}
		int[] hill = hill(ctx);
		ArchetypeInfo future = future(ctx);
		int half = (future == null ? 23 : future.minFrontage()) / 2 + 3;
		ctx.hold(new Reservation(HILL, hill[0] - half, hill[1] - half, hill[0] + half, hill[1] + half, Cell.RESERVED));
	}

	/** The keep, on the castle hill, at the front of a close kept for the castle; a cart road up to it first. */
	private static boolean keep(PlanContext ctx) {
		ArchetypeInfo keep = ctx.catalogue.info(Archetypes.KEEP);
		if (keep == null || !Catalogue.allowed(keep, ctx.culture, ctx.tier)) {
			return false;
		}
		int[] hill = hill(ctx);
		// the hill was held for this: open it to the keep's search (and hold it again if the keep does not fit yet)
		Reservation held = ctx.state.reservation(HILL);
		if (held != null) {
			ctx.state.removeReservation(HILL);
			ctx.release(held.x0(), held.z0(), held.x1(), held.z1());
		}
		if (placeKeep(ctx, keep, hill)) {
			return true;
		}
		if (held != null) {
			ctx.hold(held);
		}
		// no frontage up there yet: a cart road up to the hill's foot on the town's side (steps only where it must)
		int[] foot = held == null ? hill : foot(ctx, held);
		if (Roads.haul(ctx, foot[0], foot[1], RoadEdge.Role.APPROACH, RoadKind.LANE, 3, false, "road: the way up to the castle hill") != null) {
			return true;
		}
		ctx.routed = false;
		return Roads.haul(ctx, foot[0], foot[1], RoadEdge.Role.APPROACH, RoadKind.LANE, 3, true, "road: the stair up to the castle hill") != null;
	}

	/** Just outside the held hill on the side facing the heart: where the road up to it ends. */
	private static int[] foot(PlanContext ctx, Reservation r) {
		int cx = (r.x0() + r.x1()) / 2;
		int cz = (r.z0() + r.z1()) / 2;
		int dx = ctx.heartX() - cx;
		int dz = ctx.heartZ() - cz;
		if (Math.abs(dx) >= Math.abs(dz)) {
			return new int[]{dx > 0 ? r.x1() + 2 : r.x0() - 2, cz};
		}
		return new int[]{cx, dz > 0 ? r.z1() + 2 : r.z0() - 2};
	}

	private static boolean placeKeep(PlanContext ctx, ArchetypeInfo keep, int[] hill) {
		ArchetypeInfo future = future(ctx);
		Siting siting = Siting.near(hill[0], hill[1], 30).withHeight(0.6);
		String reason = "nobility: the lord raises his keep on the most commanding ground";
		if (future != null) {
			int f = Math.max(keep.maxFrontage(), future.minFrontage());
			int d = Math.max(keep.maxDepth(), future.minDepth());
			Parcels.Request big = new Parcels.Request(keep, Placement.SITED, District.Kind.CASTLE).sited(siting).withSize(f, d);
			Parcels.Parcel p = near(ctx, Parcels.best(ctx, big, null), hill, 40);
			if (p != null && !p.terrace) {
				Parcels.Parcel k = front(p, keep);
				ctx.hold(new Reservation(CLOSE, p.x0, p.z0, p.x1(), p.z1(), Cell.RESERVED));
				Build.building(ctx, keep, k, reason + ", with a close kept for the castle", seatParams());
				return true;
			}
		}
		Parcels.Request req = new Parcels.Request(keep, Placement.SITED, District.Kind.CASTLE).sited(siting);
		Parcels.Parcel p = near(ctx, Parcels.best(ctx, req, null), hill, 40);
		if (p != null) {
			Build.building(ctx, keep, p, reason, seatParams());
			return true;
		}
		if (Growth.trace) {
			dev.voidmc.hearth.Hearth.LOG.info("[plan]   keep: hill {},{} (heart {},{}) no frontage yet", hill[0], hill[1], ctx.heartX(), ctx.heartZ());
		}
		return false;
	}

	private static Parcels.@Nullable Parcel near(PlanContext ctx, Parcels.@Nullable Parcel p, int[] at, double within) {
		if (p == null) {
			return null;
		}
		return Math.hypot(p.x0 + p.sizeX / 2.0 - at[0], p.z0 + p.sizeZ / 2.0 - at[1]) <= within + Math.max(p.sizeX, p.sizeZ) / 2.0 ? p : null;
	}

	private static CompoundTag seatParams() {
		CompoundTag params = new CompoundTag();
		params.putString("station", "lord");
		return params;
	}

	/** What the city will raise in the keep's place: a palace in the cultures that build them, else a castle. */
	private static @Nullable ArchetypeInfo future(PlanContext ctx) {
		ArchetypeInfo palace = ctx.catalogue.info(Archetypes.PALACE);
		if (palace != null && palace.suits(ctx.culture) && !palace.cultures().isEmpty()) {
			return palace;
		}
		ArchetypeInfo castle = ctx.catalogue.info(Archetypes.CASTLE);
		return castle != null && castle.suits(ctx.culture) ? castle : null;
	}

	/** The keep at the front of its close, centred on the street, as a chapel stands at the front of its close. */
	private static Parcels.Parcel front(Parcels.Parcel p, ArchetypeInfo keep) {
		int cf = keep.minFrontage() + (keep.maxFrontage() - keep.minFrontage()) / 2;
		int cd = keep.minDepth() + (keep.maxDepth() - keep.minDepth()) / 2;
		boolean alongX = p.facing.getAxis() == Direction.Axis.Z;
		int f = p.frontage();
		int off = (f - cf) / 2;
		Parcels.Parcel c = new Parcels.Parcel();
		c.facing = p.facing;
		c.sizeX = alongX ? cf : cd;
		c.sizeZ = alongX ? cd : cf;
		c.x0 = alongX ? p.x0 + off : switch (p.facing) {
			case WEST -> p.x0;
			default -> p.x1() - cd + 1;
		};
		c.z0 = alongX ? switch (p.facing) {
			case NORTH -> p.z0;
			default -> p.z1() - cd + 1;
		} : p.z0 + off;
		c.floorY = p.floorY;
		c.streetY = p.streetY;
		c.zone = p.zone;
		c.edge = p.edge;
		c.index = p.index;
		c.gap = p.gap;
		// a close set back from a slanting street keeps its garden path to the door
		c.doorGap = p.doorGap;
		return c;
	}

	/** The castle or palace over the keep and its close, replacing the keep. */
	private static boolean castle(PlanContext ctx) {
		ArchetypeInfo info = future(ctx);
		if (info == null || !Catalogue.allowed(info, ctx.culture, ctx.tier)) {
			return false;
		}
		Reservation close = ctx.state.reservation(CLOSE);
		for (PlotView plot : ctx.settlement.plots()) {
			PlotSpec old = plot.spec();
			if (!old.archetype().equals(Archetypes.KEEP) || plot.state() != PlotState.BUILT) {
				continue;
			}
			int x0 = old.origin().getX();
			int z0 = old.origin().getZ();
			int x1 = x0 + old.sizeX() - 1;
			int z1 = z0 + old.sizeZ() - 1;
			if (close != null) {
				x0 = Math.min(x0, close.x0());
				z0 = Math.min(z0, close.z0());
				x1 = Math.max(x1, close.x1());
				z1 = Math.max(z1, close.z1());
			}
			boolean alongX = old.facing().getAxis() == Direction.Axis.Z;
			int front = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
			int depth = alongX ? z1 - z0 + 1 : x1 - x0 + 1;
			if (front < info.minFrontage() || depth < info.minDepth()) {
				return false;
			}
			CompoundTag params = old.params();
			params.putInt("replaces", plot.id());
			params.putString("station", "lord");
			PlotSpec spec = new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, old.floorY(), z0), x1 - x0 + 1, z1 - z0 + 1,
				old.facing(), info.maxHeight(), Math.min(4, old.tier() + 1), params);
			ctx.state.removeReservation(CLOSE);
			Roads.issue(ctx, spec, plot.id(), "nobility: the city's lord raises a " + Build.shortName(info) + " in place of the keep");
			return true;
		}
		return false;
	}
}
