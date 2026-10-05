package dev.voidmc.hearth.plan.growth;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.grid.Cell;
import dev.voidmc.hearth.plan.model.QueuedProject;
import dev.voidmc.hearth.plan.model.Reservation;
import dev.voidmc.hearth.plan.road.Roads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;

/**
 * The main square: held open from the founding (a village green with its well in the middle), paved as a plaza at
 * village tier, then furnished — market stalls round the well, a fountain, a statue, lamps at the corners — each kept
 * two blocks in from the square's edge so the houses and shops fronting it keep their doors on it.
 */
public final class Square {
	private Square() {
	}

	public static boolean paved(PlanContext ctx) {
		for (PlotView p : ctx.settlement.plots()) {
			if (p.spec().archetype().equals(Archetypes.PLAZA) && p.spec().params().getBooleanOr("heart", false)) {
				return true;
			}
		}
		for (QueuedProject q : ctx.state.queue) {
			if (q.spec().archetype().equals(Archetypes.PLAZA) && q.spec().params().getBooleanOr("heart", false)) {
				return true;
			}
		}
		return false;
	}

	/** The plaza's own height: room for trees in its planters and a lamp's arm (arch-landscape.md §6). */
	private static int plazaHeight(PlanContext ctx) {
		var info = ctx.catalogue.info(Archetypes.PLAZA);
		return info == null ? 10 : Math.max(4, info.maxHeight());
	}

	/** Paves the square as the heart plaza, keeping whatever already stands in it. */
	public static boolean pave(PlanContext ctx) {
		Reservation r = ctx.state.reservation("square");
		if (r == null) {
			return false;
		}
		CompoundTag params = new CompoundTag();
		params.putBoolean("heart", true);
		params.put("keep", new IntArrayTag(keep(ctx, r)));
		PlotSpec spec = new PlotSpec(Archetypes.PLAZA, ctx.style, ctx.nextSeed(), new BlockPos(r.x0(), ctx.state.heartY, r.z0()), r.x1() - r.x0() + 1,
			r.z1() - r.z0() + 1, Direction.SOUTH, plazaHeight(ctx), Build.finish(ctx, ctx.catalogue.info(Archetypes.PLAZA), 0, null), params);
		Roads.issue(ctx, spec, "heart: the square is paved as a plaza");
		return true;
	}

