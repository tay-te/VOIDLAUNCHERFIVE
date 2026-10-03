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
}
