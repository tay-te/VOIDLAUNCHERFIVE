package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.RoadEdge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Homes for people without beds. The planner first finds where the next home would go (as near the heart as the
 * streets allow), reads the density band there, and only then chooses the house: attached rows on tight frontages in
 * the core (rowhouses, then townhouses and tenements as the tier rises, shophouses on the main street), the culture's
 * own detached house in the inner town, farmhouses at the edge, stilt houses along the boardwalks, cliff dwellings on
 * steep ground. In dense layouts a crowded core is rebuilt taller instead (see {@link Upgrades}).
 */
public final class Housing {
	private Housing() {
	}

	public static boolean plan(PlanContext ctx, Needs needs, int deficit) {
		LayoutType layout = ctx.state.layout;
		double vertical = layout.verticalBias * ctx.config.verticalBias();
		if (ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && ctx.random.nextDouble() < 0.18 * vertical && Upgrades.plan(ctx, needs, true)) {
			return true;
		}
		if (layout == LayoutType.STILT && ctx.random.nextDouble() < 0.65) {
			ArchetypeInfo stilt = ctx.catalogue.resolve(Archetypes.STILT_HOUSE, ctx.culture, ctx.tier);
			if (stilt != null && stilt.flags().contains(SiteFlag.ON_WATER)
				&& Growth.placeBuilding(ctx, stilt, Placement.ON_WATER, null, "housing: " + deficit + " without beds", null)) {
				return true;
			}
		}
		if ((layout == LayoutType.FARMSTEADS || ctx.state.signature.fieldFan) && ctx.population >= 6
			&& ctx.random.nextDouble() < (layout == LayoutType.FARMSTEADS ? 0.5 : 0.15) && Farms.farmstead(ctx)) {
			return true;
		}
		ArchetypeInfo probe = generic(ctx);
		if (probe == null) {
			return false;
		}
		Parcels.Parcel site = Parcels.best(ctx, new Parcels.Request(probe, Placement.HOME, District.Kind.RESIDENTIAL), null);
		boolean grew = false;
		if (site == null) {
			grew = Expand.town(ctx);
			site = Parcels.best(ctx, new Parcels.Request(probe, Placement.HOME, District.Kind.RESIDENTIAL), null);
			if (site == null) {
				return grew;
			}
		}
		// fill in near the heart before spreading out along the main roads
		if (!layout.expansive && ctx.distanceToHeart(site.x0 + site.sizeX / 2, site.z0 + site.sizeZ / 2) > ctx.coreRadius() * Growth.reach(Placement.HOME)
			&& Expand.town(ctx)) {
			grew = true;
			Parcels.Parcel nearer = Parcels.best(ctx, new Parcels.Request(probe, Placement.HOME, District.Kind.RESIDENTIAL), null);
			if (nearer != null && (!Parcels.stillClear(ctx, site) || ctx.distanceToHeart(nearer.x0, nearer.z0) < ctx.distanceToHeart(site.x0, site.z0))) {
				site = nearer;
			} else if (!Parcels.stillClear(ctx, site)) {
				return true;
			}
		}
		// dense towns rebuild a crowded core taller before they sprawl
		if (site.zone >= 2 && (layout.dense || vertical > 1.2) && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal()
			&& Upgrades.plan(ctx, needs, true)) {
			return true;
		}
		RoadEdge front = ctx.state.graph.edge(site.edge);
		ArchetypeInfo info = choose(ctx, site, front);
		if (info == null) {
			return grew;
		}
		String reason = "housing: " + deficit + " without beds";
		boolean attached = info.flags().contains(SiteFlag.ATTACHABLE) && (site.zone == 0 || layout.dense && site.zone <= 1);
		int edge = site.edge;
		Parcels.Request req = new Parcels.Request(info, Placement.HOME, District.Kind.RESIDENTIAL);
		if (attached) {
			int units = Math.max(2, Math.min(5, (int) Math.ceil(deficit / (double) Math.max(1, info.beds())) + 1));
			if (row(ctx, info, req.withGap(0), edge, units, reason)) {
				return true;
			}
		}
		Parcels.Parcel p = Parcels.best(ctx, req, e -> e == edge);
		if (p == null) {
			p = Parcels.best(ctx, req, null);
		}
		if (p == null) {
			return grew;
		}
		Build.building(ctx, info, p, reason, null);
		return true;
	}

	/** A plain detached house of the culture, used to find where the next home goes. */
	private static @Nullable ArchetypeInfo generic(PlanContext ctx) {
		for (var id : List.of(Archetypes.COTTAGE, Archetypes.HUT, Archetypes.ROUND_HOUSE, Archetypes.LONGHOUSE)) {
			ArchetypeInfo info = ctx.catalogue.resolve(id, ctx.culture, ctx.tier);
			if (info != null && info.category() == ArchetypeCategory.HOUSING) {
				return info;
			}
		}
		List<ArchetypeInfo> all = Growth.allowed(ctx, ArchetypeCategory.HOUSING);
		return all.isEmpty() ? null : all.get(0);
	}

