package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Ranges built round a yard (a playhouse's galleries, a caravanserai's cells): the rectangle {@code [x0..x1] × [z0..z1]}
 * whose wings, {@code depth} cells deep from the outer walls, run along the sides it is given (the front always, the back
 * where {@code back}); {@link #k} tells how far a cell lies in from the outer wall, and the ring roof covers the wings.
 */
public record Ring(int x0, int z0, int x1, int z1, int depth, boolean back) {
	/** How far (x, z) lies in from the nearest outer wall of the wings (0 = the wall; depth or more: the yard). */
	public int k(int x, int z) {
		int k = Math.min(Math.min(x - this.x0, this.x1 - x), z - this.z0);
		if (this.back) {
			k = Math.min(k, this.z1 - z);
		}
		return k;
	}

	/** Whether (x, z) is a cell of the rectangle. */
	public boolean inside(int x, int z) {
		return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
	}

	/** The direction from (x, z) toward the yard (away from the nearest outer wall). */
	public Direction inward(int x, int z) {
		int dz0 = z - this.z0;
		int dx0 = x - this.x0;
		int dx1 = this.x1 - x;
		int dz1 = this.back ? this.z1 - z : Integer.MAX_VALUE;
		int m = Math.min(Math.min(dz0, dz1), Math.min(dx0, dx1));
		if (m == dz0) {
			return Dirs.BACK;
		}
		if (m == dz1) {
			return Dirs.FRONT;
		}
		return m == dx0 ? Dirs.RIGHT : Dirs.LEFT;
	}

	/**
	 * The roof over the wings at {@code plate}: pitched, its ridge running round the yard over the wings' middle, the
	 * roofing's stairs falling to the outer eaves and to the yard (the corners meeting as hips); or flat, a deck of the
	 * roofing behind a parapet of the walls on the outer edge. Only cells with nothing drawn at the deck row are roofed
	 * when {@code onlyEmpty}.
	 */
	public void roof(Draft d, int plate, boolean flat, int zLimit) {
		Mat roof = d.mat(Role.ROOF);
		int half = this.depth / 2;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = this.x0; x <= this.x1; x++) {
				for (int z = this.z0; z <= Math.min(this.z1, zLimit); z++) {
					int k = k(x, z);
					if (k >= this.depth) {
						continue;
					}
					if (flat) {
						d.set(x, plate, z, roof.has(Form.SLAB) ? roof.slab(SlabType.TOP) : roof.full());
						if (k == 0 && plate + 1 < d.maxHeight()) {
							d.set(x, plate + 1, z, d.mat(Role.WALL).full());
						}
						continue;
					}
					Direction in = inward(x, z);
					int up = Math.min(k, this.depth - 1 - k);
					int y = plate + Math.min(up, half);
					if (y >= d.maxHeight()) {
						y = d.maxHeight() - 1;
					}
					Direction facing = k < (this.depth + 1) / 2 ? in : in.getOpposite();
					boolean ridge = this.depth % 2 == 1 && k == half;
					if (ridge) {
						d.set(x, y, z, roof.has(Form.SLAB) ? roof.slab(SlabType.BOTTOM) : roof.full());
					} else {
						d.set(x, y, z, roof.has(Form.STAIRS) ? roof.stairs(facing, Half.BOTTOM) : roof.full());
					}
					for (int yy = plate; yy < y; yy++) {
						d.set(x, yy, z, roof.full());
					}
				}
			}
		}
	}
}
