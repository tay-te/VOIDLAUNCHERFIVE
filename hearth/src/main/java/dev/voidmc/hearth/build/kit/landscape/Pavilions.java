package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Open pavilions on a raised podium: four corner posts (masonry piers with arched openings in the desert) carrying a
 * wall plate and a roof in the culture's form (a curved hip in the karst, a stepped cupola on a flat roof in the
 * desert, the culture's own pitched roof elsewhere), lanterns under the eaves, paving in a ring pattern, steps up the
 * front. Used for bell pavilions, park bandstands and garden kiosks.
 */
public final class Pavilions {
	private Pavilions() {
	}

	/**
	 * A pavilion over {@code [x0..x1] × [z0..z1]} on ground level y (the podium's course at y, its floor at y + 1),
	 * with the bell hung in the middle when {@code bell}, benches on the sides when {@code benches}. Returns the top y.
	 */
	public static int pavilion(Draft d, int x0, int z0, int x1, int z1, int y, boolean bell, boolean benches) {
		Mat foot = Manner.masonry(d);
		Mat post = d.mat(Role.FRAME);
		Culture c = Manner.culture(d);
		boolean desert = c == Culture.DESERT || c == Culture.MESA;
		boolean karst = c == Culture.KARST;
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		// the podium: a course of stone, steps across the front
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int g = d.site().ground(x, z);
					for (int yy = Math.min(g, y) - 1; yy < y; yy++) {
						d.set(x, yy, z, d.mat(Role.FOUNDATION).full());
					}
					if (z == z0 && foot.has(Form.STAIRS)) {
						d.set(x, y, z, x == x0 || x == x1 ? foot.full() : foot.stairs(Dirs.BACK, Half.BOTTOM));
					} else {
						d.set(x, y, z, foot.full());
					}
				}
			}
		}
		int y0 = y + 1;
		Paving.lay(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y0, Paving.paint(d, Paving.Pattern.RINGS, cx, cz, 2, true));
		int h = Math.min(4, d.maxHeight() - y0 - 4);
		int plate = y0 + h;
		Paint column = desert ? foot.full() : post.log(Direction.Axis.Y).orElse(post.full());
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] k : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
				for (int yy = y0; yy < plate; yy++) {
					d.set(k[0], yy, k[1], column);
				}
			}
			for (int x = x0; x <= x1; x++) {
				d.set(x, plate, z0, desert ? foot.full() : beam.log(Direction.Axis.X).orElse(beam.full()));
				d.set(x, plate, z1, desert ? foot.full() : beam.log(Direction.Axis.X).orElse(beam.full()));
			}
			for (int z = z0 + 1; z < z1; z++) {
				d.set(x0, plate, z, desert ? foot.full() : beam.log(Direction.Axis.Z).orElse(beam.full()));
				d.set(x1, plate, z, desert ? foot.full() : beam.log(Direction.Axis.Z).orElse(beam.full()));
			}
			if (bell) {
				for (int x = x0 + 1; x < x1; x++) {
					d.set(x, plate, cz, beam.log(Direction.Axis.X).orElse(beam.full()));
				}
			}
			if (desert && foot.has(Form.STAIRS)) {
				// arched openings: shoulders under the lintel on every side
				for (int z : new int[]{z0, z1}) {
					d.set(x0 + 1, plate - 1, z, foot.stairs(Dirs.LEFT, Half.TOP));
					d.set(x1 - 1, plate - 1, z, foot.stairs(Dirs.RIGHT, Half.TOP));
				}
				for (int x : new int[]{x0, x1}) {
					d.set(x, plate - 1, z0 + 1, foot.stairs(Dirs.FRONT, Half.TOP));
					d.set(x, plate - 1, z1 - 1, foot.stairs(Dirs.BACK, Half.TOP));
				}
			}
		}
		if (bell) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(cx, plate - 1, cz, Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Dirs.FRONT)
					.setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
			}
		}
		int top;
		if (desert) {
			Mat roof = d.mat(Role.ROOF);
			try (var s = d.stage(StageKind.ROOF)) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						boolean edge = x == x0 || z == z0 || x == x1 || z == z1;
						d.set(x, plate + 1, z, edge ? foot.slab(SlabType.BOTTOM) : roof.full());
					}
				}
				for (int z = cz - 1; z <= cz + 1; z++) {
					for (int x = cx - 1; x <= cx + 1; x++) {
						if (x == cx && z == cz) {
							d.set(x, plate + 2, z, roof.full());
						} else if (x == cx || z == cz) {
							d.set(x, plate + 2, z, roof.stairsOrSlab(Dirs.nearest(cx - x, cz - z), Half.BOTTOM));
						} else {
							d.set(x, plate + 2, z, roof.slab(SlabType.BOTTOM));
						}
					}
				}
				d.set(cx, plate + 3, cz, roof.full());
			}
			Towers.finial(d, cx, plate + 4, cz);
			top = plate + 4;
		} else {
			RoofForm form = karst ? RoofForm.PAGODA : d.habits().roof().pick(d.random(), RoofForm.HIP, RoofForm.GABLE, RoofForm.HALF_HIP);
			Roofs.Opts o = Roofs.Opts.of(d).withEaves(0, 0);
			Roofs.Built b = Roofs.build(d, x0, z0, x1, z1, plate, form, Direction.Axis.X, o, false, false);
			top = b.roof().ridgeY;
			if (form == RoofForm.HIP && ((x1 - x0) & 1) == 0 && ((z1 - z0) & 1) == 0) {
				Towers.finial(d, cx, top + 1, cz);
				top++;
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (desert) {
				d.set(cx, plate - 1, z0, States.lantern(Lamps.lantern(d), true));
			} else {
				d.set(x0 + 1, plate - 1, z0, States.lantern(Lamps.lantern(d), true));
				d.set(x1 - 1, plate - 1, z0, States.lantern(Lamps.lantern(d), true));
			}
		}
		if (benches) {
			Benches.bench(d, x0 + 1, y0, z1 - 1, Dirs.FRONT, Dirs.RIGHT, 1, false);
			Benches.bench(d, x1 - 1, y0, z1 - 1, Dirs.FRONT, Dirs.RIGHT, 1, false);
		}
		d.poi(PoiType.MEETING_POINT, cx, y0, z0 + 1, Dirs.BACK);
		return top;
	}
}
