package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.road.Roads;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The building a town is known for (ARCHITECTURE.md §6.1 landmarks, §7.1 signatures): the first at town tier, a second
 * at city tier, chosen from the signature's preferences among what the build module offers, and set where it reads
 * from afar — a pagoda or bell tower on the summit, a lighthouse on the headland, a clock tower on the square. A
 * cathedral or temple rises in place of the chapel, on the close kept round it since the village days.
 */
public final class Landmarks {
	private Landmarks() {
	}

	static void needs(PlanContext ctx, Needs needs, List<Growth.Need> out) {
		if (ctx.tier.ordinal() < SettlementTier.TOWN.ordinal() || ctx.population < 80) {
			return;
		}
		int have = needs.category(ArchetypeCategory.LANDMARK);
		int want = ctx.tier == SettlementTier.CITY ? 2 : 1;
		if (have >= want) {
			return;
		}
		ArchetypeInfo info = next(ctx, needs);
		if (info != null) {
			out.add(new Growth.Need("landmark/" + info.id().getPath(), 230, c -> place(c, info)));
		}
	}

	/**
	 * Whether a landmark has its ground here: a lighthouse wants a headland or the shore near the town. (A harbour
	 * signature on a hill town far from the sea grew a road toward the water at every call and never placed it, so the
	 * cathedral never came and the roads ran out.)
	 */
	private static boolean grounded(PlanContext ctx, ArchetypeInfo info) {
		if (!info.id().equals(Archetypes.LIGHTHOUSE)) {
			return true;
		}
		Site site = ctx.state.site;
		return site != null && (site.hasHeadland() || site.hasWater() && site.waterDistance() <= ctx.coreRadius() * 1.5 + 24);
	}

