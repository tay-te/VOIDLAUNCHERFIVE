package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * Inner walls: the partitions that make flats, halls and corridors inside a town house, each with its door. Framed
 * houses get boarded partitions between posts, masonry houses walls of their own stuff.
 */
public final class Partitions {
	private Partitions() {
	}

	private static Mat wall(Draft d) {
		WallKind k = d.habits().walls().kind();
		return k == WallKind.LOG || k == WallKind.PLANK || k == WallKind.BAMBOO ? d.mat(Role.GABLE) : d.mat(Role.WALL);
	}

	/**
	 * A partition along z at column x, from z0 to z1, from floor y up {@code h} blocks (to the ceiling), with a door at
	 * {@code doorZ} (none if out of range) opening toward {@code doorFacing}.
	 */
	public static void alongZ(Draft d, int x, int z0, int z1, int y, int h, int doorZ, Direction doorFacing) {
		Mat m = wall(d);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int yy = y; yy < y + h; yy++) {
					if (z == doorZ && yy < y + 2) {
						continue;
					}
					if (d.isEmpty(x, yy, z)) {
						d.set(x, yy, z, m.full());
					}
				}
			}
		}
		if (doorZ >= Math.min(z0, z1) && doorZ <= Math.max(z0, z1)) {
			door(d, x, y, doorZ, doorFacing);
		}
	}

	/** A partition along x at row z, from x0 to x1, with a door at {@code doorX}. */
	public static void alongX(Draft d, int z, int x0, int x1, int y, int h, int doorX, Direction doorFacing) {
		Mat m = wall(d);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int yy = y; yy < y + h; yy++) {
					if (x == doorX && yy < y + 2) {
						continue;
					}
					if (d.isEmpty(x, yy, z)) {
						d.set(x, yy, z, m.full());
					}
				}
			}
		}
		if (doorX >= Math.min(x0, x1) && doorX <= Math.max(x0, x1)) {
			door(d, doorX, y, z, doorFacing);
		}
	}

	/** An inner door (the style's door) at (x, y, z), facing the way one walks through it. */
	public static void door(Draft d, int x, int y, int z, Direction facing) {
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(x, y, z, door, facing, DoorHingeSide.LEFT));
	}

	/** The direction from one cell to a neighbour (local labels). */
	public static Direction toward(int fx, int fz, int tx, int tz) {
		Direction dir = Dirs.of(Integer.signum(tx - fx), Integer.signum(tz - fz));
		return dir == null ? Dirs.BACK : dir;
	}
}
