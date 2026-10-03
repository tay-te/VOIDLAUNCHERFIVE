package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;

/**
 * Floors and ceilings: boards over a rectangle, with the joists of the floor above showing as beams under the ceiling
 * (they leave two blocks of headroom, which folk and doors need, and they skip stairwells).
 */
public final class Floors {
	private Floors() {
	}

	/** Floor boards at block row y over a rectangle (the floor of a storey whose standing level is y + 1). */
	public static void boards(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat floor = d.mat(Role.FLOOR);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (d.isAbsent(x, y, z)) {
						d.set(x, y, z, floor.full());
					}
				}
			}
		}
	}

	/**
	 * Exposed joists under a ceiling at block row {@code ceilingY}: beams one row below it, every {@code every} blocks,
	 * spanning the rectangle along {@code span}. Only where the cells are still open and two blocks of headroom remain
	 * below; never over a stairwell (cells already drawn are left alone).
	 */
	public static void joists(Draft d, int x0, int z0, int x1, int z1, int ceilingY, Direction.Axis span, int every, int floorY) {
		if (ceilingY - 1 - floorY < 2) {
			return;
		}
		Mat beam = d.mat(Role.BEAM);
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		try (var s = d.stage(StageKind.FRAME)) {
			if (span == Direction.Axis.X) {
				for (int z = az + 1; z < bz; z += every) {
					if (!runOpen(d, ax, z, bx, z, ceilingY)) {
						continue;
					}
					for (int x = ax; x <= bx; x++) {
						d.set(x, ceilingY - 1, z, beam.log(Direction.Axis.X));
					}
				}
			} else {
				for (int x = ax + 1; x < bx; x += every) {
					if (!runOpen(d, x, az, x, bz, ceilingY)) {
						continue;
					}
					for (int z = az; z <= bz; z++) {
						d.set(x, ceilingY - 1, z, beam.log(Direction.Axis.Z));
					}
				}
			}
		}
	}

	/** A joist run is only drawn where the ceiling above is solid all along and nothing occupies the beam's row. */
	private static boolean runOpen(Draft d, int x0, int z0, int x1, int z1, int ceilingY) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!d.isSolid(x, ceilingY, z) || !d.isEmpty(x, ceilingY - 1, z)) {
					return false;
				}
			}
		}
		return true;
	}
}
