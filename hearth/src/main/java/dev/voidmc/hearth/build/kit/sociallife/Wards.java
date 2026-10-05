package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Rooms of many beds: a dormitory's rows (beds head to the wall, a chest or a candle stand between each pair), a ward's
 * cots (a low pallet: a white bolster and a blanket in the house's cloth, not a bed anyone lives in, so a hospital never
 * counts as housing) with the healer's stand between them.
 */
public final class Wards {
	private Wards() {
	}

	/**
	 * A cot whose head is at (x, y, z) against the wall behind it, its foot one along {@code into}: a white wool bolster
	 * and a blanket of the house's cloth, a mattress half a block high. Registers SICKBED at the head, facing
	 * {@code side} (where the healer stands to tend it).
	 */
	public static void cot(Draft d, int x, int y, int z, Direction into, Direction side) {
		Block white = Blocks.WOOL_SLAB.pick(DyeColor.WHITE);
		Mat cloth = d.mat(Role.CLOTH);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.slab(white, SlabType.BOTTOM));
			int fx = x + Dirs.dx(into);
			int fz = z + Dirs.dz(into);
			d.set(fx, y, fz, cloth.has(Form.SLAB) ? cloth.slab(SlabType.BOTTOM).at(fx, y, fz) : States.slab(Blocks.WOOL_SLAB.pick(DyeColor.RED), SlabType.BOTTOM));
		}
		d.poi(PoiType.SICKBED, x, y, z, side);
	}

	/**
	 * A row of beds (or cots) along the wall {@code wall} of a hand-furnished floor: heads to the wall, every
	 * {@code pitch} cells from the row's start, each with a free cell beside it to get in; between pairs, against the
	 * wall, a chest (STORAGE) or a stand with a candle. Answers how many it placed (at most {@code max}).
	 */
	public static int row(Draft d, Floorplan plan, Direction wall, int pitch, boolean cots, int max, List<Object[]> chest) {
		List<int[]> run = plan.along(wall);
		Direction into = wall.getOpposite();
		int placed = 0;
		int gaps = 0;
		for (int i = 0; i < run.size() && placed < max; i++) {
			int[] h = run.get(i);
			int fx = h[0] + Dirs.dx(into);
			int fz = h[1] + Dirs.dz(into);
			if (!plan.inside(fx, fz) || !plan.free(h[0], h[1]) || !plan.free(fx, fz) || !Venue.floor(d, h[0], plan.y, h[1]) || !Venue.floor(d, fx, plan.y, fz)) {
				continue;
			}
			// the way in: the cell beside the foot (either side), else in front of the foot; beds packed side by side
			// (pitch 1) are each reached from before the foot
			int[] side = null;
			Direction sd = null;
			for (Direction s : pitch == 1 ? new Direction[]{into} : new Direction[]{Dirs.right(into), Dirs.left(into)}) {
				int sx = fx + Dirs.dx(s);
				int sz = fz + Dirs.dz(s);
				if (plan.free(sx, sz)) {
					side = new int[]{sx, sz};
					sd = s;
					break;
				}
			}
			if (side == null && pitch > 1) {
				int ex = fx + Dirs.dx(into);
				int ez = fz + Dirs.dz(into);
				if (plan.free(ex, ez)) {
					side = new int[]{ex, ez};
					sd = into;
				}
			}
			if (side == null || !plan.take(new int[][]{h, {fx, fz}}, side)) {
				continue;
			}
			if (cots) {
				cot(d, h[0], plan.y, h[1], into, sd);
			} else {
				Furniture.bed(d, h[0], plan.y, h[1], into);
			}
			placed++;
			// the gap after it: a chest or a candle stand against the wall, then skip to the next bed
			if (i + 1 < run.size() && pitch >= 2) {
				int[] g = run.get(i + 1);
				int gx = g[0] + Dirs.dx(into);
				int gz = g[1] + Dirs.dz(into);
				if (plan.free(g[0], g[1]) && plan.inside(gx, gz) && plan.free(gx, gz) && plan.take(g[0], g[1], into)) {
					if (!cots && (gaps & 1) == 0 && chest != null) {
						try (var s = d.stage(StageKind.INTERIOR)) {
							d.set(g[0], plan.y, g[1], States.facing(Blocks.CHEST, into), BlockEntities.container(chest));
						}
						d.poi(PoiType.STORAGE, gx, plan.y, gz, wall);
					} else {
						Mat wood = d.mat(Role.FURNITURE);
						try (var s = d.stage(StageKind.INTERIOR)) {
							d.set(g[0], plan.y, g[1], cots ? Blocks.BREWING_STAND.defaultBlockState() : wood.slab(SlabType.TOP).at(g[0], plan.y, g[1]));
						}
						if (!cots) {
							Lighting.candles(d, g[0], plan.y + 1, g[1], 1);
						}
					}
					gaps++;
				}
				i += pitch - 1;
			}
		}
		return placed;
	}
}
