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
 * garden on a spare plot in among the houses, a park on the hill or by the water when it is a city; and a town lights
 * its streets: a lamp at the junctions near the heart, along the main streets and inside its gates (ART_REVIEW_2 item 19).
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

	/** The lamps a town keeps beyond the square's four: one for about every eight souls, from town tier. */
	public static boolean lampsWanted(PlanContext ctx, Needs needs) {
		return ctx.tier.ordinal() >= SettlementTier.TOWN.ordinal() && ctx.catalogue.has(Archetypes.LAMP_POST) && Square.paved(ctx)
			&& needs.count(Archetypes.LAMP_POST) < 4 + ctx.population / 8;
	}

	/**
	 * A lamp on the corner where a new lane leaves its street, in a town's core: a 3 × 3 island just off the street's
	 * edge beside the lane's mouth, facing the street, when the corner is free and no lamp stands within
	 * {@link #LAMP_SPACING}. Called as the lane is built, so a town's junctions are lit as it grows (ART_REVIEW_2 item 19).
	 */
	static void cornerLamp(PlanContext ctx, dev.voidmc.hearth.plan.model.RoadEdge lane) {
		if (ctx.tier.ordinal() < SettlementTier.TOWN.ordinal() || lane.parent < 0 || lane.built <= 4
			|| ctx.ground.probe() instanceof dev.voidmc.hearth.plan.deep.RealmTerrain) {
			// (a deep town's lanes, grown by the same moves under the vault, are lit by its glow, not by street lamps)
			return;
		}
		ArchetypeInfo info = ctx.catalogue.info(Archetypes.LAMP_POST);
		if (info == null || !Catalogue.allowed(info, ctx.culture, ctx.tier)) {
			return;
		}
		var parent = ctx.state.graph.edge(lane.parent);
		var pd = parent.dense();
		int idx = Math.min(lane.parentIndex, pd.length() - 1);
		int jx = pd.xs()[idx];
		int jz = pd.zs()[idx];
		if (ctx.distanceToHeart(jx, jz) > ctx.coreRadius() * 1.3) {
			return;
		}
		var ld = lane.dense();
		int k = Math.min(4, ld.length() - 1);
		int hx = Integer.signum(ld.xs()[k] - ld.xs()[0]);
		int hz = Integer.signum(ld.zs()[k] - ld.zs()[0]);
		if (hx != 0 && hz != 0 || hx == 0 && hz == 0) {
			// a lane leaving at a slant has no square corner to light
			return;
		}
		for (var p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(Archetypes.LAMP_POST)
				&& Math.hypot(p.spec().origin().getX() - jx, p.spec().origin().getZ() - jz) < LAMP_SPACING) {
				return;
			}
		}
		net.minecraft.core.Direction facing = net.minecraft.core.Direction.getApproximateNearest(-hx, 0, -hz);
		int out = parent.width / 2 + 2;
		Parcels.Request req = new Parcels.Request(info, Placement.HOME, null, 0, 3, 3, false, 0, 0);
		for (int side = -1; side <= 1; side += 2) {
			// beside the lane's corridor (a column either side of its middle), just off the street's edge
			int cx = jx + hx * out + -hz * side * 3;
			int cz = jz + hz * out + hx * side * 3;
			Parcels.Parcel p = Parcels.fitAt(ctx, req, parent.id, cx - 1, cz - 1, 3, 3, facing);
			if (p != null && !p.terrace) {
				Build.building(ctx, info, p, "beauty: a lamp on the corner of the new lane", null);
				return;
			}
		}
	}

	/** How close two street lamps stand at most. */
	private static final int LAMP_SPACING = 14;

	/**
	 * A street lamp where the town's streets want light (by night they were dark between the pools of light at the
	 * doors): at the junctions nearest the heart, then along the main streets in town every twenty blocks or so, then just
	 * inside the gates; on the corner by the road, a lamp's paved island of 3 × 3, never within {@link #LAMP_SPACING} of
	 * another lamp. A few places are tried per call.
	 */
	static boolean lights(PlanContext ctx) {
		ArchetypeInfo info = ctx.catalogue.info(Archetypes.LAMP_POST);
		if (info == null || !Catalogue.allowed(info, ctx.culture, ctx.tier)) {
			return false;
		}
		List<int[]> lamps = new ArrayList<>();
		for (var p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(Archetypes.LAMP_POST)) {
				lamps.add(new int[]{p.spec().origin().getX(), p.spec().origin().getZ()});
			}
		}
		for (var q : ctx.state.queue) {
			if (q.spec().archetype().equals(Archetypes.LAMP_POST)) {
				lamps.add(new int[]{q.spec().origin().getX(), q.spec().origin().getZ()});
			}
		}
		double reach = ctx.coreRadius() * 1.3;
		List<double[]> spots = new ArrayList<>();
		for (var n : ctx.state.graph.nodes()) {
			double d = ctx.distanceToHeart(n.x(), n.z());
			if ((n.kind() == dev.voidmc.hearth.plan.model.RoadGraph.NodeKind.JUNCTION || n.kind() == dev.voidmc.hearth.plan.model.RoadGraph.NodeKind.GATE)
				&& d > ctx.state.plazaHalf + 4 && d < reach) {
				spots.add(new double[]{n.x(), n.z(), d});
			}
		}
		for (var e : ctx.state.graph.edges()) {
			if (e.role != dev.voidmc.hearth.plan.model.RoadEdge.Role.ARM) {
				continue;
			}
			var dense = e.dense();
			for (int i = 12; i < Math.min(e.built, dense.length()); i += 20) {
				double d = ctx.distanceToHeart(dense.xs()[i], dense.zs()[i]);
				if (d < reach) {
					// a little behind the junctions in the order: the crossings first
					spots.add(new double[]{dense.xs()[i], dense.zs()[i], d + 20});
				}
			}
		}
		for (var p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(Archetypes.GATEHOUSE)) {
				var s = p.spec();
				int cx = s.origin().getX() + s.sizeX() / 2;
				int cz = s.origin().getZ() + s.sizeZ() / 2;
				double d = ctx.distanceToHeart(cx, cz);
				double k = Math.max(0, d - Math.max(s.sizeX(), s.sizeZ()) / 2.0 - 5) / Math.max(1, d);
				spots.add(new double[]{ctx.heartX() + (cx - ctx.heartX()) * k, ctx.heartZ() + (cz - ctx.heartZ()) * k, d * 0.5});
			}
		}
		spots.sort(java.util.Comparator.comparingDouble((double[] a) -> a[2]).thenComparingDouble(a -> a[0]).thenComparingDouble(a -> a[1]));
		// places that would not take a lamp are passed over on the next calls (the need's failures turn the list on)
		int turn = spots.isEmpty() ? 0 : ctx.state.failures.getOrDefault("lights", 0) * 4 % spots.size();
		java.util.Collections.rotate(spots, -turn);
		int tried = 0;
		for (double[] spot : spots) {
			int x = (int) Math.round(spot[0]);
			int z = (int) Math.round(spot[1]);
			if (lamps.stream().anyMatch(l -> Math.hypot(l[0] - x, l[1] - z) < LAMP_SPACING)) {
				continue;
			}
			if (++tried > 4) {
				break;
			}
			int size = info.minFrontage();
			Parcels.Request req = new Parcels.Request(info, Placement.HOME, null, 0, size, info.minDepth(), false, 0, 0).anchoredAt(x, z);
			Parcels.Parcel p = Parcels.best(ctx, req, null);
			if (p == null || Math.hypot(p.x0 + p.sizeX / 2.0 - x, p.z0 + p.sizeZ / 2.0 - z) > 8) {
				continue;
			}
			Build.building(ctx, info, p, "beauty: a lamp to light the street", null);
			return true;
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
