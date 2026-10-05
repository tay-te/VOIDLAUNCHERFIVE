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
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int y = yFrom; y < yTo; y++) {
				d.set(x, y, z, States.facing(Blocks.LADDER, toWall.getOpposite()));
			}
		}
		return true;
	}
}
