package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.model.Reservation;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * Ground kept from the founding for the buildings a town will need on its square long before it can build them: the
 * town hall's lot on the square and the market hall's beside it. Houses and workshops cannot take a lot; it is released
 * to its building when the program comes to it. (The chapel's close, kept round the chapel for a cathedral, works the
 * same way; see {@link Landmarks}.)
 */
public final class Lots {
	private static final List<Identifier> KEPT = List.of(Archetypes.TOWN_HALL, Archetypes.MARKET_HALL, Archetypes.COURTHOUSE);

	private Lots() {
	}

	/**
	 * Whether a lot the town will want on its square is neither kept nor built yet (none could be found at founding):
	 * it is looked for again while the town is small, before the square's frontage is all taken.
	 */
	public static boolean missing(PlanContext ctx) {
		if (ctx.state.squareEdge < 0 || ctx.tier.ordinal() >= dev.voidmc.hearth.api.SettlementTier.TOWN.ordinal()) {
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
			Parcels.Request req = new Parcels.Request(info, id.equals(Archetypes.MARKET_HALL) ? Placement.CENTRAL : Placement.CIVIC, null, 1, f, d,
				false, 0, 0);
			// on the square, or on a main street at the corner of it; smaller if the square is small
			Parcels.Parcel p = Parcels.best(ctx, req, e -> e == square || ctx.state.graph.edge(e).role == RoadEdge.Role.ARM);
			if (p == null) {
				p = Parcels.best(ctx, req.withSize(info.minFrontage(), info.minDepth()), e -> e == square || ctx.state.graph.edge(e).role == RoadEdge.Role.ARM);
			}
			if (p == null || ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) > ctx.state.plazaHalf + 34) {
				continue;
			}
			if (Growth.trace) {
				dev.voidmc.hearth.Hearth.LOG.info("[plan] lot for {} at {},{} {}x{} facing {}", id.getPath(), p.x0, p.z0, p.sizeX, p.sizeZ, p.facing);
			}
			ctx.state.reservations.add(new Reservation("lot/" + id.getPath(), p.x0, p.z0, p.x1(), p.z1(), Cell.RESERVED));
			ctx.occ.markRect(p.x0, p.z0, p.x1(), p.z1(), Cell.RESERVED);
			any = true;
		}
		return any;
	}

	/** Frees the lot kept for an archetype (if there is one) so it can be built on now. */
	public static boolean release(PlanContext ctx, Identifier id) {
		Reservation r = ctx.state.reservation("lot/" + id.getPath());
		if (r == null) {
			return false;
		}
		ctx.state.removeReservation(r.tag());
		ctx.occ.clearRect(r.x0(), r.z0(), r.x1(), r.z1(), Cell.RESERVED);
		return true;
	}
}
