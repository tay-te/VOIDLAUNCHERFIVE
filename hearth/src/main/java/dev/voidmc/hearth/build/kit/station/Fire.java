package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Open fires of the poor and the remote: a fire pit on a hearthstone in the middle of a hut's floor (the smoke finds its
 * way out through the thatch), or one in the yard ringed by its seats with the cooking pot beside it. The fire is a lit
 * campfire, so it warms, cooks and lights the room (a campfire gives full light) and folk gather at it: a HEARTH is
 * registered where the keeper stands.
 */
public final class Fire {
	private Fire() {
	}

	/**
	 * A fire pit inside: a lit campfire at standing level y on a hearthstone laid in the floor under it, a HEARTH where
	 * the keeper stands (the cell toward {@code keeper}).
	 */
	public static void indoor(Draft d, int x, int y, int z, Direction keeper) {
		Mat stone = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.FLOORS)) {
			d.set(x, y - 1, z, stone.full());
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false)
				.trySetValue(BlockStateProperties.HORIZONTAL_FACING, keeper));
		}
		d.poi(PoiType.HEARTH, x + Dirs.dx(keeper), y, z + Dirs.dz(keeper), keeper.getOpposite());
	}

	/**
	 * A fire pit in the yard at (x, y, z) on its standing level: the campfire on a bed of stones, a pot (cauldron) beside
	 * it toward {@code pot}, a log laid as a seat two cells away toward {@code seat} where there is ground for it.
	 * Registers the HEARTH at the seat's side of the fire. Answers whether it was built (the cells must be free ground).
	 */
	public static boolean yard(Draft d, int x, int y, int z, Direction seat, Direction pot) {
		if (!Spots.standable(d, x, y, z) || Spots.claimed(d, x, y, z)) {
			return false;
		}
		Mat stone = d.mat(Role.FOUNDATION);
		Mat log = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, stone.full());
			d.set(x, y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false)
				.trySetValue(BlockStateProperties.HORIZONTAL_FACING, seat));
			// stones round it at the ground (where they are ground, not a path or a step)
			for (Direction dir : Dirs.HORIZONTAL) {
				int sx = x + Dirs.dx(dir);
				int sz = z + Dirs.dz(dir);
				var below = d.get(sx, y - 1, sz);
				if (Spots.standable(d, sx, y, sz) && (below == null || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)
					|| below.is(Blocks.COARSE_DIRT))) {
					d.set(sx, y - 1, sz, stone.full());
				}
			}
			int px = x + Dirs.dx(pot);
			int pz = z + Dirs.dz(pot);
			if (Spots.standable(d, px, y, pz) && !Spots.claimed(d, px, y, pz)) {
				d.set(px, y, pz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
			}
			int lx = x + Dirs.dx(seat) * 2;
			int lz = z + Dirs.dz(seat) * 2;
			if (Spots.standable(d, lx, y, lz) && !Spots.claimed(d, lx, y, lz)) {
				Direction.Axis along = Dirs.right(seat).getAxis();
				d.set(lx, y, lz, log.has(Form.STRIPPED_LOG) ? log.strippedLog(along) : log.log(along));
			}
		}
		int hx = x + Dirs.dx(seat);
		int hz = z + Dirs.dz(seat);
		if (Spots.standable(d, hx, y, hz)) {
			d.poi(PoiType.HEARTH, hx, y, hz, seat.getOpposite());
		}
		return true;
	}
}
