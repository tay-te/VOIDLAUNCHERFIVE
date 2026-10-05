package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import net.minecraft.resources.Identifier;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.RoadKind;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Specs;
import dev.voidmc.hearth.plan.society.Stations;
import dev.voidmc.hearth.api.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Turns a found parcel into plot specs and queues them in build order: the terrace it stands on (if any), the garden
 * path to a set-back door, then the building with its params (PLANNING.md, "Building params").
 */
public final class Build {
	private Build() {
	}

	/** Queues a building on a parcel; returns its spec. */
	public static PlotSpec building(PlanContext ctx, ArchetypeInfo info, Parcels.Parcel p, String reason, @Nullable CompoundTag extra) {
		if (p.terrace && ctx.catalogue.has(Archetypes.TERRACE)) {
			Roads.issue(ctx, terrace(ctx, p.x0, p.z0, p.x1(), p.z1(), p.floorY, p.facing), "terrace: level ground for " + shortName(info));
		}
		if (p.doorGap > 0) {
			int[] ent = p.entrance();
			int sx = -p.facing.getStepX();
			int sz = -p.facing.getStepZ();
			// from the column next to the road to the one in front of the door
			int ax = ent[0] - sx * (p.doorGap - 1);
			int az = ent[1] - sz * (p.doorGap - 1);
			// level with the street: steps up to a raised door are the building's own
			int[] pts = {ax, p.streetY, az, ent[0], p.streetY, ent[1]};
			Roads.issue(ctx, Specs.road(ctx, Archetypes.ROAD, pts, 1, RoadKind.PATH), "path to the door of " + shortName(info));
		}
		CompoundTag params = extra == null ? new CompoundTag() : extra.copy();
		params.putString("zone", zoneName(p.zone));
		District d = ctx.district(p.x0 + p.sizeX / 2, p.z0 + p.sizeZ / 2);
		if (d != null) {
			params.putString("district", d.kind().getSerializedName());
		}
		params.putInt("street_y", p.streetY);
		if (p.gap > 0) {
			params.putInt("gap", p.gap);
		}
		if (p.backGap > 0) {
			params.putInt("back_gap", p.backGap);
		}
		if (p.cornerLeft) {
			params.putBoolean("corner_left", true);
		}
		if (p.cornerRight) {
			params.putBoolean("corner_right", true);
		}
		if (p.terrace) {
			params.putBoolean("terraced", true);
		}
		if (p.cut) {
			params.putBoolean("cut_into_slope", true);
		}
		if (info.flags().contains(SiteFlag.ATTACHABLE) && !params.contains("party_left")) {
			params.putBoolean("party_left", false);
			params.putBoolean("party_right", false);
		}
		Station station = station(params);
		if (info.category() == ArchetypeCategory.HOUSING && !params.contains("storeys")) {
			int storeys = storeys(ctx, info, p.zone);
			params.putInt("storeys", station == null ? storeys : Math.max(1, Math.min(storeys(ctx, info, 0) + 1, Stations.storeys(station, storeys))));
		}
		int finish = finish(ctx, info, p.zone, d);
		if (station != null) {
			finish = Math.max(0, Math.min(4, finish + Stations.finish(station)));
		}
		PlotSpec spec = new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(p.x0, p.floorY, p.z0), p.sizeX, p.sizeZ, p.facing,
			height(ctx, info, p.zone), finish, params);
		Roads.issue(ctx, spec, reason);
		return spec;
	}

	/** The station a plot's params name, or null. */
	public static @Nullable Station station(CompoundTag params) {
		String name = params.getStringOr("station", "");
		if (name.isEmpty()) {
			return null;
		}
		for (Station s : Station.values()) {
			if (s.getSerializedName().equals(name)) {
				return s;
			}
		}
		return null;
	}

	/** A terrace: a level platform at {@code level} with retaining walls on its downhill sides. */
	public static PlotSpec terrace(PlanContext ctx, int x0, int z0, int x1, int z1, int level, Direction facing) {
		CompoundTag p = new CompoundTag();
		p.putInt("level", level);
		var g = ctx.ground;
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int z = z0; z <= z1; z += 2) {
			for (int x = x0; x <= x1; x += 2) {
				lo = Math.min(lo, g.ground(x, z));
				hi = Math.max(hi, g.ground(x, z));
			}
		}
		// which sides stand above the land and need a retaining wall
		p.putBoolean("retain_north", lowerEdge(ctx, x0, z0, x1, z0, level));
		p.putBoolean("retain_south", lowerEdge(ctx, x0, z1, x1, z1, level));
		p.putBoolean("retain_west", lowerEdge(ctx, x0, z0, x0, z1, level));
		p.putBoolean("retain_east", lowerEdge(ctx, x1, z0, x1, z1, level));
		int bottom = Math.min(lo, level);
		return new PlotSpec(Archetypes.TERRACE, ctx.style, ctx.nextSeed(), new BlockPos(x0, level, z0), x1 - x0 + 1, z1 - z0 + 1, facing,
			Math.max(2, hi - level + 2), ctx.state.lastTier.ordinal(), withDepth(p, level - bottom));
	}

	private static CompoundTag withDepth(CompoundTag p, int depth) {
		p.putInt("depth", Math.max(0, depth));
		return p;
	}

	private static boolean lowerEdge(PlanContext ctx, int x0, int z0, int x1, int z1, int level) {
		int low = 0;
		int n = 0;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				n++;
				if (ctx.ground.ground(x, z) < level - 1) {
					low++;
				}
			}
		}
		return low * 3 >= n;
	}

	/** The plot's height cap: tall in the core and for towers, lower toward the edge so the skyline peaks at the heart. */
	public static int height(PlanContext ctx, ArchetypeInfo info, int zone) {
		int min = info.minHeight();
		int max = info.maxHeight();
		if (info.flags().contains(SiteFlag.VERTICAL) || info.category() == ArchetypeCategory.LANDMARK || info.category() == ArchetypeCategory.CIVIC) {
			return max;
		}
		double t = switch (zone) {
			case 0 -> 1.0;
			case 1 -> 0.75;
			default -> 0.5;
		};
		if (ctx.state.layout.dense) {
			t = Math.min(1, t + 0.2);
		}
		if (keepsItsHeight(info)) {
			// the skyline cap is for streets: a barn or farmhouse alone in the fields keeps its loft (arch-homestead.md §1),
			// the brewery its oast, the warehouse its hoist gable, the market hall its cupola (arch-trade.md §4,
			// arch-civic.md §4) wherever they stand
			t = Math.max(t, 0.75);
		}
		return min + (int) Math.round((max - min) * t);
	}

	/** Buildings that look their part only near the top of their height range, wherever they are placed. */
	static boolean keepsItsHeight(ArchetypeInfo info) {
		Identifier id = info.id();
		return info.flags().contains(SiteFlag.OUTSKIRTS) || id.equals(Archetypes.BREWERY) || id.equals(Archetypes.WAREHOUSE)
			|| id.equals(Archetypes.MARKET_HALL) || id.equals(Archetypes.INN);
	}

	/** Wealth and finish 0–4 from the settlement's tier, the district and the kind of building. */
	public static int finish(PlanContext ctx, ArchetypeInfo info, int zone, @Nullable District district) {
		int base = switch (ctx.tier) {
			case CAMP -> 0;
			case HAMLET, VILLAGE -> 1;
			case TOWN -> 2;
			case CITY -> 3;
		};
		if (ctx.tier == SettlementTier.VILLAGE && zone == 0) {
			base = 2;
		}
		ArchetypeCategory c = info.category();
		if (c == ArchetypeCategory.LANDMARK || c == ArchetypeCategory.CIVIC || info.id().equals(Archetypes.MANOR)
			|| district != null && district.kind() == District.Kind.RICH) {
			base++;
		}
		if (zone >= 3 || c == ArchetypeCategory.FARM) {
			base--;
		}
		return Math.max(0, Math.min(4, base));
	}

	public static int storeys(PlanContext ctx, ArchetypeInfo info, int zone) {
		if (info.flags().contains(SiteFlag.OUTSKIRTS)) {
			// a farmhouse in the fields: the steading and laithe houses of the timber and stone cultures are two storeys
			// (arch-homestead.md §2); the archetype drops one where its plot's height says so
			return info.maxHeight() >= 11 ? 2 : 1;
		}
		if (info.flags().contains(SiteFlag.VERTICAL)) {
			int s = 3 + (zone == 0 ? 1 : 0) + (ctx.tier == SettlementTier.CITY ? 1 : 0);
			return Math.max(3, Math.min(s, info.maxHeight() / 4));
		}
		if (zone == 0 && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal()) {
			return 2;
		}
		return info.maxHeight() >= 9 && zone <= 1 ? 2 : 1;
	}

	public static String zoneName(int zone) {
		return switch (zone) {
			case 0 -> "core";
			case 1 -> "inner";
			case 2 -> "outer";
			default -> "fringe";
		};
	}

	public static String shortName(ArchetypeInfo info) {
		String path = info.id().getPath();
		int slash = path.lastIndexOf('/');
		return (slash < 0 ? path : path.substring(slash + 1)).replace('_', ' ');
	}
}
