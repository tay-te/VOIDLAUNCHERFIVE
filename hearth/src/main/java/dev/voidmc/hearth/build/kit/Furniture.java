package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Single pieces of furniture, in the style's joinery and colours, each registering what folk use it for. {@code facing}
 * is always the way the piece faces into the room (a chair's sitter looks that way, a bed's head is against the opposite
 * wall... see each method). All pieces go in the INTERIOR stage.
 */
public final class Furniture {
	private Furniture() {
	}

	/** A bed whose head is at (x, y, z) against a wall, its foot one block toward {@code into}. Registers the bed. */
	public static void bed(Draft d, int x, int y, int z, Direction into) {
		Block bed = d.mat(Role.CLOTH).block(Form.BED, x, y, z);
		if (!(bed.defaultBlockState().hasProperty(BlockStateProperties.BED_PART))) {
			bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED);
		}
		Block b = bed;
		d.inStage(StageKind.INTERIOR, () -> d.bed(x + Dirs.dx(into), y, z + Dirs.dz(into), b, into.getOpposite()));
	}

	/** A table: a post with a top (pressure plate, carpet or slab). */
	public static void table(Draft d, int x, int y, int z) {
		Mat f = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, f.fence());
			double u = Noise.unit(d.seed() ^ 0x7AB, x, y, z);
			if (u < 0.5) {
				d.set(x, y + 1, z, f.plate());
			} else if (u < 0.8) {
				d.set(x, y + 1, z, d.mat(Role.CLOTH).carpet());
			} else {
				d.set(x, y + 1, z, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) (u * 3) % 3));
			}
		}
		d.poi(PoiType.TABLE, x, y, z, Dirs.FRONT);
	}

	/** A slab-topped table one block wide and {@code length} long. */
	public static void longTable(Draft d, int x, int y, int z, Direction run, int length) {
		Mat f = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, i == 0 || i == length - 1 ? f.fence() : f.slab(SlabType.TOP));
				if (i == 0 || i == length - 1) {
					d.set(px, y + 1, pz, f.plate());
				}
			}
		}
		d.poi(PoiType.TABLE, x, y, z, Dirs.right(run));
	}

	/** A chair: a stair whose back is toward {@code back}; the sitter faces the other way. Registers a seat. */
	public static void chair(Draft d, int x, int y, int z, Direction back) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, d.mat(Role.FURNITURE).stairsOrSlab(back, Half.BOTTOM));
		}
		d.poi(PoiType.SEAT, x, y, z, back.getOpposite());
	}

	/** A chest against a wall, facing into the room, with a few of the house's things in it. Registers storage. */
	public static void chest(Draft d, int x, int y, int z, Direction facing, List<Object[]> items) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.CHEST, facing), items.isEmpty() ? null : BlockEntities.container(items));
		}
		d.poi(PoiType.STORAGE, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite());
	}

	/** A barrel lying or standing; registers storage. */
	public static void barrel(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.BARREL, facing));
		}
	}

	/** A wall shelf with a few things on it, or a bookshelf, at (x, y, z) against the wall behind it. */
	public static void shelf(Draft d, int x, int y, int z, Direction facing, String kind) {
		Block shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (shelf != null) {
				List<Object[]> items = switch (kind) {
					case "kitchen" -> List.of(new Object[]{"minecraft:bread", 1, 0}, new Object[]{"minecraft:bowl", 1, 1}, new Object[]{"minecraft:apple", 2, 2});
					case "study" -> List.of(new Object[]{"minecraft:book", 1, 0}, new Object[]{"minecraft:writable_book", 1, 1}, new Object[]{"minecraft:candle", 1, 2});
					case "tools" -> List.of(new Object[]{"minecraft:iron_axe", 1, 0}, new Object[]{"minecraft:iron_shovel", 1, 1}, new Object[]{"minecraft:shears", 1, 2});
					default -> List.of(new Object[]{"minecraft:flower_pot", 1, 0}, new Object[]{"minecraft:candle", 1, 2});
				};
				d.set(x, y, z, States.facing(shelf, facing), BlockEntities.shelf(items));
			} else {
				d.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
			}
		}
	}

	/** A bookshelf. */
	public static void bookshelf(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
		}
	}

	/** A lantern hanging from the ceiling block at {@code ceilingY} on a chain of {@code links}. */
	public static void hangingLantern(Draft d, int x, int ceilingY, int z, int links) {
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		if (!(lantern.defaultBlockState().hasProperty(BlockStateProperties.HANGING))) {
			lantern = Blocks.LANTERN;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			int y = ceilingY - 1;
			for (int i = 0; i < links; i++) {
				d.set(x, y, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
				y--;
			}
			d.set(x, y, z, States.lantern(lantern, true));
		}
	}

	/** A torch on the wall behind (x, y, z), pointing {@code facing}. */
	public static void wallTorch(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.wallTorch(Blocks.TORCH, facing));
		}
	}

	/** A rug of carpet over a rectangle, a border in the second cloth colour. */
	public static void rug(Draft d, int x0, int z0, int x1, int z1, int y) {
		Mat cloth = d.mat(Role.CLOTH);
		var p = cloth.palette();
		Block main = p.main().block(Form.CARPET);
		Block border = p.entries().size() > 1 ? p.entries().get(1).material().block(Form.CARPET) : main;
		if (main == null) {
			return;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (!d.isEmpty(x, y, z)) {
						continue;
					}
					boolean edge = x == Math.min(x0, x1) || x == Math.max(x0, x1) || z == Math.min(z0, z1) || z == Math.max(z0, z1);
					d.set(x, y, z, (edge && border != null ? border : main).defaultBlockState());
				}
			}
		}
	}

	/**
	 * A workstation for a job at (x, y, z), facing into the room; the worker's spot is the cell in front of it, which is
	 * registered as the job site.
	 */
	public static void workstation(Draft d, JobType job, int x, int y, int z, Direction facing) {
		Block b = switch (job) {
			case BUILDER -> Blocks.CRAFTING_TABLE;
			case MASON -> Blocks.STONECUTTER;
			case SMITH -> Blocks.SMITHING_TABLE;
			case CARPENTER -> Blocks.CRAFTING_TABLE;
			case BAKER -> Blocks.SMOKER;
			case BREWER -> Blocks.BREWING_STAND;
			case TAILOR -> Blocks.LOOM;
			case SCHOLAR, PRIEST -> Blocks.LECTERN;
			case FARMER -> Blocks.COMPOSTER;
			case FISHER -> Blocks.BARREL;
			case HUNTER, FORESTER -> Blocks.FLETCHING_TABLE;
			case MERCHANT, INNKEEPER -> Blocks.BARREL;
			case MINTER -> Blocks.SMITHING_TABLE;
			case GUARD -> Blocks.GRINDSTONE;
			case QUARRIER -> Blocks.STONECUTTER;
			case HERDER -> Blocks.CAULDRON;
			case COURIER -> Blocks.CARTOGRAPHY_TABLE;
			default -> Blocks.CRAFTING_TABLE;
		};
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (b == Blocks.GRINDSTONE) {
				d.set(x, y, z, States.attached(b, AttachFace.FLOOR, facing));
			} else if (b == Blocks.LECTERN) {
				d.set(x, y, z, States.facing(b, facing));
			} else {
				d.set(x, y, z, States.facing(b, facing));
			}
		}
		d.poi(PoiType.JOB_SITE, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite(), job);
	}

	/**
	 * A hearth in a wall: the fire (a furnace or smoker, or a lit campfire in a stone surround) at (x, y, z) facing into
	 * the room, a mantel above. Registers the hearth. The chimney is the caller's (it starts above this).
	 */
	public static void hearth(Draft d, int x, int y, int z, Direction facing, boolean openFire) {
		Mat stone = d.mat(Role.CHIMNEY);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (openFire) {
				d.set(x, y, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing)
					.trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			} else {
				d.set(x, y, z, States.facing(Noise.unit(d.seed(), x, y, z) < 0.5 ? Blocks.FURNACE : Blocks.SMOKER, facing));
			}
			d.set(x, y + 1, z, stone.full());
			int mx = x + Dirs.dx(facing);
			int mz = z + Dirs.dz(facing);
			if (d.isAbsent(mx, y + 2, mz) || d.isEmpty(mx, y + 2, mz)) {
				d.set(mx, y + 2, mz, stone.stairsOrSlab(facing.getOpposite(), Half.TOP));
			}
		}
		d.poi(PoiType.HEARTH, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite());
	}

	/** A kitchen counter run: smoker, barrels and a cauldron with slab tops between, against a wall. */
	public static void counter(Draft d, int x, int y, int z, Direction facing, Direction run, int length) {
		Mat f = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				double u = Noise.unit(d.seed() ^ 0xC0, px, y, pz);
				if (i == 0) {
					d.set(px, y, pz, States.facing(Blocks.SMOKER, facing));
				} else if (u < 0.3) {
					d.set(px, y, pz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				} else if (u < 0.65) {
					d.set(px, y, pz, States.facing(Blocks.BARREL, facing));
				} else {
					d.set(px, y, pz, f.trapdoor(facing, Half.TOP, false));
				}
			}
		}
	}
}
