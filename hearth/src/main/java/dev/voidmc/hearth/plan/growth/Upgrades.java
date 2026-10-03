package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.road.Roads;
import dev.voidmc.hearth.plan.road.Specs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Growing up (PLANNING.md, "Verticality"): as the tier rises, houses in the core are rebuilt as taller ones on the
 * same ground — a run of cottages along a street becomes a terrace of townhouses with party walls (the gaps between
 * them taken into the new frontages), townhouses become tenements in a city — so a village becomes a dense, tall
 * town without spreading. Where two tall rebuilds face each other across a narrow street, a walkway joins them above
 * it. Each rebuild is a project with {@code replaces} set to the plot it replaces.
 */
public final class Upgrades {
	private static final int MAX_BATCH = 4;

	private Upgrades() {
	}

	public static boolean plan(PlanContext ctx, Needs needs, boolean forHousing) {
		Set<Integer> replacing = replacing(ctx);
		List<PlotView> plots = new ArrayList<>(ctx.settlement.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		int maxZone = ctx.tier == SettlementTier.CITY ? 2 : ctx.tier == SettlementTier.TOWN ? 1 : 0;
		PlotView best = null;
		ArchetypeInfo bestTarget = null;
		double bestD = Double.MAX_VALUE;
		for (PlotView p : plots) {
			if (p.state() != PlotState.BUILT || p.owner() != null || replacing.contains(p.id())) {
				continue;
			}
			PlotSpec s = p.spec();
			int cx = s.origin().getX() + s.sizeX() / 2;
			int cz = s.origin().getZ() + s.sizeZ() / 2;
			double wall = ctx.wallLine();
			if (wall > 0 ? ctx.distanceToHeart(cx, cz) > wall : ctx.zone(cx, cz) > maxZone) {
				continue;
			}
			ArchetypeInfo target = target(ctx, s);
			if (target == null) {
				continue;
			}
			double d = ctx.distanceToHeart(cx, cz) + ctx.hash(cx, cz, 31) * 6;
			if (d < bestD) {
				bestD = d;
				best = p;
				bestTarget = target;
			}
		}
		if (best == null) {
			return false;
		}
		List<PlotView> batch = batch(ctx, best, bestTarget, plots, replacing);
		return rebuild(ctx, batch, bestTarget);
	}

	/** Plots already being replaced: by a queued project, or by a plot handed out but not built yet. */
	static Set<Integer> replacing(PlanContext ctx) {
		Set<Integer> out = new HashSet<>();
		for (QueuedProject q : ctx.state.queue) {
			if (q.replaces() >= 0) {
				out.add(q.replaces());
			}
		}
		for (PlotView p : ctx.settlement.plots()) {
			int r = p.spec().params().getIntOr("replaces", -1);
			if (r >= 0) {
				out.add(r);
			}
		}
		return out;
	}

	/** What a house could be rebuilt as: a taller archetype it upgrades to, allowed here and fitting its ground. */
	static @Nullable ArchetypeInfo target(PlanContext ctx, PlotSpec s) {
		ArchetypeInfo info = ctx.info(s.archetype());
		if (info == null || info.category() != ArchetypeCategory.HOUSING || info.upgradesTo().isEmpty()) {
			return null;
		}
		ArchetypeInfo best = null;
		for (Identifier id : info.upgradesTo()) {
			ArchetypeInfo t = ctx.catalogue.info(id);
			if (t == null || !Catalogue.allowed(t, ctx.culture, ctx.tier) || t.category() != ArchetypeCategory.HOUSING) {
				continue;
			}
			int front = s.frontage();
			int depth = s.depth();
			if (front + 3 < t.minFrontage() || depth + 4 < t.minDepth()) {
				continue;
			}
			// prefer the tallest the tier allows
			if (best == null || t.maxHeight() > best.maxHeight()) {
				best = t;
			}
		}
		return best;
	}

	/** The plot and its neighbours along the same street side that can be rebuilt with it, left to right. */
	private static List<PlotView> batch(PlanContext ctx, PlotView first, ArchetypeInfo target, List<PlotView> plots, Set<Integer> replacing) {
		List<PlotView> batch = new ArrayList<>();
		batch.add(first);
		Direction facing = first.spec().facing();
		Direction right = facing.getClockWise();
		for (int side = 0; side < 2 && batch.size() < MAX_BATCH; side++) {
			Direction step = side == 0 ? right : right.getOpposite();
			PlotView edge = side == 0 ? batch.get(batch.size() - 1) : batch.get(0);
			boolean found = true;
			while (found && batch.size() < MAX_BATCH) {
				found = false;
				for (PlotView p : plots) {
					if (batch.contains(p) || p.state() != PlotState.BUILT || p.owner() != null || replacing.contains(p.id())
						|| p.spec().facing() != facing || target(ctx, p.spec()) != target) {
						continue;
					}
					int gap = gapAlong(edge.spec(), p.spec(), step);
					if (gap >= 0 && gap <= 3 && sameFront(edge.spec(), p.spec())) {
						if (side == 0) {
							batch.add(p);
						} else {
							batch.add(0, p);
						}
						edge = p;
						found = true;
						break;
					}
				}
			}
		}
		return batch;
	}

	/** Blocks between {@code a}'s side and {@code b}'s side in direction {@code step}, or -1 if b is not there. */
	private static int gapAlong(PlotSpec a, PlotSpec b, Direction step) {
		int ax0 = a.origin().getX();
		int az0 = a.origin().getZ();
		int ax1 = ax0 + a.sizeX() - 1;
		int az1 = az0 + a.sizeZ() - 1;
		int bx0 = b.origin().getX();
		int bz0 = b.origin().getZ();
		int bx1 = bx0 + b.sizeX() - 1;
		int bz1 = bz0 + b.sizeZ() - 1;
		return switch (step) {
			case EAST -> overlapZ(az0, az1, bz0, bz1) ? bx0 - ax1 - 1 : -1;
			case WEST -> overlapZ(az0, az1, bz0, bz1) ? ax0 - bx1 - 1 : -1;
			case SOUTH -> overlapZ(ax0, ax1, bx0, bx1) ? bz0 - az1 - 1 : -1;
			default -> overlapZ(ax0, ax1, bx0, bx1) ? az0 - bz1 - 1 : -1;
		};
	}

	private static boolean overlapZ(int a0, int a1, int b0, int b1) {
		return a0 <= b1 && b0 <= a1;
	}

	/** Whether two plots facing the same way have their fronts on the same line (within a block). */
	private static boolean sameFront(PlotSpec a, PlotSpec b) {
		return switch (a.facing()) {
			case NORTH -> Math.abs(a.origin().getZ() - b.origin().getZ()) <= 1;
			case SOUTH -> Math.abs(a.origin().getZ() + a.sizeZ() - b.origin().getZ() - b.sizeZ()) <= 1;
			case WEST -> Math.abs(a.origin().getX() - b.origin().getX()) <= 1;
			default -> Math.abs(a.origin().getX() + a.sizeX() - b.origin().getX() - b.sizeX()) <= 1;
		};
	}

	private static boolean rebuild(PlanContext ctx, List<PlotView> batch, ArchetypeInfo target) {
		Direction facing = batch.get(0).spec().facing();
		boolean alongX = facing.getAxis() == Direction.Axis.Z;
		Direction right = facing.getClockWise();
		boolean ascending = right.getStepX() + right.getStepZ() > 0;
		batch.sort(Comparator.comparingInt((PlotView p) -> {
			int v = alongX ? p.spec().origin().getX() : p.spec().origin().getZ();
			return ascending ? v : -v;
		}));
		List<int[]> rects = new ArrayList<>();
		for (PlotView p : batch) {
			PlotSpec s = p.spec();
			rects.add(new int[]{s.origin().getX(), s.origin().getZ(), s.origin().getX() + s.sizeX() - 1, s.origin().getZ() + s.sizeZ() - 1});
		}
		// take the gap to the next house in the row into this one's frontage, so the row closes up
		boolean[] party = new boolean[batch.size()];
		for (int i = 0; i + 1 < rects.size(); i++) {
			int[] a = rects.get(i);
			int[] b = rects.get(i + 1);
			int front = alongX ? a[2] - a[0] + 1 : a[3] - a[1] + 1;
			int gap = alongX ? (ascending ? b[0] - a[2] - 1 : a[0] - b[2] - 1) : (ascending ? b[1] - a[3] - 1 : a[1] - b[3] - 1);
			if (gap > 0 && front + gap <= target.maxFrontage() && gapOpen(ctx, a, b, alongX, ascending, gap)) {
				if (alongX) {
					if (ascending) {
						a[2] += gap;
					} else {
						a[0] -= gap;
					}
				} else {
					if (ascending) {
						a[3] += gap;
					} else {
						a[1] -= gap;
					}
				}
				gap = 0;
			}
			party[i] = gap == 0;
		}
		List<PlotSpec> specs = new ArrayList<>();
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < batch.size(); i++) {
			PlotView p = batch.get(i);
			PlotSpec old = p.spec();
			int[] r = fit(rects.get(i), facing, target, alongX, ascending);
			if (r == null) {
				continue;
			}
			CompoundTag params = new CompoundTag();
			CompoundTag oldParams = old.params();
			for (String key : List.of("zone", "district", "street_y", "corner_left", "corner_right", "terraced")) {
				if (oldParams.contains(key)) {
					params.put(key, oldParams.get(key).copy());
				}
			}
			params.putInt("replaces", p.id());
			boolean attach = target.flags().contains(SiteFlag.ATTACHABLE);
			if (attach) {
				params.putBoolean("party_left", i > 0 && party[i - 1]);
				params.putBoolean("party_right", i < batch.size() - 1 && party[i]);
			}
			int zone = ctx.zone(r[0], r[1]);
			params.putInt("storeys", Build.storeys(ctx, target, zone));
			PlotSpec spec = new PlotSpec(target.id(), ctx.style, ctx.nextSeed(), new BlockPos(r[0], old.floorY(), r[1]), r[2] - r[0] + 1, r[3] - r[1] + 1,
				facing, Build.height(ctx, target, 0), Math.min(4, Math.max(old.tier(), Build.finish(ctx, target, zone, ctx.district(r[0], r[1])))), params);
			specs.add(spec);
			ids.add(p.id());
		}
		if (specs.isEmpty()) {
			return false;
		}
		String from = Build.shortName(ctx.info(batch.get(0).spec().archetype()));
		String reason = "growing up: " + specs.size() + " " + from + (specs.size() > 1 ? "s" : "") + " rebuilt as " + Build.shortName(target)
			+ (specs.size() > 1 ? "s" : "") + " (" + ctx.tier.getSerializedName() + " core)";
		Walkway walk = walkway(ctx, specs.get(0), target, ids);
		if (walk != null) {
			specs.set(0, withParam(specs.get(0), "walkway_y", walk.deck));
		}
		for (int i = 0; i < specs.size(); i++) {
			Roads.issue(ctx, specs.get(i), ids.get(i), reason);
		}
		if (walk != null) {
			Roads.issue(ctx, walk.other, walk.otherId, "growing up: rebuilt taller to meet a walkway across the street");
			Roads.issue(ctx, walk.spec, "walkway: joining tall houses across the street");
		}
		return true;
	}