	private static @Nullable ArchetypeInfo next(PlanContext ctx, Needs needs) {
		// a close kept round the chapel since the village days is for the cathedral or temple: it takes a landmark's
		// place before the towers can fill both slots (requests/arch-landmark.md §2)
		if (ctx.state.reservation("close") != null && (needs.category(ArchetypeCategory.LANDMARK) >= 1 || ctx.tier == SettlementTier.CITY)) {
			for (Identifier id : List.of(Archetypes.CATHEDRAL, Archetypes.TEMPLE)) {
				ArchetypeInfo info = ctx.catalogue.info(id);
				if (info != null && Catalogue.allowed(info, ctx.culture, ctx.tier) && needs.count(id) == 0 && needs.count(Archetypes.CATHEDRAL) == 0
					&& needs.count(Archetypes.TEMPLE) == 0) {
					return info;
				}
			}
		}
		if (ctx.state.signature == dev.voidmc.hearth.plan.model.Signature.NONE) {
			// no signature: the culture's own landmark first (the most particular, fewest cultures), so a karst town
			// raises its pagoda, a northern one its great hall, a maritime one its lighthouse (arch-landmark.md §1)
			List<ArchetypeInfo> allowed = new java.util.ArrayList<>(Growth.allowed(ctx, ArchetypeCategory.LANDMARK));
			allowed.sort(java.util.Comparator.comparingInt((ArchetypeInfo i) -> i.cultures().isEmpty() ? Integer.MAX_VALUE : i.cultures().size())
				.thenComparingInt(i -> {
					int k = ctx.state.signature.landmarks.indexOf(i.id());
					return k < 0 ? 99 : k;
				}));
			for (ArchetypeInfo info : allowed) {
				if (needs.count(info.id()) == 0 && grounded(ctx, info)) {
					return info;
				}
			}
			return null;
		}
		for (Identifier id : ctx.state.signature.landmarks) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info != null && Catalogue.allowed(info, ctx.culture, ctx.tier) && needs.count(id) == 0 && grounded(ctx, info)) {
				return info;
			}
		}
		for (ArchetypeInfo info : Growth.allowed(ctx, ArchetypeCategory.LANDMARK)) {
			if (needs.count(info.id()) == 0 && grounded(ctx, info)) {
				return info;
			}
		}
		return null;
	}

	/** How far from the summit (or the rise, or the heart) a great church or hall may stand and still crown it. */
	private static final int GREAT_REACH = 48;

	/** How far from the heart a building still stands on or by the square: the church, the town tower. */
	static final int BY_SQUARE = 44;

	private static boolean isLandmark(PlanContext ctx, Identifier id) {
		ArchetypeInfo i = ctx.info(id);
		return i != null && i.category() == ArchetypeCategory.LANDMARK;
	}

	/** A building on the square or beside it (within {@link #BY_SQUARE} of the heart), or nothing. */
	private static boolean bySquare(PlanContext ctx, ArchetypeInfo info, String reason) {
		Parcels.Parcel p = Parcels.best(ctx, new Parcels.Request(info, Placement.CENTRAL, District.Kind.MARKET), null);
		if (p == null || p.terrace || ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) > BY_SQUARE) {
			return false;
		}
		Build.building(ctx, info, p, reason + " over the square", null);
		return true;
	}

	/** The landmarks that need level ground (big footprints): never terraced up a slope. The lighthouse keeps its rocks. */
	static boolean big(ArchetypeInfo info) {
		return info.category() == ArchetypeCategory.LANDMARK && !info.id().equals(Archetypes.LIGHTHOUSE) && info.minFrontage() >= 11
			&& info.minDepth() >= 11;
	}

	static boolean place(PlanContext ctx, ArchetypeInfo info) {
		Identifier id = info.id();
		String reason = "landmark: the town raises its " + Build.shortName(info);
		if (id.equals(Archetypes.CATHEDRAL) || id.equals(Archetypes.TEMPLE)) {
			if (replaceChapel(ctx, info, reason)) {
				return true;
			}
			if (ctx.state.reservation("close") != null && chapelRising(ctx)) {
				// the cathedral rises on the chapel's close by the square once the chapel stands, not on a field elsewhere
				Growth.why("the chapel on its close is not built yet");
				return false;
			}
			// no close to rise on (the chapel took the least lot on a crowded square, or none was kept): by the square where
			// a level lot is free, else on the high ground in sight of the heart like the town's other great tower; the
			// civic search alone sent it 140-170 blocks out to whatever level field it found (ART_REVIEW_2 item 2)
			return great(ctx, info, reason);
		}
		Site site = ctx.state.site;
		if (id.equals(Archetypes.LIGHTHOUSE)) {
			if (site != null && site.hasHeadland() && ctx.distanceToHeart(site.headlandX(), site.headlandZ()) < ctx.coreRadius() * 3 + 60) {
				Expand.approachTo(ctx, site.headlandX(), site.headlandZ(), "approach: the road out to the headland");
			}
			return Growth.placeBuilding(ctx, info, Placement.WATER, District.Kind.HARBOUR, reason, null);
		}
		if (id.equals(Archetypes.CLOCK_TOWER)) {
			return Growth.placeBuilding(ctx, info, Placement.CENTRAL, District.Kind.MARKET, reason, null);
		}
		// the town's first tower stands over its square, where a Bohemian town has its town tower (ART_REVIEW_2 item 2); a
		// second landmark crowns the summit in sight of the heart
		boolean first = ctx.settlement.plots().stream().noneMatch(p -> isLandmark(ctx, p.spec().archetype()))
			&& ctx.state.queue.stream().noneMatch(q -> isLandmark(ctx, q.spec().archetype()));
		if (first && id.equals(Archetypes.BELL_TOWER) && bySquare(ctx, info, reason)) {
			return true;
		}
		if (id.equals(Archetypes.GREAT_HALL)) {
			return great(ctx, info, reason);
		}
		return crown(ctx, info, reason);
	}

	/** Whether the site's summit is high over the heart and near enough to be seen from it, for a landmark to crown. */
	private static boolean crownable(PlanContext ctx) {
		Site site = ctx.state.site;
		return site != null && site.summitY() > ctx.state.heartY + 8
			&& ctx.distanceToHeart(site.summitX(), site.summitZ()) < ctx.coreRadius() * 2.5 + 40;
	}

	/** A tower on the summit in sight of the heart, or on the rise where there is no summit near. */
	private static boolean crown(PlanContext ctx, ArchetypeInfo info, String reason) {
		return Growth.placeBuilding(ctx, info, crownable(ctx) ? Placement.SUMMIT : Placement.RISE, District.Kind.CIVIC, reason, null);
	}

	/**
	 * A great church or hall (a cathedral or temple with no close to rise on, a great hall): by the square where a level
	 * lot is free, else on level ground as near the summit (or the rise) as it will go, so it stands with the town's
	 * other tower over the heart. The highest-ground or civic search alone sent it 120-190 blocks out to the one level
	 * field it found at the edge (ART_REVIEW_2 item 2).
	 */
	private static boolean great(PlanContext ctx, ArchetypeInfo info, String reason) {
		Parcels.Parcel p = Parcels.best(ctx, new Parcels.Request(info, Placement.CIVIC, District.Kind.CIVIC), null);
		if (p != null && !p.terrace && ctx.distanceToHeart(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2) <= BY_SQUARE + 16) {
			Build.building(ctx, info, p, reason + " by the square", null);
			return true;
		}
		// level ground within sight of the summit, of the rise, or of the square, the higher the better
		Site site = ctx.state.site;
		List<int[]> anchors = new java.util.ArrayList<>();
		if (crownable(ctx)) {
			anchors.add(new int[]{site.summitX(), site.summitZ(), GREAT_REACH});
		}
		if (site != null && site.riseY() > ctx.state.heartY + 4) {
			anchors.add(new int[]{site.riseX(), site.riseZ(), GREAT_REACH});
		}
		anchors.add(new int[]{ctx.state.heartX, ctx.state.heartZ, BY_SQUARE + GREAT_REACH / 2});
		Parcels.Request civic = new Parcels.Request(info, Placement.CIVIC, District.Kind.CIVIC);
		for (int[] a : anchors) {
			Parcels.Parcel q = Parcels.best(ctx, civic.sited(Siting.near(a[0], a[1], a[2]).withHeight(1.0)), null);
			if (q != null && !q.terrace && q.hi - q.lo <= ctx.config.slopeTolerance() + 1
				&& Math.hypot(q.x0 + q.sizeX / 2.0 - a[0], q.z0 + q.sizeZ / 2.0 - a[1]) <= a[2] + Math.max(q.sizeX, q.sizeZ) / 2.0) {
				Build.building(ctx, info, q, reason + " over the town", null);
				return true;
			}
		}
		// none (it wants level ground): it waits, resting longer each time, rather than rise in a field at the edge; the
		// town's own growth may yet open level ground near the heart
		Growth.why("no level ground for it in sight of the heart");
		return false;
	}

	/**
	 * The chapel, on the rise, with a close kept round it (beside and behind) big enough for the cathedral or temple a
	 * city will raise in its place; a plain chapel if no such ground is free.
	 */
	/**
	 * The chapel's ground with its close: the cathedral's (or temple's) middle size where the culture raises one, else
	 * the chapel's largest; {frontage, depth}.
	 */
	static int[] closeSize(PlanContext ctx, ArchetypeInfo chapel) {
		for (Identifier id : List.of(Archetypes.CATHEDRAL, Archetypes.TEMPLE)) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info != null && info.suits(ctx.culture)) {
				return new int[]{Math.max(chapel.maxFrontage(), (info.minFrontage() + info.maxFrontage()) / 2),
					Math.max(chapel.maxDepth(), (info.minDepth() + info.maxDepth()) / 2)};
			}
		}
		return new int[]{chapel.maxFrontage(), chapel.maxDepth()};
	}

	/** The chapel's ground with the least close a cathedral (or temple) of the culture still fits; {frontage, depth}. */
	static int[] leastCloseSize(PlanContext ctx, ArchetypeInfo chapel) {
		for (Identifier id : List.of(Archetypes.CATHEDRAL, Archetypes.TEMPLE)) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info != null && info.suits(ctx.culture)) {
				return new int[]{Math.max(chapel.maxFrontage(), info.minFrontage()), Math.max(chapel.maxDepth(), info.minDepth())};
			}
		}
		return new int[]{chapel.maxFrontage(), chapel.maxDepth()};
	}

	public static boolean chapel(PlanContext ctx, ArchetypeInfo chapel, String reason) {
		// the lot kept on the square since the founding, if there is one, is its ground (and its close)
		Parcels.Parcel lot = Lots.take(ctx, chapel);
		ArchetypeInfo future = null;
		for (Identifier id : List.of(Archetypes.CATHEDRAL, Archetypes.TEMPLE)) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info != null && info.suits(ctx.culture)) {
				future = info;
				break;
			}
		}
		if (future != null) {
			// the close at the cathedral's middle size where the land allows (a nave of seven, a cloister, twin towers;
			// arch-landmark.md §3), else at its smallest
			int f = Math.max(chapel.maxFrontage(), (future.minFrontage() + future.maxFrontage()) / 2);
			int d = Math.max(chapel.maxDepth(), (future.minDepth() + future.maxDepth()) / 2);
			// the church stands on the square or just off it (a Bohemian town's skyline is its church tower and its town
			// tower over the square, ART_REVIEW_2 item 2), on the rise only where no ground by the square will hold it
			Parcels.Request big = null;
			int[] least = leastCloseSize(ctx, chapel);
			// a lot on the square too small for the cathedral's least close (the square's frontage would not hold one, as on
			// a hill) is the chapel's only when no full close is free near the square or on the rise in sight of it: a close
			// no bigger than the chapel sent the cathedral 150-190 blocks out to a field when the city raised it
			Parcels.Parcel p = lot != null && !lot.terrace && lot.frontage() >= least[0] && lot.depth() >= least[1] ? lot : null;
			if (p != null) {
				f = p.frontage();
				d = p.depth();
			}
			for (int[] size : new int[][]{{f, d}, least}) {
				if (p != null) {
					break;
				}
				Parcels.Request near = new Parcels.Request(chapel, Placement.CIVIC, District.Kind.CIVIC).withSize(size[0], size[1]);
				Parcels.Parcel q = Parcels.best(ctx, near, null);
				if (q != null && !q.terrace && ctx.distanceToHeart(q.x0 + q.sizeX / 2, q.z0 + q.sizeZ / 2) <= BY_SQUARE) {
					p = q;
					big = near;
					f = size[0];
					d = size[1];
					break;
				}
			}
			if (p == null && lot != null) {
				Parcels.Request rise = new Parcels.Request(chapel, Placement.RISE, District.Kind.CIVIC).withSize(least[0], least[1]);
				Parcels.Parcel q = Parcels.best(ctx, rise, null);
				if (q != null && !q.terrace && q.hi - q.lo <= ctx.config.slopeTolerance() + 1
					&& ctx.distanceToHeart(q.x0 + q.sizeX / 2, q.z0 + q.sizeZ / 2) <= BY_SQUARE + GREAT_REACH / 2) {
					p = q;
					big = rise;
					f = least[0];
					d = least[1];
				} else {
					// the lot on the square after all: the chapel stands there, its close no bigger than itself
					p = lot;
					f = p.frontage();
					d = p.depth();
				}
			}
			if (p == null) {
				big = new Parcels.Request(chapel, Placement.RISE, District.Kind.CIVIC).withSize(f, d);
				p = Parcels.best(ctx, big, null);
			}
			if (p == null || p.terrace) {
				f = Math.max(chapel.maxFrontage(), future.minFrontage());
				d = Math.max(chapel.maxDepth(), future.minDepth());
				big = new Parcels.Request(chapel, Placement.RISE, District.Kind.CIVIC).withSize(f, d);
				p = Parcels.best(ctx, big, null);
			}
			if (p == null && Expand.toward(ctx, Placement.RISE)) {
				p = Parcels.best(ctx, big, null);
			}
			if (p != null && !p.terrace) {
				// the chapel at the front, centred; the rest is its close
				int cf = Math.min(f, chapel.minFrontage() + (chapel.maxFrontage() - chapel.minFrontage()) / 2);
				int cd = Math.min(d, chapel.minDepth() + (chapel.maxDepth() - chapel.minDepth()) / 2);
				boolean alongX = p.facing.getAxis() == net.minecraft.core.Direction.Axis.Z;
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
				ctx.hold(new Reservation("close", p.x0, p.z0, p.x1(), p.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED));
				Build.building(ctx, chapel, c, reason + ", with a close kept round it", null);
				return true;
			}
		}
		// a plain chapel: on its lot, by the square where it fits, else on the rise
		if (lot != null && !lot.terrace && lot.frontage() <= chapel.maxFrontage() && lot.depth() <= chapel.maxDepth()) {
			Build.building(ctx, chapel, lot, reason + " on the square", null);
			return true;
		}
		Parcels.Parcel near = Parcels.best(ctx, new Parcels.Request(chapel, Placement.CIVIC, District.Kind.CIVIC), null);
		if (near != null && !near.terrace && ctx.distanceToHeart(near.x0 + near.sizeX / 2, near.z0 + near.sizeZ / 2) <= BY_SQUARE) {
			Build.building(ctx, chapel, near, reason + " by the square", null);
			return true;
		}
		return Growth.placeBuilding(ctx, chapel, Placement.RISE, District.Kind.CIVIC, reason, null);
	}

	/** Whether a chapel is planned or being built (so its close will take the cathedral once it stands). */
	private static boolean chapelRising(PlanContext ctx) {
		for (PlotView plot : ctx.settlement.plots()) {
			if (plot.spec().archetype().equals(Archetypes.CHAPEL) && plot.state() != PlotState.BUILT) {
				return true;
			}
		}
		return ctx.state.queue.stream().anyMatch(q -> q.spec().archetype().equals(Archetypes.CHAPEL));
	}

	/** A cathedral or temple on the chapel's site and its close. */
	private static boolean replaceChapel(PlanContext ctx, ArchetypeInfo info, String reason) {
		Reservation close = ctx.state.reservation("close");
		if (close == null) {
			return false;
		}
		for (PlotView plot : ctx.settlement.plots()) {
			PlotSpec old = plot.spec();
			if (!old.archetype().equals(Archetypes.CHAPEL) || plot.state() != PlotState.BUILT || Upgrades.replacing(ctx).contains(plot.id())) {
				continue;
			}
			int x0 = Math.min(close.x0(), old.origin().getX());
			int z0 = Math.min(close.z0(), old.origin().getZ());
			int x1 = Math.max(close.x1(), old.origin().getX() + old.sizeX() - 1);
			int z1 = Math.max(close.z1(), old.origin().getZ() + old.sizeZ() - 1);
			boolean alongX = old.facing().getAxis() == net.minecraft.core.Direction.Axis.Z;
			int front = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
			int depth = alongX ? z1 - z0 + 1 : x1 - x0 + 1;
			if (front < info.minFrontage() || depth < info.minDepth()) {
				return false;
			}
			CompoundTag params = old.params();
			params.putInt("replaces", plot.id());
			PlotSpec spec = new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, old.floorY(), z0), x1 - x0 + 1, z1 - z0 + 1,
				old.facing(), info.maxHeight(), Math.min(4, old.tier() + 1), params);
			ctx.state.removeReservation("close");
			Roads.issue(ctx, spec, plot.id(), reason + " in place of the chapel");
			return true;
		}
		return false;
	}
}
