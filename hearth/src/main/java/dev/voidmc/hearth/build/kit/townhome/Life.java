package dev.voidmc.hearth.build.kit.townhome;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The signs of people living high up in a crowded town: washing on lines across roof terraces and back yards, sheets
 * hung out of windows, rugs airing over balustrades, roof gardens of raised beds and pots under a pergola, a water tank
 * on a tenement roof. Everything stands on, hangs from or is strung between what the house itself built.
 */
public final class Life {
	private Life() {
	}

	private static final Block[] SHEETS = {Blocks.BIRCH_TRAPDOOR, Blocks.PALE_OAK_TRAPDOOR, Blocks.BIRCH_TRAPDOOR, Blocks.CHERRY_TRAPDOOR,
		Blocks.BAMBOO_TRAPDOOR};

	/**
	 * A washing line strung between two anchors (walls or posts the caller built) along a straight run, the string at
	 * height y over the cells between them, washing (sheets and shirts) hanging under every cell but the ends.
	 */
	public static void line(Draft d, int ax, int az, int bx, int bz, int y) {
		boolean alongX = az == bz;
		int n = alongX ? Math.abs(bx - ax) : Math.abs(bz - az);
		if (n < 2) {
			return;
		}
		int sx = Integer.signum(bx - ax);
		int sz = Integer.signum(bz - az);
		BlockState string = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, false)
			.setValue(alongX ? TripWireBlock.EAST : TripWireBlock.NORTH, true).setValue(alongX ? TripWireBlock.WEST : TripWireBlock.SOUTH, true);
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int k = 1; k < n; k++) {
				int x = ax + sx * k;
				int z = az + sz * k;
				if (!Fronts.allowed(d, x, y, z) || !d.isEmpty(x, y, z)) {
					return;
				}
			}
			for (int k = 1; k < n; k++) {
				int x = ax + sx * k;
				int z = az + sz * k;
				d.set(x, y, z, string);
				double u = Noise.unit(d.seed() ^ 0x1A0D, x, y, z);
				if (u < 0.75 && d.isEmpty(x, y - 1, z) && Fronts.allowed(d, x, y - 1, z) && y - 1 > d.site().ground(x, z) + 1) {
					Block sheet = SHEETS[(int) (u * 13) % SHEETS.length];
					Direction face = alongX ? (u < 0.37 ? Dirs.FRONT : Dirs.BACK) : (u < 0.37 ? Dirs.LEFT : Dirs.RIGHT);
					d.set(x, y - 1, z, States.trapdoor(sheet, face, Half.TOP, true));
				}
			}
		}
	}

	/**
	 * A sheet or a bright cloth hung out of a window: a wall banner of the house's cloth on the wall face at cell i,
	 * height y (it hangs down from there), at least three up over the street.
	 */
	public static void sheet(Draft d, Side s, int i, int y) {
		int x = s.outX(i, 1);
		int z = s.outZ(i, 1);
		if (!Fronts.allowed(d, x, y, z) || !Fronts.allowed(d, x, y - 1, z) || !d.isAbsent(x, y, z) || !d.isAbsent(x, y - 1, z)
			|| !dev.voidmc.hearth.build.kit.Openings.isWallBlock(d, s.x(i), y, s.z(i))) {
			return;
		}
		double u = Noise.unit(d.seed() ^ 0x5EE7, x, y, z);
		Block banner = u < 0.5 ? Blocks.WALL_BANNER.pick(net.minecraft.world.item.DyeColor.WHITE) : d.mat(Role.CLOTH).mainOr(Form.WALL_BANNER, Blocks.WALL_BANNER.pick(net.minecraft.world.item.DyeColor.WHITE));
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, States.facing(banner, s.out())));
	}

	/** A rug airing over a balustrade: a carpet laid on top of a fence rail (it rests on the rail). */
	public static void airing(Draft d, int x, int y, int z) {
		BlockState below = d.get(x, y - 1, z);
		if (below == null || !(below.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) || !d.isAbsent(x, y, z)) {
			return;
		}
		Block carpet = d.mat(Role.CLOTH).block(Form.CARPET, x, y, z);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, carpet.defaultBlockState()));
	}

	/**
	 * A roof garden over a deck rectangle (standing level y, the cells inside the parapet): raised beds along the
	 * parapet planted from the style's garden (or its crops), pots, a pergola of posts carrying a roof of leaves over a
	 * bench and a table, a washing line across the far end. Keeps {@code keepX, keepZ} (the way out of the stair house
	 * or the door) and its neighbours free.
	 */
	public static void roofGarden(Draft d, int x0, int z0, int x1, int z1, int y, int keepX, int keepZ, boolean tank) {
		int w = x1 - x0 + 1;
		int dp = z1 - z0 + 1;
		if (w < 3 || dp < 3) {
			return;
		}
		Mat fence = d.mat(Role.FENCE);
		Mat leaves = d.mat(Role.HEDGE);
		Mat cloth = d.mat(Role.CLOTH);
		java.util.function.BiPredicate<Integer, Integer> free = (x, z) -> Math.abs(x - keepX) + Math.abs(z - keepZ) > 1 && d.isEmpty(x, y, z)
			&& d.isSolid(x, y - 1, z);
		// raised beds against the parapet on the long sides, every other stretch
		try (var st = d.stage(StageKind.EXTERIOR)) {
			for (int x = x0; x <= x1; x++) {
				for (int z : new int[]{z0, z1}) {
					double u = Noise.unit(d.seed() ^ 0x6A4D, x, y, z);
					if (!free.test(x, z) || u > 0.55) {
						continue;
					}
					d.set(x, y, z, Blocks.ROOTED_DIRT.defaultBlockState());
					Block plant = u < 0.3 ? d.mat(Role.GARDEN).block(Form.FULL, x, y + 1, z) : d.mat(Role.CROPS).block(Form.FULL, x, y + 1, z);
					BlockState ps = plant instanceof net.minecraft.world.level.block.CropBlock ? States.crop(plant, 7) : plant.defaultBlockState();
					if (plant.defaultBlockState().hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
						if (y + 2 < d.maxHeight()) {
							d.tallPlant(x, y + 1, z, plant);
						}
					} else if (plant instanceof net.minecraft.world.level.block.CropBlock) {
						d.set(x, y, z, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
						d.set(x, y + 1, z, ps);
					} else {
						d.set(x, y + 1, z, ps);
					}
				}
			}
		}
		// a pergola over a table and bench in the corner away from the way out
		int px = keepX - x0 < w / 2 ? x1 - 2 : x0;
		int pz = keepZ - z0 < dp / 2 ? z1 - 2 : z0;
		if (w >= 5 && dp >= 4 && y + 3 < d.maxHeight()) {
			int qx0 = Math.max(x0, px);
			int qz0 = Math.max(z0, pz);
			int qx1 = Math.min(x1, qx0 + 2);
			int qz1 = Math.min(z1, qz0 + 2);
			boolean clear = true;
			for (int x = qx0; x <= qx1; x++) {
				for (int z = qz0; z <= qz1; z++) {
					clear &= Math.abs(x - keepX) + Math.abs(z - keepZ) > 1;
				}
			}
			if (clear) {
				try (var st = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : new int[][]{{qx0, qz0}, {qx1, qz0}, {qx0, qz1}, {qx1, qz1}}) {
						d.set(c[0], y, c[1], fence.fence());
						d.set(c[0], y + 1, c[1], fence.fence());
					}
					for (int x = qx0; x <= qx1; x++) {
						for (int z = qz0; z <= qz1; z++) {
							boolean edge = x == qx0 || x == qx1 || z == qz0 || z == qz1;
							if (leaves.has(Form.FULL) && (edge || Noise.unit(d.seed(), x, y + 2, z) < 0.5)) {
								d.set(x, y + 2, z, leaves.leaves());
							} else {
								d.set(x, y + 2, z, cloth.slab(SlabType.BOTTOM));
							}
						}
					}
				}
				int cx = (qx0 + qx1) / 2;
				int cz = (qz0 + qz1) / 2;
				if (d.isEmpty(cx, y, cz)) {
					Furniture.table(d, cx, y, cz);
					int sx = cx + (qx1 > cx ? 0 : 0);
					int sz = cz + 1 <= qz1 ? cz + 1 : cz - 1;
					if (d.isEmpty(sx, y, sz)) {
						Furniture.chair(d, sx, y, sz, sz > cz ? Dirs.BACK : Dirs.FRONT);
					}
					d.poi(PoiType.VIEWPOINT, cx, y, cz + (sz > cz ? -1 : 1), Dirs.FRONT);
				}
			}
		}
		// pots along the rest of the parapet's foot
		for (int z = z0 + 1; z < z1; z++) {
			for (int x : new int[]{x0, x1}) {
				if (free.test(x, z) && Noise.unit(d.seed() ^ 0x7077, x, y, z) < 0.3) {
					Garden.pot(d, x, y, z);
				}
			}
		}
		// a water tank: barrels standing together on the deck, a cauldron
		if (tank) {
			int tx = keepX - x0 < w / 2 ? x1 : x0;
			int tz = keepZ - z0 < dp / 2 ? z1 : z0;
			if (free.test(tx, tz)) {
				d.inStage(StageKind.EXTERIOR, () -> {
					d.set(tx, y, tz, States.facing(Blocks.BARREL, Direction.UP));
					if (y + 1 < d.maxHeight()) {
						d.set(tx, y + 1, tz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
					}
				});
			}
		}
	}
}