	private static PlotSpec withParam(PlotSpec s, String key, int value) {
		CompoundTag p = s.params();
		p.putInt(key, value);
		return new PlotSpec(s.archetype(), s.style(), s.seed(), s.origin(), s.sizeX(), s.sizeZ(), s.facing(), s.maxHeight(), s.tier(), p);
	}

	private record Walkway(PlotSpec spec, PlotSpec other, int otherId, int deck) {
	}

	/**
	 * An elevated walkway across the street between a rebuilt tall house and the house facing it, which is rebuilt
	 * tall too (both get a door at the walkway's level, params {@code walkway_y}) — when the town's habit is to join
	 * them: forest halls, hill towns, garden towns, karst cliff towns, or any city's dense core.
	 */
	private static @Nullable Walkway walkway(PlanContext ctx, PlotSpec a, ArchetypeInfo target, List<Integer> batchIds) {
		if (!target.flags().contains(SiteFlag.VERTICAL) || !ctx.catalogue.has(dev.voidmc.hearth.plan.catalogue.Archetypes.WALKWAY)) {
			return null;
		}
		boolean habit = ctx.state.signature.walkways || ctx.state.layout.dense && ctx.tier == SettlementTier.CITY;
		if (!habit) {
			return null;
		}
		int[] ent = dev.voidmc.hearth.plan.grid.PlotGeometry.entrance(a);
		Direction f = a.facing();
		// across the street: road columns, then the front of the house opposite within nine blocks
		int x = ent[0];
		int z = ent[1];
		int k = 0;
		while (k < 9 && Cell.isAccess(ctx.occ.get(x, z))) {
			x += f.getStepX();
			z += f.getStepZ();
			k++;
		}
		if (k < 3 || k >= 9 || ctx.occ.get(x, z) != Cell.BUILDING) {
			return null;
		}
		Set<Integer> replacing = replacing(ctx);
		replacing.addAll(batchIds);
		for (PlotView p : ctx.settlement.plots()) {
			PlotSpec s = p.spec();
			if (x < s.origin().getX() || x >= s.origin().getX() + s.sizeX() || z < s.origin().getZ() || z >= s.origin().getZ() + s.sizeZ()) {
				continue;
			}
			if (s.facing() != f.getOpposite() || p.state() != PlotState.BUILT || p.owner() != null || replacing.contains(p.id())) {
				return null;
			}
			ArchetypeInfo t = target(ctx, s);
			if (t == null || !t.flags().contains(SiteFlag.VERTICAL)) {
				return null;
			}
			int[] r = fit(new int[]{s.origin().getX(), s.origin().getZ(), s.origin().getX() + s.sizeX() - 1, s.origin().getZ() + s.sizeZ() - 1},
				s.facing(), t, s.facing().getAxis() == Direction.Axis.Z, true);
			if (r == null) {
				return null;
			}
			int deck = Math.max(a.floorY(), s.floorY()) + 8;
			int street = Math.min(a.floorY(), s.floorY());
			CompoundTag params = new CompoundTag();
			CompoundTag old = s.params();
			for (String key : List.of("zone", "district", "street_y", "corner_left", "corner_right", "terraced")) {
				if (old.contains(key)) {
					params.put(key, old.get(key).copy());
				}
			}
			params.putInt("replaces", p.id());
			params.putInt("walkway_y", deck);
			if (t.flags().contains(SiteFlag.ATTACHABLE)) {
				params.putBoolean("party_left", false);
				params.putBoolean("party_right", false);
			}
			params.putInt("storeys", Build.storeys(ctx, t, 0));
			PlotSpec other = new PlotSpec(t.id(), ctx.style, ctx.nextSeed(), new BlockPos(r[0], s.floorY(), r[1]), r[2] - r[0] + 1, r[3] - r[1] + 1,
				s.facing(), Build.height(ctx, t, 0), Math.min(4, s.tier() + 1), params);
			int[] otherEnt = dev.voidmc.hearth.plan.grid.PlotGeometry.entrance(other);
			int[] pts = {ent[0], deck, ent[1], otherEnt[0], deck, otherEnt[1]};
			if (Math.abs(otherEnt[0] - ent[0]) > 0 && Math.abs(otherEnt[1] - ent[1]) > 0) {
				// the two doors are not straight across: run the walkway square to the street from the first
				pts = new int[]{ent[0], deck, ent[1], x - f.getStepX(), deck, z - f.getStepZ()};
			}
			return new Walkway(Specs.walkway(ctx, pts, 3, deck, street), other, p.id(), deck);
		}
		return null;
	}