	/** The house for a place, by its density band, its street and its ground. */
	static @Nullable ArchetypeInfo choose(PlanContext ctx, Parcels.Parcel site, RoadEdge front) {
		List<ArchetypeInfo> items = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		LayoutType layout = ctx.state.layout;
		int zone = site.zone;
		SettlementTier tier = ctx.tier;
		boolean main = front.role == RoadEdge.Role.ARM || front.role == RoadEdge.Role.SQUARE;
		double slope = ctx.ground.slope(site.x0 + site.sizeX / 2, site.z0 + site.sizeZ / 2, 4);
		int raised = site.floorY - ctx.state.heartY;
		double vertical = layout.verticalBias * ctx.config.verticalBias();
		for (ArchetypeInfo info : Growth.allowed(ctx, ArchetypeCategory.HOUSING)) {
			double w = info.weight();
			var id = info.id();
			if (id.equals(Archetypes.HUT)) {
				w *= tier == SettlementTier.CAMP ? 2.5 : tier == SettlementTier.HAMLET ? 0.5 : 0;
			} else if (id.equals(Archetypes.COTTAGE)) {
				w *= switch (zone) {
					case 0 -> layout.dense ? 0.15 : tier.ordinal() >= SettlementTier.TOWN.ordinal() ? 0.3 : 0.9;
					case 1 -> layout.dense ? 0.35 : 1.6;
					case 2 -> 1.3;
					default -> 0.8;
				};
			} else if (id.equals(Archetypes.ROWHOUSE)) {
				w *= zone == 0 ? 2.2 : zone == 1 && layout.dense ? 2.4 : zone == 1 ? 0.25 : 0;
			} else if (id.equals(Archetypes.TOWNHOUSE)) {
				w *= (zone == 0 ? 2.6 : zone == 1 && layout.dense ? 1.2 : 0) * vertical;
			} else if (id.equals(Archetypes.TENEMENT)) {
				w *= (zone == 0 ? 1.4 : 0) * vertical;
			} else if (id.equals(Archetypes.SHOPHOUSE)) {
				w *= main && zone <= 1 ? 1.6 : 0;
			} else if (id.equals(Archetypes.FARMHOUSE)) {
				// farmhouses belong out among the fields, never in a town's streets
				w *= layout.dense ? 0 : layout.expansive ? (zone >= 2 ? 3 : 0.4) : zone >= 3 ? 1.2 : 0;
			} else if (id.equals(Archetypes.MANOR) || id.equals(Archetypes.STILT_HOUSE)) {
				w = 0;
			} else if (id.equals(Archetypes.TOWER_HOUSE)) {
				w *= zone <= 1 ? 0.4 + (raised > 5 ? 1.5 : 0) * vertical : 0.1;
			} else if (id.equals(Archetypes.CLIFF_DWELLING)) {
				w *= slope > 0.45 || layout == LayoutType.CLIFF ? 3.5 : slope > 0.25 ? 1.2 : 0.05;
			} else if (!info.cultures().isEmpty()) {
				// the culture's own house: longhouses, round houses, courtyard houses
				w *= zone == 0 ? 0.8 : 2.2;
			}
			if (w > 0 && info.flags().contains(SiteFlag.ON_WATER)) {
				w = 0;
			}
			if (w > 0) {
				items.add(info);
				weights.add(w);
			}
		}
		return Growth.draw(ctx, items, weights);
	}

	/**
	 * A row of attached houses along a street: the first where the frontage search puts it, the rest flush beside it
	 * to its right (then left) while the street runs on, each floor meeting the street at its own door (a row steps
	 * down a hill), party walls between neighbours in the row.
	 */
	static boolean row(PlanContext ctx, ArchetypeInfo info, Parcels.Request req, int edge, int units, String reason) {
		Parcels.Parcel first = Parcels.best(ctx, req, e -> e == edge);
		if (first == null) {
			return false;
		}
		List<Parcels.Parcel> row = new ArrayList<>();
		row.add(first);
		// a tentative claim, so units do not overlap each other while the row is laid out
		ctx.occ.markRect(first.x0, first.z0, first.x1(), first.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
		Direction right = first.facing.getClockWise();
		for (int side = 0; side < 2 && row.size() < units; side++) {
			Direction step = side == 0 ? right : right.getOpposite();
			Parcels.Parcel last = side == 0 ? row.get(row.size() - 1) : row.get(0);
			while (row.size() < units) {
				int f = info.minFrontage() + (int) Math.round((info.maxFrontage() - info.minFrontage()) * (0.15 + 0.35 * ctx.hash(last.x0, last.z0, row.size())));
				boolean alongX = first.facing.getAxis() == Direction.Axis.Z;
				int sizeX = alongX ? f : last.sizeX;
				int sizeZ = alongX ? last.sizeZ : f;
				int x0 = last.x0;
				int z0 = last.z0;
				if (alongX) {
					x0 = step.getStepX() > 0 ? last.x1() + 1 : last.x0 - sizeX;
				} else {
					z0 = step.getStepZ() > 0 ? last.z1() + 1 : last.z0 - sizeZ;
				}
				Parcels.Parcel next = Parcels.fitAt(ctx, req, edge, x0, z0, sizeX, sizeZ, first.facing);
				if (next == null || Math.abs(next.floorY - last.floorY) > 3) {
					break;
				}
				ctx.occ.markRect(next.x0, next.z0, next.x1(), next.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
				if (side == 0) {
					row.add(next);
				} else {
					row.add(0, next);
				}
				last = next;
			}
		}
		for (Parcels.Parcel p : row) {
			ctx.occ.clearRect(p.x0, p.z0, p.x1(), p.z1(), dev.voidmc.hearth.plan.grid.Cell.RESERVED);
		}
		if (row.size() < 2) {
			return false;
		}
		// order the row from the building's own left to its right, so party walls line up
		boolean ascending = right.getStepX() + right.getStepZ() > 0;
		row.sort((a, b) -> ascending ? Integer.compare(a.x0 + a.z0, b.x0 + b.z0) : Integer.compare(b.x0 + b.z0, a.x0 + a.z0));
		for (int i = 0; i < row.size(); i++) {
			CompoundTag params = new CompoundTag();
			params.putBoolean("party_left", i > 0);
			params.putBoolean("party_right", i < row.size() - 1);
			params.putInt("row", row.size());
			Build.building(ctx, info, row.get(i), reason + (i == 0 ? "" : " (row)"), params);
		}
		return true;
	}
}
