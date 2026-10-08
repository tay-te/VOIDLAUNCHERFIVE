package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.Reservation;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Ground kept from the founding for the buildings a town will need on its square long before it can build them: the
 * town hall's lot on the square and the market hall's beside it, and the church's with its close (the cathedral's
 * ground, see {@link Landmarks#chapel}), so the church and its tower stand over the square, not out at the edge
 * (ART_REVIEW_2 item 2). Houses and workshops cannot take a lot; it is released
 * to its building when the program comes to it. (The chapel's close, kept round the chapel for a cathedral, works the
 * same way; see {@link Landmarks}.)
 */
public final class Lots {
	private static final List<Identifier> KEPT = List.of(Archetypes.TOWN_HALL, Archetypes.MARKET_HALL, Archetypes.CHAPEL, Archetypes.COURTHOUSE);

	/** How far past the square's edge a hall without a lot may still stand, and be by the square. */
	private static final int BY_SQUARE = 36;

	private Lots() {
	}

	/**
	 * Whether a lot the town will want on its square is neither kept nor built yet (none could be found at founding):
	 * it is looked for again while the town is small, before the square's frontage is all taken.
	 */
	public static boolean missing(PlanContext ctx) {
		// (nor in a camp or hamlet whose homes found no other room and were given its halls' lots, see releaseForHomes)
		if (ctx.state.squareEdge < 0 || ctx.tier.ordinal() >= dev.voidmc.hearth.api.SettlementTier.TOWN.ordinal()
			|| ctx.tier.ordinal() < dev.voidmc.hearth.api.SettlementTier.VILLAGE.ordinal() && ctx.state.cooldowns.containsKey(RELEASED)) {
			return false;
		}
		for (Identifier id : KEPT) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info != null && info.suits(ctx.culture) && ctx.state.reservation("lot/" + id.getPath()) == null
				&& ctx.territory().first(id) == null && !(id.equals(Archetypes.COURTHOUSE) && !ctx.config.living().law())) {
				return true;
			}
		}
		return false;
	}

	/** Reserves the lots fronting the square that are not kept yet. Called at founding, and again while one is missing. */
	public static boolean reserve(PlanContext ctx) {
		int square = ctx.state.squareEdge;
		if (square < 0) {
			return false;
		}
		boolean any = false;
		for (Identifier id : KEPT) {
			if (ctx.state.reservation("lot/" + id.getPath()) != null || ctx.territory().first(id) != null) {
				continue;
			}
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info == null || !info.suits(ctx.culture) || id.equals(Archetypes.COURTHOUSE) && !ctx.config.living().law()) {
				continue;
			}
			int f = info.minFrontage() + (info.maxFrontage() - info.minFrontage()) * 2 / 3;
			int d = info.minDepth() + (info.maxDepth() - info.minDepth()) * 2 / 3;
			if (id.equals(Archetypes.CHAPEL)) {
				int[] close = Landmarks.closeSize(ctx, info);
				f = close[0];
				d = close[1];
			}
			Parcels.Request req = new Parcels.Request(info, id.equals(Archetypes.MARKET_HALL) ? Placement.CENTRAL : Placement.CIVIC, null, 1, f, d,
				false, 0, 0);
			// on the square, or on a main street at the corner of it; smaller if the square is small
			java.util.function.IntPredicate front = e -> e == square || ctx.state.graph.edge(e).role == RoadEdge.Role.ARM;
			Parcels.Parcel p = Parcels.best(ctx, req, front);
			if (p == null && id.equals(Archetypes.CHAPEL)) {
				// the close at the cathedral's least size before the chapel's own: a close no bigger than the chapel sends the
				// cathedral out of town when the city raises it (ART_REVIEW_2 item 2)
				int[] least = Landmarks.leastCloseSize(ctx, info);
				if (least[0] < f || least[1] < d) {
					p = Parcels.best(ctx, req.withSize(least[0], least[1]), front);
				}
			}
			if (p == null) {
				p = Parcels.best(ctx, req.withSize(info.minFrontage(), info.minDepth()), front);
			}
			if (p == null && !id.equals(Archetypes.CHAPEL)) {
				// on a terrace where the square's edge falls away: a hill town's hall has no level lot, and without one it was
				// raised a hundred blocks out on whatever flat ground the search found (the church's close must stay level)
				Parcels.Request terraced = new Parcels.Request(info, req.placement(), null, 1, f, d, true, 0, 0);
				p = Parcels.best(ctx, terraced, front);
			}
			// (a small square's lots stand on the main streets just off it: a walled town's hall found its lot 46 out, was
			// refused, and went up outside the gates)
			if (p == null || ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) > ctx.state.plazaHalf + 40) {
				continue;
			}
			if (Growth.trace) {
				dev.voidmc.hearth.Hearth.LOG.info("[plan] lot for {} at {},{} {}x{} facing {}", id.getPath(), p.x0, p.z0, p.sizeX, p.sizeZ, p.facing);
			}
			ctx.hold(new Reservation("lot/" + id.getPath(), p.x0, p.z0, p.x1(), p.z1(), Cell.RESERVED));
			any = true;
		}
		return any;
	}

	/**
	 * Builds a program building on the lot kept for it since the founding, exactly there (freed and searched for again, a
	 * hall went to whatever raised ground scored best a hundred blocks out); true if it was built. A lot that can no longer
	 * be had is given up, and the building is placed as its program says. The chapel takes its lot itself.
	 */
	static boolean build(PlanContext ctx, Program.Entry e, ArchetypeInfo info) {
		if (info.id().equals(Archetypes.CHAPEL) || !info.id().equals(e.id())) {
			release(ctx, e.id());
			return false;
		}
		Parcels.Parcel lot = take(ctx, info);
		String where = " on the square";
		if (lot == null && KEPT.contains(info.id())) {
			// no lot (none was free at the founding, or it is gone): the square's side or a street at its corner, on a terrace
			// if it must be, before the program's own search sends it out to the best raised ground anywhere
			// (at its least size if it must be; inside a walled town anywhere within the wall, never out past the gates)
			// (searched nearest the square first: the civic search ranks raised ground, and on a hill found it 90 blocks out)
			Parcels.Request near = new Parcels.Request(info, Placement.CENTRAL, e.district(), -1, -1, -1, true, 0, 0);
			double reach = Math.max(ctx.state.plazaHalf + BY_SQUARE, ctx.wallLine() > 0 ? ctx.wallLine() - 4 : 0);
			for (Parcels.Request r : List.of(near, near.withSize(info.minFrontage(), info.minDepth()))) {
				Parcels.Parcel p = Parcels.best(ctx, r, null);
				if (p != null && ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) <= reach) {
					lot = p;
					where = " by the square";
					break;
				}
			}
		}
		if (lot == null) {
			return false;
		}
		Build.building(ctx, info, lot, Growth.reasonFor(info, e.placement()) + where, null);
		return true;
	}

	/**
	 * Frees the halls' and the court's lots on the square (not the church's) for homes; true if one was kept. For a camp
	 * or hamlet whose houses find no other frontage: they are looked for again from village tier ({@link #missing}).
	 */
	static boolean releaseForHomes(PlanContext ctx) {
		boolean any = false;
		for (Identifier id : KEPT) {
			any |= release(ctx, id);
		}
		if (any) {
			// a marker, never counted down: the lots are not kept again before village tier
			ctx.state.cooldowns.put(RELEASED, ctx.state.calls);
		}
		return any;
	}

	/** Marks a camp or hamlet that gave its halls' lots to its homes (a key in the plan state's cooldowns). */
	private static final String RELEASED = "lots/released";

	/**
	 * Frees the lot kept for an archetype (if there is one) so it can be built on now. The church's lot is not freed here:
	 * the chapel takes it whole as its close ({@link #take}), or another building would take it first.
	 */
	public static boolean release(PlanContext ctx, Identifier id) {
		if (id.equals(Archetypes.CHAPEL)) {
			return false;
		}
		return free(ctx, id);
	}

	/**
	 * The lot kept for an archetype as a place to build on now: freed, and found again as a frontage place covering exactly
	 * the lot; null if there is none (or it can no longer be reached). If it cannot be had, the lot is gone either way.
	 */
	public static Parcels.@org.jspecify.annotations.Nullable Parcel take(PlanContext ctx, ArchetypeInfo info) {
		Reservation r = ctx.state.reservation("lot/" + info.id().getPath());
		if (r == null) {
			return null;
		}
		free(ctx, info.id());
		int w = r.x1() - r.x0() + 1;
		int h = r.z1() - r.z0() + 1;
		Parcels.Request req = new Parcels.Request(info, Placement.CIVIC, null, 0, -1, -1, true, 0, 0);
		// the lot faces the road its front row is on (or its door path runs to, where it was set back from a slanting street)
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			int[] ent = switch (facing) {
				case NORTH -> new int[]{r.x0() + w / 2, r.z0() - 1};
				case SOUTH -> new int[]{r.x0() + w / 2, r.z1() + 1};
				case WEST -> new int[]{r.x0() - 1, r.z0() + h / 2};
				default -> new int[]{r.x1() + 1, r.z0() + h / 2};
			};
			int gap = 0;
			while (gap <= ctx.config.doorPathMax() && !Cell.isAccess(ctx.occ.get(ent[0] + facing.getStepX() * gap, ent[1] + facing.getStepZ() * gap))) {
				if (!Cell.isVacant(ctx.occ.get(ent[0] + facing.getStepX() * gap, ent[1] + facing.getStepZ() * gap))) {
					gap = Integer.MAX_VALUE;
					break;
				}
				gap++;
			}
			if (gap > ctx.config.doorPathMax()) {
				continue;
			}
			int[] at = dev.voidmc.hearth.plan.road.Roads.edgeAt(ctx, ent[0] + facing.getStepX() * gap, ent[1] + facing.getStepZ() * gap);
			int edge = at != null ? at[0] : ctx.state.squareEdge;
			Parcels.Parcel p = edge < 0 ? null : Parcels.fitAt(ctx, req, edge, r.x0(), r.z0(), w, h, facing, gap);
			// facing this way the lot must give the building its least frontage and depth (a chapel's close may be larger)
			if (p != null && p.frontage() >= info.minFrontage() && p.depth() >= info.minDepth()
				&& (info.id().equals(Archetypes.CHAPEL) || p.frontage() <= info.maxFrontage() && p.depth() <= info.maxDepth())) {
				return p;
			}
		}
		return null;
	}

	private static boolean free(PlanContext ctx, Identifier id) {
		Reservation r = ctx.state.reservation("lot/" + id.getPath());
		if (r == null) {
			return false;
		}
		ctx.state.removeReservation(r.tag());
		ctx.release(r.x0(), r.z0(), r.x1(), r.z1());
		return true;
	}
}
