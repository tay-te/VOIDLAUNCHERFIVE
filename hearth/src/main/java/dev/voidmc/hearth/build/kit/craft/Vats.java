package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Vats and pits: the tanner's lime and bark pits sunk in the yard, the dyer's round vats, a chandler's tallow kettle
 * over its fire. A pit's liquor is clear water over a tinting bed (white lime, brown tan liquor, the dyer's woad and
 * madder), so in the world the pit shows its colour through the water the way a real one does. Water never runs: every
 * liquor cell is closed in by its kerb and its bed.
 */
public final class Vats {
	private Vats() {
	}

	/** What is in a vat, by the bed that tints the water over it. */
	public enum Liquor {
		WATER, LIME, TAN, BARK, PULP, RED, BLUE, YELLOW, GREEN, PURPLE, BLACK, ORANGE;

		/** The dyer's colours, for vats of cloth being dyed. */
		public static final Liquor[] DYES = {RED, BLUE, YELLOW, GREEN, PURPLE, BLACK, ORANGE};

		/** The block under the water that gives the liquor its colour. */
		public Block bed() {
			return switch (this) {
				case WATER -> Blocks.GRAVEL;
				case LIME -> Blocks.CALCITE;
				case TAN -> Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN);
				case BARK -> Blocks.PACKED_MUD;
				case PULP -> Blocks.CONCRETE.pick(DyeColor.WHITE);
				case RED -> Blocks.CONCRETE.pick(DyeColor.RED);
				case BLUE -> Blocks.CONCRETE.pick(DyeColor.BLUE);
				case YELLOW -> Blocks.CONCRETE.pick(DyeColor.YELLOW);
				case GREEN -> Blocks.CONCRETE.pick(DyeColor.GREEN);
				case PURPLE -> Blocks.CONCRETE.pick(DyeColor.PURPLE);
				case BLACK -> Blocks.CONCRETE.pick(DyeColor.BLACK);
				case ORANGE -> Blocks.CONCRETE.pick(DyeColor.ORANGE);
			};
		}
	}

	/**
	 * A pit sunk into the ground over cells x0..x1 × z0..z1 at standing level y: the liquor's surface in the floor row
	 * (y - 1) over its bed, closed in by a kerb of {@code kerb} in the floor row round it (where nothing is drawn yet or
	 * it is ground), the ground made up under the bed. Returns the pit's cells.
	 */
	public static List<int[]> pit(Draft d, int x0, int z0, int x1, int z1, int y, Liquor liquor, Mat kerb) {
		List<int[]> cells = new ArrayList<>();
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = az - 1; z <= bz + 1; z++) {
				for (int x = ax - 1; x <= bx + 1; x++) {
					if (!Walls.inPlot(d, x, z)) {
						continue;
					}
					boolean inside = x >= ax && x <= bx && z >= az && z <= bz;
					if (inside) {
						d.set(x, y - 3, z, kerb.full());
						d.set(x, y - 2, z, liquor.bed().defaultBlockState());
						d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
						cells.add(new int[]{x, z});
					} else {
						BlockState at = d.get(x, y - 1, z);
						if (at == null || at.isAir() || at.is(Blocks.GRASS_BLOCK) || at.is(Blocks.DIRT) || at.is(Blocks.DIRT_PATH)
							|| at.is(Blocks.COARSE_DIRT) || at.is(Blocks.GRAVEL) || at.is(Blocks.SAND) || at.is(Blocks.PACKED_MUD)) {
							d.set(x, y - 1, z, kerb.full());
						}
						BlockState under = d.get(x, y - 2, z);
						if (under == null || under.isAir() || under.is(Blocks.WATER)) {
							d.set(x, y - 2, z, kerb.full());
						}
					}
				}
			}
		}
		return cells;
	}

	/**
	 * A round vat standing on the floor at (cx, y, cz): a ring of staves (the furniture wood's stripped logs, or barrels
	 * where the wood has none) round the middle cell, the four corners stepped down as the ring rounds off, the liquor
	 * level with the rim over its bed. Needs the 3 × 3 round (cx, cz) free.
	 */
	public static void tub(Draft d, int cx, int y, int cz, Liquor liquor, Mat wood) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					if (dx == 0 && dz == 0) {
						d.set(x, y - 1, z, liquor.bed().defaultBlockState());
						d.set(x, y, z, Blocks.WATER.defaultBlockState());
						continue;
					}
					boolean corner = dx != 0 && dz != 0;
					if (corner) {
						// the ring rounding off: a stair whose high side faces the vat's middle
						Direction toMid = Math.abs(dx) > 0 ? (dx > 0 ? Dirs.LEFT : Dirs.RIGHT) : (dz > 0 ? Dirs.FRONT : Dirs.BACK);
						d.set(x, y, z, wood.has(Form.STAIRS) ? wood.stairs(toMid, Half.BOTTOM) : wood.slab(SlabType.BOTTOM));
					} else {
						Direction.Axis along = dx != 0 ? Direction.Axis.Z : Direction.Axis.X;
						d.set(x, y, z, wood.has(Form.STRIPPED_LOG) ? wood.strippedLog(along) : wood.full());
					}
				}
			}
		}
	}

	/** A sunken round vat (the dyers' and tanners' of the south): the liquor at y - 1 in a ring of the kerb, its corners kept. */
	public static void sunkRound(Draft d, int cx, int y, int cz, Liquor liquor, Mat kerb) {
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					if (!Walls.inPlot(d, x, z)) {
						continue;
					}
					if (dx == 0 && dz == 0) {
						d.set(x, y - 3, z, kerb.full());
						d.set(x, y - 2, z, liquor.bed().defaultBlockState());
						d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else {
						d.set(x, y - 2, z, kerb.full());
						d.set(x, y - 1, z, kerb.full());
						// the rim: a low kerb stone round it, the corners rounded with a slab
						boolean corner = dx != 0 && dz != 0;
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, kerb.slab(SlabType.BOTTOM));
						}
					}
				}
			}
			// the rim's stairs face outward (their low side to the walker)
			for (Direction dir : Dirs.HORIZONTAL) {
				int x = cx + Dirs.dx(dir);
				int z = cz + Dirs.dz(dir);
				if (Walls.inPlot(d, x, z) && kerb.has(Form.STAIRS)) {
					d.set(x, y, z, kerb.stairs(dir.getOpposite(), Half.BOTTOM));
				}
			}
		}
	}

	/**
	 * A kettle over its fire: a lit campfire at (x, y, z) with a cauldron of {@code fill} on it (water, or powder snow
	 * for melted wax and tallow). Returns the cauldron's y.
	 */
	public static int kettle(Draft d, int x, int y, int z, boolean wax) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			d.set(x, y + 1, z, wax ? Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)
				: Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		return y + 1;
	}

	/** A cauldron standing on the floor at (x, y, z): water, or powder snow for wax and tallow. */
	public static void cauldron(Draft d, int x, int y, int z, boolean wax) {
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, wax ? Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)
			: Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3)));
	}

	/**
	 * A low rim round a pit over x0..x1 × z0..z1: bottom slabs of the kerb stone in the ring of cells round it at
	 * standing level y, wherever nothing stands there, so the pit's water lies a step down below its lip.
	 */
	public static void rim(Draft d, int x0, int z0, int x1, int z1, int y, Mat kerb) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : Draft.ring(Math.min(x0, x1) - 1, Math.min(z0, z1) - 1, Math.max(x0, x1) + 1, Math.max(z0, z1) + 1)) {
				if (Walls.inPlot(d, c[0], c[1]) && d.isAbsent(c[0], y, c[1]) && d.isSolid(c[0], y - 1, c[1])) {
					d.set(c[0], y, c[1], kerb.slab(SlabType.BOTTOM));
				}
			}
		}
	}
}
