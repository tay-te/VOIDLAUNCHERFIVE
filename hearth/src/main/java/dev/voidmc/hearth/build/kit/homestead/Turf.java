package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Turf roofs, the way the north roofs a log hall: sods of grass laid in steps over the boards, moss creeping through
 * them, tufts of grass and fern growing on top, and a timber eave board holding the lowest course. Any roof field
 * (gable, hip, bowed) rasterises as turf.
 */
public final class Turf {
	private Turf() {
	}

	/** Rasterises a roof field in turf over a rectangle (see {@link Roofs#raster}); returns the roof. */
	public static Roofs.Roof raster(Draft d, Roofs.Field f, int y0, int x0, int z0, int x1, int z1) {
		Mat ground = d.mat(Role.GROUND);
		Mat board = d.mat(Role.FRAME).has(Form.STAIRS) ? d.mat(Role.FRAME) : d.mat(Role.ROOF);
		Roofs.Roof r;
		try (var s = d.stage(StageKind.ROOF)) {
			r = Roofs.raster(d, f, y0, x0, z0, x1, z1, ground, ground, Habits.Ridge.FULL);
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int t = r.halfHeight(x, z);
					if (t == Integer.MIN_VALUE) {
						continue;
					}
					int y = r.surfaceY(x, z);
					// the eave course: a timber board facing up the slope
					Direction up = null;
					int best = t;
					for (Direction dir : Dirs.HORIZONTAL) {
						int n = r.halfHeight(x + Dirs.dx(dir), z + Dirs.dz(dir));
						if (n != Integer.MIN_VALUE && n > best) {
							best = n;
							up = dir;
						}
					}
					boolean edge = false;
					for (Direction dir : Dirs.HORIZONTAL) {
						if (r.halfHeight(x + Dirs.dx(dir), z + Dirs.dz(dir)) == Integer.MIN_VALUE) {
							edge = true;
						}
					}
					if (edge && up != null) {
						d.set(x, y, z, board.stairs(up, Half.BOTTOM));
						continue;
					}
					if (Noise.value(d.seed() ^ 0x7055, x / 2.0, y / 2.0, z / 2.0) < 0.32) {
						d.set(x, y, z, Blocks.MOSS_BLOCK.defaultBlockState());
					}
				}
			}
		}
		// tufts on top (snow lying on the sods where the culture's winters are white)
		boolean snow = d.habits().snow();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!r.covers(x, z)) {
						continue;
					}
					int y = r.surfaceY(x, z);
					var top = d.get(x, y, z);
					if (top == null || !(top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.MOSS_BLOCK)) || y + 1 >= d.maxHeight() || !d.isAbsent(x, y + 1, z)) {
						continue;
					}
					double u = Noise.unit(d.seed() ^ 0x7F7, x, y, z);
					if (snow) {
						if (u < 0.85) {
							d.set(x, y + 1, z, dev.voidmc.hearth.build.draft.States.snow(1 + (int) (u * 3)));
						}
						continue;
					}
					if (u < 0.1) {
						d.set(x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
					} else if (u < 0.14) {
						d.set(x, y + 1, z, Blocks.FERN.defaultBlockState());
					} else if (u < 0.16 && top.is(Blocks.MOSS_BLOCK)) {
						d.set(x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
					}
				}
			}
		}
		return r;
	}
}
