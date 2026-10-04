package dev.voidmc.hearth.build.kit.learning;

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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furniture of learning: what fills a laboratory, a scriptorium, a library and a study. Brewing stands with their
 * bottles, crucibles, shelves of bottles and jars, carrels with a lectern and a stool, desks under a candle, chiseled
 * shelves of books, a reading lectern with a book open on it. Every piece stands on the floor (or on a counter) at the
 * cell given, faces into the room, and registers what folk use it for. All in the INTERIOR stage.
 */
public final class Studies {
	private Studies() {
	}

	private static boolean free(Draft d, int x, int y, int z) {
		return d.isEmpty(x, y, z) && Sacra.ok(d, x, y, z);
	}

	/**
	 * A brewing stand showing its three bottles (the stand is given bottles to keep them), standing on whatever is below.
	 */
	public static void brewingStand(Draft d, int x, int y, int z) {
		if (!Sacra.ok(d, x, y, z)) {
			return;
		}
		BlockState s = Blocks.BREWING_STAND.defaultBlockState().setValue(BlockStateProperties.HAS_BOTTLE_0, true)
			.setValue(BlockStateProperties.HAS_BOTTLE_1, true).setValue(BlockStateProperties.HAS_BOTTLE_2, Noise.unit(d.seed(), x, y, z) < 0.7);
		List<Object[]> items = new ArrayList<>();
		items.add(new Object[]{"minecraft:glass_bottle", 1, 0});
		items.add(new Object[]{"minecraft:glass_bottle", 1, 1});
		if (s.getValue(BlockStateProperties.HAS_BOTTLE_2)) {
			items.add(new Object[]{"minecraft:glass_bottle", 1, 2});
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, s, BlockEntities.container(items)));
	}

	/** A crucible: a cauldron (water in it, or empty and sooty), at (x, y, z). */
	public static void crucible(Draft d, int x, int y, int z, boolean water) {
		BlockState s = water ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 2) : Blocks.CAULDRON.defaultBlockState();
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, s));
	}

	/**
	 * A shelf of bottles and jars against the wall behind (x, y, z), facing {@code into} the room: the style's wooden
	 * shelf holding glass bottles, honey, a potion, or a bookshelf where the style's wood has no shelf.
	 */
	public static void bottleShelf(Draft d, int x, int y, int z, Direction into) {
		if (!Sacra.ok(d, x, y, z)) {
			return;
		}
		Block shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		double u = Noise.unit(d.seed() ^ 0xB077, x, y, z);
		String[][] sets = {
			{"minecraft:glass_bottle", "minecraft:honey_bottle", "minecraft:glass_bottle"},
			{"minecraft:experience_bottle", "minecraft:glass_bottle", "minecraft:redstone"},
			{"minecraft:glowstone_dust", "minecraft:glass_bottle", "minecraft:sugar"},
			{"minecraft:glass_bottle", "minecraft:fermented_spider_eye", "minecraft:glass_bottle"},
			{"minecraft:gunpowder", "minecraft:glass_bottle", "minecraft:brown_mushroom"}};
		String[] set = sets[(int) (u * sets.length)];
		List<Object[]> items = List.of(new Object[]{set[0], 1, 0}, new Object[]{set[1], 1, 1}, new Object[]{set[2], 1, 2});
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (shelf != null) {
				d.set(x, y, z, States.facing(shelf, into), BlockEntities.shelf(items));
			} else {
				d.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
			}
		}
	}

	/** A chiseled shelf of books against the wall behind (x, y, z), facing {@code into}, some slots filled. */
	public static void bookShelf(Draft d, int x, int y, int z, Direction into) {
		if (!Sacra.ok(d, x, y, z)) {
			return;
		}
		BlockState s = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, into);
		List<Object[]> books = new ArrayList<>();
		var props = new net.minecraft.world.level.block.state.properties.BooleanProperty[]{BlockStateProperties.SLOT_0_OCCUPIED,
			BlockStateProperties.SLOT_1_OCCUPIED, BlockStateProperties.SLOT_2_OCCUPIED, BlockStateProperties.SLOT_3_OCCUPIED,
			BlockStateProperties.SLOT_4_OCCUPIED, BlockStateProperties.SLOT_5_OCCUPIED};
		for (int i = 0; i < 6; i++) {
			if (Noise.unit(d.seed() ^ 0xB00C, x * 7 + i, y, z) < 0.72) {
				s = s.setValue(props[i], true);
				books.add(new Object[]{i % 3 == 2 ? "minecraft:writable_book" : "minecraft:book", 1, i});
			}
		}
		BlockState ss = s;
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, ss, books.isEmpty() ? null : BlockEntities.container(books)));
	}

	/** Bookshelves from y up {@code rows} against the wall behind (x, z), the lowest chiseled. */
	public static void books(Draft d, int x, int y, int z, int rows, Direction into) {
		for (int k = 0; k < rows; k++) {
			if (!free(d, x, y + k, z)) {
				return;
			}
			if (k == 0 || Noise.unit(d.seed() ^ 0x5B, x, y + k, z) < 0.25) {
				bookShelf(d, x, y + k, z, into);
			} else {
				int yy = y + k;
				d.inStage(StageKind.INTERIOR, () -> d.set(x, yy, z, Blocks.BOOKSHELF.defaultBlockState()));
			}
		}
	}

	/**
	 * A lectern with a book lying open on it at (x, y, z), facing {@code into}: the reader stands in front of it. With a
	 * job it is that worker's place (JOB_SITE); it is always a LECTERN for folk to read at.
	 */
	public static void lectern(Draft d, int x, int y, int z, Direction into, JobType job, String title) {
		if (!Sacra.ok(d, x, y, z)) {
			return;
		}
		BlockState s = Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, into)
			.setValue(BlockStateProperties.HAS_BOOK, true);
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, s, BlockEntities.lectern(title, "", List.of(title))));
		int fx = x + Dirs.dx(into);
		int fz = z + Dirs.dz(into);
		d.poi(PoiType.LECTERN, fx, y, fz, into.getOpposite());
		if (job != null && job != JobType.NONE) {
			d.poi(PoiType.JOB_SITE, fx, y, fz, into.getOpposite(), job);
		}
	}

	/**
	 * A carrel: a desk (a slab on a post, or a top trapdoor) at (x, y, z) against the wall, a candle and a book on it, the
	 * stool in front (a seat facing the desk). Registers TABLE and SEAT.
	 */
	public static void desk(Draft d, int x, int y, int z, Direction into) {
		desk(d, x, y, z, into, true);
	}

	/** {@link #desk}, the stool left out where the cell in front must stay a walk. */
	public static void desk(Draft d, int x, int y, int z, Direction into, boolean stool) {
		if (!Sacra.ok(d, x, y, z)) {
			return;
		}
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, wood.slab(SlabType.TOP));
		}
		double u = Noise.unit(d.seed() ^ 0xDE5C, x, y, z);
		if (u < 0.5) {
			Sacra.candles(d, x, y + 1, z, 1 + (int) (u * 4), StageKind.INTERIOR);
		} else if (u < 0.8) {
			d.inStage(StageKind.INTERIOR, () -> d.set(x, y + 1, z, Blocks.DECORATED_POT.defaultBlockState()));
		}
		d.poi(PoiType.TABLE, x, y, z, into.getOpposite());
		int sx = x + Dirs.dx(into);
		int sz = z + Dirs.dz(into);
		if (stool && free(d, sx, y, sz)) {
			try (var s = d.stage(StageKind.INTERIOR)) {
				d.set(sx, y, sz, wood.stairsOrSlab(into, Half.BOTTOM));
			}
			d.poi(PoiType.SEAT, sx, y, sz, into.getOpposite());
		}
	}

	/**
	 * A bench of the laboratory at (x, y, z): a counter of the furniture's wood (a barrel or a top slab) with something on
	 * it chosen by position: a brewing stand, candles, a pot, a jar (a flower pot).
	 */
	public static void counter(Draft d, int x, int y, int z, Direction into, int k) {
		if (!free(d, x, y, z)) {
			return;
		}
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (k % 3 == 1) {
				d.set(x, y, z, States.facing(Blocks.BARREL, Direction.UP));
			} else {
				d.set(x, y, z, wood.slab(SlabType.TOP));
			}
		}
		switch (Math.floorMod(k, 4)) {
			case 0 -> brewingStand(d, x, y + 1, z);
			case 1 -> Sacra.candles(d, x, y + 1, z, 3, StageKind.INTERIOR);
			case 2 -> d.inStage(StageKind.INTERIOR, () -> d.set(x, y + 1, z, Blocks.DECORATED_POT.defaultBlockState()));
			default -> d.inStage(StageKind.INTERIOR, () -> d.set(x, y + 1, z, Blocks.FLOWER_POT.defaultBlockState()));
		}
	}
}
