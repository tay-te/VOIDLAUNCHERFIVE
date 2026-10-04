package dev.voidmc.hearth.build.kit.station;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furniture of reading and writing, for the homes of those who live by the book (the priest, the scribe, the reeve
 * keeping his rolls, the merchant his ledgers): a lectern with its book open (LECTERN where the reader stands), a desk of
 * a board on trestles with a stool, candles and an inkwell, shelves of books and chiseled shelves half full, books in
 * stacks. Every piece sits on the floor at standing level y and claims its cells in the room it is put in, so the room
 * stays walkable.
 */
public final class Study {
	private Study() {
	}

	/**
	 * A lectern at (x, y, z) facing {@code facing} (its reader stands in the next cell that way), with a book open on it.
	 * Registers LECTERN where the reader stands.
	 */
	public static void lectern(Draft d, int x, int y, int z, Direction facing, String title, List<String> pages) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, States.facing(Blocks.LECTERN, facing).trySetValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true),
				BlockEntities.lectern(title, "", pages));
		}
		d.poi(PoiType.LECTERN, x + Dirs.dx(facing), y, z + Dirs.dz(facing), facing.getOpposite());
	}

	/**
	 * A writing desk against a wall: the board at (x, y, z) (a top slab of the furniture's wood, or a closed trapdoor on a
	 * post), candles on it, a stool in front of it toward {@code facing}. Registers TABLE at the desk and SEAT at the stool.
	 * Answers whether it was placed (the room must spare the desk's cell and the stool's).
	 */
	public static boolean desk(Draft d, Rooms.Room room, int x, int z, Direction facing, int candles) {
		int sx = x + Dirs.dx(facing);
		int sz = z + Dirs.dz(facing);
		if (!room.inside(sx, sz) || !d.isEmpty(x, room.y, z) || !d.isEmpty(sx, room.y, sz) || !room.claim(x, z)) {
			return false;
		}
		Mat wood = d.mat(Role.FURNITURE);
		int y = room.y;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, wood.has(Form.SLAB) ? wood.slab(SlabType.TOP) : wood.full());
		}
		if (candles > 0 && room.height >= 2) {
			Lighting.candles(d, x, y + 1, z, candles);
		}
		dev.voidmc.hearth.build.kit.Furniture.chair(d, sx, y, sz, facing);
		d.poi(PoiType.TABLE, sx, y, sz, facing.getOpposite());
		return true;
	}

	/**
	 * Books along a wall from a cell: a bookshelf (or a chiseled shelf, half filled, every other) {@code high} blocks tall
	 * at (x, y, z). Claims the cell in the room.
	 */
	public static boolean shelf(Draft d, Rooms.Room room, int x, int z, int high) {
		if (!room.claim(x, z)) {
			return false;
		}
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int k = 0; k < high && k < room.height; k++) {
				double u = Noise.unit(d.seed() ^ 0xB00C, x, room.y + k, z);
				d.set(x, room.y + k, z, (u < 0.3 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF).defaultBlockState());
			}
		}
		return true;
	}

	/**
	 * Books stacked on a surface at (x, y, z): a chiseled shelf turned up as a box of books is not a vanilla thing, so a
	 * stack reads as a slab of the furniture's wood with a candle or a flower pot on it now and then, or a book on a
	 * wall shelf where the furniture's wood cuts one.
	 */
	public static void stack(Draft d, int x, int y, int z, Direction facing) {
		Block shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (shelf != null) {
				d.set(x, y, z, States.facing(shelf, facing), BlockEntities.shelf(List.of(new Object[]{"minecraft:book", 2, 0},
					new Object[]{"minecraft:writable_book", 1, 1}, new Object[]{"minecraft:book", 3, 2})));
			} else {
				d.set(x, y, z, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
			}
		}
	}

	/** Whether a state is one of the study's pieces (a shelf of books, a lectern). */
	public static boolean isBooks(BlockState s) {
		return s.is(Blocks.BOOKSHELF) || s.is(Blocks.CHISELED_BOOKSHELF) || s.is(Blocks.LECTERN);
	}

	/** An inkwell's place: a dark candle (unlit) beside the writing, read as ink and quill. */
	public static BlockState ink() {
		return Blocks.DYED_CANDLE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
	}

	/** Unused bracket helper kept for desks on a wall rail. */
	static BlockState rail(Mat wood, Direction facing) {
		return wood.trapdoor(facing, Half.TOP, false).at(0, 0, 0);
	}
}
