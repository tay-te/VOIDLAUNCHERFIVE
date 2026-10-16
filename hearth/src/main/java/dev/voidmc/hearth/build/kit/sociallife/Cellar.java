package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.kit.trade.Floorplan;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A cellar dug under a floor: walls of the foundation stone, a floor of flags, three rows of headroom under the boards
 * of the room above, lanterns hung from those boards. Where the ground falls away under it (a hillside) its walls go
 * down to the ground and stand as a basement storey.
 */
public final class Cellar {
	private Cellar() {
	}

	/** Air rows in a cellar. */
	public static final int HEADROOM = 3;

	/**
	 * Digs a cellar under {@code [x0..x1] × [z0..z1]} whose ceiling is the floor row {@code ceilingY} of the room above
	 * (that floor must be drawn solid over it). Its standing level is {@code ceilingY - HEADROOM}; returns it. Call after
	 * the footings of the building over it (it replaces their fill).
	 */
	public static int dig(Draft d, int x0, int z0, int x1, int z1, int ceilingY) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		int stand = ceilingY - HEADROOM;
		int floor = stand - 1;
		Mat stone = d.mat(Role.FOUNDATION);
		Mat flags = d.mat(Role.PAVING);
		Mat fill = d.mat(Role.FILL);
		// the walls, one cell round, from the floor course (or the ground, lower) up to under the ceiling
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = az - 1; z <= bz + 1; z++) {
				for (int x = ax - 1; x <= bx + 1; x++) {
					boolean ring = x == ax - 1 || x == bx + 1 || z == az - 1 || z == bz + 1;
					int g = d.site().ground(x, z);
					if (ring) {
						for (int y = Math.min(floor, g - 2); y < ceilingY; y++) {
							d.set(x, y, z, stone.full());
						}
					} else {
						for (int y = Math.min(floor, g - 2); y < floor; y++) {
							d.set(x, y, z, fill.full());
						}
						d.set(x, floor, z, flags.full());
					}
				}
			}
		}
		d.clearBox(ax, stand, az, bx, ceilingY - 1, bz);
		return stand;
	}

	/**
	 * Stocks a dug cellar: casks lying in racks two high against its walls (a cradle of slabs under the lower one where
	 * the wall run is long), sacks in the corners (a cask there would have no face to tap), a chest of the house's stores
	 * (STORAGE) where it can be reached, lanterns hung from the ceiling every few cells. {@code plan} is the cellar's
	 * floor with the stair foot kept.
	 */
	public static void stock(Draft d, Floorplan plan, int ceilingY, List<Object[]> stores) {
		int y = plan.y;
		Mat wood = d.mat(Role.FURNITURE);
		boolean chest = false;
		for (Direction wall : Dirs.HORIZONTAL) {
			for (int[] c : plan.along(wall)) {
				if (!plan.free(c[0], c[1])) {
					continue;
				}
				Direction into = wall.getOpposite();
				double u = Noise.unit(d.seed() ^ 0xCE11A, c[0], y, c[1]);
				if (!chest && u < 0.25 && plan.take(c[0], c[1], into)) {
					try (var s = d.stage(StageKind.INTERIOR)) {
						d.set(c[0], y, c[1], States.facing(Blocks.CHEST, into), dev.voidmc.hearth.build.draft.BlockEntities.container(stores));
					}
					d.poi(PoiType.STORAGE, c[0] + Dirs.dx(into), y, c[1] + Dirs.dz(into), wall);
					chest = true;
					continue;
				}
				if (!plan.take(new int[][]{c}, null)) {
					continue;
				}
				// a corner, where two walls of casks would meet, takes a sack (a bale) instead: no cask without a face
				if ((c[0] == plan.x0 || c[0] == plan.x1) && (c[1] == plan.z0 || c[1] == plan.z1)) {
					if (u < 0.7) {
						d.inStage(StageKind.INTERIOR, () -> d.set(c[0], y, c[1], Blocks.HAY_BLOCK.defaultBlockState()));
					}
					continue;
				}
				Direction lying = Dirs.right(wall);
				try (var s = d.stage(StageKind.INTERIOR)) {
					if (u < 0.6) {
						// a cask on its side, a second over it
						d.set(c[0], y, c[1], States.facing(Blocks.BARREL, lying));
						d.set(c[0], y + 1, c[1], States.facing(Blocks.BARREL, lying));
					} else if (u < 0.75) {
						d.set(c[0], y, c[1], wood.slab(SlabType.TOP));
						d.set(c[0], y + 1, c[1], States.facing(Blocks.BARREL, lying));
					} else if (u < 0.88) {
						d.set(c[0], y, c[1], Blocks.HAY_BLOCK.defaultBlockState());
					} else {
						d.set(c[0], y, c[1], States.facing(Blocks.BARREL, Direction.UP));
						BlockState top = Noise.unit(d.seed(), c[0], y + 1, c[1]) < 0.5 ? Blocks.DECORATED_POT.defaultBlockState() : null;
						if (top != null) {
							d.set(c[0], y + 1, c[1], top);
						}
					}
				}
			}
		}
		// lanterns hung from the boards overhead, along the middle
		int cx = (plan.x0 + plan.x1) / 2;
		int cz = (plan.z0 + plan.z1) / 2;
		boolean alongX = plan.x1 - plan.x0 >= plan.z1 - plan.z0;
		int len = alongX ? plan.x1 - plan.x0 : plan.z1 - plan.z0;
		for (int k = 1; k <= len; k += 4) {
			int lx = alongX ? plan.x0 + k : cx;
			int lz = alongX ? cz : plan.z0 + k;
			if (d.isEmpty(lx, ceilingY - 1, lz) && d.isSolid(lx, ceilingY, lz)) {
				Lighting.chandelier(d, lx, lz, ceilingY, ceilingY - 1);
			}
		}
	}
}
