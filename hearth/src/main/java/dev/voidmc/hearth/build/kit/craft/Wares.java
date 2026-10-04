package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
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
 * The goods a shop shows and the fittings that tell its trade, as blocks: shelves of meat and of candles (the shelf's
 * own display, no item frames), candles standing lit in rows, the stall board let down before a shop window, hooks
 * hanging under a jetty, a merchant's scales.
 */
public final class Wares {
	private Wares() {
	}

	/** The furniture wood's shelf, or oak's. */
	public static Block shelf(Draft d) {
		Block b = d.mat(Role.FURNITURE).main(Form.SHELF);
		return b != null ? b : Blocks.OAK_SHELF;
	}

	/** A shelf at (x, y, z) facing {@code facing} (its display side) with up to three items, each {id, count}. */
	public static void shelf(Draft d, int x, int y, int z, Direction facing, List<String> items) {
		List<Object[]> stock = new ArrayList<>();
		for (int i = 0; i < Math.min(3, items.size()); i++) {
			stock.add(new Object[]{items.get(i), 1 + (i * 7 + x + z) % 3, i});
		}
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.facing(shelf(d), facing), BlockEntities.shelf(stock)));
	}

	/** The butcher's cuts, three to a shelf by position. */
	public static List<String> meat(Draft d, int x, int y, int z) {
		String[] cuts = {"minecraft:porkchop", "minecraft:beef", "minecraft:mutton", "minecraft:chicken", "minecraft:rabbit", "minecraft:cooked_porkchop",
			"minecraft:cooked_beef"};
		List<String> out = new ArrayList<>();
		long h = Noise.hash(d.seed() ^ 0x3EA7, x, y, z);
		for (int i = 0; i < 3; i++) {
			out.add(cuts[(int) Math.floorMod(h + i * 3L, (long) cuts.length)]);
		}
		return out;
	}

	/** Candles and wax for a chandler's shelf. */
	public static List<String> candleStock(Draft d, int x, int y, int z) {
		String[] stock = {"minecraft:candle", "minecraft:white_candle", "minecraft:yellow_candle", "minecraft:honeycomb", "minecraft:red_candle",
			"minecraft:candle", "minecraft:light_gray_candle"};
		List<String> out = new ArrayList<>();
		long h = Noise.hash(d.seed() ^ 0xCA4D, x, y, z);
		for (int i = 0; i < 3; i++) {
			out.add(stock[(int) Math.floorMod(h + i * 2L, (long) stock.length)]);
		}
		return out;
	}

	/** The colours candles come in: tallow and beeswax most, dyed ones for the better houses. */
	private static final DyeColor[] CANDLE_COLOURS = {null, DyeColor.WHITE, DyeColor.YELLOW, null, DyeColor.WHITE, DyeColor.RED, DyeColor.YELLOW,
		DyeColor.LIGHT_GRAY, null, DyeColor.ORANGE, DyeColor.BROWN};

	/** A candle block by colour (null: the plain tallow candle). */
	public static Block candle(DyeColor c) {
		return c == null ? Blocks.CANDLE : Blocks.DYED_CANDLE.pick(c);
	}

	/**
	 * Candles standing at (x, y, z) on what is below: {@code count} (1–4) of a colour chosen by position, lit or not.
	 */
	public static void candles(Draft d, int x, int y, int z, int count, boolean lit) {
		BlockState s = candleState(d, x, y, z, count, lit);
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, s));
	}

	/** The candles {@link #candles} would set at a position. */
	public static BlockState candleState(Draft d, int x, int y, int z, int count, boolean lit) {
		DyeColor c = CANDLE_COLOURS[(int) Math.floorMod(Noise.hash(d.seed() ^ 0xC4D1, x, y, z), (long) CANDLE_COLOURS.length)];
		return candle(c).defaultBlockState().setValue(BlockStateProperties.CANDLES, Math.clamp(count, 1, 4)).setValue(BlockStateProperties.LIT, lit);
	}

	/**
	 * The stall board let down before a shop window: in the row outside wall {@code wall} over columns c0..c0+width-1, a
	 * board at standing level y (a top slab of the furniture wood), the medieval shop's counter to the street. Returns
	 * the cells it laid.
	 */
	public static List<int[]> stallBoard(Draft d, Side wall, int c0, int width, int y) {
		List<int[]> cells = new ArrayList<>();
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int c = c0; c < c0 + width; c++) {
				if (!wall.contains(c)) {
					continue;
				}
				int i = wall.index(c);
				int ox = wall.outX(i, 1);
				int oz = wall.outZ(i, 1);
				if (!Walls.inPlot(d, ox, oz)) {
					continue;
				}
				d.set(ox, y, oz, wood.slab(SlabType.TOP));
				cells.add(new int[]{ox, oz});
			}
		}
		return cells;
	}

	/**
	 * Hooks hanging at (x, y, z) from the block above: a chain, the butcher's hook, never lower than head height over
	 * the floor at {@code floorY}.
	 */
	public static boolean hook(Draft d, int x, int y, int z, int floorY) {
		if (y < floorY + 2 || !d.isEmpty(x, y, z) || !d.isSolid(x, y + 1, z)) {
			return false;
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y)));
		return true;
	}

	/**
	 * A merchant's balance on a counter at (x, y, z): a post with a beam across its head and the two pans hanging on
	 * chains either side ({@code along} the beam), gold pans on a gentleman's counter, iron on a trader's.
	 */
	public static void scales(Draft d, int x, int y, int z, Direction along, boolean gold) {
		Mat post = d.mat(Role.FURNITURE);
		Block pan = gold ? Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE : Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE;
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, post.has(Form.FENCE) ? post.fence() : post.slab(SlabType.BOTTOM));
			d.set(x, y + 1, z, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			for (int k : new int[]{-1, 1}) {
				int px = x + Dirs.dx(along) * k;
				int pz = z + Dirs.dz(along) * k;
				if (d.isEmpty(px, y, pz) && d.isSolid(px, y - 1, pz)) {
					d.set(px, y, pz, pan.defaultBlockState());
				}
			}
		}
	}

	/** A cake of wax (a honeycomb block) or a block of tallow (white wool) at a position, by noise. */
	public static void wax(Draft d, int x, int y, int z) {
		boolean tallow = Noise.unit(d.seed() ^ 0x7A11, x, y, z) < 0.4;
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, tallow ? Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState()
			: Blocks.HONEYCOMB_BLOCK.defaultBlockState()));
	}

	/** Candles on a board: a board (an upturned stair, its flat top up) at (x, y, z) with lit candles on it. */
	public static void candleTiers(Draft d, int x, int y, int z, Direction facing, boolean lit) {
		Mat wood = d.mat(Role.FURNITURE);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(x, y, z, wood.has(Form.STAIRS) ? wood.stairs(facing.getOpposite(), Half.TOP) : wood.slab(SlabType.TOP));
		}
		candles(d, x, y + 1, z, 2 + (int) Math.floorMod(Noise.hash(d.seed(), x, y, z), 3L), lit);
	}
}