	/** Rectangles {x0, z0, x1, z1, ...} (inclusive) of everything standing inside the square. */
	private static int[] keep(PlanContext ctx, Reservation r) {
		List<Integer> out = new ArrayList<>();
		List<PlotSpec> specs = new ArrayList<>();
		for (PlotView p : ctx.settlement.plots()) {
			specs.add(p.spec());
		}
		for (QueuedProject q : ctx.state.queue) {
			specs.add(q.spec());
		}
		for (PlotSpec s : specs) {
			if (!Archetypes.isPlazaFurniture(s.archetype())) {
				continue;
			}
			int x0 = s.origin().getX();
			int z0 = s.origin().getZ();
			int x1 = x0 + s.sizeX() - 1;
			int z1 = z0 + s.sizeZ() - 1;
			if (x0 >= r.x0() && x1 <= r.x1() && z0 >= r.z0() && z1 <= r.z1()) {
				out.add(x0);
				out.add(z0);
				out.add(x1);
				out.add(z1);
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Places a small central thing (a stall, a fountain, a statue, a lamp) inside the square, two in from its edge,
	 * off the mouths where roads come in and clear of what already stands there, facing the middle.
	 */
	public static boolean furniture(PlanContext ctx, ArchetypeInfo info, String reason) {
		Reservation r = ctx.state.reservation("square");
		if (r == null) {
			return false;
		}
		int size = Math.max(info.minFrontage(), Math.min(info.maxFrontage(), info.id().equals(Archetypes.FOUNTAIN) ? 5 : 3));
		int depth = Math.max(info.minDepth(), Math.min(info.maxDepth(), size));
		int inset = 2;
		int x0 = r.x0() + inset;
		int z0 = r.z0() + inset;
		int x1 = r.x1() - inset;
		int z1 = r.z1() - inset;
		if (x1 - x0 + 1 < size + 2 || z1 - z0 + 1 < size + 2) {
			return false;
		}
		int hx = ctx.heartX();
		int hz = ctx.heartZ();
		List<int[]> spots = new ArrayList<>();
		boolean central = info.id().equals(Archetypes.FOUNTAIN) || info.id().equals(Archetypes.STATUE);
		if (central) {
			// a fountain or statue: on the cross axes, halfway out from the middle
			int off = (x1 - x0) / 4 + 1;
			spots.add(new int[]{hx - size / 2 + off, hz - depth / 2});
			spots.add(new int[]{hx - size / 2 - off, hz - depth / 2});
			spots.add(new int[]{hx - size / 2, hz - depth / 2 + off});
			spots.add(new int[]{hx - size / 2, hz - depth / 2 - off});
		}
		if (info.id().equals(Archetypes.LAMP_POST)) {
			spots.add(new int[]{x0, z0});
			spots.add(new int[]{x1, z0});
			spots.add(new int[]{x0, z1});
			spots.add(new int[]{x1, z1});
		}
		// round the edges of the inner ring
		for (int x = x0; x + size - 1 <= x1; x += size + 1) {
			spots.add(new int[]{x, z0});
			spots.add(new int[]{x, z1 - depth + 1});
		}
		for (int z = z0 + depth + 1; z + size - 1 <= z1 - depth - 1; z += size + 1) {
			spots.add(new int[]{x0, z});
			spots.add(new int[]{x1 - depth + 1, z});
		}
		for (int[] s : spots) {
			int sx1 = s[0] + size - 1;
			int sz1 = s[1] + depth - 1;
			if (!allPlaza(ctx, s[0] - 1, s[1] - 1, sx1 + 1, sz1 + 1) || nearMouth(ctx, r, s[0], s[1], sx1, sz1)) {
				continue;
			}
			int cx = (s[0] + sx1) / 2;
			int cz = (s[1] + sz1) / 2;
			Direction facing = Math.abs(cx - hx) >= Math.abs(cz - hz) ? (cx > hx ? Direction.WEST : Direction.EAST)
				: (cz > hz ? Direction.NORTH : Direction.SOUTH);
			int sizeX = facing.getAxis() == Direction.Axis.Z ? size : depth;
			int sizeZ = facing.getAxis() == Direction.Axis.Z ? depth : size;
			if (!allPlaza(ctx, s[0] - 1, s[1] - 1, s[0] + sizeX, s[1] + sizeZ)) {
				continue;
			}
			CompoundTag params = new CompoundTag();
			params.putBoolean("on_square", true);
			params.putInt("street_y", ctx.state.heartY);
			PlotSpec spec = new PlotSpec(info.id(), ctx.style, ctx.nextSeed(), new BlockPos(s[0], ctx.state.heartY, s[1]), sizeX, sizeZ, facing,
				info.maxHeight(), Build.finish(ctx, info, 0, null), params);
			Roads.issue(ctx, spec, reason);
			return true;
		}
		return false;
	}

	private static boolean allPlaza(PlanContext ctx, int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				byte c = ctx.occ.get(x, z);
				if (c != Cell.PLAZA) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether a rectangle sits in front of where a road enters the square. */
	private static boolean nearMouth(PlanContext ctx, Reservation r, int x0, int z0, int x1, int z1) {
		for (int x = x0 - 3; x <= x1 + 3; x++) {
			if (Cell.isAccess(ctx.occ.get(x, r.z0() - 1)) && ctx.occ.get(x, r.z0() - 1) != Cell.PLAZA && z0 - r.z0() < 6
				|| Cell.isAccess(ctx.occ.get(x, r.z1() + 1)) && ctx.occ.get(x, r.z1() + 1) != Cell.PLAZA && r.z1() - z1 < 6) {
				return true;
			}
		}
		for (int z = z0 - 3; z <= z1 + 3; z++) {
			if (Cell.isAccess(ctx.occ.get(r.x0() - 1, z)) && ctx.occ.get(r.x0() - 1, z) != Cell.PLAZA && x0 - r.x0() < 6
				|| Cell.isAccess(ctx.occ.get(r.x1() + 1, z)) && ctx.occ.get(r.x1() + 1, z) != Cell.PLAZA && r.x1() - x1 < 6) {
				return true;
			}
		}
		return false;
	}
}
