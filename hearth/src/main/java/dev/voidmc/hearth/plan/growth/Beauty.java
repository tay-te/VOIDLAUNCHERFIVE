package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * Once its needs are met a town makes itself pleasant: lamps at the square's corners, a fountain and a statue on it, a
 * garden on a spare plot in among the houses, a park on the hill or by the water when it is a city.
 */
public final class Beauty {
	private Beauty() {
	}

	static boolean plan(PlanContext ctx, Needs needs) {
		List<Identifier> order = new ArrayList<>();
		boolean square = ctx.state.reservation("square") != null && Square.paved(ctx);
		if (square && needs.count(Archetypes.LAMP_POST) < 4) {
			order.add(Archetypes.LAMP_POST);
		}
		if (square && needs.count(Archetypes.FOUNTAIN) < 1 && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal()) {
			order.add(Archetypes.FOUNTAIN);
		}
		if (square && needs.count(Archetypes.STATUE) < 1 && ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal()) {
			order.add(Archetypes.STATUE);
		}
		if (ctx.tier == SettlementTier.CITY && needs.count(Archetypes.PARK) < 2) {
			order.add(Archetypes.PARK);
		}
		// small squares where lanes meet in the town (the main square aside)
		if (ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && needs.count(Archetypes.PLAZA) < 2 + ctx.population / 60 && junctionSquare(ctx)) {
			return true;
		}
		order.add(Archetypes.GARDEN);
		for (Identifier id : order) {
			ArchetypeInfo info = ctx.catalogue.info(id);
			if (info == null || !Catalogue.allowed(info, ctx.culture, ctx.tier)) {
				continue;
			}
			if (Archetypes.isPlazaFurniture(id)) {
				if (Square.furniture(ctx, info, "beauty: a " + Build.shortName(info) + " on the square")) {
					return true;
				}
				continue;
			}
			Placement placement = id.equals(Archetypes.PARK) ? Placement.RICH : Placement.HOME;
			Parcels.Request req = new Parcels.Request(info, placement, id.equals(Archetypes.PARK) ? District.Kind.RICH : District.Kind.RESIDENTIAL);
			Parcels.Parcel p = Parcels.best(ctx, req, null);
			if (p != null && p.zone <= 2) {
				Build.building(ctx, info, p, "beauty: a " + Build.shortName(info) + " for the town", null);
				return true;
			}
		}
		return false;
	}

	/**
	 * A little square at a junction near the heart that has none yet: a plaza plot on the corner where the lanes meet,
	 * for a fountain, benches and a tree (params {@code junction}).
	 */
	private static boolean junctionSquare(PlanContext ctx) {
		ArchetypeInfo info = ctx.catalogue.info(Archetypes.PLAZA);
		if (info == null) {
			return false;
		}
		java.util.List<int[]> taken = new java.util.ArrayList<>();
		for (var p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(Archetypes.PLAZA)) {
				taken.add(new int[]{p.spec().origin().getX(), p.spec().origin().getZ()});
			}
		}
		var best = (dev.voidmc.hearth.plan.model.RoadGraph.Node) null;
		double bestD = ctx.coreRadius() * 1.1;
		for (var n : ctx.state.graph.nodes()) {
			if (n.kind() != dev.voidmc.hearth.plan.model.RoadGraph.NodeKind.JUNCTION) {
				continue;
			}
			double d = ctx.distanceToHeart(n.x(), n.z());
			if (d < ctx.state.plazaHalf + 14 || d >= bestD) {
				continue;
			}
			boolean far = true;
			for (int[] t : taken) {
				if (Math.hypot(t[0] - n.x(), t[1] - n.z()) < 36) {
					far = false;
					break;
				}
			}
			if (far) {
				bestD = d;
				best = n;
			}
		}
		if (best == null) {
			return false;
		}
		int size = Math.max(info.minFrontage(), Math.min(info.maxFrontage(), 7));
		Parcels.Request req = new Parcels.Request(info, Placement.HOME, null, 1, size, size, false, 0, 0).anchoredAt(best.x(), best.z());
		Parcels.Parcel p = Parcels.best(ctx, req, null);
		if (p == null || Math.hypot(p.x0 + size / 2.0 - best.x(), p.z0 + size / 2.0 - best.z()) > 12) {
			return false;
		}
		net.minecraft.nbt.CompoundTag params = new net.minecraft.nbt.CompoundTag();
		params.putBoolean("junction", true);
		Build.building(ctx, info, p, "beauty: a little square where the lanes meet", params);
		return true;
	}
}