	private static boolean gapOpen(PlanContext ctx, int[] a, int[] b, boolean alongX, boolean ascending, int gap) {
		int x0;
		int z0;
		int x1;
		int z1;
		if (alongX) {
			x0 = ascending ? a[2] + 1 : b[2] + 1;
			x1 = x0 + gap - 1;
			z0 = Math.max(a[1], b[1]);
			z1 = Math.min(a[3], b[3]);
		} else {
			z0 = ascending ? a[3] + 1 : b[3] + 1;
			z1 = z0 + gap - 1;
			x0 = Math.max(a[0], b[0]);
			x1 = Math.min(a[2], b[2]);
		}
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				byte c = ctx.occ.get(x, z);
				if (c != Cell.FREE && c != Cell.SETBACK) {
					return false;
				}
			}
		}
		return true;
	}

	/** Clamps a rectangle to the target's frontage and depth ranges (shrinking from the right and the back), or null. */
	private static int @Nullable [] fit(int[] r, Direction facing, ArchetypeInfo t, boolean alongX, boolean ascending) {
		int[] o = r.clone();
		int front = alongX ? o[2] - o[0] + 1 : o[3] - o[1] + 1;
		int depth = alongX ? o[3] - o[1] + 1 : o[2] - o[0] + 1;
		if (front < t.minFrontage() || depth < t.minDepth()) {
			return null;
		}
		int cutF = Math.max(0, front - t.maxFrontage());
		if (alongX) {
			if (ascending) {
				o[2] -= cutF;
			} else {
				o[0] += cutF;
			}
		} else {
			if (ascending) {
				o[3] -= cutF;
			} else {
				o[1] += cutF;
			}
		}
		int cutD = Math.max(0, depth - t.maxDepth());
		switch (facing) {
			case NORTH -> o[3] -= cutD;
			case SOUTH -> o[1] += cutD;
			case WEST -> o[2] -= cutD;
			default -> o[0] += cutD;
		}
		return o;
	}
}
