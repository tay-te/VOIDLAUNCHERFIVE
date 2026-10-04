package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The life of a house by the water: a boat moored at the landing, the mooring post with its lantern, crab traps and
 * lobster pots stacked on the boards, nets hung to dry over a rail. Everything here is block-built (a boat is a hull of
 * stairs and slabs lying on the water), so it is part of the blueprint and the builders' bill.
 */
public final class Waterside {
	private Waterside() {
	}

	/**
	 * A punt or dugout moored on the water: {@code len} cells from (x, z) along {@code run}, lying on the surface (its
	 * slabs in the first air above the water), stem and stern raised (stairs whose high backs face outward). Every cell
	 * must be open water of one level, free of drawing; the caller puts it beside a landing so it is tied to something.
	 * Returns whether it was drawn.
	 */
	public static boolean boat(Draft d, int x, int z, Direction run, int len) {
		int level = Integer.MIN_VALUE;
		for (int i = 0; i < len; i++) {
			int cx = x + Dirs.dx(run) * i;
			int cz = z + Dirs.dz(run) * i;
			if (!Verandas.in(d, cx, cz) || !d.site().isWater(cx, cz) || d.site().waterDepth(cx, cz) < 1) {
				return false;
			}
			int wy = d.site().water(cx, cz);
			if (level != Integer.MIN_VALUE && wy != level || !d.isAbsent(cx, wy, cz) || !d.isAbsent(cx, wy + 1, cz)) {
				return false;
			}
			level = wy;
		}
		Mat hull = d.mat(Role.FURNITURE);
		int y = level;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < len; i++) {
				int cx = x + Dirs.dx(run) * i;
				int cz = z + Dirs.dz(run) * i;
				if (i == 0) {
					d.set(cx, y, cz, hull.stairsOrSlab(run.getOpposite(), Half.BOTTOM));
				} else if (i == len - 1) {
					d.set(cx, y, cz, hull.stairsOrSlab(run, Half.BOTTOM));
				} else {
					d.set(cx, y, cz, hull.slab(SlabType.BOTTOM).orElse(hull.full()));
				}
			}
		}
		return true;
	}

	/**
	 * A mooring post at (x, z): a pile from the bed up to standing level y (a block proud of the boards around it at
	 * y - 1) with a lantern on its head.
	 */
	public static void mooring(Draft d, int x, int z, int y) {
		if (!Verandas.in(d, x, z)) {
			return;
		}
		Mat stilt = d.mat(Role.STILT);
		int g = d.site().ground(x, z);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int yy = g - 2; yy <= y; yy++) {
				d.set(x, yy, z, stilt.log(Direction.Axis.Y));
			}
		}
		if (d.isAbsent(x, y + 1, z)) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y + 1, z, States.lantern(d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN), false)));
		}
	}

	/** Crab traps or lobster pots (slatted boxes) stacked at (x, y, z), {@code high} of them where the cells are free. */
	public static int traps(Draft d, int x, int y, int z, int high) {
		int n = 0;
		for (int k = 0; k < high; k++) {
			if (!Verandas.in(d, x, z) || !d.isEmpty(x, y + k, z) || k == 0 && !d.isSolid(x, y - 1, z)) {
				break;
			}
			int yy = y + k;
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, yy, z, Blocks.COMPOSTER.defaultBlockState()));
			n++;
		}
		return n;
	}

	/** A net hung to dry over the rail at (x, z): cobweb on the fence's head. */
	public static boolean net(Draft d, int x, int y, int z) {
		var below = d.get(x, y - 1, z);
		if (!Verandas.in(d, x, z) || below == null || !(below.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) || !d.isEmpty(x, y, z)) {
			return false;
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, Blocks.COBWEB.defaultBlockState()));
		return true;
	}

	/** The water surface (first air above it) at a column, or {@link Site#NONE}. */
	public static int surface(Draft d, int x, int z) {
		return d.site().isWater(x, z) ? d.site().water(x, z) : Site.NONE;
	}
}
