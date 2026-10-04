package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.growth.Parcels.Placement;
import dev.voidmc.hearth.plan.model.RoadEdge;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.road.Roads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * The ways into a town (LIVING_WORLD.md §4, §8.5): a watch house at every gate — beside the gatehouse inside a walled
 * town's wall, or where each main road meets the edge of an open one — and toll gates across the main roads in, a little
 * way out of town, where traffic coming in pays (a toll house over the road, {@code over_road}, like a gatehouse).
 */
public final class Gates {
	private Gates() {
	}

	static void needs(PlanContext ctx, Needs needs, List<Growth.Need> out) {
		if (!ctx.config.living().law()) {
			return;
		}
		ArchetypeInfo watch = Growth.exact(ctx, Archetypes.WATCH_HOUSE);
		if (watch != null && ctx.tier.ordinal() >= SettlementTier.VILLAGE.ordinal() && ctx.population >= 30) {
			int want = Math.min(watch.maxPerSettlement() > 0 ? watch.maxPerSettlement() : 6, gates(ctx).size());
			int have = needs.count(Archetypes.WATCH_HOUSE);
			if (have < want) {
				out.add(Growth.need("law/watch_house", 150 - have * 10, c -> watchHouse(c, watch)));
			}
		}
		ArchetypeInfo toll = Growth.exact(ctx, Archetypes.TOLL_GATE);
		if (toll != null && ctx.config.living().tollGates() && ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && ctx.population >= 80) {
			int want = Math.min(toll.maxPerSettlement() > 0 ? toll.maxPerSettlement() : 2, 2);
			int have = needs.count(Archetypes.TOLL_GATE);
			if (have < want) {
				out.add(Growth.need("law/toll_gate", 120 - have * 10, c -> tollGate(c, toll)));
			}
		}
	}

	/** A gate: where a main road enters the town, as {edge, dense index}. */
	record Gate(RoadEdge edge, int index) {
	}

