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
				if (needs.count(info.id()) == 0) {
					return info;
				}
			}
			return null;
		}
		for (Identifier id : ctx.state.signature.landmarks) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info != null && Catalogue.allowed(info, ctx.culture, ctx.tier) && needs.count(id) == 0) {
				return info;
			}
		}
		for (ArchetypeInfo info : Growth.allowed(ctx, ArchetypeCategory.LANDMARK)) {
			if (needs.count(info.id()) == 0) {
				return info;
			}
		}
		return null;
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
			return Growth.placeBuilding(ctx, info, Placement.CIVIC, District.Kind.CIVIC, reason, null);
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
		if (id.equals(Archetypes.GREAT_HALL)) {
			return Growth.placeBuilding(ctx, info, Placement.CIVIC, District.Kind.CIVIC, reason, null);
		}
		boolean summit = site != null && site.summitY() > ctx.state.heartY + 8
			&& ctx.distanceToHeart(site.summitX(), site.summitZ()) < ctx.coreRadius() * 2.5 + 40;
		return Growth.placeBuilding(ctx, info, summit ? Placement.SUMMIT : Placement.RISE, District.Kind.CIVIC, reason, null);
	}

	/**
	 * The chapel, on the rise, with a close kept round it (beside and behind) big enough for the cathedral or temple a
	 * city will raise in its place; a plain chapel if no such ground is free.
	 */
	public static boolean chapel(PlanContext ctx, ArchetypeInfo chapel, String reason) {
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
			Parcels.Request big = new Parcels.Request(chapel, Placement.RISE, District.Kind.CIVIC).withSize(f, d);
			Parcels.Parcel p = Parcels.best(ctx, big, null);
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
				int cf = chapel.minFrontage() + (chapel.maxFrontage() - chapel.minFrontage()) / 2;
				int cd = chapel.minDepth() + (chapel.maxDepth() - chapel.minDepth()) / 2;
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
				ctx.state.reservations.add(new Reservation("close", p.x0, p.z0, p.x1(), p.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED));
				ctx.occ.markRect(p.x0, p.z0, p.x1(), p.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
				Build.building(ctx, chapel, c, reason + ", with a close kept round it", null);
				return true;
			}
		}
		return Growth.placeBuilding(ctx, chapel, Placement.RISE, District.Kind.CIVIC, reason, null);
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
