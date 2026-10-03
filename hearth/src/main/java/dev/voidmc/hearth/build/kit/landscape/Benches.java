package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Benches where folk sit of an evening: a row of stair seats (wood, or the paving stone in cultures that sit on stone),
 * with arm rests of open trapdoors at the ends where there is room. Every seat is a SEAT point of interest facing the
 * way the sitter looks.
 */
public final class Benches {
	private Benches() {
	}

	/** The seat material: the paving's stairs in stone-bench cultures, else the furniture wood. */
	public static Mat seat(Draft d) {
		if (Manner.stoneBenches(d)) {
			Mat p = d.mat(Role.PAVING);
			if (p.has(Form.STAIRS) && !Manner.wood(p)) {
				return p;
			}
			Mat m = Manner.masonry(d);
			if (m.has(Form.STAIRS)) {
				return m;
			}
		}
		return d.mat(Role.FURNITURE);
	}

	/**
	 * A bench of {@code length} seats starting at (x, y, z) running toward {@code run}, the sitters looking toward
	 * {@code looks}. Arm rests go in the cells just beyond the ends when they are free and inside the plot.
	 */
	public static void bench(Draft d, int x, int y, int z, Direction looks, Direction run, int length, boolean arms) {
		Mat seat = seat(d);
		Direction back = looks.getOpposite();
		Paint p = seat.has(Form.STAIRS) ? seat.stairs(back, Half.BOTTOM) : seat.stairsOrSlab(back, Half.BOTTOM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, p);
				d.poi(PoiType.SEAT, px, y, pz, looks);
			}
			if (arms) {
				Mat wood = d.mat(Role.FURNITURE);
				if (!wood.has(Form.TRAPDOOR)) {
					return;
				}
				for (int end : new int[]{-1, length}) {
					int ax = x + Dirs.dx(run) * end;
					int az = z + Dirs.dz(run) * end;
					if (!Lamps.in(d, ax, az) || !d.isEmpty(ax, y, az)) {
						continue;
					}
					// the panel stands against the seat: an open trapdoor faces away from the side it stands on
					Direction out = end < 0 ? run.getOpposite() : run;
					d.set(ax, y, az, wood.trapdoor(out, Half.BOTTOM, true));
				}
			}
		}
	}
}
