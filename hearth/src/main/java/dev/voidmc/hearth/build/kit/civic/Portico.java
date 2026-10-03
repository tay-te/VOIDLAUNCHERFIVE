package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A portico before a front: columns with bases and capitals standing on a stylobate, an entablature across them and a
 * low pediment over it (or, in timber and eastern traditions, posts with brackets under a little hipped or curved roof).
 * It gives a bank, a library or a hall its door of state.
 */
public final class Portico {
	private Portico() {
	}

	/**
	 * A portico over columns {@code x0..x1} (inclusive, the outer two and every other one between get columns) and rows
	 * {@code z0..z1} in front of a wall standing at row {@code z1 + 1}, its floor at standing level {@code y}, the columns
	 * {@code h} high. Returns the top y drawn.
	 */
	public static int build(Draft d, Tradition t, int x0, int x1, int z0, int z1, int y, int h) {
		return build(d, t, x0, x1, z0, z1, y, h, false);
	}

	/** {@link #build}; {@code stone} builds it of stone columns under a pediment whatever the tradition's posts. */
	public static int build(Draft d, Tradition t, int x0, int x1, int z0, int z1, int y, int h, boolean stone) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat paving = d.mat(Role.PAVING);
		Mat found = d.mat(Role.FOUNDATION);
		Mat roof = d.mat(Role.ROOF);
		boolean timber = !stone && Arches.timber(Arches.kind(t));
		Mat frame = d.mat(Role.FRAME);
		// the stylobate: the floor of the portico, footed to the ground
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					for (int yy = Math.min(d.site().ground(x, z), y) - 1; yy < y - 1; yy++) {
						d.set(x, yy, z, found.full());
					}
					d.set(x, y - 1, z, paving.full());
				}
			}
		}
		Grounds.cut(d, x0, z0, x1, z1, y);
		// the columns along the front row: a base, a shaft, a capital
		int n = x1 - x0;
		try (var s = d.stage(timber ? StageKind.FRAME : StageKind.WALLS)) {
			for (int x = x0; x <= x1; x++) {
				if (!column(x - x0, n)) {
					continue;
				}
				for (int yy = y; yy < y + h; yy++) {
					Paint p;
					if (timber) {
						p = frame.log(Direction.Axis.Y);
					} else if (yy == y) {
						p = accent.full();
					} else if (yy == y + h - 1) {
						p = accent.full();
					} else {
						p = shaft(d);
					}
					d.set(x, yy, z0, p);
				}
			}
			// the entablature across the columns and back to the wall
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, y + h, z, timber ? d.mat(Role.BEAM).log(Direction.Axis.X) : accent.full());
				}
			}
		}
		// brackets under the beam between timber posts
		if (timber) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				int fx = x;
				if (d.isEmpty(x, y + h - 1, z0)) {
					boolean nextLeft = !d.isEmpty(x - 1, y + h - 1, z0);
					boolean nextRight = !d.isEmpty(x + 1, y + h - 1, z0);
					if (nextLeft || nextRight) {
						d.inStage(StageKind.FRAME, () -> d.set(fx, y + h - 1, z0, d.mat(Role.BEAM).stairsOrSlab(nextLeft ? Dirs.LEFT : Dirs.RIGHT, Half.TOP)));
					}
				}
			}
		}
		// the pediment: a low gable facing the street (masonry), a hipped or curved cap (timber, karst)
		int ty = y + h + 1;
		if (t == Tradition.KARST) {
			Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1).withPitch(1.0);
			Roofs.Built b = Roofs.build(d, x0, z0, x1, z1, y + h, Habits.RoofForm.PAGODA, Direction.Axis.X, o, false, false);
			return b.roof().ridgeY;
		}
		if (t == Tradition.ADOBE) {
			// a parapet with a stepped crest
			try (var s = d.stage(StageKind.ROOF)) {
				for (int x = x0; x <= x1; x++) {
					d.set(x, ty, z0, accent.full());
					if (Math.abs(x - (x0 + x1) / 2) <= 1) {
						d.set(x, ty + 1, z0, accent.slab(SlabType.BOTTOM));
					}
				}
			}
			return ty + 1;
		}
		Roofs.Field ped = Roofs.gable(x0, z0 - 1 >= -1 ? z0 : z0, x1, z1, Direction.Axis.Z, Roofs.Profile.straight(0.5));
		Roofs.Roof r = Roofs.raster(d, ped, ty, x0, z0, x1, z1, timber ? roof : accent.has(Form.STAIRS) ? accent : roof, roof, Habits.Ridge.SLAB);
		// the tympanum: the pediment's face filled under its slopes
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0 + 1; x <= x1 - 1; x++) {
				for (int yy = ty; yy < r.bottomY(x, z0); yy++) {
					d.set(x, yy, z0, timber ? d.mat(Role.GABLE).full() : d.mat(Role.WALL).full());
				}
			}
		}
		return r.ridgeY;
	}

	/** Whether cell k of a portico n + 1 wide has a column: the ends and every other cell, never the middle (the door). */
	public static boolean column(int k, int n) {
		if (k == 0 || k == n) {
			return true;
		}
		if (n % 2 == 0 && k == n / 2) {
			return false;
		}
		return k % 2 == 0 && k != n - 1 && k != 1;
	}

	/** A column's shaft: the accent's wall posts where it has them (a round column), else a pillar block. */
	static Paint shaft(Draft d) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		if (d.tier() >= 4 || accent.main(Form.FULL) == Blocks.SMOOTH_QUARTZ) {
			return Paint.of(Blocks.QUARTZ_PILLAR.defaultBlockState());
		}
		if (accent.has(Form.WALL)) {
			return accent.wall();
		}
		Mat found = d.mat(Role.FOUNDATION);
		return found.has(Form.WALL) ? found.wall() : accent.full();
	}
}
