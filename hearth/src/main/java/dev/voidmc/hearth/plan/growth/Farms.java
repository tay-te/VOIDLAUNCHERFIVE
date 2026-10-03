package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.RoadEdge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Food: fields, orchards, pastures, paddies and terrace fields just outside the built-up town, chosen by culture and
 * land; and farmsteads — a farmhouse far out along a track with its fields round it — which is how an expansive
 * settlement spreads over hundreds of blocks.
 */
public final class Farms {
	private Farms() {
	}

	public static boolean plan(PlanContext ctx, Needs needs) {
		ArchetypeInfo info = choose(ctx);
		if (info == null) {
			return false;
		}
		if (ctx.state.layout == LayoutType.FARMSTEADS || ctx.state.signature.fieldFan) {
			int[] anchor = farmsteadWithFewestFields(ctx);
			if (anchor != null && place(ctx, info, anchor)) {
				return true;
			}
		}
		return place(ctx, info, null);
	}

	/** Places a farm plot at the edge of town, or round an anchor (a farmhouse). */
	static boolean place(PlanContext ctx, ArchetypeInfo info, int @Nullable [] anchor) {
		String reason = "food: a " + Build.shortName(info) + " to feed " + ctx.population;
		if (anchor != null) {
			Parcels.Request req = new Parcels.Request(info, Placement.EDGE, District.Kind.FARMS).anchoredAt(anchor[0], anchor[1]);
			Parcels.Parcel p = Parcels.best(ctx, req, null);
			if (p != null && Math.hypot(p.x0 - anchor[0], p.z0 - anchor[1]) < 70) {
				Build.building(ctx, info, p, reason, null);
				return true;
			}
		}
		return Growth.placeBuilding(ctx, info, Placement.EDGE, District.Kind.FARMS, reason, null);
	}

	/** The farm plot a settlement of this culture on this land lays out next. */
	static @Nullable ArchetypeInfo choose(PlanContext ctx) {
		List<ArchetypeInfo> items = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double slope = ctx.state.site == null ? 0 : ctx.state.site.slope();
		Culture c = ctx.culture;
		for (ArchetypeInfo info : Growth.allowed(ctx, ArchetypeCategory.FARM)) {
			var id = info.id();
			double w = info.weight();
			if (id.equals(Archetypes.FIELD)) {
				w *= 3;
			} else if (id.equals(Archetypes.ORCHARD)) {
				w *= Growth.isCulture(ctx, Culture.TEMPERATE, Culture.MEDITERRANEAN, Culture.WISTERIA) ? 2 : 0.7;
			} else if (id.equals(Archetypes.PASTURE)) {
				w *= Growth.isCulture(ctx, Culture.STEPPE, Culture.MOORLAND, Culture.ALPINE, Culture.TUNDRA, Culture.NORTHERN) ? 2.5 : 1;
			} else if (id.equals(Archetypes.PADDY)) {
				w *= 3;
			} else if (id.equals(Archetypes.TERRACE_FIELD)) {
				w *= ctx.state.layout.terraces || slope > 0.15 ? 3 : 0.2;
			}
			if (c == Culture.DESERT && !id.equals(Archetypes.FIELD) && !id.equals(Archetypes.ORCHARD)) {
				w *= 0.3;
			}
			items.add(info);
			weights.add(w);
		}
		return Growth.draw(ctx, items, weights);
	}

	/**
	 * A farmstead: a farmhouse well out along a track or main road, then a field or two beside it (and the track out to
	 * it, if the network does not reach that far yet).
	 */
	public static boolean farmstead(PlanContext ctx) {
		ArchetypeInfo house = ctx.catalogue.resolve(Archetypes.FARMHOUSE, ctx.culture, ctx.tier);
		if (house == null || house.category() != ArchetypeCategory.HOUSING) {
			return false;
		}
		Parcels.Request req = new Parcels.Request(house, Placement.FARMSTEAD, District.Kind.FARMS).withGap(5);
		Parcels.Parcel p = spacedFarmstead(ctx, req);
		if (p == null) {
			if (!Expand.toward(ctx, Placement.FARMSTEAD)) {
				return false;
			}
			p = spacedFarmstead(ctx, req);
			if (p == null) {
				return true;
			}
		}
		Build.building(ctx, house, p, "housing: a farmstead out along the " + Expand.describe(ctx.state.graph.edge(p.edge)), null);
		ArchetypeInfo field = choose(ctx);
		if (field != null) {
			int fields = 1 + ctx.random.nextInt(2);
			int[] anchor = {p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2};
			for (int i = 0; i < fields; i++) {
				Parcels.Request fr = new Parcels.Request(field, Placement.EDGE, District.Kind.FARMS).anchoredAt(anchor[0], anchor[1]);
				Parcels.Parcel fp = Parcels.best(ctx, fr, null);
				if (fp == null || Math.hypot(fp.x0 - anchor[0], fp.z0 - anchor[1]) > 60) {
					break;
				}
				Build.building(ctx, field, fp, "food: the farmstead's " + Build.shortName(field), null);
			}
		}
		return true;
	}

	/** A farmstead site at least 50 blocks from any other farmhouse. */
	private static Parcels.@Nullable Parcel spacedFarmstead(PlanContext ctx, Parcels.Request req) {
		List<int[]> houses = farmhouses(ctx);
		for (Parcels.Parcel p : Parcels.find(ctx, req, null, 12)) {
			boolean far = true;
			for (int[] h : houses) {
				if (Math.hypot(h[0] - p.x0, h[1] - p.z0) < 50) {
					far = false;
					break;
				}
			}
			if (far && ctx.distanceToHeart(p.x0, p.z0) > ctx.coreRadius() * 1.4) {
				return p;
			}
		}
		return null;
	}

	private static List<int[]> farmhouses(PlanContext ctx) {
		List<int[]> out = new ArrayList<>();
		for (PlotView plot : ctx.settlement.plots()) {
			if (plot.spec().archetype().equals(Archetypes.FARMHOUSE)) {
				out.add(new int[]{plot.spec().origin().getX(), plot.spec().origin().getZ()});
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (q.spec().archetype().equals(Archetypes.FARMHOUSE)) {
				out.add(new int[]{q.spec().origin().getX(), q.spec().origin().getZ()});
			}
		}
		return out;
	}

	/** The farmhouse with the fewest farm plots within 60 blocks (where the next field goes), or null. */
	private static int @Nullable [] farmsteadWithFewestFields(PlanContext ctx) {
		List<int[]> houses = farmhouses(ctx);
		if (houses.isEmpty()) {
			return null;
		}
		int[] best = null;
		int bestCount = Integer.MAX_VALUE;
		for (int[] h : houses) {
			int count = 0;
			for (PlotView plot : ctx.settlement.plots()) {
				ArchetypeInfo info = ctx.info(plot.spec().archetype());
				if (info != null && info.category() == ArchetypeCategory.FARM
					&& Math.hypot(plot.spec().origin().getX() - h[0], plot.spec().origin().getZ() - h[1]) < 60) {
					count++;
				}
			}
			if (count < bestCount && count < 4) {
				bestCount = count;
				best = h;
			}
		}
		return best;
	}

	static boolean isFarm(@Nullable ArchetypeInfo info) {
		return info != null && (info.category() == ArchetypeCategory.FARM || info.flags().contains(SiteFlag.OUTSKIRTS));
	}

	static CompoundTag none() {
		return new CompoundTag();
	}

	static boolean onTrack(PlanContext ctx, Parcels.Parcel p) {
		return ctx.state.graph.edge(p.edge).role == RoadEdge.Role.TRACK;
	}
}
