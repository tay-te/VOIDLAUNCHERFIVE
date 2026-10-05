package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.light.Lighting;
import net.minecraft.core.Direction;

/**
 * Lamps a builder hangs where the light pass would otherwise reach for its hidden light: lanterns hung under the floor
 * above against the walls of a narrow round room (a mill's floors, a tower's stages), and a lantern hung from the ridge
 * of a loft.
 */
public final class Lamps {
	private Lamps() {
	}

	/**
	 * Lanterns hung under the ceiling against the walls of the room standing at {@code floorY} round (cx, cz): looking
	 * out from the middle along each axis (left, right, back, front in turn) to the first solid wall two above the floor,
	 * the lantern hangs in the last free cell before it from whatever is drawn over it. At most {@code count}. Returns
	 * how many were hung.
	 */
	public static int byWalls(Draft d, int cx, int cz, int floorY, int count) {
		int hung = 0;
		int y = floorY + 2;
		for (Direction dir : new Direction[]{Dirs.LEFT, Dirs.RIGHT, Dirs.BACK, Dirs.FRONT}) {
			if (hung >= count) {
				break;
			}
			int x = cx;
			int z = cz;
			int lastX = Integer.MIN_VALUE;
			int lastZ = 0;
			for (int k = 1; k <= 12; k++) {
				int nx = cx + Dirs.dx(dir) * k;
				int nz = cz + Dirs.dz(dir) * k;
				if (!d.isEmpty(nx, y, nz)) {
					if (d.isSolid(nx, y, nz) && lastX != Integer.MIN_VALUE) {
						x = lastX;
						z = lastZ;
					} else {
						lastX = Integer.MIN_VALUE;
					}
					break;
				}
				lastX = nx;
				lastZ = nz;
			}
			if (lastX == Integer.MIN_VALUE || x == cx && z == cz) {
				continue;
			}
			// the floor under it and the cells round its foot free of steps: a lantern never hangs over a stair
			if (!d.isEmpty(x, y - 1, z) || !d.isEmpty(x, floorY, z) || d.isEmpty(x, floorY - 1, z)) {
				continue;
			}
			int ceil = -1;
			for (int yy = y + 1; yy <= y + 8; yy++) {
				if (!d.isEmpty(x, yy, z)) {
					ceil = yy;
					break;
				}
			}
			if (ceil < 0) {
				continue;
			}
			Lighting.chandelier(d, x, z, ceil, y);
			hung++;
		}
		return hung;
	}
}
