package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finishes for roofs once they are rasterised: a thatch laid thick (the straw shows as straw over the whole slope, its
 * cut edge only at the eaves and the verges), so a thatched hut reads as thatch and not as shingles.
 */
public final class Roofwork {
	private Roofwork() {
	}

	/**
	 * Lays a thatch thick: every stair of the roof's surface that is not on its edge (the eaves and the verges, where the
	 * cut edge of the straw shows) is restated as the thatch's full block, so the slope steps in straw. Only where the
	 * cover's stairs are another material than its full block (hay bales cut as oak stairs). Returns how many cells.
	 */
	public static int thick(Draft d, Roofs.Roof roof, Mat thatch) {
		Block full = thatch.main(Form.FULL);
		Block stairs = thatch.main(Form.STAIRS);
		if (full == null || stairs == null || full == stairs) {
			return 0;
		}
		int n = 0;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = roof.z0; z <= roof.z1; z++) {
				for (int x = roof.x0; x <= roof.x1; x++) {
					if (!roof.covers(x, z) || edge(roof, x, z)) {
						continue;
					}
					int y = roof.surfaceY(x, z);
					BlockState st = d.get(x, y, z);
					if (st != null && st.getBlock() instanceof StairBlock) {
						d.restate(x, y, z, full.defaultBlockState());
						n++;
					}
				}
			}
		}
		return n;
	}

	/**
	 * Barge boards: the verges of a gabled roof (the rows over the gable overhangs at the two ends the ridge runs to)
	 * restated in {@code trim}, so the gable's rake reads as a crisp dark edge against the sky and the straw or shingles.
	 */
	public static void verges(Draft d, Roofs.Roof roof, Direction.Axis ridge, Mat trim) {
		if (ridge == Direction.Axis.Z) {
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimRow(d, roof, roof.z0, trim);
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimRow(d, roof, roof.z1, trim);
		} else {
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimCol(d, roof, roof.x0, trim);
			dev.voidmc.hearth.build.kit.homestead.RoofDress.trimCol(d, roof, roof.x1, trim);
		}
	}

	/**
	 * Brackets under the eaves: an upside-down stair of {@code trim} tucked under the eave row against the wall head,
	 * every other cell along the walls of {@code r} (x0, z0, x1, z1) that the eaves run along, where a person walking
	 * by keeps two blocks of headroom. The eave reads thick and carried.
	 */
	public static void brackets(Draft d, Roofs.Roof roof, int[] r, Direction.Axis ridge, Mat trim, boolean allRound) {
		if (!trim.has(Form.STAIRS)) {
			return;
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (var side : dev.voidmc.hearth.build.kit.Side.of(r[0], r[1], r[2], r[3])) {
				if (!allRound && side.axis() != ridge) {
					continue;
				}
				for (int i = 1; i < side.last(); i += 2) {
					int ox = side.outX(i, 1);
					int oz = side.outZ(i, 1);
					if (!roof.covers(ox, oz) || ox < 0 || oz < 0 || ox >= d.frontage() || oz >= d.depth()) {
						continue;
					}
					int y = roof.bottomY(ox, oz) - 1;
					if (y >= d.site().ground(ox, oz) + 2 && d.isAbsent(ox, y, oz) && dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, side.x(i), y, side.z(i))) {
						d.set(ox, y, oz, trim.stairs(side.out().getOpposite(), net.minecraft.world.level.block.state.properties.Half.TOP));
					}
				}
			}
		}
	}

	/** Whether a roof column is on its edge (a neighbour off the roof). */
	public static boolean edge(Roofs.Roof roof, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!roof.covers(x + Dirs.dx(dir), z + Dirs.dz(dir))) {
				return true;
			}
		}
		return false;
	}
}
