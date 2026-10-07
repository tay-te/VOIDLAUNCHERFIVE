package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * The way up into a loft a stair cannot reach: a ladder against a wall, through a hatch cut in the loft's floor. A loft
 * folk can climb to is a room (lit, furnished, never a dark void under the roof).
 */
public final class Lofts {
	private Lofts() {
	}

	/**
	 * A ladder in the cell (x, z) from standing level {@code yFrom} up through the floor of the loft standing at
	 * {@code yTo}, against the wall toward {@code toWall}: every cell of the climb free, the wall solid behind each rung,
	 * two free rows over the hatch (under the roof, when one is given). Answers whether it went in.
	 */
	public static boolean ladder(Draft d, int x, int z, int yFrom, int yTo, Direction toWall, Roofs.Roof roof) {
		int wx = x + Dirs.dx(toWall);
		int wz = z + Dirs.dz(toWall);
		for (int y = yFrom; y < yTo; y++) {
			if (!d.isSolid(wx, y, wz)) {
				return false;
			}
			if (y < yTo - 1 && !d.isEmpty(x, y, z)) {
				return false;
			}
		}
		if (!d.isEmpty(x, yTo, z) || !d.isEmpty(x, yTo + 1, z) || roof != null && roof.covers(x, z) && roof.bottomY(x, z) < yTo + 2) {
			return false;
		}
		// a landing: a floor cell beside the hatch (not behind the ladder) with two rows of air over it, to step off onto
		if (landing(d, x, z, yTo, toWall, roof) == null) {
			return false;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int y = yFrom; y < yTo; y++) {
				d.set(x, y, z, States.facing(Blocks.LADDER, toWall.getOpposite()));
			}
		}
		return true;
	}

	/**
	 * The cell a climber steps off onto at the head of a ladder in (x, z) whose loft floor stands at {@code yTo}: beside
	 * the hatch, not behind the ladder (the wall), boarded, with two rows of air over it (under the roof, when one is
	 * given). Returns {x, z}, or null where there is none.
	 */
	public static int @org.jspecify.annotations.Nullable [] landing(Draft d, int x, int z, int yTo, Direction toWall, Roofs.Roof roof) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (dir == toWall) {
				continue;
			}
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (d.isSolid(nx, yTo - 1, nz) && d.isEmpty(nx, yTo, nz) && d.isEmpty(nx, yTo + 1, nz)
				&& (roof == null || !roof.covers(nx, nz) || roof.bottomY(nx, nz) >= yTo + 2)) {
				return new int[]{nx, nz};
			}
		}
		return null;
	}
}
