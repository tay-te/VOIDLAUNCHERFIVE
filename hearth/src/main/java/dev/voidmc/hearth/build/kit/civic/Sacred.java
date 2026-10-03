package dev.voidmc.hearth.build.kit.civic;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The fittings of a place of worship: pews in two banks either side of an aisle, the altar with its candles and the
 * priest's lectern, tall windows of coloured glass, buttresses, and the churchyard with its graves, its tree and its gate.
 */
public final class Sacred {
	private Sacred() {
	}

	/**
	 * Pews in rows across {@code x0..x1} (inside the walls), every other row from {@code zFirst} to {@code zLast}, leaving
	 * the aisle column {@code aisle} (and the cells against the walls when {@code margin}) free, all facing {@code toAltar}.
	 * Registers a seat on every other pew block.
	 */
	public static void pews(Draft d, int x0, int x1, int zFirst, int zLast, int y, int aisle, Direction toAltar, boolean margin) {
		Mat wood = d.mat(Role.FURNITURE);
		Direction back = toAltar.getOpposite();
		int step = Integer.signum(zLast - zFirst);
		if (step == 0) {
			step = 1;
		}
		int n = 0;
		for (int z = zFirst; step > 0 ? z <= zLast : z >= zLast; z += 2 * step) {
			for (int x = x0 + (margin ? 1 : 0); x <= x1 - (margin ? 1 : 0); x++) {
				if (Math.abs(x - aisle) <= (aisle >= 0 ? 0 : -1)) {
					continue;
				}
				if (!d.isEmpty(x, y, z)) {
					continue;
				}
				try (var s = d.stage(StageKind.INTERIOR)) {
					d.set(x, y, z, wood.stairsOrSlab(back, Half.BOTTOM));
				}
				if ((n++ & 1) == 0) {
					d.poi(PoiType.SEAT, x, y, z, toAltar);
				}
			}
		}
	}

	/**
	 * The altar at (cx, y, z): a table of the accent stone three blocks wide (one on narrow chancels) with candles and
	 * flowers on it, facing {@code toPeople}; a step up to it across the chancel; the priest's lectern beside it. Registers
	 * the ALTAR (in front of it, facing it) and the PRIEST's job site at the lectern.
	 *
	 * @param wide a three-block altar
	 */
	public static void altar(Draft d, int cx, int y, int z, Direction toPeople, boolean wide, int lecternX) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		Block chiseled = d.mat(Role.ACCENT).mainOr(Form.FULL, Blocks.CHISELED_STONE_BRICKS);
		Direction across = Dirs.right(toPeople);
		try (var s = d.stage(StageKind.INTERIOR)) {
			d.set(cx, y, z, chiseled.defaultBlockState());
			d.set(cx, y + 1, z, States.of(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
			if (wide) {
				for (int k : new int[]{-1, 1}) {
					int x = cx + Dirs.dx(across) * k;
					int zz = z + Dirs.dz(across) * k;
					d.set(x, y, zz, accent.stairsOrSlab(k < 0 ? across : across.getOpposite(), Half.TOP));
					if (k < 0) {
						Garden.pot(d, x, y + 1, zz);
					} else {
						d.set(x, y + 1, zz, States.of(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, 2).setValue(BlockStateProperties.LIT, true));
					}
				}
			}
		}
		d.poi(PoiType.ALTAR, cx + Dirs.dx(toPeople), y, z + Dirs.dz(toPeople), toPeople.getOpposite());
		if (lecternX != Integer.MIN_VALUE) {
			int lz = z + Dirs.dz(toPeople);
			Furniture.workstation(d, JobType.PRIEST, lecternX, y, lz, toPeople);
		}
	}

	/**
	 * A tall window in a wall at column {@code col} of side {@code s}: {@code width} (1 or 2) by {@code height} from
	 * {@code y}, its panes coloured glass of the style's cloth in the middle rows and clear at the edges, a pointed head
	 * (two shoulders on a pair, a keystone on a single light), a sill outside.
	 */
	public static void lancet(Draft d, Side s, int col, int y, int width, int height, boolean coloured) {
		int i0 = s.index(col);
		Mat glass = d.mat(Role.GLASS);
		Mat cloth = d.mat(Role.CLOTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat sill = d.mat(Role.WINDOW);
		Direction left = s.along().getOpposite();
		Direction right = s.along();
		boolean stained = coloured && cloth.has(Form.PANE);
		try (var st = d.stage(StageKind.OPENINGS)) {
			for (int c = 0; c < width; c++) {
				int i = i0 + c;
				int x = s.x(i);
				int z = s.z(i);
				for (int yy = y; yy < y + height; yy++) {
					boolean mid = yy > y && yy < y + height - 1;
					Paint p = stained && (mid || height <= 2) ? cloth.pane() : glass.pane();
					d.set(x, yy, z, p);
				}
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				// never on the front row: the street's way along it (steps down to a raised door) stays clear
				if (oz >= 1 && inPlotOrHigh(d, ox, y - 1, oz) && d.isAbsent(ox, y - 1, oz) || oz == -1 && y - 1 >= 3) {
					d.set(ox, y - 1, oz, sill.stairsOrSlab(s.out().getOpposite(), Half.TOP));
				}
			}
			int top = y + height;
			if (width >= 2) {
				d.set(s.x(i0), top - 1, s.z(i0), accent.stairsOrSlab(left, Half.TOP));
				d.set(s.x(i0 + 1), top - 1, s.z(i0 + 1), accent.stairsOrSlab(right, Half.TOP));
			}
			for (int c = -1; c <= width; c++) {
				int i = i0 + c;
				if (i >= 1 && i <= s.last() - 1) {
					d.set(s.x(i), top, s.z(i), accent.full());
				}
			}
		}
	}

	/** A round window (a plus of panes round a coloured centre) centred on cell i of side s at height yc. */
	public static void rose(Draft d, Side s, int i, int yc) {
		Mat glass = d.mat(Role.GLASS);
		Mat cloth = d.mat(Role.CLOTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Paint centre = cloth.has(Form.PANE) ? cloth.pane() : glass.pane();
		try (var st = d.stage(StageKind.OPENINGS)) {
			d.set(s.x(i), yc, s.z(i), centre);
			for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				int j = i + o[0];
				if (j < 1 || j > s.last() - 1) {
					continue;
				}
				d.set(s.x(j), yc + o[1], s.z(j), glass.pane());
			}
			for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
				int j = i + o[0];
				if (j < 1 || j > s.last() - 1) {
					continue;
				}
				d.set(s.x(j), yc + o[1], s.z(j), accent.full());
			}
		}
	}

	/**
	 * A buttress against wall {@code s} at cell i: a pier of the plinth stone one block out from the wall, standing on
	 * the ground, as high as {@code top}, finished with a sloping stair.
	 */
	public static void buttress(Draft d, Side s, int i, int base, int top) {
		int x = s.outX(i, 1);
		int z = s.outZ(i, 1);
		if (x < 0 || x >= d.frontage() || z < 0 || z >= d.depth()) {
			return;
		}
		Mat stone = d.mat(Role.PLINTH);
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var st = d.stage(StageKind.WALLS)) {
			int g = d.site().ground(x, z);
			for (int y = Math.min(base, g) - 1; y < top; y++) {
				d.set(x, y, z, y < base + 1 ? stone.full() : accent.full());
			}
			d.set(x, top, z, accent.stairsOrSlab(s.out().getOpposite(), Half.BOTTOM));
		}
	}

