package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Peeled logs: northern builders take the bark off the logs of a wall (the corner posts keep theirs), so a log house
 * reads warm and light rather than as a dark mass of bark. Applied to a finished drawing: every horizontal log of the
 * culture's wall material in a box becomes its stripped log, axis kept.
 */
public final class Logs {
	private Logs() {
	}

	/** Peels the wall logs lying horizontally in the box (inclusive), leaving posts, corners and other woods alone. */
	public static void peel(Draft d, int x0, int y0, int z0, int x1, int y1, int z1) {
		Block log = d.mat(Role.WALL).main(Form.LOG);
		Block stripped = d.mat(Role.WALL).main(Form.STRIPPED_LOG);
		Block frame = d.mat(Role.FRAME).main(Form.LOG);
		if (log == null || stripped == null || log == frame) {
			return;
		}
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					BlockState s = d.get(x, y, z);
					if (s == null || !s.is(log) || !s.hasProperty(BlockStateProperties.AXIS) || s.getValue(BlockStateProperties.AXIS).isVertical()) {
						continue;
					}
					d.restate(x, y, z, stripped.defaultBlockState().setValue(BlockStateProperties.AXIS, s.getValue(BlockStateProperties.AXIS)));
				}
			}
		}
	}

	/**
	 * A fieldstone sill under a log house: the lowest course of wall logs round a rectangle (at standing level y) laid in
	 * the plinth's stone instead, the corners left to their notched logs. A grey band at the foot of the dark logs.
	 */
	public static void sill(Draft d, int x0, int z0, int x1, int z1, int y) {
		Block log = d.mat(Role.WALL).main(Form.LOG);
		Block stripped = d.mat(Role.WALL).main(Form.STRIPPED_LOG);
		if (log == null || d.habits().walls().kind() != dev.voidmc.hearth.build.style.Habits.WallKind.LOG || d.habits().walls().plinth() > 0) {
			return;
		}
		var plinth = d.mat(Role.PLINTH);
		for (int[] c : Draft.ring(x0, z0, x1, z1)) {
			boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
			BlockState s = d.get(c[0], y, c[1]);
			if (corner || s == null || !(s.is(log) || stripped != null && s.is(stripped))) {
				continue;
			}
			d.restate(c[0], y, c[1], plinth.full().at(c[0], y, c[1]));
		}
	}

	/** {@link #sill} for a finished drawing: every horizontal wall log at level y standing on footings becomes the sill. */
	public static void sillAll(Draft d, int y) {
		Block log = d.mat(Role.WALL).main(Form.LOG);
		if (log == null || d.habits().walls().kind() != dev.voidmc.hearth.build.style.Habits.WallKind.LOG || d.habits().walls().plinth() > 0) {
			return;
		}
		var plinth = d.mat(Role.PLINTH);
		for (int z = 0; z < d.depth(); z++) {
			for (int x = 0; x < d.frontage(); x++) {
				BlockState s = d.get(x, y, z);
				Draft.Cell below = d.cell(x, y - 1, z);
				if (s == null || !s.is(log) || !s.hasProperty(BlockStateProperties.AXIS) || s.getValue(BlockStateProperties.AXIS).isVertical()
					|| below == null || below.stage() != dev.voidmc.hearth.api.StageKind.FOUNDATION) {
					continue;
				}
				d.restate(x, y, z, plinth.full().at(x, y, z));
			}
		}
	}

	/** Peels every wall log over the whole plot up to {@code top}. */
	public static void peelAll(Draft d, int top) {
		peel(d, 0, -2, 0, d.frontage() - 1, top, d.depth() - 1);
	}
}