	/** The town's gates: main roads where they cross the wall line, or where they reach the edge of town. */
	static List<Gate> gates(PlanContext ctx) {
		List<Gate> out = new ArrayList<>();
		double wall = ctx.wallLine();
		double at = wall > 0 ? wall - 6 : Parcels.edgeRing(ctx) * 0.85;
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.role != RoadEdge.Role.ARM || e.width < 3) {
				continue;
			}
			Polyline.Dense d = e.dense();
			int lim = Math.min(e.built, d.length());
			int best = -1;
			double bestD = Double.MAX_VALUE;
			for (int i = 4; i < lim; i++) {
				double dd = Math.abs(ctx.distanceToHeart(d.xs()[i], d.zs()[i]) - at);
				if (dd < bestD) {
					bestD = dd;
					best = i;
				}
			}
			if (best >= 0 && bestD < 12) {
				out.add(new Gate(e, best));
			}
		}
		return out;
	}

	/** A watch house at a gate that has none: beside the road, just inside. */
	private static boolean watchHouse(PlanContext ctx, ArchetypeInfo info) {
		List<int[]> existing = ctx.territory().positions(Archetypes.WATCH_HOUSE);
		for (Gate g : gates(ctx)) {
			Polyline.Dense d = g.edge.dense();
			int gx = d.xs()[g.index];
			int gz = d.zs()[g.index];
			boolean manned = false;
			for (int[] w : existing) {
				if (Math.hypot(w[0] - gx, w[1] - gz) < 26) {
					manned = true;
					break;
				}
			}
			if (manned) {
				continue;
			}
			int edgeId = g.edge.id;
			Parcels.Request req = new Parcels.Request(info, Placement.SITED, null).sited(Siting.near(gx, gz, 16));
			Parcels.Parcel p = Parcels.best(ctx, req, e -> e == edgeId);
			if (p != null && Math.hypot(p.x0 + p.sizeX / 2.0 - gx, p.z0 + p.sizeZ / 2.0 - gz) < 22) {
				Build.building(ctx, info, p, "law: a watch house at the gate on the " + Expand.describe(g.edge), null);
				return true;
			}
		}
		return false;
	}

	/** A toll gate across a main road a little way out of town, where nothing stands beside it. */
	private static boolean tollGate(PlanContext ctx, ArchetypeInfo info) {
		List<int[]> existing = ctx.territory().positions(Archetypes.TOLL_GATE);
		double from = Math.max(Parcels.edgeRing(ctx), ctx.wallLine()) + 22;
		// the longest roads first: the trade roads
		List<RoadEdge> arms = new ArrayList<>();
		for (RoadEdge e : ctx.state.graph.edges()) {
			if (e.role == RoadEdge.Role.ARM && e.width >= 3) {
				arms.add(e);
			}
		}
		arms.sort((a, b) -> Integer.compare(Math.min(b.built, b.length()), Math.min(a.built, a.length())));
		for (RoadEdge e : arms) {
			Polyline.Dense d = e.dense();
			int lim = Math.min(e.built, d.length()) - 4;
			for (int i = 4; i < lim; i++) {
				double dist = ctx.distanceToHeart(d.xs()[i], d.zs()[i]);
				if (dist < from || dist > from + 40) {
					continue;
				}
				boolean near = false;
				for (int[] t : existing) {
					if (Math.hypot(t[0] - d.xs()[i], t[1] - d.zs()[i]) < 60) {
						near = true;
						break;
					}
				}
				if (near) {
					break;
				}
				PlotSpec spec = overRoad(ctx, info, e, i);
				if (spec != null) {
					Roads.issue(ctx, spec, "law: a toll gate on the " + Expand.describe(e) + " into town");
					markBeside(ctx, spec);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A plot standing over a road at a dense index (the road runs through it along its facing, outward from the heart):
	 * a gatehouse or a toll gate. Null where the road bends there, climbs, or something stands beside it.
	 */
	static @Nullable PlotSpec overRoad(PlanContext ctx, ArchetypeInfo info, RoadEdge e, int i) {
		Polyline.Dense d = e.dense();
		int a = Math.max(0, i - 3);
		int b = Math.min(d.length() - 1, i + 3);
		int tx = d.xs()[b] - d.xs()[a];
		int tz = d.zs()[b] - d.zs()[a];
		// straight and level through the building
		if (Math.min(Math.abs(tx), Math.abs(tz)) > 0 || Math.abs(d.ys()[b] - d.ys()[a]) > 1 || e.typeAt(i) != RoadEdge.GRADE) {
			return null;
		}
		int ox = d.xs()[i] - ctx.heartX();
		int oz = d.zs()[i] - ctx.heartZ();
		Direction facing = Math.abs(tx) >= Math.abs(tz) ? (ox >= 0 ? Direction.EAST : Direction.WEST) : (oz >= 0 ? Direction.SOUTH : Direction.NORTH);
		int front = Math.max(info.minFrontage(), Math.min(info.maxFrontage(), e.width + 4));
		int depth = info.minDepth();
		int sizeX = facing.getAxis() == Direction.Axis.Z ? front : depth;
		int sizeZ = facing.getAxis() == Direction.Axis.Z ? depth : front;
		int x0 = d.xs()[i] - sizeX / 2;
		int z0 = d.zs()[i] - sizeZ / 2;
		for (int zz = z0; zz < z0 + sizeZ; zz++) {
			for (int xx = x0; xx < x0 + sizeX; xx++) {
				byte c = ctx.occ.get(xx, zz);
				if (!(Cell.isAccess(c) || Cell.isBuildable(c) || c == Cell.PLANNED_ROAD) || ctx.ground.isWater(xx, zz)) {
					return null;
				}
			}
		}
		CompoundTag params = new CompoundTag();
		params.putInt("passage_width", e.width);
		params.putBoolean("over_road", true);
		params.putInt("street_y", d.ys()[i]);
		return new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(x0, d.ys()[i], z0), sizeX, sizeZ, facing, info.maxHeight(),
			Build.finish(ctx, info, 2, null), params);
	}

	/** Marks the parts of an over-road plot beside the road as taken: the road keeps running through it. */
	static void markBeside(PlanContext ctx, PlotSpec spec) {
		int x0 = spec.origin().getX();
		int z0 = spec.origin().getZ();
		for (int zz = z0; zz < z0 + spec.sizeZ(); zz++) {
			for (int xx = x0; xx < x0 + spec.sizeX(); xx++) {
				if (!Cell.isAccess(ctx.occ.get(xx, zz))) {
					ctx.occ.mark(xx, zz, Cell.BUILDING);
				}
			}
		}
	}
}