	/**
	 * A churchyard over a rectangle at standing level {@code y}: rows of graves (a headstone at the head, a mound or a
	 * candle and flowers on the grave), a yew in a corner, a path along it; the style's yard boundary round it with a gate
	 * toward {@code gateSide}. Only on ground that lies within a block of {@code y}.
	 */
	public static void churchyard(Draft d, int x0, int z0, int x1, int z1, int y, boolean wall, Direction gateSide) {
		if (x1 - x0 < 1 || z1 - z0 < 1) {
			return;
		}
		Mat stone = d.mat(Role.FOUNDATION);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Fam andesite = Fam.of(d, Role.WALL_ACCENT, Form.FULL, StageKind.EXTERIOR);
		if (andesite == null) {
			andesite = Fam.of("minecraft:andesite", StageKind.EXTERIOR);
		}
		long seed = Noise.hash(d.seed(), 0x6A7E);
		boolean rowsAlongX = x1 - x0 >= z1 - z0;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (Math.abs(Groundwork.surface(d, x, z) - y) > 0 || !d.isEmpty(x, y, z) || d.site().isWater(x, z)) {
					continue;
				}
				int a = rowsAlongX ? x - x0 : z - z0;
				int b = rowsAlongX ? z - z0 : x - x0;
				// graves in rows two apart, a path cell between pairs
				if ((a % 2) != 0 || (b % 3) == 2) {
					continue;
				}
				double u = Noise.unit(seed, x, y, z);
				if (u < 0.15) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					// the headstone
					Direction facing = rowsAlongX ? Dirs.FRONT : Dirs.LEFT;
					Paint head;
					if (u < 0.45) {
						head = andesite.stairs(facing.getOpposite(), Half.BOTTOM);
					} else if (u < 0.7) {
						head = stone.has(Form.WALL) ? stone.wall() : accent.wall();
					} else if (u < 0.85) {
						head = accent.has(Form.SLAB) ? accent.slab(SlabType.BOTTOM) : stone.full();
					} else {
						head = Paint.of(Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
					}
					d.set(x, y, z, head);
					// the grave in front of it: a mound of coarse earth, flowers or a candle
					int gx = x + (rowsAlongX ? 0 : 1);
					int gz = z + (rowsAlongX ? -1 : 0);
					if (gx >= x0 && gx <= x1 && gz >= z0 && gz <= z1 && d.isEmpty(gx, y, gz) && Groundwork.surface(d, gx, gz) == y) {
						double v = Noise.unit(seed ^ 0x77, gx, y, gz);
						d.set(gx, y - 1, gz, (v < 0.5 ? Blocks.COARSE_DIRT : Blocks.PODZOL).defaultBlockState());
						if (v < 0.25) {
							d.set(gx, y, gz, States.of(Blocks.CANDLE).setValue(BlockStateProperties.LIT, v < 0.12));
						} else if (v < 0.6) {
							Garden.flower(d, gx, y, gz);
						}
					}
				}
			}
		}
		// a dark tree in the far corner
		int tx = x0 == 0 ? x0 + 1 : x1 - 1;
		int tz = z1 - 1;
		if (d.isEmpty(tx, y, tz) && Groundwork.surface(d, tx, tz) == y && x1 - x0 >= 2 && z1 - z0 >= 2) {
			Garden.tree(d, tx, y, tz, 3);
		}
	}

	/** Lit candles in a cluster on a surface block's top at (x, y, z). */
	public static void candles(Draft d, int x, int y, int z, int n) {
		BlockState c = States.of(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, Math.clamp(n, 1, 4)).setValue(BlockStateProperties.LIT, true);
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, c));
	}

	private static boolean inPlotOrHigh(Draft d, int x, int y, int z) {
		return x >= 0 && x < d.frontage() && z < d.depth() && (z >= 0 || z == -1 && y >= 3);
	}

	/** A short list helper. */
	static <T> List<T> list(T a, T b) {
		return List.of(a, b);
	}
}
